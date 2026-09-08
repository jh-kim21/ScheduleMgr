package com.projectflow.domain;

import java.util.List;
import java.util.Optional;

/** Domain-level repository port for acceptance checkpoints. */
public interface AcceptanceCheckpointRepository {

    AcceptanceCheckpoint save(AcceptanceCheckpoint checkpoint);

    Optional<AcceptanceCheckpoint> findById(Long id);

    /** Every checkpoint of a project, in no particular order. */
    List<AcceptanceCheckpoint> findByProjectId(Long projectId);

    void delete(AcceptanceCheckpoint checkpoint);
}
