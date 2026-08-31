package com.projectflow.application.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * @param lagDays days that must pass after the predecessor ends before the successor may start;
 *                {@code 0} (the default) means the very next day
 */
public record DependencyCreateRequest(
        @NotNull Long predecessorId,
        @NotNull Long successorId,
        @Min(0) Integer lagDays
) {
}
