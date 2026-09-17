package com.projectflow.domain;

import java.util.Collection;
import java.util.List;

/** Domain-level repository port for the WBS 항목 ↔ 분야 links. */
public interface WbsItemTagRepository {

    List<WbsItemTag> saveAll(List<WbsItemTag> links);

    /**
     * Links of the given entries. The WBS is always read a whole project at a time, so callers
     * pass every id at once rather than asking row by row.
     */
    List<WbsItemTag> findByWbsItemIdIn(Collection<Long> wbsItemIds);

    /** Links pointing at one tag — needed to drop them before the tag itself goes. */
    List<WbsItemTag> findByTagId(Long tagId);

    void deleteAll(List<WbsItemTag> links);
}
