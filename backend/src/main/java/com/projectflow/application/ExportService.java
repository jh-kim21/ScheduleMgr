package com.projectflow.application;

import com.projectflow.application.dto.ProjectExportResponse;
import com.projectflow.application.dto.ProjectExportResponse.ExportedBacklogItem;
import com.projectflow.application.dto.ProjectExportResponse.ExportedBaseline;
import com.projectflow.application.dto.ProjectExportResponse.ExportedBaselineItem;
import com.projectflow.application.dto.ProjectExportResponse.ExportedCheckpoint;
import com.projectflow.application.dto.ProjectExportResponse.ExportedDependency;
import com.projectflow.application.dto.ProjectExportResponse.ExportedMember;
import com.projectflow.application.dto.ProjectExportResponse.ExportedProject;
import com.projectflow.application.dto.ProjectExportResponse.ExportedRaciAssignment;
import com.projectflow.application.dto.ProjectExportResponse.ExportedRaidItem;
import com.projectflow.application.dto.ProjectExportResponse.ExportedRaidLink;
import com.projectflow.application.dto.ProjectExportResponse.ExportedSprint;
import com.projectflow.application.dto.ProjectExportResponse.ExportedSnapshot;
import com.projectflow.application.dto.ProjectExportResponse.ExportedSprintItem;
import com.projectflow.application.dto.ProjectExportResponse.ExportedWbsItem;
import com.projectflow.domain.AcceptanceCheckpoint;
import com.projectflow.domain.AcceptanceCheckpointRepository;
import com.projectflow.domain.BacklogItem;
import com.projectflow.domain.BaselineRepository;
import com.projectflow.domain.ProgressSnapshotRepository;
import com.projectflow.domain.BacklogItemRepository;
import com.projectflow.domain.Project;
import com.projectflow.domain.ProjectMember;
import com.projectflow.domain.ProjectMemberRepository;
import com.projectflow.domain.ProjectNotFoundException;
import com.projectflow.domain.ProjectRepository;
import com.projectflow.domain.RaciAssignmentRepository;
import com.projectflow.domain.RaidItem;
import com.projectflow.domain.RaidItemRepository;
import com.projectflow.domain.RaidLink;
import com.projectflow.domain.RaidLinkRepository;
import com.projectflow.domain.Sprint;
import com.projectflow.domain.SprintItem;
import com.projectflow.domain.SprintItemRepository;
import com.projectflow.domain.SprintRepository;
import com.projectflow.domain.WbsDependencyRepository;
import com.projectflow.domain.WbsItem;
import com.projectflow.domain.WbsItemRepository;
import com.projectflow.domain.WbsNode;
import com.projectflow.domain.WbsTreeAssembler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Snapshots a whole project so it can be handed to someone else (see {@link ProjectExportResponse}). */
@Service
@Transactional(readOnly = true)
public class ExportService {

    /**
     * Bump when the exported shape changes in a way an importer would need to know about.
     *
     * <p>2 — WBS entries carry {@code nodeType} and {@code executionMode} (Step 2).
     * <p>3 — the Product Backlog travels with the project (Step 3). Without this a project handed
     * to someone else would arrive with its execution items silently missing.
     * <p>4 — Sprints, their assignments and the Board's execution state (Step 4). A closed Sprint's
     * outcomes are the record of what happened, so they travel too.
     * <p>5 — the aggregation basis (Step 5): weights, Hybrid ratios, acceptance status, approval
     * checkpoints, approved baselines and saved report snapshots. Without these a shared project
     * would arrive with every number 산정 전 and no record of what was approved.
     * <p>6 — actual/forecast dates on WBS entries, and RAID links (Step 6). A RAID entry's single
     * {@code wbsItemId} became a list of links, so this file's {@code raidItems[].wbsItemId} is
     * always null and {@code links} carries what it used to say.
     */
    private static final int FORMAT_VERSION = 6;

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository memberRepository;
    private final WbsItemRepository wbsItemRepository;
    private final WbsDependencyRepository dependencyRepository;
    private final RaciAssignmentRepository raciAssignmentRepository;
    private final RaidItemRepository raidItemRepository;
    private final RaidLinkRepository raidLinkRepository;
    private final BacklogItemRepository backlogItemRepository;
    private final SprintRepository sprintRepository;
    private final SprintItemRepository sprintItemRepository;
    private final AcceptanceCheckpointRepository checkpointRepository;
    private final BaselineRepository baselineRepository;
    private final ProgressSnapshotRepository snapshotRepository;

    public ExportService(ProjectRepository projectRepository,
                          ProjectMemberRepository memberRepository,
                          WbsItemRepository wbsItemRepository,
                          WbsDependencyRepository dependencyRepository,
                          RaciAssignmentRepository raciAssignmentRepository,
                          RaidItemRepository raidItemRepository,
                          RaidLinkRepository raidLinkRepository,
                          BacklogItemRepository backlogItemRepository,
                          SprintRepository sprintRepository,
                          SprintItemRepository sprintItemRepository,
                          AcceptanceCheckpointRepository checkpointRepository,
                          BaselineRepository baselineRepository,
                          ProgressSnapshotRepository snapshotRepository) {
        this.projectRepository = projectRepository;
        this.memberRepository = memberRepository;
        this.wbsItemRepository = wbsItemRepository;
        this.dependencyRepository = dependencyRepository;
        this.raciAssignmentRepository = raciAssignmentRepository;
        this.raidItemRepository = raidItemRepository;
        this.raidLinkRepository = raidLinkRepository;
        this.backlogItemRepository = backlogItemRepository;
        this.sprintRepository = sprintRepository;
        this.sprintItemRepository = sprintItemRepository;
        this.checkpointRepository = checkpointRepository;
        this.baselineRepository = baselineRepository;
        this.snapshotRepository = snapshotRepository;
    }

    /**
     * RAID entries with their links.
     *
     * <p>{@code wbsItemId} is left null: it is the pre-Step-6 shape, kept in the file only so an
     * older export can still be read back. Writing both would let the two disagree.
     */
    private List<ExportedRaidItem> exportedRaidItems(Long projectId) {
        Map<Long, List<ExportedRaidLink>> linksByItem = new HashMap<>();
        for (RaidLink link : raidLinkRepository.findByProjectId(projectId)) {
            linksByItem.computeIfAbsent(link.getRaidItemId(), key -> new ArrayList<>())
                    .add(new ExportedRaidLink(link.getId(), link.getTargetType(), link.getTargetId()));
        }
        for (List<ExportedRaidLink> links : linksByItem.values()) {
            links.sort(Comparator.comparing(ExportedRaidLink::id));
        }
        return raidItemRepository.findByProjectId(projectId).stream()
                .sorted(Comparator.comparing(RaidItem::getId))
                .map(item -> new ExportedRaidItem(
                        item.getId(),
                        item.getType(),
                        item.getTitle(),
                        item.getDescription(),
                        item.getStatus(),
                        item.getProbability(),
                        item.getImpact(),
                        item.getOwnerMemberId(),
                        null,
                        linksByItem.getOrDefault(item.getId(), List.of()),
                        item.getDueDate(),
                        item.getResponse()))
                .toList();
    }

    public ProjectExportResponse exportProject(Long projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ProjectNotFoundException(projectId));

        List<WbsItem> items = wbsItemRepository.findByProjectId(projectId);

        // The tree is assembled only to get each row's code, which the stored entity does not have.
        Map<Long, String> codes = new HashMap<>();
        collectCodes(WbsTreeAssembler.assemble(items), codes);

        // Tree order for the WBS so the file reads top to bottom like the screen does; id order
        // elsewhere so two exports of the same data are byte-identical.
        List<ExportedWbsItem> wbsItems = new ArrayList<>();
        appendWbsInTreeOrder(WbsTreeAssembler.assemble(items), codes, wbsItems);

        return new ProjectExportResponse(
                FORMAT_VERSION,
                LocalDateTime.now(),
                new ExportedProject(
                        project.getId(),
                        project.getName(),
                        project.getDescription(),
                        project.getStatus(),
                        project.getStartDate(),
                        project.getEndDate(),
                        project.getCreatedAt(),
                        project.getUpdatedAt()
                ),
                memberRepository.findByProjectId(projectId).stream()
                        .sorted(Comparator.comparing(ProjectMember::getId))
                        .map(member -> new ExportedMember(
                                member.getId(), member.getName(), member.getEmail(), member.getPosition()))
                        .toList(),
                wbsItems,
                dependencyRepository.findByProjectId(projectId).stream()
                        .sorted(Comparator.comparing(dependency -> dependency.getId()))
                        .map(dependency -> new ExportedDependency(
                                dependency.getId(),
                                dependency.getPredecessorId(),
                                dependency.getSuccessorId(),
                                dependency.getLagDays()))
                        .toList(),
                raciAssignmentRepository.findByProjectId(projectId).stream()
                        .sorted(Comparator.comparing(assignment -> assignment.getId()))
                        .map(assignment -> new ExportedRaciAssignment(
                                assignment.getId(),
                                assignment.getWbsItemId(),
                                assignment.getMemberId(),
                                assignment.getRole()))
                        .toList(),
                exportedRaidItems(projectId),
                backlogItemRepository.findByProjectId(projectId).stream()
                        .sorted(Comparator.comparing(BacklogItem::getId))
                        .map(item -> new ExportedBacklogItem(
                                item.getId(),
                                item.getWbsItemId(),
                                item.getParentId(),
                                item.getItemType(),
                                item.getTitle(),
                                item.getDescription(),
                                item.getPriority(),
                                item.getStatus(),
                                item.getAssigneeMemberId(),
                                item.getAcceptanceCriteria(),
                                item.getStoryPoint(),
                                item.getProgressWeight(),
                                item.getArchivedAt(),
                                item.getSortOrder(),
                                item.blocked(),
                                item.getBlockedReason(),
                                item.getDoneAt()))
                        .toList(),
                sprintRepository.findByProjectId(projectId).stream()
                        .sorted(Comparator.comparing(Sprint::getId))
                        .map(sprint -> new ExportedSprint(
                                sprint.getId(),
                                sprint.getName(),
                                sprint.getGoal(),
                                sprint.getStartDate(),
                                sprint.getEndDate(),
                                sprint.getStatus(),
                                sprint.getClosedAt()))
                        .toList(),
                sprintItemRepository.findByProjectId(projectId).stream()
                        .sorted(Comparator.comparing(SprintItem::getId))
                        .map(assignment -> new ExportedSprintItem(
                                assignment.getId(),
                                assignment.getSprintId(),
                                assignment.getBacklogItemId(),
                                assignment.getAddedAt(),
                                assignment.getRemovedAt(),
                                assignment.getPointsAtStart(),
                                assignment.getPointsAtClose(),
                                assignment.getOutcome()))
                        .toList(),
                checkpointRepository.findByProjectId(projectId).stream()
                        .sorted(Comparator.comparing(AcceptanceCheckpoint::getId))
                        .map(cp -> new ExportedCheckpoint(
                                cp.getId(),
                                cp.getWbsItemId(),
                                cp.getTitle(),
                                cp.getWeight(),
                                cp.getCompletionCriteria(),
                                cp.approved(),
                                cp.getApprovedBy(),
                                cp.getApprovedAt(),
                                cp.getSortOrder()))
                        .toList(),
                baselineRepository.findByProjectId(projectId).stream()
                        .map(baseline -> new ExportedBaseline(
                                baseline.getId(),
                                baseline.getVersion(),
                                baseline.getApprovedBy(),
                                baseline.getApprovedAt(),
                                baseline.getNote(),
                                baselineRepository.findItemsByBaselineId(baseline.getId()).stream()
                                        .map(bi -> new ExportedBaselineItem(
                                                bi.getWbsItemId(),
                                                bi.getCode(),
                                                bi.getName(),
                                                bi.getNodeType(),
                                                bi.getExecutionMode(),
                                                bi.getStartDate(),
                                                bi.getEndDate(),
                                                bi.getWeight(),
                                                bi.getCompletionCriteria()))
                                        .toList()))
                        .toList(),
                snapshotRepository.findByProjectId(projectId).stream()
                        .map(snapshot -> new ExportedSnapshot(
                                snapshot.getId(),
                                snapshot.getAsOf(),
                                snapshot.getBaselineId(),
                                snapshot.getScopeItemCount(),
                                snapshot.getScopeWeightTotal(),
                                snapshot.getMetrics(),
                                snapshot.getNote()))
                        .toList()
        );
    }

    /** File name for the download; the browser shows this to whoever receives it. */
    public String fileNameFor(Long projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ProjectNotFoundException(projectId));
        return "%s-export-%s.json".formatted(safeName(project.getName()), java.time.LocalDate.now());
    }

    /**
     * Project names are free text and end up in a Content-Disposition header and on a filesystem.
     * Anything outside letters, digits, dash and underscore is replaced rather than escaped —
     * a readable approximation beats a precisely quoted name nobody can type.
     */
    private String safeName(String name) {
        String cleaned = name == null ? "" : name.strip().replaceAll("[^\\p{L}\\p{N}._-]+", "-");
        cleaned = cleaned.replaceAll("^-+|-+$", "");
        return cleaned.isEmpty() ? "project" : cleaned;
    }

    private void collectCodes(List<WbsNode> nodes, Map<Long, String> codes) {
        for (WbsNode node : nodes) {
            codes.put(node.item().getId(), node.code());
            collectCodes(node.children(), codes);
        }
    }

    private void appendWbsInTreeOrder(List<WbsNode> nodes, Map<Long, String> codes,
                                       List<ExportedWbsItem> target) {
        for (WbsNode node : nodes) {
            WbsItem item = node.item();
            target.add(new ExportedWbsItem(
                    item.getId(),
                    item.getParentId(),
                    codes.get(item.getId()),
                    item.getName(),
                    item.getDescription(),
                    item.getStartDate(),
                    item.getEndDate(),
                    item.getProgress(),
                    item.getSortOrder(),
                    item.getNodeType(),
                    item.getExecutionMode(),
                    item.getWeight(),
                    item.getAgileRatio(),
                    item.getAcceptanceStatus(),
                    item.getActualStartDate(),
                    item.getActualEndDate(),
                    item.getForecastEndDate()
            ));
            appendWbsInTreeOrder(node.children(), codes, target);
        }
    }
}
