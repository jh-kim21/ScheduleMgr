package com.projectflow.application.dto;

import com.projectflow.domain.BacklogItemType;
import com.projectflow.domain.BacklogPriority;
import com.projectflow.domain.BacklogStatus;
import com.projectflow.domain.ExecutionMode;

import java.time.LocalDateTime;
import java.util.List;

/**
 * The whole Product Backlog in one payload.
 *
 * <p>Every mutation returns all of it rather than the changed entry. Unlike the RAID log, the
 * reason here <em>is</em> cross-row: re-attaching an Epic moves every entry beneath it, and the
 * per-row warnings depend on the Work Package each one points at. A partial response would leave
 * the client showing stale links for rows it never touched.
 *
 * @param unlinkedCount entries with no Work Package, counted over the whole backlog rather than the
 *                      current filter — "미연결 3건" is a fact about the project, and a filtered
 *                      count would read as a smaller problem than it is (the RAID banner rule)
 * @param items         ordered by priority, then type, then id
 */
public record BacklogResponse(
        int unlinkedCount,
        List<BacklogItemResponse> items
) {
    /**
     * @param wbsCode           WBS code of the owning Work Package, resolved server-side because the
     *                          code is derived from tree position
     * @param wbsExecutionMode  execution mode of that Work Package, so the screen can explain the
     *                          {@code requiresExecutionModeChange} warning without another request
     * @param depth             0 for a top-level entry, 1 under an Epic, 2 for a Task — display
     *                          indentation only, since the backlog is an ordered list and nothing
     *                          is derived from position the way a WBS code is
     * @param assigneeName      resolved from {@code assigneeMemberId}
     * @param aggregated        whether progress will be counted on this entry (Story·Bug only)
     * @param childCount        direct children, so the screen can refuse deletion in advance
     * @param unlinked          no owning Work Package — allowed as a draft, but shown
     * @param linkedToSummary   the owner has since become a Summary, so the link is unusable
     * @param danglingLink      the owner id no longer resolves
     * @param requiresExecutionModeChange the owner is Waterfall or 미지정
     * @param blocked           차단됨. 상태와 별개이며 완료를 막는다
     * @param doneAt            완료로 인정된 시각. 재오픈하면 비워진다 — 상태만으로는 지금 완료인지
     *                          한때 완료였는지 구분할 수 없다
     * @param openSprintName    이 항목이 지금 들어 있는 열린 Sprint의 이름, 없으면 {@code null}.
     *                          삭제·보관이 왜 거부되는지 화면에서 미리 설명할 수 있게 한다
     * @param readyForSprint    Step 4 may put this entry into a Sprint
     */
    public record BacklogItemResponse(
            Long id,
            Long wbsItemId,
            String wbsCode,
            String wbsName,
            ExecutionMode wbsExecutionMode,
            Long parentId,
            String parentTitle,
            int depth,
            BacklogItemType itemType,
            String title,
            String description,
            BacklogPriority priority,
            BacklogStatus status,
            Long assigneeMemberId,
            String assigneeName,
            String acceptanceCriteria,
            Integer storyPoint,
            Integer progressWeight,
            LocalDateTime archivedAt,
            boolean archived,
            boolean blocked,
            String blockedReason,
            LocalDateTime doneAt,
            String openSprintName,
            boolean aggregated,
            int childCount,
            boolean unlinked,
            boolean linkedToSummary,
            boolean danglingLink,
            boolean requiresExecutionModeChange,
            boolean readyForSprint
    ) {
    }
}
