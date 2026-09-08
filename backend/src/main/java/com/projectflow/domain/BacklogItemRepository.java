package com.projectflow.domain;

import java.util.List;
import java.util.Optional;

/** Domain-level repository port for Product Backlog entries. */
public interface BacklogItemRepository {

    BacklogItem save(BacklogItem item);

    List<BacklogItem> saveAll(List<BacklogItem> items);

    Optional<BacklogItem> findById(Long id);

    /** Every entry of a project, in no particular order. */
    List<BacklogItem> findByProjectId(Long projectId);

    void delete(BacklogItem item);
}
