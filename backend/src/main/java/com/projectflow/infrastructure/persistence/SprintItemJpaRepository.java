package com.projectflow.infrastructure.persistence;

import com.projectflow.domain.SprintItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface SprintItemJpaRepository extends JpaRepository<SprintItem, Long> {

    List<SprintItem> findByProjectIdOrderByIdAsc(Long projectId);
}
