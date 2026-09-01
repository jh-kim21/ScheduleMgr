package com.projectflow.domain;

/**
 * One lifecycle shared by all four kinds. Per-kind wording ("확인됨" for an assumption,
 * "해소" for a risk) is a labelling matter the UI handles; splitting the stored state four ways
 * would multiply the filtering and reporting code for no gain.
 */
public enum RaidStatus {
    /** 등록만 된 상태. */
    OPEN,
    /** 대응·확인이 진행 중. */
    IN_PROGRESS,
    /** 더 볼 필요가 없어진 상태. 기한 초과 판정에서도 빠진다. */
    CLOSED,
}
