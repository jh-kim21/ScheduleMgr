package com.projectflow.domain;

import java.time.LocalDate;
import java.util.List;

/**
 * An assembled WBS tree node: the stored {@link WbsItem} plus the values derived from
 * its position in the tree.
 *
 * @param item      the stored entry
 * @param code      WBS code derived from tree position (e.g. {@code 1.2.1})
 * @param level     depth, 1 for roots
 * @param startDate own date for leaves, earliest child start for summary nodes
 * @param endDate   own date for leaves, latest child end for summary nodes
 * @param progress  own progress for leaves, aggregated child progress for summary nodes
 * @param children  child nodes in sibling order
 */
public record WbsNode(
        WbsItem item,
        String code,
        int level,
        LocalDate startDate,
        LocalDate endDate,
        int progress,
        List<WbsNode> children
) {
    /** A summary node rolls its schedule up from children; a leaf owns its own schedule. */
    public boolean summary() {
        return !children.isEmpty();
    }
}
