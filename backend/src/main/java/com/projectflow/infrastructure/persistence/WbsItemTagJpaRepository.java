package com.projectflow.infrastructure.persistence;

import com.projectflow.domain.WbsItemTag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

interface WbsItemTagJpaRepository extends JpaRepository<WbsItemTag, WbsItemTag.Key> {

    List<WbsItemTag> findByWbsItemIdIn(Collection<Long> wbsItemIds);

    List<WbsItemTag> findByTagId(Long tagId);
}
