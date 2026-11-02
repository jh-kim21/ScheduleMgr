package com.projectflow.application.dto;

import com.projectflow.domain.TodoPriority;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record TodoItemResponse(
        Long id,
        String title,
        TodoPriority priority,
        Long assigneeMemberId,
        String assigneeName,
        LocalDate dueDate,
        String memo,
        boolean completed,
        LocalDateTime completedAt,
        boolean overdue,
        long overdueDays,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
