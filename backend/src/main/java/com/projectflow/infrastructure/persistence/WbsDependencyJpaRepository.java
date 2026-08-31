package com.projectflow.infrastructure.persistence;

import com.projectflow.domain.WbsDependency;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface WbsDependencyJpaRepository extends JpaRepository<WbsDependency, Long> {

    List<WbsDependency> findByProjectId(Long projectId);
}
