package com.projectflow.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns a project's flat list of {@link WbsItem}s into a tree, deriving the values that
 * are not stored:
 *
 * <ul>
 *   <li><b>WBS code</b> (요구사항 5.5) — from tree position: {@code 1}, {@code 1.1}, {@code 1.2}, {@code 2} …</li>
 *   <li><b>Summary schedule</b> (요구사항 5.6) — a parent's start is the earliest start among its
 *       descendants and its end the latest end; leaves keep their own dates.</li>
 *   <li><b>Summary progress</b> — leaf-weighted: a parent's progress is the average of the leaf
 *       progress values beneath it, so a branch does not count more heavily just for being nested
 *       shallowly.</li>
 * </ul>
 *
 * Siblings are ordered by {@code sortOrder}, with id as a stable tiebreak.
 */
public final class WbsTreeAssembler {

    private static final Comparator<WbsItem> SIBLING_ORDER =
            Comparator.comparingInt(WbsItem::getSortOrder).thenComparing(WbsItem::getId);

    private WbsTreeAssembler() {
    }

    /** Assembles the root nodes of the tree formed by {@code items}. */
    public static List<WbsNode> assemble(List<WbsItem> items) {
        Map<Long, List<WbsItem>> childrenByParent = new HashMap<>();
        List<WbsItem> roots = new ArrayList<>();
        for (WbsItem item : items) {
            if (item.getParentId() == null) {
                roots.add(item);
            } else {
                childrenByParent.computeIfAbsent(item.getParentId(), key -> new ArrayList<>()).add(item);
            }
        }
        roots.sort(SIBLING_ORDER);
        childrenByParent.values().forEach(children -> children.sort(SIBLING_ORDER));

        List<WbsNode> tree = new ArrayList<>(roots.size());
        for (int i = 0; i < roots.size(); i++) {
            tree.add(assembleNode(roots.get(i), String.valueOf(i + 1), 1, childrenByParent).node());
        }
        return List.copyOf(tree);
    }

    /**
     * Ids of every item beneath {@code ancestorId}, excluding the ancestor itself. Used to reject
     * moves that would place a node inside its own subtree.
     */
    public static Set<Long> descendantIds(List<WbsItem> items, Long ancestorId) {
        Map<Long, List<WbsItem>> childrenByParent = new HashMap<>();
        for (WbsItem item : items) {
            if (item.getParentId() != null) {
                childrenByParent.computeIfAbsent(item.getParentId(), key -> new ArrayList<>()).add(item);
            }
        }
        Set<Long> descendants = new HashSet<>();
        collectDescendants(ancestorId, childrenByParent, descendants);
        return descendants;
    }

    private static void collectDescendants(Long parentId, Map<Long, List<WbsItem>> childrenByParent,
                                            Set<Long> collected) {
        for (WbsItem child : childrenByParent.getOrDefault(parentId, List.of())) {
            if (collected.add(child.getId())) {
                collectDescendants(child.getId(), childrenByParent, collected);
            }
        }
    }

    private static Assembled assembleNode(WbsItem item, String code, int level,
                                            Map<Long, List<WbsItem>> childrenByParent) {
        List<WbsItem> childItems = childrenByParent.getOrDefault(item.getId(), List.of());
        if (childItems.isEmpty()) {
            WbsNode leaf = new WbsNode(item, code, level,
                    item.getStartDate(), item.getEndDate(), item.getProgress(), List.of());
            return new Assembled(leaf, 1);
        }

        List<WbsNode> children = new ArrayList<>(childItems.size());
        LocalDate start = null;
        LocalDate end = null;
        int weightedProgress = 0;
        int leafCount = 0;

        for (int i = 0; i < childItems.size(); i++) {
            Assembled child = assembleNode(childItems.get(i), code + "." + (i + 1), level + 1, childrenByParent);
            WbsNode childNode = child.node();
            children.add(childNode);
            start = earliest(start, childNode.startDate());
            end = latest(end, childNode.endDate());
            weightedProgress += childNode.progress() * child.leafCount();
            leafCount += child.leafCount();
        }

        int progress = leafCount == 0 ? 0 : Math.round((float) weightedProgress / leafCount);
        WbsNode summary = new WbsNode(item, code, level, start, end, progress, List.copyOf(children));
        return new Assembled(summary, leafCount);
    }

    private static LocalDate earliest(LocalDate current, LocalDate candidate) {
        if (candidate == null) {
            return current;
        }
        return current == null || candidate.isBefore(current) ? candidate : current;
    }

    private static LocalDate latest(LocalDate current, LocalDate candidate) {
        if (candidate == null) {
            return current;
        }
        return current == null || candidate.isAfter(current) ? candidate : current;
    }

    /** Carries the leaf count up the recursion so summary progress can be leaf-weighted. */
    private record Assembled(WbsNode node, int leafCount) {
    }
}
