package com.projectflow.domain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The two RACI rules the matrix is checked against: exactly one Accountable per task
 * (요구사항 7.3) and at least one Responsible per task (요구사항 7.4).
 *
 * <p>These are <em>reported</em>, not enforced at write time. Two Accountables is a perfectly
 * storable state that you pass through whenever you hand ownership over — refusing the write
 * would force a delete-then-add dance, and refusing it halfway through filling in a matrix is
 * worse than showing what is still wrong. This follows the same split the schedule code uses:
 * an unsatisfiable structure (a dependency cycle) is refused, while a plan that merely disagrees
 * with itself is flagged.
 *
 * <p>Only leaves are checked. A summary with no Responsible is not a gap — the work lives in its
 * children, and counting summaries too would report the same missing assignment several times,
 * once per level. Assignments may still be made on a summary; an Accountable for a whole phase
 * is a normal thing to record.
 */
public final class RaciValidator {

    private RaciValidator() {
    }

    public enum IssueType {
        /** 최종 책임자가 둘 이상인 업무 (요구사항 7.3). */
        MULTIPLE_ACCOUNTABLE,
        /** 최종 책임자가 없는 업무 (요구사항 7.3의 "정확히 한 명"의 다른 쪽). */
        MISSING_ACCOUNTABLE,
        /** 실무 담당자가 없는 업무 (요구사항 7.4). */
        MISSING_RESPONSIBLE,
    }

    /**
     * @param wbsItemId the leaf the issue is about
     * @param type      which rule is broken
     * @param memberNames members involved, for the message — filled only for
     *                    {@link IssueType#MULTIPLE_ACCOUNTABLE}, where knowing who clashes is the
     *                    whole point
     */
    public record RaciIssue(Long wbsItemId, IssueType type, List<String> memberNames) {
    }

    /**
     * Checks every leaf of the tree.
     *
     * @param members used only to name the clashing Accountables
     */
    public static List<RaciIssue> validate(List<WbsNode> tree,
                                            List<RaciAssignment> assignments,
                                            List<ProjectMember> members) {
        Map<Long, String> memberNames = new HashMap<>();
        for (ProjectMember member : members) {
            memberNames.put(member.getId(), member.getName());
        }

        Map<Long, Map<RaciRole, Set<Long>>> byTask = index(assignments);

        List<RaciIssue> issues = new ArrayList<>();
        for (WbsNode leaf : leaves(tree)) {
            Long taskId = leaf.item().getId();
            Map<RaciRole, Set<Long>> roles = byTask.getOrDefault(taskId, Map.of());

            Set<Long> accountable = roles.getOrDefault(RaciRole.ACCOUNTABLE, Set.of());
            if (accountable.size() > 1) {
                List<String> names = accountable.stream()
                        .map(id -> memberNames.getOrDefault(id, "?"))
                        .sorted()
                        .toList();
                issues.add(new RaciIssue(taskId, IssueType.MULTIPLE_ACCOUNTABLE, names));
            } else if (accountable.isEmpty()) {
                issues.add(new RaciIssue(taskId, IssueType.MISSING_ACCOUNTABLE, List.of()));
            }

            if (roles.getOrDefault(RaciRole.RESPONSIBLE, Set.of()).isEmpty()) {
                issues.add(new RaciIssue(taskId, IssueType.MISSING_RESPONSIBLE, List.of()));
            }
        }
        return List.copyOf(issues);
    }

    private static Map<Long, Map<RaciRole, Set<Long>>> index(List<RaciAssignment> assignments) {
        Map<Long, Map<RaciRole, Set<Long>>> byTask = new HashMap<>();
        for (RaciAssignment assignment : assignments) {
            byTask.computeIfAbsent(assignment.getWbsItemId(), key -> new HashMap<>())
                    .computeIfAbsent(assignment.getRole(), key -> new LinkedHashSet<>())
                    .add(assignment.getMemberId());
        }
        return byTask;
    }

    private static List<WbsNode> leaves(List<WbsNode> nodes) {
        List<WbsNode> found = new ArrayList<>();
        collectLeaves(nodes, found);
        return found;
    }

    private static void collectLeaves(List<WbsNode> nodes, List<WbsNode> found) {
        for (WbsNode node : nodes) {
            if (node.children().isEmpty()) {
                found.add(node);
            } else {
                collectLeaves(node.children(), found);
            }
        }
    }
}
