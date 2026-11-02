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
 * One entry in a project's TODO list: a chore that is neither a WBS deliverable, a Backlog item nor
 * a RAID entry. See the V25 migration.
 *
 * <p>Completion is completedAt != null; there is no separate flag that could disagree with it.
 * Overdue-ness is derived (TodoAssessor), never stored. Project and assignee are plain ids.
 */
@Entity
@Table(name = "todo_items")
public class TodoItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TodoPriority priority;

    /** A ProjectMember of the same project, or null when unassigned. */
    @Column(name = "assignee_member_id")
    private Long assigneeMemberId;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(length = 2000)
    private String memo;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected TodoItem() {
        // JPA
    }

    public TodoItem(Long projectId, String title, TodoPriority priority, Long assigneeMemberId,
                     LocalDate dueDate, String memo, LocalDateTime completedAt) {
        this.projectId = projectId;
        this.title = title;
        this.priority = priority;
        this.assigneeMemberId = assigneeMemberId;
        this.dueDate = dueDate;
        this.memo = memo;
        this.completedAt = completedAt;
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

    /** Completion is deliberately not editable here; it has its own narrow path. */
    public void update(String title, TodoPriority priority, Long assigneeMemberId,
                        LocalDate dueDate, String memo) {
        this.title = title;
        this.priority = priority;
        this.assigneeMemberId = assigneeMemberId;
        this.dueDate = dueDate;
        this.memo = memo;
    }

    /**
     * Completing an already-completed item keeps the original time: saving again must not move
     * when it was finished to today. Reopening clears it.
     */
    public void setCompleted(boolean completed, LocalDateTime now) {
        if (completed) {
            if (this.completedAt == null) {
                this.completedAt = now;
            }
        } else {
            this.completedAt = null;
        }
    }

    public Long getId() {
        return id;
    }

    public Long getProjectId() {
        return projectId;
    }

    public String getTitle() {
        return title;
    }

    public TodoPriority getPriority() {
        return priority;
    }

    public Long getAssigneeMemberId() {
        return assigneeMemberId;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public String getMemo() {
        return memo;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
