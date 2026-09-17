package com.projectflow.application.dto;

import com.projectflow.domain.WbsTag;

/**
 * A 업무 분야 tag as it appears on a WBS row — enough to draw the chip, nothing more.
 *
 * <p>{@code color} may be {@code null}; the screen then derives one from the name by hash, so the
 * same tag keeps the same colour every time it is drawn.
 */
public record TagRef(Long id, String name, String color) {

    public static TagRef from(WbsTag tag) {
        return new TagRef(tag.getId(), tag.getName(), tag.getColor());
    }
}
