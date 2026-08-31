package com.projectflow.application.dto;

import com.projectflow.domain.DelayStatus;

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
 */
public record GanttResponse(
        LocalDate chartStart,
        LocalDate chartEnd,
        LocalDate referenceDate,
        List<GanttTaskResponse> tasks,
        List<DependencyResponse> dependencies
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
            boolean criticalPath
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
