package com.projectflow.application;

import com.projectflow.application.dto.TodoItemRequest;
import com.projectflow.application.dto.TodoItemResponse;
import com.projectflow.application.dto.TodoListResponse;
import com.projectflow.domain.ProjectMember;
import com.projectflow.domain.InvalidTodoItemException;
import com.projectflow.domain.ProjectMemberRepository;
import com.projectflow.domain.ProjectNotFoundException;
import com.projectflow.domain.ProjectRepository;
import com.projectflow.domain.TodoAssessor;
import com.projectflow.domain.TodoItem;
import com.projectflow.domain.TodoItemNotFoundException;
import com.projectflow.domain.TodoItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The project's TODO list. Not linked to WBS, Backlog or RAID by design. */
@Service
@Transactional(readOnly = true)
public class TodoService {

    private final TodoItemRepository todoItemRepository;
    private final ProjectMemberRepository memberRepository;
    private final ProjectRepository projectRepository;

    public TodoService(TodoItemRepository todoItemRepository,
                        ProjectMemberRepository memberRepository,
                        ProjectRepository projectRepository) {
        this.todoItemRepository = todoItemRepository;
        this.memberRepository = memberRepository;
        this.projectRepository = projectRepository;
    }

    public TodoListResponse getList(Long projectId) {
        return getList(projectId, LocalDate.now());
    }

    /** For the commit history: one reference date fixed across every captured screen. */
    public TodoListResponse getList(Long projectId, LocalDate referenceDate) {
        requireProject(projectId);
        return buildList(projectId, referenceDate);
    }

    @Transactional
    public TodoListResponse addItem(Long projectId, TodoItemRequest request) {
        requireProject(projectId);
        requireAssigneeOfProject(projectId, request.assigneeMemberId());
        todoItemRepository.save(new TodoItem(
                projectId,
                request.title().trim(),
                request.priority(),
                request.assigneeMemberId(),
                request.dueDate(),
                blankToNull(request.memo()),
                null));
        return buildList(projectId, LocalDate.now());
    }

    @Transactional
    public TodoListResponse updateItem(Long projectId, Long itemId, TodoItemRequest request) {
        requireProject(projectId);
        requireAssigneeOfProject(projectId, request.assigneeMemberId());
        TodoItem item = requireItemOfProject(projectId, itemId);
        item.update(
                request.title().trim(),
                request.priority(),
                request.assigneeMemberId(),
                request.dueDate(),
                blankToNull(request.memo()));
        todoItemRepository.save(item);
        return buildList(projectId, LocalDate.now());
    }

    @Transactional
    public TodoListResponse setCompleted(Long projectId, Long itemId, boolean completed) {
        requireProject(projectId);
        TodoItem item = requireItemOfProject(projectId, itemId);
        item.setCompleted(completed, LocalDateTime.now());
        todoItemRepository.save(item);
        return buildList(projectId, LocalDate.now());
    }

    @Transactional
    public TodoListResponse deleteItem(Long projectId, Long itemId) {
        requireProject(projectId);
        todoItemRepository.delete(requireItemOfProject(projectId, itemId));
        return buildList(projectId, LocalDate.now());
    }

    private TodoListResponse buildList(Long projectId, LocalDate referenceDate) {
        Map<Long, String> names = new HashMap<>();
        for (ProjectMember member : memberRepository.findByProjectId(projectId)) {
            names.put(member.getId(), member.getName());
        }
        List<TodoItemResponse> items = todoItemRepository.findByProjectId(projectId).stream()
                .sorted(Comparator.comparing(TodoItem::getId))
                .map(item -> {
                    TodoAssessor.TodoAssessment assessment = TodoAssessor.assess(item, referenceDate);
                    return new TodoItemResponse(
                            item.getId(),
                            item.getTitle(),
                            item.getPriority(),
                            item.getAssigneeMemberId(),
                            item.getAssigneeMemberId() == null
                                    ? null
                                    : names.get(item.getAssigneeMemberId()),
                            item.getDueDate(),
                            item.getMemo(),
                            item.getCompletedAt() != null,
                            item.getCompletedAt(),
                            assessment.overdue(),
                            assessment.overdueDays(),
                            item.getCreatedAt(),
                            item.getUpdatedAt());
                })
                .toList();
        return new TodoListResponse(referenceDate, items);
    }

    private void requireProject(Long projectId) {
        if (!projectRepository.existsById(projectId)) {
            throw new ProjectNotFoundException(projectId);
        }
    }

    private TodoItem requireItemOfProject(Long projectId, Long itemId) {
        return todoItemRepository.findByProjectId(projectId).stream()
                .filter(candidate -> candidate.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new TodoItemNotFoundException(itemId));
    }

    /** RaidService 의 소유자 검증과 같은 판정이지만 400 이다(RAID 는 404 로 나간다). */
    private void requireAssigneeOfProject(Long projectId, Long assigneeMemberId) {
        if (assigneeMemberId == null) {
            return;
        }
        boolean present = memberRepository.findByProjectId(projectId).stream()
                .anyMatch(member -> member.getId().equals(assigneeMemberId));
        if (!present) {
            throw new InvalidTodoItemException(
                    "Project member not found: id=" + assigneeMemberId);
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
