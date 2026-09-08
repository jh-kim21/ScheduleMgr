package com.projectflow.domain;

/**
 * Judges how sound a Backlog entry's link to the WBS is.
 *
 * <p>Same division of labour as {@link RaciValidator} and {@link ScheduleCalculator}: what cannot
 * be satisfied at all is refused when it is written, and what is merely inconsistent is
 * <em>reported</em> here so the screen can show it and the user can decide. Nothing in this class
 * is stored — all of it is derived from the entry and the Work Package it points at, so a stored
 * copy would go stale the moment either changes.
 */
public final class BacklogAssessor {

    private BacklogAssessor() {
    }

    /**
     * @param owner the linked WBS entry, or {@code null} when the entry is unlinked or the link
     *              dangles
     */
    public static BacklogAssessment assess(BacklogItem item, WbsItem owner) {
        boolean unlinked = item.getWbsItemId() == null;

        // A Work Package can be converted to a summary after Backlog items were attached to it
        // (Step 2 allows that conversion). The link then points at something that is no longer a
        // management unit, which Step 5 could not aggregate — so it has to be visible.
        boolean linkedToSummary = owner != null && !owner.workPackage();

        // 설계 §5: Agile 실행은 AGILE·HYBRID의 이야기다. Waterfall이나 미지정 Work Package에
        // Backlog를 붙이는 것 자체는 막지 않고(초안 단계에서 정상적인 순서다), 실행 방식을 바꿔야
        // 한다고 알린다 — Step 3 지시서 8항.
        boolean requiresExecutionModeChange = owner != null
                && owner.workPackage()
                && owner.getExecutionMode() != ExecutionMode.AGILE
                && owner.getExecutionMode() != ExecutionMode.HYBRID;

        boolean danglingLink = !unlinked && owner == null;

        return new BacklogAssessment(
                unlinked,
                linkedToSummary,
                danglingLink,
                requiresExecutionModeChange,
                readyForSprint(item, unlinked, linkedToSummary, danglingLink)
        );
    }

    /**
     * Whether Step 4 may put this entry into a Sprint.
     *
     * <p>Three things disqualify it: it is not an aggregated unit (an Epic groups, a Task is
     * detail — 설계 §4.1 puts only completable Story/Bug into a Sprint), it is archived, or its
     * link is unusable. A Waterfall or 미지정 Work Package is deliberately <em>not</em> a
     * disqualifier: that is a warning to act on, not a broken state, and Step 4's instruction names
     * only 미연결 and Epic as things to block.
     */
    private static boolean readyForSprint(BacklogItem item, boolean unlinked,
                                            boolean linkedToSummary, boolean danglingLink) {
        return item.aggregated()
                && !item.archived()
                && !unlinked
                && !linkedToSummary
                && !danglingLink;
    }

    /**
     * @param unlinked                    귀속 Work Package가 없음 (초안으로 허용되지만 표시해야 한다)
     * @param linkedToSummary             귀속 대상이 Summary로 전환되어 관리 단위가 아님
     * @param danglingLink                귀속 id가 있으나 그 항목을 찾을 수 없음
     * @param requiresExecutionModeChange 귀속 Work Package의 실행 방식이 Agile·Hybrid가 아님
     * @param readyForSprint              Step 4가 Sprint에 배정할 수 있는 상태
     */
    public record BacklogAssessment(
            boolean unlinked,
            boolean linkedToSummary,
            boolean danglingLink,
            boolean requiresExecutionModeChange,
            boolean readyForSprint
    ) {
    }
}
