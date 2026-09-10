package com.projectflow.presentation;

import com.projectflow.application.WbsImportParser;
import com.projectflow.application.WbsImportRow;
import com.projectflow.application.WbsService;
import com.projectflow.application.dto.WbsItemCreateRequest;
import com.projectflow.application.dto.WbsItemMoveRequest;
import com.projectflow.application.dto.WbsItemUpdateRequest;
import com.projectflow.application.dto.WbsTreeResponse;
import com.projectflow.domain.WbsImportException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;


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

    /**
     * Bulk-adds WBS items from an uploaded Excel/CSV file (레벨·업무명·시작일·종료일·진행률).
     * {@code parentId} omitted means the rows land at the project's top level.
     */
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public WbsTreeResponse importFile(@PathVariable Long projectId,
                                       @RequestParam("file") MultipartFile file,
                                       @RequestParam(required = false) Long parentId) {
        if (file.isEmpty()) {
            throw new WbsImportException("빈 파일입니다.");
        }
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new WbsImportException("파일을 읽을 수 없습니다.");
        }
        List<WbsImportRow> rows = WbsImportParser.parse(file.getOriginalFilename(), content);
        return wbsService.importRows(projectId, parentId, rows);
    }
}
