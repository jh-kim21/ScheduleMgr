package com.projectflow.application.dto;

import com.projectflow.domain.AcceptanceStatus;
import com.projectflow.domain.BacklogItemType;
import com.projectflow.domain.BacklogPriority;
import com.projectflow.domain.BacklogStatus;
import com.projectflow.domain.ExecutionMode;
import com.projectflow.domain.ProjectStatus;
import com.projectflow.domain.RaciRole;
import com.projectflow.domain.RaidLevel;
import com.projectflow.domain.RaidLinkTarget;
import com.projectflow.domain.RaidStatus;
import com.projectflow.domain.RaidType;
import com.projectflow.domain.SprintItemOutcome;
import com.projectflow.domain.SprintStatus;
import com.projectflow.domain.WbsNodeType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * A whole project in one file, for handing it to someone else.
 *
 * <p>This matters because the desktop build keeps its data in a local H2 file: two people running
 * the installed app have no shared server, so a file is the only way to move a project between
 * them. Everything needed to rebuild the project is here, in one document.
 *
 * <p><b>Stored state only.</b> Judged values — delay status, schedule violations, float, RAID
 * exposure, overdue flags — are deliberately left out. They are all computed against "today" or
 * against other rows, so a copy of them inside a shared file would be wrong the moment it is
 * opened. The one exception is the WBS code, kept because a human reading the file needs it;
 * it is derived from tree position and would be recomputed, not trusted, on any import.
 *
 * <p>The WBS is flat with {@code parentId} references rather than nested: the tree is
 * unambiguous either way, and a flat list is far easier to read, diff and re-insert.
 *
 * @param formatVersion bumped when the shape changes, so a future importer can tell what it has
 * @param exportedAt    when the snapshot was taken
 */
public record ProjectExportResponse(
        int formatVersion,
        LocalDateTime exportedAt,
        ExportedProject project,
        List<ExportedMember> members,
        List<ExportedWbsItem> wbsItems,
        List<ExportedDependency> dependencies,
        List<ExportedRaciAssignment> raciAssignments,
        List<ExportedRaidItem> raidItems,
        List<ExportedBacklogItem> backlogItems,
        List<ExportedSprint> sprints,
        List<ExportedSprintItem> sprintItems,
        List<ExportedCheckpoint> checkpoints,
        List<ExportedBaseline> baselines,
        List<ExportedSnapshot> snapshots
) {
    public record ExportedProject(
            Long id,
            String name,
            String description,
            ProjectStatus status,
            LocalDate startDate,
            LocalDate endDate,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    public record ExportedMember(
            Long id,
            String name,
            String email,
            String position
    ) {
    }

    /**
     * @param code          derived from tree position; included for readability, not authoritative
     * @param nodeType      {@code null} in files written before Step 2 (formatVersion 1); an import
     *                      then derives it from child presence, exactly as the migration did
     * @param executionMode {@code null} means 미지정, and is also what every formatVersion 1 file
     *                      implies
     */
    public record ExportedWbsItem(
            Long id,
            Long parentId,
            String code,
            String name,
            String description,
            LocalDate startDate,
            LocalDate endDate,
            int progress,
            int sortOrder,
            WbsNodeType nodeType,
            ExecutionMode executionMode,
            Integer weight,
            Integer agileRatio,
            AcceptanceStatus acceptanceStatus,
            LocalDate actualStartDate,
            LocalDate actualEndDate,
            LocalDate forecastEndDate
    ) {
    }

    /**
     * One approval checkpoint. Approval state travels: it is the Waterfall denominator's numerator,
     * and an import that reset every approval would hand over a project that had achieved nothing.
     */
    public record ExportedCheckpoint(
            Long id,
            Long wbsItemId,
            String title,
            Integer weight,
            String completionCriteria,
            boolean approved,
            String approvedBy,
            LocalDateTime approvedAt,
            int sortOrder
    ) {
    }

    /**
     * An approved baseline with its copied items. The copies are what make it a baseline, so they
     * travel verbatim — including the WBS code, which cannot be recomputed once the tree moves.
     */
    public record ExportedBaseline(
            Long id,
            int version,
            String approvedBy,
            LocalDateTime approvedAt,
            String note,
            List<ExportedBaselineItem> items
    ) {
    }

    public record ExportedBaselineItem(
            Long wbsItemId,
            String code,
            String name,
            WbsNodeType nodeType,
            ExecutionMode executionMode,
            LocalDate startDate,
            LocalDate endDate,
            Integer weight,
            String completionCriteria
    ) {
    }

    /**
     * A saved report. {@code metrics} is carried as the stored string rather than recomputed —
     * recomputing it is exactly what a snapshot exists to make unnecessary.
     */
    public record ExportedSnapshot(
            Long id,
            LocalDate asOf,
            Long baselineId,
            int scopeItemCount,
            Integer scopeWeightTotal,
            String metrics,
            String note
    ) {
    }

    public record ExportedDependency(
            Long id,
            Long predecessorId,
            Long successorId,
            int lagDays
    ) {
    }

    public record ExportedRaciAssignment(
            Long id,
            Long wbsItemId,
            Long memberId,
            RaciRole role
    ) {
    }

    /**
     * A Product Backlog entry. Flat with {@code parentId} references, like the WBS above.
     *
     * <p>{@code archivedAt} is stored state, so it travels; the per-row warnings
     * ({@code unlinked}, {@code requiresExecutionModeChange} …) do not — they are judged against
     * the Work Package each entry points at and would be wrong in a file the moment either side
     * changed.
     */
    public record ExportedBacklogItem(
            Long id,
            Long wbsItemId,
            Long parentId,
            BacklogItemType itemType,
            String title,
            String description,
            BacklogPriority priority,
            BacklogStatus status,
            Long assigneeMemberId,
            String acceptanceCriteria,
            Integer storyPoint,
            Integer progressWeight,
            LocalDateTime archivedAt,
            int sortOrder,
            boolean blocked,
            String blockedReason,
            LocalDateTime doneAt
    ) {
    }

    /**
     * A Sprint with its lifecycle state. {@code status} and {@code closedAt} travel because a
     * closed Sprint's results are the record of what happened — an import that reset every Sprint
     * to 계획 would erase the project's execution history.
     */
    public record ExportedSprint(
            Long id,
            String name,
            String goal,
            LocalDate startDate,
            LocalDate endDate,
            SprintStatus status,
            LocalDateTime closedAt
    ) {
    }

    /**
     * One Sprint assignment, including removed and settled ones — the rows <em>are</em> the
     * carry-over history, so dropping the inactive ones would lose why an item moved.
     */
    public record ExportedSprintItem(
            Long id,
            Long sprintId,
            Long backlogItemId,
            LocalDateTime addedAt,
            LocalDateTime removedAt,
            Integer pointsAtStart,
            Integer pointsAtClose,
            SprintItemOutcome outcome
    ) {
    }

    /**
     * @param wbsItemId <b>formatVersion 5 이하에서만</b> 채워지는 단일 연결. 내보내기는 이제
     *                  항상 {@code links}를 쓰고 이 값을 비운다; 가져오기는 예전 파일을 읽기 위해
     *                  둘 다 받아들인다
     * @param links     연결 대상들 (formatVersion 6부터)
     */
    public record ExportedRaidItem(
            Long id,
            RaidType type,
            String title,
            String description,
            RaidStatus status,
            RaidLevel probability,
            RaidLevel impact,
            Long ownerMemberId,
            Long wbsItemId,
            List<ExportedRaidLink> links,
            LocalDate dueDate,
            String response
    ) {
    }

    public record ExportedRaidLink(
            Long id,
            RaidLinkTarget targetType,
            Long targetId
    ) {
    }
}
