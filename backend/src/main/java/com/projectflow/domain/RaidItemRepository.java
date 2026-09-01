package com.projectflow.domain;

import java.util.List;
import java.util.Optional;

/** Domain-level repository port for RAID entries. */
public interface RaidItemRepository {

    RaidItem save(RaidItem item);

    Optional<RaidItem> findById(Long id);

    /** Every entry of a project, in no particular order. */
    List<RaidItem> findByProjectId(Long projectId);

    void delete(RaidItem item);
}
