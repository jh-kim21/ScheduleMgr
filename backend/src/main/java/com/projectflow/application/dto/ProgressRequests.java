package com.projectflow.application.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Request bodies for the aggregation basis: checkpoints, baselines and report snapshots. */
public final class ProgressRequests {

    private ProgressRequests() {
    }

    /**
     * @param weight {@code null} counts as 1 — an unweighted checkpoint set is a set of equal
     *               weight, which is the common case
     */
    public record CheckpointSaveRequest(
            @NotNull Long wbsItemId,
            @NotBlank String title,
            @Min(0) Integer weight,
            String completionCriteria
    ) {
    }

    /**
     * @param approvedBy 승인자. 로그인이 없어 자유 입력이지만, 승인은 "누가"가 핵심이라 비워 둘 수 없다
     */
    public record CheckpointApprovalRequest(
            @NotNull Boolean approved,
            String approvedBy
    ) {
    }

    /** Approving a baseline is always an explicit act — nothing here has a default. */
    public record BaselineApproveRequest(
            @NotBlank String approvedBy,
            String note
    ) {
    }

    public record SnapshotSaveRequest(
            String note
    ) {
    }
}
