package com.projectflow.presentation;

import com.projectflow.application.ImportService;
import com.projectflow.application.dto.ProjectExportResponse;
import com.projectflow.application.dto.ProjectResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Rebuilds a project from an exported file.
 *
 * <p>Not nested under {@code /projects/{id}} because it creates a project rather than acting on
 * one. The body is exactly what the export endpoint produces — the same type is used both ways, so
 * a round trip cannot drift.
 */
@RestController
@RequestMapping("/api/projects/import")
public class ImportController {

    private final ImportService importService;

    public ImportController(ImportService importService) {
        this.importService = importService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectResponse importProject(@RequestBody ProjectExportResponse file) {
        return importService.importProject(file);
    }
}
