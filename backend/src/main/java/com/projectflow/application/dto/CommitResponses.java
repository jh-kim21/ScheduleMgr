package com.projectflow.application.dto;

import com.projectflow.domain.ProjectCommit;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Response shapes for the project commit history (docs/tasks/commit-history.md).
 *
 * <p>{@link CommitSummaryResponse} is the commit's metadata only — no payload — so listing commits
 * stays cheap regardless of how large {@code raw_payload}/{@code computed_payload} are.
 * {@link CommitComputedPayload} is the other half: every screen response a commit captured, with
 * judged values included, matching the shape of the live endpoint it stands in for so a screen
 * built against the live API needs no changes to render a commit.
 */
public final class CommitResponses {

    private CommitResponses() {
    }

    /** A commit's metadata, without either payload. */
    public record CommitSummaryResponse(
            Long id,
            int version,
            LocalDate asOf,
            LocalDateTime committedAt,
            String committedBy,
            String message,
            int formatVersion,
            long payloadBytes
    ) {
        public static CommitSummaryResponse from(ProjectCommit commit) {
            return new CommitSummaryResponse(
                    commit.getId(),
                    commit.getVersion(),
                    commit.getAsOf(),
                    commit.getCommittedAt(),
                    commit.getCommittedBy(),
                    commit.getMessage(),
                    commit.getFormatVersion(),
                    commit.getPayloadBytes()
            );
        }
    }

    /**
     * Storage used against a project's limit (지시서 §4.4).
     *
     * @param warning true once usage reaches 80% of {@code maxBytes} — the point where the screen
     *                shows a "용량 84% 사용 중" banner, well before the 100% hard stop
     */
    public record CommitCapacityResponse(
            long usedBytes,
            long maxBytes,
            double usedPercent,
            boolean warning
    ) {
        private static final double WARNING_THRESHOLD_PERCENT = 80.0;

        public static CommitCapacityResponse of(long usedBytes, long maxBytes) {
            double percent = maxBytes <= 0 ? 0.0 : (usedBytes * 100.0) / maxBytes;
            return new CommitCapacityResponse(usedBytes, maxBytes, percent,
                    percent >= WARNING_THRESHOLD_PERCENT);
        }
    }

    public record CommitCreateResponse(
            CommitSummaryResponse commit,
            CommitCapacityResponse capacity
    ) {
    }

    public record CommitListResponse(
            CommitCapacityResponse capacity,
            List<CommitSummaryResponse> commits
    ) {
    }

    /**
     * Every screen response one commit captured, judged values included (지시서 §2.1, §4.3).
     *
     * <p>Each field is the corresponding live endpoint's response type verbatim — {@code wbs} is
     * exactly a {@link WbsTreeResponse}, and so on — so a commit can be handed to the same rendering
     * code a live screen already uses. {@code snapshots} here means the saved progress reports
     * ({@link SnapshotResponse}, {@code GET /progress/snapshots}), not this commit itself.
     *
     * <p>There is no separate checkpoints field: checkpoints are already nested inside
     * {@code progress.workPackages[].checkpoints}, and duplicating them here would let the two
     * copies disagree.
     */
    public record CommitComputedPayload(
            LocalDate asOf,
            WbsTreeResponse wbs,
            GanttResponse gantt,
            List<ProjectMemberResponse> members,
            RaciMatrixResponse raci,
            RaidLogResponse raid,
            BacklogResponse backlog,
            SprintResponse sprints,
            ProgressResponse progress,
            SnapshotResponse snapshots,
            DashboardResponse dashboard
    ) {
    }

    public record CommitDetailResponse(
            CommitSummaryResponse commit,
            CommitComputedPayload payload
    ) {
    }
}
