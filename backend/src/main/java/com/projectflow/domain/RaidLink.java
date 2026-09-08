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
 * One thing a RAID entry is attached to.
 *
 * <p>The same risk can affect several Stories and a Sprint at once; the design is explicit that it
 * stays <em>one</em> entry with several links rather than being copied per target.
 *
 * <p>No foreign key on {@code targetId}: it points at three different tables depending on
 * {@code targetType}. {@code RaidService} checks that the target exists and belongs to the same
 * project (설계 §11.3-7).
 */
@Entity
@Table(name = "raid_links")
public class RaidLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "raid_item_id", nullable = false)
    private Long raidItemId;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 20)
    private RaidLinkTarget targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected RaidLink() {
        // JPA
    }

    public RaidLink(Long projectId, Long raidItemId, RaidLinkTarget targetType, Long targetId) {
        this.projectId = projectId;
        this.raidItemId = raidItemId;
        this.targetType = targetType;
        this.targetId = targetId;
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

    public Long getRaidItemId() {
        return raidItemId;
    }

    public RaidLinkTarget getTargetType() {
        return targetType;
    }

    public Long getTargetId() {
        return targetId;
    }
}
