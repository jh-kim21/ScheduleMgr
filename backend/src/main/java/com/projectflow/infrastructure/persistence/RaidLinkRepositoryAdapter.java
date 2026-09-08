package com.projectflow.infrastructure.persistence;

import com.projectflow.domain.RaidLink;
import com.projectflow.domain.RaidLinkRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
class RaidLinkRepositoryAdapter implements RaidLinkRepository {

    private final RaidLinkJpaRepository jpaRepository;

    RaidLinkRepositoryAdapter(RaidLinkJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public RaidLink save(RaidLink link) {
        return jpaRepository.save(link);
    }

    @Override
    public List<RaidLink> saveAll(List<RaidLink> links) {
        return jpaRepository.saveAll(links);
    }

    @Override
    public List<RaidLink> findByProjectId(Long projectId) {
        return jpaRepository.findByProjectId(projectId);
    }

    @Override
    public void deleteAll(List<RaidLink> links) {
        jpaRepository.deleteAll(links);
    }
}
