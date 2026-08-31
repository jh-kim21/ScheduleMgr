package com.projectflow.domain;

/** Schedule health of a task as of a reference date (요구사항 8.3). */
public enum DelayStatus {

    /** 일정이 입력되지 않아 판정할 수 없음. */
    UNSCHEDULED,

    /** 시작일이 아직 오지 않음. */
    NOT_STARTED,

    /** 진행률이 경과 일수 대비 기대치를 충족. */
    ON_TRACK,

    /** 기간 안에 있지만 진행률이 기대치보다 낮음. */
    AT_RISK,

    /** 종료일이 지났는데 완료되지 않음. */
    DELAYED,

    /** 진행률 100%. */
    COMPLETED
}
