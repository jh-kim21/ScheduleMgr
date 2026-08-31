package com.projectflow.domain;

import java.util.List;
import java.util.Optional;

/**
 * Domain-level repository port for WBS entries. The infrastructure layer supplies the
 * Spring Data / JPA-backed implementation.
 */
public interface WbsItemRepository {

    WbsItem save(WbsItem item);

    List<WbsItem> saveAll(List<WbsItem> items);

    Optional<WbsItem> findById(Long id);

    /** Every entry of a project, in no particular order. */
    List<WbsItem> findByProjectId(Long projectId);

    void delete(WbsItem item);
}
