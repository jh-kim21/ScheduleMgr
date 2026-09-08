package com.projectflow.domain;

/**
 * Whether a Work Package's deliverable has been formally accepted.
 *
 * <p>Separate from progress on purpose (설계 §6.5): execution can be at 100% while acceptance is
 * still outstanding, and calling that "완료" would overstate it. {@code null} on an entry means no
 * acceptance step applies, so progress alone decides.
 */
public enum AcceptanceStatus {

    /** 인수 대기. 진척 100%라도 최종 완료가 아니다. */
    PENDING,

    /** 인수 완료. */
    ACCEPTED,
}
