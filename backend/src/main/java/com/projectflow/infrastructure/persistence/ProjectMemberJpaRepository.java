package com.projectflow.infrastructure.persistence;

import com.projectflow.domain.ProjectMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface ProjectMemberJpaRepository extends JpaRepository<ProjectMember, Long> {

    List<ProjectMember> findByProjectId(Long projectId);
}
