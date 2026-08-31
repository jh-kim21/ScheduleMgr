package com.projectflow.application.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * The WBS tree plus the date its delay verdicts were measured from.
 *
 * <p>The tree is wrapped rather than returned as a bare list because the nodes now carry
 * time-dependent values: a response that says a task is 6일 지연 is meaningless without saying
 * what "today" it was judged against.
 *
 * @param referenceDate the date delay was judged against, i.e. the server's today
 * @param nodes         root nodes in sibling order
 */
public record WbsTreeResponse(
        LocalDate referenceDate,
        List<WbsNodeResponse> nodes
) {
}
