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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Section D of docs/tasks/wbs-description-and-action-link.md: the Action Item URL a WBS entry can
 * point at.
 *
 * <p>Driven through the HTTP API rather than the service, because the two things that matter most
 * here cannot be seen from a service test at all. Whether an unsafe address is refused with 400
 * instead of 500 depends on bean validation actually running and on
 * {@code GlobalExceptionHandler} answering, and whether the column exists depends on {@code V24}
 * — booting the app on H2 is the only check that migration gets.
 *
 * <p>This value is the first place in the app where user input reaches an {@code <a href>}, so the
 * refusals are as much the subject of these tests as the happy path.
 *
 * <p>{@code description}'s length guard is tested here too rather than in a file of its own: it is
 * the same guard, in the same validation phase, on the column right beside this one — the only
 * reason it was missing is that nobody could type 2000 characters into a one-line input before the
 * form's box became a {@code <textarea>}.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:wbs-action-item-url-test;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        })
@AutoConfigureMockMvc
@ActiveProfiles("desktop")
@DisplayName("WBS 항목의 Action Item 주소 — API 계약")
class WbsActionItemUrlApiTest {

    private static final String SAFE_URL = "https://example.com/meeting/2026-09-30?tab=notes";
    private static final String SCRIPT_URL = "javascript:alert(1)";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Nested
    @DisplayName("저장과 조회")
    class Storing {

        @Test
        @DisplayName("생성할 때 넣은 주소가 트리 응답에 그대로 실린다")
        void keepsUrlGivenOnCreate() throws Exception {
            long projectId = createProject("주소 생성");
            createItem(projectId, "설계 회의록", SAFE_URL);

            assertThat(find(getTree(projectId), "설계 회의록").get("actionItemUrl").asText())
                    .isEqualTo(SAFE_URL);
        }

        @Test
        @DisplayName("수정으로 주소를 새로 넣거나 바꿀 수 있다")
        void acceptsUrlOnUpdate() throws Exception {
            long projectId = createProject("주소 수정");
            long itemId = createItem(projectId, "티켓", null);
            assertThat(find(getTree(projectId), "티켓").get("actionItemUrl").isNull()).isTrue();

            update(projectId, itemId, "티켓", "\"" + SAFE_URL + "\"");

            assertThat(find(getTree(projectId), "티켓").get("actionItemUrl").asText())
                    .isEqualTo(SAFE_URL);
        }

        @Test
        @DisplayName("빈 문자열은 미입력과 같은 뜻이라 null로 눕는다 — '없음'의 표현은 하나여야 한다")
        void normalisesBlankToNull() throws Exception {
            long projectId = createProject("주소 지우기");
            long itemId = createItem(projectId, "산출물", SAFE_URL);

            // 폼이 칸을 비우면 null이 아니라 ""가 온다. 그것을 그대로 저장하면 화면이 "없음"을
            // 두 가지 값으로 구분해야 한다.
            update(projectId, itemId, "산출물", "\"\"");

            assertThat(find(getTree(projectId), "산출물").get("actionItemUrl").isNull()).isTrue();
        }

        @Test
        @DisplayName("이름만 고쳐 저장해도 주소가 사라지지 않는다 — 폼이 값을 함께 돌려보내는 한")
        void survivesRenameThatCarriesTheValueBack() throws Exception {
            long projectId = createProject("이름만 수정");
            long itemId = createItem(projectId, "옛 이름", SAFE_URL);

            update(projectId, itemId, "새 이름", "\"" + SAFE_URL + "\"");

            assertThat(find(getTree(projectId), "새 이름").get("actionItemUrl").asText())
                    .isEqualTo(SAFE_URL);
        }
    }

    @Nested
    @DisplayName("거부")
    class Refusing {

        @Test
        @DisplayName("javascript: 주소는 생성에서 400으로 거부되고 필드 이름이 함께 온다")
        void refusesScriptUrlOnCreate() throws Exception {
            long projectId = createProject("스크립트 생성");

            MvcResult result = mockMvc.perform(post("/api/projects/" + projectId + "/wbs")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(itemBody("악성", "\"" + SCRIPT_URL + "\"")))
                    .andReturn();

            assertThat(result.getResponse().getStatus()).isEqualTo(400);
            assertThat(readTree(result).get("message").asText())
                    .contains("actionItemUrl")
                    .contains("http://");
            // 거부된 요청이 항목만 만들어 놓고 끝나면 안 된다.
            assertThat(getTree(projectId).get("nodes")).isEmpty();
        }

        @Test
        @DisplayName("javascript: 주소는 수정에서도 400이고 저장된 값은 그대로다")
        void refusesScriptUrlOnUpdate() throws Exception {
            long projectId = createProject("스크립트 수정");
            long itemId = createItem(projectId, "정상", SAFE_URL);

            MvcResult result = mockMvc.perform(put("/api/projects/" + projectId + "/wbs/" + itemId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(itemBody("정상", "\"" + SCRIPT_URL + "\"")))
                    .andReturn();

            assertThat(result.getResponse().getStatus()).isEqualTo(400);
            assertThat(find(getTree(projectId), "정상").get("actionItemUrl").asText())
                    .isEqualTo(SAFE_URL);
        }

        @Test
        @DisplayName("공백만 있는 주소는 400이다 — ^$는 정말 빈 문자열만 통과시킨다")
        void refusesWhitespaceOnlyUrl() throws Exception {
            // 여기가 가져오기와 갈리는 유일한 입력이다. ImportService는 공백만 있는 값을 미입력과
            // 같이 보고 null로 눕히는데(손으로 편집한 파일에서 흔하다), 요청 경로는 @Pattern의
            // ^$가 정확히 빈 문자열만 받으므로 거부한다. 폼은 빈 칸을 ""로 보내므로 화면에서는
            // 닿지 않는 경계이고, 정규식이 프론트와 공유하는 계약이라 넓히지 않았다.
            long projectId = createProject("공백 주소");

            MvcResult result = mockMvc.perform(post("/api/projects/" + projectId + "/wbs")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(itemBody("공백", "\"   \"")))
                    .andReturn();

            assertThat(result.getResponse().getStatus()).isEqualTo(400);
        }
    }

    @Nested
    @DisplayName("내보내기·가져오기")
    class RoundTrip {

        @Test
        @DisplayName("내보낸 파일에 주소가 실리고 다시 가져오면 살아 온다")
        void survivesExportAndImport() throws Exception {
            long projectId = createProject("왕복");
            createItem(projectId, "회의록", SAFE_URL);
            createItem(projectId, "주소 없음", null);

            JsonNode file = export(projectId);
            assertThat(file.get("formatVersion").asInt())
                    .as("actionItemUrl이 실리는 형식은 8이다")
                    .isEqualTo(8);
            assertThat(wbsItem(file, "회의록").get("actionItemUrl").asText()).isEqualTo(SAFE_URL);
            assertThat(wbsItem(file, "주소 없음").get("actionItemUrl").isNull()).isTrue();

            long copyId = importFile(file, 201);

            JsonNode copied = getTree(copyId);
            assertThat(find(copied, "회의록").get("actionItemUrl").asText()).isEqualTo(SAFE_URL);
            assertThat(find(copied, "주소 없음").get("actionItemUrl").isNull()).isTrue();
        }

        @Test
        @DisplayName("손으로 고친 javascript: 주소는 400이고 프로젝트가 만들어지지 않는다")
        void refusesScriptUrlInFile() throws Exception {
            long projectId = createProject("손편집");
            createItem(projectId, "회의록", SAFE_URL);

            // 손으로 편집한 파일은 요청 DTO의 bean validation을 타지 않는다 — 검증 단계가
            // 삽입보다 앞에 있어야 거부된 파일이 아무것도 만들지 않는다.
            JsonNode file = export(projectId);
            ((ObjectNode) wbsItem(file, "회의록")).put("actionItemUrl", SCRIPT_URL);

            int before = projectCount();
            MvcResult result = mockMvc.perform(post("/api/projects/import")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(file)))
                    .andReturn();

            assertThat(result.getResponse().getStatus()).isEqualTo(400);
            assertThat(readTree(result).get("message").asText()).contains("회의록");
            assertThat(projectCount()).isEqualTo(before);
        }

        @Test
        @DisplayName("2000자를 넘는 주소는 400으로 항목을 지목한다 — 컬럼 제약이 500으로 터지기 전에")
        void refusesOverlongUrlWithoutHittingTheColumn() throws Exception {
            // 이 검증을 "단순화"해서 지우면 VARCHAR(2000) 제약이 대신 터지고, 사용자는 어느
            // 항목이 문제인지 모르는 맨 500을 받는다 — CLAUDE.md가 가져오기에서 막다른 길이라
            // 부르는 바로 그 실패다. 이 테스트는 H2에서 도므로 컬럼 제약이 실재한다.
            long projectId = createProject("긴 주소");
            createItem(projectId, "회의록", SAFE_URL);

            String tooLong = "https://example.com/" + "a".repeat(2001 - "https://example.com/".length());
            assertThat(tooLong).hasSize(2001);

            JsonNode file = export(projectId);
            ((ObjectNode) wbsItem(file, "회의록")).put("actionItemUrl", tooLong);

            MvcResult result = mockMvc.perform(post("/api/projects/import")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(file)))
                    .andReturn();

            assertThat(result.getResponse().getStatus()).isEqualTo(400);
            assertThat(readTree(result).get("message").asText()).contains("회의록");
        }

        @Test
        @DisplayName("주소 절이 없는 구형 파일(formatVersion 7)도 거부하지 않는다")
        void acceptsOlderFileWithoutTheField() throws Exception {
            long projectId = createProject("구형 파일");
            createItem(projectId, "회의록", SAFE_URL);

            JsonNode file = export(projectId);
            ((ObjectNode) file).put("formatVersion", 7);
            for (JsonNode item : file.get("wbsItems")) {
                ((ObjectNode) item).remove("actionItemUrl");
            }

            long copyId = importFile(file, 201);

            assertThat(find(getTree(copyId), "회의록").get("actionItemUrl").isNull()).isTrue();
        }
    }

    /**
     * The mirror of the URL length guard, on the free-text column next to it. Both boundaries are
     * asserted: the probe that found this showed 2000 already worked, so an off-by-one in either
     * guard would be invisible without the passing case beside the failing one.
     */
    @Nested
    @DisplayName("설명 길이 — Action Item 주소 길이 가드의 짝")
    class DescriptionLength {

        @Test
        @DisplayName("2000자는 저장되고, 2001자는 400이다 — 컬럼 제약의 맨 500이 아니라")
        void refusesOverlongDescriptionOnCreate() throws Exception {
            long projectId = createProject("긴 설명 생성");

            assertThat(createDescribed(projectId, "딱 맞음", "가".repeat(2000))).isEqualTo(201);
            assertThat(find(getTree(projectId), "딱 맞음").get("description").asText())
                    .hasSize(2000);

            assertThat(createDescribed(projectId, "한 글자 초과", "가".repeat(2001)))
                    .isEqualTo(400);
        }

        @Test
        @DisplayName("수정에서도 2001자는 400이고 저장된 설명은 그대로다")
        void refusesOverlongDescriptionOnUpdate() throws Exception {
            long projectId = createProject("긴 설명 수정");
            createDescribed(projectId, "정상", "짧은 설명");
            long itemId = find(getTree(projectId), "정상").get("id").asLong();

            MvcResult result = mockMvc.perform(put("/api/projects/" + projectId + "/wbs/" + itemId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(describedBody("정상", "가".repeat(2001))))
                    .andReturn();

            assertThat(result.getResponse().getStatus()).isEqualTo(400);
            assertThat(find(getTree(projectId), "정상").get("description").asText())
                    .isEqualTo("짧은 설명");
        }

        @Test
        @DisplayName("가져오기에서도 2000자는 들어오고 2001자는 400으로 항목을 지목한다")
        void guardsDescriptionOnImport() throws Exception {
            long projectId = createProject("긴 설명 가져오기");
            createDescribed(projectId, "회의록", "가".repeat(2000));

            // 2000자 그대로 왕복한다 — 가드가 경계를 한 칸 당기지 않았다.
            JsonNode file = export(projectId);
            long copyId = importFile(file, 201);
            assertThat(find(getTree(copyId), "회의록").get("description").asText()).hasSize(2000);

            // 손으로 늘린 파일은 컬럼에 닿기 전에 거부되고, 어느 항목인지 알려준다.
            ((ObjectNode) wbsItem(file, "회의록")).put("description", "가".repeat(2001));
            MvcResult result = mockMvc.perform(post("/api/projects/import")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(file)))
                    .andReturn();

            assertThat(result.getResponse().getStatus()).isEqualTo(400);
            assertThat(readTree(result).get("message").asText()).contains("회의록");
        }
    }

    // ---- fixture helpers -------------------------------------------------

    private int createDescribed(long projectId, String name, String description) throws Exception {
        return mockMvc.perform(post("/api/projects/" + projectId + "/wbs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(describedBody(name, description)))
                .andReturn().getResponse().getStatus();
    }

    private String describedBody(String name, String description) throws Exception {
        return "{\"name\":\"" + name + "\",\"nodeType\":\"WORK_PACKAGE\",\"description\":"
                + objectMapper.writeValueAsString(description) + "}";
    }

    private long createProject(String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andReturn();
        return readTree(result).get("id").asLong();
    }

    /** @param url already-quoted JSON, or {@code null} to omit the field entirely */
    private long createItem(long projectId, String name, String url) throws Exception {
        String body = itemBody(name, url == null ? null : "\"" + url + "\"");
        MvcResult result = mockMvc.perform(post("/api/projects/" + projectId + "/wbs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        return find(readTree(result), name).get("id").asLong();
    }

    private void update(long projectId, long itemId, String name, String jsonUrl) throws Exception {
        MvcResult result = mockMvc.perform(put("/api/projects/" + projectId + "/wbs/" + itemId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(itemBody(name, jsonUrl)))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
    }

    private String itemBody(String name, String jsonUrl) {
        return "{\"name\":\"" + name + "\",\"nodeType\":\"WORK_PACKAGE\""
                + (jsonUrl == null ? "" : ",\"actionItemUrl\":" + jsonUrl) + "}";
    }

    private JsonNode export(long projectId) throws Exception {
        return objectMapper.readTree(mockMvc.perform(get("/api/projects/" + projectId + "/export"))
                .andReturn().getResponse().getContentAsString());
    }

    private long importFile(JsonNode file, int expectedStatus) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/projects/import")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(file)))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(expectedStatus);
        return readTree(result).get("id").asLong();
    }

    private int projectCount() throws Exception {
        return readTree(mockMvc.perform(get("/api/projects")).andReturn()).size();
    }

    private JsonNode getTree(long projectId) throws Exception {
        return readTree(mockMvc.perform(get("/api/projects/" + projectId + "/wbs")).andReturn());
    }

    private JsonNode wbsItem(JsonNode file, String name) {
        for (JsonNode item : file.get("wbsItems")) {
            if (item.get("name").asText().equals(name)) {
                return item;
            }
        }
        throw new IllegalStateException("exported item not found: " + name);
    }

    private JsonNode find(JsonNode tree, String name) {
        for (JsonNode node : tree.get("nodes")) {
            if (node.get("name").asText().equals(name)) {
                return node;
            }
        }
        throw new IllegalStateException("node not found: " + name + " in " + tree);
    }

    private JsonNode readTree(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
