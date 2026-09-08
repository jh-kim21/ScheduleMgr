package com.projectflow.application.dto;

/**
 * How much Backlog hangs off a WBS entry, for the count the WBS screen shows on a Work Package row
 * (설계 §4.3: {@code 8개 항목 / 4개 완료}).
 *
 * <p>Rolled up: a summary row reports everything beneath it, so collapsing a branch does not hide
 * the fact that it has execution items — the same reason delay badges appear on summary rows.
 *
 * @param items    entries that are not archived
 * @param done     of those, entries in {@code DONE}. This is a count, not progress — 진행률 집계는
 *                 가중치와 함께 Step 5에서 온다
 * @param archived entries put aside, counted separately so they are not mistaken for open work
 */
public record BacklogSummary(int items, int done, int archived) {

    public static final BacklogSummary EMPTY = new BacklogSummary(0, 0, 0);

    public BacklogSummary plus(BacklogSummary other) {
        return new BacklogSummary(items + other.items, done + other.done, archived + other.archived);
    }

    /**
     * Named {@code hasNone} rather than {@code isEmpty}: Jackson reads a bean-shaped {@code isX()}
     * on a record as an extra property, and an {@code "empty": false} field appeared in every WBS
     * node until this was renamed.
     */
    public boolean hasNone() {
        return items == 0 && done == 0 && archived == 0;
    }
}
