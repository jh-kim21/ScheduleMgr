package com.projectflow.application.dto;

import com.projectflow.domain.RaidLevel;
import com.projectflow.domain.RaidStatus;
import com.projectflow.domain.RaidType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Create and update share one shape: the type is editable too, because an assumption that turns
 * out false usually becomes an issue rather than a new entry.
 *
 * @param probability 확률. 영향과 함께 있을 때만 노출도로 환산된다
 * @param impact      영향
 * @param ownerMemberId 프로젝트 구성원 id, 미지정 가능
 * @param wbsItemId   관련 WBS 항목 id, 미지정 가능 — 프로젝트 전체에 대한 항목도 많다
 */
public record RaidItemRequest(
        @NotNull RaidType type,
        @NotBlank @Size(max = 255) String title,
        @Size(max = 2000) String description,
        @NotNull RaidStatus status,
        RaidLevel probability,
        RaidLevel impact,
        Long ownerMemberId,
        Long wbsItemId,
        LocalDate dueDate,
        @Size(max = 2000) String response
) {
}
