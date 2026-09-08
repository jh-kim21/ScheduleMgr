package com.projectflow.infrastructure.persistence;

import com.projectflow.domain.Baseline;
import com.projectflow.domain.BaselineItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface BaselineJpaRepository extends JpaRepository<Baseline, Long> {

    List<Baseline> findByProjectIdOrderByVersionAsc(Long projectId);
}

interface BaselineItemJpaRepository extends JpaRepository<BaselineItem, Long> {

    List<BaselineItem> findByBaselineIdOrderByIdAsc(Long baselineId);
}
