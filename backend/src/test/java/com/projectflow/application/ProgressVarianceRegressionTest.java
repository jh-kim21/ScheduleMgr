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
 * 결함 (설계서/발견한_결함.md, "Dashboard·프로젝트·내보내기" §2, CLAUDE.md "진척 집계"):
 *
 * <p>{@code ProgressService.plannedProgress}의 {@code plannedPercent}(계획)와
 * {@code comparablePercent}(실제)는 반드시 <b>같은 항목 집합</b> 위에서 계산되어야 한다.
 * 예전 구현은 계획 쪽 분모를 "기준선에 날짜가 있는 항목 전체"로, 실제 쪽 분모를 "그중 산정
 * 가능한 것만"으로 서로 다르게 잡아, variance = comparable − planned가 서로 다른 범위의 두
 * 숫자를 빼는 셈이었다. 이 테스트는 산정 전 항목이 섞였을 때 두 값이 <b>같은 부분집합</b>으로
 * 계산되는지, 그리고 산정 전 항목이 없을 때는 예전과 같은 숫자가 나오는지(회귀 방지)를 고정한다.
 *
 * <p>여기서 계산되는 기대값은 {@code ProgressService.elapsedShare}(계획 기간에 대한 선형
 * baseline, 종료일 포함)의 문서화된 공식을 그대로 재현한 것이다 — 이 테스트가 고정하려는 것은
 * 그 공식 자체가 아니라 "어떤 항목들이 계산에 들어가는가"이다.
 */
class ProgressVarianceRegressionTest {

    private static final Long PROJECT_ID = 1L;

    private final AtomicLong ids = new AtomicLong(100);
    private final List<WbsItem> items = new ArrayList<>();
    private final List<Baseline> baselines = new ArrayList<>();
    private final List<BaselineItem> baselineItems = new ArrayList<>();

    private ProgressService service;
    private final LocalDate today = LocalDate.now();

    @BeforeEach
    void setUp() {
        service = new ProgressService(wbsItemRepository(), backlogItemRepository(),
                checkpointRepository(), baselineRepository(), projectRepository());
    }

    @Test
    @DisplayName("기준선에 산정 전 항목이 섞이면 계획·실제 진척을 같은 부분집합으로 계산한다")
    void excludesNotEstimableItemsFromBothSides() {
        // A: 진행률 80, 기준선 기간이 이미 다 지남 (elapsedShare = 100)
        WbsItem a = workPackage("A", 80, today.minusDays(20), today.minusDays(10), null, null);
        // B: 진행률 40, 기준선 기간의 절반이 지남 (elapsedShare = 50)
        WbsItem b = workPackage("B", 0, today.minusDays(4), today.plusDays(5), null, null);
        b.update("B", null, b.getStartDate(), b.getEndDate(), 40, WbsNodeType.WORK_PACKAGE,
                null, null, null, null, null, null, null);
        // C: 산정 전 — Agile인데 집계 대상 Story/Bug가 하나도 없다. 아직 시작 전 (elapsedShare = 0)
        WbsItem c = workPackage("C", 0, today.plusDays(5), today.plusDays(15),
                ExecutionMode.AGILE, null);

        addBaselineItem(a);
        addBaselineItem(b);
        addBaselineItem(c);

        ProgressResponse response = service.getProgress(PROJECT_ID);
        ProgressResponse.ProjectProgress project = response.project();

        // 고정하려는 계약: C(산정 전)는 계획·실제 양쪽에서 함께 빠진다.
        // 계획 = (100*1 + 50*1) / 2 = 75, 실제 = (80*1 + 40*1) / 2 = 60
        assertThat(project.plannedPercent()).isNotNull();
        assertThat(project.comparablePercent()).isNotNull();
        assertThat(project.plannedPercent()).isCloseTo(75.0, within(0.01));
        assertThat(project.comparablePercent()).isCloseTo(60.0, within(0.01));
        assertThat(project.variancePoints()).isCloseTo(-15.0, within(0.01));

        // 이전의 결함이라면 계획 분모에 C까지 포함해 50이 나왔을 것이다 — 그 값이 아니어야 한다.
        assertThat(project.plannedPercent()).isNotCloseTo(50.0, within(0.01));
    }

    @Test
    @DisplayName("산정 전 항목이 하나도 없으면 예전과 같은 값이 나온다 (회귀 방지)")
    void unaffectedWhenNothingIsExcluded() {
        WbsItem a = workPackage("A", 80, today.minusDays(20), today.minusDays(10), null, null);
        WbsItem b = workPackage("B", 0, today.minusDays(4), today.plusDays(5), null, null);
        b.update("B", null, b.getStartDate(), b.getEndDate(), 40, WbsNodeType.WORK_PACKAGE,
                null, null, null, null, null, null, null);

        addBaselineItem(a);
        addBaselineItem(b);

        ProgressResponse.ProjectProgress project = service.getProgress(PROJECT_ID).project();

        assertThat(project.plannedPercent()).isCloseTo(75.0, within(0.01));
        assertThat(project.comparablePercent()).isCloseTo(60.0, within(0.01));
        assertThat(project.variancePoints()).isCloseTo(-15.0, within(0.01));
    }

    @Test
    @DisplayName("승인된 기준선이 없으면 계획·실제·편차 모두 null이다 — 시간만으로 계획을 지어내지 않는다")
    void nullWithoutBaseline() {
        workPackage("A", 80, today.minusDays(20), today.minusDays(10), null, null);
        // 기준선을 추가하지 않는다.

        ProgressResponse.ProjectProgress project = service.getProgress(PROJECT_ID).project();

        assertThat(project.plannedPercent()).isNull();
        assertThat(project.comparablePercent()).isNull();
        assertThat(project.variancePoints()).isNull();
    }

    // ------------------------------------------------------------------ 도우미

    private WbsItem workPackage(String name, int progress, LocalDate start, LocalDate end,
                                  ExecutionMode mode, Integer weight) {
        WbsItem item = new WbsItem(PROJECT_ID, null, name, null, start, end, progress,
                items.size(), WbsNodeType.WORK_PACKAGE, mode);
        ReflectionTestUtils.setField(item, "id", ids.incrementAndGet());
        if (weight != null) {
            item.update(name, null, start, end, progress, WbsNodeType.WORK_PACKAGE, mode,
                    weight, null, null, null, null, null);
        }
        items.add(item);
        return item;
    }

    private void addBaselineItem(WbsItem item) {
        baselineItems.add(new BaselineItem(1L, item.getId(), "1", item.getName(),
                WbsNodeType.WORK_PACKAGE, item.getExecutionMode(),
                item.getStartDate(), item.getEndDate(), null, null));
        if (baselines.isEmpty()) {
            Baseline baseline = new Baseline(PROJECT_ID, 1, "PM", null);
            ReflectionTestUtils.setField(baseline, "id", 1L);
            baselines.add(baseline);
        }
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
            public Baseline save(Baseline baseline) {
                baselines.add(baseline);
                return baseline;
            }

            @Override
            public List<BaselineItem> saveItems(List<BaselineItem> toSave) {
                baselineItems.addAll(toSave);
                return toSave;
            }

            @Override
            public List<Baseline> findByProjectId(Long projectId) {
                return baselines.stream().filter(b -> b.getProjectId().equals(projectId)).toList();
            }

            @Override
            public List<BaselineItem> findItemsByBaselineId(Long baselineId) {
                return List.copyOf(baselineItems);
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
