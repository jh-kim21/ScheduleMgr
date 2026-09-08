package com.projectflow.application;

import com.projectflow.application.dto.SprintRequests.BoardMoveRequest;
import com.projectflow.application.dto.SprintRequests.SprintAssignRequest;
import com.projectflow.application.dto.SprintRequests.SprintCloseRequest;
import com.projectflow.application.dto.SprintRequests.SprintSaveRequest;
import com.projectflow.application.dto.SprintResponse;
import com.projectflow.application.dto.SprintResponse.SprintDetail;
import com.projectflow.application.dto.SprintResponse.SprintItemDetail;
import com.projectflow.domain.BacklogAssessor;
import com.projectflow.domain.BacklogItem;
import com.projectflow.domain.BacklogItemNotFoundException;
import com.projectflow.domain.BacklogItemRepository;
import com.projectflow.domain.BacklogStatus;
import com.projectflow.domain.CompletionCheck;
import com.projectflow.domain.InvalidSprintException;
import com.projectflow.domain.ProjectMember;
import com.projectflow.domain.ProjectMemberRepository;
import com.projectflow.domain.ProjectNotFoundException;
import com.projectflow.domain.ProjectRepository;
import com.projectflow.domain.RaidLinkTarget;
import com.projectflow.domain.Sprint;
import com.projectflow.domain.SprintAssessor;
import com.projectflow.domain.SprintItem;
import com.projectflow.domain.SprintItemOutcome;
import com.projectflow.domain.SprintItemRepository;
import com.projectflow.domain.SprintNotFoundException;
import com.projectflow.domain.SprintRepository;
import com.projectflow.domain.SprintStatus;
import com.projectflow.domain.WbsItem;
import com.projectflow.domain.WbsItemRepository;
import com.projectflow.domain.WbsNode;
import com.projectflow.domain.WbsTreeAssembler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Optional;

/**
 * Sprint planning, Board execution, closing and carry-over (Hybrid PM Step 4).
 *
 * <p><b>단일 팀 전제.</b> The project runs one team, so there is no team model and <b>at most one
 * Sprint may be ACTIVE</b>. That single rule is also what satisfies "동시에 두 활성 Sprint에
 * 배정하지 않는다" — combined with "an item has at most one live assignment across open Sprints",
 * an item cannot be counted twice anywhere.
 *
 * <p><b>배정은 지우지 않는다.</b> Removing an item stamps the row; closing settles it with an
 * outcome. So re-assigning a carried-over item never rewrites what the previous Sprint recorded,
 * and completed work is credited to exactly one Sprint.
 */
@Service
@Transactional(readOnly = true)
public class SprintService {

    /** Newest period first: the Sprint being planned or run is the one people look at. */
    private static final Comparator<Sprint> SPRINT_ORDER =
            Comparator.comparing(Sprint::getStartDate).reversed().thenComparing(Sprint::getId);

    private final SprintRepository sprintRepository;
    private final SprintItemRepository sprintItemRepository;
    private final BacklogItemRepository backlogItemRepository;
    private final WbsItemRepository wbsItemRepository;
    private final ProjectMemberRepository memberRepository;
    private final ProjectRepository projectRepository;

    /** Only to drop RAID links when a target disappears — the register itself is never
     * rebuilt from here (지시서 6-C). */
    private final RaidService raidService;

    public SprintService(SprintRepository sprintRepository,
                          SprintItemRepository sprintItemRepository,
                          BacklogItemRepository backlogItemRepository,
                          WbsItemRepository wbsItemRepository,
                          ProjectMemberRepository memberRepository,
                          ProjectRepository projectRepository,
                          RaidService raidService) {
        this.sprintRepository = sprintRepository;
        this.sprintItemRepository = sprintItemRepository;
        this.backlogItemRepository = backlogItemRepository;
        this.wbsItemRepository = wbsItemRepository;
        this.memberRepository = memberRepository;
        this.projectRepository = projectRepository;
        this.raidService = raidService;
    }

    public SprintResponse getSprints(Long projectId) {
        requireProject(projectId);
        return build(projectId);
    }

    // ------------------------------------------------------------------ 계획

    @Transactional
    public SprintResponse create(Long projectId, SprintSaveRequest request) {
        requireProject(projectId);
        requirePeriod(request);
        requireNameAvailable(projectId, request.name().trim(), null);

        sprintRepository.save(new Sprint(projectId, request.name().trim(),
                blankToNull(request.goal()), request.startDate(), request.endDate()));
        return build(projectId);
    }

    @Transactional
    public SprintResponse update(Long projectId, Long sprintId, SprintSaveRequest request) {
        requireProject(projectId);
        requirePeriod(request);
        Sprint sprint = requireSprintOfProject(projectId, sprintId);
        if (sprint.getStatus() == SprintStatus.CLOSED) {
            throw new InvalidSprintException("종료된 Sprint는 수정할 수 없습니다. 종료 결과는 이력입니다.");
        }
        requireNameAvailable(projectId, request.name().trim(), sprintId);

        sprint.update(request.name().trim(), blankToNull(request.goal()),
                request.startDate(), request.endDate());
        sprintRepository.save(sprint);
        return build(projectId);
    }

    /**
     * Deletes a Sprint that was never run. An ACTIVE one has to be closed — closing is what records
     * what happened — and a CLOSED one is history that other numbers refer to.
     */
    @Transactional
    public SprintResponse delete(Long projectId, Long sprintId) {
        requireProject(projectId);
        Sprint sprint = requireSprintOfProject(projectId, sprintId);
        if (sprint.getStatus() != SprintStatus.PLANNED) {
            throw new InvalidSprintException(sprint.getStatus() == SprintStatus.ACTIVE
                    ? "실행 중인 Sprint는 삭제할 수 없습니다. 먼저 종료하세요."
                    : "종료된 Sprint는 삭제할 수 없습니다. 종료 결과는 이력입니다.");
        }
        List<SprintItem> live = liveAssignmentsOf(projectId, sprintId);
        if (!live.isEmpty()) {
            throw new InvalidSprintException(
                    "배정된 항목이 %d건 있어 삭제할 수 없습니다. 항목을 먼저 제거하세요."
                            .formatted(live.size()));
        }
        raidService.detachTargets(projectId, RaidLinkTarget.SPRINT, Set.of(sprintId));
        sprintRepository.delete(sprint);
        return build(projectId);
    }

    /**
     * Starts a Sprint and stamps the estimates it is committing to.
     *
     * <p>Only one Sprint may run at a time (단일 팀). The estimates are re-stamped here rather than
     * trusted from assignment time because planning numbers keep changing until the Sprint begins.
     */
    @Transactional
    public SprintResponse start(Long projectId, Long sprintId) {
        requireProject(projectId);
        Sprint sprint = requireSprintOfProject(projectId, sprintId);
        if (sprint.getStatus() != SprintStatus.PLANNED) {
            throw new InvalidSprintException("계획 상태인 Sprint만 시작할 수 있습니다.");
        }
        activeSprint(projectId).ifPresent(active -> {
            throw new InvalidSprintException(
                    "이미 실행 중인 Sprint가 있습니다: '%s'. 한 번에 하나만 실행합니다 (단일 팀)."
                            .formatted(active.getName()));
        });

        Map<Long, BacklogItem> itemsById = backlogById(projectId);
        List<SprintItem> live = liveAssignmentsOf(projectId, sprintId);
        for (SprintItem assignment : live) {
            BacklogItem item = itemsById.get(assignment.getBacklogItemId());
            assignment.stampStartPoints(item == null ? null : item.getStoryPoint());
        }
        if (!live.isEmpty()) {
            sprintItemRepository.saveAll(live);
        }

        sprint.start();
        sprintRepository.save(sprint);
        return build(projectId);
    }

    /**
     * Closes a Sprint, recording each assignment's outcome, and optionally re-assigns the
     * incomplete ones.
     *
     * <p><b>항목의 상태는 건드리지 않는다.</b> Closing is the Sprint's statement about what it
     * achieved; whether the work is still in Review is the item's own business. That separation is
     * what lets a carried-over item finish in the next Sprint without the previous one's numbers
     * moving.
     */
    @Transactional
    public SprintResponse close(Long projectId, Long sprintId, SprintCloseRequest request) {
        requireProject(projectId);
        Sprint sprint = requireSprintOfProject(projectId, sprintId);
        if (sprint.getStatus() != SprintStatus.ACTIVE) {
            throw new InvalidSprintException(sprint.getStatus() == SprintStatus.CLOSED
                    ? "이미 종료된 Sprint입니다."
                    : "실행 중인 Sprint만 종료할 수 있습니다. 먼저 시작하세요.");
        }

        Sprint carryOverTarget = null;
        if (request != null && request.carryOverToSprintId() != null) {
            if (request.carryOverToSprintId().equals(sprintId)) {
                throw new InvalidSprintException("종료하는 Sprint로 이월할 수 없습니다.");
            }
            carryOverTarget = requireSprintOfProject(projectId, request.carryOverToSprintId());
            if (!carryOverTarget.open()) {
                throw new InvalidSprintException("종료된 Sprint로는 이월할 수 없습니다.");
            }
        }

        Map<Long, BacklogItem> itemsById = backlogById(projectId);
        List<SprintItem> live = liveAssignmentsOf(projectId, sprintId);
        List<SprintItem> touched = new ArrayList<>(live);

        for (SprintItem assignment : live) {
            BacklogItem item = itemsById.get(assignment.getBacklogItemId());
            boolean done = item != null && item.getStatus() == BacklogStatus.DONE;
            assignment.settle(done ? SprintItemOutcome.DONE : SprintItemOutcome.CARRIED_OVER,
                    item == null ? null : item.getStoryPoint());

            if (!done && carryOverTarget != null) {
                touched.add(new SprintItem(projectId, carryOverTarget.getId(),
                        assignment.getBacklogItemId(),
                        item == null ? null : item.getStoryPoint()));
            }
        }
        sprintItemRepository.saveAll(touched);

        sprint.close();
        sprintRepository.save(sprint);
        return build(projectId);
    }

    // ------------------------------------------------------------------ 배정

    /**
     * Puts a Backlog entry into a Sprint.
     *
     * <p>Three rules, all of which exist to keep one piece of work from being counted twice: the
     * entry has to be an aggregation unit with a usable Work Package link
     * ({@code readyForSprint}), the Sprint has to be open, and the entry may not already be live in
     * another open Sprint.
     */
    @Transactional
    public SprintResponse assign(Long projectId, Long sprintId, SprintAssignRequest request) {
        requireProject(projectId);
        Sprint sprint = requireSprintOfProject(projectId, sprintId);
        if (!sprint.open()) {
            throw new InvalidSprintException("종료된 Sprint에는 항목을 배정할 수 없습니다.");
        }

        BacklogItem item = requireBacklogItemOfProject(projectId, request.backlogItemId());
        requireAssignable(projectId, item);

        List<SprintItem> assignments = sprintItemRepository.findByProjectId(projectId);
        Map<Long, Sprint> sprintsById = sprintsById(projectId);
        for (SprintItem existing : assignments) {
            if (!existing.active() || !existing.getBacklogItemId().equals(item.getId())) {
                continue;
            }
            Sprint holder = sprintsById.get(existing.getSprintId());
            if (holder == null || !holder.open()) {
                continue;
            }
            throw new InvalidSprintException(holder.getId().equals(sprintId)
                    ? "이미 이 Sprint에 배정된 항목입니다."
                    : "'%s'에 이미 배정되어 있습니다. 한 항목은 한 Sprint에만 들어갑니다."
                            .formatted(holder.getName()));
        }

        sprintItemRepository.save(new SprintItem(projectId, sprintId, item.getId(),
                item.getStoryPoint()));
        return build(projectId);
    }

    /** Takes an entry out of an open Sprint. The row stays, stamped as {@code REMOVED}. */
    @Transactional
    public SprintResponse unassign(Long projectId, Long sprintId, Long backlogItemId) {
        requireProject(projectId);
        Sprint sprint = requireSprintOfProject(projectId, sprintId);
        if (!sprint.open()) {
            throw new InvalidSprintException("종료된 Sprint의 배정은 바꿀 수 없습니다. 종료 결과는 이력입니다.");
        }
        SprintItem assignment = liveAssignmentsOf(projectId, sprintId).stream()
                .filter(candidate -> candidate.getBacklogItemId().equals(backlogItemId))
                .findFirst()
                .orElseThrow(() -> new InvalidSprintException("이 Sprint에 배정되지 않은 항목입니다."));

        assignment.removeFromSprint();
        sprintItemRepository.save(assignment);
        return build(projectId);
    }

    // ------------------------------------------------------------------ Board

    /**
     * One board move: the column, the blocked flag, or both.
     *
     * <p>Reaching Done goes through {@link CompletionCheck} — the minimum completion procedure.
     * Leaving Done is always allowed and clears the entry's completion time, while the Sprint
     * outcomes already recorded stay exactly as they were (지시서 10항).
     */
    @Transactional
    public SprintResponse move(Long projectId, Long sprintId, Long backlogItemId,
                                BoardMoveRequest request) {
        requireProject(projectId);
        Sprint sprint = requireSprintOfProject(projectId, sprintId);
        if (!sprint.open()) {
            throw new InvalidSprintException("종료된 Sprint의 항목은 보드에서 옮길 수 없습니다.");
        }
        liveAssignmentsOf(projectId, sprintId).stream()
                .filter(candidate -> candidate.getBacklogItemId().equals(backlogItemId))
                .findFirst()
                .orElseThrow(() -> new InvalidSprintException("이 Sprint에 배정되지 않은 항목입니다."));

        BacklogItem item = requireBacklogItemOfProject(projectId, backlogItemId);

        // 차단은 상태와 별개라 같은 요청에서 함께 바뀔 수 있다. 차단 해제를 먼저 적용해야
        // "차단 해제 + 완료"를 한 번에 보낼 수 있다.
        if (request.blocked() != null) {
            if (request.blocked()) {
                item.block(blankToNull(request.blockedReason()));
            } else {
                item.unblock();
            }
        }

        if (request.status() == BacklogStatus.DONE && item.getStatus() != BacklogStatus.DONE) {
            CompletionCheck.blocker(item, request.confirmed()).ifPresent(reason -> {
                throw new InvalidSprintException(reason);
            });
        }
        item.changeStatus(request.status());
        backlogItemRepository.save(item);
        return build(projectId);
    }

    // ------------------------------------------------- 다른 서비스가 쓰는 조회

    /**
     * Live assignments of {@code backlogItemIds} in Sprints that are not closed.
     *
     * <p>Used by {@code BacklogService} before deleting or archiving an entry: dropping something a
     * running Sprint is working on would take the Sprint's record with it.
     */
    public List<SprintItem> liveAssignmentsFor(Long projectId, List<Long> backlogItemIds) {
        Map<Long, Sprint> sprintsById = sprintsById(projectId);
        return sprintItemRepository.findByProjectId(projectId).stream()
                .filter(SprintItem::active)
                .filter(assignment -> backlogItemIds.contains(assignment.getBacklogItemId()))
                .filter(assignment -> {
                    Sprint sprint = sprintsById.get(assignment.getSprintId());
                    return sprint != null && sprint.open();
                })
                .toList();
    }

    /** Name of the open Sprint an entry is live in, keyed by Backlog id. */
    public Map<Long, String> openSprintNamesByItem(Long projectId) {
        Map<Long, Sprint> sprintsById = sprintsById(projectId);
        Map<Long, String> names = new HashMap<>();
        for (SprintItem assignment : sprintItemRepository.findByProjectId(projectId)) {
            if (!assignment.active()) {
                continue;
            }
            Sprint sprint = sprintsById.get(assignment.getSprintId());
            if (sprint != null && sprint.open()) {
                names.put(assignment.getBacklogItemId(), sprint.getName());
            }
        }
        return names;
    }

    // ------------------------------------------------------------------ 검증

    private void requireAssignable(Long projectId, BacklogItem item) {
        WbsItem owner = item.getWbsItemId() == null ? null
                : wbsItemRepository.findByProjectId(projectId).stream()
                        .filter(candidate -> candidate.getId().equals(item.getWbsItemId()))
                        .findFirst()
                        .orElse(null);
        BacklogAssessor.BacklogAssessment assessment = BacklogAssessor.assess(item, owner);
        if (assessment.readyForSprint()) {
            return;
        }
        if (!item.aggregated()) {
            throw new InvalidSprintException(
                    "%s은(는) Sprint에 배정할 수 없습니다. 완료 가능한 Story 또는 Bug만 배정합니다."
                            .formatted(item.getItemType()));
        }
        if (item.archived()) {
            throw new InvalidSprintException("보관된 항목은 Sprint에 배정할 수 없습니다.");
        }
        throw new InvalidSprintException(
                "귀속 Work Package가 없거나 쓸 수 없는 상태입니다. Sprint에 넣기 전에 연결을 정리하세요.");
    }

    private void requirePeriod(SprintSaveRequest request) {
        if (request.endDate().isBefore(request.startDate())) {
            throw new InvalidSprintException("종료일이 시작일보다 앞설 수 없습니다.");
        }
    }

    private void requireNameAvailable(Long projectId, String name, Long selfId) {
        boolean taken = sprintRepository.findByProjectId(projectId).stream()
                .filter(sprint -> selfId == null || !sprint.getId().equals(selfId))
                .anyMatch(sprint -> sprint.getName().equals(name));
        if (taken) {
            throw new InvalidSprintException("같은 이름의 Sprint가 이미 있습니다: " + name);
        }
    }

    // ------------------------------------------------------------------ 조립

    private SprintResponse build(Long projectId) {
        List<Sprint> sprints = sprintRepository.findByProjectId(projectId).stream()
                .sorted(SPRINT_ORDER)
                .toList();
        List<SprintItem> assignments = sprintItemRepository.findByProjectId(projectId);
        Map<Long, BacklogItem> itemsById = backlogById(projectId);

        Map<Long, String> memberNames = new HashMap<>();
        for (ProjectMember member : memberRepository.findByProjectId(projectId)) {
            memberNames.put(member.getId(), member.getName());
        }

        // WBS 코드는 트리 위치에서 파생되므로 코드를 붙이려면 트리를 조립해야 한다.
        List<WbsItem> wbsItems = wbsItemRepository.findByProjectId(projectId);
        Map<Long, WbsNode> wbsNodes = new HashMap<>();
        collectNodes(WbsTreeAssembler.assemble(wbsItems), wbsNodes);
        Map<Long, WbsItem> wbsById = new HashMap<>();
        for (WbsItem wbsItem : wbsItems) {
            wbsById.put(wbsItem.getId(), wbsItem);
        }

        boolean anyActive = sprints.stream().anyMatch(s -> s.getStatus() == SprintStatus.ACTIVE);
        Long activeSprintId = sprints.stream()
                .filter(s -> s.getStatus() == SprintStatus.ACTIVE)
                .map(Sprint::getId)
                .findFirst()
                .orElse(null);

        List<SprintDetail> details = new ArrayList<>(sprints.size());
        for (Sprint sprint : sprints) {
            List<SprintItem> mine = assignments.stream()
                    .filter(assignment -> assignment.getSprintId().equals(sprint.getId()))
                    .toList();
            SprintAssessor.SprintProgress progress =
                    SprintAssessor.assess(sprint, mine, itemsById);

            // 종료된 Sprint는 결과가 찍힌 배정을 모두 보여준다(제거된 것 포함 — 왜 빠졌는지가
            // 이력이다). 열린 Sprint는 지금 들어 있는 것만 보여준다.
            List<SprintItem> shown = sprint.open()
                    ? mine.stream().filter(SprintItem::active).toList()
                    : mine;

            List<SprintItemDetail> items = new ArrayList<>(shown.size());
            for (SprintItem assignment : shown) {
                BacklogItem item = itemsById.get(assignment.getBacklogItemId());
                if (item == null) {
                    continue;
                }
                WbsNode node = item.getWbsItemId() == null ? null : wbsNodes.get(item.getWbsItemId());
                WbsItem owner = item.getWbsItemId() == null ? null : wbsById.get(item.getWbsItemId());
                int openChildren = (int) itemsById.values().stream()
                        .filter(child -> item.getId().equals(child.getParentId()))
                        .filter(child -> child.getStatus() != BacklogStatus.DONE)
                        .count();

                items.add(new SprintItemDetail(
                        assignment.getId(),
                        item.getId(),
                        item.getItemType(),
                        item.getTitle(),
                        item.getPriority(),
                        item.getStatus(),
                        item.blocked(),
                        item.getBlockedReason(),
                        item.getAssigneeMemberId(),
                        item.getAssigneeMemberId() == null
                                ? null
                                : memberNames.get(item.getAssigneeMemberId()),
                        item.getAcceptanceCriteria(),
                        item.getStoryPoint(),
                        item.getWbsItemId(),
                        node == null ? null : node.code(),
                        owner == null ? null : owner.getName(),
                        assignment.getPointsAtStart(),
                        assignment.getPointsAtClose(),
                        assignment.getOutcome(),
                        assignment.getOutcome() == SprintItemOutcome.REMOVED,
                        openChildren,
                        // 그 Sprint가 완료로 찍었는데 지금은 완료가 아니면 재오픈된 것이다.
                        assignment.getOutcome() == SprintItemOutcome.DONE
                                && item.getStatus() != BacklogStatus.DONE,
                        item.getDoneAt()
                ));
            }

            boolean live = mine.stream().anyMatch(SprintItem::active);
            details.add(new SprintDetail(
                    sprint.getId(),
                    sprint.getName(),
                    sprint.getGoal(),
                    sprint.getStartDate(),
                    sprint.getEndDate(),
                    sprint.getStatus(),
                    sprint.getClosedAt(),
                    progress.plannedItems(),
                    progress.plannedPoints(),
                    progress.doneItems(),
                    progress.donePoints(),
                    progress.blockedItems(),
                    progress.carriedOverItems(),
                    sprint.getStatus() == SprintStatus.PLANNED && !anyActive,
                    sprint.getStatus() == SprintStatus.PLANNED && !live,
                    items
            ));
        }

        return new SprintResponse(activeSprintId, details);
    }

    // ------------------------------------------------------------------ 도우미

    private List<SprintItem> liveAssignmentsOf(Long projectId, Long sprintId) {
        return sprintItemRepository.findByProjectId(projectId).stream()
                .filter(assignment -> assignment.getSprintId().equals(sprintId))
                .filter(SprintItem::active)
                .toList();
    }

    private Optional<Sprint> activeSprint(Long projectId) {
        return sprintRepository.findByProjectId(projectId).stream()
                .filter(sprint -> sprint.getStatus() == SprintStatus.ACTIVE)
                .findFirst();
    }

    private Map<Long, Sprint> sprintsById(Long projectId) {
        Map<Long, Sprint> byId = new HashMap<>();
        for (Sprint sprint : sprintRepository.findByProjectId(projectId)) {
            byId.put(sprint.getId(), sprint);
        }
        return byId;
    }

    private Map<Long, BacklogItem> backlogById(Long projectId) {
        Map<Long, BacklogItem> byId = new HashMap<>();
        for (BacklogItem item : backlogItemRepository.findByProjectId(projectId)) {
            byId.put(item.getId(), item);
        }
        return byId;
    }

    private void requireProject(Long projectId) {
        if (!projectRepository.existsById(projectId)) {
            throw new ProjectNotFoundException(projectId);
        }
    }

    private Sprint requireSprintOfProject(Long projectId, Long sprintId) {
        return sprintRepository.findByProjectId(projectId).stream()
                .filter(sprint -> sprint.getId().equals(sprintId))
                .findFirst()
                .orElseThrow(() -> new SprintNotFoundException(sprintId));
    }

    private BacklogItem requireBacklogItemOfProject(Long projectId, Long backlogItemId) {
        return backlogItemRepository.findByProjectId(projectId).stream()
                .filter(item -> item.getId().equals(backlogItemId))
                .findFirst()
                .orElseThrow(() -> new BacklogItemNotFoundException(backlogItemId));
    }

    private static void collectNodes(List<WbsNode> nodes, Map<Long, WbsNode> byId) {
        for (WbsNode node : nodes) {
            byId.put(node.item().getId(), node);
            collectNodes(node.children(), byId);
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
