package com.projectflow.application;

import com.projectflow.application.dto.BacklogResponse;
import com.projectflow.application.dto.DashboardResponse;
import com.projectflow.application.dto.GanttResponse;
import com.projectflow.application.dto.ProgressResponse;
import com.projectflow.application.dto.RaciMatrixResponse;
import com.projectflow.application.dto.RaidLogResponse;
import com.projectflow.application.dto.RaidLogResponse.RaidItemResponse;
import com.projectflow.application.dto.SprintResponse;
import com.projectflow.domain.Project;
import com.projectflow.domain.ProjectRepository;
import com.projectflow.domain.ProjectStatus;
import com.projectflow.domain.RaidLevel;
import com.projectflow.domain.RaidStatus;
import com.projectflow.domain.RaidType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * DashboardService computes nothing — it only arranges what the other services already returned
 * (Step 7). These tests mock those collaborators rather than rebuilding six services' worth of
 * fake repositories: what is worth pinning down here is the arranging itself.
 */
@DisplayName("Dashboard 집계 (Step 7)")
class DashboardServiceTest {

    private static final Long PROJECT_ID = 1L;
    private static final int LIST_LIMIT = 5;

    private ProjectRepository projectRepository;
    private ProgressService progressService;
    private GanttService ganttService;
    private SprintService sprintService;
    private BacklogService backlogService;
    private RaciService raciService;
    private RaidService raidService;

    private DashboardService service;

    @BeforeEach
    void setUp() {
        projectRepository = mock(ProjectRepository.class);
        progressService = mock(ProgressService.class);
        ganttService = mock(GanttService.class);
        sprintService = mock(SprintService.class);
        backlogService = mock(BacklogService.class);
        raciService = mock(RaciService.class);
        raidService = mock(RaidService.class);

        service = new DashboardService(projectRepository, progressService, ganttService,
                sprintService, backlogService, raciService, raidService);

        Project project = new Project("테스트 프로젝트", null, ProjectStatus.IN_PROGRESS,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
        ReflectionTestUtils.setField(project, "id", PROJECT_ID);
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));

        when(progressService.getProgress(PROJECT_ID)).thenReturn(emptyProgress());
        when(ganttService.getGantt(PROJECT_ID)).thenReturn(emptyGantt());
        when(sprintService.getSprints(PROJECT_ID)).thenReturn(new SprintResponse(null, List.of()));
        when(backlogService.getBacklog(PROJECT_ID)).thenReturn(new BacklogResponse(0, List.of()));
        when(raciService.getMatrix(PROJECT_ID)).thenReturn(
                new RaciMatrixResponse(List.of(), List.of(), List.of(), List.of()));
    }

    /**
     * 카드는 여전히 5건만 보여준다(지시서 "카드당 5건만 싣되 id는 남긴다") — 이 테스트는 그 규칙을
     * 깨지 않는다. 다만 헤드라인 숫자(대시보드 KPI 타일이 읽는 값)는 그 5건짜리 목록의 길이가
     * 아니라, 자르기 전의 진짜 건수여야 한다. 예전에는 count 필드가 아예 없어 화면이 목록 길이를
     * 대신 썼고, 6건 이상일 때 "5"라는 틀린 헤드라인이 나갔다.
     */
    @Test
    @DisplayName("열린 이슈·노출도 높음·기한 초과가 5건을 넘으면 목록은 5건, 총건수는 잘리지 않는다")
    void controlCardCountsSurviveTheListCap() {
        int total = 7;
        when(raidService.getLog(PROJECT_ID)).thenReturn(
                new RaidLogResponse(LocalDate.now(), openIssueOverdueHighExposureItems(total)));

        DashboardResponse dashboard = service.getDashboard(PROJECT_ID);

        assertThat(dashboard.control().openIssueCount()).isEqualTo(total);
        assertThat(dashboard.control().highExposureCount()).isEqualTo(total);
        assertThat(dashboard.control().overdueCount()).isEqualTo(total);

        assertThat(dashboard.control().openIssues()).hasSize(LIST_LIMIT);
        assertThat(dashboard.control().highExposure()).hasSize(LIST_LIMIT);
        assertThat(dashboard.control().overdue()).hasSize(LIST_LIMIT);
    }

    @Test
    @DisplayName("5건 이하면 총건수와 목록 길이가 같다")
    void controlCardCountsMatchListWhenUnderTheCap() {
        int total = 3;
        when(raidService.getLog(PROJECT_ID)).thenReturn(
                new RaidLogResponse(LocalDate.now(), openIssueOverdueHighExposureItems(total)));

        DashboardResponse dashboard = service.getDashboard(PROJECT_ID);

        assertThat(dashboard.control().openIssueCount()).isEqualTo(total);
        assertThat(dashboard.control().highExposureCount()).isEqualTo(total);
        assertThat(dashboard.control().overdueCount()).isEqualTo(total);

        assertThat(dashboard.control().openIssues()).hasSize(total);
        assertThat(dashboard.control().highExposure()).hasSize(total);
        assertThat(dashboard.control().overdue()).hasSize(total);
    }

    /** 항목마다 세 조건(열린 이슈·노출도 높음·기한 초과)을 모두 만족시켜 한 데이터셋으로 셋을 함께 본다. */
    private List<RaidItemResponse> openIssueOverdueHighExposureItems(int count) {
        List<RaidItemResponse> items = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            items.add(new RaidItemResponse(
                    (long) i,
                    RaidType.ISSUE,
                    "이슈 " + i,
                    null,
                    RaidStatus.OPEN,
                    RaidLevel.HIGH,
                    RaidLevel.HIGH,
                    null,
                    null,
                    List.of(),
                    LocalDate.now().minusDays(i),
                    null,
                    9,
                    RaidLevel.HIGH,
                    true,
                    i));
        }
        return items;
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
        return new GanttResponse(null, null, LocalDate.now(), false, null, List.of(), List.of(),
                List.of());
    }
}
