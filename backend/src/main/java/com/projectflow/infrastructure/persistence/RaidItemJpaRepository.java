package com.projectflow.infrastructure.persistence;

import com.projectflow.domain.RaidItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface RaidItemJpaRepository extends JpaRepository<RaidItem, Long> {

    List<RaidItem> findByProjectId(Long projectId);
}
