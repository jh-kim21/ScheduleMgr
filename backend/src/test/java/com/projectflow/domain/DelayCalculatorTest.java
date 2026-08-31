package com.projectflow.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class DelayCalculatorTest {

    private static final LocalDate START = LocalDate.parse("2026-09-01");
    private static final LocalDate END = LocalDate.parse("2026-09-10");

    @Test
    @DisplayName("진행률 100%는 종료일이 지났어도 완료로 본다")
    void completedRegardlessOfDate() {
        var assessment = assess(100, "2026-12-31");

        assertThat(assessment.status()).isEqualTo(DelayStatus.COMPLETED);
        assertThat(assessment.delayDays()).isZero();
        assertThat(assessment.progressGap()).isZero();
    }

    @Test
    @DisplayName("일정이 없으면 판정하지 않는다")
    void unscheduledWhenDatesMissing() {
        assertThat(DelayCalculator.assess(null, END, 0, START).status()).isEqualTo(DelayStatus.UNSCHEDULED);
        assertThat(DelayCalculator.assess(START, null, 0, START).status()).isEqualTo(DelayStatus.UNSCHEDULED);
    }

    @Test
    @DisplayName("시작일 전에는 미착수로 보고 지연을 계산하지 않는다")
    void notStartedBeforeStartDate() {
        var assessment = assess(0, "2026-08-25");

        assertThat(assessment.status()).isEqualTo(DelayStatus.NOT_STARTED);
        assertThat(assessment.expectedProgress()).isZero();
        assertThat(assessment.progressGap()).isZero();
    }

    @Test
    @DisplayName("기대 진행률은 경과 일수에 비례하고 종료일 포함으로 계산한다")
    void expectedProgressIsLinearOverInclusiveSpan() {
        // 09-01 ~ 09-10 은 10일. 09-05 시점 경과 5일 -> 50%
        assertThat(assess(50, "2026-09-05").expectedProgress()).isEqualTo(50);
        // 시작일 당일에도 하루가 경과한 것으로 본다 -> 10%
        assertThat(assess(10, "2026-09-01").expectedProgress()).isEqualTo(10);
        // 종료일 당일에는 100%를 기대한다
        assertThat(assess(100, "2026-09-10").expectedProgress()).isEqualTo(100);
    }

    @Test
    @DisplayName("기대치를 충족하면 정상이다")
    void onTrackWhenMeetingBaseline() {
        var assessment = assess(50, "2026-09-05");

        assertThat(assessment.status()).isEqualTo(DelayStatus.ON_TRACK);
        assertThat(assessment.progressGap()).isZero();
    }

    @Test
    @DisplayName("기대치를 앞서가도 정상이며 격차는 0이다")
    void aheadOfScheduleIsOnTrackWithoutNegativeGap() {
        var assessment = assess(90, "2026-09-05");

        assertThat(assessment.status()).isEqualTo(DelayStatus.ON_TRACK);
        assertThat(assessment.progressGap()).isZero();
    }

    @Test
    @DisplayName("기간 안이지만 진행률이 모자라면 지연 위험이다")
    void atRiskWhenBehindBaseline() {
        var assessment = assess(20, "2026-09-05");

        assertThat(assessment.status()).isEqualTo(DelayStatus.AT_RISK);
        assertThat(assessment.expectedProgress()).isEqualTo(50);
        assertThat(assessment.progressGap()).isEqualTo(30);
        assertThat(assessment.delayDays()).isZero();
    }

    @Test
    @DisplayName("시작일이 지났는데 진행률 0%면 지연 위험으로 잡힌다")
    void notYetStartedWorkIsAtRisk() {
        var assessment = assess(0, "2026-09-03");

        assertThat(assessment.status()).isEqualTo(DelayStatus.AT_RISK);
        assertThat(assessment.progressGap()).isEqualTo(assessment.expectedProgress());
    }

    @Test
    @DisplayName("종료일이 지났는데 미완료면 지연이고 경과 일수를 센다")
    void delayedAfterEndDate() {
        var assessment = assess(80, "2026-09-15");

        assertThat(assessment.status()).isEqualTo(DelayStatus.DELAYED);
        assertThat(assessment.expectedProgress()).isEqualTo(100);
        assertThat(assessment.progressGap()).isEqualTo(20);
        assertThat(assessment.delayDays()).isEqualTo(5);
    }

    @Test
    @DisplayName("종료일 당일은 아직 지연이 아니다")
    void endDateItselfIsNotYetDelayed() {
        assertThat(assess(80, "2026-09-10").status()).isEqualTo(DelayStatus.AT_RISK);
        assertThat(assess(80, "2026-09-11").status()).isEqualTo(DelayStatus.DELAYED);
        assertThat(assess(80, "2026-09-11").delayDays()).isEqualTo(1);
    }

    @Test
    @DisplayName("하루짜리 업무는 그날 안에 완료되기를 기대한다")
    void singleDayTaskExpectsFullProgressSameDay() {
        var assessment = DelayCalculator.assess(START, START, 0, START);

        assertThat(assessment.expectedProgress()).isEqualTo(100);
        assertThat(assessment.status()).isEqualTo(DelayStatus.AT_RISK);
    }

    private static DelayCalculator.DelayAssessment assess(int progress, String referenceDate) {
        return DelayCalculator.assess(START, END, progress, LocalDate.parse(referenceDate));
    }
}
