package com.projectflow.presentation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * The seam between the Action Item URL (docs/tasks/wbs-description-and-action-link.md §D) and
 * <b>commits that were taken before it existed</b>.
 *
 * <p>Nothing else in the suite can reach this state. Every other commit test creates its commit
 * with the current code, so its payloads always carry {@code actionItemUrl} and {@code
 * formatVersion 8}. A commit is immutable by design, so the rows that already sit in a user's H2
 * file have neither — and they are read back by {@code ProjectCommitService.readComputedPayload}
 * /{@code readRawPayload}, which deserialise the stored JSON into today's records. This test
 * manufactures that state the only way it can be manufactured: by rewriting the two payload
 * columns with plain JDBC, using only the column names {@code V22} fixed.
 *
 * <p>Two questions, neither of which a single-silo test could ask:
 *
 * <ul>
 *   <li>Does viewing a pre-V24 commit still work — i.e. does the missing key deserialise as
 *       "none recorded" rather than blowing up the whole detail response?</li>
 *   <li>Does restoring one still work — {@code ImportService.SUPPORTED_FORMAT_VERSION} went
 *       7 → 8, and a stored payload says 7.</li>
 * </ul>
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:wbs-action-url-commit-seam;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        })
@AutoConfigureMockMvc
@ActiveProfiles("desktop")
@DisplayName("Action Item 주소 — 커밋·일괄 가져오기 이음새")
class WbsActionItemUrlCommitSeamTest {

    private static final String SAFE_URL = "https://example.com/meeting/2026-09-30";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DataSource dataSource;

    @Nested
    @DisplayName("V24 이전에 찍힌 커밋")
    class OldCommit {

        @Test
        @DisplayName("주소 키가 아예 없는 computed_payload도 그대로 조회된다 — 미입력으로 읽힌다")
        void readsComputedPayloadWithoutTheKey() throws Exception {
            long projectId = createProject("구형 커밋 조회");
            createItem(projectId, "설계 회의록", SAFE_URL);
            JsonNode commit = postCommit(projectId);
            downgradeToPreV24(commit.get("id").asLong());

            MvcResult result = mockMvc.perform(
                            get("/api/projects/" + projectId + "/commits/" + commit.get("version").asInt()))
                    .andReturn();

            assertThat(result.getResponse().getStatus())
                    .as("저장된 JSON에 없는 필드는 null로 읽혀야 한다 — 500이 나면 구형 커밋을 "
                            + "아예 열 수 없다는 뜻이고, 커밋은 불변이라 고칠 방법도 없다")
                    .isEqualTo(200);
            JsonNode node = readTree(result).get("payload").get("wbs").get("nodes").get(0);
            assertThat(node.get("name").asText()).isEqualTo("설계 회의록");
            assertThat(node.has("actionItemUrl")).isTrue();
            assertThat(node.get("actionItemUrl").isNull()).isTrue();
        }

        @Test
        @DisplayName("formatVersion 7짜리 raw_payload도 복원된다 — 지원 버전이 8로 올라갔어도")
        void restoresRawPayloadStoredAsVersionSeven() throws Exception {
            long projectId = createProject("구형 커밋 복원");
            createItem(projectId, "설계 회의록", SAFE_URL);
            JsonNode commit = postCommit(projectId);
            long commitId = commit.get("id").asLong();
            downgradeToPreV24(commitId);

            // 저장된 값이 정말 구형인지 먼저 못박는다 — 그러지 않으면 이 테스트는 8짜리 payload를
            // 복원하는 위 케이스와 같은 것을 두 번 확인하는 셈이 된다.
            assertThat(storedRawPayload(commitId).get("formatVersion").asInt()).isEqualTo(7);
            assertThat(storedRawPayload(commitId).get("wbsItems").get(0).has("actionItemUrl"))
                    .isFalse();

            MvcResult result = mockMvc.perform(post("/api/projects/commits/" + commitId + "/restore"))
                    .andReturn();

            assertThat(result.getResponse().getStatus())
                    .as("import는 파일보다 높은 버전만 거부한다. 7 <= 8이므로 통과해야 한다")
                    .isEqualTo(201);
            long copyId = readTree(result).get("id").asLong();
            assertThat(find(getTree(copyId), "설계 회의록").get("actionItemUrl").isNull())
                    .as("구형 커밋에는 주소가 없었으므로 복원본에도 없다 — 조용히 채워 넣지 않는다")
                    .isTrue();
        }

        @Test
        @DisplayName("대조군 — 지금 찍은 커밋(8)을 복원하면 주소가 살아 온다")
        void restoresCurrentCommitWithTheUrl() throws Exception {
            long projectId = createProject("새 커밋 복원");
            createItem(projectId, "설계 회의록", SAFE_URL);
            JsonNode commit = postCommit(projectId);

            assertThat(commit.get("formatVersion").asInt()).isEqualTo(8);

            MvcResult result = mockMvc.perform(
                            post("/api/projects/commits/" + commit.get("id").asLong() + "/restore"))
                    .andReturn();

            assertThat(result.getResponse().getStatus()).isEqualTo(201);
            long copyId = readTree(result).get("id").asLong();
            assertThat(find(getTree(copyId), "설계 회의록").get("actionItemUrl").asText())
                    .isEqualTo(SAFE_URL);
        }

        @Test
        @DisplayName("구형 커밋의 raw_payload 내려받기도 그대로 돈다")
        void exportsOldRawPayload() throws Exception {
            long projectId = createProject("구형 커밋 내보내기");
            createItem(projectId, "설계 회의록", SAFE_URL);
            JsonNode commit = postCommit(projectId);
            downgradeToPreV24(commit.get("id").asLong());

            MvcResult result = mockMvc.perform(get("/api/projects/" + projectId + "/commits/"
                            + commit.get("version").asInt() + "/export"))
                    .andReturn();

            assertThat(result.getResponse().getStatus()).isEqualTo(200);
            JsonNode file = objectMapper.readTree(result.getResponse().getContentAsString());
            assertThat(file.get("formatVersion").asInt())
                    .as("박제된 값이므로 오늘의 8로 올려 적지 않는다")
                    .isEqualTo(7);
        }
    }

    @Nested
    @DisplayName("Excel·CSV 일괄 가져오기 (WbsService의 세 번째 new WbsItem)")
    class BulkImport {

        /**
         * The parser's five fixed columns are an intentional contract (지시서 §4), so this path was
         * deliberately left alone. That makes it the one creation path where {@code actionItemUrl}
         * is never set at all — it has to fall out of the constructor as {@code null} and travel
         * through the tree response and an export/import round trip without anything choking on it.
         */
        @Test
        @DisplayName("파일로 넣은 행은 주소가 null이고, 그 상태로 내보내기·가져오기까지 돈다")
        void bulkImportedRowsCarryNoUrl() throws Exception {
            long projectId = createProject("일괄 가져오기");

            String csv = "레벨,업무명,시작일,종료일,진행률\n"
                    + "1,설계,2026-01-01,2026-01-31,0\n"
                    + "2,화면 설계,2026-01-01,2026-01-15,0\n";
            MvcResult result = mockMvc.perform(multipartCsv(projectId, csv)).andReturn();
            assertThat(result.getResponse().getStatus()).isEqualTo(201);

            JsonNode tree = readTree(result);
            assertThat(find(tree, "설계").get("actionItemUrl").isNull()).isTrue();
            assertThat(find(tree, "화면 설계").get("actionItemUrl").isNull()).isTrue();

            JsonNode file = export(projectId);
            assertThat(file.get("formatVersion").asInt()).isEqualTo(8);
            for (JsonNode item : file.get("wbsItems")) {
                assertThat(item.get("actionItemUrl").isNull())
                        .as("파서가 채우지 않는 값이므로 내보내기에서도 비어 있어야 한다")
                        .isTrue();
            }

            MvcResult imported = mockMvc.perform(post("/api/projects/import")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(file)))
                    .andReturn();
            assertThat(imported.getResponse().getStatus()).isEqualTo(201);
            assertThat(find(getTree(readTree(imported).get("id").asLong()), "화면 설계")
                    .get("actionItemUrl").isNull()).isTrue();
        }
    }

    // ---- 구형 커밋 제조 ----------------------------------------------------

    /**
     * Rewrites a commit's two payload columns into the shape V24 could not have produced: every
     * {@code actionItemUrl} key removed (not set to null — <em>absent</em>, which is what a JSON
     * written before the field existed looks like) and {@code formatVersion} back to 7.
     *
     * <p>Plain JDBC on purpose. There is no API that can write a commit payload — commits are
     * immutable and have no update endpoint — and that immutability is exactly why this state is
     * reachable in the field but not through the app.
     */
    private void downgradeToPreV24(long commitId) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            JsonNode raw = readPayloadColumn(connection, commitId, "raw_payload");
            ((ObjectNode) raw).put("formatVersion", 7);
            for (JsonNode item : raw.get("wbsItems")) {
                ((ObjectNode) item).remove("actionItemUrl");
            }

            JsonNode computed = readPayloadColumn(connection, commitId, "computed_payload");
            stripActionItemUrl(computed.get("wbs").get("nodes"));

            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE project_commits SET raw_payload = ?, computed_payload = ?,"
                            + " format_version = 7 WHERE id = ?")) {
                update.setString(1, objectMapper.writeValueAsString(raw));
                update.setString(2, objectMapper.writeValueAsString(computed));
                update.setLong(3, commitId);
                assertThat(update.executeUpdate()).isEqualTo(1);
            }
        }
    }

    /** Depth-first: the tree response nests children, so a flat pass would miss everything but roots. */
    private void stripActionItemUrl(JsonNode nodes) {
        for (JsonNode node : nodes) {
            ((ObjectNode) node).remove("actionItemUrl");
            stripActionItemUrl(node.get("children"));
        }
    }

    private JsonNode readPayloadColumn(Connection connection, long commitId, String column)
            throws Exception {
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT " + column + " FROM project_commits WHERE id = ?")) {
            select.setLong(1, commitId);
            try (ResultSet rows = select.executeQuery()) {
                assertThat(rows.next()).isTrue();
                return objectMapper.readTree(rows.getString(1));
            }
        }
    }

    private JsonNode storedRawPayload(long commitId) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            return readPayloadColumn(connection, commitId, "raw_payload");
        }
    }

    // ---- fixture helpers -------------------------------------------------

    private MockMultipartHttpServletRequestBuilder multipartCsv(long projectId, String csv) {
        return multipart("/api/projects/" + projectId + "/wbs/import")
                .file(new MockMultipartFile("file", "wbs.csv", "text/csv",
                        csv.getBytes(StandardCharsets.UTF_8)));
    }

    private long createProject(String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andReturn();
        return readTree(result).get("id").asLong();
    }

    private void createItem(long projectId, String name, String url) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/projects/" + projectId + "/wbs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"nodeType\":\"WORK_PACKAGE\","
                                + "\"actionItemUrl\":\"" + url + "\"}"))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
    }

    private JsonNode postCommit(long projectId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/projects/" + projectId + "/commits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"committedBy\":\"tester\",\"message\":\"이음새 확인\"}"))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        return readTree(result).get("commit");
    }

    private JsonNode export(long projectId) throws Exception {
        return objectMapper.readTree(mockMvc.perform(get("/api/projects/" + projectId + "/export"))
                .andReturn().getResponse().getContentAsString());
    }

    private JsonNode getTree(long projectId) throws Exception {
        return readTree(mockMvc.perform(get("/api/projects/" + projectId + "/wbs")).andReturn());
    }

    private JsonNode find(JsonNode tree, String name) {
        JsonNode found = search(tree.get("nodes"), name);
        if (found == null) {
            throw new IllegalStateException("node not found: " + name + " in " + tree);
        }
        return found;
    }

    private JsonNode search(JsonNode nodes, String name) {
        for (JsonNode node : nodes) {
            if (node.get("name").asText().equals(name)) {
                return node;
            }
            JsonNode deeper = search(node.get("children"), name);
            if (deeper != null) {
                return deeper;
            }
        }
        return null;
    }

    private JsonNode readTree(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
