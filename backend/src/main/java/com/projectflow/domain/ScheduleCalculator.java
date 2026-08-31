package com.projectflow.domain;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Finish-to-start schedule arithmetic over a project's WBS tree and dependencies.
 *
 * <p>The rule is: a successor may start no earlier than {@code lagDays} days after its
 * predecessor's end date — so with {@code lagDays = 0} it starts the day after. Dates are treated
 * as inclusive, and calendar days are used throughout (no working-day calendar yet).
 *
 * <p>Tasks with no dates take part in nothing: they cannot be flagged and cannot be shifted.
 */
public final class ScheduleCalculator {

    private ScheduleCalculator() {
    }

    /**
     * @param earliestStarts   earliest allowed start per task, for tasks that have a dated predecessor
     * @param violatedTaskIds  tasks whose current start is earlier than that
     */
    public record ScheduleAnalysis(Map<Long, LocalDate> earliestStarts, Set<Long> violatedTaskIds) {
    }

    /** Checks every dependency against the tree's derived dates (요구사항 6.3). */
    public static ScheduleAnalysis analyze(List<WbsNode> tree, List<WbsDependency> dependencies) {
        Map<Long, WbsNode> nodesById = indexNodes(tree);
        Map<Long, LocalDate> earliestStarts = new HashMap<>();

        for (WbsDependency dependency : dependencies) {
            WbsNode predecessor = nodesById.get(dependency.getPredecessorId());
            if (predecessor == null || predecessor.endDate() == null) {
                continue;
            }
            LocalDate candidate = predecessor.endDate().plusDays(dependency.getLagDays() + 1L);
            earliestStarts.merge(dependency.getSuccessorId(), candidate,
                    (existing, next) -> next.isAfter(existing) ? next : existing);
        }

        Set<Long> violated = new LinkedHashSet<>();
        earliestStarts.forEach((taskId, earliest) -> {
            WbsNode successor = nodesById.get(taskId);
            if (successor != null && successor.startDate() != null && successor.startDate().isBefore(earliest)) {
                violated.add(taskId);
            }
        });

        return new ScheduleAnalysis(Map.copyOf(earliestStarts), Set.copyOf(violated));
    }

    /**
     * Pushes tasks later until every finish-to-start constraint holds (요구사항 6.6), mutating the
     * given items, and returns the ids of the leaves that actually moved.
     *
     * <p>A violated task is moved by shifting every leaf beneath it, which is what makes the fix
     * work for summary tasks too: their dates are derived, so the only way to move a phase is to
     * move its contents. Each pass fixes all current violations and only ever moves work later, so
     * on an acyclic graph the loop converges; the iteration cap is a guard against a cyclic graph
     * slipping past validation, not an expected exit.
     */
    public static Set<Long> relax(List<WbsItem> items, List<WbsDependency> dependencies) {
        Map<Long, WbsItem> itemsById = new HashMap<>();
        Map<Long, List<WbsItem>> childrenByParent = new HashMap<>();
        for (WbsItem item : items) {
            itemsById.put(item.getId(), item);
            if (item.getParentId() != null) {
                childrenByParent.computeIfAbsent(item.getParentId(), key -> new ArrayList<>()).add(item);
            }
        }

        Set<Long> shifted = new LinkedHashSet<>();
        int maxPasses = items.size() + dependencies.size() + 2;

        for (int pass = 0; pass < maxPasses; pass++) {
            List<WbsNode> tree = WbsTreeAssembler.assemble(items);
            ScheduleAnalysis analysis = analyze(tree, dependencies);
            if (analysis.violatedTaskIds().isEmpty()) {
                return Set.copyOf(shifted);
            }

            Map<Long, WbsNode> nodesById = indexNodes(tree);
            for (Long taskId : analysis.violatedTaskIds()) {
                WbsNode node = nodesById.get(taskId);
                LocalDate earliest = analysis.earliestStarts().get(taskId);
                if (node == null || node.startDate() == null || earliest == null) {
                    continue;
                }
                long delta = ChronoUnit.DAYS.between(node.startDate(), earliest);
                if (delta <= 0) {
                    continue;
                }
                for (WbsItem leaf : leavesUnder(taskId, itemsById, childrenByParent)) {
                    if (leaf.getStartDate() == null && leaf.getEndDate() == null) {
                        continue;
                    }
                    leaf.shiftBy(delta);
                    shifted.add(leaf.getId());
                }
            }
        }

        // Both known causes — a cycle and a same-branch pair — are rejected before we get here, so
        // reaching this point means an unsatisfiable shape we have not accounted for.
        throw new IllegalStateException(
                "일정 재계산이 수렴하지 않았습니다. 선후행 관계에 순환이나 상위·하위 관계가 있는지 확인하세요.");
    }

    /**
     * Dependencies whose two endpoints sit on the same branch — one is the other's ancestor.
     *
     * <p>No schedule satisfies such a constraint, so they are rejected rather than relaxed. A
     * summary's dates are derived from the leaves beneath it, which makes both directions
     * self-contradictory: a leaf cannot start after the summary it is part of ends, and a summary
     * cannot start after one of its own leaves ends. Worse, the only lever {@link #relax} has —
     * pushing the successor's leaves later — moves the predecessor's derived date by the same
     * amount, so the violation returns unchanged on every pass and the loop never converges.
     */
    public static List<WbsDependency> selfReferentialDependencies(List<WbsItem> items,
                                                                   List<WbsDependency> dependencies) {
        Map<Long, Long> parentById = parentIndex(items);
        return dependencies.stream()
                .filter(dependency -> onSameBranch(
                        parentById, dependency.getPredecessorId(), dependency.getSuccessorId()))
                .toList();
    }

    /** True when one of the two items is an ancestor of the other; see the note above. */
    public static boolean onSameBranch(List<WbsItem> items, Long a, Long b) {
        return onSameBranch(parentIndex(items), a, b);
    }

    private static boolean onSameBranch(Map<Long, Long> parentById, Long a, Long b) {
        return isAncestor(parentById, a, b) || isAncestor(parentById, b, a);
    }

    private static boolean isAncestor(Map<Long, Long> parentById, Long ancestor, Long descendant) {
        Set<Long> guard = new HashSet<>();
        Long current = parentById.get(descendant);
        while (current != null && guard.add(current)) {
            if (current.equals(ancestor)) {
                return true;
            }
            current = parentById.get(current);
        }
        return false;
    }

    private static Map<Long, Long> parentIndex(List<WbsItem> items) {
        Map<Long, Long> parentById = new HashMap<>();
        for (WbsItem item : items) {
            parentById.put(item.getId(), item.getParentId());
        }
        return parentById;
    }

    /** The item itself when it is a leaf, otherwise every leaf in its subtree. */
    private static List<WbsItem> leavesUnder(Long id, Map<Long, WbsItem> itemsById,
                                              Map<Long, List<WbsItem>> childrenByParent) {
        List<WbsItem> leaves = new ArrayList<>();
        collectLeaves(id, childrenByParent, itemsById, leaves, new HashSet<>());
        return leaves;
    }

    private static void collectLeaves(Long id, Map<Long, List<WbsItem>> childrenByParent,
                                       Map<Long, WbsItem> itemsById, List<WbsItem> leaves, Set<Long> guard) {
        if (!guard.add(id)) {
            return;
        }
        List<WbsItem> children = childrenByParent.getOrDefault(id, List.of());
        if (children.isEmpty()) {
            WbsItem self = itemsById.get(id);
            if (self != null) {
                leaves.add(self);
            }
            return;
        }
        for (WbsItem child : children) {
            collectLeaves(child.getId(), childrenByParent, itemsById, leaves, guard);
        }
    }

    private static Map<Long, WbsNode> indexNodes(List<WbsNode> tree) {
        Map<Long, WbsNode> byId = new HashMap<>();
        collectNodes(tree, byId);
        return byId;
    }

    private static void collectNodes(List<WbsNode> nodes, Map<Long, WbsNode> byId) {
        for (WbsNode node : nodes) {
            byId.put(node.item().getId(), node);
            collectNodes(node.children(), byId);
        }
    }
}
