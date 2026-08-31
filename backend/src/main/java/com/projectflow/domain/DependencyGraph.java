package com.projectflow.domain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The predecessor → successor graph of a project's dependencies, used for cycle detection
 * (요구사항 6.3).
 *
 * <p>Cycles are rejected at write time rather than tolerated at read time: a cyclic graph has no
 * valid schedule, so there is nothing sensible for the Gantt view or the recalculation pass to do
 * with one.
 */
public final class DependencyGraph {

    private final Map<Long, List<Long>> successorsByPredecessor;

    private DependencyGraph(Map<Long, List<Long>> successorsByPredecessor) {
        this.successorsByPredecessor = successorsByPredecessor;
    }

    public static DependencyGraph of(List<WbsDependency> dependencies) {
        Map<Long, List<Long>> edges = new HashMap<>();
        for (WbsDependency dependency : dependencies) {
            edges.computeIfAbsent(dependency.getPredecessorId(), key -> new ArrayList<>())
                    .add(dependency.getSuccessorId());
        }
        return new DependencyGraph(edges);
    }

    /**
     * True when {@code target} is reachable from {@code start} by following successor edges.
     * Adding an edge {@code target → start} would therefore close a cycle.
     */
    public boolean reaches(Long start, Long target) {
        Set<Long> visited = new HashSet<>();
        return reachesFrom(start, target, visited);
    }

    private boolean reachesFrom(Long current, Long target, Set<Long> visited) {
        if (current.equals(target)) {
            return true;
        }
        if (!visited.add(current)) {
            return false;
        }
        for (Long next : successorsByPredecessor.getOrDefault(current, List.of())) {
            if (reachesFrom(next, target, visited)) {
                return true;
            }
        }
        return false;
    }
}
