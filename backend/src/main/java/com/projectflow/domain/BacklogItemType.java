package com.projectflow.domain;

/**
 * The kinds of Product Backlog entry, and which of them progress is counted on.
 *
 * <p>The hierarchy is Epic → Story/Bug → Task, expressed with {@code parent_id}. Epic is a grouping
 * device inside one Work Package, not a level of the WBS (설계 §4.1: Epic과 Story 사이에 별도 계층
 * 노드를 만들지 않는다), and a Task is the execution detail of one Story or Bug.
 */
public enum BacklogItemType {

    /** 큰 범위를 묶는 분류. Sprint에 직접 넣지 않고, 진척에도 별도로 가산하지 않는다. */
    EPIC,

    /** 완료 가능한 실행 단위. 집계 대상이다. */
    STORY,

    /** 결함. Story와 같은 취급으로 집계 대상이다. */
    BUG,

    /** Story·Bug의 실행 상세. 부모의 귀속을 따르고, 진척에 별도로 가산하지 않는다. */
    TASK;

    /**
     * Whether progress will be counted on this kind (Step 5).
     *
     * <p>Only Story and Bug. Counting an Epic as well as the Stories inside it, or a Task as well as
     * its Story, would count the same work twice (설계 §6.1). Stored state does not depend on this —
     * it exists so the aggregation that arrives in Step 5 has one place to ask.
     */
    public boolean aggregated() {
        return this == STORY || this == BUG;
    }

    /** Whether this kind may sit directly under a Work Package rather than under another entry. */
    public boolean topLevel() {
        return this != TASK;
    }
}
