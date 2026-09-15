package com.projectflow.infrastructure.persistence;

import com.projectflow.domain.ProjectCommit;
import com.projectflow.domain.ProjectCommitRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
class ProjectCommitRepositoryAdapter implements ProjectCommitRepository {

    private final ProjectCommitJpaRepository commits;

    ProjectCommitRepositoryAdapter(ProjectCommitJpaRepository commits) {
        this.commits = commits;
    }

    @Override
    public ProjectCommit save(ProjectCommit commit) {
        return commits.save(commit);
    }

    @Override
    public void delete(ProjectCommit commit) {
        commits.delete(commit);
    }

    @Override
    public List<ProjectCommit> findByProjectId(Long projectId) {
        return commits.findByProjectIdOrderByVersionDesc(projectId);
    }

    @Override
    public Optional<ProjectCommit> findByProjectIdAndVersion(Long projectId, int version) {
        return commits.findByProjectIdAndVersion(projectId, version);
    }

    @Override
    public Optional<ProjectCommit> findById(Long id) {
        return commits.findById(id);
    }

    @Override
    public int findMaxVersion(Long projectId) {
        return commits.findMaxVersion(projectId);
    }

    @Override
    public long sumPayloadBytes(Long projectId) {
        return commits.sumPayloadBytes(projectId);
    }
}
