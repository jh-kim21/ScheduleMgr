package com.projectflow.presentation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Phase C·D of docs/tasks/wbs-tree-improvements.md, driven through the HTTP API: 담당자 on the WBS
 * tree, and 업무 분야 tags.
 *
 * <p>Through the API on purpose. The service-level tests next door use in-memory fakes, and two
 * of the rules here cannot be seen from there at all: whether a refusal comes back as 400 rather
 * than 500 depends on {@code GlobalExceptionHandler} knowing the new exception (지시서 부록 함정 9),
 * and whether {@code /wbs/tags} collides with {@code /wbs/{itemId}} depends on request mapping.
 * Booting the app also runs {@code V23} on H2, which is the only check the migration gets.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:wbs-tree-annotation-test;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        })
@AutoConfigureMockMvc
@ActiveProfiles("desktop")
@DisplayName("WBS 트리의 담당자와 분야 — API 계약")
class WbsTreeAnnotationApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // ---- Phase C: 담당자 -------------------------------------------------

    @Nested
    @DisplayName("담당자 (RACI의 Responsible 재사용)")
    class Responsible {

        @Test
        @DisplayName("트리의 담당자가 RACI 매트릭스와 같은 답을 낸다 — 상속 포함")
        void matchesRaciMatrixIncludingInheritance() throws Exception {
            long projectId = createProject("담당자 일치");
            long stage = createItem(projectId, null, "설계", "SUMMARY");
            long own = createItem(projectId, stage, "화면 설계", "WORK_PACKAGE");
            long inherits = createItem(projectId, stage, "DB 설계", "WORK_PACKAGE");

            long lead = createMember(projectId, "김재학");
            long dev = createMember(projectId, "이승하");
            assign(projectId, stage, lead, "RESPONSIBLE");
            // 단계의 A는 담당자가 아니다 — 트리에 실리면 안 된다.
            assign(projectId, stage, lead, "ACCOUNTABLE");
            assign(projectId, own, dev, "RESPONSIBLE");

            JsonNode tree = getTree(projectId);
            JsonNode stageNode = find(tree, "설계");
            JsonNode ownNode = find(tree, "화면 설계");
            JsonNode inheritsNode = find(tree, "DB 설계");

            assertThat(memberIds(stageNode.get("responsible"))).containsExactly(lead);
            assertThat(stageNode.get("responsibleInherited")).isEmpty();

            // 자기 글자가 있으면 그것이 유효한 답이다 — 상속은 비어 있다.
            assertThat(memberIds(ownNode.get("responsible"))).containsExactly(dev);
            assertThat(ownNode.get("responsibleInherited")).isEmpty();

            // 자기 글자가 없으면 상위 단계의 것을 물려받되, 자기 것과 섞이지 않게 따로 싣는다.
            assertThat(ownNode.get("responsible")).isNotEmpty();
            assertThat(inheritsNode.get("responsible")).isEmpty();
            assertThat(memberIds(inheritsNode.get("responsibleInherited"))).containsExactly(lead);

            // 같은 함수(RaciInheritance)를 쓰는지 — 매트릭스가 말하는 유효 R과 행별로 대조한다.
            JsonNode matrix = readTree(mockMvc.perform(
                    get("/api/projects/" + projectId + "/raci")).andReturn());
            for (long itemId : List.of(stage, own, inherits)) {
                assertThat(effectiveResponsibleFromTree(tree, itemId))
                        .as("WBS 트리와 RACI 매트릭스가 같은 담당자를 말해야 한다: item=" + itemId)
                        .containsExactlyInAnyOrderElementsOf(
                                effectiveResponsibleFromMatrix(matrix, itemId));
            }
        }

        @Test
        @DisplayName("아무도 배정되지 않으면 null이 아니라 빈 배열이다")
        void emptyRatherThanNull() throws Exception {
            long projectId = createProject("빈 담당자");
            createItem(projectId, null, "혼자", "WORK_PACKAGE");

            JsonNode node = find(getTree(projectId), "혼자");
            assertThat(node.get("responsible").isArray()).isTrue();
            assertThat(node.get("responsible")).isEmpty();
            assertThat(node.get("responsibleInherited").isArray()).isTrue();
            assertThat(node.get("responsibleInherited")).isEmpty();
        }
    }

    // ---- Phase D: 분야 ---------------------------------------------------

    @Nested
    @DisplayName("분야 태그")
    class Tags {

        @Test
        @DisplayName("같은 이름의 태그를 두 번 만들면 400이다 (UNIQUE 위반의 500이 아니다)")
        void duplicateNameIsRejectedWith400() throws Exception {
            long projectId = createProject("중복 이름");
            createTag(projectId, "Service");

            mockMvc.perform(post("/api/projects/" + projectId + "/wbs/tags")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"service\"}"))
                    .andExpect(result -> assertThat(result.getResponse().getStatus())
                            .as("대소문자만 다른 이름도 같은 이름이다")
                            .isEqualTo(400));
        }

        @Test
        @DisplayName("다른 프로젝트의 태그 id를 붙이면 400이다")
        void rejectsTagFromAnotherProject() throws Exception {
            long mine = createProject("내 프로젝트");
            long theirs = createProject("남의 프로젝트");
            long foreignTag = createTag(theirs, "Web");
            long item = createItem(mine, null, "업무", "WORK_PACKAGE");

            MvcResult result = mockMvc.perform(put("/api/projects/" + mine + "/wbs/" + item)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"업무\",\"tagIds\":[" + foreignTag + "]}"))
                    .andReturn();

            assertThat(result.getResponse().getStatus()).isEqualTo(400);
            assertThat(readTree(result).get("message").asText()).contains("분야");
        }

        @Test
        @DisplayName("tagIds를 보내지 않으면 기존 태그가 그대로 남는다 (null = 변경 없음)")
        void omittedTagIdsLeaveTagsAlone() throws Exception {
            long projectId = createProject("보존");
            long tag = createTag(projectId, "Service");
            long item = createItem(projectId, null, "업무", "WORK_PACKAGE");
            setTags(projectId, item, List.of(tag));

            // 이 필드를 모르는 호출자의 저장 — 태그가 조용히 지워지면 안 된다 (커밋 da96ebe).
            mockMvc.perform(put("/api/projects/" + projectId + "/wbs/" + item)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"이름만 바꾼다\"}"));

            assertThat(tagIds(find(getTree(projectId), "이름만 바꾼다").get("tags")))
                    .containsExactly(tag);
        }

        @Test
        @DisplayName("빈 배열을 보내면 전부 해제한다")
        void emptyTagIdsClearsThem() throws Exception {
            long projectId = createProject("전부 해제");
            long tag = createTag(projectId, "Service");
            long item = createItem(projectId, null, "업무", "WORK_PACKAGE");
            setTags(projectId, item, List.of(tag));

            setTags(projectId, item, List.of());

            assertThat(find(getTree(projectId), "업무").get("tags")).isEmpty();
        }

        @Test
        @DisplayName("Summary의 태그는 바꿀 수 없지만, 보관값을 그대로 되보내면 통과한다")
        void summaryTagsAreRetainedButNotChangeable() throws Exception {
            long projectId = createProject("Summary 보관");
            long service = createTag(projectId, "Service");
            long web = createTag(projectId, "Web");
            long item = createItem(projectId, null, "전환될 업무", "WORK_PACKAGE");
            setTags(projectId, item, List.of(service));

            // Work Package → Summary 로 전환. 폼은 보관값을 그대로 되돌려 보낸다.
            MvcResult converted = mockMvc.perform(put("/api/projects/" + projectId + "/wbs/" + item)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"전환될 업무\",\"nodeType\":\"SUMMARY\",\"tagIds\":["
                                    + service + "]}"))
                    .andReturn();
            assertThat(converted.getResponse().getStatus())
                    .as("보관값과 같은 집합이면 변경이 아니다 — 거부하면 폼이 저장될 수 없다")
                    .isEqualTo(200);
            assertThat(tagIds(find(getTree(projectId), "전환될 업무").get("tags")))
                    .containsExactly(service);

            MvcResult changed = mockMvc.perform(put("/api/projects/" + projectId + "/wbs/" + item)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"전환될 업무\",\"nodeType\":\"SUMMARY\",\"tagIds\":["
                                    + web + "]}"))
                    .andReturn();
            assertThat(changed.getResponse().getStatus()).isEqualTo(400);
            // 거부되었으니 보관값은 그대로다.
            assertThat(tagIds(find(getTree(projectId), "전환될 업무").get("tags")))
                    .containsExactly(service);
        }

        @Test
        @DisplayName("상위 행은 하위(손자 포함)의 분야를 합집합으로 요약하고, 자식이 없으면 null이다")
        void summaryRowsAggregateTagsFromBelow() throws Exception {
            long projectId = createProject("요약");
            long service = createTag(projectId, "Service");
            long web = createTag(projectId, "Web");

            long top = createItem(projectId, null, "1단계", "SUMMARY");
            long mid = createItem(projectId, top, "1.1단계", "SUMMARY");
            long leafA = createItem(projectId, mid, "손자 A", "WORK_PACKAGE");
            long leafB = createItem(projectId, top, "자식 B", "WORK_PACKAGE");
            setTags(projectId, leafA, List.of(service));
            setTags(projectId, leafB, List.of(web));

            JsonNode tree = getTree(projectId);
            assertThat(tagIds(find(tree, "1단계").get("tagSummary")))
                    .as("손자까지 센다")
                    .containsExactlyInAnyOrder(service, web);
            assertThat(tagIds(find(tree, "1.1단계").get("tagSummary"))).containsExactly(service);
            // ExecutionModeSummary와 같은 규칙 — 자식이 없으면 요약할 것이 없다.
            assertThat(find(tree, "손자 A").get("tagSummary").isNull()).isTrue();
            assertThat(tagIds(find(tree, "손자 A").get("tags"))).containsExactly(service);
        }

        @Test
        @DisplayName("태그를 지우면 연결만 사라지고 WBS 항목은 남는다")
        void deletingTagKeepsTheWbsItem() throws Exception {
            long projectId = createProject("태그 삭제");
            long tag = createTag(projectId, "Service");
            long item = createItem(projectId, null, "업무", "WORK_PACKAGE");
            setTags(projectId, item, List.of(tag));

            mockMvc.perform(delete("/api/projects/" + projectId + "/wbs/tags/" + tag))
                    .andReturn();

            JsonNode node = find(getTree(projectId), "업무");
            assertThat(node.get("id").asLong()).isEqualTo(item);
            assertThat(node.get("tags")).isEmpty();
        }

        @Test
        @DisplayName("태그 마스터 경로가 WBS 항목 경로와 충돌하지 않는다")
        void tagRoutesDoNotCollideWithItemRoutes() throws Exception {
            long projectId = createProject("경로");
            long tag = createTag(projectId, "Service");

            MvcResult renamed = mockMvc.perform(put("/api/projects/" + projectId + "/wbs/tags/" + tag)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"Backend\",\"color\":\"#123456\"}"))
                    .andReturn();
            assertThat(renamed.getResponse().getStatus()).isEqualTo(200);

            JsonNode list = readTree(mockMvc.perform(
                    get("/api/projects/" + projectId + "/wbs/tags")).andReturn());
            assertThat(list).hasSize(1);
            assertThat(list.get(0).get("name").asText()).isEqualTo("Backend");
            assertThat(list.get(0).get("color").asText()).isEqualTo("#123456");
        }
    }

    // ---- 내보내기 → 가져오기 왕복 -------------------------------------------

    @Test
    @DisplayName("내보내기 → 가져오기 왕복에서 분야가 살아남고 id가 다시 매겨진다")
    void tagsSurviveExportImportWithRemappedIds() throws Exception {
        long projectId = createProject("왕복 원본");
        long service = createTag(projectId, "Service");
        long web = createTag(projectId, "Web");
        long item = createItem(projectId, null, "업무", "WORK_PACKAGE");
        setTags(projectId, item, List.of(service, web));

        String file = mockMvc.perform(get("/api/projects/" + projectId + "/export"))
                .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(file).get("formatVersion").asInt())
                .as("분야가 실리는 형식은 7이다")
                .isEqualTo(7);

        MvcResult imported = mockMvc.perform(post("/api/projects/import")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(file))
                .andReturn();
        long copyId = readTree(imported).get("id").asLong();
        assertThat(copyId).isNotEqualTo(projectId);

        JsonNode copiedTags = readTree(mockMvc.perform(
                get("/api/projects/" + copyId + "/wbs/tags")).andReturn());
        assertThat(copiedTags).hasSize(2);
        List<Long> copiedIds = new ArrayList<>();
        for (JsonNode tag : copiedTags) {
            copiedIds.add(tag.get("id").asLong());
        }
        assertThat(copiedIds)
                .as("파일의 id는 그것을 만든 설치본의 것이다 — 그대로 쓰면 안 된다")
                .doesNotContain(service, web);

        // 항목의 연결도 새 id를 가리켜야 한다.
        JsonNode copiedItem = find(getTree(copyId), "업무");
        assertThat(tagIds(copiedItem.get("tags")))
                .containsExactlyInAnyOrderElementsOf(copiedIds);
        assertThat(names(copiedItem.get("tags")))
                .containsExactlyInAnyOrder("Service", "Web");
    }

    @Test
    @DisplayName("분야가 없는 구형 파일(formatVersion 6)도 거부하지 않는다")
    void acceptsOlderFilesWithoutTags() throws Exception {
        long projectId = createProject("구형 파일 원본");
        createItem(projectId, null, "업무", "WORK_PACKAGE");

        JsonNode file = objectMapper.readTree(
                mockMvc.perform(get("/api/projects/" + projectId + "/export"))
                        .andReturn().getResponse().getContentAsString());
        // formatVersion 7 이전의 파일을 흉내 낸다 — 절 자체가 없다.
        ((com.fasterxml.jackson.databind.node.ObjectNode) file).put("formatVersion", 6);
        ((com.fasterxml.jackson.databind.node.ObjectNode) file).remove("tags");

        MvcResult imported = mockMvc.perform(post("/api/projects/import")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(file)))
                .andReturn();

        assertThat(imported.getResponse().getStatus()).isEqualTo(201);
        long copyId = readTree(imported).get("id").asLong();
        assertThat(readTree(mockMvc.perform(
                get("/api/projects/" + copyId + "/wbs/tags")).andReturn())).isEmpty();
    }

    // ---- fixture helpers -------------------------------------------------

    private long createProject(String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andReturn();
        return readTree(result).get("id").asLong();
    }

    private long createItem(long projectId, Long parentId, String name, String nodeType)
            throws Exception {
        String body = "{\"name\":\"" + name + "\",\"nodeType\":\"" + nodeType + "\""
                + (parentId == null ? "" : ",\"parentId\":" + parentId) + "}";
        MvcResult result = mockMvc.perform(post("/api/projects/" + projectId + "/wbs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn();
        return find(readTree(result), name).get("id").asLong();
    }

    private long createMember(long projectId, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/projects/" + projectId + "/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andReturn();
        return readTree(result).get("id").asLong();
    }

    private void assign(long projectId, long wbsItemId, long memberId, String role)
            throws Exception {
        mockMvc.perform(post("/api/projects/" + projectId + "/raci/assignments")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"wbsItemId\":" + wbsItemId + ",\"memberId\":" + memberId
                        + ",\"role\":\"" + role + "\"}"));
    }

    private long createTag(long projectId, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/projects/" + projectId + "/wbs/tags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andReturn();
        for (JsonNode tag : readTree(result)) {
            if (tag.get("name").asText().equals(name)) {
                return tag.get("id").asLong();
            }
        }
        throw new IllegalStateException("created tag not found: "
                + result.getResponse().getContentAsString());
    }

    /** {@code PUT /wbs/{itemId}} with an explicit {@code tagIds}, as the form always sends. */
    private void setTags(long projectId, long itemId, List<Long> tagIds) throws Exception {
        String name = find(getTree(projectId), itemId).get("name").asText();
        mockMvc.perform(put("/api/projects/" + projectId + "/wbs/" + itemId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"tagIds\":" + tagIds + "}"))
                .andReturn();
    }

    private JsonNode getTree(long projectId) throws Exception {
        return readTree(mockMvc.perform(get("/api/projects/" + projectId + "/wbs")).andReturn());
    }

    private JsonNode find(JsonNode tree, String name) {
        JsonNode found = search(tree.get("nodes"), node -> node.get("name").asText().equals(name));
        if (found == null) {
            throw new IllegalStateException("node not found: " + name + " in " + tree);
        }
        return found;
    }

    private JsonNode find(JsonNode tree, long id) {
        JsonNode found = search(tree.get("nodes"), node -> node.get("id").asLong() == id);
        if (found == null) {
            throw new IllegalStateException("node not found: " + id);
        }
        return found;
    }

    private JsonNode search(JsonNode nodes, java.util.function.Predicate<JsonNode> matches) {
        for (JsonNode node : nodes) {
            if (matches.test(node)) {
                return node;
            }
            JsonNode inChildren = search(node.get("children"), matches);
            if (inChildren != null) {
                return inChildren;
            }
        }
        return null;
    }

    private List<Long> memberIds(JsonNode refs) {
        List<Long> ids = new ArrayList<>();
        for (JsonNode ref : refs) {
            ids.add(ref.get("memberId").asLong());
        }
        return ids;
    }

    private List<Long> tagIds(JsonNode refs) {
        List<Long> ids = new ArrayList<>();
        for (JsonNode ref : refs) {
            ids.add(ref.get("id").asLong());
        }
        return ids;
    }

    private List<String> names(JsonNode refs) {
        List<String> names = new ArrayList<>();
        for (JsonNode ref : refs) {
            names.add(ref.get("name").asText());
        }
        return names;
    }

    /** Everyone the tree says is responsible for a row, own and inherited together. */
    private List<Long> effectiveResponsibleFromTree(JsonNode tree, long itemId) {
        JsonNode node = find(tree, itemId);
        List<Long> ids = new ArrayList<>(memberIds(node.get("responsible")));
        ids.addAll(memberIds(node.get("responsibleInherited")));
        return ids;
    }

    /**
     * The same question asked of the RACI matrix: a row's own R if it has one, otherwise the
     * inherited R that is not struck through as overridden.
     */
    private List<Long> effectiveResponsibleFromMatrix(JsonNode matrix, long itemId) {
        List<Long> own = new ArrayList<>();
        List<Long> inherited = new ArrayList<>();
        for (JsonNode cell : matrix.get("cells")) {
            if (cell.get("wbsItemId").asLong() != itemId) {
                continue;
            }
            long memberId = cell.get("memberId").asLong();
            for (JsonNode role : cell.get("roles")) {
                if (role.asText().equals("RESPONSIBLE")) {
                    own.add(memberId);
                }
            }
            for (JsonNode role : cell.get("inherited")) {
                if (role.get("role").asText().equals("RESPONSIBLE")
                        && !role.get("overridden").asBoolean()) {
                    inherited.add(memberId);
                }
            }
        }
        return own.isEmpty() ? inherited : own;
    }

    private JsonNode readTree(MvcResult result) throws Exception {
        String body = result.getResponse().getContentAsString();
        return body.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(body);
    }
}
