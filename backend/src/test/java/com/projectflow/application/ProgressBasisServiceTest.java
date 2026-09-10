package com.projectflow.application;

import com.projectflow.application.dto.ProgressRequests.BaselineApproveRequest;
import com.projectflow.application.dto.ProgressResponse;
import com.projectflow.domain.AcceptanceCheckpoint;
import com.projectflow.domain.AcceptanceCheckpointRepository;
import com.projectflow.domain.BacklogItem;
import com.projectflow.domain.BacklogItemRepository;
import com.projectflow.domain.Baseline;
import com.projectflow.domain.BaselineItem;
import com.projectflow.domain.BaselineRepository;
import com.projectflow.domain.ChangeLog;
import com.projectflow.domain.ChangeLogRepository;
import com.projectflow.domain.Project;
import com.projectflow.domain.ProjectRepository;
import com.projectflow.domain.ProjectStatus;
import com.projectflow.domain.ProgressSnapshot;
import com.projectflow.domain.ProgressSnapshotRepository;
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

/**
 * 결함 수정 검증: 기준선의 Summary 행은 저장 컬럼이 아니라 집계된(하위에서 롤업된) 일정을 담아야
 * 한다 (CLAUDE.md "간트의 세 가지 일정과 Sprint 레인" 및 "WBS 설계상 알아둘 점" — 파생 값은 저장하지
 * 않고, Summary의 저장 컬럼은 보통 비어 있다).
 *
 * <p>수정 전에는 {@code item.getStartDate()}/{@code getEndDate()}를 그대로 복사해 Summary
 * 기준선 행의 날짜가 항상 {@code null}이었다 — 간트가 그 행에 기준 막대를 그릴 수 없었다.
 */
class ProgressBasisServiceTest {

    private static final Long PROJECT_ID = 1L;

    private final AtomicLong ids = new AtomicLong(300);
    private final List<WbsItem> items = new ArrayList<>();
    private final List<BaselineItem> baselineItems = new ArrayList<>();
    private final List<Baseline> baselines = new ArrayList<>();

    private ProgressBasisService service;

    @BeforeEach
    void setUp() {
        ProgressService progressService = new ProgressService(wbsItemRepository(),
                backlogItemRepository(), checkpointRepository(), baselineRepository(),
                projectRepository());
        service = new ProgressBasisService(checkpointRepository(), baselineRepository(),
                snapshotRepository(), changeLogRepository(), wbsItemRepository(),
                projectRepository(), progressService);
    }

    @Test
    @DisplayName("Summary 항목은 기준선에 하위에서 집계된 시작·종료일을 담는다")
    void baselinesSummaryWithRolledUpDates() {
        LocalDate childAStart = LocalDate.of(2026, 1, 5);
        LocalDate childAEnd = LocalDate.of(2026, 1, 15);
        LocalDate childBStart = LocalDate.of(2026, 1, 10);
        LocalDate childBEnd = LocalDate.of(2026, 1, 25);

        WbsItem summary = new WbsItem(PROJECT_ID, null, "단계", null, null, null, 0, 0,
                WbsNodeType.SUMMARY, null);
        ReflectionTestUtils.setField(summary, "id", ids.incrementAndGet());
        items.add(summary);

        WbsItem childA = new WbsItem(PROJECT_ID, summary.getId(), "설계", null,
                childAStart, childAEnd, 100, 0);
        ReflectionTestUtils.setField(childA, "id", ids.incrementAndGet());
        items.add(childA);

        WbsItem childB = new WbsItem(PROJECT_ID, summary.getId(), "개발", null,
                childBStart, childBEnd, 0, 1);
        ReflectionTestUtils.setField(childB, "id", ids.incrementAndGet());
        items.add(childB);

        // Summary 자신의 저장 컬럼은 결코 채워지지 않는다 — 이것이 재현하려는 상태다.
        assertThat(summary.getStartDate()).isNull();
        assertThat(summary.getEndDate()).isNull();

        ProgressResponse response = service.approveBaseline(PROJECT_ID,
                new BaselineApproveRequest("PM", null));
        assertThat(response.baseline()).isNotNull();

        BaselineItem summaryBaseline = baselineItems.stream()
                .filter(item -> item.getWbsItemId().equals(summary.getId()))
                .findFirst()
                .orElseThrow();

        // 하위 중 가장 이른 시작(1/5)과 가장 늦은 종료(1/25) — WbsTreeAssembler가 내는 값과 같다.
        assertThat(summaryBaseline.getStartDate()).isEqualTo(childAStart);
        assertThat(summaryBaseline.getEndDate()).isEqualTo(childBEnd);
        assertThat(summaryBaseline.getNodeType()).isEqualTo(WbsNodeType.SUMMARY);

        BaselineItem childABaseline = baselineItems.stream()
                .filter(item -> item.getWbsItemId().equals(childA.getId()))
                .findFirst()
                .orElseThrow();
        assertThat(childABaseline.getStartDate()).isEqualTo(childAStart);
        assertThat(childABaseline.getEndDate()).isEqualTo(childAEnd);
    }

    private WbsItemRepository wbsItemRepository() {
        return new WbsItemRepository() {
            @Override
            public WbsItem save(WbsItem item) {
                if (item.getId() == null) {
                    ReflectionTestUtils.setField(item, "id", ids.incrementAndGet());
                }
                if (!items.contains(item)) {
                    items.add(item);
                }
                return item;
            }

            @Override
            public List<WbsItem> saveAll(List<WbsItem> toSave) {
                toSave.forEach(this::save);
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
                if (baseline.getId() == null) {
                    ReflectionTestUtils.setField(baseline, "id", ids.incrementAndGet());
                }
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
                return baselineItems.stream()
                        .filter(item -> item.getBaselineId().equals(baselineId))
                        .toList();
            }
        };
    }

    private ChangeLogRepository changeLogRepository() {
        return new ChangeLogRepository() {
            @Override
            public ChangeLog save(ChangeLog change) {
                return change;
            }

            @Override
            public List<ChangeLog> saveAll(List<ChangeLog> toSave) {
                return toSave;
            }

            @Override
            public List<ChangeLog> findByProjectId(Long projectId) {
                return List.of();
            }
        };
    }

    private ProgressSnapshotRepository snapshotRepository() {
        return new ProgressSnapshotRepository() {
            @Override
            public ProgressSnapshot save(ProgressSnapshot snapshot) {
                return snapshot;
            }

            @Override
            public List<ProgressSnapshot> findByProjectId(Long projectId) {
                return List.of();
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
