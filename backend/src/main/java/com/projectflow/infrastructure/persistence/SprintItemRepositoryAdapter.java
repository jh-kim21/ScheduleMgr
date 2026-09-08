package com.projectflow.infrastructure.persistence;

import com.projectflow.domain.SprintItem;
import com.projectflow.domain.SprintItemRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
class SprintItemRepositoryAdapter implements SprintItemRepository {

    private final SprintItemJpaRepository jpaRepository;

    SprintItemRepositoryAdapter(SprintItemJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public SprintItem save(SprintItem item) {
        return jpaRepository.save(item);
    }

    @Override
    public List<SprintItem> saveAll(List<SprintItem> items) {
        return jpaRepository.saveAll(items);
    }

    @Override
    public List<SprintItem> findByProjectId(Long projectId) {
        return jpaRepository.findByProjectIdOrderByIdAsc(projectId);
    }
}
