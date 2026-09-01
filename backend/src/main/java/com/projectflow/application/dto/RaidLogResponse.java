package com.projectflow.application.dto;

import com.projectflow.domain.RaidLevel;
import com.projectflow.domain.RaidStatus;
import com.projectflow.domain.RaidType;

import java.time.LocalDate;
import java.util.List;

/**
 * The whole register in one payload (요구사항 9).
 *
 * <p>Every mutation returns the whole log rather than the single changed entry. Not for the WBS's
 * reason — nothing here is derived across rows — but because {@code overdue} is measured against
 * {@link #referenceDate}, which the client must not compute itself, and returning both together
 * saves the client from merging a row into a list it holds.
 *
 * @param referenceDate the date overdue-ness was judged against, decided by the server
 */
public record RaidLogResponse(
        LocalDate referenceDate,
        List<RaidItemResponse> items
) {
    /**
     * @param ownerName   resolved from {@code ownerMemberId}, so the client needs no second lookup
     * @param wbsCode     WBS code of the linked task, resolved server-side (the code is derived from
     *                    tree position, so only the server can produce it)
     * @param wbsName     name of the linked task
     * @param exposure    probability × impact (1–9), null unless both are set
     * @param exposureLevel band {@code exposure} falls in, null likewise
     * @param overdue     past its due date while not closed
     * @param overdueDays days past the due date; 0 when not overdue
     */
    public record RaidItemResponse(
            Long id,
            RaidType type,
            String title,
            String description,
            RaidStatus status,
            RaidLevel probability,
            RaidLevel impact,
            Long ownerMemberId,
            String ownerName,
            Long wbsItemId,
            String wbsCode,
            String wbsName,
            LocalDate dueDate,
            String response,
            Integer exposure,
            RaidLevel exposureLevel,
            boolean overdue,
            long overdueDays
    ) {
    }
}
