package com.projectflow.domain;

public class ProjectMemberNotFoundException extends RuntimeException {

    public ProjectMemberNotFoundException(Long id) {
        super("Project member not found: id=" + id);
    }
}
