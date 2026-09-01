package com.projectflow.domain;

import java.util.List;
import java.util.Optional;

/** Domain-level repository port for RACI assignments. */
public interface RaciAssignmentRepository {

    RaciAssignment save(RaciAssignment assignment);

    Optional<RaciAssignment> findById(Long id);

    /** Every assignment of a project, in no particular order. */
    List<RaciAssignment> findByProjectId(Long projectId);

    void delete(RaciAssignment assignment);
}
