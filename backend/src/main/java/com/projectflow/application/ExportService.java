package com.projectflow.application;

import com.projectflow.application.dto.ProjectExportResponse;
import com.projectflow.application.dto.ProjectExportResponse.ExportedDependency;
import com.projectflow.application.dto.ProjectExportResponse.ExportedMember;
import com.projectflow.application.dto.ProjectExportResponse.ExportedProject;
import com.projectflow.application.dto.ProjectExportResponse.ExportedRaciAssignment;
import com.projectflow.application.dto.ProjectExportResponse.ExportedRaidItem;
import com.projectflow.application.dto.ProjectExportResponse.ExportedWbsItem;
import com.projectflow.domain.Project;
import com.projectflow.domain.ProjectMember;
import com.projectflow.domain.ProjectMemberRepository;
import com.projectflow.domain.ProjectNotFoundException;
import com.projectflow.domain.ProjectRepository;
import com.projectflow.domain.RaciAssignmentRepository;
import com.projectflow.domain.RaidItemRepository;
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

    /** Bump when the exported shape changes in a way an importer would need to know about. */
    private static final int FORMAT_VERSION = 1;

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository memberRepository;
    private final WbsItemRepository wbsItemRepository;
    private final WbsDependencyRepository dependencyRepository;
    private final RaciAssignmentRepository raciAssignmentRepository;
    private final RaidItemRepository raidItemRepository;

    public ExportService(ProjectRepository projectRepository,
                          ProjectMemberRepository memberRepository,
                          WbsItemRepository wbsItemRepository,
                          WbsDependencyRepository dependencyRepository,
                          RaciAssignmentRepository raciAssignmentRepository,
                          RaidItemRepository raidItemRepository) {
        this.projectRepository = projectRepository;
        this.memberRepository = memberRepository;
        this.wbsItemRepository = wbsItemRepository;
        this.dependencyRepository = dependencyRepository;
        this.raciAssignmentRepository = raciAssignmentRepository;
        this.raidItemRepository = raidItemRepository;
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
                raidItemRepository.findByProjectId(projectId).stream()
                        .sorted(Comparator.comparing(item -> item.getId()))
                        .map(item -> new ExportedRaidItem(
                                item.getId(),
                                item.getType(),
                                item.getTitle(),
                                item.getDescription(),
                                item.getStatus(),
                                item.getProbability(),
                                item.getImpact(),
                                item.getOwnerMemberId(),
                                item.getWbsItemId(),
                                item.getDueDate(),
                                item.getResponse()))
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
                    item.getSortOrder()
            ));
            appendWbsInTreeOrder(node.children(), codes, target);
        }
    }
}
