package com.projectflow.application;

import com.projectflow.application.dto.BacklogItemRequest;
import com.projectflow.application.dto.BacklogResponse;
import com.projectflow.application.dto.BacklogResponse.BacklogItemResponse;
import com.projectflow.application.dto.BacklogSummary;
import com.projectflow.domain.BacklogAssessor;
import com.projectflow.domain.BacklogItem;
import com.projectflow.domain.BacklogItemNotFoundException;
import com.projectflow.domain.BacklogItemRepository;
import com.projectflow.domain.BacklogItemType;
import com.projectflow.domain.ChangeLog;
import com.projectflow.domain.ChangeLogRepository;
import com.projectflow.domain.ChangeReason;
import com.projectflow.domain.BacklogStatus;
import com.projectflow.domain.CompletionCheck;
import com.projectflow.domain.InvalidBacklogItemException;
import com.projectflow.domain.ProjectMember;
import com.projectflow.domain.ProjectMemberNotFoundException;
import com.projectflow.domain.ProjectMemberRepository;
import com.projectflow.domain.ProjectNotFoundException;
import com.projectflow.domain.ProjectRepository;
import com.projectflow.domain.RaidLinkTarget;
import com.projectflow.domain.SprintItem;
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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The Product Backlog (Hybrid PM Step 3) — the execution items that implement a Work Package.
 *
 * <p><b>귀속은 아래로 상속된다.</b> An entry with a parent always carries the parent's Work Package,
 * and re-attaching a parent moves its whole subtree. That is why "상하위 귀속 불일치" is not a
 * validation here but an impossibility: there is only one place the value can come from.
 *
 * <p><b>구조는 거부하고, 계획은 표시한다.</b> A Task with no parent, a parent cycle, a link to a
 * Summary — none of those can be stored (400). An unlinked draft or a Story under a Waterfall Work
 * Package can: {@link BacklogAssessor} reports them and the screen shows them. Same split the
 * schedule and RACI screens already use.
 */
@Service
@Transactional(readOnly = true)
public class BacklogService {

    /** Families stay together, ordered by priority then id — see {@link #orderForDisplay}. */
    private static final Comparator<BacklogItem> SIBLING_ORDER =
            Comparator.<BacklogItem, Integer>comparing(item -> item.getPriority().ordinal())
                    .thenComparing(BacklogItem::getId);

    private final BacklogItemRepository backlogItemRepository;
    private final ChangeLogRepository changeLogRepository;
    private final WbsItemRepository wbsItemRepository;
    private final ProjectMemberRepository memberRepository;
    private final ProjectRepository projectRepository;
    private final SprintService sprintService;

    /** Only to drop RAID links when a target disappears — the register itself is never
     * rebuilt from here (지시서 6-C). */
    private final RaidService raidService;

    public BacklogService(BacklogItemRepository backlogItemRepository,
                           ChangeLogRepository changeLogRepository,
                           WbsItemRepository wbsItemRepository,
                           ProjectMemberRepository memberRepository,
                           ProjectRepository projectRepository,
                           SprintService sprintService,
                           RaidService raidService) {
        this.backlogItemRepository = backlogItemRepository;
        this.changeLogRepository = changeLogRepository;
        this.wbsItemRepository = wbsItemRepository;
        this.memberRepository = memberRepository;
        this.projectRepository = projectRepository;
        this.sprintService = sprintService;
        this.raidService = raidService;
    }

    public BacklogResponse getBacklog(Long projectId) {
        requireProject(projectId);
        return build(projectId);
    }

    @Transactional
    public BacklogResponse addItem(Long projectId, BacklogItemRequest request) {
        requireProject(projectId);
        List<BacklogItem> items = backlogItemRepository.findByProjectId(projectId);
        requireAssigneeOfProject(projectId, request.assigneeMemberId());

        BacklogItem parent = resolveParent(projectId, items, request.parentId(), null,
                request.itemType());
        Long wbsItemId = resolveLink(projectId, request.wbsItemId(), parent);
        requireCompletable(null, request);

        int sortOrder = items.stream().mapToInt(BacklogItem::getSortOrder).max().orElse(-1) + 1;

        BacklogItem saved = backlogItemRepository.save(new BacklogItem(
                projectId,
                wbsItemId,
                request.parentId(),
                request.itemType(),
                request.title().trim(),
                blankToNull(request.description()),
                request.priority(),
                request.status(),
                request.assigneeMemberId(),
                blankToNull(request.acceptanceCriteria()),
                request.storyPoint(),
                request.progressWeight(),
                sortOrder
        ));
        if (wbsItemId != null) {
            recordLinkChange(projectId, saved.getId(), null, wbsItemId,
                    parent == null ? ChangeReason.REASSIGNED
                                    : ChangeReason.INHERITED_FROM_PARENT);
        }
        return build(projectId);
    }

    @Transactional
    public BacklogResponse updateItem(Long projectId, Long itemId, BacklogItemRequest request) {
        requireProject(projectId);
        List<BacklogItem> items = backlogItemRepository.findByProjectId(projectId);
        BacklogItem item = requireItemOfProject(items, itemId);
        requireAssigneeOfProject(projectId, request.assigneeMemberId());

        BacklogItem parent = resolveParent(projectId, items, request.parentId(), itemId,
                request.itemType());
        requireTypeStillAcceptsChildren(items, itemId, request.itemType());
        Long wbsItemId = resolveLink(projectId, request.wbsItemId(), parent);

        requireCompletable(item, request);

        Long previousWbsItemId = item.getWbsItemId();
        item.update(
                wbsItemId,
                request.parentId(),
                request.itemType(),
                request.title().trim(),
                blankToNull(request.description()),
                request.priority(),
                request.status(),
                request.assigneeMemberId(),
                blankToNull(request.acceptanceCriteria()),
                request.storyPoint(),
                request.progressWeight()
        );
        backlogItemRepository.save(item);

        if (!Objects.equals(previousWbsItemId, wbsItemId)) {
            recordLinkChange(projectId, itemId, previousWbsItemId, wbsItemId,
                    ChangeReason.REASSIGNED);
            // 하위는 상위의 귀속을 따른다. 이력도 각각 남겨야 "왜 옮겨졌는지"를 설명할 수 있다.
            cascadeLink(projectId, items, itemId, wbsItemId);
        }
        return build(projectId);
    }

    /**
     * Puts an entry aside, or brings it back. 보관 keeps the entry's status: what state the work was
     * left in is part of the record, and it is also what makes archiving the answer to "이 Work
     * Package를 지우고 싶은데 Backlog가 붙어 있다".
     */
    @Transactional
    public BacklogResponse setArchived(Long projectId, Long itemId, boolean archived) {
        requireProject(projectId);
        BacklogItem item = requireItemOfProject(backlogItemRepository.findByProjectId(projectId), itemId);
        if (archived) {
            // 열린 Sprint가 작업 중인 항목을 접어두면 보드에서 사라지고 그 Sprint의 계획도
            // 말없이 줄어든다. Sprint에서 먼저 빼야 한다.
            requireNotInOpenSprint(projectId, itemId, "보관할 수 없습니다");
            item.archive();
        } else {
            item.restore();
        }
        backlogItemRepository.save(item);
        return build(projectId);
    }

    /**
     * Deletes one entry. An entry with children is refused rather than cascaded: the database would
     * happily take the whole subtree with it, and losing a Story's Tasks because someone deleted
     * the Story is exactly the silent loss Step 3 is asked to prevent.
     */
    @Transactional
    public BacklogResponse deleteItem(Long projectId, Long itemId) {
        requireProject(projectId);
        List<BacklogItem> items = backlogItemRepository.findByProjectId(projectId);
        BacklogItem item = requireItemOfProject(items, itemId);

        List<BacklogItem> children = childrenOf(items, itemId);
        if (!children.isEmpty()) {
            throw new InvalidBacklogItemException(
                    "'%s'에 하위 항목이 %d건 있어 삭제할 수 없습니다. 하위 항목을 먼저 옮기거나 삭제하세요."
                            .formatted(item.getTitle(), children.size()));
        }
        // sprint_items 의 FK가 CASCADE 라 지우면 그 Sprint의 배정 기록까지 사라진다.
        // WBS 삭제 가드와 같은 이유로 먼저 막는다.
        requireNotInOpenSprint(projectId, itemId, "삭제할 수 없습니다");
        raidService.detachTargets(projectId, RaidLinkTarget.BACKLOG_ITEM, Set.of(itemId));
        backlogItemRepository.delete(item);
        return build(projectId);
    }

    // ------------------------------------------------------- WBS 화면이 쓰는 조회

    /**
     * Backlog counts per WBS entry, keyed by the entry the items are linked to.
     *
     * <p>Not rolled up here — {@code WbsService} does that as it walks the tree, since only it
     * knows the shape. Unlinked entries are absent from the map by construction.
     */
    public Map<Long, BacklogSummary> summariesByWbsItem(Long projectId) {
        Map<Long, BacklogSummary> summaries = new HashMap<>();
        for (BacklogItem item : backlogItemRepository.findByProjectId(projectId)) {
            Long wbsItemId = item.getWbsItemId();
            if (wbsItemId == null) {
                continue;
            }
            BacklogSummary one = item.archived()
                    ? new BacklogSummary(0, 0, 1)
                    : new BacklogSummary(1, item.getStatus() == BacklogStatus.DONE ? 1 : 0, 0);
            summaries.merge(wbsItemId, one, BacklogSummary::plus);
        }
        return summaries;
    }

    // ------------------------------------------------------- WBS 삭제 시 연결 손실 방지

    /**
     * Backlog entries attached to any of {@code wbsItemIds} that are not archived.
     *
     * <p>Used by {@code WbsService} before deleting a subtree. The rule (설계 §11.3-6) is that a
     * Work Package with linked entries may not just vanish — the user has to re-attach them or
     * archive them first. Archived entries do not block: putting them aside <em>is</em> the
     * explicit act the rule asks for.
     */
    public List<BacklogItem> activeItemsLinkedTo(Long projectId, Set<Long> wbsItemIds) {
        return backlogItemRepository.findByProjectId(projectId).stream()
                .filter(item -> item.getWbsItemId() != null && wbsItemIds.contains(item.getWbsItemId()))
                .filter(item -> !item.archived())
                .toList();
    }

    /**
     * Detaches archived entries from Work Packages that are about to be deleted, recording why.
     *
     * <p>The foreign key would null the column anyway; doing it here first means the reason is in
     * the history instead of the link simply being gone one day.
     */
    @Transactional
    public void detachArchivedBefore(Long projectId, Set<Long> wbsItemIds) {
        List<BacklogItem> affected = backlogItemRepository.findByProjectId(projectId).stream()
                .filter(item -> item.getWbsItemId() != null && wbsItemIds.contains(item.getWbsItemId()))
                .toList();
        List<ChangeLog> changes = new ArrayList<>(affected.size());
        for (BacklogItem item : affected) {
            changes.add(ChangeLog.of(projectId, ChangeLog.BACKLOG_ITEM, item.getId(), "wbsItemId",
                    item.getWbsItemId(), null, ChangeReason.WBS_ITEM_DELETED));
            item.relinkTo(null);
        }
        if (!affected.isEmpty()) {
            backlogItemRepository.saveAll(affected);
            changeLogRepository.saveAll(changes);
        }
    }

    // ------------------------------------------------------------------ 검증

    /**
     * Resolves and checks the parent, returning it (or {@code null} for a top-level entry).
     *
     * <p>{@code itemId} is the entry being updated, or {@code null} when creating — it is what lets
     * this reject making an entry its own descendant.
     */
    private BacklogItem resolveParent(Long projectId, List<BacklogItem> items, Long parentId,
                                       Long itemId, BacklogItemType itemType) {
        if (parentId == null) {
            if (!itemType.topLevel()) {
                throw new InvalidBacklogItemException(
                        "Task는 상위 항목(Story 또는 Bug)이 있어야 합니다. Work Package에 직접 붙일 수 없습니다.");
            }
            return null;
        }
        if (parentId.equals(itemId)) {
            throw new InvalidBacklogItemException("항목을 자기 자신의 하위로 둘 수 없습니다.");
        }
        BacklogItem parent = requireItemOfProject(items, parentId);
        requireParentAccepts(parent.getItemType(), itemType);
        // Defence in depth: the type rules already make a cycle impossible (a Story's only allowed
        // parent is an Epic, and an Epic may have none), so nothing reaches this today. It stays
        // because it is what keeps the tree acyclic the day Epic nesting is allowed.
        if (itemId != null && descendantIds(items, itemId).contains(parentId)) {
            throw new InvalidBacklogItemException("항목을 자신의 하위 항목 아래로 둘 수 없습니다.");
        }
        return parent;
    }

    /** The only two shapes the hierarchy allows: Epic → Story·Bug, and Story·Bug → Task. */
    private void requireParentAccepts(BacklogItemType parentType, BacklogItemType childType) {
        boolean allowed = switch (childType) {
            case EPIC -> false;
            case STORY, BUG -> parentType == BacklogItemType.EPIC;
            case TASK -> parentType == BacklogItemType.STORY || parentType == BacklogItemType.BUG;
        };
        if (!allowed) {
            throw new InvalidBacklogItemException(
                    childType == BacklogItemType.EPIC
                            ? "Epic은 다른 항목의 하위가 될 수 없습니다."
                            : "%s는 %s의 하위가 될 수 없습니다.".formatted(childType, parentType));
        }
    }

    /** Changing an entry's own type must not orphan the children it already has. */
    private void requireTypeStillAcceptsChildren(List<BacklogItem> items, Long itemId,
                                                   BacklogItemType newType) {
        for (BacklogItem child : childrenOf(items, itemId)) {
            requireParentAccepts(newType, child.getItemType());
        }
    }

    /**
     * The Work Package this entry belongs to.
     *
     * <p>With a parent there is nothing to decide — the entry inherits. A request that names a
     * different Work Package is rejected rather than quietly corrected, so a client cannot believe
     * it moved a Task away from its Story.
     */
    private Long resolveLink(Long projectId, Long requestedWbsItemId, BacklogItem parent) {
        if (parent != null) {
            if (requestedWbsItemId != null
                    && !requestedWbsItemId.equals(parent.getWbsItemId())) {
                throw new InvalidBacklogItemException(
                        "하위 항목의 귀속은 상위 항목을 따릅니다. 귀속을 바꾸려면 상위 항목을 옮기세요.");
            }
            return parent.getWbsItemId();
        }
        if (requestedWbsItemId == null) {
            return null;
        }
        WbsItem owner = wbsItemRepository.findByProjectId(projectId).stream()
                .filter(candidate -> candidate.getId().equals(requestedWbsItemId))
                .findFirst()
                .orElseThrow(() -> new WbsItemNotFoundException(requestedWbsItemId));
        if (!owner.workPackage()) {
            throw new InvalidBacklogItemException(
                    "'%s'은(는) Summary라 Backlog를 귀속시킬 수 없습니다. 최하위 Work Package에 연결하세요."
                            .formatted(owner.getName()));
        }
        return requestedWbsItemId;
    }

    /**
     * The completion procedure applies to the edit form too, not just the board.
     *
     * <p>If it did not, the form would be a way around it and the gate would be decorative. Only an
     * actual transition <em>into</em> Done is checked — re-saving an entry that is already complete
     * must not demand a fresh confirmation.
     */
    private void requireCompletable(BacklogItem existing, BacklogItemRequest request) {
        if (request.status() != BacklogStatus.DONE) {
            return;
        }
        if (existing != null && existing.getStatus() == BacklogStatus.DONE) {
            return;
        }
        BacklogItem subject = existing != null ? existing : draftFor(request);
        CompletionCheck.blocker(subject, request.confirmed()).ifPresent(reason -> {
            throw new InvalidBacklogItemException(reason);
        });
    }

    /** A stand-in for a not-yet-saved entry, so the same check can run before it exists. */
    private BacklogItem draftFor(BacklogItemRequest request) {
        return new BacklogItem(null, null, null, request.itemType(), request.title(), null,
                request.priority(), BacklogStatus.TODO, null,
                blankToNull(request.acceptanceCriteria()), null, null, 0);
    }

    private void requireAssigneeOfProject(Long projectId, Long assigneeMemberId) {
        if (assigneeMemberId == null) {
            return;
        }
        boolean present = memberRepository.findByProjectId(projectId).stream()
                .anyMatch(member -> member.getId().equals(assigneeMemberId));
        if (!present) {
            throw new ProjectMemberNotFoundException(assigneeMemberId);
        }
    }

    // ------------------------------------------------------------------ 조립

    private BacklogResponse build(Long projectId) {
        List<BacklogItem> items = backlogItemRepository.findByProjectId(projectId);

        Map<Long, WbsItem> wbsById = new HashMap<>();
        for (WbsItem wbsItem : wbsItemRepository.findByProjectId(projectId)) {
            wbsById.put(wbsItem.getId(), wbsItem);
        }
        // WBS 코드는 트리 위치에서 파생되므로 코드를 붙이려면 트리를 조립해야 한다 (RaidService와 동일).
        Map<Long, WbsNode> wbsNodes = new HashMap<>();
        collectNodes(WbsTreeAssembler.assemble(new ArrayList<>(wbsById.values())), wbsNodes);

        Map<Long, String> memberNames = new HashMap<>();
        for (ProjectMember member : memberRepository.findByProjectId(projectId)) {
            memberNames.put(member.getId(), member.getName());
        }

        Map<Long, BacklogItem> itemsById = new HashMap<>();
        for (BacklogItem item : items) {
            itemsById.put(item.getId(), item);
        }

        // 열린 Sprint에 들어 있으면 삭제·보관이 거부된다. 화면이 미리 설명할 수 있게 이름을 싣는다.
        Map<Long, String> openSprintNames = sprintService.openSprintNamesByItem(projectId);

        List<BacklogItemResponse> responses = new ArrayList<>(items.size());
        for (Ordered ordered : orderForDisplay(items)) {
            BacklogItem item = ordered.item();
            WbsItem owner = item.getWbsItemId() == null ? null : wbsById.get(item.getWbsItemId());
            WbsNode node = item.getWbsItemId() == null ? null : wbsNodes.get(item.getWbsItemId());
            BacklogItem parent = item.getParentId() == null ? null : itemsById.get(item.getParentId());
            BacklogAssessor.BacklogAssessment assessment = BacklogAssessor.assess(item, owner);

            responses.add(new BacklogItemResponse(
                    item.getId(),
                    item.getWbsItemId(),
                    node == null ? null : node.code(),
                    owner == null ? null : owner.getName(),
                    owner == null ? null : owner.getExecutionMode(),
                    item.getParentId(),
                    parent == null ? null : parent.getTitle(),
                    ordered.depth(),
                    item.getItemType(),
                    item.getTitle(),
                    item.getDescription(),
                    item.getPriority(),
                    item.getStatus(),
                    item.getAssigneeMemberId(),
                    item.getAssigneeMemberId() == null
                            ? null
                            : memberNames.get(item.getAssigneeMemberId()),
                    item.getAcceptanceCriteria(),
                    item.getStoryPoint(),
                    item.getProgressWeight(),
                    item.getArchivedAt(),
                    item.archived(),
                    item.blocked(),
                    item.getBlockedReason(),
                    item.getDoneAt(),
                    openSprintNames.get(item.getId()),
                    item.aggregated(),
                    childrenOf(items, item.getId()).size(),
                    assessment.unlinked(),
                    assessment.linkedToSummary(),
                    assessment.danglingLink(),
                    assessment.requiresExecutionModeChange(),
                    assessment.readyForSprint()
            ));
        }

        int unlinkedCount = (int) items.stream()
                .filter(item -> item.getWbsItemId() == null && !item.archived())
                .count();
        return new BacklogResponse(unlinkedCount, responses);
    }

    /**
     * Depth-first so a family reads as a block: an Epic, then its Stories, then each Story's Tasks.
     *
     * <p>The Product Backlog is an ordered list, not a tree (설계 §4.1), so this is presentation
     * order only — nothing is derived from the position, unlike a WBS code.
     */
    private List<Ordered> orderForDisplay(List<BacklogItem> items) {
        Map<Long, List<BacklogItem>> byParent = new HashMap<>();
        List<BacklogItem> roots = new ArrayList<>();
        for (BacklogItem item : items) {
            if (item.getParentId() == null) {
                roots.add(item);
            } else {
                byParent.computeIfAbsent(item.getParentId(), key -> new ArrayList<>()).add(item);
            }
        }
        roots.sort(SIBLING_ORDER);
        byParent.values().forEach(children -> children.sort(SIBLING_ORDER));

        List<Ordered> ordered = new ArrayList<>(items.size());
        for (BacklogItem root : roots) {
            appendWithChildren(root, 0, byParent, ordered);
        }
        // 상위가 사라진 항목(예: 손으로 편집한 데이터)이 목록에서 빠지지 않게 뒤에 붙인다.
        if (ordered.size() != items.size()) {
            Set<Long> placed = new LinkedHashSet<>();
            ordered.forEach(entry -> placed.add(entry.item().getId()));
            items.stream()
                    .filter(item -> !placed.contains(item.getId()))
                    .sorted(SIBLING_ORDER)
                    .forEach(item -> ordered.add(new Ordered(item, 0)));
        }
        return ordered;
    }

    private void appendWithChildren(BacklogItem item, int depth,
                                     Map<Long, List<BacklogItem>> byParent, List<Ordered> ordered) {
        ordered.add(new Ordered(item, depth));
        for (BacklogItem child : byParent.getOrDefault(item.getId(), List.of())) {
            appendWithChildren(child, depth + 1, byParent, ordered);
        }
    }

    private record Ordered(BacklogItem item, int depth) {
    }

    // ------------------------------------------------------------------ 도우미

    private void cascadeLink(Long projectId, List<BacklogItem> items, Long itemId, Long wbsItemId) {
        Set<Long> descendants = descendantIds(items, itemId);
        List<BacklogItem> moved = new ArrayList<>();
        List<ChangeLog> changes = new ArrayList<>();
        for (BacklogItem candidate : items) {
            if (!descendants.contains(candidate.getId())
                    || Objects.equals(candidate.getWbsItemId(), wbsItemId)) {
                continue;
            }
            changes.add(ChangeLog.of(projectId, ChangeLog.BACKLOG_ITEM, candidate.getId(),
                    "wbsItemId", candidate.getWbsItemId(), wbsItemId,
                    ChangeReason.INHERITED_FROM_PARENT));
            candidate.relinkTo(wbsItemId);
            moved.add(candidate);
        }
        if (!moved.isEmpty()) {
            backlogItemRepository.saveAll(moved);
            changeLogRepository.saveAll(changes);
        }
    }

    private Set<Long> descendantIds(List<BacklogItem> items, Long ancestorId) {
        Map<Long, List<BacklogItem>> byParent = new HashMap<>();
        for (BacklogItem item : items) {
            if (item.getParentId() != null) {
                byParent.computeIfAbsent(item.getParentId(), key -> new ArrayList<>()).add(item);
            }
        }
        Set<Long> collected = new LinkedHashSet<>();
        collectDescendants(ancestorId, byParent, collected);
        return collected;
    }

    private void collectDescendants(Long parentId, Map<Long, List<BacklogItem>> byParent,
                                     Set<Long> collected) {
        for (BacklogItem child : byParent.getOrDefault(parentId, List.of())) {
            if (collected.add(child.getId())) {
                collectDescendants(child.getId(), byParent, collected);
            }
        }
    }

    private List<BacklogItem> childrenOf(List<BacklogItem> items, Long itemId) {
        return items.stream()
                .filter(candidate -> itemId.equals(candidate.getParentId()))
                .toList();
    }

    /**
     * Refuses to disturb an entry a live Sprint is working on.
     *
     * <p>{@code sprint_items} cascades on the Backlog entry, so deleting one would take the
     * Sprint's assignment record with it — the same silent loss the WBS delete guard prevents.
     * A closed Sprint's record does not block anything: it is history that no longer changes.
     */
    private void requireNotInOpenSprint(Long projectId, Long itemId, String action) {
        List<SprintItem> live = sprintService.liveAssignmentsFor(projectId, List.of(itemId));
        if (!live.isEmpty()) {
            throw new InvalidBacklogItemException(
                    "진행 중인 Sprint에 배정된 항목이라 %s. Sprint에서 먼저 제거하세요.".formatted(action));
        }
    }

    private void recordLinkChange(Long projectId, Long itemId, Long from, Long to,
                                   ChangeReason reason) {
        changeLogRepository.save(ChangeLog.of(projectId, ChangeLog.BACKLOG_ITEM, itemId,
                "wbsItemId", from, to, reason));
    }

    private void requireProject(Long projectId) {
        if (!projectRepository.existsById(projectId)) {
            throw new ProjectNotFoundException(projectId);
        }
    }

    private BacklogItem requireItemOfProject(List<BacklogItem> projectItems, Long itemId) {
        return projectItems.stream()
                .filter(item -> item.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new BacklogItemNotFoundException(itemId));
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
