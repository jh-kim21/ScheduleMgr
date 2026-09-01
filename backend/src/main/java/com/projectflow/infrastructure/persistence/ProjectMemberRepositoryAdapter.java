package com.projectflow.infrastructure.persistence;

import com.projectflow.domain.ProjectMember;
import com.projectflow.domain.ProjectMemberRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
class ProjectMemberRepositoryAdapter implements ProjectMemberRepository {

    private final ProjectMemberJpaRepository jpaRepository;

    ProjectMemberRepositoryAdapter(ProjectMemberJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public ProjectMember save(ProjectMember member) {
        return jpaRepository.save(member);
    }

    @Override
    public Optional<ProjectMember> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public List<ProjectMember> findByProjectId(Long projectId) {
        return jpaRepository.findByProjectId(projectId);
    }

    @Override
    public void delete(ProjectMember member) {
        jpaRepository.delete(member);
    }
}
