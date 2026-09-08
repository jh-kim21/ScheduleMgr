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
 * An approved baseline: the scope, schedule, weights and completion criteria as they stood when
 * someone signed off (설계 §11.1).
 *
 * <p><b>Never created automatically.</b> A baseline nobody approved cannot serve as the thing
 * actuals are compared against, and the instruction is explicit about not inventing one.
 *
 * <p>The items are <em>copied</em> rather than referenced ({@link BaselineItem}). A reference would
 * follow the plan as it changes, which is precisely what a baseline exists not to do.
 */
@Entity
@Table(name = "baselines")
public class Baseline {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    /** 1-based within the project, so people can say "2차 기준". */
    @Column(nullable = false)
    private int version;

    /** Free text: there is no login, and an approval with no approver is not an approval. */
    @Column(name = "approved_by", nullable = false, length = 100)
    private String approvedBy;

    @Column(name = "approved_at", nullable = false, updatable = false)
    private LocalDateTime approvedAt;

    @Column(length = 1000)
    private String note;

    protected Baseline() {
        // JPA
    }

    public Baseline(Long projectId, int version, String approvedBy, String note) {
        this.projectId = projectId;
        this.version = version;
        this.approvedBy = approvedBy;
        this.note = note;
    }

    /** Restores a baseline from an exported file, keeping when it was approved. */
    public Baseline(Long projectId, int version, String approvedBy, String note,
                     LocalDateTime approvedAt) {
        this(projectId, version, approvedBy, note);
        this.approvedAt = approvedAt;
    }

    @PrePersist
    void onCreate() {
        if (approvedAt == null) {
            this.approvedAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public Long getProjectId() {
        return projectId;
    }

    public int getVersion() {
        return version;
    }

    public String getApprovedBy() {
        return approvedBy;
    }

    public LocalDateTime getApprovedAt() {
        return approvedAt;
    }

    public String getNote() {
        return note;
    }
}
