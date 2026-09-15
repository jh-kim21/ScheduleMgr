package com.projectflow.domain;

import java.util.List;

/**
 * Raised when committing would push a project's total commit storage past its configured limit
 * (지시서 §4.4 — project-flow.commit.max-bytes-per-project, default 1GB).
 *
 * <p>Carries the numbers and the existing commits themselves (not a DTO — this is the domain
 * layer) so the screen that receives the 409 can show "용량 84% 사용 중 ... 삭제할 커밋을
 * 고르세요" without a second round trip. {@link com.projectflow.presentation.GlobalExceptionHandler}
 * turns these into the same {@code capacity}/{@code commits} shape the list endpoint returns.
 *
 * <p><b>No auto-deletion happens here or anywhere else</b> (지시서 §2.3) — this exception only
 * reports the situation; a person chooses what to delete.
 */
public class CommitCapacityExceededException extends RuntimeException {

    private final long usedBytes;
    private final long maxBytes;
    private final List<ProjectCommit> existingCommits;

    public CommitCapacityExceededException(String message, long usedBytes, long maxBytes,
                                            List<ProjectCommit> existingCommits) {
        super(message);
        this.usedBytes = usedBytes;
        this.maxBytes = maxBytes;
        this.existingCommits = existingCommits;
    }

    public long getUsedBytes() {
        return usedBytes;
    }

    public long getMaxBytes() {
        return maxBytes;
    }

    public List<ProjectCommit> getExistingCommits() {
        return existingCommits;
    }
}
