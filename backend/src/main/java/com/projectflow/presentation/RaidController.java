package com.projectflow.presentation;

import com.projectflow.application.RaidService;
import com.projectflow.application.dto.RaidItemRequest;
import com.projectflow.application.dto.RaidLogResponse;
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
 * Every mutation returns the whole log, because overdue-ness is judged against the payload's
 * {@code referenceDate} and the client must not substitute its own clock.
 */
@RestController
@RequestMapping("/api/projects/{projectId}/raid")
public class RaidController {

    private final RaidService raidService;

    public RaidController(RaidService raidService) {
        this.raidService = raidService;
    }

    @GetMapping
    public RaidLogResponse getLog(@PathVariable Long projectId) {
        return raidService.getLog(projectId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RaidLogResponse addItem(@PathVariable Long projectId,
                                    @Valid @RequestBody RaidItemRequest request) {
        return raidService.addItem(projectId, request);
    }

    @PutMapping("/{itemId}")
    public RaidLogResponse updateItem(@PathVariable Long projectId,
                                       @PathVariable Long itemId,
                                       @Valid @RequestBody RaidItemRequest request) {
        return raidService.updateItem(projectId, itemId, request);
    }

    @DeleteMapping("/{itemId}")
    public RaidLogResponse deleteItem(@PathVariable Long projectId, @PathVariable Long itemId) {
        return raidService.deleteItem(projectId, itemId);
    }
}
