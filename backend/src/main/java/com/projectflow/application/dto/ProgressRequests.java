package com.projectflow.application.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Request bodies for the aggregation basis: checkpoints, baselines and report snapshots. */
public final class ProgressRequests {

    private ProgressRequests() {
    }

    /**
     * @param weight {@code null} counts as 1 — an unweighted checkpoint set is a set of equal
     *               weight, which is the common case
     */
    public record CheckpointSaveRequest(
            @NotNull Long wbsItemId,
            @NotBlank String title,
            @Min(0) Integer weight,
            String completionCriteria
    ) {
    }

    /**
     * @param approvedBy 승인자. 로그인이 없어 자유 입력이지만, 승인은 "누가"가 핵심이라 비워 둘 수 없다
     */
    public record CheckpointApprovalRequest(
            @NotNull Boolean approved,
            String approvedBy
    ) {
    }

    /** Approving a baseline is always an explicit act — nothing here has a default. */
    public record BaselineApproveRequest(
            @NotBlank String approvedBy,
            String note
    ) {
    }

    public record SnapshotSaveRequest(
            String note
    ) {
    }

    /**
     * 진척 탭 표에서 가중치·α만 바꾸는 좁은 경로 (진척 집계 설계상 알아둘 점 — Step 5 참고).
     *
     * <p>진척 탭이 그리는 {@code WorkPackageProgress}에는 {@code startDate}·{@code endDate}·
     * {@code progress}·{@code description} 같은 필드가 없다. 그 화면에서 일반
     * {@code PUT /wbs/{itemId}}를 부르면 폼이 모르는 그 필드들이 null로 덮여 사라진다(결함 수정
     * {@code da96ebe}와 같은 종류의 사고). 그래서 이 두 필드에 대해서만 전체 치환하는 별도 엔드포인트를
     * 둔다.
     *
     * @param weight     {@code null}은 "미입력"이라는 뜻 있는 값이다(0과 다르다 — 산정 전은 0%가
     *                   아니다). 이 요청은 두 필드에 대한 전체 치환이므로, null을 보내면 값이
     *                   지워진다 — "null이면 안 바꾼다"로 만들면 지울 방법이 사라진다.
     * @param agileRatio 위와 같음. 범위는 {@link WbsItemUpdateRequest}의 같은 필드와 정확히 맞춘다 —
     *                   어긋나면 같은 값이 화면마다 다르게 거부된다.
     */
    public record WorkPackageBasisRequest(
            @Min(0) Integer weight,
            @Min(0) @Max(100) Integer agileRatio
    ) {
    }
}
