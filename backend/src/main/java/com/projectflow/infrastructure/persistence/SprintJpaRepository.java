package com.projectflow.infrastructure.persistence;

import com.projectflow.domain.Sprint;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface SprintJpaRepository extends JpaRepository<Sprint, Long> {

    List<Sprint> findByProjectId(Long projectId);
}
