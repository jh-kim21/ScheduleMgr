package com.projectflow.application;

import com.projectflow.application.dto.GanttResponse;
import com.projectflow.application.dto.WbsNodeResponse;
import com.projectflow.domain.AcceptanceCheckpoint;
import com.projectflow.domain.AcceptanceCheckpointRepository;
import com.projectflow.domain.BacklogItem;
import com.projectflow.domain.BacklogItemRepository;
import com.projectflow.domain.BacklogItemType;
import com.projectflow.domain.BacklogPriority;
import com.projectflow.domain.BacklogStatus;
import com.projectflow.domain.Baseline;
import com.projectflow.domain.BaselineItem;
import com.projectflow.domain.BaselineRepository;
import com.projectflow.domain.DelayCalculator;
import com.projectflow.domain.DelayStatus;
import com.projectflow.domain.ExecutionMode;
import com.projectflow.domain.ExecutionModeSummary;
import com.projectflow.domain.Project;
import com.projectflow.domain.ProjectRepository;
import com.projectflow.domain.ProjectStatus;
import com.projectflow.domain.ProgressBasis;
import com.projectflow.domain.ProgressCalculator.ProgressResult;
import com.projectflow.domain.Sprint;
import com.projectflow.domain.SprintItem;
import com.projectflow.domain.SprintItemRepository;
import com.projectflow.domain.SprintRepository;
import com.projectflow.domain.WbsDependency;
import com.projectflow.domain.WbsDependencyRepository;
import com.projectflow.domain.WbsItem;
import com.projectflow.domain.WbsItemRepository;
import com.projectflow.domain.WbsNode;
import com.projectflow.domain.WbsNodeType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 계약 (설계서/발견한_결함.md WBS §5, CLAUDE.md "지연 판정 설계상 알아둘 점"):
 *
 * <blockquote>지연 판정에 쓰는 진척 = {@code computedProgress != null ? computedProgress : 저장된 progress}</blockquote>
 *
 * <p>예전 구현은 {@code DelayCalculator}에 항상 저장된 {@code progress}만 넘겨, Agile Work
 * Package에서 Story를 모두 완료해 진척 칸은 100%인데 배지는 낡은 저장값 기준으로 "지연"을
 * 보여주는 불일치가 있었다. 이 테스트는 그 계약을 {@code WbsNodeResponse}와
 * {@code GanttService} 양쪽에서 함께 고정한다 — 한쪽만 고치면 WBS 화면과 간트 화면이 다시
 * 어긋난다.
 *
 * <p>미지정(MANUAL) Work Package는 {@code computedProgress == 저장된 progress}이므로 전환
 * 정책("아무것도 지정하지 않은 프로젝트의 화면 숫자는 그대로")에 따라 예전과 같은 판정이
 * 나와야 한다 — 이것도 함께 고정한다.
 */
class DelayJudgementConsistencyTest {

    private static final Long PROJECT_ID = 1L;
    private final AtomicLong ids = new AtomicLong(100);

    @Nested
    @DisplayName("WbsNodeResponse")
    class WbsNodeResponseContract {

        @Test
        @DisplayName("Agile Work Package: Story를 전부 완료했으면 저장된 progress가 0이어도 지연이 아니다")
        void usesComputedProgressWhenAvailable() {
            LocalDate referenceDate = LocalDate.now();
            // 일정은 이미 끝났고, 저장된 progress는 손대지 않아 0으로 남아 있다.
            WbsItem item = newItem("개발", ExecutionMode.AGILE,
                    referenceDate.minusDays(20), referenceDate.minusDays(5), 0);
            WbsNode node = leaf(item);

            // 공통 집계는 Story를 전부 완료해 100%로 본다 — WBS 화면의 진척 칸이 보여주는 값.
            Map<Long, ProgressResult> computed = Map.of(item.getId(),
                    new ProgressResult(100.0, ProgressBasis.AGILE, false, false, null));

            WbsNodeResponse response = WbsNodeResponse.from(node, referenceDate, Map.of(), computed);

            assertThat(response.computedProgress()).isEqualTo(100.0);
            // 저장된 값은 legacy 필드로 그대로 남는다 — 지워지거나 덮어써지지 않는다.
            assertThat(response.progress()).isEqualTo(0);
            // 지연 판정은 100%(computedProgress) 기준이어야 한다: 완료로 본다.
            assertThat(response.delayStatus()).isEqualTo(DelayStatus.COMPLETED);
        }

        @Test
        @DisplayName("미지정(MANUAL) 항목은 computedProgress == 저장값이라 판정이 예전과 같다")
        void manualItemsAreUnaffected() {
            LocalDate referenceDate = LocalDate.now();
            LocalDate start = referenceDate.minusDays(10);
            LocalDate end = referenceDate.plusDays(10);
            WbsItem item = newItem("설계", null, start, end, 10);
            WbsNode node = leaf(item);

            // 실행 방식 미지정 Work Package는 MANUAL 기준으로 저장값 그대로 계산된다.
            Map<Long, ProgressResult> computed = Map.of(item.getId(),
                    new ProgressResult(10.0, ProgressBasis.MANUAL, false, false, null));

            WbsNodeResponse response = WbsNodeResponse.from(node, referenceDate, Map.of(), computed);
            DelayCalculator.DelayAssessment expected =
                    DelayCalculator.assess(start, end, 10, referenceDate);

            assertThat(response.delayStatus()).isEqualTo(expected.status());
            assertThat(response.progressGap()).isEqualTo(expected.progressGap());
            assertThat(response.expectedProgress()).isEqualTo(expected.expectedProgress());
        }

        @Test
        @DisplayName("산정 전(computedProgress == null)이면 저장된 progress로 판정한다")
        void fallsBackToStoredProgressWhenNotEstimable() {
            LocalDate referenceDate = LocalDate.now();
            LocalDate start = referenceDate.minusDays(20);
            LocalDate end = referenceDate.minusDays(5);
            // Agile인데 집계 대상 Story·Bug가 없어 산정 전 — 저장된 progress(30)로 판정해야 한다.
            WbsItem item = newItem("개발", ExecutionMode.AGILE, start, end, 30);
            WbsNode node = leaf(item);

            Map<Long, ProgressResult> computed = Map.of(item.getId(), notEstimable());

            WbsNodeResponse response = WbsNodeResponse.from(node, referenceDate, Map.of(), computed);
            DelayCalculator.DelayAssessment expected =
                    DelayCalculator.assess(start, end, 30, referenceDate);

            assertThat(response.computedProgress()).isNull();
            assertThat(response.delayStatus()).isEqualTo(expected.status());
            assertThat(response.delayDays()).isEqualTo(expected.delayDays());
        }
    }

    @Nested
    @DisplayName("GanttService — 같은 계약, 같은 결과")
    class GanttServiceContract {

        private final List<WbsItem> wbsItems = new ArrayList<>();
        private final List<BacklogItem> backlogItems = new ArrayList<>();

        @Test
        @DisplayName("Agile Work Package: Story를 전부 완료했으면 간트 배지도 지연이 아니다")
        void ganttUsesComputedProgressToo() {
            LocalDate referenceDate = LocalDate.now();
            WbsItem item = addWbsItem("개발", ExecutionMode.AGILE,
                    referenceDate.minusDays(20), referenceDate.minusDays(5), 0);
            addBacklogItem(item.getId(), BacklogStatus.DONE);
            addBacklogItem(item.getId(), BacklogStatus.DONE);

            GanttResponse response = ganttService().getGantt(PROJECT_ID);

            GanttResponse.GanttTaskResponse task = taskFor(response, item.getId());
            assertThat(task.computedProgress()).isEqualTo(100.0);
            // Gantt의 progress 칸은 저장값 그대로다 — WbsNodeResponse와 같은 규칙.
            assertThat(task.progress()).isEqualTo(0);
            assertThat(task.delayStatus()).isEqualTo(DelayStatus.COMPLETED);
        }

        @Test
        @DisplayName("미지정 Work Package는 간트에서도 저장된 progress 기준으로 예전과 같이 판정한다")
        void ganttManualItemsAreUnaffected() {
            LocalDate referenceDate = LocalDate.now();
            LocalDate start = referenceDate.minusDays(10);
            LocalDate end = referenceDate.plusDays(10);
            WbsItem item = addWbsItem("설계", null, start, end, 10);

            GanttResponse response = ganttService().getGantt(PROJECT_ID);
            GanttResponse.GanttTaskResponse task = taskFor(response, item.getId());
            DelayCalculator.DelayAssessment expected =
                    DelayCalculator.assess(start, end, 10, referenceDate);

            assertThat(task.delayStatus()).isEqualTo(expected.status());
            assertThat(task.progressGap()).isEqualTo(expected.progressGap());
        }

        private GanttResponse.GanttTaskResponse taskFor(GanttResponse response, Long itemId) {
            return response.tasks().stream()
                    .filter(task -> task.id().equals(itemId))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("간트에 항목이 없습니다: id=" + itemId));
        }

        private WbsItem addWbsItem(String name, ExecutionMode mode, LocalDate start, LocalDate end,
                                     int progress) {
            WbsItem item = newItem(name, mode, start, end, progress);
            wbsItems.add(item);
            return item;
        }

        private void addBacklogItem(Long wbsItemId, BacklogStatus status) {
            BacklogItem item = new BacklogItem(PROJECT_ID, wbsItemId, null, BacklogItemType.STORY,
                    "스토리", null, BacklogPriority.MEDIUM, status, null, null, null, null,
                    backlogItems.size());
            ReflectionTestUtils.setField(item, "id", ids.incrementAndGet());
            backlogItems.add(item);
        }

        private GanttService ganttService() {
            return new GanttService(wbsItemRepo(), dependencyRepo(), projectRepo(),
                    baselineRepo(), sprintRepo(), sprintItemRepo(), backlogItemRepo(),
                    progressService());
        }

        private ProgressService progressService() {
            return new ProgressService(wbsItemRepo(), backlogItemRepo(), checkpointRepo(),
                    baselineRepo(), projectRepo());
        }

        private WbsItemRepository wbsItemRepo() {
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
                    return wbsItems.stream().filter(item -> item.getId().equals(id)).findFirst();
                }

                @Override
                public List<WbsItem> findByProjectId(Long projectId) {
                    return wbsItems.stream()
                            .filter(item -> item.getProjectId().equals(projectId)).toList();
                }

                @Override
                public void delete(WbsItem item) {
                    wbsItems.remove(item);
                }
            };
        }

        private BacklogItemRepository backlogItemRepo() {
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
                    return backlogItems.stream().filter(item -> item.getId().equals(id)).findFirst();
                }

                @Override
                public List<BacklogItem> findByProjectId(Long projectId) {
                    return backlogItems.stream()
                            .filter(item -> item.getProjectId().equals(projectId)).toList();
                }

                @Override
                public void delete(BacklogItem item) {
                    backlogItems.remove(item);
                }
            };
        }

        private WbsDependencyRepository dependencyRepo() {
            return new WbsDependencyRepository() {
                @Override
                public WbsDependency save(WbsDependency dependency) {
                    return dependency;
                }

                @Override
                public Optional<WbsDependency> findById(Long id) {
                    return Optional.empty();
                }

                @Override
                public List<WbsDependency> findByProjectId(Long projectId) {
                    return List.of();
                }

                @Override
                public void delete(WbsDependency dependency) {
                }
            };
        }

        private BaselineRepository baselineRepo() {
            return new BaselineRepository() {
                @Override
                public Baseline save(Baseline baseline) {
                    return baseline;
                }

                @Override
                public List<BaselineItem> saveItems(List<BaselineItem> items) {
                    return items;
                }

                @Override
                public List<Baseline> findByProjectId(Long projectId) {
                    return List.of();
                }

                @Override
                public List<BaselineItem> findItemsByBaselineId(Long baselineId) {
                    return List.of();
                }
            };
        }

        private SprintRepository sprintRepo() {
            return new SprintRepository() {
                @Override
                public Sprint save(Sprint sprint) {
                    return sprint;
                }

                @Override
                public Optional<Sprint> findById(Long id) {
                    return Optional.empty();
                }

                @Override
                public List<Sprint> findByProjectId(Long projectId) {
                    return List.of();
                }

                @Override
                public void delete(Sprint sprint) {
                }
            };
        }

        private SprintItemRepository sprintItemRepo() {
            return new SprintItemRepository() {
                @Override
                public SprintItem save(SprintItem item) {
                    return item;
                }

                @Override
                public List<SprintItem> saveAll(List<SprintItem> items) {
                    return items;
                }

                @Override
                public List<SprintItem> findByProjectId(Long projectId) {
                    return List.of();
                }
            };
        }

        private AcceptanceCheckpointRepository checkpointRepo() {
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

        private ProjectRepository projectRepo() {
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

    // ------------------------------------------------------------------ 도우미

    private WbsItem newItem(String name, ExecutionMode mode, LocalDate start, LocalDate end,
                              int progress) {
        WbsItem item = new WbsItem(PROJECT_ID, null, name, null, start, end, progress, 0,
                WbsNodeType.WORK_PACKAGE, mode);
        ReflectionTestUtils.setField(item, "id", ids.incrementAndGet());
        return item;
    }

    private WbsNode leaf(WbsItem item) {
        return new WbsNode(item, "1", 1, item.getStartDate(), item.getEndDate(),
                item.getProgress(), (ExecutionModeSummary) null, List.of());
    }

    private ProgressResult notEstimable() {
        return new ProgressResult(null, ProgressBasis.NOT_ESTIMABLE, false, false,
                "집계 대상 Story·Bug가 없습니다.");
    }
}
