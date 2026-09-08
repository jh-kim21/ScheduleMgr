package com.projectflow.application.dto;

import com.projectflow.domain.RaciInheritance.RoleSource;
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
 * @param cells     one entry per non-empty cell, own letters and inherited ones alike
 * @param issues    RACI rule breaches — see {@link RaciValidator}
 */
public record RaciMatrixResponse(
        List<ProjectMemberResponse> members,
        List<RaciTaskResponse> tasks,
        List<RaciCellResponse> cells,
        List<RaciIssueResponse> issues
) {
    /**
     * @param summary        true when the entry has children; assignments are allowed on it, and
     *                       since Step 6 they are inherited by the work inside it
     * @param storyAssignees Backlog 담당자. <b>RACI 역할이 아니다</b> (지시서 6-B) — 함께
     *                       보여 주되 글자로 섹지 않고, 여기서 바꾸지도 못한다. Story의 담당자를
     *                       바꿔도 이 업무의 A는 그대로이고, 그 반대도 마찬가지다
     */
    public record RaciTaskResponse(
            Long id,
            Long parentId,
            String code,
            int level,
            String name,
            boolean summary,
            List<StoryAssigneeResponse> storyAssignees
    ) {
    }

    /**
     * Somebody carrying Backlog work under this Work Package.
     *
     * @param itemCount how many Backlog entries they hold here — enough to see who is loaded
     *                  without opening the Backlog screen
     */
    public record StoryAssigneeResponse(
            Long memberId,
            String memberName,
            int itemCount
    ) {
    }

    /**
     * One cell, split by where its letters come from.
     *
     * <p>Inherited letters are a separate list rather than a flag on the cell: the same person can
     * hold R here and inherit A from the phase, and flattening the two would make the inherited A
     * look deletable.
     *
     * @param assignmentIds ids of the stored letters, aligned with {@code roles} by index, so the
     *                      client can delete a single letter without another lookup
     * @param inherited     letters that apply here but live on an ancestor. No ids — they cannot be
     *                      removed from this row; the row that declares them has to be edited
     */
    public record RaciCellResponse(
            Long wbsItemId,
            Long memberId,
            List<RaciRole> roles,
            List<Long> assignmentIds,
            List<InheritedRoleResponse> inherited
    ) {
    }

    /**
     * @param source     always {@code INHERITED} here; carried so the client renders one shape
     * @param sourceItemId the row the letter is assigned on
     * @param sourceCode WBS code of that row, so the screen can say 어디서 왔는지
     * @param overridden this row assigns the same role to somebody else, so the inherited letter is
     *                   not in force here — 하위 재정의
     */
    public record InheritedRoleResponse(
            RaciRole role,
            RoleSource source,
            Long sourceItemId,
            String sourceCode,
            boolean overridden
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
