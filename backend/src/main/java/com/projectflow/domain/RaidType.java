package com.projectflow.domain;

/** The four kinds of RAID entry (요구사항 9.1~9.4). */
public enum RaidType {
    /** 아직 일어나지 않았지만 일어날 수 있는 일 (9.1). */
    RISK,
    /** 사실이라고 전제하고 계획한 것 — 틀리면 계획이 흔들린다 (9.2). */
    ASSUMPTION,
    /** 이미 일어나서 대응이 필요한 일 (9.3). */
    ISSUE,
    /** 프로젝트 밖에서 받아야 하는 것 (9.4). WBS의 선후행 관계와 다르다. */
    DEPENDENCY,
}
