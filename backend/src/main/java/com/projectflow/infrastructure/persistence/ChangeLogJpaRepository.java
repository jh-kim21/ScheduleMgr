package com.projectflow.infrastructure.persistence;

import com.projectflow.domain.ChangeLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface ChangeLogJpaRepository extends JpaRepository<ChangeLog, Long> {

    List<ChangeLog> findByProjectIdOrderByIdAsc(Long projectId);
}
