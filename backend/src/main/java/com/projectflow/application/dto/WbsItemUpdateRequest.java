package com.projectflow.application.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

public record WbsItemUpdateRequest(
        @NotBlank String name,
        String description,
        LocalDate startDate,
        LocalDate endDate,
        @Min(0) @Max(100) Integer progress
) {
}
