package com.projectflow.application.dto;

import com.projectflow.domain.DelayStatus;
import com.projectflow.domain.ProgressBasis;
import com.projectflow.domain.SprintStatus;

import java.time.LocalDate;
import java.util.List;

/**
 * Everything the Gantt view needs in one payload (요구사항 6.4).
 *
 * @param chartStart    earliest dated task, or {@code null} when nothing is scheduled yet
 * @param chartEnd      latest dated task, or {@code null}
 * @param referenceDate the date delay was judged against — returned so the client shows the same
 *                      "today" the server used instead of its own clock
 * @param tasks         WBS entries flattened in tree order, so chart rows line up with the WBS view
 * @param dependencies  finish-to-start links to draw between rows
 * @param hasBaseline   whether a baseline has been approved. When false the chart must say
 *                      "기준 일정 미등록" rather than drawing the current plan as if it were one
 *                      (지시서 6-A)
 * @param baselineVersion the approved version the baseline bars come from, {@code null} when none
 * @param sprints       Sprint periods as their own lane. One entry per Sprint even when it spans
 *                      several Work Packages — repeating it under each row would invent schedule
 *                      and progress that do not exist
 */
public record GanttResponse(
        LocalDate chartStart,
        LocalDate chartEnd,
        LocalDate referenceDate,
        boolean hasBaseline,
        Integer baselineVersion,
        List<GanttTaskResponse> tasks,
        List<DependencyResponse> dependencies,
        List<SprintLane> sprints
) {
    /**
     * Two independent health signals travel with each row, and they answer different questions:
     * {@code scheduleViolation} is about the plan contradicting itself (a task starting before its
     * predecessor finishes), while {@code delayStatus} is about the plan versus reality as of
     * {@link GanttResponse#referenceDate}.
     *
     * @param earliestStart      earliest start allowed by predecessors, {@code null} when unconstrained
     * @param scheduleViolation  true when the task starts before {@code earliestStart}
     * @param expectedProgress   progress the linear baseline expects by the reference date
     * @param progressGap        percentage points behind that baseline; 0 when on or ahead
     * @param delayDays          days past the planned end date while incomplete; 0 otherwise
     * @param floatDays          days this task may slip before the project end moves, {@code null}
     *                           when it takes part in no dependency and so sits on no chain
     * @param criticalPath       true when there is no float left to give
     * @param baselineStart      approved start, {@code null} when this row is not in the baseline
     * @param baselineEnd        approved end. The current plan never overwrites these — that is the
     *                           whole point of a baseline
     * @param actualStart        when work really started, {@code null} when not recorded
     * @param actualEnd          when it really finished
     * @param forecastEnd        when it now looks like it will finish
     * @param baselineExceeded   forecast (or, without one, the current plan) runs past the approved
     *                           end date — the warning the design asks for
     * @param baselineSlipDays   how many days past it, 0 when not exceeded
     * @param computedProgress   the common aggregation's figure, unrounded; {@code null} = 산정 전
     * @param progressBasis      how that figure was arrived at
     * @param acceptancePending  진척 100%이지만 인수가 남았다
     * @param sprintIds          Sprints working on this row, for highlighting the lane. Reference
     *                           only: no schedule or progress is derived from it
     */
    public record GanttTaskResponse(
            Long id,
            Long parentId,
            String code,
            int level,
            String name,
            boolean summary,
            LocalDate startDate,
            LocalDate endDate,
            int progress,
            LocalDate earliestStart,
            boolean scheduleViolation,
            DelayStatus delayStatus,
            int expectedProgress,
            int progressGap,
            long delayDays,
            Long floatDays,
            boolean criticalPath,
            LocalDate baselineStart,
            LocalDate baselineEnd,
            LocalDate actualStart,
            LocalDate actualEnd,
            LocalDate forecastEnd,
            boolean baselineExceeded,
            long baselineSlipDays,
            Double computedProgress,
            ProgressBasis progressBasis,
            boolean acceptancePending,
            List<Long> sprintIds
    ) {
    }

    /**
     * One Sprint as a lane of its own.
     *
     * @param wbsItemIds the Work Packages this Sprint is working on, so selecting a row can
     *                   highlight the lane. A reference, not a second copy of the schedule
     */
    public record SprintLane(
            Long id,
            String name,
            String goal,
            LocalDate startDate,
            LocalDate endDate,
            SprintStatus status,
            int plannedItems,
            int doneItems,
            List<Long> wbsItemIds
    ) {
    }

    /** @param criticalPath true when this link joins two critical tasks with no slack between them */
    public record DependencyResponse(
            Long id,
            Long predecessorId,
            Long successorId,
            int lagDays,
            boolean criticalPath
    ) {
    }
}
