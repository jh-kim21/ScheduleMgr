package com.projectflow.application.dto;

import com.projectflow.domain.ProjectStatus;
import com.projectflow.domain.RaciRole;
import com.projectflow.domain.RaidLevel;
import com.projectflow.domain.RaidStatus;
import com.projectflow.domain.RaidType;

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
        List<ExportedRaidItem> raidItems
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

    /** @param code derived from tree position; included for readability, not authoritative */
    public record ExportedWbsItem(
            Long id,
            Long parentId,
            String code,
            String name,
            String description,
            LocalDate startDate,
            LocalDate endDate,
            int progress,
            int sortOrder
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
            LocalDate dueDate,
            String response
    ) {
    }
}
