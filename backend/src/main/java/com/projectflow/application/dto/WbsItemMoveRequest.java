package com.projectflow.application.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Drag &amp; drop payload (요구사항 5.4).
 *
 * @param parentId new parent id, or {@code null} to move to the root level
 * @param position zero-based index among the new siblings; values past the end append
 */
public record WbsItemMoveRequest(
        Long parentId,
        @NotNull @Min(0) Integer position
) {
}
