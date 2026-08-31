package com.projectflow.domain;

import java.util.List;
import java.util.Optional;

/** Domain-level repository port for WBS dependencies. */
public interface WbsDependencyRepository {

    WbsDependency save(WbsDependency dependency);

    Optional<WbsDependency> findById(Long id);

    List<WbsDependency> findByProjectId(Long projectId);

    void delete(WbsDependency dependency);
}
