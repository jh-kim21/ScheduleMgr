package com.projectflow.infrastructure.persistence;

import com.projectflow.domain.WbsTag;
import com.projectflow.domain.WbsTagRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
class WbsTagRepositoryAdapter implements WbsTagRepository {

    private final WbsTagJpaRepository jpaRepository;

    WbsTagRepositoryAdapter(WbsTagJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public WbsTag save(WbsTag tag) {
        return jpaRepository.save(tag);
    }

    @Override
    public List<WbsTag> findByProjectId(Long projectId) {
        return jpaRepository.findByProjectId(projectId);
    }

    @Override
    public void delete(WbsTag tag) {
        jpaRepository.delete(tag);
    }
}
