package com.projectflow.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectflow.application.dto.BacklogResponse;
import com.projectflow.application.dto.CommitRequests.CommitCreateRequest;
import com.projectflow.application.dto.CommitResponses.CommitCapacityResponse;
import com.projectflow.application.dto.CommitResponses.CommitComputedPayload;
import com.projectflow.application.dto.CommitResponses.CommitCreateResponse;
import com.projectflow.application.dto.CommitResponses.CommitDetailResponse;
import com.projectflow.application.dto.CommitResponses.CommitListResponse;
import com.projectflow.application.dto.CommitResponses.CommitSummaryResponse;
import com.projectflow.application.dto.DashboardResponse;
import com.projectflow.application.dto.GanttResponse;
import com.projectflow.application.dto.ProgressResponse;
import com.projectflow.application.dto.ProjectExportResponse;
import com.projectflow.application.dto.ProjectExportResponse.ExportedProject;
import com.projectflow.application.dto.ProjectMemberResponse;
import com.projectflow.application.dto.ProjectResponse;
import com.projectflow.application.dto.RaciMatrixResponse;
import com.projectflow.application.dto.RaidLogResponse;
import com.projectflow.application.dto.SnapshotResponse;
import com.projectflow.application.dto.SprintResponse;
import com.projectflow.application.dto.WbsTreeResponse;
import com.projectflow.domain.CommitCapacityExceededException;
import com.projectflow.domain.CommitNotFoundException;
import com.projectflow.domain.Project;
import com.projectflow.domain.ProjectCommit;
import com.projectflow.domain.ProjectCommitRepository;
import com.projectflow.domain.ProjectNotFoundException;
import com.projectflow.domain.ProjectRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

/**
 * Explicit, point-in-time snapshots of a whole project — a "커밋" in the git sense, never taken
 * automatically (docs/tasks/commit-history.md §2.3).
 *
 * <p><b>This service computes nothing new.</b> Like {@link DashboardService}, it calls the
 * services that already own each screen's response and stores what they return; the one thing it
 * adds is a single reference date shared by every one of those calls, and the two payloads that
 * make a commit different from those services' normal responses:
 *
 * <ul>
 *   <li>{@code rawPayload} — exactly {@link ExportService}'s output (stored state only, no judged
 *       values), kept so a commit can be handed to {@link ImportService} to build a new project.</li>
 *   <li>{@code computedPayload} — every captured screen's response as it read that day, judged
 *       values included and never recomputed later (§2.1 — the same reasoning {@code
 *       progress_snapshots.metrics} already relies on).</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class ProjectCommitService {

    private final ProjectCommitRepository commitRepository;
    private final ProjectRepository projectRepository;
    private final ExportService exportService;
    private final ImportService importService;
    private final WbsService wbsService;
    private final GanttService ganttService;
    private final ProjectMemberService memberService;
    private final RaciService raciService;
    private final RaidService raidService;
    private final BacklogService backlogService;
    private final SprintService sprintService;
    private final ProgressService progressService;
    private final ProgressBasisService progressBasisService;
    private final DashboardService dashboardService;
    private final ObjectMapper objectMapper;
    private final long maxBytesPerProject;

    public ProjectCommitService(ProjectCommitRepository commitRepository,
                                 ProjectRepository projectRepository,
                                 ExportService exportService,
                                 ImportService importService,
                                 WbsService wbsService,
                                 GanttService ganttService,
                                 ProjectMemberService memberService,
                                 RaciService raciService,
                                 RaidService raidService,
                                 BacklogService backlogService,
                                 SprintService sprintService,
                                 ProgressService progressService,
                                 ProgressBasisService progressBasisService,
                                 DashboardService dashboardService,
                                 ObjectMapper objectMapper,
                                 @Value("${project-flow.commit.max-bytes-per-project:1073741824}")
                                 long maxBytesPerProject) {
        this.commitRepository = commitRepository;
        this.projectRepository = projectRepository;
        this.exportService = exportService;
        this.importService = importService;
        this.wbsService = wbsService;
        this.ganttService = ganttService;
        this.memberService = memberService;
        this.raciService = raciService;
        this.raidService = raidService;
        this.backlogService = backlogService;
        this.sprintService = sprintService;
        this.progressService = progressService;
        this.progressBasisService = progressBasisService;
        this.dashboardService = dashboardService;
        this.objectMapper = objectMapper;
        this.maxBytesPerProject = maxBytesPerProject;
    }

    /**
     * Captures the current moment as a new commit.
     *
     * <p>Never automatic (§2.3) — this is only ever called from the explicit "＋ 현재 시점 커밋"
     * action. The capacity gate runs <em>before</em> the version is assigned or anything is saved,
     * so a rejected commit leaves the sequence exactly as it was.
     */
    @Transactional
    public CommitCreateResponse createCommit(Long projectId, CommitCreateRequest request) {
        requireProject(projectId);
        // One reference date for every call below (§3.5) — committing just before midnight must not
        // let the WBS, Gantt, Progress and RAID sections disagree about what "오늘" was.
        LocalDate asOf = LocalDate.now();

        ProjectExportResponse rawPayload = exportService.exportProject(projectId);
        CommitComputedPayload computedPayload = capture(projectId, asOf);

        String rawJson = writeJson(rawPayload);
        String computedJson = writeJson(computedPayload);
        long payloadBytes = utf8Length(rawJson) + utf8Length(computedJson);

        long usedBytes = commitRepository.sumPayloadBytes(projectId);
        long projectedTotal = usedBytes + payloadBytes;
        if (projectedTotal > maxBytesPerProject) {
            // 자동 삭제는 하지 않는다(§2.3) — 무엇을 지울지는 화면이 이 목록을 보여주고 사람이 고른다.
            throw new CommitCapacityExceededException(
                    "커밋 용량을 초과했습니다 (프로젝트별 최대 %.0fMB). 기존 커밋을 삭제한 뒤 다시 시도하세요."
                            .formatted(maxBytesPerProject / (1024.0 * 1024)),
                    usedBytes, maxBytesPerProject, commitRepository.findByProjectId(projectId));
        }

        // MAX(version)+1, never size()+1 (§3.3) — 삭제된 번호는 재사용하지 않는다.
        int nextVersion = commitRepository.findMaxVersion(projectId) + 1;
        ProjectCommit saved = commitRepository.save(new ProjectCommit(
                projectId,
                nextVersion,
                asOf,
                blankToNull(request.committedBy()),
                blankToNull(request.message()),
                // ExportService.FORMAT_VERSION 그 자체 — export 응답에 실려 오므로 상수를 복제하지 않는다.
                rawPayload.formatVersion(),
                payloadBytes,
                rawJson,
                computedJson));

        return new CommitCreateResponse(
                CommitSummaryResponse.from(saved),
                CommitCapacityResponse.of(projectedTotal, maxBytesPerProject));
    }

    public CommitListResponse listCommits(Long projectId) {
        requireProject(projectId);
        return buildList(projectId);
    }

    public CommitDetailResponse getCommit(Long projectId, int version) {
        requireProject(projectId);
        ProjectCommit commit = requireCommit(projectId, version);
        return new CommitDetailResponse(CommitSummaryResponse.from(commit), readComputedPayload(commit));
    }

    @Transactional
    public CommitListResponse deleteCommit(Long projectId, int version) {
        requireProject(projectId);
        ProjectCommit commit = requireCommit(projectId, version);
        commitRepository.delete(commit);
        return buildList(projectId);
    }

    /** The commit's {@code raw_payload}, exactly as {@code ExportService} produced it that day. */
    public ProjectExportResponse exportRawPayload(Long projectId, int version) {
        requireProject(projectId);
        return readRawPayload(requireCommit(projectId, version));
    }

    /** File name for {@link #exportRawPayload}'s download, mirroring {@code ExportService.fileNameFor}. */
    public String fileNameFor(Long projectId, int version) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ProjectNotFoundException(projectId));
        ProjectCommit commit = requireCommit(projectId, version);
        return "%s-v%d-%s.json".formatted(safeName(project.getName()), commit.getVersion(), commit.getAsOf());
    }

    /**
     * Rebuilds the commit's {@code raw_payload} as a brand-new project (지시서 §4.5, §2.3).
     *
     * <p>Delegates entirely to {@link ImportService}, which already makes a new project and
     * remaps every id — the only addition here is the "(vN 복원)" name so the restored copy does
     * not read as an ordinary import of the same file. There is no path that overwrites the current
     * project: a commit only ever produces a new one.
     *
     * <p>Addressed by {@code commitId} rather than {@code (projectId, version)} — the endpoint is
     * {@code POST /api/projects/commits/{commitId}/restore}, not nested under a project, because
     * restoring does not act on an existing project.
     */
    @Transactional
    public ProjectResponse restore(Long commitId) {
        ProjectCommit commit = commitRepository.findById(commitId)
                .orElseThrow(() -> new CommitNotFoundException(commitId));
        ProjectExportResponse original = readRawPayload(commit);
        ExportedProject renamed = renameForRestore(original.project(), commit.getVersion());
        ProjectExportResponse toImport = new ProjectExportResponse(
                original.formatVersion(),
                original.exportedAt(),
                renamed,
                original.members(),
                original.wbsItems(),
                original.dependencies(),
                original.raciAssignments(),
                original.raidItems(),
                original.backlogItems(),
                original.sprints(),
                original.sprintItems(),
                original.checkpoints(),
                original.baselines(),
                original.snapshots(),
                original.tags());
        return importService.importProject(toImport);
    }

    // ------------------------------------------------------------------ 캡처

    /**
     * Every screen's response, all judged against the same {@code asOf} (§3.5).
     *
     * <p>Calls the existing query services exactly as their controllers do — nothing here is
     * recomputed or read from a repository directly (§3.6). The dashboard is captured through its
     * own {@code referenceDate} overload rather than {@code getDashboard(projectId)}, because that
     * overload would otherwise call {@code ProgressService}/{@code GanttService}/{@code RaidService}
     * with {@code LocalDate.now()} again and the dashboard section could disagree with the rest of
     * this same commit.
     */
    private CommitComputedPayload capture(Long projectId, LocalDate asOf) {
        WbsTreeResponse wbs = wbsService.getTree(projectId, asOf);
        GanttResponse gantt = ganttService.getGantt(projectId, asOf);
        List<ProjectMemberResponse> members = memberService.listMembers(projectId);
        RaciMatrixResponse raci = raciService.getMatrix(projectId);
        RaidLogResponse raid = raidService.getLog(projectId, asOf);
        BacklogResponse backlog = backlogService.getBacklog(projectId);
        SprintResponse sprints = sprintService.getSprints(projectId);
        ProgressResponse progress = progressService.getProgress(projectId, asOf);
        SnapshotResponse snapshots = progressBasisService.listSnapshots(projectId);
        DashboardResponse dashboard = dashboardService.getDashboard(projectId, asOf);

        return new CommitComputedPayload(
                asOf, wbs, gantt, members, raci, raid, backlog, sprints, progress, snapshots, dashboard);
    }

    // ------------------------------------------------------------------ 조회 보조

    private CommitListResponse buildList(Long projectId) {
        List<ProjectCommit> commits = commitRepository.findByProjectId(projectId);
        long usedBytes = commitRepository.sumPayloadBytes(projectId);
        return new CommitListResponse(
                CommitCapacityResponse.of(usedBytes, maxBytesPerProject),
                commits.stream().map(CommitSummaryResponse::from).toList());
    }

    private ProjectCommit requireCommit(Long projectId, int version) {
        return commitRepository.findByProjectIdAndVersion(projectId, version)
                .orElseThrow(() -> new CommitNotFoundException(projectId, version));
    }

    private ExportedProject renameForRestore(ExportedProject project, int version) {
        return new ExportedProject(
                project.id(),
                "%s (v%d 복원)".formatted(project.name(), version),
                project.description(),
                project.status(),
                project.startDate(),
                project.endDate(),
                project.createdAt(),
                project.updatedAt());
    }

    // ------------------------------------------------------------------ 직렬화

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("커밋 페이로드를 직렬화할 수 없습니다.", e);
        }
    }

    private ProjectExportResponse readRawPayload(ProjectCommit commit) {
        return readJson(commit.getRawPayload(), ProjectExportResponse.class);
    }

    private CommitComputedPayload readComputedPayload(ProjectCommit commit) {
        return readJson(commit.getComputedPayload(), CommitComputedPayload.class);
    }

    private <T> T readJson(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("저장된 커밋 페이로드를 읽을 수 없습니다.", e);
        }
    }

    private long utf8Length(String value) {
        return value.getBytes(StandardCharsets.UTF_8).length;
    }

    // ------------------------------------------------------------------ 기타

    private void requireProject(Long projectId) {
        if (!projectRepository.existsById(projectId)) {
            throw new ProjectNotFoundException(projectId);
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /**
     * Same rule as {@code ExportService.safeName} — project names are free text and this ends up in
     * a {@code Content-Disposition} header and on a filesystem.
     */
    private String safeName(String name) {
        String cleaned = name == null ? "" : name.strip().replaceAll("[^\\p{L}\\p{N}._-]+", "-");
        cleaned = cleaned.replaceAll("^-+|-+$", "");
        return cleaned.isEmpty() ? "project" : cleaned;
    }
}
