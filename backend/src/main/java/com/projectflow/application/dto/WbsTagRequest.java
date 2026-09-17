package com.projectflow.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param color     optional; {@code null} lets the screen derive one from the name
 * @param sortOrder optional on create — {@code null} appends to the end of the list
 */
public record WbsTagRequest(
        @NotBlank @Size(max = 50) String name,
        @Size(max = 20) String color,
        Integer sortOrder
) {
}
