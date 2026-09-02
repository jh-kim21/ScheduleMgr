package com.projectflow.application;

import com.projectflow.application.dto.ProjectExportResponse;
import com.projectflow.application.dto.ProjectExportResponse.ExportedDependency;
import com.projectflow.application.dto.ProjectExportResponse.ExportedMember;
import com.projectflow.application.dto.ProjectExportResponse.ExportedRaciAssignment;
import com.projectflow.application.dto.ProjectExportResponse.ExportedRaidItem;
import com.projectflow.application.dto.ProjectExportResponse.ExportedWbsItem;
import com.projectflow.application.dto.ProjectResponse;
import com.projectflow.domain.InvalidImportException;
import com.projectflow.domain.Project;
import com.projectflow.domain.ProjectMember;
import com.projectflow.domain.ProjectMemberRepository;
import com.projectflow.domain.ProjectRepository;
import com.projectflow.domain.RaciAssignment;
import com.projectflow.domain.RaciAssignmentRepository;
import com.projectflow.domain.RaidItem;
import com.projectflow.domain.RaidItemRepository;
import com.projectflow.domain.WbsDependency;
import com.projectflow.domain.WbsDependencyRepository;
import com.projectflow.domain.WbsItem;
import com.projectflow.domain.WbsItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Rebuilds a project from an exported file (the other half of {@link ExportService}).
 *
 * <p><b>Always a new project.</b> Merging into an existing one would mean deciding, row by row,
 * whether something is the same task under a new name — a question the file cannot answer. Making
 * a new project is unambiguous and is what sharing actually needs: you receive someone's project
 * and look at it next to your own.
 *
 * <p><b>Every id is reassigned.</b> The ids in the file belong to the install that produced it and
 * mean nothing here, so rows are inserted parents-first and old ids are mapped to new ones as they
 * go. This is why the WBS has to be inserted in dependency order rather than file order.
 *
 * <p><b>Structure is validated, plan quality is not.</b> Anything that would hit a database
 * constraint or dangle (a parent that is not in the file, a self-dependency, a duplicate RACI
 * letter) is refused with a message naming it. But a plan that merely disagrees with itself — a
 * dependency cycle, a link between a task and its own summary — is imported as-is: it is the
 * user's own data, and the schedule screens already explain those problems where they matter.
 * Refusing the file would leave them with data they cannot get in at all.
 */
@Service
@Transactional(readOnly = true)
public class ImportService {

    /** Files from a newer format may contain fields this version would silently drop. */
    private static final int SUPPORTED_FORMAT_VERSION = 1;

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository memberRepository;
    private final WbsItemRepository wbsItemRepository;
    private final WbsDependencyRepository dependencyRepository;
    private final RaciAssignmentRepository raciAssignmentRepository;
    private final RaidItemRepository raidItemRepository;

    public ImportService(ProjectRepository projectRepository,
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

    @Transactional
    public ProjectResponse importProject(ProjectExportResponse file) {
        validate(file);

        Project project = projectRepository.save(new Project(
                availableName(file.project().name()),
                file.project().description(),
                file.project().status(),
                file.project().startDate(),
                file.project().endDate()
        ));
        Long projectId = project.getId();

        // 손으로 편집한 파일에서 절이 빠져 있을 수 있어 null 안전 접근자를 쓴다.
        Map<Long, Long> memberIds = insertMembers(projectId, members(file));
        Map<Long, Long> wbsIds = insertWbsItems(projectId, wbsItems(file));
        insertDependencies(projectId, dependencies(file), wbsIds);
        insertRaciAssignments(projectId, raciAssignments(file), wbsIds, memberIds);
        insertRaidItems(projectId, raidItems(file), wbsIds, memberIds);

        return ProjectResponse.from(project);
    }

    // ------------------------------------------------------------------ 검증

    private void validate(ProjectExportResponse file) {
        if (file == null || file.project() == null) {
            throw new InvalidImportException("프로젝트 정보가 없는 파일입니다.");
        }
        if (file.formatVersion() > SUPPORTED_FORMAT_VERSION) {
            throw new InvalidImportException(
                    "이 버전이 읽을 수 없는 형식입니다 (파일 %d, 지원 %d). 앱을 업데이트하세요."
                            .formatted(file.formatVersion(), SUPPORTED_FORMAT_VERSION));
        }
        if (file.project().name() == null || file.project().name().isBlank()) {
            throw new InvalidImportException("프로젝트 이름이 비어 있습니다.");
        }
        if (file.project().status() == null) {
            throw new InvalidImportException("프로젝트 상태가 비어 있습니다.");
        }

        Set<Long> memberIds = idsOf(members(file), ExportedMember::id, "구성원");
        Set<Long> wbsIds = idsOf(wbsItems(file), ExportedWbsItem::id, "WBS 항목");

        for (ExportedWbsItem item : wbsItems(file)) {
            if (item.name() == null || item.name().isBlank()) {
                throw new InvalidImportException("이름이 비어 있는 WBS 항목이 있습니다: id=" + item.id());
            }
            if (item.parentId() != null && !wbsIds.contains(item.parentId())) {
                throw new InvalidImportException(
                        "WBS 항목 '%s'의 상위 항목(id=%d)이 파일에 없습니다."
                                .formatted(item.name(), item.parentId()));
            }
            if (Objects.equals(item.parentId(), item.id())) {
                throw new InvalidImportException("자기 자신을 상위로 가리키는 WBS 항목이 있습니다: id=" + item.id());
            }
        }
        // 순환 상위 참조는 트리를 만들 수 없게 하므로 삽입 전에 잡는다.
        detectParentCycles(wbsItems(file));

        Set<String> dependencyPairs = new HashSet<>();
        for (ExportedDependency dependency : dependencies(file)) {
            requireKnown(wbsIds, dependency.predecessorId(), "선후행 관계의 선행 업무");
            requireKnown(wbsIds, dependency.successorId(), "선후행 관계의 후행 업무");
            if (Objects.equals(dependency.predecessorId(), dependency.successorId())) {
                throw new InvalidImportException(
                        "선행과 후행이 같은 선후행 관계가 있습니다: id=" + dependency.id());
            }
            if (!dependencyPairs.add(dependency.predecessorId() + ">" + dependency.successorId())) {
                throw new InvalidImportException(
                        "같은 선후행 관계가 두 번 들어 있습니다: %d → %d"
                                .formatted(dependency.predecessorId(), dependency.successorId()));
            }
            if (dependency.lagDays() < 0) {
                throw new InvalidImportException("대기 일수가 음수인 선후행 관계가 있습니다: id=" + dependency.id());
            }
        }

        Set<String> raciCells = new HashSet<>();
        for (ExportedRaciAssignment assignment : raciAssignments(file)) {
            requireKnown(wbsIds, assignment.wbsItemId(), "RACI 배정의 업무");
            requireKnown(memberIds, assignment.memberId(), "RACI 배정의 구성원");
            if (assignment.role() == null) {
                throw new InvalidImportException("역할이 비어 있는 RACI 배정이 있습니다: id=" + assignment.id());
            }
            String cell = assignment.wbsItemId() + ":" + assignment.memberId() + ":" + assignment.role();
            if (!raciCells.add(cell)) {
                throw new InvalidImportException("같은 RACI 배정이 두 번 들어 있습니다: " + cell);
            }
        }

        for (ExportedRaidItem item : raidItems(file)) {
            if (item.title() == null || item.title().isBlank()) {
                throw new InvalidImportException("제목이 비어 있는 RAID 항목이 있습니다: id=" + item.id());
            }
            if (item.type() == null || item.status() == null) {
                throw new InvalidImportException(
                        "종류나 상태가 비어 있는 RAID 항목이 있습니다: " + item.title());
            }
            if (item.ownerMemberId() != null) {
                requireKnown(memberIds, item.ownerMemberId(), "RAID 항목의 소유자");
            }
            if (item.wbsItemId() != null) {
                requireKnown(wbsIds, item.wbsItemId(), "RAID 항목의 관련 업무");
            }
        }
    }

    private <T> Set<Long> idsOf(List<T> rows, java.util.function.Function<T, Long> id, String label) {
        Set<Long> seen = new LinkedHashSet<>();
        for (T row : rows) {
            Long value = id.apply(row);
            if (value == null) {
                throw new InvalidImportException("id가 없는 %s가 있습니다.".formatted(label));
            }
            if (!seen.add(value)) {
                throw new InvalidImportException("%s의 id가 중복됩니다: %d".formatted(label, value));
            }
        }
        return seen;
    }

    private void requireKnown(Set<Long> known, Long id, String label) {
        if (id == null || !known.contains(id)) {
            throw new InvalidImportException("%s(id=%s)가 파일에 없습니다.".formatted(label, id));
        }
    }

    /** Walks each item's parent chain; a repeat means the chain loops and no tree exists. */
    private void detectParentCycles(List<ExportedWbsItem> items) {
        Map<Long, Long> parents = new HashMap<>();
        for (ExportedWbsItem item : items) {
            parents.put(item.id(), item.parentId());
        }
        for (Long start : parents.keySet()) {
            Set<Long> path = new LinkedHashSet<>();
            Long current = start;
            while (current != null) {
                if (!path.add(current)) {
                    throw new InvalidImportException(
                            "WBS 상위 참조가 순환합니다: " + path.stream().map(String::valueOf).toList());
                }
                current = parents.get(current);
            }
        }
    }

    // ------------------------------------------------------------------ 삽입

    /**
     * Appends a suffix when the name is already taken. Project names carry no uniqueness rule, but
     * two identical entries in the picker are indistinguishable, and someone importing a colleague's
     * copy of a project they also have is the normal case.
     */
    private String availableName(String name) {
        Set<String> taken = new HashSet<>();
        for (Project project : projectRepository.findAll()) {
            taken.add(project.getName());
        }
        String candidate = name.strip();
        if (!taken.contains(candidate)) {
            return candidate;
        }
        String imported = candidate + " (가져옴)";
        if (!taken.contains(imported)) {
            return imported;
        }
        for (int suffix = 2; ; suffix++) {
            String numbered = "%s (가져옴 %d)".formatted(candidate, suffix);
            if (!taken.contains(numbered)) {
                return numbered;
            }
        }
    }

    private Map<Long, Long> insertMembers(Long projectId, List<ExportedMember> members) {
        Map<Long, Long> idMap = new HashMap<>();
        for (ExportedMember member : members) {
            ProjectMember saved = memberRepository.save(new ProjectMember(
                    projectId, member.name(), member.email(), member.position()));
            idMap.put(member.id(), saved.getId());
        }
        return idMap;
    }

    /**
     * Parents before children, because a child's {@code parentId} has to be a new id that already
     * exists. File order is not relied on — an exported file happens to be in tree order, but a
     * hand-edited one need not be.
     */
    private Map<Long, Long> insertWbsItems(Long projectId, List<ExportedWbsItem> items) {
        Map<Long, List<ExportedWbsItem>> byParent = new HashMap<>();
        for (ExportedWbsItem item : items) {
            byParent.computeIfAbsent(item.parentId(), key -> new ArrayList<>()).add(item);
        }

        Map<Long, Long> idMap = new HashMap<>();
        Deque<ExportedWbsItem> queue = new ArrayDeque<>(byParent.getOrDefault(null, List.of()));
        while (!queue.isEmpty()) {
            ExportedWbsItem item = queue.removeFirst();
            Long newParentId = item.parentId() == null ? null : idMap.get(item.parentId());
            WbsItem saved = wbsItemRepository.save(new WbsItem(
                    projectId,
                    newParentId,
                    item.name(),
                    item.description(),
                    item.startDate(),
                    item.endDate(),
                    item.progress(),
                    item.sortOrder()
            ));
            idMap.put(item.id(), saved.getId());
            queue.addAll(byParent.getOrDefault(item.id(), List.of()));
        }

        // 위 순회는 최상위에서 닿을 수 있는 항목만 넣는다. 검증이 상위 존재와 순환을 이미
        // 막았으므로 남는 항목이 있으면 이쪽 논리가 잘못된 것이다.
        if (idMap.size() != items.size()) {
            throw new IllegalStateException(
                    "WBS 삽입이 누락되었습니다: 파일 %d개 중 %d개".formatted(items.size(), idMap.size()));
        }
        return idMap;
    }

    private void insertDependencies(Long projectId, List<ExportedDependency> dependencies,
                                     Map<Long, Long> wbsIds) {
        for (ExportedDependency dependency : dependencies) {
            dependencyRepository.save(new WbsDependency(
                    projectId,
                    wbsIds.get(dependency.predecessorId()),
                    wbsIds.get(dependency.successorId()),
                    dependency.lagDays()
            ));
        }
    }

    private void insertRaciAssignments(Long projectId, List<ExportedRaciAssignment> assignments,
                                        Map<Long, Long> wbsIds, Map<Long, Long> memberIds) {
        for (ExportedRaciAssignment assignment : assignments) {
            raciAssignmentRepository.save(new RaciAssignment(
                    projectId,
                    wbsIds.get(assignment.wbsItemId()),
                    memberIds.get(assignment.memberId()),
                    assignment.role()
            ));
        }
    }

    private void insertRaidItems(Long projectId, List<ExportedRaidItem> items,
                                  Map<Long, Long> wbsIds, Map<Long, Long> memberIds) {
        for (ExportedRaidItem item : items) {
            raidItemRepository.save(new RaidItem(
                    projectId,
                    item.type(),
                    item.title(),
                    item.description(),
                    item.status(),
                    item.probability(),
                    item.impact(),
                    item.ownerMemberId() == null ? null : memberIds.get(item.ownerMemberId()),
                    item.wbsItemId() == null ? null : wbsIds.get(item.wbsItemId()),
                    item.dueDate(),
                    item.response()
            ));
        }
    }

    // null 리스트를 빈 리스트로 — 손으로 편집한 파일에서 절이 빠져 있을 수 있다.
    private List<ExportedMember> members(ProjectExportResponse file) {
        return file.members() == null ? List.of() : file.members();
    }

    private List<ExportedWbsItem> wbsItems(ProjectExportResponse file) {
        return file.wbsItems() == null ? List.of() : file.wbsItems();
    }

    private List<ExportedDependency> dependencies(ProjectExportResponse file) {
        return file.dependencies() == null ? List.of() : file.dependencies();
    }

    private List<ExportedRaciAssignment> raciAssignments(ProjectExportResponse file) {
        return file.raciAssignments() == null ? List.of() : file.raciAssignments();
    }

    private List<ExportedRaidItem> raidItems(ProjectExportResponse file) {
        return file.raidItems() == null ? List.of() : file.raidItems();
    }
}
