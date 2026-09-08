package com.projectflow.infrastructure.persistence;

import com.projectflow.domain.ChangeLog;
import com.projectflow.domain.ChangeLogRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
class ChangeLogRepositoryAdapter implements ChangeLogRepository {

    private final ChangeLogJpaRepository jpaRepository;

    ChangeLogRepositoryAdapter(ChangeLogJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public ChangeLog save(ChangeLog change) {
        return jpaRepository.save(change);
    }

    @Override
    public List<ChangeLog> saveAll(List<ChangeLog> changes) {
        return jpaRepository.saveAll(changes);
    }

    @Override
    public List<ChangeLog> findByProjectId(Long projectId) {
        return jpaRepository.findByProjectIdOrderByIdAsc(projectId);
    }
}
