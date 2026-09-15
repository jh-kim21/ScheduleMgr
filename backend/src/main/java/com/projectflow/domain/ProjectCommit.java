package com.projectflow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A "박제" of one project moment (docs/tasks/commit-history.md), taken explicitly by a person —
 * the same sense as a git commit, not an autosave.
 *
 * <p><b>Immutable.</b> There is no update method here on purpose: a commit can be deleted, but
 * never edited (지시서 §2.3 "커밋 수정: 불가"). Anything that looks like "fixing" a commit is a
 * new commit.
 *
 * <p><b>Two payloads, two jobs.</b> {@code rawPayload} is exactly what {@code ExportService}
 * produces — stored state only, no judged values — and exists so this commit can be handed to
 * {@code ImportService} to build a new project (restore). {@code computedPayload} is the opposite:
 * every screen response this commit captured, judged values included, so viewing an old commit
 * shows the numbers as they actually read that day rather than recomputed with today's logic
 * (지시서 §2.1 — the same reasoning as {@code progress_snapshots.metrics}).
 *
 * <p>Both are {@code TEXT}, not {@code CLOB}: PostgreSQL has no {@code CLOB} type, and Hibernate 6
 * maps a {@code @Lob String} to PostgreSQL's {@code oid} (large object), which is not what a plain
 * JSON string needs. {@link JdbcTypeCode} with {@link SqlTypes#LONGVARCHAR} keeps both columns
 * ordinary text on both H2 and PostgreSQL.
 */
@Entity
@Table(name = "project_commits")
public class ProjectCommit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    /** 1-based within the project, from {@code MAX(version) + 1} — never {@code count() + 1}
     * (지시서 §3.3), because deletion would let two commits collide on the same reused number. */
    @Column(nullable = false)
    private int version;

    /** The single reference date every captured screen in this commit was judged against. */
    @Column(name = "as_of", nullable = false)
    private LocalDate asOf;

    @Column(name = "committed_at", nullable = false, updatable = false)
    private LocalDateTime committedAt;

    /** Free text: there is no login, and an unattributed commit is still a valid commit here. */
    @Column(name = "committed_by", length = 100)
    private String committedBy;

    @Column(length = 1000)
    private String message;

    /** {@code ExportService.FORMAT_VERSION} at the time this commit was taken. */
    @Column(name = "format_version", nullable = false)
    private int formatVersion;

    /** {@code rawPayload} + {@code computedPayload} byte length, cached so capacity checks do not
     * re-scan every TEXT column of every commit on each write. */
    @Column(name = "payload_bytes", nullable = false)
    private long payloadBytes;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "raw_payload", nullable = false)
    private String rawPayload;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "computed_payload", nullable = false)
    private String computedPayload;

    protected ProjectCommit() {
        // JPA
    }

    public ProjectCommit(Long projectId, int version, LocalDate asOf, String committedBy, String message,
                          int formatVersion, long payloadBytes, String rawPayload, String computedPayload) {
        this.projectId = projectId;
        this.version = version;
        this.asOf = asOf;
        this.committedBy = committedBy;
        this.message = message;
        this.formatVersion = formatVersion;
        this.payloadBytes = payloadBytes;
        this.rawPayload = rawPayload;
        this.computedPayload = computedPayload;
    }

    @PrePersist
    void onCreate() {
        if (committedAt == null) {
            this.committedAt = LocalDateTime.now();
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

    public LocalDate getAsOf() {
        return asOf;
    }

    public LocalDateTime getCommittedAt() {
        return committedAt;
    }

    public String getCommittedBy() {
        return committedBy;
    }

    public String getMessage() {
        return message;
    }

    public int getFormatVersion() {
        return formatVersion;
    }

    public long getPayloadBytes() {
        return payloadBytes;
    }

    public String getRawPayload() {
        return rawPayload;
    }

    public String getComputedPayload() {
        return computedPayload;
    }
}
