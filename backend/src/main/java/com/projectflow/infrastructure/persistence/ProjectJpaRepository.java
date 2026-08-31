package com.projectflow.infrastructure.persistence;

import com.projectflow.domain.Project;
import org.springframework.data.jpa.repository.JpaRepository;

interface ProjectJpaRepository extends JpaRepository<Project, Long> {
}
