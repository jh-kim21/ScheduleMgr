package com.projectflow.domain;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * The two judgements the RAID log makes about an entry, both derived on read rather than stored —
 * the same reason WBS codes and delay verdicts are derived (요구사항 9).
 *
 * <ul>
 *   <li><b>Exposure</b> — probability × impact on the usual 1·2·3 scale, so 1 to 9. Computed
 *       whenever both are given, with no per-type rule: a risk is the typical user, but recording
 *       an impact on an issue that already happened is normal too, and inventing a rule that a
 *       type may not carry a number would only make the register lie about what was entered.</li>
 *   <li><b>Overdue</b> — the due date has passed and the entry is not closed. As with delay, the
 *       reference date is decided by the server and returned with the payload, so every row in one
 *       response is measured against the same "today" and a long-open tab cannot drift.</li>
 * </ul>
 */
public final class RaidAssessor {

    private RaidAssessor() {
    }

    /**
     * @param exposure      probability × impact (1–9), or null unless both are set
     * @param exposureLevel the band {@code exposure} falls in, or null likewise
     * @param overdue       past its due date while still open
     * @param overdueDays   days past the due date; 0 when not overdue
     */
    public record RaidAssessment(
            Integer exposure,
            RaidLevel exposureLevel,
            boolean overdue,
            long overdueDays
    ) {
    }

    public static RaidAssessment assess(RaidItem item, LocalDate referenceDate) {
        Integer exposure = null;
        RaidLevel exposureLevel = null;
        if (item.getProbability() != null && item.getImpact() != null) {
            exposure = item.getProbability().weight() * item.getImpact().weight();
            exposureLevel = bandOf(exposure);
        }

        // A closed entry is not late: there is nothing left to be late for.
        boolean overdue = item.getDueDate() != null
                && item.getStatus() != RaidStatus.CLOSED
                && item.getDueDate().isBefore(referenceDate);
        long overdueDays = overdue
                ? ChronoUnit.DAYS.between(item.getDueDate(), referenceDate)
                : 0;

        return new RaidAssessment(exposure, exposureLevel, overdue, overdueDays);
    }

    /**
     * Bands the 1–9 score. Only 1, 2, 3, 4, 6 and 9 are reachable products of {1,2,3}, so the
     * thresholds are chosen on those: 1–2 낮음, 3–4 보통, 6 이상 높음. "높음" therefore means at
     * least one HIGH alongside a MEDIUM, which is the point at which a risk needs a plan.
     */
    private static RaidLevel bandOf(int exposure) {
        if (exposure >= 6) {
            return RaidLevel.HIGH;
        }
        return exposure >= 3 ? RaidLevel.MEDIUM : RaidLevel.LOW;
    }
}
