package com.projectflow.application.dto;

import com.projectflow.domain.RaciRole;
import com.projectflow.domain.RaciValidator;

import java.util.List;

/**
 * The whole matrix in one payload (요구사항 7.2): its columns (members), its rows (WBS entries in
 * tree order, so they line up with the WBS and Gantt views), the letters in each cell, and what
 * the RACI rules say is wrong.
 *
 * <p>Like the WBS and Gantt endpoints, every mutation returns the whole matrix rather than the one
 * changed cell — adding an Accountable can resolve or create an issue on that row, and the client
 * has no way to work that out from a partial response.
 *
 * @param members   matrix columns, in creation order
 * @param tasks     matrix rows, flattened in tree order
 * @param cells     one entry per non-empty cell
 * @param issues    RACI rule breaches, leaves only — see {@link RaciValidator}
 */
public record RaciMatrixResponse(
        List<ProjectMemberResponse> members,
        List<RaciTaskResponse> tasks,
        List<RaciCellResponse> cells,
        List<RaciIssueResponse> issues
) {
    /**
     * @param summary true when the entry has children; assignments are allowed on it but the rule
     *                checks skip it
     */
    public record RaciTaskResponse(
            Long id,
            Long parentId,
            String code,
            int level,
            String name,
            boolean summary
    ) {
    }

    /**
     * @param assignmentIds ids of the stored letters, aligned with {@code roles} by index, so the
     *                      client can delete a single letter without another lookup
     */
    public record RaciCellResponse(
            Long wbsItemId,
            Long memberId,
            List<RaciRole> roles,
            List<Long> assignmentIds
    ) {
    }

    /** @param memberNames who clashes; filled only for {@code MULTIPLE_ACCOUNTABLE} */
    public record RaciIssueResponse(
            Long wbsItemId,
            String code,
            String name,
            RaciValidator.IssueType type,
            List<String> memberNames
    ) {
    }
}
