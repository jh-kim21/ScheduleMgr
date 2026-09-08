package com.projectflow.domain;

/**
 * What became of one assignment when its Sprint closed. {@code null} while the Sprint is open.
 *
 * <p>This is the Sprint's record, not the item's current state. An item carried over from Sprint 1
 * and finished in Sprint 2 reads {@code CARRIED_OVER} in the first and {@code DONE} in the second —
 * which is exactly what keeps completed work from being counted twice.
 */
public enum SprintItemOutcome {

    /** Sprint 종료 시점에 완료되어 있었다. 완료 실적은 이 Sprint의 것이다. */
    DONE,

    /** 종료 시점에 미완료였다. 다음 Sprint로 재배정할 대상이다. */
    CARRIED_OVER,

    /** Sprint가 끝나기 전에 범위에서 빠졌다. 완료도 이월도 아니다. */
    REMOVED,
}
