package com.projectflow.application.dto;

import jakarta.validation.constraints.NotNull;

public record TodoCompletionRequest(@NotNull Boolean completed) {
}
