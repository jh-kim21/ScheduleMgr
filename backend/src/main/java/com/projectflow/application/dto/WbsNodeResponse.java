package com.projectflow.application.dto;

import com.projectflow.domain.DelayCalculator;
import com.projectflow.domain.DelayStatus;
import com.projectflow.domain.WbsNode;

import java.time.LocalDate;
import java.util.List;

/**
 * A WBS tree node as returned by the API. {@code code}, {@code level}, {@code summary}, the delay
 * fields and — for summary nodes — the schedule and progress are all derived server-side, so
 * clients render the tree without recomputing anything.
 *
 * @param delayStatus      schedule health as of the enclosing {@link WbsTreeResponse#referenceDate}
 * @param expectedProgress progress the linear baseline expects by that date
 * @param progressGap      percentage points behind that baseline; 0 when on or ahead
 * @param delayDays        days past the planned end date while incomplete; 0 otherwise
 */
public record WbsNodeResponse(
        Long id,
        Long parentId,
        String code,
        int level,
        String name,
        String description,
        LocalDate startDate,
        LocalDate endDate,
        int progress,
        boolean summary,
        DelayStatus delayStatus,
        int expectedProgress,
        int progressGap,
        long delayDays,
        List<WbsNodeResponse> children
) {
    public static WbsNodeResponse from(WbsNode node, LocalDate referenceDate) {
        DelayCalculator.DelayAssessment delay = DelayCalculator.assess(
                node.startDate(), node.endDate(), node.progress(), referenceDate);

        return new WbsNodeResponse(
                node.item().getId(),
                node.item().getParentId(),
                node.code(),
                node.level(),
                node.item().getName(),
                node.item().getDescription(),
                node.startDate(),
                node.endDate(),
                node.progress(),
                node.summary(),
                delay.status(),
                delay.expectedProgress(),
                delay.progressGap(),
                delay.delayDays(),
                node.children().stream().map(child -> from(child, referenceDate)).toList()
        );
    }
}
