package com.projectflow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One entry in the project's RAID log (요구사항 9): a risk, an assumption, an issue or an outside
 * dependency. All four live in one table because they share nearly every field and are read as one
 * register — see the V6 migration for why that beats four tables.
 *
 * <p>Exposure and overdue-ness are <em>not</em> stored: they are derived from probability × impact
 * and from the due date against a reference date, the same way WBS codes and delay verdicts are
 * derived rather than saved. See {@link RaidAssessor}.
 *
 * <p>As elsewhere, the project and owner links are plain ids — a project's whole log is loaded at
 * once, so lazy associations would only add queries.
 */
@Entity
@Table(name = "raid_items")
public class RaidItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Enumerated(EnumType.STRING)
    @Column(name = "raid_type", nullable = false, length = 20)
    private RaidType type;

    @Column(nullable = false)
    private String title;

    @Column(length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RaidStatus status;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private RaidLevel probability;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private RaidLevel impact;

    /** A {@link ProjectMember} of the same project, or null when nobody owns it yet. */
    @Column(name = "owner_member_id")
    private Long ownerMemberId;

    // 무엇에 대한 항목인지는 raid_links 가 가진다. 예전에는 wbs_item_id 컬럼 하나였지만, 같은
    // 위험이 여러 Story·Sprint에 걸리는 경우를 한 항목으로 관리하려면 연결이 여러 개여야 한다
    // (설계 §9). 그 컬럼은 V21에서 링크로 옮기고 지웠다.

    /** 대응·확인 기한. Null when there is no date to be late against. */
    @Column(name = "due_date")
    private LocalDate dueDate;

    /** 대응 방안 / 확인 방법 / 해결 방안 — what is being done about it. */
    @Column(length = 2000)
    private String response;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected RaidItem() {
        // JPA
    }

    public RaidItem(Long projectId, RaidType type, String title, String description,
                     RaidStatus status, RaidLevel probability, RaidLevel impact,
                     Long ownerMemberId, LocalDate dueDate, String response) {
        this.projectId = projectId;
        this.type = type;
        this.title = title;
        this.description = description;
        this.status = status;
        this.probability = probability;
        this.impact = impact;
        this.ownerMemberId = ownerMemberId;
        this.dueDate = dueDate;
        this.response = response;
    }

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    /** The type is editable too: an assumption that turns out false often becomes an issue. */
    public void update(RaidType type, String title, String description, RaidStatus status,
                        RaidLevel probability, RaidLevel impact, Long ownerMemberId,
                        LocalDate dueDate, String response) {
        this.type = type;
        this.title = title;
        this.description = description;
        this.status = status;
        this.probability = probability;
        this.impact = impact;
        this.ownerMemberId = ownerMemberId;
        this.dueDate = dueDate;
        this.response = response;
    }

    public Long getId() {
        return id;
    }

    public Long getProjectId() {
        return projectId;
    }

    public RaidType getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public RaidStatus getStatus() {
        return status;
    }

    public RaidLevel getProbability() {
        return probability;
    }

    public RaidLevel getImpact() {
        return impact;
    }

    public Long getOwnerMemberId() {
        return ownerMemberId;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public String getResponse() {
        return response;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
