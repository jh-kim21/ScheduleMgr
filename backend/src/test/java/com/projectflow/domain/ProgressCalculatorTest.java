package com.projectflow.domain;

import com.projectflow.domain.ProgressCalculator.ProgressResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The aggregation rules of 설계 §6, including the three figures the Step 5 instruction names as
 * completion criteria, and the ways a number can be <em>absent</em> rather than zero.
 */
class ProgressCalculatorTest {

    private static final Long PROJECT_ID = 1L;

    private final AtomicLong ids = new AtomicLong(900);
    private final Map<Long, List<BacklogItem>> backlog = new HashMap<>();
    private final Map<Long, List<AcceptanceCheckpoint>> checkpoints = new HashMap<>();

    @Nested
    @DisplayName("Agile 집계")
    class Agile {

        @Test
        @DisplayName("동일 가중치 10개 중 6개 완료 시 60%")
        void sixOfTen() {
            WbsItem wp = workPackage("개발", ExecutionMode.AGILE, null);
            for (int i = 0; i < 10; i++) {
                story(wp, null, i < 6);
            }

            assertThat(percentOf(wp)).isEqualTo(60.0);
            assertThat(basisOf(wp)).isEqualTo(ProgressBasis.AGILE);
        }

        @Test
        @DisplayName("가중치 합 20 중 완료 12면 60% — 개수가 아니라 가중치로 센다")
        void weightedSixty() {
            WbsItem wp = workPackage("개발", ExecutionMode.AGILE, null);
            story(wp, 12, true);
            story(wp, 8, false);

            assertThat(percentOf(wp)).isEqualTo(60.0);
        }

        @Test
        @DisplayName("집계 대상이 없으면 0%가 아니라 산정 전")
        void noItemsIsNotZero() {
            WbsItem wp = workPackage("개발", ExecutionMode.AGILE, null);

            assertThat(percentOf(wp)).isNull();
            assertThat(basisOf(wp)).isEqualTo(ProgressBasis.NOT_ESTIMABLE);
            assertThat(resultOf(wp).note()).contains("Story·Bug");
        }

        @Test
        @DisplayName("가중치를 모두 0으로 적으면 분모가 0이라 산정 전")
        void zeroDenominator() {
            WbsItem wp = workPackage("개발", ExecutionMode.AGILE, null);
            story(wp, 0, true);
            story(wp, 0, false);

            assertThat(percentOf(wp)).isNull();
        }
    }

    @Nested
    @DisplayName("Waterfall 집계")
    class Waterfall {

        @Test
        @DisplayName("승인된 체크포인트 가중치 비율")
        void approvedWeightRatio() {
            WbsItem wp = workPackage("인수", ExecutionMode.WATERFALL, null);
            checkpoint(wp, 30, true);
            checkpoint(wp, 70, false);

            assertThat(percentOf(wp)).isEqualTo(30.0);
            assertThat(basisOf(wp)).isEqualTo(ProgressBasis.WATERFALL);
        }

        @Test
        @DisplayName("체크포인트가 없으면 산정 전 — 0%로 단정하지 않는다")
        void noCheckpoints() {
            WbsItem wp = workPackage("인수", ExecutionMode.WATERFALL, null);

            assertThat(percentOf(wp)).isNull();
            assertThat(resultOf(wp).note()).contains("체크포인트");
        }
    }

    @Nested
    @DisplayName("Hybrid 집계")
    class Hybrid {

        @Test
        @DisplayName("비중 0.7, Agile 80%, Waterfall 50%이면 71%")
        void seventyOne() {
            WbsItem wp = workPackage("통합 검증", ExecutionMode.HYBRID, 70);
            // Agile 80%: 가중치 8 완료 / 10 전체
            story(wp, 8, true);
            story(wp, 2, false);
            // Waterfall 50%
            checkpoint(wp, 50, true);
            checkpoint(wp, 50, false);

            assertThat(percentOf(wp)).isEqualTo(71.0);
            assertThat(basisOf(wp)).isEqualTo(ProgressBasis.HYBRID);
        }

        @Test
        @DisplayName("비중이 없으면 산정 전 — 임의의 기본값을 만들지 않는다")
        void ratioRequired() {
            WbsItem wp = workPackage("통합 검증", ExecutionMode.HYBRID, null);
            story(wp, null, true);
            checkpoint(wp, null, true);

            assertThat(percentOf(wp)).isNull();
            assertThat(resultOf(wp).note()).contains("비중");
        }

        @Test
        @DisplayName("한쪽 요소가 비어 있으면 산정 전 — 남은 쪽만으로 비중을 적용하지 않는다")
        void bothComponentsRequired() {
            WbsItem wp = workPackage("통합 검증", ExecutionMode.HYBRID, 70);
            story(wp, null, true);

            assertThat(percentOf(wp)).isNull();
            assertThat(resultOf(wp).note()).contains("체크포인트");
        }
    }

    @Nested
    @DisplayName("상위 WBS 집계")
    class Rollup {

        @Test
        @DisplayName("자식 가중치 20·50·30, 진척 80·40·60이면 부모 54%")
        void fiftyFour() {
            WbsItem parent = summary("제품 기능");
            manual(parent, "사용자 관리", 80, 20);
            manual(parent, "프로젝트 관리", 40, 50);
            manual(parent, "WBS 관리", 60, 30);

            assertThat(percentOf(parent)).isEqualTo(54.0);
            assertThat(basisOf(parent)).isEqualTo(ProgressBasis.ROLLUP);
        }

        @Test
        @DisplayName("가중치가 하나도 없으면 기존과 같은 leaf 개수 가중 평균을 쓴다 (전환 정책)")
        void legacyWeightingWhenNoWeights() {
            // 왼쪽 가지는 leaf 1개(100%), 오른쪽 가지는 leaf 3개(모두 0%).
            // 직계 자식 균등 평균이면 50%지만, 기존 동작은 leaf 개수 가중이라 25%다.
            WbsItem root = summary("루트");
            manual(root, "혼자", 100, null);
            WbsItem branch = summaryUnder(root, "여럿");
            manual(branch, "가", 0, null);
            manual(branch, "나", 0, null);
            manual(branch, "다", 0, null);

            assertThat(percentOf(root)).isEqualTo(25.0);
            assertThat(basisOf(root)).isEqualTo(ProgressBasis.LEGACY_ROLLUP);
        }

        @Test
        @DisplayName("일부 자식만 가중치가 있으면 평균에서 빼고 불완전으로 알린다 — 0%로 세지 않는다")
        void partialWeightsAreReported() {
            WbsItem parent = summary("단계");
            manual(parent, "가중치 있음", 100, 10);
            manual(parent, "가중치 없음", 0, null);

            // 가중치 없는 자식을 0으로 세면 50%가 되지만, 그것은 근거 없는 숫자다.
            assertThat(percentOf(parent)).isEqualTo(100.0);
            assertThat(resultOf(parent).incompleteWeights()).isTrue();
        }

        @Test
        @DisplayName("산정 전인 자식은 조용히 빠지지 않고 불완전 상태를 전파한다")
        void notEstimableChildPropagates() {
            WbsItem parent = summary("단계");
            manual(parent, "센다", 100, 10);
            // 집계 대상이 없는 Agile Work Package → 산정 전
            workPackageUnder(parent, "못 센다", ExecutionMode.AGILE, null, 10);

            assertThat(percentOf(parent)).isEqualTo(100.0);
            assertThat(resultOf(parent).incompleteChildren()).isTrue();
            assertThat(resultOf(parent).incomplete()).isTrue();
        }

        @Test
        @DisplayName("모든 자식이 산정 전이면 부모도 산정 전")
        void allChildrenNotEstimable() {
            WbsItem parent = summary("단계");
            workPackageUnder(parent, "가", ExecutionMode.AGILE, null, null);
            workPackageUnder(parent, "나", ExecutionMode.WATERFALL, null, null);

            assertThat(percentOf(parent)).isNull();
            assertThat(basisOf(parent)).isEqualTo(ProgressBasis.NOT_ESTIMABLE);
        }

        @Test
        @DisplayName("상위의 수동 진행률은 집계에 쓰이지 않는다 (설계 §6.1)")
        void parentManualProgressIgnored() {
            WbsItem parent = summary("단계");
            ReflectionTestUtils.setField(parent, "progress", 99);
            manual(parent, "자식", 10, null);

            assertThat(percentOf(parent)).isEqualTo(10.0);
        }

        @Test
        @DisplayName("반올림하지 않은 값을 돌려준다 — 반올림은 표시 단계에서만")
        void keepsPrecision() {
            WbsItem parent = summary("단계");
            manual(parent, "가", 100, 1);
            manual(parent, "나", 0, 2);

            assertThat(percentOf(parent)).isEqualTo(100.0 / 3);
            assertThat(resultOf(parent).displayPercent()).isEqualTo(33);
        }
    }

    @Nested
    @DisplayName("전환 정책과 예외")
    class Transition {

        @Test
        @DisplayName("실행 방식 미지정 Work Package는 입력한 진행률을 그대로 쓴다")
        void unspecifiedKeepsManualProgress() {
            WbsItem wp = workPackage("예전 업무", null, null);
            ReflectionTestUtils.setField(wp, "progress", 42);

            assertThat(percentOf(wp)).isEqualTo(42.0);
            assertThat(basisOf(wp)).isEqualTo(ProgressBasis.MANUAL);
        }

        @Test
        @DisplayName("하위가 아직 없는 Summary는 산정 전")
        void childlessSummary() {
            WbsItem lonely = summary("전환 중");

            assertThat(percentOf(lonely)).isNull();
            assertThat(resultOf(lonely).note()).contains("하위 항목이 없는");
        }

        @Test
        @DisplayName("보관·Epic·Task는 Agile 분모에 들어가지 않는다 — 호출자가 걸러 넘긴다")
        void aggregationUnitsOnly() {
            // ProgressService가 집계 대상만 넘기므로, 계산기는 받은 것만 센다.
            // 여기서는 Story 2개 중 1개 완료 = 50%가 되는 것으로 그 계약을 고정한다.
            WbsItem wp = workPackage("개발", ExecutionMode.AGILE, null);
            story(wp, null, true);
            story(wp, null, false);

            assertThat(percentOf(wp)).isEqualTo(50.0);
        }
    }

    // ------------------------------------------------------------------ 도우미

    private final List<WbsItem> items = new ArrayList<>();

    private WbsItem register(WbsItem item) {
        ReflectionTestUtils.setField(item, "id", ids.incrementAndGet());
        items.add(item);
        return item;
    }

    private WbsItem summary(String name) {
        return register(new WbsItem(PROJECT_ID, null, name, null, null, null, 0, items.size(),
                WbsNodeType.SUMMARY, null));
    }

    private WbsItem summaryUnder(WbsItem parent, String name) {
        return register(new WbsItem(PROJECT_ID, parent.getId(), name, null, null, null, 0,
                items.size(), WbsNodeType.SUMMARY, null));
    }

    private WbsItem workPackage(String name, ExecutionMode mode, Integer agileRatio) {
        WbsItem item = register(new WbsItem(PROJECT_ID, null, name, null, null, null, 0,
                items.size(), WbsNodeType.WORK_PACKAGE, mode));
        item.restoreProgressBasis(null, agileRatio, null);
        return item;
    }

    private WbsItem workPackageUnder(WbsItem parent, String name, ExecutionMode mode,
                                      Integer agileRatio, Integer weight) {
        WbsItem item = register(new WbsItem(PROJECT_ID, parent.getId(), name, null, null, null, 0,
                items.size(), WbsNodeType.WORK_PACKAGE, mode));
        item.restoreProgressBasis(weight, agileRatio, null);
        return item;
    }

    /** A 미지정 Work Package with a stored progress value — the pre-Step-5 shape. */
    private WbsItem manual(WbsItem parent, String name, int progress, Integer weight) {
        WbsItem item = register(new WbsItem(PROJECT_ID, parent.getId(), name, null, null, null,
                progress, items.size(), WbsNodeType.WORK_PACKAGE, null));
        item.restoreProgressBasis(weight, null, null);
        return item;
    }

    private void story(WbsItem workPackage, Integer weight, boolean done) {
        BacklogItem item = new BacklogItem(PROJECT_ID, workPackage.getId(), null,
                BacklogItemType.STORY, "story", null, BacklogPriority.MEDIUM,
                done ? BacklogStatus.DONE : BacklogStatus.TODO, null, null, null, weight, 0);
        ReflectionTestUtils.setField(item, "id", ids.incrementAndGet());
        backlog.computeIfAbsent(workPackage.getId(), key -> new ArrayList<>()).add(item);
    }

    private void checkpoint(WbsItem workPackage, Integer weight, boolean approved) {
        AcceptanceCheckpoint cp = new AcceptanceCheckpoint(PROJECT_ID, workPackage.getId(),
                "checkpoint", weight, null, 0);
        ReflectionTestUtils.setField(cp, "id", ids.incrementAndGet());
        if (approved) {
            cp.approve("kim");
        }
        checkpoints.computeIfAbsent(workPackage.getId(), key -> new ArrayList<>()).add(cp);
    }

    private ProgressResult resultOf(WbsItem item) {
        Map<Long, ProgressResult> results = ProgressCalculator.compute(
                WbsTreeAssembler.assemble(items), backlog, checkpoints);
        return results.get(item.getId());
    }

    private Double percentOf(WbsItem item) {
        return resultOf(item).percent();
    }

    private ProgressBasis basisOf(WbsItem item) {
        return resultOf(item).basis();
    }
}
