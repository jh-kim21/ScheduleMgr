package com.projectflow.domain;

import java.util.List;

/** Domain-level repository port for Sprint assignments. */
public interface SprintItemRepository {

    SprintItem save(SprintItem item);

    List<SprintItem> saveAll(List<SprintItem> items);

    /** Every assignment of a project, including removed and settled ones — the rows are history. */
    List<SprintItem> findByProjectId(Long projectId);
}
