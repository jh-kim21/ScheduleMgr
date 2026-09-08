package com.projectflow.domain;

import java.time.LocalDate;
import java.util.List;

/**
 * An assembled WBS tree node: the stored {@link WbsItem} plus the values derived from
 * its position in the tree.
 *
 * @param item                 the stored entry
 * @param code                 WBS code derived from tree position (e.g. {@code 1.2.1})
 * @param level                depth, 1 for roots
 * @param startDate            own date for leaves, earliest child start for summary nodes
 * @param endDate              own date for leaves, latest child end for summary nodes
 * @param progress             own progress for leaves, aggregated child progress for summary nodes
 * @param executionModeSummary how the Work Packages below are executed; {@code null} when there
 *                             are no children, in which case the entry's own mode is what matters
 * @param children             child nodes in sibling order
 */
public record WbsNode(
        WbsItem item,
        String code,
        int level,
        LocalDate startDate,
        LocalDate endDate,
        int progress,
        ExecutionModeSummary executionModeSummary,
        List<WbsNode> children
) {
    /**
     * A summary node rolls its schedule up from children; a leaf owns its own schedule.
     *
     * <p>Deliberately still keyed on child presence rather than on {@link WbsItem#getNodeType()}:
     * this answers "where do the dates come from?", and an entry converted to {@code SUMMARY}
     * before its children exist must keep showing its own dates instead of blanking them out.
     * Use {@code nodeType} for "is this a Work Package?" questions.
     */
    public boolean summary() {
        return !children.isEmpty();
    }
}
