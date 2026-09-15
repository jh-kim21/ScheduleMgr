package com.projectflow.application.dto;

/** Request bodies for the project commit history (docs/tasks/commit-history.md). */
public final class CommitRequests {

    private CommitRequests() {
    }

    /**
     * Both fields are optional free text — there is no login, and a commit with no message or no
     * author is still a valid commit (지시서 §4.1: {@code committed_by}는 자유 입력, nullable).
     */
    public record CommitCreateRequest(
            String committedBy,
            String message
    ) {
    }
}
