package com.projectflow.application;

import com.projectflow.application.dto.BacklogSummary;
import com.projectflow.application.dto.MemberRef;
import com.projectflow.application.dto.TagRef;
import com.projectflow.application.dto.WbsItemCreateRequest;
import com.projectflow.application.dto.WbsItemMoveRequest;
import com.projectflow.application.dto.WbsItemUpdateRequest;
import com.projectflow.application.dto.WbsNodeResponse;
import com.projectflow.application.dto.WbsNodeResponse.RowAnnotations;
import com.projectflow.application.dto.WbsTreeResponse;
import com.projectflow.domain.AcceptanceStatus;
import com.projectflow.domain.BacklogItem;
import com.projectflow.domain.ChangeLog;
import com.projectflow.domain.ChangeLogRepository;
import com.projectflow.domain.ChangeReason;
import com.projectflow.domain.ExecutionMode;
import com.projectflow.domain.ProgressCalculator.ProgressResult;
import com.projectflow.domain.InvalidWbsHierarchyException;
import com.projectflow.domain.InvalidWbsTagException;
import com.projectflow.domain.ProjectMember;
import com.projectflow.domain.ProjectMemberRepository;
import com.projectflow.domain.ProjectNotFoundException;
import com.projectflow.domain.ProjectRepository;
import com.projectflow.domain.RaciAssignment;
import com.projectflow.domain.RaciAssignmentRepository;
import com.projectflow.domain.RaciInheritance;
import com.projectflow.domain.RaciInheritance.EffectiveRole;
import com.projectflow.domain.RaciInheritance.RoleSource;
import com.projectflow.domain.RaciRole;
import com.projectflow.domain.RaidLinkTarget;

import com.projectflow.domain.WbsItem;
import com.projectflow.domain.WbsItemTag;
import com.projectflow.domain.WbsItemTagRepository;
import com.projectflow.domain.WbsNode;
import com.projectflow.domain.WbsItemNotFoundException;
import com.projectflow.domain.WbsItemRepository;
import com.projectflow.domain.WbsNodeType;
import com.projectflow.domain.WbsTag;
import com.projectflow.domain.WbsTagRepository;
import com.projectflow.domain.WbsTreeAssembler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class WbsService {

    private static final Comparator<WbsItem> SIBLING_ORDER =
            Comparator.comparingInt(WbsItem::getSortOrder).thenComparing(WbsItem::getId);

    private final WbsItemRepository wbsItemRepository;
    private final ProjectRepository projectRepository;
    private final ChangeLogRepository changeLogRepository;
    private final BacklogService backlogService;
    private final ProgressService progressService;

    /**
     * RACI is read straight from its repositories, not through {@code RaciService}: the tree shows
     * who is responsible, and application → application calls are the dashboard's exception alone.
     * The inheritance itself is resolved by the same domain helper the matrix uses, so the two
     * screens cannot end up naming different people.
     */
    private final RaciAssignmentRepository raciAssignmentRepository;
    private final ProjectMemberRepository memberRepository;

    private final WbsTagRepository tagRepository;
    private final WbsItemTagRepository itemTagRepository;

    /** Only to drop RAID links when a target disappears — the register itself is never
     * rebuilt from here (지시서 6-C). */
    private final RaidService raidService;

    public WbsService(WbsItemRepository wbsItemRepository,
                       ProjectRepository projectRepository,
                       ChangeLogRepository changeLogRepository,
                       BacklogService backlogService,
                       ProgressService progressService,
                       RaidService raidService,
                       RaciAssignmentRepository raciAssignmentRepository,
                       ProjectMemberRepository memberRepository,
                       WbsTagRepository tagRepository,
                       WbsItemTagRepository itemTagRepository) {
        this.wbsItemRepository = wbsItemRepository;
        this.projectRepository = projectRepository;
        this.changeLogRepository = changeLogRepository;
        this.backlogService = backlogService;
        this.progressService = progressService;
        this.raidService = raidService;
        this.raciAssignmentRepository = raciAssignmentRepository;
        this.memberRepository = memberRepository;
        this.tagRepository = tagRepository;
        this.itemTagRepository = itemTagRepository;
    }

    /**
     * The project's WBS as a tree, with codes, summary rollups and delay verdicts derived
     * (요구사항 5.2, 8.3).
     */
    public WbsTreeResponse getTree(Long projectId) {
        requireProject(projectId);
        return treeOf(projectId, LocalDate.now());
    }

    /**
     * Same tree, judged against a caller-supplied reference date instead of today.
     *
     * <p>For the commit history feature (§3.5): a commit fixes one "오늘" for every screen it
     * captures, so it cannot let this service pick its own via {@link LocalDate#now()}.
     */
    public WbsTreeResponse getTree(Long projectId, LocalDate referenceDate) {
        requireProject(projectId);
        return treeOf(projectId, referenceDate);
    }

    /** Assembles the tree without re-checking the project; callers have already done so. */
    private WbsTreeResponse treeOf(Long projectId) {
        // One reference date for the whole response, so every row is judged against the same today.
        return treeOf(projectId, LocalDate.now());
    }

    private WbsTreeResponse treeOf(Long projectId, LocalDate referenceDate) {
        // 설계 §4.3의 "연결 Backlog 수"를 트리에 함께 싣는다. 이 화면 하나를 위해 클라이언트가
        // Backlog까지 따로 읽고 병합하는 것보다, 이미 트리 전체를 반환하는 응답에 얹는 편이 단순하다.
        Map<Long, BacklogSummary> backlog = backlogService.summariesByWbsItem(projectId);
        List<WbsNode> roots = WbsTreeAssembler.assemble(wbsItemRepository.findByProjectId(projectId));
        // 지시서 5-C: WBS 화면도 공통 집계 결과를 쓴다 — 화면마다 다른 숫자가 나오면 안 된다.
        Map<Long, ProgressResult> computed = progressService.resultsByWbsItem(projectId, roots);
        Map<Long, RowAnnotations> annotations = annotationsFor(projectId, roots);
        List<WbsNodeResponse> nodes = roots.stream()
                .map(node -> WbsNodeResponse.from(node, referenceDate, backlog, computed, annotations))
                .toList();
        return new WbsTreeResponse(referenceDate, nodes);
    }

    @Transactional
    public WbsTreeResponse createItem(Long projectId, WbsItemCreateRequest request) {
        requireProject(projectId);
        List<WbsItem> items = wbsItemRepository.findByProjectId(projectId);

        if (request.parentId() != null) {
            requireCanHaveChildren(requireItemOfProject(items, request.parentId()));
        }

        // 새 항목은 자식이 없으므로 기본값이 Work Package다 — 마이그레이션이 기존 leaf를 백필한
        // 규칙과 같고, 이 필드를 모르는 클라이언트도 예전과 같은 결과를 얻는다.
        WbsNodeType nodeType = request.nodeType() != null ? request.nodeType() : WbsNodeType.WORK_PACKAGE;
        requireModeAllowedOn(nodeType, request.executionMode());
        // 분야는 항목이 생긴 뒤에야 붙일 수 있지만, 거부는 삽입 전에 한다 — 거부된 요청이 항목만
        // 만들어 놓고 끝나면 안 된다. 새 항목에 보관된 값은 없으므로 Summary에는 아무것도 못 붙인다.
        Set<Long> wantedTags = request.tagIds() == null
                ? null
                : requireTagsOfProject(projectId, request.tagIds());
        if (wantedTags != null && nodeType == WbsNodeType.SUMMARY) {
            requireTagsUnchangedOnSummary(wantedTags, Set.of());
        }

        int sortOrder = items.stream()
                .filter(item -> sameParent(item.getParentId(), request.parentId()))
                .mapToInt(WbsItem::getSortOrder)
                .max()
                .orElse(-1) + 1;

        WbsItem saved = wbsItemRepository.save(new WbsItem(
                projectId,
                request.parentId(),
                request.name(),
                request.description(),
                request.startDate(),
                request.endDate(),
                request.progress() != null ? request.progress() : 0,
                sortOrder,
                nodeType,
                request.executionMode()
        ));
        // 새 항목은 자식이 없어 결함 2의 롤업 문제가 없다 — weight·agileRatio·acceptanceStatus·
        // 실적/예상 일자는 update()가 아니라 이 별도 호출로만 채워지므로, 값이 하나도 없으면 굳이
        // 다시 저장하지 않는다.
        boolean hasBasis = request.weight() != null || request.agileRatio() != null
                || request.acceptanceStatus() != null;
        if (hasBasis) {
            saved.restoreProgressBasis(request.weight(), request.agileRatio(), request.acceptanceStatus());
        }
        boolean hasActuals = request.actualStartDate() != null || request.actualEndDate() != null
                || request.forecastEndDate() != null;
        if (hasActuals) {
            saved.restoreActualDates(request.actualStartDate(), request.actualEndDate(), request.forecastEndDate());
        }
        if (hasBasis || hasActuals) {
            wbsItemRepository.save(saved);
        }
        if (wantedTags != null && !wantedTags.isEmpty()) {
            syncTags(saved.getId(), wantedTags, List.of());
        }
        if (request.executionMode() != null) {
            recordChange(projectId, saved.getId(), "executionMode", null, request.executionMode(),
                    ChangeReason.REASSIGNED);
        }
        return treeOf(projectId);
    }

    /**
     * Bulk-adds a batch of rows parsed from an uploaded Excel/CSV file (레벨·업무명·시작일·종료일·
     * 진행률만) under {@code parentId} (or at the project's top level when {@code null}).
     *
     * <p>{@code rows} arrive already validated and ordered as they appeared in the file — see
     * {@link WbsImportParser}. Rebuilding the tree from a flat, level-numbered list works because
     * the parser already refused any row whose level skips more than one step deeper than the row
     * before it: that guarantee is exactly what lets a single lookahead decide each row's parent
     * (the most recently created row at {@code level - 1}) and whether it has children (the very
     * next row is one level deeper). No separate two-pass tree assembly is needed.
     *
     * <p>Rows are inserted one at a time rather than batched, because each child needs its parent's
     * generated id — {@code IDENTITY} means {@code save} assigns it immediately, so the next row can
     * read it straight away.
     */
    @Transactional
    public WbsTreeResponse importRows(Long projectId, Long parentId, List<WbsImportRow> rows) {
        requireProject(projectId);
        List<WbsItem> items = wbsItemRepository.findByProjectId(projectId);

        if (parentId != null) {
            requireCanHaveChildren(requireItemOfProject(items, parentId));
        }

        // 부모별 "다음 형제 자리". 기존 항목에서 시작해, 새로 추가한 행도 바로 이 맵에 반영해 나간다.
        Map<Long, Integer> nextSortOrder = new HashMap<>();
        for (WbsItem item : items) {
            nextSortOrder.merge(item.getParentId(), item.getSortOrder() + 1, Math::max);
        }

        // level(1..N) -> 그 레벨에서 가장 최근에 만든 행의 id. 파일이 위에서 아래로 적힌 순서 그대로
        // 처리되므로, 레벨 L 행을 만날 때 level-1 자리에 있는 값이 곧 그 행의 부모다.
        Map<Integer, Long> lastIdAtLevel = new HashMap<>();

        for (int i = 0; i < rows.size(); i++) {
            WbsImportRow row = rows.get(i);
            Long rowParentId = row.level() == 1 ? parentId : lastIdAtLevel.get(row.level() - 1);
            boolean hasChildRow = i + 1 < rows.size() && rows.get(i + 1).level() == row.level() + 1;
            WbsNodeType nodeType = hasChildRow ? WbsNodeType.SUMMARY : WbsNodeType.WORK_PACKAGE;

            int sortOrder = nextSortOrder.getOrDefault(rowParentId, 0);
            nextSortOrder.put(rowParentId, sortOrder + 1);

            WbsItem saved = wbsItemRepository.save(new WbsItem(
                    projectId, rowParentId, row.name(), null,
                    row.startDate(), row.endDate(), row.progress(), sortOrder,
                    nodeType, null));
            lastIdAtLevel.put(row.level(), saved.getId());
        }

        return treeOf(projectId);
    }

    /**
     * Updates one entry's own attributes, its kind and its execution mode.
     *
     * <p>Two structural rules are enforced here rather than in the entity, because both need the
     * rest of the tree: an entry with children cannot be a Work Package, and a summary cannot be
     * <em>given</em> an execution mode. The second is phrased as "cannot change" rather than
     * "must be null" on purpose — a summary may still carry a mode retained from before it was
     * converted, and the edit form sends that value straight back, which must not be an error.
     */
    @Transactional
    public WbsTreeResponse updateItem(Long projectId, Long itemId, WbsItemUpdateRequest request) {
        requireProject(projectId);
        List<WbsItem> items = wbsItemRepository.findByProjectId(projectId);
        WbsItem item = requireItemOfProject(items, itemId);

        // 필드를 모르는 클라이언트가 보내는 null은 "그대로 두라"는 뜻이다.
        WbsNodeType nodeType = request.nodeType() != null ? request.nodeType() : item.getNodeType();
        if (nodeType == WbsNodeType.WORK_PACKAGE && hasChildren(items, itemId)) {
            throw new InvalidWbsHierarchyException(
                    "하위 항목이 있는 항목은 Work Package로 바꿀 수 없습니다. 하위 항목을 먼저 옮기거나 삭제하세요.");
        }
        if (nodeType == WbsNodeType.SUMMARY && item.workPackage()) {
            requireNoActiveBacklogForConversion(projectId, itemId);
        }

        ExecutionMode previousMode = item.getExecutionMode();
        Integer previousWeight = item.getWeight();
        Integer previousRatio = item.getAgileRatio();
        if (nodeType == WbsNodeType.SUMMARY && !Objects.equals(request.executionMode(), previousMode)) {
            throw new InvalidWbsHierarchyException(
                    "상위(Summary) 항목에는 실행 방식을 지정할 수 없습니다. 실행 방식은 최하위 Work Package에 지정합니다.");
        }
        // Summary로 전환할 때 값을 지우지 않는다 (설계 §5). 되돌리면 그대로 살아난다.
        ExecutionMode nextMode = nodeType == WbsNodeType.SUMMARY ? previousMode : request.executionMode();

        // tagIds가 null이면 "그대로 두라"는 뜻이다 — 이 필드를 모르는 호출자가 저장해도 분야가
        // 조용히 지워지면 안 된다. 실행 방식과 같은 이유로 Summary에서는 보관값과 같을 때만 통과한다.
        if (request.tagIds() != null) {
            Set<Long> wantedTags = requireTagsOfProject(projectId, request.tagIds());
            List<WbsItemTag> currentTags = currentLinks(itemId);
            if (nodeType == WbsNodeType.SUMMARY) {
                requireTagsUnchangedOnSummary(wantedTags, tagIdsOf(currentTags));
            } else {
                syncTags(itemId, wantedTags, currentTags);
            }
        }

        // 결함 2: 하위가 있는 항목의 일정·진행률은 WbsTreeAssembler가 매번 다시 계산하는 집계값이다
        // (CLAUDE.md "파생 값은 저장하지 않습니다"). 화면이 그 세 칸을 비활성화해 두긴 하지만, 폼
        // 상태에는 여전히 집계값이 담겨 그대로 제출된다 — 그걸 저장 컬럼에 박으면 나중에 하위를
        // 모두 지웠을 때 원래 입력값 대신 그 순간의 집계값이 남는다. 그래서 서버가 최종 방어선으로
        // 하위가 있으면 요청 값을 무시하고 지금 저장된 값을 그대로 유지한다.
        boolean rolledUp = hasChildren(items, itemId);
        LocalDate nextStartDate = rolledUp ? item.getStartDate() : request.startDate();
        LocalDate nextEndDate = rolledUp ? item.getEndDate() : request.endDate();
        int nextProgress = rolledUp ? item.getProgress()
                : (request.progress() != null ? request.progress() : 0);

        item.update(
                request.name(),
                request.description(),
                nextStartDate,
                nextEndDate,
                nextProgress,
                nodeType,
                nextMode,
                request.weight(),
                request.agileRatio(),
                request.acceptanceStatus(),
                request.actualStartDate(),
                request.actualEndDate(),
                request.forecastEndDate()
        );
        wbsItemRepository.save(item);

        if (!Objects.equals(previousMode, nextMode)) {
            recordChange(projectId, itemId, "executionMode", previousMode, nextMode,
                    ChangeReason.REASSIGNED);
        }
        if (!Objects.equals(previousWeight, request.weight())) {
            recordChange(projectId, itemId, "weight", previousWeight, request.weight(),
                    ChangeReason.BASIS_CHANGED);
        }
        if (!Objects.equals(previousRatio, request.agileRatio())) {
            recordChange(projectId, itemId, "agileRatio", previousRatio, request.agileRatio(),
                    ChangeReason.BASIS_CHANGED);
        }
        return treeOf(projectId);
    }

    /**
     * Re-parents and/or reorders an entry (요구사항 5.4). Moving an item into its own subtree is
     * rejected, which is what keeps the tree acyclic.
     */
    @Transactional
    public WbsTreeResponse moveItem(Long projectId, Long itemId, WbsItemMoveRequest request) {
        requireProject(projectId);
        List<WbsItem> items = wbsItemRepository.findByProjectId(projectId);
        WbsItem item = requireItemOfProject(items, itemId);
        Long newParentId = request.parentId();

        if (newParentId != null) {
            if (newParentId.equals(itemId)) {
                throw new InvalidWbsHierarchyException("항목을 자기 자신의 하위로 이동할 수 없습니다.");
            }
            requireCanHaveChildren(requireItemOfProject(items, newParentId));
            Set<Long> descendants = WbsTreeAssembler.descendantIds(items, itemId);
            if (descendants.contains(newParentId)) {
                throw new InvalidWbsHierarchyException("항목을 자신의 하위 항목 아래로 이동할 수 없습니다.");
            }
        }

        List<WbsItem> siblings = items.stream()
                .filter(candidate -> sameParent(candidate.getParentId(), newParentId))
                .filter(candidate -> !candidate.getId().equals(itemId))
                .sorted(SIBLING_ORDER)
                .collect(Collectors.toCollection(ArrayList::new));

        int position = Math.min(request.position(), siblings.size());
        siblings.add(position, item);
        item.moveTo(newParentId, position);
        for (int i = 0; i < siblings.size(); i++) {
            siblings.get(i).changeSortOrder(i);
        }

        wbsItemRepository.saveAll(siblings);
        return treeOf(projectId);
    }

    /**
     * Deletes the entry and, by the {@code wbs_items.parent_id} cascade, everything beneath it.
     *
     * <p><b>Backlog가 붙어 있으면 거부한다</b> (설계 §11.3-6). The cascade would take every linked
     * Backlog entry's link with it — and, once Sprints exist, its execution record — which is
     * exactly the silent loss Step 3 has to prevent. The user re-attaches the entries elsewhere or
     * archives them; archiving is the explicit "이건 접어둔다" the rule asks for, so archived
     * entries do not block. Those are detached here first, with a reason recorded, rather than
     * being quietly nulled by the foreign key.
     */
    @Transactional
    public void deleteItem(Long projectId, Long itemId) {
        requireProject(projectId);
        List<WbsItem> items = wbsItemRepository.findByProjectId(projectId);
        WbsItem item = requireItemOfProject(items, itemId);

        Set<Long> affected = new LinkedHashSet<>(WbsTreeAssembler.descendantIds(items, itemId));
        affected.add(itemId);
        requireNoActiveBacklog(projectId, affected);
        backlogService.detachArchivedBefore(projectId, affected);
        // RAID 항목은 남기고 연결만 끊는다 — 위험 기록이 계획과 함께 사라지면 안 된다.
        raidService.detachTargets(projectId, RaidLinkTarget.WBS_ITEM, affected);

        wbsItemRepository.delete(item);
    }

    /**
     * A Work Package with live Backlog may not become a Summary.
     *
     * <p>A Summary aggregates its children, so a Story still attached to it would have its weight
     * counted twice once Step 5's aggregation runs — once inside the Summary's own rollup and once
     * as the Summary's children. Step 3 flagged this state (`linkedToSummary`) rather than
     * preventing it; from Step 5 the numbers depend on it, so the conversion is refused instead.
     * Archived entries do not block, exactly as with deletion.
     */
    private void requireNoActiveBacklogForConversion(Long projectId, Long itemId) {
        List<BacklogItem> linked = backlogService.activeItemsLinkedTo(projectId, Set.of(itemId));
        if (linked.isEmpty()) {
            return;
        }
        String examples = linked.stream()
                .limit(3)
                .map(BacklogItem::getTitle)
                .collect(Collectors.joining(", "));
        throw new InvalidWbsHierarchyException(
                "연결된 Backlog 항목이 %d건 있어 Summary로 바꿀 수 없습니다 (%s%s). 다른 Work Package로 옮기거나 보관하세요."
                        .formatted(linked.size(), examples, linked.size() > 3 ? " 등" : ""));
    }

    private void requireNoActiveBacklog(Long projectId, Set<Long> wbsItemIds) {
        List<BacklogItem> linked = backlogService.activeItemsLinkedTo(projectId, wbsItemIds);
        if (linked.isEmpty()) {
            return;
        }
        String examples = linked.stream()
                .limit(3)
                .map(BacklogItem::getTitle)
                .collect(Collectors.joining(", "));
        throw new InvalidWbsHierarchyException(
                "연결된 Backlog 항목이 %d건 있어 삭제할 수 없습니다 (%s%s). 다른 Work Package로 옮기거나 보관하세요."
                        .formatted(linked.size(), examples, linked.size() > 3 ? " 등" : ""));
    }

    private void requireProject(Long projectId) {
        if (!projectRepository.existsById(projectId)) {
            throw new ProjectNotFoundException(projectId);
        }
    }

    private WbsItem requireItemOfProject(List<WbsItem> projectItems, Long itemId) {
        return projectItems.stream()
                .filter(item -> item.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new WbsItemNotFoundException(itemId));
    }

    private boolean sameParent(Long left, Long right) {
        return left == null ? right == null : left.equals(right);
    }

    private boolean hasChildren(List<WbsItem> projectItems, Long itemId) {
        return projectItems.stream().anyMatch(candidate -> itemId.equals(candidate.getParentId()));
    }

    /**
     * A Work Package is the lowest management unit, so it cannot gain children implicitly. Letting
     * it would silently turn it into a summary and orphan its execution mode — and, from Step 3,
     * whatever Backlog items are attached to it. Converting it to a summary first is an explicit
     * act, and the conversion keeps the mode rather than discarding it.
     */
    private void requireCanHaveChildren(WbsItem parent) {
        if (parent.workPackage()) {
            throw new InvalidWbsHierarchyException(
                    "'%s'은(는) Work Package라 하위 항목을 둘 수 없습니다. 먼저 구분을 Summary로 바꾸세요."
                            .formatted(parent.getName()));
        }
    }

    private void requireModeAllowedOn(WbsNodeType nodeType, ExecutionMode executionMode) {
        if (nodeType == WbsNodeType.SUMMARY && executionMode != null) {
            throw new InvalidWbsHierarchyException(
                    "상위(Summary) 항목에는 실행 방식을 지정할 수 없습니다. 실행 방식은 최하위 Work Package에 지정합니다.");
        }
    }

    /**
     * 담당자와 분야, resolved once for the whole project.
     *
     * <p>Both are read a project at a time, like the WBS itself: three queries no matter how big
     * the tree is. Asking per row would be the N+1 the plain-id design exists to avoid.
     */
    private Map<Long, RowAnnotations> annotationsFor(Long projectId, List<WbsNode> roots) {
        Map<Long, String> memberNames = new HashMap<>();
        for (ProjectMember member : memberRepository.findByProjectId(projectId)) {
            memberNames.put(member.getId(), member.getName());
        }
        List<RaciAssignment> assignments = raciAssignmentRepository.findByProjectId(projectId);
        // 같은 함수, 같은 답 — RACI 화면이 쓰는 상속 규칙을 그대로 쓴다.
        Map<Long, Map<RaciRole, EffectiveRole>> effective =
                RaciInheritance.resolve(roots, assignments);

        // 마스터 순서(sortOrder, id)를 한 번 정해 두고, 행의 칩과 요약 모두 그 순서를 따른다.
        List<WbsTag> tags = tagRepository.findByProjectId(projectId).stream()
                .sorted(Comparator.comparingInt(WbsTag::getSortOrder).thenComparing(WbsTag::getId))
                .toList();
        Set<Long> itemIds = new LinkedHashSet<>();
        collectIds(roots, itemIds);
        Map<Long, List<Long>> tagIdsByItem = new HashMap<>();
        for (WbsItemTag link : itemTagRepository.findByWbsItemIdIn(itemIds)) {
            tagIdsByItem.computeIfAbsent(link.getWbsItemId(), key -> new ArrayList<>())
                    .add(link.getTagId());
        }

        Map<Long, RowAnnotations> annotations = new HashMap<>();
        for (WbsNode root : roots) {
            collectAnnotations(root, effective, memberNames, tags, tagIdsByItem, annotations);
        }
        return annotations;
    }

    /**
     * Fills {@code annotations} for this node and everything under it, and hands its parent the
     * set of 분야 in its own subtree — that union is what a summary row displays.
     *
     * @return tag ids attached anywhere in this subtree, this node included
     */
    private Set<Long> collectAnnotations(WbsNode node,
                                          Map<Long, Map<RaciRole, EffectiveRole>> effective,
                                          Map<Long, String> memberNames,
                                          List<WbsTag> tags,
                                          Map<Long, List<Long>> tagIdsByItem,
                                          Map<Long, RowAnnotations> annotations) {
        Long itemId = node.item().getId();

        Set<Long> below = new LinkedHashSet<>();
        for (WbsNode child : node.children()) {
            below.addAll(collectAnnotations(child, effective, memberNames, tags, tagIdsByItem,
                    annotations));
        }

        Set<Long> own = new LinkedHashSet<>(tagIdsByItem.getOrDefault(itemId, List.of()));
        // 자식이 없으면 요약할 것이 없다 — ExecutionModeSummary와 같은 규칙으로 null이다. 자기가
        // 보관 중인 태그는 요약에 넣지 않는다(상위 자신의 값이지 하위의 사실이 아니다).
        List<TagRef> summary = node.children().isEmpty() ? null : refsOf(tags, below);

        List<MemberRef> responsible = List.of();
        List<MemberRef> inherited = List.of();
        EffectiveRole role = effective.getOrDefault(itemId, Map.of()).get(RaciRole.RESPONSIBLE);
        if (role != null) {
            List<MemberRef> members = role.memberIds().stream()
                    .map(memberId -> new MemberRef(memberId,
                            memberNames.getOrDefault(memberId, "?")))
                    .toList();
            // 역할당 EffectiveRole은 하나다 — 둘이 동시에 차는 일은 없다.
            if (role.source() == RoleSource.OWN) {
                responsible = members;
            } else {
                inherited = members;
            }
        }

        annotations.put(itemId,
                new RowAnnotations(responsible, inherited, refsOf(tags, own), summary));

        Set<Long> including = new LinkedHashSet<>(own);
        including.addAll(below);
        return including;
    }

    private void collectIds(List<WbsNode> nodes, Set<Long> target) {
        for (WbsNode node : nodes) {
            target.add(node.item().getId());
            collectIds(node.children(), target);
        }
    }

    /** The given tags in master order; ids with no tag behind them are simply left out. */
    private List<TagRef> refsOf(List<WbsTag> tags, Set<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return tags.stream()
                .filter(tag -> ids.contains(tag.getId()))
                .map(TagRef::from)
                .toList();
    }

    /**
     * The tag ids a request asks for, checked against this project's list.
     *
     * <p>A tag from another project is refused rather than ignored: silently dropping it would
     * make the save look like it worked while the chip never appears.
     */
    private Set<Long> requireTagsOfProject(Long projectId, List<Long> requested) {
        Set<Long> known = tagRepository.findByProjectId(projectId).stream()
                .map(WbsTag::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<Long> wanted = new LinkedHashSet<>();
        for (Long tagId : requested) {
            if (tagId == null || !known.contains(tagId)) {
                throw new InvalidWbsTagException("이 프로젝트에 없는 분야입니다: id=" + tagId);
            }
            wanted.add(tagId);
        }
        return wanted;
    }

    private List<WbsItemTag> currentLinks(Long itemId) {
        return itemTagRepository.findByWbsItemIdIn(List.of(itemId));
    }

    private Set<Long> tagIdsOf(List<WbsItemTag> links) {
        return links.stream()
                .map(WbsItemTag::getTagId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * A summary's 분야 may not be changed, but it may be repeated.
     *
     * <p>Phrased as "cannot change" rather than "must be empty" for the same reason the execution
     * mode is (설계 §5): converting a Work Package keeps its tags rather than erasing them, and the
     * edit form sends those retained values straight back. Refusing them would make every save of a
     * converted entry an error.
     */
    private void requireTagsUnchangedOnSummary(Set<Long> wanted, Set<Long> current) {
        if (!wanted.equals(current)) {
            throw new InvalidWbsTagException(
                    "상위(Summary) 항목에는 분야를 지정할 수 없습니다. 분야는 최하위 Work Package에 지정합니다.");
        }
    }

    /**
     * Applies the requested set by difference only.
     *
     * <p>Deleting every link and re-inserting would give the surviving ones new rows — the same
     * reason RAID links are diffed rather than replaced.
     */
    private void syncTags(Long itemId, Set<Long> wanted, List<WbsItemTag> current) {
        List<WbsItemTag> removed = current.stream()
                .filter(link -> !wanted.contains(link.getTagId()))
                .toList();
        if (!removed.isEmpty()) {
            itemTagRepository.deleteAll(removed);
        }
        Set<Long> kept = tagIdsOf(current);
        List<WbsItemTag> added = wanted.stream()
                .filter(tagId -> !kept.contains(tagId))
                .map(tagId -> new WbsItemTag(itemId, tagId))
                .toList();
        if (!added.isEmpty()) {
            itemTagRepository.saveAll(added);
        }
    }

    /**
     * Records one field change of a WBS entry.
     *
     * <p>Weight and Hybrid ratio changes are logged as well as execution mode changes: they move
     * the denominator of the aggregation, and 설계 §6.1 asks that a progress figure which shifted
     * because the denominator shifted can be explained.
     */
    private void recordChange(Long projectId, Long itemId, String field, Object from, Object to,
                                ChangeReason reason) {
        changeLogRepository.save(
                ChangeLog.of(projectId, ChangeLog.WBS_ITEM, itemId, field, from, to, reason));
    }
}
