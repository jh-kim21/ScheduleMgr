package com.projectflow.infrastructure.persistence;

import com.projectflow.domain.ProgressSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface ProgressSnapshotJpaRepository extends JpaRepository<ProgressSnapshot, Long> {

    List<ProgressSnapshot> findByProjectIdOrderByAsOfDescIdDesc(Long projectId);
}
