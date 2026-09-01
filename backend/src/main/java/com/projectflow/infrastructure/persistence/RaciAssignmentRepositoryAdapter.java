package com.projectflow.infrastructure.persistence;

import com.projectflow.domain.RaciAssignment;
import com.projectflow.domain.RaciAssignmentRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
class RaciAssignmentRepositoryAdapter implements RaciAssignmentRepository {

    private final RaciAssignmentJpaRepository jpaRepository;

    RaciAssignmentRepositoryAdapter(RaciAssignmentJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public RaciAssignment save(RaciAssignment assignment) {
        return jpaRepository.save(assignment);
    }

    @Override
    public Optional<RaciAssignment> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public List<RaciAssignment> findByProjectId(Long projectId) {
        return jpaRepository.findByProjectId(projectId);
    }

    @Override
    public void delete(RaciAssignment assignment) {
        jpaRepository.delete(assignment);
    }
}
