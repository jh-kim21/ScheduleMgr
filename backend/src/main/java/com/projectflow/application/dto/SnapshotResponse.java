package com.projectflow.application.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Saved progress reports, newest first.
 *
 * @param metrics the project-level figures as they read on {@code asOf}, verbatim from when the
 *                report was taken. Not recomputed — that is the entire point (설계 §6.5)
 */
public record SnapshotResponse(
        List<SnapshotDetail> snapshots
) {
    public record SnapshotDetail(
            Long id,
            LocalDate asOf,
            Long baselineId,
            Integer baselineVersion,
            int scopeItemCount,
            Integer scopeWeightTotal,
            String metrics,
            String note,
            LocalDateTime createdAt
    ) {
    }
}
