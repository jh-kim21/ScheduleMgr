package com.projectflow.domain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Who is effectively in each role on each row, once assignments made on a phase are read as
 * applying to the work inside it (설계 §8, 지시서 6-B).
 *
 * <p><b>Nothing is stored.</b> Inheritance is resolved on read, like WBS codes and summary
 * schedules: an assignment moved with its row would otherwise leave copies behind on every
 * descendant, and a re-parented Work Package would keep answering to its old phase.
 *
 * <p><b>Per role, not per row.</b> A Work Package that names its own Responsible still inherits
 * the phase's Accountable. Treating any local letter as a full override would silently drop the
 * phase's Accountable the moment somebody filled in a Responsible, which is the opposite of what
 * a matrix is for.
 *
 * <p><b>The nearest ancestor wins.</b> When a phase and a sub-phase both name an Accountable, the
 * sub-phase's is the one in force — this is 재정의, and it is reported as such rather than merged.
 * Two Accountables on the <em>same</em> row is a different thing: a real clash, left visible for
 * {@link RaciValidator} to report rather than resolved by picking one.
 */
public final class RaciInheritance {

    private RaciInheritance() {
    }

    /** Where a row's letters for one role came from. */
    public enum RoleSource {
        /** Assigned on this row. */
        OWN,
        /** Nothing on this row, so an ancestor's assignment applies. */
        INHERITED,
    }

    /**
     * One role's holders on one row.
     *
     * @param memberIds  who holds it, in assignment order
     * @param source     assigned here or inherited
     * @param sourceItemId the row the letters actually live on — this row when {@code OWN}
     * @param overrides  this row replaces an ancestor's assignment for the same role. Only ever
     *                   true for {@code OWN}: it is the signal 하위 재정의 the design asks for
     */
    public record EffectiveRole(List<Long> memberIds, RoleSource source, Long sourceItemId,
                                 boolean overrides) {
    }

    /**
     * Resolved roles for every row of the tree, keyed by WBS item id then by role.
     *
     * <p>Roles with no holder anywhere up the chain are simply absent, so a caller can ask
     * {@code get(itemId).get(ACCOUNTABLE) == null} and mean "nobody, at any level".
     */
    public static Map<Long, Map<RaciRole, EffectiveRole>> resolve(List<WbsNode> tree,
                                                                    List<RaciAssignment> assignments) {
        Map<Long, Map<RaciRole, List<Long>>> own = new HashMap<>();
        for (RaciAssignment assignment : assignments) {
            own.computeIfAbsent(assignment.getWbsItemId(), key -> new LinkedHashMap<>())
                    .computeIfAbsent(assignment.getRole(), key -> new ArrayList<>())
                    .add(assignment.getMemberId());
        }

        Map<Long, Map<RaciRole, EffectiveRole>> resolved = new HashMap<>();
        walk(tree, Map.of(), own, resolved);
        return resolved;
    }

    private static void walk(List<WbsNode> nodes,
                              Map<RaciRole, EffectiveRole> fromAncestors,
                              Map<Long, Map<RaciRole, List<Long>>> own,
                              Map<Long, Map<RaciRole, EffectiveRole>> resolved) {
        for (WbsNode node : nodes) {
            Long itemId = node.item().getId();
            Map<RaciRole, List<Long>> here = own.getOrDefault(itemId, Map.of());

            Map<RaciRole, EffectiveRole> effective = new LinkedHashMap<>(fromAncestors);
            for (Map.Entry<RaciRole, List<Long>> entry : here.entrySet()) {
                boolean overrides = fromAncestors.containsKey(entry.getKey());
                effective.put(entry.getKey(), new EffectiveRole(
                        List.copyOf(entry.getValue()), RoleSource.OWN, itemId, overrides));
            }
            resolved.put(itemId, Map.copyOf(effective));

            // Children see this row's letters as inherited, whatever they were here.
            Map<RaciRole, EffectiveRole> forChildren = new LinkedHashMap<>();
            for (Map.Entry<RaciRole, EffectiveRole> entry : effective.entrySet()) {
                EffectiveRole role = entry.getValue();
                forChildren.put(entry.getKey(), new EffectiveRole(
                        role.memberIds(), RoleSource.INHERITED, role.sourceItemId(), false));
            }
            walk(node.children(), Map.copyOf(forChildren), own, resolved);
        }
    }

    /** Everyone in one role on one row, own or inherited; empty when nobody holds it. */
    public static Set<Long> holders(Map<Long, Map<RaciRole, EffectiveRole>> resolved,
                                     Long wbsItemId, RaciRole role) {
        EffectiveRole effective = resolved.getOrDefault(wbsItemId, Map.of()).get(role);
        return effective == null ? Set.of() : new LinkedHashSet<>(effective.memberIds());
    }
}
