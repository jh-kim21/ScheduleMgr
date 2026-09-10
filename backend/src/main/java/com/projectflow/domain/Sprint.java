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
 * One Sprint: a goal and a period the team commits to.
 *
 * <p><b>No team column.</b> The project runs a single team (a recorded decision), so a team id
 * would hold the same value on every row. That assumption is also what justifies "at most one
 * ACTIVE Sprint per project" — with one team there is only ever one Sprint in flight.
 */
@Entity
@Table(name = "sprints")
public class Sprint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 1000)
    private String goal;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SprintStatus status;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected Sprint() {
        // JPA
    }

    public Sprint(Long projectId, String name, String goal, LocalDate startDate, LocalDate endDate) {
        this.projectId = projectId;
        this.name = name;
        this.goal = goal;
        this.startDate = startDate;
        this.endDate = endDate;
        this.status = SprintStatus.PLANNED;
    }

    /** Restores a Sprint from an exported file, lifecycle state included. */
    public Sprint(Long projectId, String name, String goal, LocalDate startDate, LocalDate endDate,
                   SprintStatus status, LocalDateTime closedAt) {
        this(projectId, name, goal, startDate, endDate);
        this.status = status;
        this.closedAt = closedAt;
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

    /** Name, goal and period. The lifecycle moves through {@link #start()} and {@link #close()}. */
    public void update(String name, String goal, LocalDate startDate, LocalDate endDate) {
        this.name = name;
        this.goal = goal;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public void start() {
        this.status = SprintStatus.ACTIVE;
    }

    /**
     * Undoes {@link #start()}: back to {@link SprintStatus#PLANNED}. This is a correction, not a
     * close — it carries no {@code closedAt} and settles nothing. Callers (SprintService) are
     * responsible for un-stamping whatever the start stamped (assignments' committed points);
     * this method only resets the Sprint's own state.
     */
    public void cancelStart() {
        this.status = SprintStatus.PLANNED;
    }

    public void close() {
        this.status = SprintStatus.CLOSED;
        this.closedAt = LocalDateTime.now();
    }

    /** Whether items may still be added, removed or moved on the board. */
    public boolean open() {
        return status != SprintStatus.CLOSED;
    }

    public Long getId() {
        return id;
    }

    public Long getProjectId() {
        return projectId;
    }

    public String getName() {
        return name;
    }

    public String getGoal() {
        return goal;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public SprintStatus getStatus() {
        return status;
    }

    public LocalDateTime getClosedAt() {
        return closedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
