package com.projectflow.application.dto;

import com.projectflow.domain.Project;
import com.projectflow.domain.ProjectStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record ProjectResponse(
        Long id,
        String name,
        String description,
        ProjectStatus status,
        LocalDate startDate,
        LocalDate endDate,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static ProjectResponse from(Project project) {
        return new ProjectResponse(
                project.getId(),
                project.getName(),
                project.getDescription(),
                project.getStatus(),
                project.getStartDate(),
                project.getEndDate(),
                project.getCreatedAt(),
                project.getUpdatedAt()
        );
    }
}
