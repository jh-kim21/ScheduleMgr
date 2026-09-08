package com.projectflow.domain;

import java.util.List;

/** Domain-level repository port for progress report snapshots. */
public interface ProgressSnapshotRepository {

    ProgressSnapshot save(ProgressSnapshot snapshot);

    /** Every snapshot of a project, newest report date first. */
    List<ProgressSnapshot> findByProjectId(Long projectId);
}
