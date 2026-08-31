package com.projectflow.application.dto;

import com.projectflow.domain.ProjectStatus;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

public record ProjectCreateRequest(
        @NotBlank String name,
        String description,
        ProjectStatus status,
        LocalDate startDate,
        LocalDate endDate
) {
}
