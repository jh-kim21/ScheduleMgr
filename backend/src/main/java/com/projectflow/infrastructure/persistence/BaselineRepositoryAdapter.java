package com.projectflow.infrastructure.persistence;

import com.projectflow.domain.Baseline;
import com.projectflow.domain.BaselineItem;
import com.projectflow.domain.BaselineRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
class BaselineRepositoryAdapter implements BaselineRepository {

    private final BaselineJpaRepository baselines;
    private final BaselineItemJpaRepository items;

    BaselineRepositoryAdapter(BaselineJpaRepository baselines, BaselineItemJpaRepository items) {
        this.baselines = baselines;
        this.items = items;
    }

    @Override
    public Baseline save(Baseline baseline) {
        return baselines.save(baseline);
    }

    @Override
    public List<BaselineItem> saveItems(List<BaselineItem> toSave) {
        return items.saveAll(toSave);
    }

    @Override
    public List<Baseline> findByProjectId(Long projectId) {
        return baselines.findByProjectIdOrderByVersionAsc(projectId);
    }

    @Override
    public List<BaselineItem> findItemsByBaselineId(Long baselineId) {
        return items.findByBaselineIdOrderByIdAsc(baselineId);
    }
}
