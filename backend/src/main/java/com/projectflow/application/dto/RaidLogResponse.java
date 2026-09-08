package com.projectflow.application.dto;

import com.projectflow.domain.RaidLevel;
import com.projectflow.domain.RaidLinkTarget;
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
     * @param links       what the entry is attached to. Resolved server-side because a WBS code is
     *                    derived from tree position and a Sprint or Story name is not something the
     *                    client holds while looking at the register
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
            List<RaidLinkResponse> links,
            LocalDate dueDate,
            String response,
            Integer exposure,
            RaidLevel exposureLevel,
            boolean overdue,
            long overdueDays
    ) {
    }

    /**
     * @param targetCode WBS 코드처럼 표시용 식별자. Sprint·Backlog에는 없어 {@code null}
     * @param targetName 대상의 이름. 대상이 사라졌으면 {@code null} — 링크는 FK가 아니라
     *                   끊어질 수 있고, 그 사실을 숨기지 않는다
     */
    public record RaidLinkResponse(
            Long id,
            RaidLinkTarget targetType,
            Long targetId,
            String targetCode,
            String targetName
    ) {
    }
}
