package com.projectflow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A single WBS entry. The parent link is stored as a plain id rather than a JPA
 * association: WBS operations always load the whole project's items and build the
 * tree in memory ({@link WbsTreeAssembler}), so lazy associations would only add
 * N+1 queries for no benefit.
 *
 * <p>WBS code, summary dates and summary progress are <em>not</em> stored — they are
 * derived from tree position and children on every read, so they can never go stale
 * after a move or reorder.
 */
@Entity
@Table(name = "wbs_items")
public class WbsItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(nullable = false)
    private String name;

    @Column(length = 2000)
    private String description;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(nullable = false)
    private int progress;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected WbsItem() {
        // JPA
    }

    public WbsItem(Long projectId, Long parentId, String name, String description,
                    LocalDate startDate, LocalDate endDate, int progress, int sortOrder) {
        this.projectId = projectId;
        this.parentId = parentId;
        this.name = name;
        this.description = description;
        this.startDate = startDate;
        this.endDate = endDate;
        this.progress = progress;
        this.sortOrder = sortOrder;
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

    /** Updates the item's own attributes; structural fields are changed via {@link #moveTo}. */
    public void update(String name, String description, LocalDate startDate, LocalDate endDate, int progress) {
        this.name = name;
        this.description = description;
        this.startDate = startDate;
        this.endDate = endDate;
        this.progress = progress;
    }

    /**
     * Slides both dates by {@code days}, preserving duration. Used by schedule recalculation
     * (요구사항 6.6); a null date stays null.
     */
    public void shiftBy(long days) {
        if (startDate != null) {
            this.startDate = startDate.plusDays(days);
        }
        if (endDate != null) {
            this.endDate = endDate.plusDays(days);
        }
    }

    public void moveTo(Long parentId, int sortOrder) {
        this.parentId = parentId;
        this.sortOrder = sortOrder;
    }

    public void changeSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public Long getId() {
        return id;
    }

    public Long getProjectId() {
        return projectId;
    }

    public Long getParentId() {
        return parentId;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public int getProgress() {
        return progress;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
