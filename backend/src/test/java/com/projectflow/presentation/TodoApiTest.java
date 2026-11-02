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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:todo-api-test;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        })
@AutoConfigureMockMvc
@ActiveProfiles("desktop")
@DisplayName("TODO - API 계약")
class TodoApiTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("존재하지 않는 TODO 수정·완료·삭제는 404다 (500이 아님)")
    void missingTodoIs404() throws Exception {
        long p = project("404");
        assertThat(status(send(put(base(p) + "/999999"), body("x", null)))).isEqualTo(404);
        assertThat(status(send(put(base(p) + "/999999/completion"), "{\"completed\":true}"))).isEqualTo(404);
        assertThat(status(mockMvc.perform(delete(base(p) + "/999999")).andReturn())).isEqualTo(404);
    }

    @Test
    @DisplayName("다른 프로젝트의 TODO를 수정하면 404다")
    void otherProjectsTodoIs404() throws Exception {
        long a = project("A");
        long b = project("B");
        long id = createTodo(a, "x", null);
        assertThat(status(send(put(base(b) + "/" + id), body("y", null)))).isEqualTo(404);
    }

    @Test
    @DisplayName("제목이 비었거나 공백이면 400이다")
    void blankTitleIs400() throws Exception {
        long p = project("blank");
        assertThat(status(send(post(base(p)), body("", null)))).isEqualTo(400);
        assertThat(status(send(post(base(p)), body("   ", null)))).isEqualTo(400);
        assertThat(list(p).get("items")).isEmpty();
    }

    @Test
    @DisplayName("우선순위가 없거나 모르는 값이면 400이다")
    void badPriorityIs400() throws Exception {
        long p = project("prio");
        assertThat(status(send(post(base(p)), "{\"title\":\"x\"}"))).isEqualTo(400);
        assertThat(status(send(post(base(p)), "{\"title\":\"x\",\"priority\":\"URGENT\"}"))).isEqualTo(400);
    }

    @Test
    @DisplayName("제목 255자는 저장되고 256자와 메모 2001자는 400이다")
    void lengthBoundaries() throws Exception {
        long p = project("len");
        assertThat(status(send(post(base(p)), body("a".repeat(255), null)))).isEqualTo(201);
        assertThat(status(send(post(base(p)), body("a".repeat(256), null)))).isEqualTo(400);
        String longMemo = "{\"title\":\"x\",\"priority\":\"LOW\",\"memo\":\"" + "m".repeat(2001) + "\"}";
        assertThat(status(send(post(base(p)), longMemo))).isEqualTo(400);
    }

    @Test
    @DisplayName("다른 프로젝트 구성원을 담당자로 지정하면 400이다")
    void foreignAssigneeIs400() throws Exception {
        long a = project("A-own");
        long b = project("B-own");
        long foreign = createMember(b, "외부");
        assertThat(status(send(post(base(a)), body("x", foreign)))).isEqualTo(400);
        assertThat(list(a).get("items")).isEmpty();
    }

    @Test
    @DisplayName("완료 토글은 왕복하고 메모·기한 등 다른 필드를 건드리지 않는다")
    void completionRoundTrip() throws Exception {
        long p = project("toggle");
        String create = "{\"title\":\"x\",\"priority\":\"HIGH\",\"dueDate\":\"2030-05-01\",\"memo\":\"메모\"}";
        assertThat(status(send(post(base(p)), create))).isEqualTo(201);
        long id = list(p).get("items").get(0).get("id").asLong();

        MvcResult done = send(put(base(p) + "/" + id + "/completion"), "{\"completed\":true}");
        assertThat(status(done)).isEqualTo(200);
        JsonNode item = read(done).get("items").get(0);
        assertThat(item.get("completed").asBoolean()).isTrue();
        assertThat(item.get("completedAt").isNull()).isFalse();
        assertThat(item.get("memo").asText()).isEqualTo("메모");
        assertThat(item.get("dueDate").asText()).isEqualTo("2030-05-01");
        assertThat(item.get("priority").asText()).isEqualTo("HIGH");

        JsonNode undone = read(send(put(base(p) + "/" + id + "/completion"), "{\"completed\":false}"))
                .get("items").get(0);
        assertThat(undone.get("completed").asBoolean()).isFalse();
        assertThat(undone.get("completedAt").isNull()).isTrue();
    }

    @Test
    @DisplayName("completed 값이 없는 완료 요청은 400이다")
    void completionRequiresValue() throws Exception {
        long p = project("noval");
        long id = createTodo(p, "x", null);
        assertThat(status(send(put(base(p) + "/" + id + "/completion"), "{}"))).isEqualTo(400);
    }

    @Test
    @DisplayName("응답에 필요한 필드가 실린다")
    void responseShape() throws Exception {
        long p = project("shape");
        createTodo(p, "x", null);
        JsonNode body = list(p);
        assertThat(body.has("referenceDate")).isTrue();
        JsonNode item = body.get("items").get(0);
        assertThat(item.has("completed")).isTrue();
        assertThat(item.has("overdue")).isTrue();
        assertThat(item.has("overdueDays")).isTrue();
        assertThat(item.has("assigneeName")).isTrue();
    }

    @Test
    @DisplayName("구성원을 삭제해도 TODO는 남고 담당자만 비워진다 (ON DELETE SET NULL)")
    void deletingMemberKeepsTodo() throws Exception {
        long p = project("setnull");
        long member = createMember(p, "김PM");
        long id = createTodo(p, "계약서 검토", member);
        assertThat(list(p).get("items").get(0).get("assigneeName").asText()).isEqualTo("김PM");

        MvcResult removed = mockMvc.perform(delete("/api/projects/" + p + "/members/" + member)).andReturn();
        assertThat(status(removed)).isEqualTo(204);

        JsonNode items = list(p).get("items");
        assertThat(items).hasSize(1);
        assertThat(items.get(0).get("id").asLong()).isEqualTo(id);
        assertThat(items.get(0).get("assigneeMemberId").isNull()).isTrue();
        assertThat(items.get(0).get("assigneeName").isNull()).isTrue();
    }

    @Test
    @DisplayName("프로젝트를 삭제해도 오류 없이 끝난다 (CASCADE)")
    void deletingProjectCascades() throws Exception {
        long p = project("cascade");
        createTodo(p, "x", null);
        assertThat(status(mockMvc.perform(delete("/api/projects/" + p)).andReturn())).isEqualTo(204);
    }

    // ---- helpers ----

    private static String base(long p) {
        return "/api/projects/" + p + "/todos";
    }

    private static String body(String title, Long assignee) {
        return "{\"title\":\"" + title + "\",\"priority\":\"MEDIUM\""
                + (assignee == null ? "" : ",\"assigneeMemberId\":" + assignee) + "}";
    }

    private static int status(MvcResult r) {
        return r.getResponse().getStatus();
    }

    private MvcResult send(MockHttpServletRequestBuilder b, String json) throws Exception {
        return mockMvc.perform(b.contentType(MediaType.APPLICATION_JSON).content(json)).andReturn();
    }

    private JsonNode read(MvcResult r) throws Exception {
        return objectMapper.readTree(r.getResponse().getContentAsString());
    }

    private JsonNode list(long p) throws Exception {
        return read(mockMvc.perform(get(base(p))).andReturn());
    }

    private long project(String name) throws Exception {
        return read(send(post("/api/projects"), "{\"name\":\"" + name + "\"}")).get("id").asLong();
    }

    private long createMember(long p, String name) throws Exception {
        return read(send(post("/api/projects/" + p + "/members"), "{\"name\":\"" + name + "\"}"))
                .get("id").asLong();
    }

    private long createTodo(long p, String title, Long assignee) throws Exception {
        MvcResult r = send(post(base(p)), body(title, assignee));
        assertThat(status(r)).isEqualTo(201);
        JsonNode items = read(r).get("items");
        return items.get(items.size() - 1).get("id").asLong();
    }
}
