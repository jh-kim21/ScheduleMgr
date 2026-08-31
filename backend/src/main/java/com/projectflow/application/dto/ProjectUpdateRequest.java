package com.projectflow.application.dto;

import com.projectflow.domain.ProjectStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record ProjectUpdateRequest(
        @NotBlank String name,
        String description,
        @NotNull ProjectStatus status,
        LocalDate startDate,
        LocalDate endDate
) {
}
