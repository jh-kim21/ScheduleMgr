package com.projectflow.domain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The one place project progress is computed (설계 §6, §12.2 "공통 집계 서비스").
 *
 * <p>Nothing here is stored. Progress is derived from the current backlog, the current checkpoints
 * and the current weights, so a stored copy would go stale the moment any of them moved
 * (설계 §11.3-8). That is also why re-opening an item, adding scope or editing a weight needs no
 * recalculation step: the next read <em>is</em> the recalculation.
 *
 * <h2>산정 전은 0%가 아니다</h2>
 * A percentage of {@code null} means "not yet computable" — no aggregation units, no checkpoints,
 * no agreed Hybrid ratio. It is never flattened to 0, and a parent never silently drops such a
 * child: it excludes it from the average and raises {@code incompleteChildren} so the screen can
 * say the number is partial (지시서 5-B).
 *
 * <h2>전환 정책 — 기존 숫자를 바꾸지 않는다</h2>
 * Two things protect existing projects. A Work Package with no execution mode keeps using its
 * manually entered progress ({@link ProgressBasis#MANUAL}), and a parent whose direct children
 * carry no weights falls back to the leaf-count weighting this app has always used
 * ({@link ProgressBasis#LEGACY_ROLLUP}). Where nothing has opted in, every number is identical to
 * before Step 5; the design's weighted formula takes over branch by branch as weights are entered.
 */
public final class ProgressCalculator {

    private ProgressCalculator() {
    }

    /**
     * @param backlogByWorkPackage aggregated, non-archived Backlog entries keyed by owning
     *                             Work Package (Epic and Task must already be filtered out — they
     *                             are not aggregation units, 설계 §6.1)
     * @param checkpointsByWbsItem acceptance checkpoints keyed by Work Package
     * @return one result per WBS item id
     */
    public static Map<Long, ProgressResult> compute(
            List<WbsNode> roots,
            Map<Long, List<BacklogItem>> backlogByWorkPackage,
            Map<Long, List<AcceptanceCheckpoint>> checkpointsByWbsItem) {

        Map<Long, ProgressResult> results = new HashMap<>();
        for (WbsNode root : roots) {
            visit(root, backlogByWorkPackage, checkpointsByWbsItem, results);
        }
        return results;
    }

    /**
     * Project-level progress: the same rollup applied to the root nodes.
     *
     * <p>Computed from the roots rather than from every leaf, so a weight set on a top-level branch
     * means what it says — the branch's share of the whole project.
     */
    public static ProgressResult projectProgress(List<WbsNode> roots,
                                                   Map<Long, ProgressResult> results) {
        return rollUp(roots, results);
    }

    private static Contribution visit(WbsNode node,
                                       Map<Long, List<BacklogItem>> backlogByWorkPackage,
                                       Map<Long, List<AcceptanceCheckpoint>> checkpointsByWbsItem,
                                       Map<Long, ProgressResult> results) {
        WbsItem item = node.item();

        if (!node.children().isEmpty()) {
            int leaves = 0;
            for (WbsNode child : node.children()) {
                leaves += visit(child, backlogByWorkPackage, checkpointsByWbsItem, results).leafCount();
            }
            ProgressResult rolled = rollUp(node.children(), results);
            results.put(item.getId(), rolled);
            return new Contribution(rolled, leaves);
        }

        ProgressResult own = leafProgress(item, backlogByWorkPackage, checkpointsByWbsItem);
        results.put(item.getId(), own);
        return new Contribution(own, 1);
    }

    private static ProgressResult leafProgress(WbsItem item,
                                                 Map<Long, List<BacklogItem>> backlogByWorkPackage,
                                                 Map<Long, List<AcceptanceCheckpoint>> checkpointsByWbsItem) {
        if (!item.workPackage()) {
            // 자식이 아직 없는 Summary. 상위는 자식 결과만 집계하므로(설계 §6.1) 셀 것이 없다.
            return ProgressResult.notEstimable("하위 항목이 없는 Summary입니다.");
        }

        ExecutionMode mode = item.getExecutionMode();
        if (mode == null) {
            // 전환 정책: 실행 방식을 고르기 전에는 예전처럼 입력한 진행률을 쓴다.
            return new ProgressResult((double) item.getProgress(), ProgressBasis.MANUAL,
                    false, false, null);
        }

        Double agile = agileProgress(backlogByWorkPackage.get(item.getId()));
        Double waterfall = waterfallProgress(checkpointsByWbsItem.get(item.getId()));

        return switch (mode) {
            case AGILE -> agile == null
                    ? ProgressResult.notEstimable("집계 대상 Story·Bug가 없습니다.")
                    : new ProgressResult(agile, ProgressBasis.AGILE, false, false, null);
            case WATERFALL -> waterfall == null
                    ? ProgressResult.notEstimable("승인 체크포인트가 없습니다.")
                    : new ProgressResult(waterfall, ProgressBasis.WATERFALL, false, false, null);
            case HYBRID -> hybridProgress(item, agile, waterfall);
        };
    }

    /** 100 × Σ(완료 항목 가중치) / Σ(집계 대상 가중치). {@code null} 가중치는 균등(1)으로 본다. */
    private static Double agileProgress(List<BacklogItem> items) {
        if (items == null || items.isEmpty()) {
            return null;
        }
        double denominator = 0;
        double numerator = 0;
        for (BacklogItem item : items) {
            double weight = weightOf(item.getProgressWeight());
            denominator += weight;
            if (item.getStatus() == BacklogStatus.DONE) {
                numerator += weight;
            }
        }
        // 모든 가중치를 0으로 적어 넣은 경우. 0%로 단정할 근거가 없다.
        return denominator == 0 ? null : 100 * numerator / denominator;
    }

    /** 100 × Σ(승인된 체크포인트 가중치) / Σ(전체 체크포인트 가중치). */
    private static Double waterfallProgress(List<AcceptanceCheckpoint> checkpoints) {
        if (checkpoints == null || checkpoints.isEmpty()) {
            return null;
        }
        double denominator = 0;
        double numerator = 0;
        for (AcceptanceCheckpoint checkpoint : checkpoints) {
            double weight = weightOf(checkpoint.getWeight());
            denominator += weight;
            if (checkpoint.approved()) {
                numerator += weight;
            }
        }
        return denominator == 0 ? null : 100 * numerator / denominator;
    }

    /**
     * α × Agile + (1 − α) × Waterfall.
     *
     * <p>All three inputs are required. A missing ratio means the split was never agreed, and a
     * missing component means one side has nothing to measure — in both cases a number would be
     * invented rather than computed, so the answer is 산정 전 (설계 §6.3: 비중은 실행 전에 정한다).
     */
    private static ProgressResult hybridProgress(WbsItem item, Double agile, Double waterfall) {
        Integer ratio = item.getAgileRatio();
        if (ratio == null) {
            return ProgressResult.notEstimable("Hybrid 비중(α)이 정해지지 않았습니다.");
        }
        if (agile == null && waterfall == null) {
            return ProgressResult.notEstimable("집계 대상 Story·Bug와 승인 체크포인트가 모두 없습니다.");
        }
        if (agile == null) {
            return ProgressResult.notEstimable("Agile 요소에 집계 대상 Story·Bug가 없습니다.");
        }
        if (waterfall == null) {
            return ProgressResult.notEstimable("승인 요소에 체크포인트가 없습니다.");
        }
        double alpha = ratio / 100.0;
        return new ProgressResult(alpha * agile + (1 - alpha) * waterfall,
                ProgressBasis.HYBRID, false, false, null);
    }

    /**
     * Σ(직계 자식 가중치 × 자식 진척) / Σ(직계 자식 가중치).
     *
     * <p>With no weights anywhere among the direct children this falls back to leaf-count
     * weighting, which is what the app has always done — see the transition policy above. With
     * weights present, children that lack one are left out of the average and reported through
     * {@code incompleteWeights} rather than being treated as zero.
     */
    private static ProgressResult rollUp(List<WbsNode> children, Map<Long, ProgressResult> results) {
        boolean anyWeight = children.stream().anyMatch(child -> child.item().getWeight() != null);
        boolean incompleteChildren = false;
        boolean incompleteWeights = false;

        double weighted = 0;
        double weightSum = 0;

        for (WbsNode child : children) {
            ProgressResult childResult = results.get(child.item().getId());
            if (childResult == null || childResult.percent() == null) {
                incompleteChildren = true;
                continue;
            }
            if (childResult.incomplete()) {
                incompleteChildren = true;
            }

            Integer declared = child.item().getWeight();
            double weight;
            if (anyWeight) {
                if (declared == null) {
                    // 조용히 빼지 않고 불완전으로 알린다 (지시서 5-B).
                    incompleteWeights = true;
                    continue;
                }
                weight = declared;
            } else {
                weight = leafCount(child);
            }
            weighted += weight * childResult.percent();
            weightSum += weight;
        }

        if (weightSum == 0) {
            return new ProgressResult(null, ProgressBasis.NOT_ESTIMABLE, incompleteWeights, true,
                    anyWeight ? "하위 가중치의 합이 0이거나 산정 가능한 하위가 없습니다."
                              : "산정 가능한 하위 항목이 없습니다.");
        }
        return new ProgressResult(weighted / weightSum,
                anyWeight ? ProgressBasis.ROLLUP : ProgressBasis.LEGACY_ROLLUP,
                incompleteWeights, incompleteChildren, null);
    }

    private static int leafCount(WbsNode node) {
        if (node.children().isEmpty()) {
            return 1;
        }
        int total = 0;
        for (WbsNode child : node.children()) {
            total += leafCount(child);
        }
        return total;
    }

    /** 미입력 가중치는 균등(1)으로 본다 — "가중치를 아직 안 넣었다"는 "비중이 같다"의 흔한 표현이다. */
    private static double weightOf(Integer weight) {
        return weight == null ? 1 : weight;
    }

    private record Contribution(ProgressResult result, int leafCount) {
    }

    /**
     * @param percent            0~100, 반올림하지 않은 값. {@code null}이면 <b>산정 전</b>이며 0%가 아니다.
     *                           반올림은 표시 단계에서만 한다 (지시서 완료 기준)
     * @param basis              어떻게 계산된 값인가
     * @param incompleteWeights  일부 하위에 가중치가 없어 평균에서 빠졌다
     * @param incompleteChildren 일부 하위가 산정 전이거나 그 자체로 불완전하다
     * @param note               산정 전인 이유 (사람에게 보여줄 한 줄)
     */
    public record ProgressResult(
            Double percent,
            ProgressBasis basis,
            boolean incompleteWeights,
            boolean incompleteChildren,
            String note
    ) {
        static ProgressResult notEstimable(String note) {
            return new ProgressResult(null, ProgressBasis.NOT_ESTIMABLE, false, false, note);
        }

        /** Whether the number, if any, is only part of the picture. */
        public boolean incomplete() {
            return incompleteWeights || incompleteChildren;
        }

        /** Rounded for display. {@code null} stays null — 산정 전은 숫자가 아니다. */
        public Integer displayPercent() {
            return percent == null ? null : (int) Math.round(percent);
        }
    }
}
