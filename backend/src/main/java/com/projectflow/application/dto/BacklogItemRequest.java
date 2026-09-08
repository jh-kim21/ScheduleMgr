package com.projectflow.application.dto;

import com.projectflow.domain.BacklogItemType;
import com.projectflow.domain.BacklogPriority;
import com.projectflow.domain.BacklogStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * @param wbsItemId  owning Work Package, or {@code null} for an unlinked draft. For an entry with a
 *                   parent this must match the parent's — the service rejects a different value
 *                   rather than silently overriding it, so a client cannot think it moved a Task
 *                   away from its Story
 * @param parentId   Epic for a Story/Bug, or the Story/Bug for a Task
 * @param storyPoint team estimate; never converted into {@code progressWeight}
 * @param progressWeight share of progress inside the owning Work Package (used from Step 5)
 */
public record BacklogItemRequest(
        Long wbsItemId,
        Long parentId,
        @NotNull BacklogItemType itemType,
        @NotBlank String title,
        String description,
        @NotNull BacklogPriority priority,
        @NotNull BacklogStatus status,
        Long assigneeMemberId,
        String acceptanceCriteria,
        @Min(0) Integer storyPoint,
        @Min(0) Integer progressWeight,
        /**
         * 완료 상태로 바꿀 때만 의미가 있다. 시스템에 Definition of Done이 저장되어 있지 않으므로
         * "확인했다"는 사실 자체를 호출자가 밝혀야 한다 (Step 4 지시서 7항).
         */
        Boolean acceptanceConfirmed
) {
    public boolean confirmed() {
        return Boolean.TRUE.equals(acceptanceConfirmed);
    }
}
