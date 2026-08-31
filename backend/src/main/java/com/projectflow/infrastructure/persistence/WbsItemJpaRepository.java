package com.projectflow.infrastructure.persistence;

import com.projectflow.domain.WbsItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface WbsItemJpaRepository extends JpaRepository<WbsItem, Long> {

    List<WbsItem> findByProjectId(Long projectId);
}
