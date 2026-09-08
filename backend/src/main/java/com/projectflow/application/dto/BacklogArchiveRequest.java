package com.projectflow.application.dto;

import jakarta.validation.constraints.NotNull;

/**
 * @param archived {@code true} 보관, {@code false} 복구. A body rather than two endpoints, so the
 *                 screen's toggle maps to one call whichever way it is going.
 */
public record BacklogArchiveRequest(
        @NotNull Boolean archived
) {
}
