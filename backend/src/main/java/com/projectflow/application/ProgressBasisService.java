package com.projectflow.application;

import com.projectflow.application.dto.ProgressRequests.BaselineApproveRequest;
import com.projectflow.application.dto.ProgressRequests.CheckpointApprovalRequest;
import com.projectflow.application.dto.ProgressRequests.CheckpointSaveRequest;
import com.projectflow.application.dto.ProgressRequests.SnapshotSaveRequest;
import com.projectflow.application.dto.ProgressResponse;
import com.projectflow.application.dto.SnapshotResponse;
import com.projectflow.application.dto.SnapshotResponse.SnapshotDetail;
import com.projectflow.domain.AcceptanceCheckpoint;
import com.projectflow.domain.AcceptanceCheckpointRepository;
import com.projectflow.domain.Baseline;
import com.projectflow.domain.BaselineItem;
import com.projectflow.domain.BaselineRepository;
import com.projectflow.domain.ChangeLog;
import com.projectflow.domain.ChangeLogRepository;
import com.projectflow.domain.ChangeReason;
import com.projectflow.domain.InvalidWbsHierarchyException;
import com.projectflow.domain.ProgressSnapshot;
import com.projectflow.domain.ProgressSnapshotRepository;
import com.projectflow.domain.ProjectNotFoundException;
import com.projectflow.domain.ProjectRepository;
import com.projectflow.domain.WbsItem;
import com.projectflow.domain.WbsItemNotFoundException;
import com.projectflow.domain.WbsItemRepository;
import com.projectflow.domain.WbsNode;
import com.projectflow.domain.WbsTreeAssembler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The basis the aggregation runs on: approval checkpoints, approved baselines, and the reports
 * written down for later (Hybrid PM Step 5-A / 5-C).
 *
 * <p>Split from {@link ProgressService} on purpose — that one only reads and derives, this one is
 * where the durable facts are created. Both are needed to answer "얼마나 왔나", but only one of
 * them changes anything.
 */
@Service
@Transactional(readOnly = true)
public class ProgressBasisService {

    private final AcceptanceCheckpointRepository checkpointRepository;
    private final BaselineRepository baselineRepository;
    private final ProgressSnapshotRepository snapshotRepository;
    private final ChangeLogRepository changeLogRepository;
    private final WbsItemRepository wbsItemRepository;
    private final ProjectRepository projectRepository;
    private final ProgressService progressService;

    public ProgressBasisService(AcceptanceCheckpointRepository checkpointRepository,
                                 BaselineRepository baselineRepository,
                                 ProgressSnapshotRepository snapshotRepository,
                                 ChangeLogRepository changeLogRepository,
                                 WbsItemRepository wbsItemRepository,
                                 ProjectRepository projectRepository,
                                 ProgressService progressService) {
        this.checkpointRepository = checkpointRepository;
        this.baselineRepository = baselineRepository;
        this.snapshotRepository = snapshotRepository;
        this.changeLogRepository = changeLogRepository;
        this.wbsItemRepository = wbsItemRepository;
        this.projectRepository = projectRepository;
        this.progressService = progressService;
    }

    // ------------------------------------------------------------------ 체크포인트

    /**
     * Adds an approval checkpoint to a Work Package.
     *
     * <p>Only a Work Package may hold one: a Summary's progress comes from its children (설계 §6.1),
     * so a checkpoint there would be a second, competing source.
     */
    @Transactional
    public ProgressResponse addCheckpoint(Long projectId, CheckpointSaveRequest request) {
        requireProject(projectId);
        WbsItem owner = requireWorkPackage(projectId, request.wbsItemId());

        int sortOrder = checkpointRepository.findByProjectId(projectId).stream()
                .filter(cp -> cp.getWbsItemId().equals(owner.getId()))
                .mapToInt(AcceptanceCheckpoint::getSortOrder)
                .max()
                .orElse(-1) + 1;

        checkpointRepository.save(new AcceptanceCheckpoint(projectId, owner.getId(),
                request.title().trim(), request.weight(), blankToNull(request.completionCriteria()),
                sortOrder));
        return progressService.getProgress(projectId);
    }

    @Transactional
    public ProgressResponse updateCheckpoint(Long projectId, Long checkpointId,
                                              CheckpointSaveRequest request) {
        requireProject(projectId);
        AcceptanceCheckpoint checkpoint = requireCheckpoint(projectId, checkpointId);
        Integer previousWeight = checkpoint.getWeight();

        checkpoint.update(request.title().trim(), request.weight(),
                blankToNull(request.completionCriteria()));
        checkpointRepository.save(checkpoint);

        // 체크포인트 가중치는 Waterfall 분모다. 바뀌면 진척이 움직이므로 이유가 남아야 한다.
        if (!java.util.Objects.equals(previousWeight, request.weight())) {
            changeLogRepository.save(ChangeLog.of(projectId, ChangeLog.CHECKPOINT, checkpointId,
                    "weight", previousWeight, request.weight(), ChangeReason.BASIS_CHANGED));
        }
        return progressService.getProgress(projectId);
    }

    /**
     * Approves or withdraws a checkpoint. Either way it is logged: an approval is the event that
     * moves Waterfall progress, so "왜 올랐나"의 답이 남아야 한다.
     */
    @Transactional
    public ProgressResponse setCheckpointApproval(Long projectId, Long checkpointId,
                                                    CheckpointApprovalRequest request) {
        requireProject(projectId);
        AcceptanceCheckpoint checkpoint = requireCheckpoint(projectId, checkpointId);
        boolean wasApproved = checkpoint.approved();

        if (Boolean.TRUE.equals(request.approved())) {
            String approvedBy = blankToNull(request.approvedBy());
            if (approvedBy == null) {
                throw new InvalidWbsHierarchyException("승인자를 입력해야 승인할 수 있습니다.");
            }
            checkpoint.approve(approvedBy);
        } else {
            checkpoint.revoke();
        }
        checkpointRepository.save(checkpoint);

        if (wasApproved != checkpoint.approved()) {
            changeLogRepository.save(ChangeLog.of(projectId, ChangeLog.CHECKPOINT, checkpointId,
                    "approved", wasApproved, checkpoint.approved(), ChangeReason.BASIS_CHANGED));
        }
        return progressService.getProgress(projectId);
    }

    @Transactional
    public ProgressResponse deleteCheckpoint(Long projectId, Long checkpointId) {
        requireProject(projectId);
        checkpointRepository.delete(requireCheckpoint(projectId, checkpointId));
        return progressService.getProgress(projectId);
    }

    // ------------------------------------------------------------------ Baseline

    /**
     * Approves the current plan as a new baseline version.
     *
     * <p>Copies every WBS entry as it stands — scope, dates, weight and, for a Work Package, the
     * completion criteria of its checkpoints joined into one text. A reference would follow the
     * plan as it changes, which is the one thing a baseline must not do.
     *
     * <p>Never called on its own. There is no "auto-approve on first save" anywhere, because a
     * baseline nobody approved cannot be the thing actuals are compared against.
     */
    @Transactional
    public ProgressResponse approveBaseline(Long projectId, BaselineApproveRequest request) {
        requireProject(projectId);
        List<WbsItem> items = wbsItemRepository.findByProjectId(projectId);
        if (items.isEmpty()) {
            throw new InvalidWbsHierarchyException("WBS 항목이 없어 기준선을 승인할 수 없습니다.");
        }

        int nextVersion = baselineRepository.findByProjectId(projectId).size() + 1;
        Baseline baseline = baselineRepository.save(
                new Baseline(projectId, nextVersion, request.approvedBy().trim(),
                        blankToNull(request.note())));

        Map<Long, String> codes = new HashMap<>();
        collectCodes(WbsTreeAssembler.assemble(items), codes);
        Map<Long, String> criteria = criteriaByWbsItem(projectId);

        List<BaselineItem> copied = new ArrayList<>(items.size());
        for (WbsItem item : items) {
            copied.add(new BaselineItem(
                    baseline.getId(),
                    item.getId(),
                    codes.get(item.getId()),
                    item.getName(),
                    item.getNodeType(),
                    item.getExecutionMode(),
                    item.getStartDate(),
                    item.getEndDate(),
                    item.getWeight(),
                    criteria.get(item.getId())
            ));
        }
        baselineRepository.saveItems(copied);
        return progressService.getProgress(projectId);
    }

    /**
     * The completion criteria of each Work Package, as one text per entry.
     *
     * <p>A Work Package's completion criteria <em>are</em> its checkpoints (지시서 5-A separates
     * sibling weight, Backlog weight and checkpoint criteria), and a baseline has to be readable
     * years later, so the titles and criteria are flattened into the snapshot rather than
     * referenced.
     */
    private Map<Long, String> criteriaByWbsItem(Long projectId) {
        Map<Long, List<String>> lines = new HashMap<>();
        checkpointRepository.findByProjectId(projectId).stream()
                .sorted(java.util.Comparator.comparingInt(AcceptanceCheckpoint::getSortOrder)
                        .thenComparing(AcceptanceCheckpoint::getId))
                .forEach(cp -> lines.computeIfAbsent(cp.getWbsItemId(), key -> new ArrayList<>())
                        .add(cp.getCompletionCriteria() == null || cp.getCompletionCriteria().isBlank()
                                ? cp.getTitle()
                                : cp.getTitle() + ": " + cp.getCompletionCriteria()));

        Map<Long, String> joined = new HashMap<>();
        lines.forEach((wbsItemId, texts) -> joined.put(wbsItemId, String.join(" / ", texts)));
        return joined;
    }

    // ------------------------------------------------------------------ 스냅샷

    /**
     * Writes down today's project-level figures.
     *
     * <p>Progress itself is always recomputed, which makes it right for "지금" and impossible for
     * "그때" — the scope, weights and policy have moved since. So a report is stored when it is
     * reported, together with the baseline it was compared against.
     */
    @Transactional
    public SnapshotResponse saveSnapshot(Long projectId, SnapshotSaveRequest request) {
        requireProject(projectId);
        ProgressResponse progress = progressService.getProgress(projectId);
        List<WbsItem> items = wbsItemRepository.findByProjectId(projectId);

        Integer weightTotal = items.stream().map(WbsItem::getWeight).anyMatch(java.util.Objects::nonNull)
                ? items.stream().map(WbsItem::getWeight).filter(java.util.Objects::nonNull)
                        .mapToInt(Integer::intValue).sum()
                : null;

        snapshotRepository.save(new ProgressSnapshot(
                projectId,
                progress.referenceDate(),
                progress.baseline() == null ? null : progress.baseline().id(),
                items.size(),
                weightTotal,
                metricsJson(progress),
                blankToNull(request == null ? null : request.note())
        ));
        return listSnapshots(projectId);
    }

    public SnapshotResponse listSnapshots(Long projectId) {
        requireProject(projectId);
        Map<Long, Integer> versions = new HashMap<>();
        for (Baseline baseline : baselineRepository.findByProjectId(projectId)) {
            versions.put(baseline.getId(), baseline.getVersion());
        }
        return new SnapshotResponse(snapshotRepository.findByProjectId(projectId).stream()
                .map(snapshot -> new SnapshotDetail(
                        snapshot.getId(),
                        snapshot.getAsOf(),
                        snapshot.getBaselineId(),
                        snapshot.getBaselineId() == null ? null : versions.get(snapshot.getBaselineId()),
                        snapshot.getScopeItemCount(),
                        snapshot.getScopeWeightTotal(),
                        snapshot.getMetrics(),
                        snapshot.getNote(),
                        snapshot.getCreatedAt()))
                .toList());
    }

    /**
     * Hand-built JSON. The project-level figures are a handful of numbers with a stable shape, and
     * a column per metric would mean a migration every time a metric is added.
     */
    private String metricsJson(ProgressResponse progress) {
        ProgressResponse.ProjectProgress project = progress.project();
        return ("{\"actualPercent\":%s,\"basis\":\"%s\",\"workPackageCount\":%d,"
                + "\"notEstimableCount\":%d,\"incomplete\":%s,\"plannedPercent\":%s,"
                + "\"comparablePercent\":%s,\"variancePoints\":%s,\"acceptancePending\":%d,"
                + "\"scopeAdded\":%d,\"scopeRemoved\":%d,\"scopeWeightChanged\":%d}")
                .formatted(
                        number(project.actualPercent()),
                        project.basis(),
                        project.workPackageCount(),
                        project.notEstimableCount(),
                        project.incomplete(),
                        number(project.plannedPercent()),
                        number(project.comparablePercent()),
                        number(project.variancePoints()),
                        project.acceptancePending(),
                        progress.scope().added().size(),
                        progress.scope().removed().size(),
                        progress.scope().weightChanged().size());
    }

    /** {@code null} stays {@code null} in the JSON — 산정 전은 0이 아니다. */
    private String number(Double value) {
        return value == null ? "null" : String.valueOf(Math.round(value * 100) / 100.0);
    }

    // ------------------------------------------------------------------ 검증

    private WbsItem requireWorkPackage(Long projectId, Long wbsItemId) {
        WbsItem item = wbsItemRepository.findByProjectId(projectId).stream()
                .filter(candidate -> candidate.getId().equals(wbsItemId))
                .findFirst()
                .orElseThrow(() -> new WbsItemNotFoundException(wbsItemId));
        if (!item.workPackage()) {
            throw new InvalidWbsHierarchyException(
                    "'%s'은(는) Summary라 승인 체크포인트를 둘 수 없습니다. 진척은 하위에서 집계됩니다."
                            .formatted(item.getName()));
        }
        return item;
    }

    private AcceptanceCheckpoint requireCheckpoint(Long projectId, Long checkpointId) {
        return checkpointRepository.findByProjectId(projectId).stream()
                .filter(candidate -> candidate.getId().equals(checkpointId))
                .findFirst()
                .orElseThrow(() -> new WbsItemNotFoundException(checkpointId));
    }

    private void requireProject(Long projectId) {
        if (!projectRepository.existsById(projectId)) {
            throw new ProjectNotFoundException(projectId);
        }
    }

    private static void collectCodes(List<WbsNode> nodes, Map<Long, String> codes) {
        for (WbsNode node : nodes) {
            codes.put(node.item().getId(), node.code());
            collectCodes(node.children(), codes);
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
