package com.projectflow.infrastructure.persistence;

import com.projectflow.domain.WbsItemTag;
import com.projectflow.domain.WbsItemTagRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
class WbsItemTagRepositoryAdapter implements WbsItemTagRepository {

    private final WbsItemTagJpaRepository jpaRepository;

    WbsItemTagRepositoryAdapter(WbsItemTagJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public List<WbsItemTag> saveAll(List<WbsItemTag> links) {
        return jpaRepository.saveAll(links);
    }

    @Override
    public List<WbsItemTag> findByWbsItemIdIn(Collection<Long> wbsItemIds) {
        // An empty IN () is not portable SQL, and there is nothing to ask for anyway.
        return wbsItemIds.isEmpty() ? List.of() : jpaRepository.findByWbsItemIdIn(wbsItemIds);
    }

    @Override
    public List<WbsItemTag> findByTagId(Long tagId) {
        return jpaRepository.findByTagId(tagId);
    }

    @Override
    public void deleteAll(List<WbsItemTag> links) {
        jpaRepository.deleteAll(links);
    }
}
