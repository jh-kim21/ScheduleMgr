package com.projectflow.domain;

import java.util.List;
import java.util.Map;

/**
 * Per-Sprint numbers, derived on read.
 *
 * <p>Nothing here is stored: the counts come from the assignments and the entries they point at, so
 * a stored copy would be stale the moment a card moved. Same division as {@link RaidAssessor}.
 *
 * <p><b>완료 실적은 한 Sprint에만 쌓인다.</b> A closed Sprint reports what it settled as
 * {@link SprintItemOutcome#DONE}; an item carried over and finished later belongs to the later
 * Sprint. An open Sprint reports what is Done <em>right now</em>, which can still change.
 */
public final class SprintAssessor {

    private SprintAssessor() {
    }

    public static SprintProgress assess(Sprint sprint, List<SprintItem> assignments,
                                          Map<Long, BacklogItem> itemsById) {
        int planned = 0;
        int plannedPoints = 0;
        int done = 0;
        int donePoints = 0;
        int blocked = 0;
        int carriedOver = 0;

        for (SprintItem assignment : assignments) {
            if (sprint.getStatus() == SprintStatus.CLOSED) {
                // 종료된 Sprint는 자기가 찍어 둔 결과만 말한다. 그 뒤에 항목이 재오픈되거나
                // 다른 Sprint에서 완료되어도 이 숫자는 변하지 않아야 한다.
                if (assignment.getOutcome() == SprintItemOutcome.REMOVED) {
                    continue;
                }
                planned++;
                plannedPoints += points(assignment.getPointsAtStart());
                if (assignment.getOutcome() == SprintItemOutcome.DONE) {
                    done++;
                    donePoints += points(assignment.getPointsAtClose());
                } else if (assignment.getOutcome() == SprintItemOutcome.CARRIED_OVER) {
                    carriedOver++;
                }
                continue;
            }

            if (!assignment.active()) {
                continue;
            }
            BacklogItem item = itemsById.get(assignment.getBacklogItemId());
            if (item == null) {
                continue;
            }
            planned++;
            plannedPoints += points(item.getStoryPoint());
            if (item.getStatus() == BacklogStatus.DONE) {
                done++;
                donePoints += points(item.getStoryPoint());
            }
            if (item.blocked()) {
                blocked++;
            }
        }

        return new SprintProgress(planned, plannedPoints, done, donePoints, blocked, carriedOver);
    }

    private static int points(Integer value) {
        return value == null ? 0 : value;
    }

    /**
     * @param plannedItems  배정된 항목 수 (종료된 Sprint에서는 제거된 것을 뺀 수)
     * @param plannedPoints 그 항목들의 Story Point 합. 종료된 Sprint는 시작 시점 값을 쓴다
     * @param doneItems     완료된 항목 수
     * @param donePoints    완료된 항목의 Story Point 합 — Step 7의 팀 속도 추세 입력
     * @param blockedItems  차단된 항목 수 (열린 Sprint에서만 의미가 있다)
     * @param carriedOverItems 이월된 항목 수 (종료된 Sprint에서만 채워진다)
     */
    public record SprintProgress(
            int plannedItems,
            int plannedPoints,
            int doneItems,
            int donePoints,
            int blockedItems,
            int carriedOverItems
    ) {
    }
}
