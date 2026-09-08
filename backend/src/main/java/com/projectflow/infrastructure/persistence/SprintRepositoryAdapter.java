package com.projectflow.infrastructure.persistence;

import com.projectflow.domain.Sprint;
import com.projectflow.domain.SprintRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
class SprintRepositoryAdapter implements SprintRepository {

    private final SprintJpaRepository jpaRepository;

    SprintRepositoryAdapter(SprintJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Sprint save(Sprint sprint) {
        return jpaRepository.save(sprint);
    }

    @Override
    public Optional<Sprint> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public List<Sprint> findByProjectId(Long projectId) {
        return jpaRepository.findByProjectId(projectId);
    }

    @Override
    public void delete(Sprint sprint) {
        jpaRepository.delete(sprint);
    }
}
