package com.projectflow.domain;

import java.util.Optional;

/**
 * The minimum completion procedure (Step 4 지시서 7항): what must hold before an entry may be
 * marked Done.
 *
 * <p>Deliberately small. There is no Definition of Done stored anywhere, so the check cannot verify
 * the content of anything — what it can do is refuse a completion nobody looked at, and refuse one
 * that contradicts the board (a blocked card is not finished work).
 *
 * <p><b>Open Tasks do not block a Story.</b> The instruction forbids <em>auto</em>-completing a
 * Story when its Tasks finish; it does not say a Story may not be completed while a Task is open —
 * a Task that turned out to be unnecessary is a normal way for that to happen. The open count
 * travels to the board instead, so the person clicking 완료 can see it.
 */
public final class CompletionCheck {

    private CompletionCheck() {
    }

    /**
     * Why {@code item} may not be marked Done, or empty when it may.
     *
     * @param acceptanceConfirmed the caller states the acceptance criteria and Definition of Done
     *                            were checked. This is the "확인" in the procedure — nothing else
     *                            in the system can attest to it
     */
    public static Optional<String> blocker(BacklogItem item, boolean acceptanceConfirmed) {
        if (item.blocked()) {
            return Optional.of("차단된 항목은 완료할 수 없습니다. 차단을 먼저 해제하세요.");
        }
        if (!acceptanceConfirmed) {
            String criteria = item.getAcceptanceCriteria();
            return Optional.of(criteria == null || criteria.isBlank()
                    ? "완료 기준을 확인했다는 표시가 없습니다. 완료 처리는 확인 후에만 가능합니다."
                    : "수용 조건을 확인했다는 표시가 없습니다: " + criteria);
        }
        return Optional.empty();
    }
}
