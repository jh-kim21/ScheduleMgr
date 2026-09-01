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
 * A person on a project (요구사항 4.2), and therefore a column of the RACI matrix.
 *
 * <p>Members are project-scoped rather than global accounts: there is no login yet, and a name
 * that means something inside one project ("PL", "외주 개발") does not need to be unique across
 * the whole system. Names are unique within a project so matrix columns stay tellable apart.
 *
 * <p>As with {@link WbsItem}, the project link is a plain id — RACI work always loads a whole
 * project's members at once.
 */
@Entity
@Table(name = "project_members")
public class ProjectMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 255)
    private String email;

    /** Job title or role on the project, e.g. "PM", "백엔드". Free text, not a RACI role. */
    @Column(length = 100)
    private String position;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected ProjectMember() {
        // JPA
    }

    public ProjectMember(Long projectId, String name, String email, String position) {
        this.projectId = projectId;
        this.name = name;
        this.email = email;
        this.position = position;
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

    public void update(String name, String email, String position) {
        this.name = name;
        this.email = email;
        this.position = position;
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

    public String getEmail() {
        return email;
    }

    public String getPosition() {
        return position;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
