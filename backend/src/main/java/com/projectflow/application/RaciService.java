package com.projectflow.application;

import com.projectflow.application.dto.ProjectMemberResponse;
import com.projectflow.application.dto.RaciAssignmentRequest;
import com.projectflow.application.dto.RaciMatrixResponse;
import com.projectflow.application.dto.RaciMatrixResponse.RaciCellResponse;
import com.projectflow.application.dto.RaciMatrixResponse.RaciIssueResponse;
import com.projectflow.application.dto.RaciMatrixResponse.RaciTaskResponse;
import com.projectflow.domain.InvalidRaciAssignmentException;
import com.projectflow.domain.ProjectMember;
import com.projectflow.domain.ProjectMemberNotFoundException;
import com.projectflow.domain.ProjectMemberRepository;
import com.projectflow.domain.ProjectNotFoundException;
import com.projectflow.domain.ProjectRepository;
import com.projectflow.domain.RaciAssignment;
import com.projectflow.domain.RaciAssignmentRepository;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** The RACI matrix (요구사항 7). */
@Service
@Transactional(readOnly = true)
public class RaciService {

    private final WbsItemRepository wbsItemRepository;
    private final ProjectMemberRepository memberRepository;
    private final RaciAssignmentRepository assignmentRepository;
    private final ProjectRepository projectRepository;

    public RaciService(WbsItemRepository wbsItemRepository,
                        ProjectMemberRepository memberRepository,
                        RaciAssignmentRepository assignmentRepository,
                        ProjectRepository projectRepository) {
        this.wbsItemRepository = wbsItemRepository;
        this.memberRepository = memberRepository;
        this.assignmentRepository = assignmentRepository;
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

        List<RaciTaskResponse> tasks = flattened.stream()
                .map(node -> new RaciTaskResponse(
                        node.item().getId(),
                        node.item().getParentId(),
                        node.code(),
                        node.level(),
                        node.item().getName(),
                        node.summary()
                ))
                .toList();

        List<RaciCellResponse> cells = buildCells(assignments);

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
     * One entry per non-empty cell, letters in enum order. The assignment ids ride along aligned
     * by index so the client can delete a single letter without another lookup.
     */
    private List<RaciCellResponse> buildCells(List<RaciAssignment> assignments) {
        record Cell(Long wbsItemId, Long memberId) {
        }

        Map<Cell, List<RaciAssignment>> byCell = new LinkedHashMap<>();
        for (RaciAssignment assignment : assignments) {
            byCell.computeIfAbsent(new Cell(assignment.getWbsItemId(), assignment.getMemberId()),
                    key -> new ArrayList<>()).add(assignment);
        }

        return byCell.entrySet().stream()
                .map(entry -> {
                    List<RaciAssignment> sorted = entry.getValue().stream()
                            .sorted(Comparator.comparing(RaciAssignment::getRole))
                            .toList();
                    return new RaciCellResponse(
                            entry.getKey().wbsItemId(),
                            entry.getKey().memberId(),
                            sorted.stream().map(RaciAssignment::getRole).toList(),
                            sorted.stream().map(RaciAssignment::getId).toList()
                    );
                })
                .toList();
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
