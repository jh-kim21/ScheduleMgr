package com.projectflow.infrastructure.persistence;

import com.projectflow.domain.RaidLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface RaidLinkJpaRepository extends JpaRepository<RaidLink, Long> {

    List<RaidLink> findByProjectId(Long projectId);
}
