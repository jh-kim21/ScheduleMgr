package com.projectflow.presentation;

import com.projectflow.application.TodoService;
import com.projectflow.application.dto.TodoCompletionRequest;
import com.projectflow.application.dto.TodoItemRequest;
import com.projectflow.application.dto.TodoListResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Every mutation returns the whole list, judged against the payload's referenceDate. */
@RestController
@RequestMapping("/api/projects/{projectId}/todos")
public class TodoController {

    private final TodoService todoService;

    public TodoController(TodoService todoService) {
        this.todoService = todoService;
    }

    @GetMapping
    public TodoListResponse getList(@PathVariable Long projectId) {
        return todoService.getList(projectId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TodoListResponse addItem(@PathVariable Long projectId,
                                     @Valid @RequestBody TodoItemRequest request) {
        return todoService.addItem(projectId, request);
    }

    @PutMapping("/{itemId}")
    public TodoListResponse updateItem(@PathVariable Long projectId,
                                        @PathVariable Long itemId,
                                        @Valid @RequestBody TodoItemRequest request) {
        return todoService.updateItem(projectId, itemId, request);
    }

    @PutMapping("/{itemId}/completion")
    public TodoListResponse setCompleted(@PathVariable Long projectId,
                                          @PathVariable Long itemId,
                                          @Valid @RequestBody TodoCompletionRequest request) {
        return todoService.setCompleted(projectId, itemId, request.completed());
    }

    @DeleteMapping("/{itemId}")
    public TodoListResponse deleteItem(@PathVariable Long projectId, @PathVariable Long itemId) {
        return todoService.deleteItem(projectId, itemId);
    }
}
