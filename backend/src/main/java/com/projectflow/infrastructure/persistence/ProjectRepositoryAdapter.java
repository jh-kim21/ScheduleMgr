package com.projectflow.infrastructure.persistence;

import com.projectflow.domain.Project;
import com.projectflow.domain.ProjectRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
class ProjectRepositoryAdapter implements ProjectRepository {

    private final ProjectJpaRepository jpaRepository;

    ProjectRepositoryAdapter(ProjectJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Project save(Project project) {
        return jpaRepository.save(project);
    }

    @Override
    public Optional<Project> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public List<Project> findAll() {
        return jpaRepository.findAll();
    }

    @Override
    public void deleteById(Long id) {
        jpaRepository.deleteById(id);
    }

    @Override
    public boolean existsById(Long id) {
        return jpaRepository.existsById(id);
    }
}
