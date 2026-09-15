package com.projectflow.domain;

/** Raised when a commit version (or, for restore, a commit id) does not exist for the project. */
public class CommitNotFoundException extends RuntimeException {

    public CommitNotFoundException(Long projectId, int version) {
        super("커밋을 찾을 수 없습니다: 프로젝트 %d, 버전 %d".formatted(projectId, version));
    }

    public CommitNotFoundException(Long commitId) {
        super("커밋을 찾을 수 없습니다: " + commitId);
    }
}
