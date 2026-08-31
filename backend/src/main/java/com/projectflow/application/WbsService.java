package com.projectflow.application;

import com.projectflow.application.dto.WbsItemCreateRequest;
import com.projectflow.application.dto.WbsItemMoveRequest;
import com.projectflow.application.dto.WbsItemUpdateRequest;
import com.projectflow.application.dto.WbsNodeResponse;
import com.projectflow.application.dto.WbsTreeResponse;
import com.projectflow.domain.InvalidWbsHierarchyException;
import com.projectflow.domain.ProjectNotFoundException;
import com.projectflow.domain.ProjectRepository;
import com.projectflow.domain.WbsItem;
import com.projectflow.domain.WbsItemNotFoundException;
import com.projectflow.domain.WbsItemRepository;
import com.projectflow.domain.WbsTreeAssembler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class WbsService {

    private static final Comparator<WbsItem> SIBLING_ORDER =
            Comparator.comparingInt(WbsItem::getSortOrder).thenComparing(WbsItem::getId);

    private final WbsItemRepository wbsItemRepository;
    private final ProjectRepository projectRepository;

    public WbsService(WbsItemRepository wbsItemRepository, ProjectRepository projectRepository) {
        this.wbsItemRepository = wbsItemRepository;
        this.projectRepository = projectRepository;
    }

    /**
     * The project's WBS as a tree, with codes, summary rollups and delay verdicts derived
     * (요구사항 5.2, 8.3).
     */
    public WbsTreeResponse getTree(Long projectId) {
        requireProject(projectId);
        return treeOf(projectId);
    }

    /** Assembles the tree without re-checking the project; callers have already done so. */
    private WbsTreeResponse treeOf(Long projectId) {
        // One reference date for the whole response, so every row is judged against the same today.
        LocalDate referenceDate = LocalDate.now();
        List<WbsNodeResponse> nodes =
                WbsTreeAssembler.assemble(wbsItemRepository.findByProjectId(projectId)).stream()
                        .map(node -> WbsNodeResponse.from(node, referenceDate))
                        .toList();
        return new WbsTreeResponse(referenceDate, nodes);
    }

    @Transactional
    public WbsTreeResponse createItem(Long projectId, WbsItemCreateRequest request) {
        requireProject(projectId);
        List<WbsItem> items = wbsItemRepository.findByProjectId(projectId);

        if (request.parentId() != null) {
            requireItemOfProject(items, request.parentId());
        }

        int sortOrder = items.stream()
                .filter(item -> sameParent(item.getParentId(), request.parentId()))
                .mapToInt(WbsItem::getSortOrder)
                .max()
                .orElse(-1) + 1;

        wbsItemRepository.save(new WbsItem(
                projectId,
                request.parentId(),
                request.name(),
                request.description(),
                request.startDate(),
                request.endDate(),
                request.progress() != null ? request.progress() : 0,
                sortOrder
        ));
        return treeOf(projectId);
    }

    @Transactional
    public WbsTreeResponse updateItem(Long projectId, Long itemId, WbsItemUpdateRequest request) {
        requireProject(projectId);
        WbsItem item = requireItemOfProject(wbsItemRepository.findByProjectId(projectId), itemId);
        item.update(
                request.name(),
                request.description(),
                request.startDate(),
                request.endDate(),
                request.progress() != null ? request.progress() : 0
        );
        wbsItemRepository.save(item);
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
            requireItemOfProject(items, newParentId);
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

    /** Deletes the entry and, by the {@code wbs_items.parent_id} cascade, everything beneath it. */
    @Transactional
    public void deleteItem(Long projectId, Long itemId) {
        requireProject(projectId);
        WbsItem item = requireItemOfProject(wbsItemRepository.findByProjectId(projectId), itemId);
        wbsItemRepository.delete(item);
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
}
