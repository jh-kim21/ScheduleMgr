package com.projectflow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * One letter in one cell of the RACI matrix: this member has this role on this WBS entry
 * (요구사항 7.1).
 *
 * <p>A cell holds a <em>set</em> of letters rather than a single one, so the same person can be
 * both Accountable and Responsible for a task — the common case where the owner also does the
 * work. Forcing one letter per cell would make that task look like it has no Responsible.
 *
 * <p>There is no {@code updatedAt}: a letter is added or removed, never edited in place.
 */
@Entity
@Table(name = "raci_assignments")
public class RaciAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "wbs_item_id", nullable = false)
    private Long wbsItemId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Enumerated(EnumType.STRING)
    @Column(name = "raci_role", nullable = false, length = 20)
    private RaciRole role;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected RaciAssignment() {
        // JPA
    }

    public RaciAssignment(Long projectId, Long wbsItemId, Long memberId, RaciRole role) {
        this.projectId = projectId;
        this.wbsItemId = wbsItemId;
        this.memberId = memberId;
        this.role = role;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Long getProjectId() {
        return projectId;
    }

    public Long getWbsItemId() {
        return wbsItemId;
    }

    public Long getMemberId() {
        return memberId;
    }

    public RaciRole getRole() {
        return role;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
