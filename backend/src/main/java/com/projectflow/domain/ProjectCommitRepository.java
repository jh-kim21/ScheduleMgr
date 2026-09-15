package com.projectflow.domain;

import java.util.List;
import java.util.Optional;

/** Domain-level repository port for project commits (docs/tasks/commit-history.md). */
public interface ProjectCommitRepository {

    ProjectCommit save(ProjectCommit commit);

    void delete(ProjectCommit commit);

    /** Every commit of a project, newest version first — a commit history reads top-down like a log. */
    List<ProjectCommit> findByProjectId(Long projectId);

    Optional<ProjectCommit> findByProjectIdAndVersion(Long projectId, int version);

    /** By its own id rather than project-scoped, because restore addresses a commit directly
     * ({@code POST /api/projects/commits/{commitId}/restore}) without knowing its project up front. */
    Optional<ProjectCommit> findById(Long id);

    /** {@code MAX(version)} for the project, or 0 when it has none — so the caller can add 1
     * without a null check. Never {@code count()}: deleted versions are not reused (§3.3). */
    int findMaxVersion(Long projectId);

    /** {@code SUM(payload_bytes)} for the project, or 0 when it has none. */
    long sumPayloadBytes(Long projectId);
}
