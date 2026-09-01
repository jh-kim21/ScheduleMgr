package com.projectflow.infrastructure.persistence;

import com.projectflow.domain.RaciAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface RaciAssignmentJpaRepository extends JpaRepository<RaciAssignment, Long> {

    List<RaciAssignment> findByProjectId(Long projectId);
}
