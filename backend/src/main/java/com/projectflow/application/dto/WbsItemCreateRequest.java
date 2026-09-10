package com.projectflow.application.dto;

import com.projectflow.domain.AcceptanceStatus;
import com.projectflow.domain.ExecutionMode;
import com.projectflow.domain.WbsNodeType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

/**
 * @param parentId         parent entry id, or {@code null} to create a root-level entry
 * @param nodeType         {@code null} defaults to {@link WbsNodeType#WORK_PACKAGE} — a new entry has
 *                         no children, which is exactly what the migration backfilled as a Work
 *                         Package, so an older client that omits the field keeps behaving as before
 * @param executionMode    {@code null} means 미지정, i.e. keep using the manually entered progress
 * @param weight           share among siblings; {@code null} is "not entered", not 0
 * @param agileRatio       Hybrid's α (0–100), same constraint as on update — kept here too so a
 *                         Work Package created straight into HYBRID does not need a follow-up edit
 *                         just to stop reading as 산정 전
 * @param acceptanceStatus formal acceptance, kept apart from progress. {@code null} = 절차 없음
 */
public record WbsItemCreateRequest(
        Long parentId,
        @NotBlank String name,
        String description,
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
        LocalDate forecastEndDate
) {
}
