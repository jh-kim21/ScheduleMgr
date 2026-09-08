package com.projectflow.domain;

import java.util.List;
import java.util.Optional;

/** Domain-level repository port for Sprints. */
public interface SprintRepository {

    Sprint save(Sprint sprint);

    Optional<Sprint> findById(Long id);

    /** Every Sprint of a project, in no particular order. */
    List<Sprint> findByProjectId(Long projectId);

    void delete(Sprint sprint);
}
