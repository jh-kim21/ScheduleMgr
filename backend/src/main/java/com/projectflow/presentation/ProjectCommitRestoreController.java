package com.projectflow.presentation;

import com.projectflow.application.ProjectCommitService;
import com.projectflow.application.dto.ProjectResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Rebuilds a project from a commit.
 *
 * <p>Not nested under {@code /projects/{id}/commits} because it creates a project rather than
 * acting on one — the same reasoning that keeps {@link ImportController} outside
 * {@code /projects/{id}}. Addressed by {@code commitId} rather than {@code (projectId, version)}
 * for the same reason: it names the thing being restored, not a project it does not yet belong to.
 */
@RestController
@RequestMapping("/api/projects/commits")
public class ProjectCommitRestoreController {

    private final ProjectCommitService commitService;

    public ProjectCommitRestoreController(ProjectCommitService commitService) {
        this.commitService = commitService;
    }

    @PostMapping("/{commitId}/restore")
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectResponse restore(@PathVariable Long commitId) {
        return commitService.restore(commitId);
    }
}
