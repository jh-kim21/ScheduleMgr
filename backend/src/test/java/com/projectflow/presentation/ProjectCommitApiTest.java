package com.projectflow.presentation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.sql.DataSource;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Black-box tests for the commit-history feature (docs/tasks/commit-history.md), driven entirely
 * through the HTTP API rather than the implementers' internal classes.
 *
 * <p>This is deliberate, not just a style choice: three developers are building this feature
 * concurrently and none of their entity/service/repository class names are part of the contract
 * the team lead handed down (only the endpoints, JSON field names, and table/column names from the
 * migration draft are). Referencing an internal class that turns out to be named differently would
 * fail compilation for the whole test module, hiding every other result. Hitting the documented
 * endpoints and asserting on the documented JSON shape stays valid no matter which of the three
 * chooses which internal design, and is exactly what a real client (the frontend) would depend on
 * anyway.
 *
 * <p>The one exception is {@link #deletingProjectCascadesToItsCommits()}, which reads the
 * {@code project_commits} table directly with plain JDBC — using only the column names fixed by
 * 지시서 §4.1's migration DDL, not any Java class — because there is no API that can observe a row
 * that no longer has a project to hang off of.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:project-commit-api-test;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        })
@AutoConfigureMockMvc
@ActiveProfiles("desktop")
@DisplayName("프로젝트 커밋 히스토리 — API 계약")
class ProjectCommitApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DataSource dataSource;

    // ---- fixture helpers -------------------------------------------------

    private long createProject(String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andReturn();
        return readTree(result).get("id").asLong();
    }

    private long createWorkPackage(long projectId, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/projects/" + projectId + "/wbs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"startDate\":\"2026-01-01\","
                                + "\"endDate\":\"2026-01-31\",\"progress\":0}"))
                .andReturn();
        JsonNode tree = readTree(result);
        // creation returns the whole rebuilt tree (CLAUDE.md); the entry just added is the one
        // whose name matches — there is exactly one at this point in each test that uses this.
        for (JsonNode node : tree.get("nodes")) {
            if (node.get("name").asText().equals(name)) {
                return node.get("id").asLong();
            }
        }
        throw new IllegalStateException("created node not found in tree: " + tree);
    }

    /**
     * Returns just the {@code commit} sub-object of {@code CommitCreateResponse} ({@code
     * {commit: {...}, capacity: {...}}}) — every call site here wants the metadata (id, version,
     * asOf, ...), never the capacity gauge that rides along with it.
     */
    private JsonNode postCommit(long projectId, String message) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/projects/" + projectId + "/commits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"committedBy\":\"tester\",\"message\":\"" + message + "\"}"))
                .andReturn();
        return readTree(result).get("commit");
    }

    private JsonNode readTree(MvcResult result) throws Exception {
        String body = result.getResponse().getContentAsString();
        return body.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(body);
    }

    // ---- 1. version numbering survives deletion (지시서 3.3 / 7절 최우선 항목) -------------

    /**
     * 지시서 3.3이 명시적으로 경고하는 함정이다: {@code count + 1}로 채번하면 삭제된 번호가
     * 재사용된다. [v1, v2, v3]에서 v2를 지우면 남은 커밋 수는 2개이므로 {@code count+1}은 v3을
     * 다시 내놓고, {@code UNIQUE (project_id, version)}에 걸려 500이 나거나(운이 나쁘면) 실제로
     * v3이 두 번 존재하는 것처럼 보이는 더 나쁜 결과가 나온다. 올바른 구현
     * ({@code MAX(version)+1})은 삭제된 v2를 건너뛰고 v4를 내놓아야 한다.
     */
    @Test
    @DisplayName("[v1,v2,v3]에서 v2를 삭제하고 커밋하면 v3이 아니라 v4가 된다 — count+1 채번의 함정을 잡는다")
    void versionNumberingSkipsDeletedNumbers() throws Exception {
        long projectId = createProject("채번 테스트");

        JsonNode v1 = postCommit(projectId, "v1");
        JsonNode v2 = postCommit(projectId, "v2");
        JsonNode v3 = postCommit(projectId, "v3");
        assertThat(v1.get("version").asInt()).isEqualTo(1);
        assertThat(v2.get("version").asInt()).isEqualTo(2);
        assertThat(v3.get("version").asInt()).isEqualTo(3);

        mockMvc.perform(delete("/api/projects/" + projectId + "/commits/2"))
                .andExpect(status2xx());

        JsonNode v4 = postCommit(projectId, "v4 — should not be v3 again");
        assertThat(v4.get("version").asInt())
                .as("MAX(version)+1이어야 한다. size()+1(즉 count+1)으로 구현하면 남은 커밋이 2개뿐이라 "
                        + "여기서 3이 나오고 UNIQUE(project_id, version)과 충돌한다")
                .isEqualTo(4);
    }

    // ---- 2. content is frozen at commit time (이 기능의 존재 이유) ------------------------

    /**
     * 커밋의 존재 이유 자체를 검증한다: 저장 이후 라이브 데이터가 바뀌어도 커밋을 열면 저장
     * "당시"의 숫자가 그대로 나와야 한다. 재계산 방식으로 구현하면(지시서 2.1이 경고하는 바로 그
     * 실수) 이 테스트가 실패한다 — computed_payload가 최신 진행률을 다시 읽어와 버리기 때문이다.
     */
    @Test
    @DisplayName("커밋 후 데이터를 바꿔도 커밋 조회 시 저장 당시의 숫자가 그대로 나온다 (재계산 아님)")
    void commitContentIsFrozenAtCommitTime() throws Exception {
        long projectId = createProject("박제 테스트");
        long wpId = createWorkPackage(projectId, "업무 A");

        // progress 30% 상태에서 커밋
        mockMvc.perform(put("/api/projects/" + projectId + "/wbs/" + wpId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"업무 A\",\"startDate\":\"2026-01-01\",\"endDate\":\"2026-01-31\","
                        + "\"progress\":30}"));
        JsonNode commit = postCommit(projectId, "30% 시점");
        int version = commit.get("version").asInt();

        // 커밋 후 90%로 크게 바꾸고, 두 번째 업무도 추가한다
        mockMvc.perform(put("/api/projects/" + projectId + "/wbs/" + wpId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"업무 A\",\"startDate\":\"2026-01-01\",\"endDate\":\"2026-01-31\","
                        + "\"progress\":90}"));
        createWorkPackage(projectId, "업무 B");

        // 라이브 데이터는 바뀌어 있어야 정상
        MvcResult live = mockMvc.perform(get("/api/projects/" + projectId + "/wbs")).andReturn();
        JsonNode liveTree = readTree(live);
        assertThat(liveTree.get("nodes")).hasSize(2);
        assertThat(findByName(liveTree.get("nodes"), "업무 A").get("progress").asInt()).isEqualTo(90);

        // 커밋에 박제된 데이터는 그대로여야 한다
        MvcResult commitDetail = mockMvc.perform(
                        get("/api/projects/" + projectId + "/commits/" + version))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode payload = readTree(commitDetail).get("payload");
        assertThat(payload).as("CommitDetailResponse.payload가 응답에 있어야 한다").isNotNull();
        JsonNode wbsAtCommit = payload.get("wbs");
        assertThat(wbsAtCommit.get("nodes"))
                .as("커밋 시점에는 업무 A 하나뿐이었다")
                .hasSize(1);
        assertThat(findByName(wbsAtCommit.get("nodes"), "업무 A").get("progress").asInt())
                .as("커밋 시점의 진행률(30%)이 보존되어야 한다 — 최신 값(90%)이 아니다")
                .isEqualTo(30);
    }

    private JsonNode findByName(JsonNode nodes, String name) {
        for (JsonNode node : nodes) {
            if (node.get("name").asText().equals(name)) {
                return node;
            }
        }
        throw new IllegalStateException("no node named " + name + " in " + nodes);
    }

    // ---- 3. latest commit is deletable (기준선과 다른 점) ----------------------------------

    @Test
    @DisplayName("가장 최근 커밋도 삭제할 수 있다 — 기준선(Baseline)과 달리 제약이 없다")
    void latestCommitIsDeletable() throws Exception {
        long projectId = createProject("최신 삭제 테스트");
        postCommit(projectId, "v1");
        JsonNode v2 = postCommit(projectId, "v2 — 가장 최근");

        mockMvc.perform(delete("/api/projects/" + projectId + "/commits/" + v2.get("version").asInt()))
                .andExpect(status2xx());

        mockMvc.perform(get("/api/projects/" + projectId + "/commits/" + v2.get("version").asInt()))
                .andExpect(status().is4xxClientError());
    }

    // ---- 4. no update endpoint (불변, 삭제만 가능) ------------------------------------------

    @Test
    @DisplayName("커밋 수정 API가 없다 — PUT은 404 또는 405여야 한다 (200으로 받아들이면 안 됨)")
    void commitsCannotBeUpdated() throws Exception {
        long projectId = createProject("불변성 테스트");
        JsonNode v1 = postCommit(projectId, "v1");

        mockMvc.perform(put("/api/projects/" + projectId + "/commits/" + v1.get("version").asInt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"몰래 고치기\"}"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assertThat(status)
                            .as("PUT 매핑이 아예 없어야 하므로 404(경로 없음) 또는 405(메서드 불허)만 허용된다")
                            .isIn(404, 405);
                });
    }

    // ---- 5. project deletion cascades to its commits (CASCADE) -----------------------------

    /**
     * API만으로는 관측할 수 없다 — 프로젝트가 사라지면 그 프로젝트의 커밋을 나열할 API 자체가
     * 없어진다(404). 그래서 여기서만 예외적으로 원시 JDBC로 {@code project_commits} 테이블을
     * 직접 들여다본다. 컬럼명은 지시서 §4.1의 마이그레이션 DDL에 고정되어 있으므로 구현 클래스
     * 이름과 무관하다.
     */
    @Test
    @DisplayName("프로젝트를 삭제하면 그 프로젝트의 커밋도 함께 삭제된다 (project_commits.project_id ON DELETE CASCADE)")
    void deletingProjectCascadesToItsCommits() throws Exception {
        long projectId = createProject("CASCADE 테스트");
        postCommit(projectId, "v1");
        postCommit(projectId, "v2");

        assertThat(countCommitRows(projectId))
                .as("삭제 전에는 커밋이 남아 있어야 정상이다 (이 수가 이미 0이면 커밋 저장 자체가 실패한 것)")
                .isEqualTo(2);

        mockMvc.perform(delete("/api/projects/" + projectId))
                .andExpect(status2xx());

        assertThat(countCommitRows(projectId))
                .as("프로젝트 삭제 후 project_commits에 이 project_id의 행이 남아 있으면 안 된다")
                .isZero();
    }

    private long countCommitRows(long projectId) throws Exception {
        try (var connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM project_commits WHERE project_id = ?")) {
            statement.setLong(1, projectId);
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    // ---- 6. single reference date across all screens in one commit -------------------------

    /**
     * 지시서 3.5: {@code LocalDate.now()}가 WbsService·GanttService·ProgressService·RaidService
     * 네 곳에 흩어져 있어, 커밋이 이들을 연달아 호출하면 자정 직전에는 화면마다 "오늘"이 갈릴 수
     * 있다. 시계를 실제로 자정 직전으로 돌리기는 어려우므로, 대신 한 커밋 안에 저장된 여러 화면의
     * {@code referenceDate}가 서로 같고 최상위 {@code asOf}와도 같은지로 단일화 여부를 확인한다 —
     * 자정 여부와 무관하게 "한 번만 정해서 재사용했는가"를 보여주는 대리 지표다.
     */
    @Test
    @DisplayName("커밋 하나 안의 wbs·gantt·progress·raid·dashboard referenceDate가 모두 같고 asOf와 일치한다")
    void allScreensInOneCommitShareTheSameReferenceDate() throws Exception {
        long projectId = createProject("기준일 단일화 테스트");
        createWorkPackage(projectId, "업무 A");

        JsonNode commit = postCommit(projectId, "기준일 확인");
        int version = commit.get("version").asInt();

        MvcResult detail = mockMvc.perform(get("/api/projects/" + projectId + "/commits/" + version))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = readTree(detail);
        String asOf = body.get("commit").get("asOf").asText();
        JsonNode payload = body.get("payload");

        assertThat(payload.get("asOf").asText())
                .as("CommitComputedPayload.asOf도 같은 값이어야 한다 — 메타의 asOf와 페이로드의 asOf가 "
                        + "따로 계산되면 애초에 '한 번만 정해 재사용'하지 않았다는 뜻이다")
                .isEqualTo(asOf);
        assertThat(payload.get("wbs").get("referenceDate").asText()).isEqualTo(asOf);
        assertThat(payload.get("gantt").get("referenceDate").asText()).isEqualTo(asOf);
        assertThat(payload.get("progress").get("referenceDate").asText()).isEqualTo(asOf);
        assertThat(payload.get("raid").get("referenceDate").asText()).isEqualTo(asOf);
        assertThat(payload.get("dashboard").get("referenceDate").asText()).isEqualTo(asOf);
    }

    // ---- 7. new exceptions answer with 4xx, never 500 --------------------------------------

    /**
     * CLAUDE.md에 기록된 실제 사고("Step 4에서 [예외 등록을] 빠뜨려 Sprint의 모든 거부가 500으로
     * 나갔고, 서비스 단위 테스트로는 드러나지 않았다")와 같은 사고를 잡기 위한 컨트롤러 레벨 확인.
     * 서비스 단위 테스트는 예외가 던져지는 것만 보고, 그 예외가 {@code GlobalExceptionHandler}에
     * 등록되어 있는지는 못 본다 — MockMvc로 실제 상태 코드를 봐야 한다.
     */
    @Test
    @DisplayName("존재하지 않는 프로젝트/커밋에 대한 커밋 API 호출은 4xx다 — 500이 아니다")
    void unknownProjectOrCommitAnswersWithClientErrorNotServerError() throws Exception {
        long missingProjectId = 999_999_999L;
        long missingVersion = 999;

        mockMvc.perform(get("/api/projects/" + missingProjectId + "/commits"))
                .andExpect(status().is4xxClientError());
        mockMvc.perform(post("/api/projects/" + missingProjectId + "/commits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"committedBy\":\"tester\",\"message\":\"x\"}"))
                .andExpect(status().is4xxClientError());

        long projectId = createProject("존재하지 않는 커밋 테스트");
        mockMvc.perform(get("/api/projects/" + projectId + "/commits/" + missingVersion))
                .andExpect(status().is4xxClientError());
        mockMvc.perform(delete("/api/projects/" + projectId + "/commits/" + missingVersion))
                .andExpect(status().is4xxClientError());
        mockMvc.perform(get("/api/projects/" + projectId + "/commits/" + missingVersion + "/export"))
                .andExpect(status().is4xxClientError());
        mockMvc.perform(post("/api/projects/commits/" + missingVersion + "/restore"))
                .andExpect(status().is4xxClientError());
    }

    // ---- 8. restore creates a new project with everything remapped -------------------------

    /**
     * 복원 파이프라인(컨트롤러 → raw_payload → 기존 ImportService) 자체를 검증한다. id 재매핑
     * 로직 그 자체는 이미 ImportServiceTest가 촘촘히 덮고 있으므로, 여기서는 "커밋이 저장해 둔
     * raw_payload를 restore 엔드포인트가 ImportService에 제대로 넘기는가"만 확인한다 — 그래서
     * 각 영역(WBS·의존성·RACI·RAID·Backlog·Sprint)에서 하나씩만 심고, id가 실제로 바뀌었는지와
     * 원본 프로젝트가 그대로 남아 있는지를 함께 본다.
     */
    @Test
    @DisplayName("커밋에서 복원하면 새 프로젝트가 생기고 WBS·의존성·RACI·RAID·Backlog·Sprint 참조가 재매핑된다")
    void restoreCreatesNewProjectWithRemappedReferences() throws Exception {
        long projectId = createProject("복원 원본");

        // WBS: Summary 루트 아래 두 Work Package
        MvcResult rootResult = mockMvc.perform(post("/api/projects/" + projectId + "/wbs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"루트\",\"nodeType\":\"SUMMARY\"}"))
                .andReturn();
        long rootId = findByName(readTree(rootResult).get("nodes"), "루트").get("id").asLong();

        MvcResult aResult = mockMvc.perform(post("/api/projects/" + projectId + "/wbs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parentId\":" + rootId + ",\"name\":\"WP-A\","
                                + "\"startDate\":\"2026-01-01\",\"endDate\":\"2026-01-10\"}"))
                .andReturn();
        long wpAId = findChild(readTree(aResult), rootId, "WP-A");

        MvcResult bResult = mockMvc.perform(post("/api/projects/" + projectId + "/wbs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parentId\":" + rootId + ",\"name\":\"WP-B\","
                                + "\"startDate\":\"2026-01-11\",\"endDate\":\"2026-01-20\"}"))
                .andReturn();
        long wpBId = findChild(readTree(bResult), rootId, "WP-B");

        // 의존성: A -> B
        mockMvc.perform(post("/api/projects/" + projectId + "/gantt/dependencies")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"predecessorId\":" + wpAId + ",\"successorId\":" + wpBId
                        + ",\"lagDays\":2}"));

        // 구성원 + RACI
        MvcResult memberResult = mockMvc.perform(post("/api/projects/" + projectId + "/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"김담당\"}"))
                .andReturn();
        long memberId = readTree(memberResult).get("id").asLong();
        mockMvc.perform(post("/api/projects/" + projectId + "/raci/assignments")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"wbsItemId\":" + wpAId + ",\"memberId\":" + memberId
                        + ",\"role\":\"RESPONSIBLE\"}"));

        // RAID: WP-A에 연결된 위험
        mockMvc.perform(post("/api/projects/" + projectId + "/raid")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"RISK\",\"title\":\"복원 테스트 위험\",\"status\":\"OPEN\","
                        + "\"links\":[{\"targetType\":\"WBS_ITEM\",\"targetId\":" + wpAId + "}]}"));

        // Backlog: WP-A에 붙은 Story
        MvcResult backlogResult = mockMvc.perform(post("/api/projects/" + projectId + "/backlog")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"wbsItemId\":" + wpAId + ",\"itemType\":\"STORY\","
                                + "\"title\":\"복원 테스트 스토리\",\"priority\":\"MEDIUM\","
                                + "\"status\":\"TODO\"}"))
                .andReturn();
        long backlogItemId = findByTitle(readTree(backlogResult).get("items"), "복원 테스트 스토리")
                .get("id").asLong();

        // Sprint에 배정
        MvcResult sprintResult = mockMvc.perform(post("/api/projects/" + projectId + "/sprints")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Sprint 1\",\"startDate\":\"2026-01-01\","
                                + "\"endDate\":\"2026-01-14\"}"))
                .andReturn();
        long sprintId = readTree(sprintResult).get("sprints").get(0).get("id").asLong();
        mockMvc.perform(post("/api/projects/" + projectId + "/sprints/" + sprintId + "/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"backlogItemId\":" + backlogItemId + "}"));

        JsonNode commit = postCommit(projectId, "복원용 커밋");
        long commitId = commit.get("id").asLong();
        int version = commit.get("version").asInt();

        MvcResult restoreResult = mockMvc.perform(post("/api/projects/commits/" + commitId + "/restore"))
                .andExpect(status().is2xxSuccessful())
                .andReturn();
        JsonNode restored = readTree(restoreResult);
        long newProjectId = restored.get("id").asLong();
        assertThat(newProjectId).isNotEqualTo(projectId);
        assertThat(restored.get("name").asText())
                .as("지시서 4.5: '원본명 (v3 복원)' 형태의 접미사가 붙어야 한다")
                .contains("v" + version)
                .contains("복원");

        // WBS: 같은 구조, 다른 id
        MvcResult newWbs = mockMvc.perform(get("/api/projects/" + newProjectId + "/wbs")).andReturn();
        JsonNode newTree = readTree(newWbs);
        long newRootId = findByName(newTree.get("nodes"), "루트").get("id").asLong();
        assertThat(newRootId).isNotEqualTo(rootId);
        long newWpAId = findChild(newTree, newRootId, "WP-A");
        long newWpBId = findChild(newTree, newRootId, "WP-B");
        assertThat(newWpAId).isNotEqualTo(wpAId);
        assertThat(newWpBId).isNotEqualTo(wpBId);

        // 의존성이 새 id를 가리킨다
        MvcResult newGantt = mockMvc.perform(get("/api/projects/" + newProjectId + "/gantt")).andReturn();
        JsonNode dependencies = readTree(newGantt).get("dependencies");
        assertThat(dependencies).hasSize(1);
        JsonNode dependency = dependencies.get(0);
        assertThat(dependency.get("predecessorId").asLong()).isEqualTo(newWpAId);
        assertThat(dependency.get("successorId").asLong()).isEqualTo(newWpBId);
        assertThat(dependency.get("lagDays").asInt()).isEqualTo(2);

        // RACI: 새 구성원 id로 RESPONSIBLE이 새 WP-A에 배정되어 있다
        MvcResult newRaci = mockMvc.perform(get("/api/projects/" + newProjectId + "/raci")).andReturn();
        JsonNode raciBody = readTree(newRaci);
        long newMemberId = findByName(raciBody.get("members"), "김담당").get("id").asLong();
        assertThat(newMemberId).isNotEqualTo(memberId);
        boolean hasResponsibleCell = false;
        for (JsonNode cell : raciBody.get("cells")) {
            if (cell.get("wbsItemId").asLong() == newWpAId && cell.get("memberId").asLong() == newMemberId) {
                for (JsonNode role : cell.get("roles")) {
                    if ("RESPONSIBLE".equals(role.asText())) {
                        hasResponsibleCell = true;
                    }
                }
            }
        }
        assertThat(hasResponsibleCell)
                .as("복원된 프로젝트의 새 WP-A × 새 구성원 셀에 RESPONSIBLE이 있어야 한다")
                .isTrue();

        // RAID: 새 WP-A를 가리킨다
        MvcResult newRaid = mockMvc.perform(get("/api/projects/" + newProjectId + "/raid")).andReturn();
        JsonNode raidItems = readTree(newRaid).get("items");
        JsonNode restoredRisk = findByTitle(raidItems, "복원 테스트 위험");
        boolean linksToNewWpA = false;
        for (JsonNode link : restoredRisk.get("links")) {
            if ("WBS_ITEM".equals(link.get("targetType").asText()) && link.get("targetId").asLong() == newWpAId) {
                linksToNewWpA = true;
            }
        }
        assertThat(linksToNewWpA).isTrue();

        // Backlog: 새 WP-A를 가리키고, id는 다르다
        MvcResult newBacklog = mockMvc.perform(get("/api/projects/" + newProjectId + "/backlog")).andReturn();
        JsonNode newBacklogItem = findByTitle(readTree(newBacklog).get("items"), "복원 테스트 스토리");
        long newBacklogItemId = newBacklogItem.get("id").asLong();
        assertThat(newBacklogItemId).isNotEqualTo(backlogItemId);
        assertThat(newBacklogItem.get("wbsItemId").asLong()).isEqualTo(newWpAId);

        // Sprint: 새 Backlog 항목 id로 배정되어 있다
        MvcResult newSprints = mockMvc.perform(get("/api/projects/" + newProjectId + "/sprints")).andReturn();
        JsonNode newSprintList = readTree(newSprints).get("sprints");
        assertThat(newSprintList).hasSize(1);
        boolean assigned = false;
        for (JsonNode item : newSprintList.get(0).get("items")) {
            if (item.get("backlogItemId").asLong() == newBacklogItemId) {
                assigned = true;
            }
        }
        assertThat(assigned).isTrue();

        // 원본 프로젝트는 그대로 남아 있다 (id도, 데이터도)
        MvcResult originalStillThere = mockMvc.perform(get("/api/projects/" + projectId)).andReturn();
        assertThat(originalStillThere.getResponse().getStatus()).isEqualTo(200);
        MvcResult originalWbsStillThere = mockMvc.perform(get("/api/projects/" + projectId + "/wbs")).andReturn();
        long stillRootId = findByName(readTree(originalWbsStillThere).get("nodes"), "루트").get("id").asLong();
        assertThat(stillRootId).isEqualTo(rootId);
    }

    private long findChild(JsonNode tree, long parentId, String name) {
        JsonNode root = null;
        for (JsonNode node : tree.get("nodes")) {
            if (node.get("id").asLong() == parentId) {
                root = node;
            }
        }
        assertThat(root).as("parent " + parentId + " must be present in " + tree).isNotNull();
        for (JsonNode child : root.get("children")) {
            if (child.get("name").asText().equals(name)) {
                return child.get("id").asLong();
            }
        }
        throw new IllegalStateException("no child named " + name + " under " + parentId);
    }

    private JsonNode findByTitle(JsonNode items, String title) {
        List<String> titles = new ArrayList<>();
        for (JsonNode item : items) {
            titles.add(item.get("title") != null ? item.get("title").asText() : item.get("name").asText());
            String candidate = item.has("title") ? item.get("title").asText()
                    : item.has("name") ? item.get("name").asText() : null;
            if (title.equals(candidate)) {
                return item;
            }
        }
        throw new IllegalStateException("no item titled " + title + ", had " + titles);
    }

    private static org.springframework.test.web.servlet.ResultMatcher status2xx() {
        return status().is2xxSuccessful();
    }
}
