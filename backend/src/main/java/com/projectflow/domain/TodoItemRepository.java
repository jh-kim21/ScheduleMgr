package com.projectflow.domain;

import java.util.List;
import java.util.Optional;

/** Domain-level repository port for TODO entries. */
public interface TodoItemRepository {

    TodoItem save(TodoItem item);

    Optional<TodoItem> findById(Long id);

    /** Every entry of a project, in no particular order. */
    List<TodoItem> findByProjectId(Long projectId);

    void delete(TodoItem item);
}
