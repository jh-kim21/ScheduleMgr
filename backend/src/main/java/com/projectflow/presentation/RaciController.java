package com.projectflow.presentation;

import com.projectflow.application.RaciService;
import com.projectflow.application.dto.RaciAssignmentRequest;
import com.projectflow.application.dto.RaciMatrixResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Like the WBS and Gantt endpoints, every mutation returns the whole matrix: one letter can
 * resolve or create a rule issue on its row, which a partial response would not show.
 */
@RestController
@RequestMapping("/api/projects/{projectId}/raci")
public class RaciController {

    private final RaciService raciService;

    public RaciController(RaciService raciService) {
        this.raciService = raciService;
    }

    @GetMapping
    public RaciMatrixResponse getMatrix(@PathVariable Long projectId) {
        return raciService.getMatrix(projectId);
    }

    @PostMapping("/assignments")
    @ResponseStatus(HttpStatus.CREATED)
    public RaciMatrixResponse assign(@PathVariable Long projectId,
                                      @Valid @RequestBody RaciAssignmentRequest request) {
        return raciService.assign(projectId, request);
    }

    @DeleteMapping("/assignments/{assignmentId}")
    public RaciMatrixResponse unassign(@PathVariable Long projectId, @PathVariable Long assignmentId) {
        return raciService.unassign(projectId, assignmentId);
    }
}
