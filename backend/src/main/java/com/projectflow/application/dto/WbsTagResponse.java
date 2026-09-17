package com.projectflow.application.dto;

import com.projectflow.domain.WbsTag;

/**
 * One 업무 분야 tag in the project's master list.
 *
 * <p>Carries {@code sortOrder} where {@link TagRef} does not: the management dialog reorders the
 * list, while a chip on a WBS row only has to be drawn.
 */
public record WbsTagResponse(Long id, String name, String color, int sortOrder) {

    public static WbsTagResponse from(WbsTag tag) {
        return new WbsTagResponse(tag.getId(), tag.getName(), tag.getColor(), tag.getSortOrder());
    }
}
