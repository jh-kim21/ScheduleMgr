package com.projectflow.domain;

/**
 * Why something changed. A code rather than free text: there is no screen to type a reason into,
 * but the distinction carries meaning later — a weight the user edited and a link broken by a
 * deletion look identical without it.
 */
public enum ChangeReason {

    /** 사용자가 값을 직접 지정하거나 옮겼다. */
    REASSIGNED,

    /** 상위 항목이 옮겨져 하위가 함께 따라갔다. */
    INHERITED_FROM_PARENT,

    /** 귀속되어 있던 WBS 항목이 삭제되어 분리되었다. */
    WBS_ITEM_DELETED,

    /** 집계의 분모가 되는 값(가중치·Hybrid 비중)이 바뀌었다. */
    BASIS_CHANGED,
}
