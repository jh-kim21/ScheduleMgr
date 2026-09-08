package com.projectflow.infrastructure.persistence;

import com.projectflow.domain.ProgressSnapshot;
import com.projectflow.domain.ProgressSnapshotRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
class ProgressSnapshotRepositoryAdapter implements ProgressSnapshotRepository {

    private final ProgressSnapshotJpaRepository jpaRepository;

    ProgressSnapshotRepositoryAdapter(ProgressSnapshotJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public ProgressSnapshot save(ProgressSnapshot snapshot) {
        return jpaRepository.save(snapshot);
    }

    @Override
    public List<ProgressSnapshot> findByProjectId(Long projectId) {
        return jpaRepository.findByProjectIdOrderByAsOfDescIdDesc(projectId);
    }
}
