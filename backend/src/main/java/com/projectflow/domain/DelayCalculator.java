package com.projectflow.domain;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Decides how late a task is by comparing its planned schedule and recorded progress against a
 * reference date — normally today (요구사항 8.3). Nothing here is stored or entered by hand.
 *
 * <p>The expectation curve is linear over the planned span: a task running 1일~10일 is expected to
 * be 50% done on day 5. That is a deliberately simple model — without effort estimates or actual
 * start/finish dates there is nothing better to weight it by, and a linear baseline is the one
 * every reader can predict.
 *
 * <p>Both ends of the planned range are inclusive, so a task planned for a single day is expected
 * to be finished by the end of that day.
 */
public final class DelayCalculator {

    private DelayCalculator() {
    }

    /**
     * @param status           overall verdict
     * @param expectedProgress progress the linear baseline expects by the reference date, 0–100
     * @param progressGap      how far behind the baseline the task is in percentage points; 0 when
     *                         on or ahead of schedule
     * @param delayDays        days past the planned end date while still incomplete; 0 otherwise
     */
    public record DelayAssessment(
            DelayStatus status,
            int expectedProgress,
            int progressGap,
            long delayDays
    ) {
    }

    public static DelayAssessment assess(LocalDate startDate, LocalDate endDate, int progress,
                                          LocalDate referenceDate) {
        if (progress >= 100) {
            return new DelayAssessment(DelayStatus.COMPLETED, 100, 0, 0);
        }
        if (startDate == null || endDate == null) {
            return new DelayAssessment(DelayStatus.UNSCHEDULED, 0, 0, 0);
        }
        if (referenceDate.isBefore(startDate)) {
            return new DelayAssessment(DelayStatus.NOT_STARTED, 0, 0, 0);
        }
        if (referenceDate.isAfter(endDate)) {
            long delayDays = ChronoUnit.DAYS.between(endDate, referenceDate);
            return new DelayAssessment(DelayStatus.DELAYED, 100, 100 - progress, delayDays);
        }

        long plannedDays = ChronoUnit.DAYS.between(startDate, endDate) + 1;
        long elapsedDays = ChronoUnit.DAYS.between(startDate, referenceDate) + 1;
        int expectedProgress = (int) Math.min(100, Math.round(100.0 * elapsedDays / plannedDays));
        int progressGap = Math.max(0, expectedProgress - progress);

        return new DelayAssessment(
                progressGap > 0 ? DelayStatus.AT_RISK : DelayStatus.ON_TRACK,
                expectedProgress,
                progressGap,
                0
        );
    }
}
