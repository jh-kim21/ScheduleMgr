package com.projectflow.application.dto;

import com.projectflow.domain.AcceptanceStatus;
import com.projectflow.domain.ExecutionMode;
import com.projectflow.domain.ProgressBasis;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * The common progress figures every screen reads (설계 §12.2 "공통 집계 서비스").
 *
 * <p>Percentages are unrounded — rounding happens where the number is displayed, so a rollup is
 * never computed from already-rounded children. A {@code null} percentage means <b>산정 전</b> and
 * is never the same as 0.
 *
 * @param referenceDate the date planned progress was measured against, decided by the server
 */
public record ProgressResponse(
        LocalDate referenceDate,
        ProjectProgress project,
        List<WorkPackageProgress> workPackages,
        BaselineSummary baseline,
        ScopeComparison scope
) {
    /**
     * @param actualPercent      진척 over the current scope. {@code null} = 산정 전
     * @param notEstimableCount  Work Packages that cannot be measured yet — the number that says
     *                           how much of the project the headline figure is silent about
     * @param incomplete         some part of the rollup was left out (missing weight or 산정 전 하위)
     * @param plannedPercent     계획 진척 over the baselined Work Packages, or {@code null} when no
     *                           baseline is approved — time alone is never used as actual progress
     * @param comparablePercent  실제 진척 over that <em>same</em> set, so the two can be subtracted
     * @param variancePoints     comparable − planned, in percentage points
     * @param acceptancePending  Work Packages at 100% whose acceptance is still outstanding
     * @param varianceExcludedCount baselined Work Packages left out of {@code plannedPercent} and
     *                              {@code comparablePercent} because they are still 산정 전 — dropping
     *                              them from <em>both</em> sides is what keeps the two numbers over the
     *                              same denominator (설계 §6.5); this is how many were dropped
     */
    public record ProjectProgress(
            Double actualPercent,
            ProgressBasis basis,
            int workPackageCount,
            int notEstimableCount,
            boolean incomplete,
            Double plannedPercent,
            Double comparablePercent,
            Double variancePoints,
            int acceptancePending,
            int varianceExcludedCount
    ) {
    }

    /**
     * @param percent          unrounded, {@code null} = 산정 전
     * @param note             why it is 산정 전, in one line
     * @param backlogTotal     집계 대상 Story·Bug 수 (Epic·Task는 세지 않는다)
     * @param checkpointTotal  체크포인트 수 — Waterfall·Hybrid의 분모
     * @param acceptancePending 진척 100%이지만 인수가 남았다
     */
    public record WorkPackageProgress(
            Long wbsItemId,
            String code,
            String name,
            ExecutionMode executionMode,
            Integer weight,
            Integer agileRatio,
            AcceptanceStatus acceptanceStatus,
            Double percent,
            ProgressBasis basis,
            String note,
            int backlogTotal,
            int backlogDone,
            int checkpointTotal,
            int checkpointApproved,
            boolean acceptancePending,
            List<CheckpointDetail> checkpoints
    ) {
    }

    public record CheckpointDetail(
            Long id,
            String title,
            Integer weight,
            String completionCriteria,
            boolean approved,
            String approvedBy,
            LocalDateTime approvedAt
    ) {
    }

    /** {@code null} in the response means no baseline has been approved — nothing was invented. */
    public record BaselineSummary(
            Long id,
            int version,
            String approvedBy,
            LocalDateTime approvedAt,
            String note,
            int itemCount
    ) {
    }

    /**
     * 기준 범위와 현재 범위의 차이 (지시서 5-C). This is what explains a progress figure that moved
     * because the denominator moved rather than because work happened (설계 §6.1).
     *
     * @param added        현재에만 있는 항목
     * @param removed      기준에만 있는 항목 (지워졌거나 범위에서 빠졌다)
     * @param weightChanged 가중치가 달라진 항목
     */
    public record ScopeComparison(
            boolean hasBaseline,
            int baselineItemCount,
            int currentItemCount,
            List<String> added,
            List<String> removed,
            List<String> weightChanged
    ) {
    }
}
