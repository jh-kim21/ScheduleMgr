package com.projectflow.application;

import com.projectflow.application.dto.ProjectExportResponse;
import com.projectflow.application.dto.ProjectExportResponse.ExportedBacklogItem;
import com.projectflow.application.dto.ProjectExportResponse.ExportedBaseline;
import com.projectflow.application.dto.ProjectExportResponse.ExportedBaselineItem;
import com.projectflow.application.dto.ProjectExportResponse.ExportedCheckpoint;
import com.projectflow.application.dto.ProjectExportResponse.ExportedDependency;
import com.projectflow.application.dto.ProjectExportResponse.ExportedMember;
import com.projectflow.application.dto.ProjectExportResponse.ExportedRaciAssignment;
import com.projectflow.application.dto.ProjectExportResponse.ExportedRaidItem;
import com.projectflow.application.dto.ProjectExportResponse.ExportedRaidLink;
import com.projectflow.application.dto.ProjectExportResponse.ExportedSprint;
import com.projectflow.application.dto.ProjectExportResponse.ExportedSnapshot;
import com.projectflow.application.dto.ProjectExportResponse.ExportedSprintItem;
import com.projectflow.application.dto.ProjectExportResponse.ExportedWbsItem;
import com.projectflow.application.dto.ProjectResponse;
import com.projectflow.domain.AcceptanceCheckpoint;
import com.projectflow.domain.AcceptanceCheckpointRepository;
import com.projectflow.domain.BacklogItem;
import com.projectflow.domain.Baseline;
import com.projectflow.domain.BaselineItem;
import com.projectflow.domain.BaselineRepository;
import com.projectflow.domain.ProgressSnapshot;
import com.projectflow.domain.ProgressSnapshotRepository;
import com.projectflow.domain.BacklogItemRepository;
import com.projectflow.domain.BacklogItemType;
import com.projectflow.domain.InvalidImportException;
import com.projectflow.domain.Project;
import com.projectflow.domain.ProjectMember;
import com.projectflow.domain.ProjectMemberRepository;
import com.projectflow.domain.ProjectRepository;
import com.projectflow.domain.RaciAssignment;
import com.projectflow.domain.RaciAssignmentRepository;
import com.projectflow.domain.RaidItem;
import com.projectflow.domain.RaidLink;
import com.projectflow.domain.RaidLinkRepository;
import com.projectflow.domain.RaidLinkTarget;
import com.projectflow.domain.RaidItemRepository;
import com.projectflow.domain.Sprint;
import com.projectflow.domain.SprintItem;
import com.projectflow.domain.SprintItemRepository;
import com.projectflow.domain.SprintRepository;
import com.projectflow.domain.SprintStatus;
import com.projectflow.domain.WbsDependency;
import com.projectflow.domain.WbsDependencyRepository;
import com.projectflow.domain.WbsItem;
import com.projectflow.domain.WbsItemRepository;
import com.projectflow.domain.WbsNodeType;
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

    /**
     * Files from a newer format may contain fields this version would silently drop.
     *
     * <p>Older files are still accepted. A formatVersion 1 file has no {@code nodeType} or
     * {@code executionMode}, and {@link #insertWbsItems} fills both in the same way the schema
     * migration did — child presence decides the kind, and the mode stays 미지정. A file below
     * version 3 simply has no Backlog section, one below 4 has no Sprints, and one below 5 has no
     * aggregation basis — a missing section is the same as an empty one, and a project with no
     * weights or checkpoints reads as 산정 전, which is the honest answer for it.
     */
    private static final int SUPPORTED_FORMAT_VERSION = 6;

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

    public ImportService(ProjectRepository projectRepository,
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
        Map<Long, Long> raidIds = insertRaidItems(projectId, raidItems(file), memberIds);
        Map<Long, Long> backlogIds = insertBacklogItems(projectId, backlogItems(file), wbsIds, memberIds);
        Map<Long, Long> sprintIds = insertSprints(projectId, sprints(file), sprintItems(file), backlogIds);
        // Last: a link can point at a Sprint or a Backlog entry, so every target needs its new id
        // before the mapping can be done.
        insertRaidLinks(projectId, raidItems(file), raidIds, wbsIds, backlogIds, sprintIds);
        insertProgressBasis(projectId, file, wbsIds);

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
            // formatVersion 5이하의 단일 연결. 지금은 links로 오지만, 예전 파일을 그대로 읽는다.
            if (item.wbsItemId() != null) {
                requireKnown(wbsIds, item.wbsItemId(), "RAID 항목의 관련 업무");
            }
            validateRaidLinks(item, wbsIds, backlogIdsOf(file), sprintIdsOf(file));
        }

        validateBacklog(file, wbsIds, memberIds);
        validateSprints(file);
    }

    /**
     * Sprint checks. Structure is refused; a plan that merely disagrees with itself comes in.
     *
     * <p>Refused: a dangling Sprint or Backlog reference, a period that runs backwards, a duplicate
     * name (the table has a unique constraint), more than one ACTIVE Sprint, the same item live
     * twice in one Sprint. Accepted as-is: a closed Sprint whose items have since been re-opened —
     * that is precisely the state Step 4 keeps distinguishable, and refusing it would make a real
     * project unimportable.
     */
    private void validateSprints(ProjectExportResponse file) {
        List<ExportedSprint> sprints = sprints(file);
        Set<Long> sprintIds = idsOf(sprints, ExportedSprint::id, "Sprint");
        Set<Long> backlogIds = idsOf(backlogItems(file), ExportedBacklogItem::id, "Backlog 항목");

        Set<String> names = new HashSet<>();
        int active = 0;
        for (ExportedSprint sprint : sprints) {
            if (sprint.name() == null || sprint.name().isBlank()) {
                throw new InvalidImportException("이름이 비어 있는 Sprint가 있습니다: id=" + sprint.id());
            }
            if (!names.add(sprint.name())) {
                throw new InvalidImportException("같은 이름의 Sprint가 두 번 들어 있습니다: " + sprint.name());
            }
            if (sprint.startDate() == null || sprint.endDate() == null) {
                throw new InvalidImportException("기간이 비어 있는 Sprint가 있습니다: " + sprint.name());
            }
            if (sprint.endDate().isBefore(sprint.startDate())) {
                throw new InvalidImportException("종료일이 시작일보다 앞선 Sprint가 있습니다: " + sprint.name());
            }
            if (sprint.status() == null) {
                throw new InvalidImportException("상태가 비어 있는 Sprint가 있습니다: " + sprint.name());
            }
            if (sprint.status() == SprintStatus.ACTIVE) {
                active++;
            }
        }
        if (active > 1) {
            throw new InvalidImportException(
                    "실행 중인 Sprint가 %d개 있습니다. 한 번에 하나만 실행할 수 있습니다 (단일 팀)."
                            .formatted(active));
        }

        Set<String> live = new HashSet<>();
        for (ExportedSprintItem assignment : sprintItems(file)) {
            requireKnown(sprintIds, assignment.sprintId(), "Sprint 배정의 Sprint");
            requireKnown(backlogIds, assignment.backlogItemId(), "Sprint 배정의 Backlog 항목");
            if (assignment.removedAt() == null
                    && !live.add(assignment.sprintId() + ":" + assignment.backlogItemId())) {
                throw new InvalidImportException(
                        "같은 항목이 한 Sprint에 두 번 배정되어 있습니다: sprint=%d, item=%d"
                                .formatted(assignment.sprintId(), assignment.backlogItemId()));
            }
        }
    }

    /**
     * Backlog checks follow the same split as the rest of the importer: a shape the model cannot
     * hold is refused, and a plan that merely disagrees with itself comes in.
     *
     * <p>Refused: a dangling parent or Work Package, a parent cycle, a Task with no parent, a
     * parent/child pairing the hierarchy does not allow. Accepted as-is: an unlinked draft, and a
     * link to a WBS entry that has since become a Summary — {@code BacklogAssessor} reports both on
     * screen, and refusing the file would leave the user unable to load their own data at all.
     */
    /**
     * A link has to name a target that is in this file, and may name it only once.
     *
     * <p>{@code target_id} has no foreign key — it points at three tables — so nothing downstream
     * would catch a dangling one; it would simply render as a blank row in the register.
     */
    private void validateRaidLinks(ExportedRaidItem item, Set<Long> wbsIds,
                                    Set<Long> backlogIds, Set<Long> sprintIds) {
        List<ExportedRaidLink> links = item.links() == null ? List.of() : item.links();
        Set<String> seen = new HashSet<>();
        for (ExportedRaidLink link : links) {
            if (link.targetType() == null || link.targetId() == null) {
                throw new InvalidImportException(
                        "연결 대상이 비어 있는 RAID 항목이 있습니다: " + item.title());
            }
            if (!seen.add(link.targetType() + ":" + link.targetId())) {
                throw new InvalidImportException(
                        "RAID 항목 '%s'에 같은 연결이 두 번 있습니다.".formatted(item.title()));
            }
            boolean known = switch (link.targetType()) {
                case WBS_ITEM -> wbsIds.contains(link.targetId());
                case BACKLOG_ITEM -> backlogIds.contains(link.targetId());
                case SPRINT -> sprintIds.contains(link.targetId());
            };
            if (!known) {
                throw new InvalidImportException(
                        "RAID 항목 '%s'의 연결 대상을 파일에서 찾을 수 없습니다: %s #%d"
                                .formatted(item.title(), link.targetType(), link.targetId()));
            }
        }
    }

    private Set<Long> backlogIdsOf(ProjectExportResponse file) {
        Set<Long> ids = new HashSet<>();
        for (ExportedBacklogItem item : backlogItems(file)) {
            ids.add(item.id());
        }
        return ids;
    }

    private Set<Long> sprintIdsOf(ProjectExportResponse file) {
        Set<Long> ids = new HashSet<>();
        for (ExportedSprint sprint : sprints(file)) {
            ids.add(sprint.id());
        }
        return ids;
    }

    private void validateBacklog(ProjectExportResponse file, Set<Long> wbsIds, Set<Long> memberIds) {
        List<ExportedBacklogItem> items = backlogItems(file);
        Set<Long> ids = idsOf(items, ExportedBacklogItem::id, "Backlog 항목");
        Map<Long, ExportedBacklogItem> byId = new HashMap<>();
        for (ExportedBacklogItem item : items) {
            byId.put(item.id(), item);
        }

        for (ExportedBacklogItem item : items) {
            if (item.title() == null || item.title().isBlank()) {
                throw new InvalidImportException("제목이 비어 있는 Backlog 항목이 있습니다: id=" + item.id());
            }
            if (item.itemType() == null || item.priority() == null || item.status() == null) {
                throw new InvalidImportException(
                        "유형·우선순위·상태가 비어 있는 Backlog 항목이 있습니다: " + item.title());
            }
            if (item.wbsItemId() != null) {
                requireKnown(wbsIds, item.wbsItemId(), "Backlog 항목 '%s'의 귀속 업무".formatted(item.title()));
            }
            if (item.assigneeMemberId() != null) {
                requireKnown(memberIds, item.assigneeMemberId(),
                        "Backlog 항목 '%s'의 담당자".formatted(item.title()));
            }
            if (Objects.equals(item.parentId(), item.id())) {
                throw new InvalidImportException(
                        "자기 자신을 상위로 가리키는 Backlog 항목이 있습니다: id=" + item.id());
            }
            if (item.parentId() == null) {
                if (item.itemType() == BacklogItemType.TASK) {
                    throw new InvalidImportException(
                            "Task '%s'에 상위 항목이 없습니다. Task는 Story 또는 Bug의 하위여야 합니다."
                                    .formatted(item.title()));
                }
                continue;
            }
            if (!ids.contains(item.parentId())) {
                throw new InvalidImportException(
                        "Backlog 항목 '%s'의 상위 항목(id=%d)이 파일에 없습니다."
                                .formatted(item.title(), item.parentId()));
            }
            requireBacklogParentAccepts(byId.get(item.parentId()), item);
        }
        detectBacklogParentCycles(items);
    }

    /** The two shapes the hierarchy allows: Epic → Story·Bug, and Story·Bug → Task. */
    private void requireBacklogParentAccepts(ExportedBacklogItem parent, ExportedBacklogItem child) {
        BacklogItemType parentType = parent.itemType();
        boolean allowed = switch (child.itemType()) {
            case EPIC -> false;
            case STORY, BUG -> parentType == BacklogItemType.EPIC;
            case TASK -> parentType == BacklogItemType.STORY || parentType == BacklogItemType.BUG;
        };
        if (!allowed) {
            throw new InvalidImportException(
                    "Backlog 계층이 올바르지 않습니다: '%s'(%s)은(는) '%s'(%s)의 하위가 될 수 없습니다."
                            .formatted(child.title(), child.itemType(), parent.title(), parentType));
        }
    }

    /**
     * Defence in depth. The type rules above already make a cycle impossible — the only parent a
     * Story or Bug may have is an Epic, and an Epic may have none — so nothing reaches this today.
     * It stays because "부모 순환 차단" is a stated requirement and because the day Epic nesting is
     * allowed, this is what keeps an unbuildable file out.
     */
    private void detectBacklogParentCycles(List<ExportedBacklogItem> items) {
        Map<Long, Long> parents = new HashMap<>();
        for (ExportedBacklogItem item : items) {
            parents.put(item.id(), item.parentId());
        }
        for (Long start : parents.keySet()) {
            Set<Long> path = new LinkedHashSet<>();
            Long current = start;
            while (current != null) {
                if (!path.add(current)) {
                    throw new InvalidImportException(
                            "Backlog 상위 참조가 순환합니다: " + path.stream().map(String::valueOf).toList());
                }
                current = parents.get(current);
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
     *
     * <p>{@code nodeType} is resolved rather than trusted, the same way {@code code} is recomputed:
     * an entry with children in the file is a summary whatever the file says, since a Work Package
     * with children is a state the editing rules never allow and the user could not fix afterwards.
     * A missing value (formatVersion 1, or a hand-edited file) falls back to child presence too.
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
            boolean hasChildren = !byParent.getOrDefault(item.id(), List.of()).isEmpty();
            WbsNodeType nodeType = hasChildren
                    ? WbsNodeType.SUMMARY
                    : (item.nodeType() != null ? item.nodeType() : WbsNodeType.WORK_PACKAGE);
            WbsItem row = new WbsItem(
                    projectId,
                    newParentId,
                    item.name(),
                    item.description(),
                    item.startDate(),
                    item.endDate(),
                    item.progress(),
                    item.sortOrder(),
                    nodeType,
                    // Summary가 보관 중인 값도 그대로 가져온다 — 되돌리면 살아나야 하는 값이다.
                    item.executionMode()
            );
            // 집계 기준과 실적 날짜도 저장된 상태다. 삽입 전에 얹어 한 번만 저장한다.
            row.restoreProgressBasis(item.weight(), item.agileRatio(), item.acceptanceStatus());
            row.restoreActualDates(item.actualStartDate(), item.actualEndDate(),
                    item.forecastEndDate());
            WbsItem saved = wbsItemRepository.save(row);
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

    private Map<Long, Long> insertRaidItems(Long projectId, List<ExportedRaidItem> items,
                                             Map<Long, Long> memberIds) {
        Map<Long, Long> raidIds = new HashMap<>();
        for (ExportedRaidItem item : items) {
            RaidItem saved = raidItemRepository.save(new RaidItem(
                    projectId,
                    item.type(),
                    item.title(),
                    item.description(),
                    item.status(),
                    item.probability(),
                    item.impact(),
                    item.ownerMemberId() == null ? null : memberIds.get(item.ownerMemberId()),
                    item.dueDate(),
                    item.response()
            ));
            raidIds.put(item.id(), saved.getId());
        }
        return raidIds;
    }

    /**
     * RAID links, with every id remapped.
     *
     * <p>A formatVersion 5 file has no {@code links} and one {@code wbsItemId} instead; it becomes a
     * single {@code WBS_ITEM} link, which is exactly what V21 did to the column in the database.
     */
    private void insertRaidLinks(Long projectId, List<ExportedRaidItem> items,
                                  Map<Long, Long> raidIds, Map<Long, Long> wbsIds,
                                  Map<Long, Long> backlogIds, Map<Long, Long> sprintIds) {
        List<RaidLink> links = new ArrayList<>();
        for (ExportedRaidItem item : items) {
            Long raidItemId = raidIds.get(item.id());
            List<ExportedRaidLink> exported = item.links() == null ? List.of() : item.links();
            if (exported.isEmpty() && item.wbsItemId() != null) {
                links.add(new RaidLink(projectId, raidItemId, RaidLinkTarget.WBS_ITEM,
                        wbsIds.get(item.wbsItemId())));
                continue;
            }
            for (ExportedRaidLink link : exported) {
                Long targetId = switch (link.targetType()) {
                    case WBS_ITEM -> wbsIds.get(link.targetId());
                    case BACKLOG_ITEM -> backlogIds.get(link.targetId());
                    case SPRINT -> sprintIds.get(link.targetId());
                };
                links.add(new RaidLink(projectId, raidItemId, link.targetType(), targetId));
            }
        }
        if (!links.isEmpty()) {
            raidLinkRepository.saveAll(links);
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

    private List<ExportedBacklogItem> backlogItems(ProjectExportResponse file) {
        return file.backlogItems() == null ? List.of() : file.backlogItems();
    }

    private List<ExportedSprint> sprints(ProjectExportResponse file) {
        return file.sprints() == null ? List.of() : file.sprints();
    }

    private List<ExportedSprintItem> sprintItems(ProjectExportResponse file) {
        return file.sprintItems() == null ? List.of() : file.sprintItems();
    }

    private List<ExportedCheckpoint> checkpoints(ProjectExportResponse file) {
        return file.checkpoints() == null ? List.of() : file.checkpoints();
    }

    private List<ExportedBaseline> baselines(ProjectExportResponse file) {
        return file.baselines() == null ? List.of() : file.baselines();
    }

    private List<ExportedSnapshot> snapshots(ProjectExportResponse file) {
        return file.snapshots() == null ? List.of() : file.snapshots();
    }

    /**
     * Restores the rest of the aggregation basis: checkpoints, baselines and saved reports.
     * Weights and ratios travel with the WBS rows themselves, in {@link #insertWbsItems}.
     *
     * <p>Approval state travels as it stands. Resetting approvals on import would hand over a
     * project that had achieved nothing, and the outcomes cannot be re-derived — an approval is an
     * event, not a function of the current data. Snapshot metrics are copied verbatim for the same
     * reason: recomputing them is precisely what a snapshot exists to make unnecessary.
     */
    private void insertProgressBasis(Long projectId, ProjectExportResponse file,
                                      Map<Long, Long> wbsIds) {
        for (ExportedCheckpoint exported : checkpoints(file)) {
            Long newWbsItemId = wbsIds.get(exported.wbsItemId());
            if (newWbsItemId == null) {
                continue;
            }
            AcceptanceCheckpoint checkpoint = new AcceptanceCheckpoint(
                    projectId, newWbsItemId, exported.title(), exported.weight(),
                    exported.completionCriteria(), exported.sortOrder());
            if (exported.approved()) {
                checkpoint.restoreApproval(exported.approvedBy(), exported.approvedAt());
            }
            checkpointRepository.save(checkpoint);
        }

        Map<Long, Long> baselineIds = new HashMap<>();
        for (ExportedBaseline exported : baselines(file)) {
            Baseline baseline = baselineRepository.save(new Baseline(projectId, exported.version(),
                    exported.approvedBy(), exported.note(), exported.approvedAt()));
            baselineIds.put(exported.id(), baseline.getId());

            List<BaselineItem> items = new ArrayList<>();
            for (ExportedBaselineItem item : exported.items() == null ? List.<ExportedBaselineItem>of() : exported.items()) {
                // 지워진 항목의 기준선 기록도 남아야 하므로, 매핑되지 않는 id는 원래 값을 그대로 둔다.
                Long mapped = wbsIds.getOrDefault(item.wbsItemId(), item.wbsItemId());
                items.add(new BaselineItem(baseline.getId(), mapped, item.code(), item.name(),
                        item.nodeType(), item.executionMode(), item.startDate(), item.endDate(),
                        item.weight(), item.completionCriteria()));
            }
            if (!items.isEmpty()) {
                baselineRepository.saveItems(items);
            }
        }

        for (ExportedSnapshot exported : snapshots(file)) {
            snapshotRepository.save(new ProgressSnapshot(projectId, exported.asOf(),
                    exported.baselineId() == null ? null : baselineIds.get(exported.baselineId()),
                    exported.scopeItemCount(), exported.scopeWeightTotal(), exported.metrics(),
                    exported.note()));
        }
    }

    /**
     * Parents before children, like the WBS, so a child's remapped {@code parentId} already exists.
     *
     * <p>A child's Work Package link is <em>taken from its parent</em> rather than from the file.
     * In this model the link always comes from the top of the family, so a differing value in a
     * child row carries no information — the same reasoning that has {@code insertWbsItems}
     * recompute {@code nodeType} instead of trusting it.
     */
    private Map<Long, Long> insertBacklogItems(Long projectId, List<ExportedBacklogItem> items,
                                                 Map<Long, Long> wbsIds, Map<Long, Long> memberIds) {
        Map<Long, List<ExportedBacklogItem>> byParent = new HashMap<>();
        for (ExportedBacklogItem item : items) {
            byParent.computeIfAbsent(item.parentId(), key -> new ArrayList<>()).add(item);
        }

        Map<Long, Long> idMap = new HashMap<>();
        // 부모의 (새) 귀속을 자식에게 물려주기 위해 함께 들고 내려간다.
        Map<Long, Long> linkByOldId = new HashMap<>();
        Deque<ExportedBacklogItem> queue = new ArrayDeque<>(byParent.getOrDefault(null, List.of()));
        while (!queue.isEmpty()) {
            ExportedBacklogItem item = queue.removeFirst();
            Long newParentId = item.parentId() == null ? null : idMap.get(item.parentId());
            Long newWbsItemId = item.parentId() == null
                    ? (item.wbsItemId() == null ? null : wbsIds.get(item.wbsItemId()))
                    : linkByOldId.get(item.parentId());

            BacklogItem row = new BacklogItem(
                    projectId,
                    newWbsItemId,
                    newParentId,
                    item.itemType(),
                    item.title(),
                    item.description(),
                    item.priority(),
                    item.status(),
                    item.assigneeMemberId() == null ? null : memberIds.get(item.assigneeMemberId()),
                    item.acceptanceCriteria(),
                    item.storyPoint(),
                    item.progressWeight(),
                    item.sortOrder(),
                    item.archivedAt()
            );
            // 차단 여부와 완료 시각도 저장된 상태다. 받은 쪽에서 차단이 풀려 있거나 완료 시각이
            // 비어 있으면 보드 표시와 재오픈 판정이 어긋난다.
            row.restoreExecutionState(item.blocked(), item.blockedReason(), item.doneAt());
            BacklogItem saved = backlogItemRepository.save(row);
            idMap.put(item.id(), saved.getId());
            linkByOldId.put(item.id(), newWbsItemId);
            queue.addAll(byParent.getOrDefault(item.id(), List.of()));
        }

        // 위 순회는 최상위에서 닿을 수 있는 항목만 넣는다. 검증이 상위 존재와 순환을 이미 막았으므로
        // 남는 항목이 있으면 이쪽 논리가 잘못된 것이다 (WBS 삽입과 같은 안전장치).
        if (idMap.size() != items.size()) {
            throw new IllegalStateException(
                    "Backlog 삽입이 누락되었습니다: 파일 %d개 중 %d개".formatted(items.size(), idMap.size()));
        }
        return idMap;
    }

    /**
     * Sprints first, then their assignments — an assignment's {@code sprintId} has to be a new id
     * that already exists, the same ordering the WBS and Backlog inserts need.
     *
     * <p>Lifecycle state and outcomes are restored as they were. A closed Sprint arriving as 계획
     * would erase what the project actually did, and the outcomes cannot be re-derived: an item's
     * current status says nothing about how it looked when a Sprint closed months ago.
     */
    private Map<Long, Long> insertSprints(Long projectId, List<ExportedSprint> sprints,
                                           List<ExportedSprintItem> assignments,
                                           Map<Long, Long> backlogIds) {
        Map<Long, Long> sprintIds = new HashMap<>();
        for (ExportedSprint sprint : sprints) {
            Sprint saved = sprintRepository.save(new Sprint(
                    projectId,
                    sprint.name(),
                    sprint.goal(),
                    sprint.startDate(),
                    sprint.endDate(),
                    sprint.status(),
                    sprint.closedAt()
            ));
            sprintIds.put(sprint.id(), saved.getId());
        }
        for (ExportedSprintItem assignment : assignments) {
            sprintItemRepository.save(new SprintItem(
                    projectId,
                    sprintIds.get(assignment.sprintId()),
                    backlogIds.get(assignment.backlogItemId()),
                    assignment.pointsAtStart(),
                    assignment.pointsAtClose(),
                    assignment.outcome(),
                    assignment.addedAt(),
                    assignment.removedAt()
            ));
        }
        return sprintIds;
    }
}
