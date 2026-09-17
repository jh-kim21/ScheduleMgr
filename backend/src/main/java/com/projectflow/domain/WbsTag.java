package com.projectflow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 업무 분야 (Service, Web …) — a label a Work Package can carry, project-scoped like
 * {@link ProjectMember}.
 *
 * <p>Project-scoped rather than global for the same reason members are: there is no login, and a
 * label that means something inside one project does not have to mean the same thing in another.
 * Names are unique within a project so two chips are never indistinguishable.
 *
 * <p>The project link is a plain id, like {@link WbsItem}'s — tag work always loads a whole
 * project's tags at once, so a lazy association would only add queries.
 *
 * <p>{@code color} is optional; when it is absent the screen derives one from the name (a hash, so
 * it stays the same every time the row is redrawn).
 */
@Entity
@Table(name = "wbs_tags")
public class WbsTag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(length = 20)
    private String color;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected WbsTag() {
        // JPA
    }

    public WbsTag(Long projectId, String name, String color, int sortOrder) {
        this.projectId = projectId;
        this.name = name;
        this.color = color;
        this.sortOrder = sortOrder;
    }

    public void update(String name, String color, int sortOrder) {
        this.name = name;
        this.color = color;
        this.sortOrder = sortOrder;
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

    public String getColor() {
        return color;
    }

    public int getSortOrder() {
        return sortOrder;
    }
}
