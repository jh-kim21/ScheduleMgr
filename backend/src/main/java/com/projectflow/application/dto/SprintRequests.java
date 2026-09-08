package com.projectflow.application.dto;

import com.projectflow.domain.BacklogStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * The Sprint and Board request bodies, kept together because each is two or three fields and they
 * are only ever read next to one another.
 */
public final class SprintRequests {

    private SprintRequests() {
    }

    /** 기간은 둘 다 필수다 — Sprint는 "언제까지"가 없으면 Sprint가 아니다. */
    public record SprintSaveRequest(
            @NotBlank String name,
            String goal,
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate
    ) {
    }

    public record SprintAssignRequest(
            @NotNull Long backlogItemId
    ) {
    }

    /**
     * @param carryOverToSprintId 미완료 항목을 곧바로 재배정할 Sprint. {@code null}이면 이월 대상만
     *                            표시하고 배정은 나중에 한다 — 종료와 재배정을 한 번에 하는 것이
     *                            흔한 흐름이지만, 다음 Sprint를 아직 만들지 않은 경우도 정상이다
     */
    public record SprintCloseRequest(
            Long carryOverToSprintId
    ) {
    }

    /**
     * One board move: a column change, a block/unblock, or both.
     *
     * @param acceptanceConfirmed 완료로 옮길 때만 의미가 있다. 시스템에는 Definition of Done이
     *                            저장되어 있지 않으므로, "확인했다"는 사실 자체를 호출자가 밝혀야
     *                            한다 (Step 4 지시서 7항의 최소 완료 절차)
     */
    public record BoardMoveRequest(
            @NotNull BacklogStatus status,
            Boolean blocked,
            String blockedReason,
            Boolean acceptanceConfirmed
    ) {
        public boolean confirmed() {
            return Boolean.TRUE.equals(acceptanceConfirmed);
        }
    }
}
