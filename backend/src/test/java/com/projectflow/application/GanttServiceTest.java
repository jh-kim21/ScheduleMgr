package com.projectflow.application;

import com.projectflow.application.dto.GanttResponse;
import com.projectflow.application.dto.GanttResponse.GanttTaskResponse;
import com.projectflow.domain.AcceptanceCheckpoint;
import com.projectflow.domain.AcceptanceCheckpointRepository;
import com.projectflow.domain.BacklogItem;
import com.projectflow.domain.BacklogItemRepository;
import com.projectflow.domain.Baseline;
import com.projectflow.domain.BaselineItem;
import com.projectflow.domain.BaselineRepository;
import com.projectflow.domain.DelayStatus;
import com.projectflow.domain.ExecutionMode;
import com.projectflow.domain.Project;
import com.projectflow.domain.ProjectRepository;
import com.projectflow.domain.ProjectStatus;
import com.projectflow.domain.Sprint;
import com.projectflow.domain.SprintItem;
import com.projectflow.domain.SprintItemRepository;
import com.projectflow.domain.SprintRepository;
import com.projectflow.domain.WbsDependency;
import com.projectflow.domain.WbsDependencyRepository;
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
 * 결함 수정 검증(간트): {@link WbsNodeResponseTest}와 같은 계약을 간트가 지키는지 — 지연 판정은
 * 저장된 progress가 아니라 공통 집계({@code computedProgress})를 우선해야 한다. 간트는 WBS와는 다른
 * 코드 경로(WbsNodeResponse.from을 거치지 않는다)라 따로 확인이 필요하다.
 */
class GanttServiceTest {

    private static final Long PROJECT_ID = 1L;

    private final AtomicLong ids = new AtomicLong(400);
    private final List<WbsItem> items = new ArrayList<>();
    private final List<BacklogItem> backlogItems = new ArrayList<>();

    private GanttService service;

    @BeforeEach
    void setUp() {
        ProgressService progressService = new ProgressService(wbsItemRepository(),
                backlogItemRepository(), checkpointRepository(), baselineRepository(),
                projectRepository());
        service = new GanttService(wbsItemRepository(), dependencyRepository(), projectRepository(),
                baselineRepository(), sprintRepository(), sprintItemRepository(),
                backlogItemRepository(), progressService);
    }

    @Test
    @DisplayName("Agile Work Package는 Story가 모두 완료돼 있으면 저장된 progress와 무관하게 완료로 본다")
    void ganttUsesComputedProgressForDelay() {
        LocalDate today = LocalDate.now();
        WbsItem item = workPackage("개발", ExecutionMode.AGILE, 0,
                today.minusDays(20), today.minusDays(1));
        story(item, true);
        story(item, true);

        GanttResponse response = service.getGantt(PROJECT_ID);
        GanttTaskResponse task = response.tasks().stream()
                .filter(t -> t.id().equals(item.getId()))
                .findFirst()
                .orElseThrow();

        assertThat(task.computedProgress()).isEqualTo(100.0);
        assertThat(task.progress()).isZero(); // 저장값은 그대로 노출된다 — 판정만 바뀐다.
        assertThat(task.delayStatus()).isEqualTo(DelayStatus.COMPLETED);
        assertThat(task.delayDays()).isZero();
    }

    private WbsItem workPackage(String name, ExecutionMode mode, int progress,
                                 LocalDate start, LocalDate end) {
        WbsItem item = new WbsItem(PROJECT_ID, null, name, null, start, end, progress, items.size(),
                WbsNodeType.WORK_PACKAGE, mode);
        ReflectionTestUtils.setField(item, "id", ids.incrementAndGet());
        items.add(item);
        return item;
    }

    private void story(WbsItem owner, boolean done) {
        BacklogItem item = new BacklogItem(PROJECT_ID, owner.getId(), null,
                com.projectflow.domain.BacklogItemType.STORY, "스토리", null,
                com.projectflow.domain.BacklogPriority.MEDIUM,
                done ? com.projectflow.domain.BacklogStatus.DONE
                        : com.projectflow.domain.BacklogStatus.TODO,
                null, null, null, null, backlogItems.size());
        ReflectionTestUtils.setField(item, "id", ids.incrementAndGet());
        backlogItems.add(item);
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
                return backlogItems.stream().filter(item -> item.getId().equals(id)).findFirst();
            }

            @Override
            public List<BacklogItem> findByProjectId(Long projectId) {
                return backlogItems.stream()
                        .filter(item -> item.getProjectId().equals(projectId))
                        .toList();
            }

            @Override
            public void delete(BacklogItem item) {
                backlogItems.remove(item);
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
                return baseline;
            }

            @Override
            public List<BaselineItem> saveItems(List<BaselineItem> toSave) {
                return toSave;
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

    private WbsDependencyRepository dependencyRepository() {
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

    private SprintRepository sprintRepository() {
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

    private SprintItemRepository sprintItemRepository() {
        return new SprintItemRepository() {
            @Override
            public SprintItem save(SprintItem item) {
                return item;
            }

            @Override
            public List<SprintItem> saveAll(List<SprintItem> toSave) {
                return toSave;
            }

            @Override
            public List<SprintItem> findByProjectId(Long projectId) {
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
