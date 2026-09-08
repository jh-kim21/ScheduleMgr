package com.projectflow.application.dto;

import com.projectflow.domain.RaidLinkTarget;
import jakarta.validation.constraints.NotNull;

/**
 * One thing a RAID entry is being attached to.
 *
 * <p>Sent as a list so the same risk can name several Stories and a Sprint at once — the design is
 * explicit that it stays one entry rather than being copied per target (설계 §9).
 */
public record RaidLinkRequest(
        @NotNull RaidLinkTarget targetType,
        @NotNull Long targetId
) {
}
