package com.projectflow.presentation;

import com.projectflow.application.BacklogService;
import com.projectflow.application.dto.BacklogArchiveRequest;
import com.projectflow.application.dto.BacklogItemRequest;
import com.projectflow.application.dto.BacklogResponse;
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
 * Every mutation returns the whole backlog: re-attaching an Epic moves everything beneath it, and
 * each row's warnings depend on the Work Package it points at, so a partial response would leave
 * untouched rows showing stale links.
 */
@RestController
@RequestMapping("/api/projects/{projectId}/backlog")
public class BacklogController {

    private final BacklogService backlogService;

    public BacklogController(BacklogService backlogService) {
        this.backlogService = backlogService;
    }

    @GetMapping
    public BacklogResponse getBacklog(@PathVariable Long projectId) {
        return backlogService.getBacklog(projectId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BacklogResponse addItem(@PathVariable Long projectId,
                                    @Valid @RequestBody BacklogItemRequest request) {
        return backlogService.addItem(projectId, request);
    }

    @PutMapping("/{itemId}")
    public BacklogResponse updateItem(@PathVariable Long projectId,
                                       @PathVariable Long itemId,
                                       @Valid @RequestBody BacklogItemRequest request) {
        return backlogService.updateItem(projectId, itemId, request);
    }

    /** 보관·복구. Separate from the update endpoint so putting an entry aside is one click. */
    @PutMapping("/{itemId}/archive")
    public BacklogResponse setArchived(@PathVariable Long projectId,
                                        @PathVariable Long itemId,
                                        @Valid @RequestBody BacklogArchiveRequest request) {
        return backlogService.setArchived(projectId, itemId, request.archived());
    }

    @DeleteMapping("/{itemId}")
    public BacklogResponse deleteItem(@PathVariable Long projectId, @PathVariable Long itemId) {
        return backlogService.deleteItem(projectId, itemId);
    }
}
