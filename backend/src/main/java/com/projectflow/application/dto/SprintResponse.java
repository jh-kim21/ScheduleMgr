package com.projectflow.application.dto;

import com.projectflow.domain.BacklogItemType;
import com.projectflow.domain.BacklogPriority;
import com.projectflow.domain.BacklogStatus;
import com.projectflow.domain.SprintItemOutcome;
import com.projectflow.domain.SprintStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Every Sprint of a project with its assignments, plus the numbers derived from them.
 *
 * <p>All mutations return the whole list rather than one Sprint. Closing with carry-over touches
 * two Sprints at once, starting one is only legal because no other is active, and the Board reads
 * the same payload — a partial response would leave the client holding a contradiction.
 *
 * @param activeSprintId the Sprint the Board shows, or {@code null} when none is running
 */
public record SprintResponse(
        Long activeSprintId,
        List<SprintDetail> sprints
) {
    /**
     * @param plannedItems  배정된 항목 수, {@code plannedPoints} 그 합계
     * @param doneItems     완료 항목 수, {@code donePoints} 그 합계 — Step 7의 속도 추세 입력
     * @param blockedItems  차단된 항목 수 (열린 Sprint에서만 의미가 있다)
     * @param carriedOverItems 이월된 항목 수 (종료된 Sprint에서만 채워진다)
     * @param canStart      지금 시작할 수 있는가 (계획 상태이고 다른 활성 Sprint가 없음)
     * @param canDelete     지금 삭제할 수 있는가 (계획 상태이고 활성 배정이 없음)
     */
    public record SprintDetail(
            Long id,
            String name,
            String goal,
            LocalDate startDate,
            LocalDate endDate,
            SprintStatus status,
            LocalDateTime closedAt,
            int plannedItems,
            int plannedPoints,
            int doneItems,
            int donePoints,
            int blockedItems,
            int carriedOverItems,
            boolean canStart,
            boolean canDelete,
            List<SprintItemDetail> items
    ) {
    }

    /**
     * One assignment as the Board and the Sprint list show it.
     *
     * @param outcome        Sprint가 종료될 때 찍힌 결과. 열려 있는 동안은 {@code null}
     * @param removed        Sprint가 끝나기 전에 빠졌다
     * @param openChildCount 완료되지 않은 하위 항목 수. 완료를 막지는 않지만 (Task 완료만으로
     *                       Story를 자동 완료하지 않는 것과 별개로) 눌러야 하는 사람이 알아야 한다
     * @param reopened       한때 완료로 찍혔던 Sprint 결과가 있는데 지금은 완료가 아니다
     */
    public record SprintItemDetail(
            Long assignmentId,
            Long backlogItemId,
            BacklogItemType itemType,
            String title,
            BacklogPriority priority,
            BacklogStatus status,
            boolean blocked,
            String blockedReason,
            Long assigneeMemberId,
            String assigneeName,
            String acceptanceCriteria,
            Integer storyPoint,
            Long wbsItemId,
            String wbsCode,
            String wbsName,
            Integer pointsAtStart,
            Integer pointsAtClose,
            SprintItemOutcome outcome,
            boolean removed,
            int openChildCount,
            boolean reopened,
            LocalDateTime doneAt
    ) {
    }
}
