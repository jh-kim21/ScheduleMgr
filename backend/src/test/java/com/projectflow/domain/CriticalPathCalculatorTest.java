package com.projectflow.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CriticalPathCalculatorTest {

    private static final Long PROJECT_ID = 1L;

    @Nested
    @DisplayName("임계 경로 판정")
    class Critical {

        @Test
        @DisplayName("빈틈 없이 이어진 사슬은 전부 임계 경로가 된다")
        void tightChainIsAllCritical() {
            List<WbsItem> items = List.of(
                    item(1L, null, "설계", 0, "2026-09-01", "2026-09-10"),
                    item(2L, null, "개발", 1, "2026-09-11", "2026-09-20")
            );
            WbsDependency edge = dependency(10L, 1L, 2L, 0);

            var analysis = CriticalPathCalculator.analyze(WbsTreeAssembler.assemble(items), List.of(edge));

            assertThat(analysis.criticalTaskIds()).containsExactlyInAnyOrder(1L, 2L);
            assertThat(analysis.floatDays()).containsEntry(1L, 0L).containsEntry(2L, 0L);
            assertThat(analysis.criticalDependencyIds()).containsExactly(10L);
        }

        @Test
        @DisplayName("선행에 여유가 있으면 그만큼 float이 잡히고 임계 경로에서 빠진다")
        void gapBecomesFloat() {
            List<WbsItem> items = List.of(
                    item(1L, null, "설계", 0, "2026-09-01", "2026-09-10"),
                    item(2L, null, "개발", 1, "2026-09-15", "2026-09-24")
            );
            WbsDependency edge = dependency(10L, 1L, 2L, 0);

            var analysis = CriticalPathCalculator.analyze(WbsTreeAssembler.assemble(items), List.of(edge));

            assertThat(analysis.floatDays()).containsEntry(1L, 4L).containsEntry(2L, 0L);
            assertThat(analysis.criticalTaskIds()).containsExactly(2L);
            assertThat(analysis.criticalDependencyIds()).isEmpty();
        }

        @Test
        @DisplayName("lag는 계획상 대기이므로 여유로 세지 않는다")
        void lagIsNotFloat() {
            List<WbsItem> items = List.of(
                    item(1L, null, "설계", 0, "2026-09-01", "2026-09-10"),
                    item(2L, null, "개발", 1, "2026-09-14", "2026-09-24")
            );
            WbsDependency edge = dependency(10L, 1L, 2L, 3);

            var analysis = CriticalPathCalculator.analyze(WbsTreeAssembler.assemble(items), List.of(edge));

            assertThat(analysis.floatDays()).containsEntry(1L, 0L);
            assertThat(analysis.criticalTaskIds()).containsExactlyInAnyOrder(1L, 2L);
            assertThat(analysis.criticalDependencyIds()).containsExactly(10L);
        }

        @Test
        @DisplayName("두 갈래 중 긴 쪽만 임계 경로가 된다")
        void onlyTheLongerBranchIsCritical() {
            List<WbsItem> items = List.of(
                    item(1L, null, "긴 선행", 0, "2026-09-01", "2026-09-10"),
                    item(2L, null, "짧은 선행", 1, "2026-09-01", "2026-09-05"),
                    item(3L, null, "후행", 2, "2026-09-11", "2026-09-20")
            );

            var analysis = CriticalPathCalculator.analyze(
                    WbsTreeAssembler.assemble(items),
                    List.of(dependency(10L, 1L, 3L, 0), dependency(11L, 2L, 3L, 0)));

            assertThat(analysis.criticalTaskIds()).containsExactlyInAnyOrder(1L, 3L);
            assertThat(analysis.floatDays()).containsEntry(2L, 5L);
            assertThat(analysis.criticalDependencyIds()).containsExactly(10L);
        }

        @Test
        @DisplayName("프로젝트 종료일보다 먼저 끝나는 사슬은 그 차이만큼 float을 갖는다")
        void chainEndingEarlyHasFloat() {
            List<WbsItem> items = List.of(
                    item(1L, null, "짧은 사슬 선행", 0, "2026-09-01", "2026-09-05"),
                    item(2L, null, "짧은 사슬 후행", 1, "2026-09-06", "2026-09-10"),
                    item(3L, null, "긴 사슬 선행", 2, "2026-09-01", "2026-09-10"),
                    item(4L, null, "긴 사슬 후행", 3, "2026-09-11", "2026-09-30")
            );

            var analysis = CriticalPathCalculator.analyze(
                    WbsTreeAssembler.assemble(items),
                    List.of(dependency(10L, 1L, 2L, 0), dependency(11L, 3L, 4L, 0)));

            assertThat(analysis.criticalTaskIds()).containsExactlyInAnyOrder(3L, 4L);
            assertThat(analysis.floatDays()).containsEntry(1L, 20L).containsEntry(2L, 20L);
        }

        @Test
        @DisplayName("계획이 이미 제약을 어기면 float이 음수가 되고 임계 경로로 본다")
        void violatedPlanGetsNegativeFloat() {
            List<WbsItem> items = List.of(
                    item(1L, null, "선행", 0, "2026-09-01", "2026-09-10"),
                    item(2L, null, "후행", 1, "2026-09-05", "2026-09-20")
            );

            var analysis = CriticalPathCalculator.analyze(
                    WbsTreeAssembler.assemble(items), List.of(dependency(10L, 1L, 2L, 0)));

            assertThat(analysis.floatDays()).containsEntry(1L, -6L);
            assertThat(analysis.criticalTaskIds()).containsExactlyInAnyOrder(1L, 2L);
        }
    }

    @Nested
    @DisplayName("참여 대상")
    class Participation {

        @Test
        @DisplayName("선후행 관계가 없는 항목은 사슬이 없으므로 판정에서 빠진다")
        void unlinkedTasksAreExcluded() {
            List<WbsItem> items = List.of(
                    item(1L, null, "선행", 0, "2026-09-01", "2026-09-10"),
                    item(2L, null, "후행", 1, "2026-09-11", "2026-09-20"),
                    item(3L, null, "외톨이", 2, "2026-09-01", "2026-12-31")
            );

            var analysis = CriticalPathCalculator.analyze(
                    WbsTreeAssembler.assemble(items), List.of(dependency(10L, 1L, 2L, 0)));

            assertThat(analysis.floatDays()).doesNotContainKey(3L);
            assertThat(analysis.criticalTaskIds()).containsExactlyInAnyOrder(1L, 2L);
        }

        @Test
        @DisplayName("일정이 없는 항목은 판정할 수 없어 빠진다")
        void undatedTasksAreExcluded() {
            List<WbsItem> items = List.of(
                    item(1L, null, "선행", 0, "2026-09-01", "2026-09-10"),
                    item(2L, null, "일정 미정", 1, null, null)
            );

            var analysis = CriticalPathCalculator.analyze(
                    WbsTreeAssembler.assemble(items), List.of(dependency(10L, 1L, 2L, 0)));

            assertThat(analysis.floatDays()).doesNotContainKey(2L);
            assertThat(analysis.criticalDependencyIds()).isEmpty();
        }

        @Test
        @DisplayName("선후행 관계가 하나도 없으면 임계 경로도 없다")
        void noDependenciesMeansNoCriticalPath() {
            List<WbsItem> items = List.of(item(1L, null, "혼자", 0, "2026-09-01", "2026-09-10"));

            var analysis = CriticalPathCalculator.analyze(WbsTreeAssembler.assemble(items), List.of());

            assertThat(analysis.criticalTaskIds()).isEmpty();
            assertThat(analysis.floatDays()).isEmpty();
        }

        @Test
        @DisplayName("Summary에 걸린 관계는 집계된 일정으로 판정한다")
        void summaryUsesAggregatedSchedule() {
            List<WbsItem> items = List.of(
                    item(1L, null, "설계", 0, null, null),
                    item(2L, 1L, "화면 설계", 0, "2026-09-01", "2026-09-05"),
                    item(3L, 1L, "상세 설계", 1, "2026-09-06", "2026-09-10"),
                    item(4L, null, "개발", 1, "2026-09-11", "2026-09-20")
            );

            var analysis = CriticalPathCalculator.analyze(
                    WbsTreeAssembler.assemble(items), List.of(dependency(10L, 1L, 4L, 0)));

            // Summary 1의 집계 종료일은 2026-09-10이므로 개발이 바로 다음 날 시작 = 여유 0.
            assertThat(analysis.criticalTaskIds()).containsExactlyInAnyOrder(1L, 4L);
            assertThat(analysis.floatDays()).containsEntry(1L, 0L);
            assertThat(analysis.criticalDependencyIds()).containsExactly(10L);
        }
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

    private static WbsDependency dependency(Long id, Long predecessorId, Long successorId, int lagDays) {
        WbsDependency dependency = new WbsDependency(PROJECT_ID, predecessorId, successorId, lagDays);
        ReflectionTestUtils.setField(dependency, "id", id);
        return dependency;
    }
}
