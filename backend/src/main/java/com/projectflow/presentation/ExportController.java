package com.projectflow.presentation;

import com.projectflow.application.ExportService;
import com.projectflow.application.dto.ProjectExportResponse;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

/**
 * Downloads a project as one file (요구사항: 데이터 공유).
 *
 * <p>Served with {@code Content-Disposition: attachment} so a plain link in the UI produces a
 * saved file — no fetch-and-blob code on the client, and it works the same in the installed app
 * as it does behind the dev proxy.
 */
@RestController
@RequestMapping("/api/projects/{projectId}/export")
public class ExportController {

    private final ExportService exportService;

    public ExportController(ExportService exportService) {
        this.exportService = exportService;
    }

    @GetMapping
    public ResponseEntity<ProjectExportResponse> exportProject(@PathVariable Long projectId) {
        ProjectExportResponse body = exportService.exportProject(projectId);

        // The filename is UTF-8 encoded by ContentDisposition, so a Korean project name survives
        // the header instead of arriving as mojibake.
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(exportService.fileNameFor(projectId), StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }
}
