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
 * <p>Only leaves are checked <em>for gaps</em>. A summary with no Responsible is not a gap — the
 * work lives in its children, and counting summaries too would report the same missing assignment
 * several times, once per level. Assignments may still be made on a summary; an Accountable for a
 * whole phase is a normal thing to record, and since Step 6 it is inherited by the work inside it.
 *
 * <p><b>Gaps are judged against inherited roles</b> ({@link RaciInheritance}). A Work Package under
 * a phase whose Accountable is named is not missing one. What counts as missing changed when
 * inheritance arrived, and reporting it the old way would have told users to re-enter letters the
 * matrix already had.
 *
 * <p><b>A clash is reported where it is declared</b> — on the row carrying the two Accountables,
 * leaf or summary. That is where the cleanup happens, and it keeps one mistake to one issue instead
 * of repeating it on every descendant that inherits it. Two Accountables is still never deleted
 * silently (지시서 6-B): it is listed as 정리 대상.
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
        Map<Long, Map<RaciRole, RaciInheritance.EffectiveRole>> effective =
                RaciInheritance.resolve(tree, assignments);

        List<RaciIssue> issues = new ArrayList<>();

        // Clashes first, on the row that declares them — including summaries, whose Accountable now
        // reaches every Work Package below it.
        for (WbsNode node : all(tree)) {
            Long taskId = node.item().getId();
            Set<Long> declared = byTask.getOrDefault(taskId, Map.of())
                    .getOrDefault(RaciRole.ACCOUNTABLE, Set.of());
            if (declared.size() > 1) {
                List<String> names = declared.stream()
                        .map(id -> memberNames.getOrDefault(id, "?"))
                        .sorted()
                        .toList();
                issues.add(new RaciIssue(taskId, IssueType.MULTIPLE_ACCOUNTABLE, names));
            }
        }

        // Gaps only on leaves, and only when nothing up the chain fills them either.
        for (WbsNode leaf : leaves(tree)) {
            Long taskId = leaf.item().getId();
            if (RaciInheritance.holders(effective, taskId, RaciRole.ACCOUNTABLE).isEmpty()) {
                issues.add(new RaciIssue(taskId, IssueType.MISSING_ACCOUNTABLE, List.of()));
            }
            if (RaciInheritance.holders(effective, taskId, RaciRole.RESPONSIBLE).isEmpty()) {
                issues.add(new RaciIssue(taskId, IssueType.MISSING_RESPONSIBLE, List.of()));
            }
        }
        return List.copyOf(issues);
    }

    private static List<WbsNode> all(List<WbsNode> nodes) {
        List<WbsNode> found = new ArrayList<>();
        collectAll(nodes, found);
        return found;
    }

    private static void collectAll(List<WbsNode> nodes, List<WbsNode> found) {
        for (WbsNode node : nodes) {
            found.add(node);
            collectAll(node.children(), found);
        }
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
