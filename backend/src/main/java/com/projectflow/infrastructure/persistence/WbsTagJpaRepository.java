package com.projectflow.infrastructure.persistence;

import com.projectflow.domain.WbsTag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface WbsTagJpaRepository extends JpaRepository<WbsTag, Long> {

    List<WbsTag> findByProjectId(Long projectId);
}
