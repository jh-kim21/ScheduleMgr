package com.projectflow.application.dto;

import com.projectflow.domain.ProjectMember;

import java.time.LocalDateTime;

public record ProjectMemberResponse(
        Long id,
        String name,
        String email,
        String position,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static ProjectMemberResponse from(ProjectMember member) {
        return new ProjectMemberResponse(
                member.getId(),
                member.getName(),
                member.getEmail(),
                member.getPosition(),
                member.getCreatedAt(),
                member.getUpdatedAt()
        );
    }
}
