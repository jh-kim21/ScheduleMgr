package com.projectflow.infrastructure.persistence;

import com.projectflow.domain.BacklogItem;
import com.projectflow.domain.BacklogItemRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
class BacklogItemRepositoryAdapter implements BacklogItemRepository {

    private final BacklogItemJpaRepository jpaRepository;

    BacklogItemRepositoryAdapter(BacklogItemJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public BacklogItem save(BacklogItem item) {
        return jpaRepository.save(item);
    }

    @Override
    public List<BacklogItem> saveAll(List<BacklogItem> items) {
        return jpaRepository.saveAll(items);
    }

    @Override
    public Optional<BacklogItem> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public List<BacklogItem> findByProjectId(Long projectId) {
        return jpaRepository.findByProjectId(projectId);
    }

    @Override
    public void delete(BacklogItem item) {
        jpaRepository.delete(item);
    }
}
