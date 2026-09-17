package com.projectflow.presentation;

import com.projectflow.application.WbsTagService;
import com.projectflow.application.dto.WbsTagRequest;
import com.projectflow.application.dto.WbsTagResponse;
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

import java.util.List;

/**
 * The project's 업무 분야 master list.
 *
 * <p>Nested under {@code /wbs} because that is the only tree these tags label, but it does not
 * collide with {@link WbsController}: {@code /wbs/tags} is one segment where {@code /wbs/{itemId}}
 * expects a number, and {@code /wbs/tags/{tagId}} has a literal where {@code /wbs/{itemId}/move}
 * has one too — no request matches both.
 *
 * <p>Every mutation returns the whole list, so the client never merges rows itself.
 */
@RestController
@RequestMapping("/api/projects/{projectId}/wbs/tags")
public class WbsTagController {

    private final WbsTagService wbsTagService;

    public WbsTagController(WbsTagService wbsTagService) {
        this.wbsTagService = wbsTagService;
    }

    @GetMapping
    public List<WbsTagResponse> listTags(@PathVariable Long projectId) {
        return wbsTagService.listTags(projectId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public List<WbsTagResponse> createTag(@PathVariable Long projectId,
                                            @Valid @RequestBody WbsTagRequest request) {
        return wbsTagService.createTag(projectId, request);
    }

    @PutMapping("/{tagId}")
    public List<WbsTagResponse> updateTag(@PathVariable Long projectId,
                                            @PathVariable Long tagId,
                                            @Valid @RequestBody WbsTagRequest request) {
        return wbsTagService.updateTag(projectId, tagId, request);
    }

    /** Removes the tag and its links; the WBS entries that carried it stay. */
    @DeleteMapping("/{tagId}")
    public List<WbsTagResponse> deleteTag(@PathVariable Long projectId, @PathVariable Long tagId) {
        return wbsTagService.deleteTag(projectId, tagId);
    }
}
