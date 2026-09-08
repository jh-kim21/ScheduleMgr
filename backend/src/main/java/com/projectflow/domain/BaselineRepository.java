package com.projectflow.domain;

import java.util.List;

/** Domain-level repository port for approved baselines and their copied items. */
public interface BaselineRepository {

    Baseline save(Baseline baseline);

    List<BaselineItem> saveItems(List<BaselineItem> items);

    /** Every baseline of a project, oldest version first. */
    List<Baseline> findByProjectId(Long projectId);

    List<BaselineItem> findItemsByBaselineId(Long baselineId);
}
