package com.projectflow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * A finish-to-start link between two WBS entries (요구사항 6.2): the successor may not start
 * until {@code lagDays} have passed after the predecessor finishes.
 *
 * <p>As with {@link WbsItem}, the endpoints are stored as plain ids — schedule work always loads
 * the project's whole graph at once.
 */
@Entity
@Table(name = "wbs_dependencies")
public class WbsDependency {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "predecessor_id", nullable = false)
    private Long predecessorId;

    @Column(name = "successor_id", nullable = false)
    private Long successorId;

    @Column(name = "lag_days", nullable = false)
    private int lagDays;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected WbsDependency() {
        // JPA
    }

    public WbsDependency(Long projectId, Long predecessorId, Long successorId, int lagDays) {
        this.projectId = projectId;
        this.predecessorId = predecessorId;
        this.successorId = successorId;
        this.lagDays = lagDays;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    /**
     * Retargets and re-lags the link. Callers must have re-run the endpoint checks first — moving
     * an endpoint can introduce a duplicate or a cycle just as adding a link can.
     */
    public void update(Long predecessorId, Long successorId, int lagDays) {
        this.predecessorId = predecessorId;
        this.successorId = successorId;
        this.lagDays = lagDays;
    }

    public Long getId() {
        return id;
    }

    public Long getProjectId() {
        return projectId;
    }

    public Long getPredecessorId() {
        return predecessorId;
    }

    public Long getSuccessorId() {
        return successorId;
    }

    public int getLagDays() {
        return lagDays;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
