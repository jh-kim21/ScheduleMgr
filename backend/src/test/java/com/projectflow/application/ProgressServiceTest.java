package com.projectflow.application;

import com.projectflow.application.dto.ProgressResponse;
import com.projectflow.domain.AcceptanceCheckpoint;
import com.projectflow.domain.AcceptanceCheckpointRepository;
import com.projectflow.domain.BacklogItem;
import com.projectflow.domain.BacklogItemRepository;
import com.projectflow.domain.Baseline;
import com.projectflow.domain.BaselineItem;
import com.projectflow.domain.BaselineRepository;
import com.projectflow.domain.ExecutionMode;
import com.projectflow.domain.Project;
import com.projectflow.domain.ProjectRepository;
import com.projectflow.domain.ProjectStatus;
import com.projectflow.domain.WbsItem;
import com.projectflow.domain.WbsItemRepository;
import com.projectflow.domain.WbsNodeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * 결함 수정 검증: 편차(variance)는 계획·실제 양쪽이 <em>같은</em> Work Package 집합 위에서 계산돼야
 * 한다 (CLAUDE.md "진척 집계 설계상 알아둘 점" — "편차는 기준선에 든 항목만으로 양쪽을 계산해 뺀다").
 *
 * <p>수정 전에는 계획 쪽 분모(날짜 있는 전체)와 실제 쪽 분모(그중 산정 가능한 것만)가 달라, 산정 전인
 * Work Package가 하나만 섞여도 서로 다른 범위의 두 숫자를 빼는 셈이었다.
 */
class ProgressServiceTest {

    private static final Long PROJECT_ID = 1L;

    private final AtomicLong ids = new AtomicLong(200);
    private final List<WbsItem> items = new ArrayList<>();
    private final List<BaselineItem> baselineItems = new ArrayList<>();
    private Baseline baseline;

    private ProgressService service;

    @BeforeEach
    void setUp() {
        service = new ProgressService(wbsItemRepository(), backlogItemRepository(),
                checkpointRepository(), baselineRepository(), projectRepository());
    }

    @Test
    @DisplayName("기준선에 산정 전 Work Package가 섞이면 그 항목은 계획·실제 양쪽에서 함께 빠진다")
    void excludesNotEstimableFromBothSidesOfVariance() {
        LocalDate today = LocalDate.now();

        // 이미 끝났어야 할 계획(진행률 60%, MANUAL) — 실제 쪽 숫자를 낼 수 있다.
        WbsItem measurable = workPackage("설계", null, 60,
                today.minusDays(20), today.minusDays(10));
        // 아직 시작 전인데 실행 방식은 Agile로 정했지만 연결된 Story가 없다 — 산정 전.
        WbsItem notEstimable = workPackage("개발", ExecutionMode.AGILE, 0,
                today.plusDays(10), today.plusDays(20));

        addBaselineItem(measurable, today.minusDays(20), today.minusDays(10));
        addBaselineItem(notEstimable, today.plusDays(10), today.plusDays(20));

        ProgressResponse response = service.getProgress(PROJECT_ID);
        ProgressResponse.ProjectProgress project = response.project();

        // 산정 전 1건은 계획·실제 양쪽에서 빠졌다고 알려야 한다.
        assertThat(project.varianceExcludedCount()).isEqualTo(1);
        // 계획 쪽 분모에 아직 시작 전인 항목(elapsedShare=0)이 섞여 있었다면 50%로 낮아졌을 것이다.
        // 같은 집합(measurable 하나)로만 계산하면 이미 다 지난 계획이라 100%다.
        assertThat(project.plannedPercent()).isCloseTo(100.0, within(0.01));
        assertThat(project.comparablePercent()).isCloseTo(60.0, within(0.01));
        // 수정 전이었다면 변경 전 코드가 60 - 50 = +10(계획보다 앞섬)으로 잘못 판정했을 상황 —
        // 올바른 값은 실제로 크게 뒤처진 -40이다.
        assertThat(project.variancePoints()).isCloseTo(-40.0, within(0.01));
    }

    @Test
    @DisplayName("Summary 기준선 항목은 계획 진척 계산에서 빠진다 — Work Package와 이중 계산되지 않는다")
    void excludesSummaryBaselineRowsFromVariance() {
        LocalDate today = LocalDate.now();
        WbsItem child = workPackage("개발", null, 50, today.minusDays(10), today.minusDays(1));

        WbsItem summary = new WbsItem(PROJECT_ID, null, "단계", null, null, null, 0, 0,
                WbsNodeType.SUMMARY, null);
        ReflectionTestUtils.setField(summary, "id", ids.incrementAndGet());
        items.add(summary);
        ReflectionTestUtils.setField(child, "parentId", summary.getId());

        // Summary 행도 (예전 결함처럼) 날짜를 갖고 기준선에 들어왔다고 가정한다 — 집계된 날짜를
        // 그대로 복사한 결과와 같다. nodeType이 WORK_PACKAGE가 아니므로 걸러져야 한다.
        addBaselineItemRaw(summary, WbsNodeType.SUMMARY, today.minusDays(10), today.minusDays(1));
        addBaselineItem(child, today.minusDays(10), today.minusDays(1));

        ProgressResponse response = service.getProgress(PROJECT_ID);
        ProgressResponse.ProjectProgress project = response.project();

        // Summary까지 더했다면 같은 기간이 두 번 잡혀도 가중치 평균이라 결과값 자체는 우연히 같을
        // 수 있지만, 제외 건수는 0으로 남아야 한다 — Summary는애초에 후보에서 빠지기 때문이다.
        assertThat(project.varianceExcludedCount()).isEqualTo(0);
        assertThat(project.plannedPercent()).isCloseTo(100.0, within(0.01));
        assertThat(project.comparablePercent()).isCloseTo(50.0, within(0.01));
    }

    private WbsItem workPackage(String name, ExecutionMode mode, int progress,
                                 LocalDate start, LocalDate end) {
        WbsItem item = new WbsItem(PROJECT_ID, null, name, null, start, end, progress, items.size(),
                WbsNodeType.WORK_PACKAGE, mode);
        ReflectionTestUtils.setField(item, "id", ids.incrementAndGet());
        items.add(item);
        return item;
    }

    private void addBaselineItem(WbsItem item, LocalDate start, LocalDate end) {
        addBaselineItemRaw(item, item.getNodeType(), start, end);
    }

    private void addBaselineItemRaw(WbsItem item, WbsNodeType nodeType, LocalDate start, LocalDate end) {
        if (baseline == null) {
            baseline = new Baseline(PROJECT_ID, 1, "PM", null);
            ReflectionTestUtils.setField(baseline, "id", 900L);
        }
        baselineItems.add(new BaselineItem(baseline.getId(), item.getId(), "1", item.getName(),
                nodeType, item.getExecutionMode(), start, end, 1, null));
    }

    private WbsItemRepository wbsItemRepository() {
        return new WbsItemRepository() {
            @Override
            public WbsItem save(WbsItem item) {
                return item;
            }

            @Override
            public List<WbsItem> saveAll(List<WbsItem> toSave) {
                return toSave;
            }

            @Override
            public Optional<WbsItem> findById(Long id) {
                return items.stream().filter(item -> item.getId().equals(id)).findFirst();
            }

            @Override
            public List<WbsItem> findByProjectId(Long projectId) {
                return items.stream().filter(item -> item.getProjectId().equals(projectId)).toList();
            }

            @Override
            public void delete(WbsItem item) {
                items.remove(item);
            }
        };
    }

    private BacklogItemRepository backlogItemRepository() {
        return new BacklogItemRepository() {
            @Override
            public BacklogItem save(BacklogItem item) {
                return item;
            }

            @Override
            public List<BacklogItem> saveAll(List<BacklogItem> toSave) {
                return toSave;
            }

            @Override
            public Optional<BacklogItem> findById(Long id) {
                return Optional.empty();
            }

            @Override
            public List<BacklogItem> findByProjectId(Long projectId) {
                return List.of();
            }

            @Override
            public void delete(BacklogItem item) {
            }
        };
    }

    private AcceptanceCheckpointRepository checkpointRepository() {
        return new AcceptanceCheckpointRepository() {
            @Override
            public AcceptanceCheckpoint save(AcceptanceCheckpoint checkpoint) {
                return checkpoint;
            }

            @Override
            public Optional<AcceptanceCheckpoint> findById(Long id) {
                return Optional.empty();
            }

            @Override
            public List<AcceptanceCheckpoint> findByProjectId(Long projectId) {
                return List.of();
            }

            @Override
            public void delete(AcceptanceCheckpoint checkpoint) {
            }
        };
    }

    private BaselineRepository baselineRepository() {
        return new BaselineRepository() {
            @Override
            public Baseline save(Baseline toSave) {
                return toSave;
            }

            @Override
            public List<BaselineItem> saveItems(List<BaselineItem> toSave) {
                baselineItems.addAll(toSave);
                return toSave;
            }

            @Override
            public List<Baseline> findByProjectId(Long projectId) {
                return baseline == null ? List.of() : List.of(baseline);
            }

            @Override
            public List<BaselineItem> findItemsByBaselineId(Long baselineId) {
                return baselineItems.stream()
                        .filter(item -> item.getBaselineId().equals(baselineId))
                        .toList();
            }
        };
    }

    private ProjectRepository projectRepository() {
        Project project = new Project("AEGIS", null, ProjectStatus.IN_PROGRESS, null, null);
        ReflectionTestUtils.setField(project, "id", PROJECT_ID);
        return new ProjectRepository() {
            @Override
            public Project save(Project toSave) {
                return toSave;
            }

            @Override
            public Optional<Project> findById(Long id) {
                return PROJECT_ID.equals(id) ? Optional.of(project) : Optional.empty();
            }

            @Override
            public List<Project> findAll() {
                return List.of(project);
            }

            @Override
            public void deleteById(Long id) {
            }

            @Override
            public boolean existsById(Long id) {
                return PROJECT_ID.equals(id);
            }
        };
    }
}
