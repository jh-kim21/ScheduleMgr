package com.projectflow.infrastructure.persistence;

import com.projectflow.domain.AcceptanceCheckpoint;
import com.projectflow.domain.AcceptanceCheckpointRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
class AcceptanceCheckpointRepositoryAdapter implements AcceptanceCheckpointRepository {

    private final AcceptanceCheckpointJpaRepository jpaRepository;

    AcceptanceCheckpointRepositoryAdapter(AcceptanceCheckpointJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public AcceptanceCheckpoint save(AcceptanceCheckpoint checkpoint) {
        return jpaRepository.save(checkpoint);
    }

    @Override
    public Optional<AcceptanceCheckpoint> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public List<AcceptanceCheckpoint> findByProjectId(Long projectId) {
        return jpaRepository.findByProjectId(projectId);
    }

    @Override
    public void delete(AcceptanceCheckpoint checkpoint) {
        jpaRepository.delete(checkpoint);
    }
}
