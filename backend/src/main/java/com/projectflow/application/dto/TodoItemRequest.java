package com.projectflow.application.dto;

import com.projectflow.domain.TodoPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** 완료 여부는 받지 않는다. 완료는 TodoCompletionRequest 경로로만 바뀐다. */
public record TodoItemRequest(
        @NotBlank @Size(max = 255) String title,
        @NotNull TodoPriority priority,
        Long assigneeMemberId,
        LocalDate dueDate,
        @Size(max = 2000) String memo
) {
}
