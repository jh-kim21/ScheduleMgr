package com.projectflow.domain;

/**
 * Sprint lifecycle. Moves forward only: a closed Sprint's results are history, and re-opening one
 * would make "이 Sprint에서 무엇이 완료됐나"에 두 개의 답이 생긴다.
 */
public enum SprintStatus {

    /** 계획 중. 항목을 넣고 빼는 단계이며 추정값도 아직 바뀐다. */
    PLANNED,

    /** 실행 중. 프로젝트에 하나만 있을 수 있다 — 단일 팀 전제. */
    ACTIVE,

    /** 종료됨. 배정마다 결과(outcome)가 찍혀 있고 더 바뀌지 않는다. */
    CLOSED,
}
