package com.projectflow.application.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProjectMemberUpdateRequest(
        @NotBlank @Size(max = 100) String name,
        @Email @Size(max = 255) String email,
        @Size(max = 100) String position
) {
}
