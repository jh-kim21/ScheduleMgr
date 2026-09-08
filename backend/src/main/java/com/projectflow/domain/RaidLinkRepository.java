package com.projectflow.domain;

import java.util.List;

/** Domain-level repository port for RAID links. */
public interface RaidLinkRepository {

    RaidLink save(RaidLink link);

    List<RaidLink> saveAll(List<RaidLink> links);

    /** Every link of a project, in no particular order. */
    List<RaidLink> findByProjectId(Long projectId);

    void deleteAll(List<RaidLink> links);
}
