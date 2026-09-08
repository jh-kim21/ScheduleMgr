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
 * One recorded field change (설계 §11.1의 ChangeLog).
 *
 * <p>This absorbs the two narrow tables Steps 2 and 3 created — execution mode changes and Backlog
 * link changes — and takes the weight and ratio changes Step 5 adds. Four kinds of change did not
 * justify four tables.
 *
 * <p>The target is {@code (entityType, entityId)} with no foreign key. A change to something that
 * was later deleted still has to be readable: explaining "왜 분모가 달라졌나" (설계 §6.1) needs the
 * history of rows that no longer exist.
 *
 * <p>No 변경자 column: there is no login, so it could only ever be null. When accounts arrive this
 * is where the column goes.
 */
@Entity
@Table(name = "change_logs")
public class ChangeLog {

    /** Entity kinds recorded so far. Plain strings, since the set grows with each step. */
    public static final String WBS_ITEM = "WBS_ITEM";
    public static final String BACKLOG_ITEM = "BACKLOG_ITEM";
    public static final String CHECKPOINT = "CHECKPOINT";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "entity_type", nullable = false, length = 40)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    /** One field per row: a serialised whole entity would leave the reader to diff it. */
    @Column(nullable = false, length = 40)
    private String field;

    @Column(name = "before_value", length = 500)
    private String beforeValue;

    @Column(name = "after_value", length = 500)
    private String afterValue;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ChangeReason reason;

    @Column(name = "changed_at", nullable = false, updatable = false)
    private LocalDateTime changedAt;

    protected ChangeLog() {
        // JPA
    }

    public ChangeLog(Long projectId, String entityType, Long entityId, String field,
                      String beforeValue, String afterValue, ChangeReason reason) {
        this.projectId = projectId;
        this.entityType = entityType;
        this.entityId = entityId;
        this.field = field;
        this.beforeValue = beforeValue;
        this.afterValue = afterValue;
        this.reason = reason;
    }

    /** Convenience for the common case: a value that may be null on either side. */
    public static ChangeLog of(Long projectId, String entityType, Long entityId, String field,
                                Object before, Object after, ChangeReason reason) {
        return new ChangeLog(projectId, entityType, entityId, field,
                before == null ? null : String.valueOf(before),
                after == null ? null : String.valueOf(after),
                reason);
    }

    @PrePersist
    void onCreate() {
        this.changedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Long getProjectId() {
        return projectId;
    }

    public String getEntityType() {
        return entityType;
    }

    public Long getEntityId() {
        return entityId;
    }

    public String getField() {
        return field;
    }

    public String getBeforeValue() {
        return beforeValue;
    }

    public String getAfterValue() {
        return afterValue;
    }

    public ChangeReason getReason() {
        return reason;
    }

    public LocalDateTime getChangedAt() {
        return changedAt;
    }
}
