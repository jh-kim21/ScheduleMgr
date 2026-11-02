package com.projectflow.infrastructure.persistence;

import com.projectflow.domain.TodoItem;
import com.projectflow.domain.TodoItemRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
class TodoItemRepositoryAdapter implements TodoItemRepository {

    private final TodoItemJpaRepository jpaRepository;

    TodoItemRepositoryAdapter(TodoItemJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public TodoItem save(TodoItem item) {
        return jpaRepository.save(item);
    }

    @Override
    public Optional<TodoItem> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public List<TodoItem> findByProjectId(Long projectId) {
        return jpaRepository.findByProjectId(projectId);
    }

    @Override
    public void delete(TodoItem item) {
        jpaRepository.delete(item);
    }
}
