package com.projectflow.application.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Both endpoints are editable, not just the lag: a link typed against the wrong row is the usual
 * reason to edit one, and deleting then re-adding loses nothing but costs two steps.
 *
 * @param lagDays days that must pass after the predecessor ends before the successor may start;
 *                {@code 0} (the default) means the very next day
 */
public record DependencyUpdateRequest(
        @NotNull Long predecessorId,
        @NotNull Long successorId,
        @Min(0) Integer lagDays
) {
}
