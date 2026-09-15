package com.projectflow.infrastructure.persistence;

import com.projectflow.domain.ProjectCommit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

interface ProjectCommitJpaRepository extends JpaRepository<ProjectCommit, Long> {

    List<ProjectCommit> findByProjectIdOrderByVersionDesc(Long projectId);

    Optional<ProjectCommit> findByProjectIdAndVersion(Long projectId, int version);

    @Query("SELECT COALESCE(MAX(c.version), 0) FROM ProjectCommit c WHERE c.projectId = :projectId")
    int findMaxVersion(@Param("projectId") Long projectId);

    @Query("SELECT COALESCE(SUM(c.payloadBytes), 0) FROM ProjectCommit c WHERE c.projectId = :projectId")
    long sumPayloadBytes(@Param("projectId") Long projectId);
}
