package com.projectflow.domain;

import java.util.List;

/** Domain-level repository port for 업무 분야 tags. */
public interface WbsTagRepository {

    WbsTag save(WbsTag tag);

    /** Every tag of a project, in no particular order — callers sort by (sortOrder, id). */
    List<WbsTag> findByProjectId(Long projectId);

    void delete(WbsTag tag);
}
