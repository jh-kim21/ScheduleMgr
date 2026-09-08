package com.projectflow.infrastructure.persistence;

import com.projectflow.domain.BacklogItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface BacklogItemJpaRepository extends JpaRepository<BacklogItem, Long> {

    List<BacklogItem> findByProjectId(Long projectId);
}
