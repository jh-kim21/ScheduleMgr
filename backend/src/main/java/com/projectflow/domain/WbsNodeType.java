package com.projectflow.domain;

/**
 * What kind of management unit a WBS entry is.
 *
 * <p>This is stored rather than derived, unlike {@link WbsNode#summary()} which answers the
 * narrower question "does this row roll its schedule up from children?". The two agree for all
 * existing data — the migration backfilled this column from child presence — and diverge only
 * while a node has been converted to {@code SUMMARY} but has no children yet.
 *
 * <p>The distinction matters because a Work Package is the point where the WBS meets the Agile
 * execution layer: Backlog items attach to it and (from Step 5) weights are counted there. If that
 * identity were derived from child presence, adding one child would silently orphan those links.
 *
 * <p>{@code MILESTONE} from the design is deliberately absent: milestones belong to the Gantt work
 * in a later step, and an unused enum constant would already have to be handled everywhere.
 */
public enum WbsNodeType {

    /** 하위 항목을 묶는 범위. 실행 방식을 직접 갖지 않고 하위의 요약만 보여준다. */
    SUMMARY,

    /** 최하위 관리 단위. 실행 방식을 갖고, 하위 항목을 둘 수 없다. */
    WORK_PACKAGE,
}
