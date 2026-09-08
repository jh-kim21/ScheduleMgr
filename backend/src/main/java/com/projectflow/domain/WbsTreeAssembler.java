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
 *   <li><b>Execution mode summary</b> — how the Work Packages beneath a summary are executed,
 *       counted per mode, since a summary has no mode of its own (설계 §5).</li>
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
                    item.getStartDate(), item.getEndDate(), item.getProgress(), null, List.of());
            // A childless entry contributes its own mode upward, but only if it is a Work Package:
            // one converted to SUMMARY ahead of its children has no mode to report.
            return new Assembled(leaf, 1,
                    item.workPackage() ? ExecutionModeSummary.of(item.getExecutionMode())
                                        : ExecutionModeSummary.EMPTY);
        }

        List<WbsNode> children = new ArrayList<>(childItems.size());
        LocalDate start = null;
        LocalDate end = null;
        int weightedProgress = 0;
        int leafCount = 0;
        ExecutionModeSummary modes = ExecutionModeSummary.EMPTY;

        for (int i = 0; i < childItems.size(); i++) {
            Assembled child = assembleNode(childItems.get(i), code + "." + (i + 1), level + 1, childrenByParent);
            WbsNode childNode = child.node();
            children.add(childNode);
            start = earliest(start, childNode.startDate());
            end = latest(end, childNode.endDate());
            weightedProgress += childNode.progress() * child.leafCount();
            leafCount += child.leafCount();
            modes = modes.plus(child.modes());
        }

        int progress = leafCount == 0 ? 0 : Math.round((float) weightedProgress / leafCount);
        WbsNode summary = new WbsNode(item, code, level, start, end, progress, modes, List.copyOf(children));
        // A summary's own stored mode is never counted — 설계 §5: 상위는 실행 실적을 갖지 않는다.
        return new Assembled(summary, leafCount, modes);
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

    /**
     * Carries the leaf count up the recursion so summary progress can be leaf-weighted, and the
     * execution modes of the Work Packages below so each summary can report them.
     */
    private record Assembled(WbsNode node, int leafCount, ExecutionModeSummary modes) {
    }
}
