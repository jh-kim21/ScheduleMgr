package com.projectflow.domain;

import java.util.List;
import java.util.Optional;

/** Domain-level repository port for project members. */
public interface ProjectMemberRepository {

    ProjectMember save(ProjectMember member);

    Optional<ProjectMember> findById(Long id);

    /** Every member of a project, in no particular order. */
    List<ProjectMember> findByProjectId(Long projectId);

    void delete(ProjectMember member);
}
