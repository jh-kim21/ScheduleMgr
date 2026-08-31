package com.projectflow.infrastructure.persistence;

import com.projectflow.domain.WbsItem;
import com.projectflow.domain.WbsItemRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
class WbsItemRepositoryAdapter implements WbsItemRepository {

    private final WbsItemJpaRepository jpaRepository;

    WbsItemRepositoryAdapter(WbsItemJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public WbsItem save(WbsItem item) {
        return jpaRepository.save(item);
    }

    @Override
    public List<WbsItem> saveAll(List<WbsItem> items) {
        return jpaRepository.saveAll(items);
    }

    @Override
    public Optional<WbsItem> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public List<WbsItem> findByProjectId(Long projectId) {
        return jpaRepository.findByProjectId(projectId);
    }

    @Override
    public void delete(WbsItem item) {
        jpaRepository.delete(item);
    }
}
