package com.projectflow.infrastructure.persistence;

import com.projectflow.domain.AcceptanceCheckpoint;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface AcceptanceCheckpointJpaRepository extends JpaRepository<AcceptanceCheckpoint, Long> {

    List<AcceptanceCheckpoint> findByProjectId(Long projectId);
}
