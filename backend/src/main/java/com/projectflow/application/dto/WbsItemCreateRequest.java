package com.projectflow.application.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

/**
 * @param parentId parent entry id, or {@code null} to create a root-level entry
 */
public record WbsItemCreateRequest(
        Long parentId,
        @NotBlank String name,
        String description,
        LocalDate startDate,
        LocalDate endDate,
        @Min(0) @Max(100) Integer progress
) {
}
