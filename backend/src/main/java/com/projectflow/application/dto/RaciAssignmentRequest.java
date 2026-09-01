package com.projectflow.application.dto;

import com.projectflow.domain.RaciRole;
import jakarta.validation.constraints.NotNull;

/** One letter to add to one cell (요구사항 7.1). */
public record RaciAssignmentRequest(
        @NotNull Long wbsItemId,
        @NotNull Long memberId,
        @NotNull RaciRole role
) {
}
