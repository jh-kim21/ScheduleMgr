package com.projectflow.domain;

import java.util.List;

/** Domain-level repository port for change records. */
public interface ChangeLogRepository {

    ChangeLog save(ChangeLog change);

    List<ChangeLog> saveAll(List<ChangeLog> changes);

    /** Every record of a project, oldest first. */
    List<ChangeLog> findByProjectId(Long projectId);
}
