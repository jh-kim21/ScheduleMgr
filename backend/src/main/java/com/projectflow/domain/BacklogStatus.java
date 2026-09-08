package com.projectflow.domain;

/**
 * Execution state of a Backlog entry.
 *
 * <p>These are already the Board columns Step 4 will render (To Do → In Progress → Review → Done),
 * chosen now rather than inventing a smaller set to be remapped later — Step 4's instruction
 * expects to preserve the meaning of whatever states exist.
 *
 * <p>보관(archive) is <em>not</em> here: it is orthogonal, kept as {@code archived_at} so an
 * archived entry retains the state it was in and the Board's transitions stay about work.
 */
public enum BacklogStatus {
    TODO,
    IN_PROGRESS,
    REVIEW,

    /**
     * 완료. Step 5는 이 상태만 진척에 반영한다 — Review나 In Progress에 임의의 부분 완료율을
     * 부여하지 않는다(설계 §6.2).
     */
    DONE,
}
