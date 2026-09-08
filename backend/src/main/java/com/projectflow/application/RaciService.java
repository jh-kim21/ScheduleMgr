package com.projectflow.application;

import com.projectflow.application.dto.ProjectMemberResponse;
import com.projectflow.application.dto.RaciAssignmentRequest;
import com.projectflow.application.dto.RaciMatrixResponse;
import com.projectflow.application.dto.RaciMatrixResponse.InheritedRoleResponse;
import com.projectflow.application.dto.RaciMatrixResponse.RaciCellResponse;
import com.projectflow.application.dto.RaciMatrixResponse.RaciIssueResponse;
import com.projectflow.application.dto.RaciMatrixResponse.RaciTaskResponse;
import com.projectflow.application.dto.RaciMatrixResponse.StoryAssigneeResponse;
import com.projectflow.domain.BacklogItem;
import com.projectflow.domain.BacklogItemRepository;
import com.projectflow.domain.InvalidRaciAssignmentException;
import com.projectflow.domain.ProjectMember;
import com.projectflow.domain.ProjectMemberNotFoundException;
import com.projectflow.domain.ProjectMemberRepository;
import com.projectflow.domain.ProjectNotFoundException;
import com.projectflow.domain.ProjectRepository;
import com.projectflow.domain.RaciAssignment;
import com.projectflow.domain.RaciAssignmentRepository;
import com.projectflow.domain.RaciInheritance;
import com.projectflow.domain.RaciInheritance.EffectiveRole;
import com.projectflow.domain.RaciInheritance.RoleSource;
import com.projectflow.domain.RaciRole;
import com.projectflow.domain.RaciValidator;
import com.projectflow.domain.WbsItem;
import com.projectflow.domain.WbsItemNotFoundException;
import com.projectflow.domain.WbsItemRepository;
import com.projectflow.domain.WbsNode;
import com.projectflow.domain.WbsTreeAssembler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** The RACI matrix (요구사항 7). */
@Service
@Transactional(readOnly = true)
public class RaciService {

    private final WbsItemRepository wbsItemRepository;
    private final ProjectMemberRepository memberRepository;
    private final RaciAssignmentRepository assignmentRepository;
    private final BacklogItemRepository backlogItemRepository;
    private final ProjectRepository projectRepository;

    public RaciService(WbsItemRepository wbsItemRepository,
                        ProjectMemberRepository memberRepository,
                        RaciAssignmentRepository assignmentRepository,
                        BacklogItemRepository backlogItemRepository,
                        ProjectRepository projectRepository) {
        this.wbsItemRepository = wbsItemRepository;
        this.memberRepository = memberRepository;
        this.assignmentRepository = assignmentRepository;
        this.backlogItemRepository = backlogItemRepository;
        this.projectRepository = projectRepository;
    }

    /** Columns, rows, cells and rule breaches in one payload (요구사항 7.2). */
    public RaciMatrixResponse getMatrix(Long projectId) {
        requireProject(projectId);
        return buildMatrix(projectId);
    }

    /** Adds one letter to one cell (요구사항 7.1). */
    @Transactional
    public RaciMatrixResponse assign(Long projectId, RaciAssignmentRequest request) {
        requireProject(projectId);
        requireWbsItemOfProject(projectId, request.wbsItemId());
        requireMemberOfProject(projectId, request.memberId());

        boolean duplicate = assignmentRepository.findByProjectId(projectId).stream()
                .anyMatch(assignment -> assignment.getWbsItemId().equals(request.wbsItemId())
                        && assignment.getMemberId().equals(request.memberId())
                        && assignment.getRole() == request.role());
        if (duplicate) {
            throw new InvalidRaciAssignmentException("이미 배정된 역할입니다.");
        }

        assignmentRepository.save(new RaciAssignment(
                projectId, request.wbsItemId(), request.memberId(), request.role()));
        return buildMatrix(projectId);
    }

    /** Removes one letter from one cell. */
    @Transactional
    public RaciMatrixResponse unassign(Long projectId, Long assignmentId) {
        requireProject(projectId);
        RaciAssignment assignment = assignmentRepository.findByProjectId(projectId).stream()
                .filter(candidate -> candidate.getId().equals(assignmentId))
                .findFirst()
                .orElseThrow(() -> new InvalidRaciAssignmentException(
                        "RACI 배정을 찾을 수 없습니다: id=" + assignmentId));
        assignmentRepository.delete(assignment);
        return buildMatrix(projectId);
    }

    private RaciMatrixResponse buildMatrix(Long projectId) {
        List<WbsItem> items = wbsItemRepository.findByProjectId(projectId);
        List<ProjectMember> members = memberRepository.findByProjectId(projectId).stream()
                .sorted(Comparator.comparing(ProjectMember::getId))
                .toList();
        List<RaciAssignment> assignments = assignmentRepository.findByProjectId(projectId);

        List<WbsNode> tree = WbsTreeAssembler.assemble(items);
        List<WbsNode> flattened = new ArrayList<>();
        flatten(tree, flattened);

        Map<Long, List<StoryAssigneeResponse>> storyAssignees =
                storyAssignees(projectId, members);
        List<RaciTaskResponse> tasks = flattened.stream()
                .map(node -> new RaciTaskResponse(
                        node.item().getId(),
                        node.item().getParentId(),
                        node.code(),
                        node.level(),
                        node.item().getName(),
                        node.summary(),
                        storyAssignees.getOrDefault(node.item().getId(), List.of())
                ))
                .toList();

        Map<Long, Map<RaciRole, EffectiveRole>> effective =
                RaciInheritance.resolve(tree, assignments);
        Map<Long, String> codes = new HashMap<>();
        for (WbsNode node : flattened) {
            codes.put(node.item().getId(), node.code());
        }
        List<RaciCellResponse> cells = buildCells(flattened, assignments, effective, codes);

        Map<Long, RaciTaskResponse> tasksById = tasks.stream()
                .collect(Collectors.toMap(RaciTaskResponse::id, task -> task));
        List<RaciIssueResponse> issues = RaciValidator.validate(tree, assignments, members).stream()
                .map(issue -> {
                    RaciTaskResponse task = tasksById.get(issue.wbsItemId());
                    return new RaciIssueResponse(
                            issue.wbsItemId(),
                            task != null ? task.code() : "?",
                            task != null ? task.name() : "?",
                            issue.type(),
                            issue.memberNames()
                    );
                })
                .toList();

        return new RaciMatrixResponse(
                members.stream().map(ProjectMemberResponse::from).toList(), tasks, cells, issues);
    }

    /**
     * One entry per cell that has anything in it, letters in enum order. The assignment ids ride
     * along aligned by index so the client can delete a single letter without another lookup.
     *
     * <p>A cell also appears when the only thing in it is inherited from a phase — that is the
     * point of showing inheritance. Those entries carry no assignment id: the letter is not stored
     * on this row and cannot be removed from it.
     */
    private List<RaciCellResponse> buildCells(List<WbsNode> flattened,
                                               List<RaciAssignment> assignments,
                                               Map<Long, Map<RaciRole, EffectiveRole>> effective,
                                               Map<Long, String> codes) {
        record Cell(Long wbsItemId, Long memberId) {
        }

        Map<Cell, List<RaciAssignment>> ownByCell = new LinkedHashMap<>();
        for (RaciAssignment assignment : assignments) {
            ownByCell.computeIfAbsent(new Cell(assignment.getWbsItemId(), assignment.getMemberId()),
                    key -> new ArrayList<>()).add(assignment);
        }

        // What each row inherits is exactly what its parent has in force. Reading it from the
        // parent's resolved map rather than the row's own keeps the letters a child overrides
        // visible — resolve() drops them from the child, because there they are not in force.
        Map<Cell, List<InheritedRoleResponse>> inheritedByCell = new LinkedHashMap<>();
        for (WbsNode node : flattened) {
            Long itemId = node.item().getId();
            Long parentId = node.item().getParentId();
            if (parentId == null) {
                continue;
            }
            Map<RaciRole, EffectiveRole> fromParent = effective.getOrDefault(parentId, Map.of());
            Set<RaciRole> ownRoles = new LinkedHashSet<>();
            for (RaciAssignment assignment : assignments) {
                if (assignment.getWbsItemId().equals(itemId)) {
                    ownRoles.add(assignment.getRole());
                }
            }
            for (Map.Entry<RaciRole, EffectiveRole> entry : fromParent.entrySet()) {
                RaciRole role = entry.getKey();
                boolean overridden = ownRoles.contains(role);
                for (Long memberId : entry.getValue().memberIds()) {
                    inheritedByCell.computeIfAbsent(new Cell(itemId, memberId),
                            key -> new ArrayList<>())
                            .add(new InheritedRoleResponse(role, RoleSource.INHERITED,
                                    entry.getValue().sourceItemId(),
                                    codes.get(entry.getValue().sourceItemId()), overridden));
                }
            }
        }

        Set<Cell> allCells = new LinkedHashSet<>(ownByCell.keySet());
        allCells.addAll(inheritedByCell.keySet());

        List<RaciCellResponse> cells = new ArrayList<>();
        for (Cell cell : allCells) {
            List<RaciAssignment> sorted = ownByCell.getOrDefault(cell, List.of()).stream()
                    .sorted(Comparator.comparing(RaciAssignment::getRole))
                    .toList();
            List<InheritedRoleResponse> inherited =
                    inheritedByCell.getOrDefault(cell, List.of()).stream()
                            .sorted(Comparator.comparing(InheritedRoleResponse::role))
                            .toList();
            cells.add(new RaciCellResponse(
                    cell.wbsItemId(),
                    cell.memberId(),
                    sorted.stream().map(RaciAssignment::getRole).toList(),
                    sorted.stream().map(RaciAssignment::getId).toList(),
                    inherited
            ));
        }
        return cells;
    }

    /**
     * Backlog 담당자, per Work Package.
     *
     * <p>Shown beside the matrix but <b>not</b> as a RACI letter (지시서 6-B): a Story assignee is
     * who is doing a piece of execution this sprint, while R and A are who answers for the Work
     * Package. Nothing here writes to {@code raci_assignments}, and changing a Story's assignee
     * leaves the matrix exactly as it was.
     *
     * <p>Archived entries are left out — they are not work anybody is carrying now.
     */
    private Map<Long, List<StoryAssigneeResponse>> storyAssignees(Long projectId,
                                                                    List<ProjectMember> members) {
        Map<Long, String> memberNames = new HashMap<>();
        for (ProjectMember member : members) {
            memberNames.put(member.getId(), member.getName());
        }

        Map<Long, Map<Long, Integer>> counts = new LinkedHashMap<>();
        for (BacklogItem item : backlogItemRepository.findByProjectId(projectId)) {
            if (item.getAssigneeMemberId() == null || item.getWbsItemId() == null
                    || item.archived()) {
                continue;
            }
            counts.computeIfAbsent(item.getWbsItemId(), key -> new LinkedHashMap<>())
                    .merge(item.getAssigneeMemberId(), 1, Integer::sum);
        }

        Map<Long, List<StoryAssigneeResponse>> byItem = new HashMap<>();
        for (Map.Entry<Long, Map<Long, Integer>> entry : counts.entrySet()) {
            byItem.put(entry.getKey(), entry.getValue().entrySet().stream()
                    .map(assignee -> new StoryAssigneeResponse(assignee.getKey(),
                            memberNames.getOrDefault(assignee.getKey(), "?"), assignee.getValue()))
                    .sorted(Comparator.comparing(StoryAssigneeResponse::memberName))
                    .toList());
        }
        return byItem;
    }

    private void flatten(List<WbsNode> nodes, List<WbsNode> target) {
        for (WbsNode node : nodes) {
            target.add(node);
            flatten(node.children(), target);
        }
    }

    private void requireProject(Long projectId) {
        if (!projectRepository.existsById(projectId)) {
            throw new ProjectNotFoundException(projectId);
        }
    }

    private void requireWbsItemOfProject(Long projectId, Long wbsItemId) {
        boolean present = wbsItemRepository.findByProjectId(projectId).stream()
                .anyMatch(item -> item.getId().equals(wbsItemId));
        if (!present) {
            throw new WbsItemNotFoundException(wbsItemId);
        }
    }

    private void requireMemberOfProject(Long projectId, Long memberId) {
        boolean present = memberRepository.findByProjectId(projectId).stream()
                .anyMatch(member -> member.getId().equals(memberId));
        if (!present) {
            throw new ProjectMemberNotFoundException(memberId);
        }
    }
}
