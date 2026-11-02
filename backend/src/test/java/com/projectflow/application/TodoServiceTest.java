package com.projectflow.application;

import com.projectflow.application.dto.ProjectCreateRequest;
import com.projectflow.application.dto.ProjectMemberCreateRequest;
import com.projectflow.application.dto.TodoItemRequest;
import com.projectflow.application.dto.TodoItemResponse;
import com.projectflow.application.dto.TodoListResponse;
import com.projectflow.domain.TodoItemNotFoundException;
import com.projectflow.domain.TodoPriority;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:todo-service-test;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password="
})
@ActiveProfiles("desktop")
@DisplayName("TodoService")
class TodoServiceTest {

    @Autowired
    private TodoService service;
    @Autowired
    private ProjectService projectService;
    @Autowired
    private ProjectMemberService memberService;

    private Long project(String name) {
        return projectService.createProject(new ProjectCreateRequest(name, null, null, null, null)).id();
    }

    private static TodoItemRequest req(String title, Long assignee, LocalDate due) {
        return new TodoItemRequest(title, TodoPriority.MEDIUM, assignee, due, null);
    }

    private static TodoItemResponse only(TodoListResponse list) {
        assertThat(list.items()).hasSize(1);
        return list.items().get(0);
    }

    @Test
    @DisplayName("TODO가 없는 프로젝트는 빈 목록을 돌려준다")
    void emptyList() {
        TodoListResponse list = service.getList(project("빈 목록"));
        assertThat(list.items()).isEmpty();
        assertThat(list.referenceDate()).isNotNull();
    }

    @Test
    @DisplayName("응답의 referenceDate는 인자로 넘긴 기준일이다")
    void referenceDateIsTheArgument() {
        Long p = project("기준일");
        LocalDate date = LocalDate.parse("2030-01-15");
        assertThat(service.getList(p, date).referenceDate()).isEqualTo(date);
    }

    @Test
    @DisplayName("기준일에 따라 기한 초과가 판정된다")
    void overdueFollowsReferenceDate() {
        Long p = project("초과");
        service.addItem(p, req("보고", null, LocalDate.parse("2030-01-10")));
        assertThat(only(service.getList(p, LocalDate.parse("2030-01-10"))).overdue()).isFalse();
        TodoItemResponse late = only(service.getList(p, LocalDate.parse("2030-01-11")));
        assertThat(late.overdue()).isTrue();
        assertThat(late.overdueDays()).isEqualTo(1);
    }

    @Test
    @DisplayName("다른 프로젝트의 구성원을 담당자로 지정하면 거부하고 아무것도 만들지 않는다")
    void rejectsForeignAssignee() {
        Long a = project("A");
        Long b = project("B");
        Long foreign = memberService.addMember(b, new ProjectMemberCreateRequest("외부", null, null)).id();

        assertThatThrownBy(() -> service.addItem(a, req("x", foreign, null)))
                .isInstanceOf(RuntimeException.class);
        assertThat(service.getList(a).items()).isEmpty();
    }

    @Test
    @DisplayName("수정에서도 다른 프로젝트의 구성원은 거부하고 기존 담당자가 유지된다")
    void rejectsForeignAssigneeOnUpdate() {
        Long a = project("A2");
        Long b = project("B2");
        Long mine = memberService.addMember(a, new ProjectMemberCreateRequest("내", null, null)).id();
        Long foreign = memberService.addMember(b, new ProjectMemberCreateRequest("외부", null, null)).id();
        Long id = only(service.addItem(a, req("x", mine, null))).id();

        assertThatThrownBy(() -> service.updateItem(a, id, req("x", foreign, null)))
                .isInstanceOf(RuntimeException.class);
        assertThat(only(service.getList(a)).assigneeMemberId()).isEqualTo(mine);
    }

    @Test
    @DisplayName("같은 프로젝트의 구성원은 이름과 함께 실린다")
    void assigneeNameIsFilled() {
        Long p = project("담당자");
        Long m = memberService.addMember(p, new ProjectMemberCreateRequest("김PM", null, null)).id();
        TodoItemResponse item = only(service.addItem(p, req("x", m, null)));
        assertThat(item.assigneeMemberId()).isEqualTo(m);
        assertThat(item.assigneeName()).isEqualTo("김PM");
    }

    @Test
    @DisplayName("setCompleted(true)를 두 번 불러도 completedAt은 바뀌지 않는다")
    void completedAtIsStable() throws Exception {
        Long p = project("완료");
        Long id = only(service.addItem(p, req("x", null, null))).id();

        service.setCompleted(p, id, true);
        // 저장소를 다시 읽은 값끼리 비교한다 (DB 정밀도로 잘리기 전 값과 비교하면 나노초가 어긋난다).
        TodoItemResponse first = only(service.getList(p));
        assertThat(first.completed()).isTrue();
        assertThat(first.completedAt()).isNotNull();

        Thread.sleep(20);
        service.setCompleted(p, id, true);
        TodoItemResponse second = only(service.getList(p));
        assertThat(second.completedAt()).isEqualTo(first.completedAt());
    }

    @Test
    @DisplayName("setCompleted(false)는 completedAt을 비운다")
    void uncompleteClears() {
        Long p = project("해제");
        Long id = only(service.addItem(p, req("x", null, null))).id();
        service.setCompleted(p, id, true);

        TodoItemResponse item = only(service.setCompleted(p, id, false));
        assertThat(item.completed()).isFalse();
        assertThat(item.completedAt()).isNull();
    }

    @Test
    @DisplayName("일반 수정은 완료 상태를 건드리지 않는다")
    void updateKeepsCompletion() {
        Long p = project("수정");
        Long id = only(service.addItem(p, req("x", null, null))).id();
        service.setCompleted(p, id, true);

        TodoItemResponse item = only(service.updateItem(p, id, req("y", null, null)));
        assertThat(item.title()).isEqualTo("y");
        assertThat(item.completed()).isTrue();
    }

    @Test
    @DisplayName("다른 프로젝트의 항목 id로 수정·완료·삭제하면 TodoItemNotFoundException")
    void otherProjectsItemIsNotFound() {
        Long a = project("A3");
        Long b = project("B3");
        Long id = only(service.addItem(a, req("x", null, null))).id();

        assertThatThrownBy(() -> service.updateItem(b, id, req("y", null, null)))
                .isInstanceOf(TodoItemNotFoundException.class);
        assertThatThrownBy(() -> service.setCompleted(b, id, true))
                .isInstanceOf(TodoItemNotFoundException.class);
        assertThatThrownBy(() -> service.deleteItem(b, id))
                .isInstanceOf(TodoItemNotFoundException.class);
        assertThat(only(service.getList(a)).title()).isEqualTo("x");
    }

    @Test
    @DisplayName("존재하지 않는 id는 TodoItemNotFoundException")
    void missingId() {
        Long p = project("없음");
        assertThatThrownBy(() -> service.updateItem(p, 999_999L, req("y", null, null)))
                .isInstanceOf(TodoItemNotFoundException.class);
    }

    @Test
    @DisplayName("삭제하면 목록에서 사라진다")
    void deleteRemoves() {
        Long p = project("삭제");
        Long id = only(service.addItem(p, req("x", null, null))).id();
        assertThat(service.deleteItem(p, id).items()).isEmpty();
    }
}
