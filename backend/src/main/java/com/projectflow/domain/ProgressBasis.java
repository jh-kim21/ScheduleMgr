package com.projectflow.domain;

/**
 * How a row's progress was arrived at. Travels with the number so a screen can say *why* it reads
 * what it reads — the same reason the delay fields carry their inputs.
 */
public enum ProgressBasis {

    /** 실행 방식 미지정. 사람이 입력한 진행률을 그대로 쓴다 — 기존 동작의 보존 경로. */
    MANUAL,

    /** 완료된 Story·Bug의 가중치 비율 (설계 §6.2). */
    AGILE,

    /** 승인된 체크포인트의 가중치 비율 (설계 §6.3). */
    WATERFALL,

    /** α × Agile + (1−α) × Waterfall (설계 §6.3). */
    HYBRID,

    /** 직계 자식의 가중치 가중 평균 (설계 §6.4). */
    ROLLUP,

    /**
     * 가중치가 하나도 입력되지 않은 가지의 상위. 기존과 같은 하위 leaf 개수 가중 평균을 쓴다 —
     * 가중치를 넣기 전까지 화면의 숫자가 달라지지 않게 하는 전환 정책이다.
     */
    LEGACY_ROLLUP,

    /** 산정 전. 분모가 없거나 필요한 기준이 갖춰지지 않았다. 0%와 구분해야 한다. */
    NOT_ESTIMABLE,
}
