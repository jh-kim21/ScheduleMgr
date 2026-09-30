package com.projectflow.application.dto;

import com.projectflow.domain.AcceptanceStatus;
import com.projectflow.domain.ExecutionMode;
import com.projectflow.domain.WbsNodeType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * @param description    free text; capped to the column's 2000 characters so an over-long paste
 *                         comes back as a named 400 rather than a bare 500 from the database
 * @param actionItemUrl    external Action Item address. Only {@code http}/{@code https} is
 *                         accepted, because this value ends up in an {@code <a href>} and a
 *                         {@code javascript:} URL would run merely by being clicked. {@code null}
 *                         (omitted) and {@code ""} (the form cleared) both mean 미입력 and store
 *                         as {@code null}
 * @param nodeType         {@code null} leaves the entry's current kind alone, so a client that does
 *                         not know about the field cannot accidentally demote a summary
 * @param executionMode    {@code null} means 미지정. On a summary entry this may only repeat the
 *                         value already stored (the retained mode) — see {@code WbsService}
 * @param weight           share among siblings. {@code null} is "not entered", which is not 0 —
 *                         the aggregation treats the two differently
 * @param agileRatio       Hybrid's α (0–100). Required for {@code HYBRID}; without it the entry is
 *                         산정 전 rather than given an invented default
 * @param acceptanceStatus formal acceptance, kept apart from progress. {@code null} = 절차 없음
 * @param tagIds           업무 분야 to attach. <b>{@code null} means "leave them alone"; an empty
 *                         list means "remove them all".</b> Without that distinction a caller that
 *                         does not know about the field — an older client, another screen's form —
 *                         would silently wipe every tag each time it saved (커밋 {@code da96ebe})
 */
public record WbsItemUpdateRequest(
        @NotBlank String name,
        @Size(max = 2000) String description,
        @Size(max = 2000)
        @Pattern(regexp = "^$|^https?://\\S+$",
                 message = "Action Item 주소는 http:// 또는 https:// 로 시작해야 합니다")
        String actionItemUrl,
        LocalDate startDate,
        LocalDate endDate,
        @Min(0) @Max(100) Integer progress,
        WbsNodeType nodeType,
        ExecutionMode executionMode,
        @Min(0) Integer weight,
        @Min(0) @Max(100) Integer agileRatio,
        AcceptanceStatus acceptanceStatus,
        LocalDate actualStartDate,
        LocalDate actualEndDate,
        LocalDate forecastEndDate,
        List<Long> tagIds
) {
}
