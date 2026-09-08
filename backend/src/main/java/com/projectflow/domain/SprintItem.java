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
 * One Backlog entry's assignment to one Sprint — and, once the Sprint closes, what became of it.
 *
 * <p>The row is the history. Removing an item stamps {@link #removedAt} instead of deleting, and
 * closing stamps {@link #outcome} and {@link #pointsAtClose}. That is what lets an item be carried
 * over without touching what the previous Sprint recorded.
 */
@Entity
@Table(name = "sprint_items")
public class SprintItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "sprint_id", nullable = false)
    private Long sprintId;

    @Column(name = "backlog_item_id", nullable = false)
    private Long backlogItemId;

    @Column(name = "added_at", nullable = false, updatable = false)
    private LocalDateTime addedAt;

    @Column(name = "removed_at")
    private LocalDateTime removedAt;

    @Column(name = "points_at_start")
    private Integer pointsAtStart;

    @Column(name = "points_at_close")
    private Integer pointsAtClose;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private SprintItemOutcome outcome;

    protected SprintItem() {
        // JPA
    }

    public SprintItem(Long projectId, Long sprintId, Long backlogItemId, Integer pointsAtStart) {
        this.projectId = projectId;
        this.sprintId = sprintId;
        this.backlogItemId = backlogItemId;
        this.pointsAtStart = pointsAtStart;
    }

    /** Restores an assignment from an exported file, with whatever the Sprint recorded. */
    public SprintItem(Long projectId, Long sprintId, Long backlogItemId, Integer pointsAtStart,
                       Integer pointsAtClose, SprintItemOutcome outcome,
                       LocalDateTime addedAt, LocalDateTime removedAt) {
        this(projectId, sprintId, backlogItemId, pointsAtStart);
        this.pointsAtClose = pointsAtClose;
        this.outcome = outcome;
        this.addedAt = addedAt;
        this.removedAt = removedAt;
    }

    @PrePersist
    void onCreate() {
        if (addedAt == null) {
            this.addedAt = LocalDateTime.now();
        }
    }

    /** Whether this assignment still counts — i.e. the item is in the Sprint right now. */
    public boolean active() {
        return removedAt == null;
    }

    /**
     * Re-stamps the estimate as the Sprint starts. Planning estimates keep changing until then, and
     * the number worth remembering is the one the team committed to.
     */
    public void stampStartPoints(Integer storyPoint) {
        this.pointsAtStart = storyPoint;
    }

    /** Taken out of the Sprint before it ended: neither completed nor carried over. */
    public void removeFromSprint() {
        this.removedAt = LocalDateTime.now();
        this.outcome = SprintItemOutcome.REMOVED;
    }

    /** Records the Sprint's verdict. Called once, when the Sprint closes. */
    public void settle(SprintItemOutcome outcome, Integer pointsAtClose) {
        this.outcome = outcome;
        this.pointsAtClose = pointsAtClose;
        this.removedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Long getProjectId() {
        return projectId;
    }

    public Long getSprintId() {
        return sprintId;
    }

    public Long getBacklogItemId() {
        return backlogItemId;
    }

    public LocalDateTime getAddedAt() {
        return addedAt;
    }

    public LocalDateTime getRemovedAt() {
        return removedAt;
    }

    public Integer getPointsAtStart() {
        return pointsAtStart;
    }

    public Integer getPointsAtClose() {
        return pointsAtClose;
    }

    public SprintItemOutcome getOutcome() {
        return outcome;
    }
}
