package com.projectflow.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TODO 기한 초과 판정")
class TodoAssessorTest {

    private static final LocalDate TODAY = LocalDate.parse("2026-09-10");
    private static final LocalDateTime DONE = LocalDateTime.parse("2026-09-01T10:00:00");

    @Test
    @DisplayName("기한 당일은 아직 초과가 아니다")
    void dueTodayIsNotOverdue() {
        var result = TodoAssessor.assess(TODAY, null, TODAY);
        assertThat(result.overdue()).isFalse();
        assertThat(result.overdueDays()).isZero();
    }

    @Test
    @DisplayName("기한이 미래면 초과가 아니다")
    void futureIsNotOverdue() {
        var result = TodoAssessor.assess(TODAY.plusDays(1), null, TODAY);
        assertThat(result.overdue()).isFalse();
        assertThat(result.overdueDays()).isZero();
    }

    @Test
    @DisplayName("기한 다음 날은 초과이고 overdueDays는 1이다")
    void dayAfterIsOverdueByOne() {
        var result = TodoAssessor.assess(TODAY.minusDays(1), null, TODAY);
        assertThat(result.overdue()).isTrue();
        assertThat(result.overdueDays()).isEqualTo(1);
    }

    @Test
    @DisplayName("초과 일수는 기한과 기준일의 차이다")
    void overdueDaysIsTheGap() {
        assertThat(TodoAssessor.assess(TODAY.minusDays(30), null, TODAY).overdueDays()).isEqualTo(30);
    }

    @Test
    @DisplayName("완료된 항목은 기한이 지나도 초과가 아니다")
    void completedIsNeverOverdue() {
        var result = TodoAssessor.assess(TODAY.minusDays(10), DONE, TODAY);
        assertThat(result.overdue()).isFalse();
        assertThat(result.overdueDays()).isZero();
    }

    @Test
    @DisplayName("기한이 없으면 초과가 아니다")
    void noDueDateIsNeverOverdue() {
        var result = TodoAssessor.assess(null, null, TODAY);
        assertThat(result.overdue()).isFalse();
        assertThat(result.overdueDays()).isZero();
    }

    @Test
    @DisplayName("기한도 없고 완료됐어도 초과가 아니다")
    void noDueDateAndCompleted() {
        assertThat(TodoAssessor.assess(null, DONE, TODAY).overdue()).isFalse();
    }
}
