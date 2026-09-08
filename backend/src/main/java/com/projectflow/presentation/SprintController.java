package com.projectflow.presentation;

import com.projectflow.application.SprintService;
import com.projectflow.application.dto.SprintRequests.BoardMoveRequest;
import com.projectflow.application.dto.SprintRequests.SprintAssignRequest;
import com.projectflow.application.dto.SprintRequests.SprintCloseRequest;
import com.projectflow.application.dto.SprintRequests.SprintSaveRequest;
import com.projectflow.application.dto.SprintResponse;
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
 * Sprint planning and Board execution.
 *
 * <p>Every mutation returns every Sprint of the project. Closing with carry-over changes two of
 * them at once, and whether a Sprint may be started depends on whether another is running — a
 * per-Sprint response would leave the client holding contradictory buttons.
 */
@RestController
@RequestMapping("/api/projects/{projectId}/sprints")
public class SprintController {

    private final SprintService sprintService;

    public SprintController(SprintService sprintService) {
        this.sprintService = sprintService;
    }

    @GetMapping
    public SprintResponse getSprints(@PathVariable Long projectId) {
        return sprintService.getSprints(projectId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SprintResponse create(@PathVariable Long projectId,
                                  @Valid @RequestBody SprintSaveRequest request) {
        return sprintService.create(projectId, request);
    }

    @PutMapping("/{sprintId}")
    public SprintResponse update(@PathVariable Long projectId,
                                  @PathVariable Long sprintId,
                                  @Valid @RequestBody SprintSaveRequest request) {
        return sprintService.update(projectId, sprintId, request);
    }

    @DeleteMapping("/{sprintId}")
    public SprintResponse delete(@PathVariable Long projectId, @PathVariable Long sprintId) {
        return sprintService.delete(projectId, sprintId);
    }

    @PostMapping("/{sprintId}/start")
    public SprintResponse start(@PathVariable Long projectId, @PathVariable Long sprintId) {
        return sprintService.start(projectId, sprintId);
    }

    /** 종료. 본문의 {@code carryOverToSprintId}로 미완료 항목을 곧바로 재배정할 수 있다. */
    @PostMapping("/{sprintId}/close")
    public SprintResponse close(@PathVariable Long projectId,
                                 @PathVariable Long sprintId,
                                 @RequestBody(required = false) SprintCloseRequest request) {
        return sprintService.close(projectId, sprintId, request);
    }

    @PostMapping("/{sprintId}/items")
    public SprintResponse assign(@PathVariable Long projectId,
                                  @PathVariable Long sprintId,
                                  @Valid @RequestBody SprintAssignRequest request) {
        return sprintService.assign(projectId, sprintId, request);
    }

    @DeleteMapping("/{sprintId}/items/{backlogItemId}")
    public SprintResponse unassign(@PathVariable Long projectId,
                                    @PathVariable Long sprintId,
                                    @PathVariable Long backlogItemId) {
        return sprintService.unassign(projectId, sprintId, backlogItemId);
    }

    /**
     * One Board move. Lives under the Sprint rather than under the Backlog entry because the Board
     * is what the caller is looking at, and the response it needs back is the Sprint.
     */
    @PutMapping("/{sprintId}/board/{backlogItemId}")
    public SprintResponse move(@PathVariable Long projectId,
                                @PathVariable Long sprintId,
                                @PathVariable Long backlogItemId,
                                @Valid @RequestBody BoardMoveRequest request) {
        return sprintService.move(projectId, sprintId, backlogItemId, request);
    }
}
