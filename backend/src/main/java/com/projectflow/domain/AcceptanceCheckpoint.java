package com.projectflow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * One approval checkpoint of a Waterfall or Hybrid Work Package — together, these <em>are</em> the
 * denominator of its progress (설계 §6.3).
 *
 * <p>A Work Package with no checkpoints is 산정 전 rather than 0%: there is nothing to measure
 * against yet, which is a different statement from "none of it is done".
 */
@Entity
@Table(name = "acceptance_checkpoints")
public class AcceptanceCheckpoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "wbs_item_id", nullable = false)
    private Long wbsItemId;

    @Column(nullable = false)
    private String title;

    /** {@code null} counts as 1 — an unweighted set is a set of equal weight. */
    private Integer weight;

    @Column(name = "completion_criteria", length = 2000)
    private String completionCriteria;

    @Column(nullable = false)
    private boolean approved;

    /** Free text: there is no login, and an approval with no approver is not an approval. */
    @Column(name = "approved_by", length = 100)
    private String approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected AcceptanceCheckpoint() {
        // JPA
    }

    public AcceptanceCheckpoint(Long projectId, Long wbsItemId, String title, Integer weight,
                                  String completionCriteria, int sortOrder) {
        this.projectId = projectId;
        this.wbsItemId = wbsItemId;
        this.title = title;
        this.weight = weight;
        this.completionCriteria = completionCriteria;
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

    public void update(String title, Integer weight, String completionCriteria) {
        this.title = title;
        this.weight = weight;
        this.completionCriteria = completionCriteria;
    }

    /** Approving records who and when — the two things that make it an approval. */
    public void approve(String approvedBy) {
        this.approved = true;
        this.approvedBy = approvedBy;
        this.approvedAt = LocalDateTime.now();
    }

    /**
     * Withdraws the approval. The approver and time are cleared with it: keeping them would leave
     * a row that looks approved by someone who no longer approves it. The fact that it happened
     * lives in the change log.
     */
    public void revoke() {
        this.approved = false;
        this.approvedBy = null;
        this.approvedAt = null;
    }

    /**
     * Restores an approval from an exported file, keeping who approved it and when.
     *
     * <p>An approval is an event, not a function of the current data — it cannot be re-derived, so
     * an import that reset it would hand over a project that had achieved nothing.
     */
    public void restoreApproval(String approvedBy, LocalDateTime approvedAt) {
        this.approved = true;
        this.approvedBy = approvedBy;
        this.approvedAt = approvedAt;
    }

    public boolean approved() {
        return approved;
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

    public String getTitle() {
        return title;
    }

    public Integer getWeight() {
        return weight;
    }

    public String getCompletionCriteria() {
        return completionCriteria;
    }

    public String getApprovedBy() {
        return approvedBy;
    }

    public LocalDateTime getApprovedAt() {
        return approvedAt;
    }

    public int getSortOrder() {
        return sortOrder;
    }
}
