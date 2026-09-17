package com.projectflow.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.projectflow.application.dto.BacklogResponse;
import com.projectflow.application.dto.CommitRequests.CommitCreateRequest;
import com.projectflow.application.dto.CommitResponses.CommitCreateResponse;
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
import com.projectflow.domain.ProjectStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ProjectCommitService} computes nothing new (docs/tasks/commit-history.md §3.6) — it calls
 * the same query services a live screen would and stores what they return. So, like
 * {@link DashboardServiceTest}, these tests mock those collaborators rather than rebuilding a dozen
 * services' worth of fake repositories; what is worth pinning down here is capacity gating, version
 * numbering, the single reference date, and the restore rename — the things this service actually
 * adds on top of its collaborators.
 *
 * <p>{@link ProjectCommitRepository} is the one dependency given a small hand-rolled in-memory fake
 * instead of a mock: {@code MAX(version)+1} and {@code SUM(payload_bytes)} are exactly the kind of
 * small stateful behaviour this codebase's other service tests (e.g. {@code SprintServiceTest})
 * fake rather than stub call-by-call.
 */
@DisplayName("프로젝트 커밋 히스토리 (docs/tasks/commit-history.md)")
class ProjectCommitServiceTest {

    private static final Long PROJECT_ID = 1L;

    private ProjectRepository projectRepository;
    private ExportService exportService;
    private ImportService importService;
    private WbsService wbsService;
    private GanttService ganttService;
    private ProjectMemberService memberService;
    private RaciService raciService;
    private RaidService raidService;
    private BacklogService backlogService;
    private SprintService sprintService;
    private ProgressService progressService;
    private ProgressBasisService progressBasisService;

    private ProjectCommitService service;

    @BeforeEach
    void setUp() {
        projectRepository = mock(ProjectRepository.class);
        exportService = mock(ExportService.class);
        importService = mock(ImportService.class);
        wbsService = mock(WbsService.class);
        ganttService = mock(GanttService.class);
        memberService = mock(ProjectMemberService.class);
        raciService = mock(RaciService.class);
        raidService = mock(RaidService.class);
        backlogService = mock(BacklogService.class);
        sprintService = mock(SprintService.class);
        progressService = mock(ProgressService.class);
        progressBasisService = mock(ProgressBasisService.class);

        Project project = new Project("커밋 테스트", null, ProjectStatus.IN_PROGRESS,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
        ReflectionTestUtils.setField(project, "id", PROJECT_ID);
        when(projectRepository.existsById(PROJECT_ID)).thenReturn(true);
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));

        when(exportService.exportProject(PROJECT_ID)).thenReturn(minimalExport());
        when(wbsService.getTree(eq(PROJECT_ID), any(LocalDate.class)))
                .thenReturn(new WbsTreeResponse(LocalDate.now(), List.of()));
        when(ganttService.getGantt(eq(PROJECT_ID), any(LocalDate.class))).thenReturn(emptyGantt());
        when(memberService.listMembers(PROJECT_ID)).thenReturn(List.of());
        when(raciService.getMatrix(PROJECT_ID))
                .thenReturn(new RaciMatrixResponse(List.of(), List.of(), List.of(), List.of()));
        when(raidService.getLog(eq(PROJECT_ID), any(LocalDate.class)))
                .thenReturn(new RaidLogResponse(LocalDate.now(), List.of()));
        when(backlogService.getBacklog(PROJECT_ID)).thenReturn(new BacklogResponse(0, List.of()));
        when(sprintService.getSprints(PROJECT_ID)).thenReturn(new SprintResponse(null, List.of()));
        when(progressService.getProgress(eq(PROJECT_ID), any(LocalDate.class)))
                .thenReturn(emptyProgress());
        when(progressBasisService.listSnapshots(PROJECT_ID)).thenReturn(new SnapshotResponse(List.of()));

        // 실제 DashboardService를 그대로 감아서 쓴다 — ProjectCommitService.capture()가 대시보드
        // 섹션을 이 서비스와 똑같은 asOf로 호출하므로(§3.5), 목(mock)으로 갈음하면 그 propagation을
        // 검증할 수 없다.
        DashboardService dashboardService = new DashboardService(projectRepository, progressService,
                ganttService, sprintService, backlogService, raciService, raidService);

        service = new ProjectCommitService(fakeCommitRepository(), projectRepository, exportService,
                importService, wbsService, ganttService, memberService, raciService, raidService,
                backlogService, sprintService, progressService, progressBasisService, dashboardService,
                objectMapper(), 1_073_741_824L);
    }

    // ------------------------------------------------------------------ 채번(§3.3)

    @Test
    @DisplayName("[v1,v2,v3]에서 v2를 삭제하고 커밋하면 v4가 된다 (MAX(version)+1, count+1 아님)")
    void versionNumberingSkipsDeletedVersion() {
        int v1 = service.createCommit(PROJECT_ID, blankRequest()).commit().version();
        int v2 = service.createCommit(PROJECT_ID, blankRequest()).commit().version();
        int v3 = service.createCommit(PROJECT_ID, blankRequest()).commit().version();
        assertThat(List.of(v1, v2, v3)).containsExactly(1, 2, 3);

        service.deleteCommit(PROJECT_ID, 2);

        int v4 = service.createCommit(PROJECT_ID, blankRequest()).commit().version();
        assertThat(v4)
                .as("count+1이면 남은 커밋이 2개뿐이라 3이 나오고 UNIQUE(project_id, version)과 충돌한다")
                .isEqualTo(4);
    }

    @Test
    @DisplayName("가장 최근 커밋도 삭제할 수 있다 — Baseline과 달리 제약이 없다")
    void latestCommitIsDeletable() {
        service.createCommit(PROJECT_ID, blankRequest());
        CommitCreateResponse latest = service.createCommit(PROJECT_ID, blankRequest());

        service.deleteCommit(PROJECT_ID, latest.commit().version());

        assertThatThrownBy(() -> service.getCommit(PROJECT_ID, latest.commit().version()))
                .isInstanceOf(CommitNotFoundException.class);
    }

    // ------------------------------------------------------------------ 용량 게이트(§4.4)

    @Test
    @DisplayName("사용량이 80% 이상이면 경고 플래그가 켜진다 (거부되지는 않는다)")
    void warnsAt80PercentWithoutRejecting() {
        long payloadBytes = payloadBytesOfOneCommit();
        // 첫 커밋 자체가 한도의 80% 이상을 차지하도록 한도를 좁힌다.
        ProjectCommitService tightService = serviceWithLimit((long) (payloadBytes / 0.8));

        CommitCreateResponse response = tightService.createCommit(PROJECT_ID, blankRequest());

        assertThat(response.capacity().warning()).isTrue();
    }

    @Test
    @DisplayName("사용량이 100%를 넘으면 409에 해당하는 예외가 나고, 기존 커밋 목록이 함께 실린다")
    void rejectsWhenOverCapacity() {
        long payloadBytes = payloadBytesOfOneCommit();
        ProjectCommitService tightService = serviceWithLimit(payloadBytes); // 첫 커밋으로 정확히 100%
        tightService.createCommit(PROJECT_ID, blankRequest());

        assertThatThrownBy(() -> tightService.createCommit(PROJECT_ID, blankRequest()))
                .isInstanceOf(CommitCapacityExceededException.class)
                .satisfies(ex -> {
                    CommitCapacityExceededException capacityEx = (CommitCapacityExceededException) ex;
                    assertThat(capacityEx.getUsedBytes()).isEqualTo(payloadBytes);
                    assertThat(capacityEx.getExistingCommits()).hasSize(1);
                });
    }

    @Test
    @DisplayName("용량 초과로 거부된 커밋은 버전을 소비하지 않는다 — 저장된 것이 없어야 한다")
    void rejectedCommitLeavesVersionSequenceUntouched() {
        long payloadBytes = payloadBytesOfOneCommit();
        ProjectCommitService tightService = serviceWithLimit(payloadBytes);
        tightService.createCommit(PROJECT_ID, blankRequest()); // v1, 한도를 채운다

        assertThatThrownBy(() -> tightService.createCommit(PROJECT_ID, blankRequest()))
                .isInstanceOf(CommitCapacityExceededException.class);

        assertThat(tightService.listCommits(PROJECT_ID).commits()).hasSize(1);
    }

    // ------------------------------------------------------------------ 기준일 단일화(§3.5)

    @Test
    @DisplayName("한 커밋 안에서 WBS·간트·진척·RAID·대시보드가 모두 같은 기준일로 조회된다")
    void everyScreenIsCapturedWithTheSameReferenceDate() {
        service.createCommit(PROJECT_ID, blankRequest());

        ArgumentCaptor<LocalDate> wbsDate = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> ganttDate = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> progressDate = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> raidDate = ArgumentCaptor.forClass(LocalDate.class);

        verify(wbsService).getTree(eq(PROJECT_ID), wbsDate.capture());
        // gantt와 raid는 capture() 자체와, capture()가 넘긴 값을 그대로 받는 실제 DashboardService
        // 양쪽에서 불리므로 두 번 호출된다 — 두 호출 모두 같은 날짜인지를 본다(그렇지 않으면
        // 대시보드 섹션만 다른 "오늘"을 읽는 회귀가 된다).
        verify(ganttService, times(2)).getGantt(eq(PROJECT_ID), ganttDate.capture());
        verify(progressService, times(2)).getProgress(eq(PROJECT_ID), progressDate.capture());
        verify(raidService, times(2)).getLog(eq(PROJECT_ID), raidDate.capture());

        LocalDate asOf = wbsDate.getValue();
        assertThat(ganttDate.getAllValues()).allMatch(asOf::equals);
        assertThat(progressDate.getAllValues()).allMatch(asOf::equals);
        assertThat(raidDate.getAllValues()).allMatch(asOf::equals);
    }

    // ------------------------------------------------------------------ 저장된 값(§2.1, §4.3)

    @Test
    @DisplayName("커밋의 format_version은 ExportService가 그 순간 낸 formatVersion을 그대로 쓴다")
    void formatVersionComesFromExportResponse() {
        when(exportService.exportProject(PROJECT_ID)).thenReturn(minimalExport(9));

        CommitCreateResponse response = service.createCommit(PROJECT_ID, blankRequest());

        assertThat(response.commit().formatVersion()).isEqualTo(9);
    }

    @Test
    @DisplayName("존재하지 않는 프로젝트에 대한 커밋 요청은 ProjectNotFoundException이다")
    void unknownProjectIsRejected() {
        when(projectRepository.existsById(999L)).thenReturn(false);

        assertThatThrownBy(() -> service.createCommit(999L, blankRequest()))
                .isInstanceOf(ProjectNotFoundException.class);
    }

    // ------------------------------------------------------------------ 복원(§4.5)

    @Test
    @DisplayName("복원은 '원본명 (vN 복원)'으로 새 프로젝트를 만들고 ImportService에 위임한다")
    void restoreRenamesProjectAndDelegatesToImportService() {
        CommitCreateResponse created = service.createCommit(PROJECT_ID, blankRequest());
        long commitId = created.commit().id();
        int version = created.commit().version();

        ProjectResponse restored = new ProjectResponse(99L, "커밋 테스트 (v%d 복원)".formatted(version),
                null, ProjectStatus.PLANNED, null, null, null, null);
        when(importService.importProject(any())).thenReturn(restored);

        ProjectResponse result = service.restore(commitId);

        assertThat(result).isEqualTo(restored);
        ArgumentCaptor<ProjectExportResponse> captor = ArgumentCaptor.forClass(ProjectExportResponse.class);
        verify(importService).importProject(captor.capture());
        assertThat(captor.getValue().project().name())
                .isEqualTo("커밋 테스트 (v%d 복원)".formatted(version));
    }

    @Test
    @DisplayName("존재하지 않는 커밋의 복원은 CommitNotFoundException이다")
    void restoringUnknownCommitFails() {
        assertThatThrownBy(() -> service.restore(999_999L))
                .isInstanceOf(CommitNotFoundException.class);
        verify(importService, never()).importProject(any());
    }

    // ------------------------------------------------------------------ 헬퍼

    private ProjectCommitService serviceWithLimit(long maxBytes) {
        return new ProjectCommitService(fakeCommitRepository(), projectRepository, exportService,
                importService, wbsService, ganttService, memberService, raciService, raidService,
                backlogService, sprintService, progressService, progressBasisService,
                new DashboardService(projectRepository, progressService, ganttService, sprintService,
                        backlogService, raciService, raidService),
                objectMapper(), maxBytes);
    }

    /** One commit's actual serialized size under this test's fixtures, used to size tight limits. */
    private long payloadBytesOfOneCommit() {
        ProjectCommitService probe = serviceWithLimit(Long.MAX_VALUE);
        return probe.createCommit(PROJECT_ID, blankRequest()).commit().payloadBytes();
    }

    private CommitCreateRequest blankRequest() {
        return new CommitCreateRequest("tester", "테스트 커밋");
    }

    private ProjectExportResponse minimalExport() {
        return minimalExport(6);
    }

    private ProjectExportResponse minimalExport(int formatVersion) {
        return new ProjectExportResponse(
                formatVersion,
                java.time.LocalDateTime.now(),
                new ExportedProject(PROJECT_ID, "커밋 테스트", null, ProjectStatus.IN_PROGRESS,
                        LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), null, null),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of());
    }

    private ProgressResponse emptyProgress() {
        return new ProgressResponse(
                LocalDate.now(),
                new ProgressResponse.ProjectProgress(null, null, 0, 0, false, null, null, null, 0, 0),
                List.of(),
                null,
                new ProgressResponse.ScopeComparison(false, 0, 0, List.of(), List.of(), List.of()));
    }

    private GanttResponse emptyGantt() {
        return new GanttResponse(null, null, LocalDate.now(), false, null, List.of(), List.of(), List.of());
    }

    private ObjectMapper objectMapper() {
        return new ObjectMapper().registerModule(new JavaTimeModule());
    }

    /**
     * A small in-memory fake rather than a mock — {@code MAX(version)+1} and
     * {@code SUM(payload_bytes)} are real stateful behaviour, not a canned return value.
     *
     * <p>Each call returns a repository backed by its own fresh, private list: {@code
     * serviceWithLimit} builds a new {@link ProjectCommitService} per call (to probe one commit's
     * size, or to test a tight limit in isolation), and sharing one list across those would let an
     * earlier probe's commit count against a later test's capacity math.
     */
    private ProjectCommitRepository fakeCommitRepository() {
        List<ProjectCommit> commits = new ArrayList<>();
        long[] nextId = {1};
        return new ProjectCommitRepository() {
            @Override
            public ProjectCommit save(ProjectCommit commit) {
                ReflectionTestUtils.setField(commit, "id", nextId[0]++);
                commits.add(commit);
                return commit;
            }

            @Override
            public void delete(ProjectCommit commit) {
                commits.removeIf(c -> c.getId().equals(commit.getId()));
            }

            @Override
            public List<ProjectCommit> findByProjectId(Long projectId) {
                return commits.stream()
                        .filter(c -> c.getProjectId().equals(projectId))
                        .sorted((a, b) -> Integer.compare(b.getVersion(), a.getVersion()))
                        .toList();
            }

            @Override
            public Optional<ProjectCommit> findByProjectIdAndVersion(Long projectId, int version) {
                return commits.stream()
                        .filter(c -> c.getProjectId().equals(projectId) && c.getVersion() == version)
                        .findFirst();
            }

            @Override
            public Optional<ProjectCommit> findById(Long id) {
                return commits.stream().filter(c -> c.getId().equals(id)).findFirst();
            }

            @Override
            public int findMaxVersion(Long projectId) {
                return commits.stream()
                        .filter(c -> c.getProjectId().equals(projectId))
                        .mapToInt(ProjectCommit::getVersion)
                        .max()
                        .orElse(0);
            }

            @Override
            public long sumPayloadBytes(Long projectId) {
                return commits.stream()
                        .filter(c -> c.getProjectId().equals(projectId))
                        .mapToLong(ProjectCommit::getPayloadBytes)
                        .sum();
            }
        };
    }
}
