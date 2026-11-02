package com.projectflow.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * 기한 초과 판정. RaidAssessor 와 같은 규칙이지만 일부러 따로 둔다. RAID 판정을 바꿀 때
 * TODO가 같이 흔들리면 안 된다.
 *
 * <p>기한이 없으면 초과가 아니고, 완료된 항목은 기한이 지나도 초과가 아니며, 기한 당일은 아직
 * 초과가 아니다. 기준일은 서버가 정해 응답에 함께 싣는다.
 */
public final class TodoAssessor {

    private TodoAssessor() {
    }

    public record TodoAssessment(boolean overdue, long overdueDays) {
    }

    public static TodoAssessment assess(LocalDate dueDate, LocalDateTime completedAt,
                                         LocalDate referenceDate) {
        boolean overdue = dueDate != null
                && completedAt == null
                && dueDate.isBefore(referenceDate);
        long overdueDays = overdue ? ChronoUnit.DAYS.between(dueDate, referenceDate) : 0;
        return new TodoAssessment(overdue, overdueDays);
    }

    public static TodoAssessment assess(TodoItem item, LocalDate referenceDate) {
        return assess(item.getDueDate(), item.getCompletedAt(), referenceDate);
    }
}
