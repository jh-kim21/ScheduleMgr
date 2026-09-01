package com.projectflow.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class RaidAssessorTest {

    private static final Long PROJECT_ID = 1L;
    private static final LocalDate TODAY = LocalDate.parse("2026-09-10");

    @Nested
    @DisplayName("노출도 (확률 × 영향)")
    class Exposure {

        @Test
        @DisplayName("확률과 영향이 모두 있으면 곱으로 환산한다")
        void multipliesWeights() {
            assertThat(assess(RaidLevel.HIGH, RaidLevel.HIGH).exposure()).isEqualTo(9);
            assertThat(assess(RaidLevel.HIGH, RaidLevel.MEDIUM).exposure()).isEqualTo(6);
            assertThat(assess(RaidLevel.MEDIUM, RaidLevel.MEDIUM).exposure()).isEqualTo(4);
            assertThat(assess(RaidLevel.LOW, RaidLevel.HIGH).exposure()).isEqualTo(3);
            assertThat(assess(RaidLevel.LOW, RaidLevel.MEDIUM).exposure()).isEqualTo(2);
            assertThat(assess(RaidLevel.LOW, RaidLevel.LOW).exposure()).isEqualTo(1);
        }

        @Test
        @DisplayName("6 이상은 높음, 3~4는 보통, 그 아래는 낮음으로 묶는다")
        void bandsTheScore() {
            assertThat(assess(RaidLevel.HIGH, RaidLevel.HIGH).exposureLevel()).isEqualTo(RaidLevel.HIGH);
            assertThat(assess(RaidLevel.HIGH, RaidLevel.MEDIUM).exposureLevel()).isEqualTo(RaidLevel.HIGH);
            assertThat(assess(RaidLevel.MEDIUM, RaidLevel.MEDIUM).exposureLevel()).isEqualTo(RaidLevel.MEDIUM);
            assertThat(assess(RaidLevel.LOW, RaidLevel.HIGH).exposureLevel()).isEqualTo(RaidLevel.MEDIUM);
            assertThat(assess(RaidLevel.LOW, RaidLevel.MEDIUM).exposureLevel()).isEqualTo(RaidLevel.LOW);
            assertThat(assess(RaidLevel.LOW, RaidLevel.LOW).exposureLevel()).isEqualTo(RaidLevel.LOW);
        }

        @Test
        @DisplayName("한쪽만 있으면 환산하지 않는다")
        void needsBothSides() {
            assertThat(assess(RaidLevel.HIGH, null).exposure()).isNull();
            assertThat(assess(null, RaidLevel.HIGH).exposure()).isNull();
            assertThat(assess(null, null).exposureLevel()).isNull();
        }

        @Test
        @DisplayName("종류로 제한하지 않는다 — 이슈에 영향과 확률을 적어도 환산한다")
        void doesNotDependOnType() {
            RaidItem issue = item(RaidType.ISSUE, RaidStatus.OPEN, RaidLevel.HIGH, RaidLevel.HIGH, null);
            assertThat(RaidAssessor.assess(issue, TODAY).exposure()).isEqualTo(9);
        }

        private RaidAssessor.RaidAssessment assess(RaidLevel probability, RaidLevel impact) {
            return RaidAssessor.assess(
                    item(RaidType.RISK, RaidStatus.OPEN, probability, impact, null), TODAY);
        }
    }

    @Nested
    @DisplayName("기한 초과 판정")
    class Overdue {

        @Test
        @DisplayName("기한이 지나고 종결되지 않았으면 초과로 본다")
        void pastDueAndOpen() {
            var assessment = RaidAssessor.assess(
                    item(RaidType.RISK, RaidStatus.IN_PROGRESS, null, null, "2026-09-03"), TODAY);

            assertThat(assessment.overdue()).isTrue();
            assertThat(assessment.overdueDays()).isEqualTo(7);
        }

        @Test
        @DisplayName("기한 당일은 아직 초과가 아니다")
        void dueTodayIsNotOverdue() {
            var assessment = RaidAssessor.assess(
                    item(RaidType.RISK, RaidStatus.OPEN, null, null, "2026-09-10"), TODAY);

            assertThat(assessment.overdue()).isFalse();
            assertThat(assessment.overdueDays()).isZero();
        }

        @Test
        @DisplayName("종결된 항목은 기한이 지났어도 초과로 보지 않는다")
        void closedIsNeverOverdue() {
            var assessment = RaidAssessor.assess(
                    item(RaidType.RISK, RaidStatus.CLOSED, null, null, "2026-08-01"), TODAY);

            assertThat(assessment.overdue()).isFalse();
            assertThat(assessment.overdueDays()).isZero();
        }

        @Test
        @DisplayName("기한이 없으면 초과 판정 대상이 아니다")
        void noDueDateNoJudgement() {
            var assessment = RaidAssessor.assess(
                    item(RaidType.ASSUMPTION, RaidStatus.OPEN, null, null, null), TODAY);

            assertThat(assessment.overdue()).isFalse();
        }
    }

    private static RaidItem item(RaidType type, RaidStatus status,
                                  RaidLevel probability, RaidLevel impact, String dueDate) {
        return new RaidItem(
                PROJECT_ID, type, "제목", null, status, probability, impact, null, null,
                dueDate == null ? null : LocalDate.parse(dueDate), null);
    }
}
