package com.projectflow.infrastructure.persistence;

import com.projectflow.domain.WbsDependency;
import com.projectflow.domain.WbsDependencyRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
class WbsDependencyRepositoryAdapter implements WbsDependencyRepository {

    private final WbsDependencyJpaRepository jpaRepository;

    WbsDependencyRepositoryAdapter(WbsDependencyJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public WbsDependency save(WbsDependency dependency) {
        return jpaRepository.save(dependency);
    }

    @Override
    public Optional<WbsDependency> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public List<WbsDependency> findByProjectId(Long projectId) {
        return jpaRepository.findByProjectId(projectId);
    }

    @Override
    public void delete(WbsDependency dependency) {
        jpaRepository.delete(dependency);
    }
}
