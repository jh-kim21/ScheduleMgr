package com.projectflow.application;

import java.time.LocalDate;

/**
 * One validated row of an uploaded WBS file, before it becomes a {@link com.projectflow.domain.WbsItem}.
 *
 * <p>{@code level} is 1-based indentation depth (열 "레벨"), not a WBS code — codes are derived from
 * tree position and are never accepted from a file (같은 이유로 가져오기가 {@code code}를 무시하는 것과
 * 같다). {@code rowNumber} is the file's own row number (header counted), used only for error
 * messages so they point at what the user sees in Excel/CSV.
 */
public record WbsImportRow(
        int rowNumber,
        int level,
        String name,
        LocalDate startDate,
        LocalDate endDate,
        int progress
) {
}
