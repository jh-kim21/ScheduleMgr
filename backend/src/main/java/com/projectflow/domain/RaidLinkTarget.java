package com.projectflow.domain;

/**
 * What a RAID entry can be attached to (설계 §9).
 *
 * <p>Three kinds through one column rather than three nullable foreign keys: the set grows (a
 * Sprint target only became possible in Step 4) and a table with one populated column out of three
 * says nothing a type code does not.
 */
public enum RaidLinkTarget {

    /** WBS 업무. 일정·범위 쪽 영향. */
    WBS_ITEM,

    /** Sprint. 실행 주기 쪽 영향. */
    SPRINT,

    /** Backlog 항목. 개별 Story·Bug에 걸린 위험이나 차단. */
    BACKLOG_ITEM,
}
