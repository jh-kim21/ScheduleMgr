package com.projectflow.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class ScheduleCalculatorTest {

    private static final Long PROJECT_ID = 1L;

    @Nested
    @DisplayName("analyze")
    class Analyze {

        @Test
        @DisplayName("lag 0이면 선행 종료 다음 날이 가장 이른 시작일이 된다")
        void earliestStartIsDayAfterPredecessor() {
            List<WbsItem> items = List.of(
                    item(1L, null, "선행", 0, "2026-09-01", "2026-09-10"),
                    item(2L, null, "후행", 1, "2026-09-20", "2026-09-30")
            );

            var analysis = ScheduleCalculator.analyze(
                    WbsTreeAssembler.assemble(items), List.of(dependency(1L, 2L, 0)));

            assertThat(analysis.earliestStarts()).containsEntry(2L, LocalDate.parse("2026-09-11"));
            assertThat(analysis.violatedTaskIds()).isEmpty();
        }

        @Test
        @DisplayName("lag 일수만큼 가장 이른 시작일이 뒤로 밀린다")
        void lagPushesEarliestStart() {
            List<WbsItem> items = List.of(
                    item(1L, null, "선행", 0, "2026-09-01", "2026-09-10"),
                    item(2L, null, "후행", 1, "2026-09-20", "2026-09-30")
            );

            var analysis = ScheduleCalculator.analyze(
                    WbsTreeAssembler.assemble(items), List.of(dependency(1L, 2L, 3)));

            assertThat(analysis.earliestStarts()).containsEntry(2L, LocalDate.parse("2026-09-14"));
        }

        @Test
        @DisplayName("선행보다 먼저 시작하는 후행은 위반으로 표시된다")
        void flagsSuccessorStartingTooEarly() {
            List<WbsItem> items = List.of(
                    item(1L, null, "선행", 0, "2026-09-01", "2026-09-10"),
                    item(2L, null, "후행", 1, "2026-09-05", "2026-09-15")
            );

            var analysis = ScheduleCalculator.analyze(
                    WbsTreeAssembler.assemble(items), List.of(dependency(1L, 2L, 0)));

            assertThat(analysis.violatedTaskIds()).containsExactly(2L);
        }

        @Test
        @DisplayName("선행이 여러 개면 가장 늦은 제약이 적용된다")
        void takesLatestConstraintAmongPredecessors() {
            List<WbsItem> items = List.of(
                    item(1L, null, "선행 A", 0, "2026-09-01", "2026-09-10"),
                    item(2L, null, "선행 B", 1, "2026-09-01", "2026-09-25"),
                    item(3L, null, "후행", 2, "2026-10-01", "2026-10-10")
            );

            var analysis = ScheduleCalculator.analyze(
                    WbsTreeAssembler.assemble(items),
                    List.of(dependency(1L, 3L, 0), dependency(2L, 3L, 0)));

            assertThat(analysis.earliestStarts()).containsEntry(3L, LocalDate.parse("2026-09-26"));
        }

        @Test
        @DisplayName("일정이 없는 항목은 제약도 위반도 만들지 않는다")
        void ignoresUndatedTasks() {
            List<WbsItem> items = List.of(
                    item(1L, null, "일정 미정", 0, null, null),
                    item(2L, null, "후행", 1, "2026-09-05", "2026-09-15")
            );

            var analysis = ScheduleCalculator.analyze(
                    WbsTreeAssembler.assemble(items), List.of(dependency(1L, 2L, 0)));

            assertThat(analysis.earliestStarts()).isEmpty();
            assertThat(analysis.violatedTaskIds()).isEmpty();
        }

        @Test
        @DisplayName("Summary 항목의 집계된 일정으로 제약을 판정한다")
        void usesRolledUpDatesForSummaryTasks() {
            List<WbsItem> items = List.of(
                    item(1L, null, "설계", 0, null, null),
                    item(2L, 1L, "설계 하위", 0, "2026-09-01", "2026-09-30"),
                    item(3L, null, "개발", 1, "2026-09-15", "2026-09-25")
            );

            // 선행 '설계'의 집계 종료일은 09-30이므로 09-15에 시작하는 '개발'은 위반이다.
            var analysis = ScheduleCalculator.analyze(
                    WbsTreeAssembler.assemble(items), List.of(dependency(1L, 3L, 0)));

            assertThat(analysis.earliestStarts()).containsEntry(3L, LocalDate.parse("2026-10-01"));
            assertThat(analysis.violatedTaskIds()).containsExactly(3L);
        }
    }

    @Nested
    @DisplayName("relax")
    class Relax {

        @Test
        @DisplayName("위반한 후행을 기간을 유지한 채 뒤로 밀어낸다")
        void shiftsViolatingSuccessorPreservingDuration() {
            List<WbsItem> items = mutableList(
                    item(1L, null, "선행", 0, "2026-09-01", "2026-09-10"),
                    item(2L, null, "후행", 1, "2026-09-05", "2026-09-15")
            );

            Set<Long> shifted = ScheduleCalculator.relax(items, List.of(dependency(1L, 2L, 0)));

            assertThat(shifted).containsExactly(2L);
            WbsItem successor = items.get(1);
            assertThat(successor.getStartDate()).isEqualTo(LocalDate.parse("2026-09-11"));
            assertThat(successor.getEndDate()).isEqualTo(LocalDate.parse("2026-09-21"));
        }

        @Test
        @DisplayName("연쇄 의존성을 한 번의 호출로 모두 해소한다")
        void propagatesThroughDependencyChain() {
            List<WbsItem> items = mutableList(
                    item(1L, null, "A", 0, "2026-09-01", "2026-09-10"),
                    item(2L, null, "B", 1, "2026-09-01", "2026-09-05"),
                    item(3L, null, "C", 2, "2026-09-01", "2026-09-03")
            );

            Set<Long> shifted = ScheduleCalculator.relax(
                    items, List.of(dependency(1L, 2L, 0), dependency(2L, 3L, 0)));

            assertThat(shifted).containsExactlyInAnyOrder(2L, 3L);
            assertThat(items.get(1).getStartDate()).isEqualTo(LocalDate.parse("2026-09-11"));
            assertThat(items.get(1).getEndDate()).isEqualTo(LocalDate.parse("2026-09-15"));
            assertThat(items.get(2).getStartDate()).isEqualTo(LocalDate.parse("2026-09-16"));
            assertThat(items.get(2).getEndDate()).isEqualTo(LocalDate.parse("2026-09-18"));
        }

        @Test
        @DisplayName("Summary 항목이 위반하면 하위 leaf 전체를 함께 밀어낸다")
        void shiftsWholeSubtreeWhenSummaryViolates() {
            List<WbsItem> items = mutableList(
                    item(1L, null, "선행", 0, "2026-09-01", "2026-09-10"),
                    item(2L, null, "개발", 1, null, null),
                    item(3L, 2L, "API", 0, "2026-09-05", "2026-09-08"),
                    item(4L, 2L, "화면", 1, "2026-09-09", "2026-09-12")
            );

            Set<Long> shifted = ScheduleCalculator.relax(items, List.of(dependency(1L, 2L, 0)));

            // '개발'의 집계 시작일 09-05 -> 09-11 이므로 하위 두 항목이 6일씩 밀린다.
            assertThat(shifted).containsExactlyInAnyOrder(3L, 4L);
            assertThat(items.get(2).getStartDate()).isEqualTo(LocalDate.parse("2026-09-11"));
            assertThat(items.get(2).getEndDate()).isEqualTo(LocalDate.parse("2026-09-14"));
            assertThat(items.get(3).getStartDate()).isEqualTo(LocalDate.parse("2026-09-15"));
            assertThat(items.get(3).getEndDate()).isEqualTo(LocalDate.parse("2026-09-18"));
        }

        @Test
        @DisplayName("위반이 없으면 아무 항목도 옮기지 않는다")
        void leavesValidScheduleUntouched() {
            List<WbsItem> items = mutableList(
                    item(1L, null, "선행", 0, "2026-09-01", "2026-09-10"),
                    item(2L, null, "후행", 1, "2026-09-20", "2026-09-30")
            );

            Set<Long> shifted = ScheduleCalculator.relax(items, List.of(dependency(1L, 2L, 0)));

            assertThat(shifted).isEmpty();
            assertThat(items.get(1).getStartDate()).isEqualTo(LocalDate.parse("2026-09-20"));
        }

        @Test
        @DisplayName("선후행 관계가 없으면 그대로 통과한다")
        void handlesEmptyDependencies() {
            List<WbsItem> items = mutableList(item(1L, null, "단독", 0, "2026-09-01", "2026-09-10"));

            assertThatCode(() -> ScheduleCalculator.relax(items, List.of())).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("상위·하위 항목 사이의 선후행 관계")
    class SameBranch {

        /**
         * 이런 관계는 만족할 수 있는 일정이 없어 relax가 수렴하지 못한다. 그래서 등록/수정 시점과
         * 재계산 진입 시점에 미리 걸러내고, 아래 두 테스트가 그 전제를 고정한다.
         */
        @Test
        @DisplayName("부모 → 자식 관계는 relax가 수렴하지 않는다")
        void parentToChildDoesNotConverge() {
            List<WbsItem> items = mutableList(
                    item(1L, null, "개발", 0, null, null),
                    item(2L, 1L, "배치 개발", 0, "2026-09-01", "2026-09-10")
            );

            assertThatCode(() -> ScheduleCalculator.relax(items, List.of(dependency(1L, 2L, 0))))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("자식 → 부모 관계도 relax가 수렴하지 않는다")
        void childToParentDoesNotConverge() {
            List<WbsItem> items = mutableList(
                    item(1L, null, "개발", 0, null, null),
                    item(2L, 1L, "배치 개발", 0, "2026-09-01", "2026-09-10")
            );

            assertThatCode(() -> ScheduleCalculator.relax(items, List.of(dependency(2L, 1L, 0))))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("직계가 아닌 조상·자손도 같은 가지로 판정한다")
        void detectsIndirectAncestor() {
            List<WbsItem> items = List.of(
                    item(1L, null, "개발", 0, null, null),
                    item(2L, 1L, "API", 0, null, null),
                    item(3L, 2L, "배치", 0, "2026-09-01", "2026-09-10")
            );

            assertThat(ScheduleCalculator.onSameBranch(items, 1L, 3L)).isTrue();
            assertThat(ScheduleCalculator.onSameBranch(items, 3L, 1L)).isTrue();
        }

        @Test
        @DisplayName("형제나 남남은 같은 가지가 아니다")
        void siblingsAreNotSameBranch() {
            List<WbsItem> items = List.of(
                    item(1L, null, "개발", 0, null, null),
                    item(2L, 1L, "API", 0, "2026-09-01", "2026-09-10"),
                    item(3L, 1L, "배치", 1, "2026-09-11", "2026-09-20"),
                    item(4L, null, "설계", 1, "2026-08-01", "2026-08-10")
            );

            assertThat(ScheduleCalculator.onSameBranch(items, 2L, 3L)).isFalse();
            assertThat(ScheduleCalculator.onSameBranch(items, 2L, 4L)).isFalse();
        }

        @Test
        @DisplayName("selfReferentialDependencies는 문제되는 관계만 골라낸다")
        void picksOutOnlyOffendingDependencies() {
            List<WbsItem> items = List.of(
                    item(1L, null, "개발", 0, null, null),
                    item(2L, 1L, "API", 0, "2026-09-01", "2026-09-10"),
                    item(3L, 1L, "배치", 1, "2026-09-11", "2026-09-20")
            );
            WbsDependency offending = dependency(1L, 2L, 0);
            WbsDependency fine = dependency(2L, 3L, 0);

            assertThat(ScheduleCalculator.selfReferentialDependencies(items, List.of(offending, fine)))
                    .containsExactly(offending);
        }
    }

    private static List<WbsItem> mutableList(WbsItem... items) {
        return new ArrayList<>(List.of(items));
    }

    private static WbsItem item(Long id, Long parentId, String name, int sortOrder,
                                 String startDate, String endDate) {
        WbsItem item = new WbsItem(
                PROJECT_ID, parentId, name, null,
                startDate == null ? null : LocalDate.parse(startDate),
                endDate == null ? null : LocalDate.parse(endDate),
                0, sortOrder);
        ReflectionTestUtils.setField(item, "id", id);
        return item;
    }

    private static WbsDependency dependency(Long predecessorId, Long successorId, int lagDays) {
        return new WbsDependency(PROJECT_ID, predecessorId, successorId, lagDays);
    }
}
