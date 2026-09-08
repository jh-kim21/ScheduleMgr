package com.projectflow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A progress report as it read on one day (설계 §6.5 "과거 보고").
 *
 * <p>Progress itself is never stored — it is recomputed on every read, so it always reflects the
 * current scope and weights. That is right for "지금 어디까지 왔나" and useless for "지난달 보고서의
 * 그 숫자". A past report cannot be recomputed, because the scope, weights and policy it was
 * measured with have since moved. So the number is written down when it is reported.
 *
 * <p>{@code metrics} is a JSON string of project-level figures. Per-item detail is deliberately
 * left out: it would grow without bound on a large project, and the report it has to reproduce is
 * the project-level one.
 */
@Entity
@Table(name = "progress_snapshots")
public class ProgressSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    /** 보고일. Decided by the server, like every other reference date in this app. */
    @Column(name = "as_of", nullable = false)
    private LocalDate asOf;

    /** Which baseline the report compared against; {@code null} when none was approved yet. */
    @Column(name = "baseline_id")
    private Long baselineId;

    @Column(name = "scope_item_count", nullable = false)
    private int scopeItemCount;

    @Column(name = "scope_weight_total")
    private Integer scopeWeightTotal;

    @Column(nullable = false, length = 4000)
    private String metrics;

    @Column(length = 1000)
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected ProgressSnapshot() {
        // JPA
    }

    public ProgressSnapshot(Long projectId, LocalDate asOf, Long baselineId, int scopeItemCount,
                              Integer scopeWeightTotal, String metrics, String note) {
        this.projectId = projectId;
        this.asOf = asOf;
        this.baselineId = baselineId;
        this.scopeItemCount = scopeItemCount;
        this.scopeWeightTotal = scopeWeightTotal;
        this.metrics = metrics;
        this.note = note;
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

    public LocalDate getAsOf() {
        return asOf;
    }

    public Long getBaselineId() {
        return baselineId;
    }

    public int getScopeItemCount() {
        return scopeItemCount;
    }

    public Integer getScopeWeightTotal() {
        return scopeWeightTotal;
    }

    public String getMetrics() {
        return metrics;
    }

    public String getNote() {
        return note;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
