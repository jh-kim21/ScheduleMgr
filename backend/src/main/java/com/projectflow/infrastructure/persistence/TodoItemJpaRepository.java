package com.projectflow.infrastructure.persistence;

import com.projectflow.domain.TodoItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface TodoItemJpaRepository extends JpaRepository<TodoItem, Long> {

    List<TodoItem> findByProjectId(Long projectId);
}
