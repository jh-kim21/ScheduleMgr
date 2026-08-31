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
 * Critical path over the finish-to-start network (요구사항 6 확장): the chain of tasks whose slip
 * moves the project's end date.
 *
 * <p>Three decisions shape what this produces, and they follow from how the rest of the schedule
 * code works:
 *
 * <ul>
 *   <li><b>Only tasks that take part in a dependency are considered.</b> "Critical path" is a
 *       statement about a chain; a task nobody depends on and that depends on nobody is not on one.
 *       Undated tasks are excluded for the same reason {@link ScheduleCalculator} excludes them —
 *       they cannot be measured.</li>
 *   <li><b>The plan's own dates are the forward pass.</b> Early finish is simply the planned end
 *       date, so a gap the user deliberately left counts as real float. This is what makes the
 *       answer actionable: it says "if this task slips, the project end moves", about the plan as
 *       entered rather than about a plan nobody typed.</li>
 *   <li><b>Summary tasks are single nodes with their aggregated dates</b>, matching
 *       {@link ScheduleCalculator#analyze}. A dependency may be attached to a summary, and when it
 *       is, the rolled-up schedule is what the constraint is measured against.</li>
 * </ul>
 *
 * <p>Float can come out negative, which means the plan is already infeasible there — the same
 * situation the row reports as a schedule violation. Those tasks count as critical: there is no
 * slack to give.
 */
public final class CriticalPathCalculator {

    private CriticalPathCalculator() {
    }

    /**
     * @param floatDays        total float per participating task, in calendar days; negative when
     *                         the plan already breaks the constraint
     * @param criticalTaskIds  tasks with no float to give ({@code floatDays <= 0})
     * @param criticalDependencyIds links joining two critical tasks with no slack between them
     */
    public record CriticalPathAnalysis(
            Map<Long, Long> floatDays,
            Set<Long> criticalTaskIds,
            Set<Long> criticalDependencyIds
    ) {
        public static CriticalPathAnalysis empty() {
            return new CriticalPathAnalysis(Map.of(), Set.of(), Set.of());
        }
    }

    public static CriticalPathAnalysis analyze(List<WbsNode> tree, List<WbsDependency> dependencies) {
        Map<Long, WbsNode> nodesById = indexNodes(tree);

        // Participating = referenced by a dependency and dated on both ends.
        Set<Long> participants = new LinkedHashSet<>();
        for (WbsDependency dependency : dependencies) {
            addIfDated(participants, nodesById, dependency.getPredecessorId());
            addIfDated(participants, nodesById, dependency.getSuccessorId());
        }
        if (participants.isEmpty()) {
            return CriticalPathAnalysis.empty();
        }

        // Only edges whose both ends participate can carry float backwards.
        List<WbsDependency> edges = dependencies.stream()
                .filter(dependency -> participants.contains(dependency.getPredecessorId())
                        && participants.contains(dependency.getSuccessorId()))
                .toList();

        Map<Long, List<WbsDependency>> outgoing = new HashMap<>();
        for (WbsDependency edge : edges) {
            outgoing.computeIfAbsent(edge.getPredecessorId(), key -> new ArrayList<>()).add(edge);
        }

        LocalDate projectFinish = participants.stream()
                .map(id -> nodesById.get(id).endDate())
                .max(LocalDate::compareTo)
                .orElseThrow();

        // Backward pass. The dependency graph is acyclic (cycles are refused at write time), so
        // memoised recursion terminates; the guard set only protects against a cycle that slipped
        // past validation, in which case that branch simply stops contributing.
        Map<Long, LocalDate> lateFinish = new HashMap<>();
        for (Long id : participants) {
            lateFinish(id, nodesById, outgoing, projectFinish, lateFinish, new HashSet<>());
        }

        Map<Long, Long> floatDays = new HashMap<>();
        Set<Long> critical = new LinkedHashSet<>();
        for (Long id : participants) {
            LocalDate finish = lateFinish.get(id);
            if (finish == null) {
                continue;
            }
            long slack = ChronoUnit.DAYS.between(nodesById.get(id).endDate(), finish);
            floatDays.put(id, slack);
            if (slack <= 0) {
                critical.add(id);
            }
        }

        // An edge is on the path when both ends are critical and nothing is idling between them.
        Set<Long> criticalEdges = new LinkedHashSet<>();
        for (WbsDependency edge : edges) {
            if (!critical.contains(edge.getPredecessorId()) || !critical.contains(edge.getSuccessorId())) {
                continue;
            }
            if (gapDays(nodesById, edge) <= 0) {
                criticalEdges.add(edge.getId());
            }
        }

        return new CriticalPathAnalysis(Map.copyOf(floatDays), Set.copyOf(critical), Set.copyOf(criticalEdges));
    }

    /**
     * Latest finish that still lets every successor start on its planned date: one day before the
     * earliest successor's late start, minus that link's lag. A task with no successors is bounded
     * only by the project's own end.
     */
    private static LocalDate lateFinish(Long id,
                                         Map<Long, WbsNode> nodesById,
                                         Map<Long, List<WbsDependency>> outgoing,
                                         LocalDate projectFinish,
                                         Map<Long, LocalDate> memo,
                                         Set<Long> visiting) {
        LocalDate cached = memo.get(id);
        if (cached != null) {
            return cached;
        }
        if (!visiting.add(id)) {
            return null;
        }

        LocalDate finish = projectFinish;
        for (WbsDependency edge : outgoing.getOrDefault(id, List.of())) {
            LocalDate successorFinish = lateFinish(
                    edge.getSuccessorId(), nodesById, outgoing, projectFinish, memo, visiting);
            if (successorFinish == null) {
                continue;
            }
            LocalDate successorStart = successorFinish.minusDays(durationDays(nodesById, edge.getSuccessorId()) - 1);
            LocalDate candidate = successorStart.minusDays(edge.getLagDays() + 1L);
            if (candidate.isBefore(finish)) {
                finish = candidate;
            }
        }

        visiting.remove(id);
        memo.put(id, finish);
        return finish;
    }

    /** Inclusive duration, so a task starting and ending the same day lasts one day. */
    private static long durationDays(Map<Long, WbsNode> nodesById, Long id) {
        WbsNode node = nodesById.get(id);
        return ChronoUnit.DAYS.between(node.startDate(), node.endDate()) + 1;
    }

    /** Idle days between a predecessor's earliest allowed handover and the successor's start. */
    private static long gapDays(Map<Long, WbsNode> nodesById, WbsDependency edge) {
        LocalDate earliestStart = nodesById.get(edge.getPredecessorId()).endDate()
                .plusDays(edge.getLagDays() + 1L);
        return ChronoUnit.DAYS.between(earliestStart, nodesById.get(edge.getSuccessorId()).startDate());
    }

    private static void addIfDated(Set<Long> participants, Map<Long, WbsNode> nodesById, Long id) {
        WbsNode node = nodesById.get(id);
        if (node != null && node.startDate() != null && node.endDate() != null) {
            participants.add(id);
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
