package com.projectflow.presentation;

import com.projectflow.application.WbsService;
import com.projectflow.application.dto.WbsItemCreateRequest;
import com.projectflow.application.dto.WbsItemMoveRequest;
import com.projectflow.application.dto.WbsItemUpdateRequest;
import com.projectflow.application.dto.WbsTreeResponse;
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
 * Every mutating endpoint returns the whole rebuilt tree: WBS codes and summary rollups shift
 * across unrelated rows on any structural change, so a partial response would leave the client
 * with stale codes.
 */
@RestController
@RequestMapping("/api/projects/{projectId}/wbs")
public class WbsController {

    private final WbsService wbsService;

    public WbsController(WbsService wbsService) {
        this.wbsService = wbsService;
    }

    @GetMapping
    public WbsTreeResponse getTree(@PathVariable Long projectId) {
        return wbsService.getTree(projectId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WbsTreeResponse createItem(@PathVariable Long projectId,
                                            @Valid @RequestBody WbsItemCreateRequest request) {
        return wbsService.createItem(projectId, request);
    }

    @PutMapping("/{itemId}")
    public WbsTreeResponse updateItem(@PathVariable Long projectId,
                                            @PathVariable Long itemId,
                                            @Valid @RequestBody WbsItemUpdateRequest request) {
        return wbsService.updateItem(projectId, itemId, request);
    }

    /** Drag &amp; drop: re-parent and/or reorder an entry. */
    @PutMapping("/{itemId}/move")
    public WbsTreeResponse moveItem(@PathVariable Long projectId,
                                          @PathVariable Long itemId,
                                          @Valid @RequestBody WbsItemMoveRequest request) {
        return wbsService.moveItem(projectId, itemId, request);
    }

    /** Deletes the entry together with everything beneath it. */
    @DeleteMapping("/{itemId}")
    public WbsTreeResponse deleteItem(@PathVariable Long projectId, @PathVariable Long itemId) {
        wbsService.deleteItem(projectId, itemId);
        return wbsService.getTree(projectId);
    }
}
