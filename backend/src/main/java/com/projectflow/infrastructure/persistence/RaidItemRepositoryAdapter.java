package com.projectflow.infrastructure.persistence;

import com.projectflow.domain.RaidItem;
import com.projectflow.domain.RaidItemRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
class RaidItemRepositoryAdapter implements RaidItemRepository {

    private final RaidItemJpaRepository jpaRepository;

    RaidItemRepositoryAdapter(RaidItemJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public RaidItem save(RaidItem item) {
        return jpaRepository.save(item);
    }

    @Override
    public Optional<RaidItem> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public List<RaidItem> findByProjectId(Long projectId) {
        return jpaRepository.findByProjectId(projectId);
    }

    @Override
    public void delete(RaidItem item) {
        jpaRepository.delete(item);
    }
}
