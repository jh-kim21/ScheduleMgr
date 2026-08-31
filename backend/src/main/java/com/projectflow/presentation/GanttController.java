package com.projectflow.presentation;

import com.projectflow.application.GanttService;
import com.projectflow.application.dto.DependencyCreateRequest;
import com.projectflow.application.dto.DependencyUpdateRequest;
import com.projectflow.application.dto.GanttResponse;
import com.projectflow.application.dto.ScheduleRecalculationResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Like the WBS endpoints, every mutation returns the whole chart: adding a dependency can change
 * which other rows are flagged as violating their constraints.
 */
@RestController
@RequestMapping("/api/projects/{projectId}/gantt")
public class GanttController {

    private final GanttService ganttService;

    public GanttController(GanttService ganttService) {
        this.ganttService = ganttService;
    }

    @GetMapping
    public GanttResponse getGantt(@PathVariable Long projectId) {
        return ganttService.getGantt(projectId);
    }

    @PostMapping("/dependencies")
    @ResponseStatus(HttpStatus.CREATED)
    public GanttResponse addDependency(@PathVariable Long projectId,
                                        @Valid @RequestBody DependencyCreateRequest request) {
        return ganttService.addDependency(projectId, request);
    }

    @PutMapping("/dependencies/{dependencyId}")
    public GanttResponse updateDependency(@PathVariable Long projectId,
                                           @PathVariable Long dependencyId,
                                           @Valid @RequestBody DependencyUpdateRequest request) {
        return ganttService.updateDependency(projectId, dependencyId, request);
    }

    @DeleteMapping("/dependencies/{dependencyId}")
    public GanttResponse deleteDependency(@PathVariable Long projectId, @PathVariable Long dependencyId) {
        return ganttService.deleteDependency(projectId, dependencyId);
    }

    /** Shifts violating tasks so every finish-to-start constraint holds (요구사항 6.6). */
    @PostMapping("/recalculate")
    public ScheduleRecalculationResponse recalculate(@PathVariable Long projectId) {
        return ganttService.recalculate(projectId);
    }
}
