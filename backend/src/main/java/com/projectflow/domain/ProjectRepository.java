package com.projectflow.domain;

import java.util.List;
import java.util.Optional;

/**
 * Domain-level repository port. The infrastructure layer supplies the
 * Spring Data / JPA-backed implementation.
 */
public interface ProjectRepository {

    Project save(Project project);

    Optional<Project> findById(Long id);

    List<Project> findAll();

    void deleteById(Long id);

    boolean existsById(Long id);
}
