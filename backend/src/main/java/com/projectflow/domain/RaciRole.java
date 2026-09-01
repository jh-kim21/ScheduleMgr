package com.projectflow.domain;

/** The four RACI roles (요구사항 7.1). */
public enum RaciRole {
    /** 실무를 수행하는 사람. 업무마다 최소 한 명 있어야 한다 (요구사항 7.4). */
    RESPONSIBLE,
    /** 최종 책임자. 업무마다 정확히 한 명이어야 한다 (요구사항 7.3). */
    ACCOUNTABLE,
    /** 의견을 구하는 사람. */
    CONSULTED,
    /** 결과를 통보받는 사람. */
    INFORMED,
}
