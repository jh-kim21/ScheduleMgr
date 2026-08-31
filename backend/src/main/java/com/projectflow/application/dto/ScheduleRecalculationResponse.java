package com.projectflow.application.dto;

/**
 * Result of pushing tasks to satisfy their dependencies (요구사항 6.6).
 *
 * @param shiftedTaskCount how many WBS entries actually moved; {@code 0} means nothing violated
 * @param gantt            the recalculated chart data
 */
public record ScheduleRecalculationResponse(
        int shiftedTaskCount,
        GanttResponse gantt
) {
}
