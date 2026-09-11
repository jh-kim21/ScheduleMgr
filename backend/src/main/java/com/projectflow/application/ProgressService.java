package com.projectflow.application;

import com.projectflow.application.dto.ProgressResponse;
import com.projectflow.application.dto.ProgressResponse.BaselineSummary;
import com.projectflow.application.dto.ProgressResponse.CheckpointDetail;
import com.projectflow.application.dto.ProgressResponse.ProjectProgress;
import com.projectflow.application.dto.ProgressResponse.ScopeComparison;
import com.projectflow.application.dto.ProgressResponse.WorkPackageProgress;
import com.projectflow.domain.AcceptanceCheckpoint;
import com.projectflow.domain.AcceptanceCheckpointRepository;
import com.projectflow.domain.AcceptanceStatus;
import com.projectflow.domain.BacklogItem;
import com.projectflow.domain.BacklogItemRepository;
import com.projectflow.domain.BacklogStatus;
import com.projectflow.domain.Baseline;
import com.projectflow.domain.BaselineItem;
import com.projectflow.domain.BaselineRepository;
import com.projectflow.domain.ProgressBasis;
import com.projectflow.domain.ProgressCalculator;
import com.projectflow.domain.ProgressCalculator.ProgressResult;
import com.projectflow.domain.ProjectNotFoundException;
import com.projectflow.domain.ProjectRepository;
import com.projectflow.domain.WbsItem;
import com.projectflow.domain.WbsItemRepository;
import com.projectflow.domain.WbsNode;
import com.projectflow.domain.WbsNodeType;
import com.projectflow.domain.WbsTreeAssembler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The common aggregation service: one place that answers "얼마나 왔나" for every screen
 * (설계 §12.2).
 *
 * <p>Nothing is stored. Every read recomputes from the current backlog, checkpoints and weights,
 * which is why re-opening an item, adding scope or editing a weight needs no explicit
 * recalculation — and why a past report has to be written down separately
 * ({@code ProgressSnapshot}).
 *
 * <p><b>계획 진척은 시간이 아니라 승인된 기준선에서 나온다.</b> Without an approved baseline it is
 * 미산정, full stop: the elapsed share of a plan nobody approved is not a plan. The comparison is
 * made over the baselined Work Packages with the baseline's own weights, and the actual figure is
 * recomputed over that same set — otherwise the two numbers describe different projects
 * (설계 §6.5).
 */
@Service
@Transactional(readOnly = true)
public class ProgressService {

    private final WbsItemRepository wbsItemRepository;
    private final BacklogItemRepository backlogItemRepository;
    private final AcceptanceCheckpointRepository checkpointRepository;
    private final BaselineRepository baselineRepository;
    private final ProjectRepository projectRepository;

    public ProgressService(WbsItemRepository wbsItemRepository,
                            BacklogItemRepository backlogItemRepository,
                            AcceptanceCheckpointRepository checkpointRepository,
                            BaselineRepository baselineRepository,
                            ProjectRepository projectRepository) {
        this.wbsItemRepository = wbsItemRepository;
        this.backlogItemRepository = backlogItemRepository;
        this.checkpointRepository = checkpointRepository;
        this.baselineRepository = baselineRepository;
        this.projectRepository = projectRepository;
    }

    public ProgressResponse getProgress(Long projectId) {
        requireProject(projectId);
        return build(projectId);
    }

    /**
     * Progress per WBS item, for other services to apply the same numbers (지시서 5-C).
     *
     * <p>Used by {@code WbsService} so the WBS screen shows the common result rather than a second
     * opinion.
     */
    public Map<Long, ProgressResult> resultsByWbsItem(Long projectId, List<WbsNode> roots) {
        return ProgressCalculator.compute(roots,
                aggregatedBacklogByWorkPackage(projectId),
                checkpointsByWbsItem(projectId));
    }

    // ------------------------------------------------------------------ 조립

    private ProgressResponse build(Long projectId) {
        LocalDate referenceDate = LocalDate.now();
        List<WbsItem> items = wbsItemRepository.findByProjectId(projectId);
        List<WbsNode> roots = WbsTreeAssembler.assemble(items);

        Map<Long, List<BacklogItem>> backlog = aggregatedBacklogByWorkPackage(projectId);
        Map<Long, List<AcceptanceCheckpoint>> checkpoints = checkpointsByWbsItem(projectId);
        Map<Long, ProgressResult> results = ProgressCalculator.compute(roots, backlog, checkpoints);
        Map<Long, String> codes = new HashMap<>();
        collectCodes(roots, codes);

        List<WorkPackageProgress> workPackages = new ArrayList<>();
        int notEstimable = 0;
        int acceptancePendingCount = 0;
        for (WbsItem item : items) {
            if (!item.workPackage()) {
                continue;
            }
            ProgressResult result = results.getOrDefault(item.getId(),
                    new ProgressResult(null, ProgressBasis.NOT_ESTIMABLE, false, false, null));
            List<BacklogItem> linked = backlog.getOrDefault(item.getId(), List.of());
            List<AcceptanceCheckpoint> cps = checkpoints.getOrDefault(item.getId(), List.of());
            boolean pending = acceptancePending(item, result);

            if (result.percent() == null) {
                notEstimable++;
            }
            if (pending) {
                acceptancePendingCount++;
            }

            workPackages.add(new WorkPackageProgress(
                    item.getId(),
                    codes.get(item.getId()),
                    item.getName(),
                    item.getExecutionMode(),
                    item.getWeight(),
                    item.getAgileRatio(),
                    item.getAcceptanceStatus(),
                    result.percent(),
                    result.basis(),
                    result.note(),
                    linked.size(),
                    (int) linked.stream().filter(i -> i.getStatus() == BacklogStatus.DONE).count(),
                    cps.size(),
                    (int) cps.stream().filter(AcceptanceCheckpoint::approved).count(),
                    pending,
                    cps.stream()
                            .sorted(Comparator.comparingInt(AcceptanceCheckpoint::getSortOrder)
                                    .thenComparing(AcceptanceCheckpoint::getId))
                            .map(cp -> new CheckpointDetail(cp.getId(), cp.getTitle(), cp.getWeight(),
                                    cp.getCompletionCriteria(), cp.approved(), cp.getApprovedBy(),
                                    cp.getApprovedAt()))
                            .toList()
            ));
        }
        workPackages.sort(Comparator.comparing(
                WorkPackageProgress::code, Comparator.nullsLast(Comparator.naturalOrder())));

        ProgressResult projectResult = ProgressCalculator.projectProgress(roots, results);
        Baseline baseline = latestBaseline(projectId);
        List<BaselineItem> baselineItems = baseline == null
                ? List.of()
                : baselineRepository.findItemsByBaselineId(baseline.getId());

        Planned planned = plannedProgress(baselineItems, results, referenceDate);

        return new ProgressResponse(
                referenceDate,
                new ProjectProgress(
                        projectResult.percent(),
                        projectResult.basis(),
                        workPackages.size(),
                        notEstimable,
                        projectResult.incomplete(),
                        planned.planned(),
                        planned.comparable(),
                        planned.variance(),
                        acceptancePendingCount,
                        planned.excludedCount()
                ),
                workPackages,
                baseline == null ? null : new BaselineSummary(baseline.getId(), baseline.getVersion(),
                        baseline.getApprovedBy(), baseline.getApprovedAt(), baseline.getNote(),
                        baselineItems.size()),
                compareScope(items, baselineItems, codes, baseline != null)
        );
    }

    /**
     * Planned progress and the actual figure over the same set.
     *
     * <p>Flat over the baselined Work Packages rather than hierarchical: a baseline stores a
     * snapshot list, not a tree, so the only weighting both sides can share is the baseline's own.
     * Summary rows are skipped by design (not just by missing dates) — a Summary's baseline
     * schedule spans the same ground as its own Work Packages, and averaging both flat would count
     * that ground twice; the actual side has the same shape because a Work Package is always a leaf
     * (설계: "Work Package에는 하위를 둘 수 없다"), so its {@code ProgressResult} is never a rollup.
     *
     * <p><b>결함 수정 (2026-09):</b> planned와 comparable은 반드시 같은 항목 집합 위에서 계산한다.
     * 예전 코드는 {@code weightSum}(날짜 있는 전체)과 {@code actualWeightSum}(그중 산정 가능한 것만)이
     * 달라, variance = comparable − planned가 서로 다른 분모의 두 숫자를 빼는 셈이었다. 이제 날짜가
     * 있고 <em>동시에</em> 산정 가능한 항목만 양쪽에 반영하고, 빠진 항목 수를
     * {@link Planned#excludedCount()}로 실어 화면이 "N개 제외됨"을 말할 수 있게 한다.
     */
    private Planned plannedProgress(List<BaselineItem> baselineItems,
                                      Map<Long, ProgressResult> results,
                                      LocalDate referenceDate) {
        double plannedWeighted = 0;
        double actualWeighted = 0;
        double weightSum = 0;
        int excludedCount = 0;

        for (BaselineItem item : baselineItems) {
            if (item.getNodeType() != WbsNodeType.WORK_PACKAGE) {
                continue;
            }
            if (item.getStartDate() == null || item.getEndDate() == null) {
                continue;
            }
            ProgressResult actual = results.get(item.getWbsItemId());
            if (actual == null || actual.percent() == null) {
                // 산정 전인 항목은 실제 쪽 숫자가 없다. 계획만 넣으면 비교 불가능한 두 범위가
                // 되므로 이 항목은 계획·실제 양쪽에서 함께 뺀다.
                excludedCount++;
                continue;
            }

            double weight = item.getWeight() == null ? 1 : item.getWeight();
            plannedWeighted += weight * elapsedShare(item.getStartDate(), item.getEndDate(), referenceDate);
            weightSum += weight;
            actualWeighted += weight * actual.percent();
        }

        if (weightSum == 0) {
            return new Planned(null, null, null, excludedCount);
        }
        double plannedPercent = plannedWeighted / weightSum;
        double comparable = actualWeighted / weightSum;
        double variance = comparable - plannedPercent;
        return new Planned(plannedPercent, comparable, variance, excludedCount);
    }

    /**
     * Linear share of the baseline period already elapsed, end date inclusive — the same convention
     * the delay judgement uses. This is a <em>planned</em> curve, never treated as actual progress.
     */
    private double elapsedShare(LocalDate start, LocalDate end, LocalDate referenceDate) {
        if (referenceDate.isBefore(start)) {
            return 0;
        }
        long total = ChronoUnit.DAYS.between(start, end) + 1;
        if (total <= 0) {
            return 100;
        }
        long elapsed = ChronoUnit.DAYS.between(start, referenceDate) + 1;
        return Math.min(100.0, 100.0 * elapsed / total);
    }

    /** 진척 100%인데 인수가 남았다 — 최종 완료와 구분해야 하는 상태 (설계 §6.5). */
    private boolean acceptancePending(WbsItem item, ProgressResult result) {
        return item.getAcceptanceStatus() == AcceptanceStatus.PENDING
                && result.percent() != null
                && result.percent() >= 100;
    }

    private ScopeComparison compareScope(List<WbsItem> items, List<BaselineItem> baselineItems,
                                          Map<Long, String> codes, boolean hasBaseline) {
        Map<Long, BaselineItem> baselineById = new HashMap<>();
        for (BaselineItem item : baselineItems) {
            baselineById.put(item.getWbsItemId(), item);
        }
        Map<Long, WbsItem> currentById = new HashMap<>();
        for (WbsItem item : items) {
            currentById.put(item.getId(), item);
        }

        List<String> added = new ArrayList<>();
        List<String> weightChanged = new ArrayList<>();
        for (WbsItem item : items) {
            BaselineItem approved = baselineById.get(item.getId());
            if (approved == null) {
                added.add(label(codes.get(item.getId()), item.getName()));
            } else if (!Objects.equals(approved.getWeight(), item.getWeight())) {
                weightChanged.add("%s (%s → %s)".formatted(
                        label(codes.get(item.getId()), item.getName()),
                        approved.getWeight() == null ? "미입력" : approved.getWeight(),
                        item.getWeight() == null ? "미입력" : item.getWeight()));
            }
        }

        List<String> removed = new ArrayList<>();
        for (BaselineItem approved : baselineItems) {
            if (!currentById.containsKey(approved.getWbsItemId())) {
                removed.add(label(approved.getCode(), approved.getName()));
            }
        }

        return new ScopeComparison(hasBaseline, baselineItems.size(), items.size(),
                added, removed, weightChanged);
    }

    private String label(String code, String name) {
        return code == null ? name : code + " " + name;
    }

    // ------------------------------------------------------------------ 입력 수집

    /**
     * Aggregation units only: Story and Bug, not archived (설계 §6.1). Counting an Epic alongside
     * the Stories inside it, or a Task alongside its Story, would count the same work twice.
     */
    private Map<Long, List<BacklogItem>> aggregatedBacklogByWorkPackage(Long projectId) {
        Map<Long, List<BacklogItem>> byWorkPackage = new HashMap<>();
        for (BacklogItem item : backlogItemRepository.findByProjectId(projectId)) {
            if (item.getWbsItemId() == null || !item.aggregated() || item.archived()) {
                continue;
            }
            byWorkPackage.computeIfAbsent(item.getWbsItemId(), key -> new ArrayList<>()).add(item);
        }
        return byWorkPackage;
    }

    private Map<Long, List<AcceptanceCheckpoint>> checkpointsByWbsItem(Long projectId) {
        Map<Long, List<AcceptanceCheckpoint>> byItem = new HashMap<>();
        for (AcceptanceCheckpoint checkpoint : checkpointRepository.findByProjectId(projectId)) {
            byItem.computeIfAbsent(checkpoint.getWbsItemId(), key -> new ArrayList<>()).add(checkpoint);
        }
        return byItem;
    }

    Baseline latestBaseline(Long projectId) {
        List<Baseline> baselines = baselineRepository.findByProjectId(projectId);
        return baselines.isEmpty() ? null : baselines.get(baselines.size() - 1);
    }

    private static void collectCodes(List<WbsNode> nodes, Map<Long, String> codes) {
        for (WbsNode node : nodes) {
            codes.put(node.item().getId(), node.code());
            collectCodes(node.children(), codes);
        }
    }

    private void requireProject(Long projectId) {
        if (!projectRepository.existsById(projectId)) {
            throw new ProjectNotFoundException(projectId);
        }
    }

    private record Planned(Double planned, Double comparable, Double variance, int excludedCount) {
    }
}
