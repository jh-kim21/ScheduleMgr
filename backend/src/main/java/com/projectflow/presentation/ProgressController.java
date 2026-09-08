package com.projectflow.presentation;

import com.projectflow.application.ProgressBasisService;
import com.projectflow.application.ProgressService;
import com.projectflow.application.dto.ProgressRequests.BaselineApproveRequest;
import com.projectflow.application.dto.ProgressRequests.CheckpointApprovalRequest;
import com.projectflow.application.dto.ProgressRequests.CheckpointSaveRequest;
import com.projectflow.application.dto.ProgressRequests.SnapshotSaveRequest;
import com.projectflow.application.dto.ProgressResponse;
import com.projectflow.application.dto.SnapshotResponse;
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
 * The common progress figures, and the basis they are computed from.
 *
 * <p>Every mutation returns the whole progress payload. A checkpoint approval moves its Work
 * Package, which moves every summary above it, which moves the project figure — a partial response
 * would leave the screen showing a number that disagrees with the one just changed.
 */
@RestController
@RequestMapping("/api/projects/{projectId}/progress")
public class ProgressController {

    private final ProgressService progressService;
    private final ProgressBasisService basisService;

    public ProgressController(ProgressService progressService, ProgressBasisService basisService) {
        this.progressService = progressService;
        this.basisService = basisService;
    }

    @GetMapping
    public ProgressResponse getProgress(@PathVariable Long projectId) {
        return progressService.getProgress(projectId);
    }

    @PostMapping("/checkpoints")
    @ResponseStatus(HttpStatus.CREATED)
    public ProgressResponse addCheckpoint(@PathVariable Long projectId,
                                           @Valid @RequestBody CheckpointSaveRequest request) {
        return basisService.addCheckpoint(projectId, request);
    }

    @PutMapping("/checkpoints/{checkpointId}")
    public ProgressResponse updateCheckpoint(@PathVariable Long projectId,
                                              @PathVariable Long checkpointId,
                                              @Valid @RequestBody CheckpointSaveRequest request) {
        return basisService.updateCheckpoint(projectId, checkpointId, request);
    }

    /** 승인·승인 취소. Waterfall 진척을 움직이는 사건이라 이력이 함께 남는다. */
    @PutMapping("/checkpoints/{checkpointId}/approval")
    public ProgressResponse setApproval(@PathVariable Long projectId,
                                         @PathVariable Long checkpointId,
                                         @Valid @RequestBody CheckpointApprovalRequest request) {
        return basisService.setCheckpointApproval(projectId, checkpointId, request);
    }

    @DeleteMapping("/checkpoints/{checkpointId}")
    public ProgressResponse deleteCheckpoint(@PathVariable Long projectId,
                                              @PathVariable Long checkpointId) {
        return basisService.deleteCheckpoint(projectId, checkpointId);
    }

    /** 기준선 승인. 명시적 행위이며 자동으로 일어나는 경로는 없다. */
    @PostMapping("/baselines")
    @ResponseStatus(HttpStatus.CREATED)
    public ProgressResponse approveBaseline(@PathVariable Long projectId,
                                             @Valid @RequestBody BaselineApproveRequest request) {
        return basisService.approveBaseline(projectId, request);
    }

    @GetMapping("/snapshots")
    public SnapshotResponse getSnapshots(@PathVariable Long projectId) {
        return basisService.listSnapshots(projectId);
    }

    @PostMapping("/snapshots")
    @ResponseStatus(HttpStatus.CREATED)
    public SnapshotResponse saveSnapshot(@PathVariable Long projectId,
                                          @RequestBody(required = false) SnapshotSaveRequest request) {
        return basisService.saveSnapshot(projectId, request);
    }
}
