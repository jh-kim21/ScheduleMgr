package com.projectflow.application.dto;

import com.projectflow.application.dto.ProgressResponse.BaselineSummary;
import com.projectflow.application.dto.ProgressResponse.ProjectProgress;
import com.projectflow.application.dto.ProgressResponse.ScopeComparison;
import com.projectflow.domain.ExecutionMode;
import com.projectflow.domain.ProjectStatus;
import com.projectflow.domain.SprintStatus;

import java.time.LocalDate;
import java.util.List;

/**
 * The project dashboard (지시서 Step 7).
 *
 * <p><b>Nothing here is computed twice.</b> Every figure is read from the service that already owns
 * it — progress from {@code ProgressService}, delay and float from {@code GanttService}, execution
 * from {@code SprintService}, and so on. That is the only way the completion criterion "WBS, 간트,
 * Dashboard 진척이 일치한다" can hold: a dashboard with its own arithmetic is a fourth opinion.
 *
 * <p><b>산정 전은 0%가 아니다.</b> Percentages stay {@code null} when the aggregation has nothing to
 * measure, and {@link ProjectProgress#notEstimableCount()} says how much of the project the headline
 * figure is silent about.
 *
 * <p>Every attention list carries the ids needed to open the row it is about, so a number on the
 * dashboard always leads somewhere.
 *
 * @param referenceDate the single "today" every judgement here was made against, decided by the
 *                      server — the client must not substitute its own clock
 */
public record DashboardResponse(
        LocalDate referenceDate,
        ProjectCard project,
        ProjectProgress progress,
        BaselineSummary baseline,
        ScheduleCard schedule,
        ExecutionCard execution,
        List<SprintVelocity> velocity,
        WorkPackageCard workPackages,
        ControlCard control,
        ScopeComparison scope,
        List<DataGap> gaps
) {
    /**
     * @param planEnd     the latest planned end across the WBS
     * @param forecastEnd the latest 예상 종료 — the plan's own end when nothing is forecast. Not a
     *                    milestone: this codebase has no milestone entity, so the dashboard states
     *                    the project's end dates rather than inventing one
     */
    public record ProjectCard(
            Long id,
            String name,
            ProjectStatus status,
            LocalDate startDate,
            LocalDate endDate,
            LocalDate planStart,
            LocalDate planEnd,
            LocalDate forecastEnd
    ) {
    }

    /**
     * Schedule health, straight from the Gantt payload.
     *
     * @param delayedCount   leaf rows only — a summary already reflects its children's delay, and
     *                       counting both double-counts (the WBS screen's rule)
     * @param baselineExceeded rows whose forecast runs past the approved baseline end
     */
    public record ScheduleCard(
            int delayedCount,
            int atRiskCount,
            long worstDelayDays,
            int criticalPathCount,
            int scheduleViolationCount,
            List<TaskRef> delayed,
            List<TaskRef> baselineExceeded,
            List<TaskRef> acceptancePending
    ) {
    }

    /**
     * The Sprint that is running, or {@code null} when none is.
     *
     * <p>단일 팀 전제이므로 실행 중인 Sprint는 하나다. Should teams arrive, this becomes a list and
     * the velocity series below splits per team — the numbers must never be added across teams.
     */
    public record ExecutionCard(
            ActiveSprint activeSprint,
            int backlogUnlinkedCount
    ) {
    }

    public record ActiveSprint(
            Long id,
            String name,
            String goal,
            LocalDate startDate,
            LocalDate endDate,
            int plannedItems,
            int plannedPoints,
            int doneItems,
            int donePoints,
            List<BlockedItem> blocked
    ) {
    }

    /**
     * @param raid open RAID entries attached to this entry or to its Work Package, with their
     *             owners — "왜 막혔나"의 답이 카드 옆에 있어야 한다 (지시서 6-C)
     */
    public record BlockedItem(
            Long backlogItemId,
            String title,
            String reason,
            String assigneeName,
            Long wbsItemId,
            String wbsCode,
            List<RaidRef> raid
    ) {
    }

    /**
     * Completed Story Points per closed Sprint, oldest first.
     *
     * <p>Closed Sprints only. An open Sprint's number is still moving, and putting it on the same
     * line would read as a drop every time you look mid-Sprint — {@code inProgress} carries it
     * separately so the screen can show it without joining the trend.
     */
    public record SprintVelocity(
            Long sprintId,
            String name,
            LocalDate startDate,
            LocalDate endDate,
            SprintStatus status,
            int donePoints,
            int doneItems,
            int carriedOverItems,
            boolean inProgress
    ) {
    }

    /**
     * @param byExecutionMode counts per mode; 미지정 is a key with a {@code null} mode name and is
     *                        never hidden — an unassigned Work Package is something to look at
     */
    public record WorkPackageCard(
            int total,
            int notEstimableCount,
            int acceptancePendingCount,
            List<ModeCount> byExecutionMode
    ) {
    }

    /** @param mode {@code null} = 미지정 */
    public record ModeCount(ExecutionMode mode, int count) {
    }

    /**
     * Responsibility and risk.
     *
     * @param raciIssueCount rule breaches from the RACI matrix, already inherited-aware — do not
     *                       recount them here
     */
    public record ControlCard(
            int raciIssueCount,
            int missingAccountableCount,
            int missingResponsibleCount,
            int multipleAccountableCount,
            List<RaidRef> openIssues,
            List<RaidRef> highExposure,
            List<RaidRef> overdue
    ) {
    }

    public record TaskRef(
            Long wbsItemId,
            String code,
            String name,
            String detail
    ) {
    }

    public record RaidRef(
            Long raidItemId,
            String type,
            String title,
            String ownerName,
            String detail
    ) {
    }

    /**
     * Something the numbers above cannot answer because the data is not there (지시서 3항).
     *
     * <p>Reported rather than filled in: a missing weight is a decision nobody has made, and
     * inventing one would move every figure it feeds.
     *
     * @param kind      a stable code the screen maps to a message and a destination
     * @param count     how many rows are affected
     * @param wbsItemIds / {@code backlogItemIds} the rows themselves, so the number leads somewhere
     */
    public record DataGap(
            String kind,
            String label,
            int count,
            List<Long> wbsItemIds,
            List<Long> backlogItemIds
    ) {
    }
}
