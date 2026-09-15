package com.projectflow.presentation;

import com.projectflow.application.ProjectCommitService;
import com.projectflow.application.dto.CommitRequests.CommitCreateRequest;
import com.projectflow.application.dto.CommitResponses.CommitCreateResponse;
import com.projectflow.application.dto.CommitResponses.CommitDetailResponse;
import com.projectflow.application.dto.CommitResponses.CommitListResponse;
import com.projectflow.application.dto.ProjectExportResponse;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

/**
 * Project commit history (docs/tasks/commit-history.md) — explicit, immutable snapshots of a whole
 * project. There is no update endpoint here: a commit can only be created, read or deleted
 * (지시서 §2.3 "커밋 수정: 불가").
 */
@RestController
@RequestMapping("/api/projects/{projectId}/commits")
public class ProjectCommitController {

    private final ProjectCommitService commitService;

    public ProjectCommitController(ProjectCommitService commitService) {
        this.commitService = commitService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CommitCreateResponse createCommit(@PathVariable Long projectId,
                                              @RequestBody(required = false) CommitCreateRequest request) {
        return commitService.createCommit(projectId, request == null ? new CommitCreateRequest(null, null) : request);
    }

    @GetMapping
    public CommitListResponse listCommits(@PathVariable Long projectId) {
        return commitService.listCommits(projectId);
    }

    @GetMapping("/{version}")
    public CommitDetailResponse getCommit(@PathVariable Long projectId, @PathVariable int version) {
        return commitService.getCommit(projectId, version);
    }

    @DeleteMapping("/{version}")
    public CommitListResponse deleteCommit(@PathVariable Long projectId, @PathVariable int version) {
        return commitService.deleteCommit(projectId, version);
    }

    /**
     * The commit's {@code raw_payload} as a download — the same shape {@code GET .../export}
     * produces for the live project, just frozen at this commit's {@code as_of} (지시서 §4.2).
     */
    @GetMapping("/{version}/export")
    public ResponseEntity<ProjectExportResponse> exportCommit(@PathVariable Long projectId,
                                                               @PathVariable int version) {
        ProjectExportResponse body = commitService.exportRawPayload(projectId, version);

        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(commitService.fileNameFor(projectId, version), StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }
}
