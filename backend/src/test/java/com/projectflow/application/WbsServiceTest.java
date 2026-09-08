package com.projectflow.application;

import com.projectflow.application.dto.WbsItemCreateRequest;
import com.projectflow.application.dto.WbsItemMoveRequest;
import com.projectflow.application.dto.WbsItemUpdateRequest;
import com.projectflow.application.dto.WbsNodeResponse;
import com.projectflow.application.dto.WbsTreeResponse;
import com.projectflow.domain.BacklogItem;
import com.projectflow.domain.BacklogItemRepository;
import com.projectflow.domain.BacklogItemType;
import com.projectflow.domain.AcceptanceCheckpoint;
import com.projectflow.domain.AcceptanceCheckpointRepository;
import com.projectflow.domain.Baseline;
import com.projectflow.domain.BaselineItem;
import com.projectflow.domain.BaselineRepository;
import com.projectflow.domain.ChangeLog;
import com.projectflow.domain.ChangeLogRepository;
import com.projectflow.domain.ChangeReason;
import com.projectflow.domain.BacklogPriority;
import com.projectflow.domain.BacklogStatus;
import com.projectflow.domain.ExecutionMode;
import com.projectflow.domain.ExecutionModeSummary;
import com.projectflow.domain.InvalidWbsHierarchyException;
import com.projectflow.domain.Project;
import com.projectflow.domain.ProjectRepository;
import com.projectflow.domain.ProjectStatus;
import com.projectflow.domain.Sprint;
import com.projectflow.domain.SprintItem;
import com.projectflow.domain.SprintItemRepository;
import com.projectflow.domain.SprintRepository;
import com.projectflow.domain.ProjectMember;
import com.projectflow.domain.ProjectMemberRepository;
import com.projectflow.domain.WbsItem;
import com.projectflow.domain.WbsItemRepository;
import com.projectflow.domain.WbsNodeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

/**
 * The Step 2 rules that need the rest of the tree to decide, so they live in the service rather
 * than the entity: what may hold an execution mode, and what may gain children.
 *
 * <p>In-memory fakes rather than mocks, as in {@code ImportServiceTest} — these rules are about
 * what ends up stored, and the mode history is only observable by looking at what was saved.
 */
class WbsServiceTest {

    private static final Long PROJECT_ID = 1L;

    private final AtomicLong ids = new AtomicLong(100);
    private final List<WbsItem> items = new ArrayList<>();
    private final List<BacklogItem> backlogItems = new ArrayList<>();
    /** One store now: Step 5 folded the two narrow history tables into change_logs. */
    private final List<ChangeLog> changes = new ArrayList<>();

    private WbsService service;
    /** Shared link store, so a test can seed a RAID link and check it was detached. */
    private TestRaidService raid;

    @BeforeEach
    void setUp() {
        // 실제 BacklogService를 끼운다 — WBS 삭제 가드가 그 판정을 그대로 쓰기 때문에,
        // 가짜로 대체하면 정작 검증하려는 규칙이 빠진다.
        raid = TestRaidService.create();
        BacklogService backlogService = new BacklogService(
                backlogItemRepository(), changeLogRepository(), wbsItemRepository(),
                memberRepository(), projectRepository(), sprintService(), raid.service());
        service = new WbsService(wbsItemRepository(), projectRepository(), changeLogRepository(),
                backlogService, progressService(), raid.service());
    }

    @Nested
    @DisplayName("실행 방식")
    class Modes {

        @Test
        @DisplayName("새 항목은 기본이 Work Package이고 실행 방식은 미지정이다")
        void defaultsToUnspecifiedWorkPackage() {
            service.createItem(PROJECT_ID, create(null, "설계", null, null));

            WbsItem saved = byName("설계");
            assertThat(saved.getNodeType()).isEqualTo(WbsNodeType.WORK_PACKAGE);
            assertThat(saved.getExecutionMode()).isNull();
            // 미지정으로 만든 것은 변경이 아니므로 이력도 없다.
            assertThat(modeChanges()).isEmpty();
        }

        @Test
        @DisplayName("Work Package에 실행 방식을 지정하고 다시 조회할 수 있다")
        void storesModeOnWorkPackage() {
            service.createItem(PROJECT_ID, create(null, "개발", WbsNodeType.WORK_PACKAGE, ExecutionMode.AGILE));

            WbsNodeResponse node = onlyRoot(service.getTree(PROJECT_ID));
            assertThat(node.nodeType()).isEqualTo(WbsNodeType.WORK_PACKAGE);
            assertThat(node.executionMode()).isEqualTo(ExecutionMode.AGILE);
            // 자식이 없으므로 요약할 것이 없다 — 자기 실행 방식이 곧 답이다.
            assertThat(node.executionModeSummary()).isNull();
        }

        @Test
        @DisplayName("Summary에는 실행 방식을 지정할 수 없다")
        void rejectsModeOnSummary() {
            service.createItem(PROJECT_ID, create(null, "단계", WbsNodeType.SUMMARY, null));
            Long id = byName("단계").getId();

            assertThatThrownBy(() -> service.updateItem(PROJECT_ID, id,
                    update("단계", WbsNodeType.SUMMARY, ExecutionMode.AGILE)))
                    .isInstanceOf(InvalidWbsHierarchyException.class)
                    .hasMessageContaining("Summary");

            assertThatThrownBy(() -> service.createItem(PROJECT_ID,
                    create(null, "다른 단계", WbsNodeType.SUMMARY, ExecutionMode.WATERFALL)))
                    .isInstanceOf(InvalidWbsHierarchyException.class)
                    .hasMessageContaining("Summary");
        }

        @Test
        @DisplayName("Summary로 전환하면 실행 방식을 지우지 않고 보관하며, 되돌리면 살아난다")
        void retainsModeAcrossConversion() {
            service.createItem(PROJECT_ID, create(null, "개발", WbsNodeType.WORK_PACKAGE, ExecutionMode.AGILE));
            Long id = byName("개발").getId();

            // 폼은 보관값을 그대로 되돌려 보낸다 — 값이 그대로면 변경이 아니므로 통과해야 한다.
            service.updateItem(PROJECT_ID, id, update("개발", WbsNodeType.SUMMARY, ExecutionMode.AGILE));
            assertThat(byName("개발").getNodeType()).isEqualTo(WbsNodeType.SUMMARY);
            assertThat(byName("개발").getExecutionMode()).isEqualTo(ExecutionMode.AGILE);

            service.updateItem(PROJECT_ID, id, update("개발", WbsNodeType.WORK_PACKAGE, ExecutionMode.AGILE));
            assertThat(byName("개발").getExecutionMode()).isEqualTo(ExecutionMode.AGILE);
            // 값이 한 번도 바뀌지 않았으므로 생성 시점의 이력 한 건뿐이다.
            assertThat(modeChanges()).hasSize(1);
        }

        @Test
        @DisplayName("실행 방식이 바뀔 때마다 이력을 남긴다 (미지정 → 지정, 지정 → 미지정 포함)")
        void recordsEveryModeChange() {
            service.createItem(PROJECT_ID, create(null, "개발", null, null));
            Long id = byName("개발").getId();

            service.updateItem(PROJECT_ID, id, update("개발", null, ExecutionMode.AGILE));
            service.updateItem(PROJECT_ID, id, update("개발", null, ExecutionMode.HYBRID));
            service.updateItem(PROJECT_ID, id, update("개발", null, null));
            // 같은 값으로 다시 저장하는 것은 변경이 아니다.
            service.updateItem(PROJECT_ID, id, update("개발", null, null));

            assertThat(modeChanges()).extracting(ChangeLog::getBeforeValue, ChangeLog::getAfterValue)
                    .containsExactly(
                            tuple(null, "AGILE"),
                            tuple("AGILE", "HYBRID"),
                            tuple("HYBRID", null));
        }

        @Test
        @DisplayName("Summary는 하위 Work Package의 실행 방식을 요약한다")
        void summarisesDescendantModes() {
            service.createItem(PROJECT_ID, create(null, "단계", WbsNodeType.SUMMARY, null));
            Long stage = byName("단계").getId();
            service.createItem(PROJECT_ID, create(stage, "중간", WbsNodeType.SUMMARY, null));
            Long middle = byName("중간").getId();
            service.createItem(PROJECT_ID, create(middle, "개발", WbsNodeType.WORK_PACKAGE, ExecutionMode.AGILE));
            service.createItem(PROJECT_ID, create(middle, "인수", WbsNodeType.WORK_PACKAGE, ExecutionMode.WATERFALL));
            service.createItem(PROJECT_ID, create(stage, "미정 작업", WbsNodeType.WORK_PACKAGE, null));

            // 손자까지 센다 — 자식이 Summary뿐인 상위가 아무것도 보고하지 못하면 쓸모가 없다.
            assertThat(onlyRoot(service.getTree(PROJECT_ID)).executionModeSummary())
                    .isEqualTo(new ExecutionModeSummary(1, 1, 0, 1));
        }
    }

    @Nested
    @DisplayName("관리 단위 구분")
    class NodeTypes {

        @Test
        @DisplayName("Work Package 아래에는 항목을 추가할 수 없다")
        void rejectsChildUnderWorkPackage() {
            service.createItem(PROJECT_ID, create(null, "개발", WbsNodeType.WORK_PACKAGE, ExecutionMode.AGILE));
            Long id = byName("개발").getId();

            assertThatThrownBy(() -> service.createItem(PROJECT_ID, create(id, "하위", null, null)))
                    .isInstanceOf(InvalidWbsHierarchyException.class)
                    .hasMessageContaining("Work Package");
        }

        @Test
        @DisplayName("Work Package 아래로 이동시킬 수도 없다")
        void rejectsMoveUnderWorkPackage() {
            service.createItem(PROJECT_ID, create(null, "개발", WbsNodeType.WORK_PACKAGE, ExecutionMode.AGILE));
            service.createItem(PROJECT_ID, create(null, "다른 작업", null, null));
            Long target = byName("개발").getId();
            Long moving = byName("다른 작업").getId();

            assertThatThrownBy(() -> service.moveItem(PROJECT_ID, moving, new WbsItemMoveRequest(target, 0)))
                    .isInstanceOf(InvalidWbsHierarchyException.class)
                    .hasMessageContaining("Work Package");
        }

        @Test
        @DisplayName("Summary로 전환한 뒤에는 하위를 추가할 수 있다")
        void allowsChildAfterConversion() {
            service.createItem(PROJECT_ID, create(null, "개발", WbsNodeType.WORK_PACKAGE, ExecutionMode.AGILE));
            Long id = byName("개발").getId();

            service.updateItem(PROJECT_ID, id, update("개발", WbsNodeType.SUMMARY, ExecutionMode.AGILE));
            service.createItem(PROJECT_ID, create(id, "하위", WbsNodeType.WORK_PACKAGE, ExecutionMode.AGILE));

            assertThat(byName("하위").getParentId()).isEqualTo(id);
        }

        @Test
        @DisplayName("하위가 있는 항목은 Work Package로 되돌릴 수 없다")
        void rejectsDemotingParentToWorkPackage() {
            service.createItem(PROJECT_ID, create(null, "단계", WbsNodeType.SUMMARY, null));
            Long stage = byName("단계").getId();
            service.createItem(PROJECT_ID, create(stage, "하위", null, null));

            assertThatThrownBy(() -> service.updateItem(PROJECT_ID, stage,
                    update("단계", WbsNodeType.WORK_PACKAGE, null)))
                    .isInstanceOf(InvalidWbsHierarchyException.class)
                    .hasMessageContaining("하위 항목이 있는");
        }

        @Test
        @DisplayName("구분을 보내지 않는 클라이언트는 기존 값을 유지한다")
        void keepsNodeTypeWhenOmitted() {
            service.createItem(PROJECT_ID, create(null, "단계", WbsNodeType.SUMMARY, null));
            Long id = byName("단계").getId();

            service.updateItem(PROJECT_ID, id, update("단계 이름 변경", null, null));

            assertThat(byName("단계 이름 변경").getNodeType()).isEqualTo(WbsNodeType.SUMMARY);
        }
    }

    @Nested
    @DisplayName("Backlog가 붙은 WBS 삭제")
    class DeleteWithBacklog {

        @Test
        @DisplayName("연결된 Backlog가 있으면 삭제를 거부한다 — 연쇄 삭제로 실행 기록이 사라지면 안 된다")
        void refusesDeleteWithLinkedBacklog() {
            service.createItem(PROJECT_ID, create(null, "개발", WbsNodeType.WORK_PACKAGE, ExecutionMode.AGILE));
            Long workPackage = byName("개발").getId();
            linkBacklog(workPackage, "WBS 계층 등록", false);

            assertThatThrownBy(() -> service.deleteItem(PROJECT_ID, workPackage))
                    .isInstanceOf(InvalidWbsHierarchyException.class)
                    .hasMessageContaining("Backlog")
                    .hasMessageContaining("보관");
            assertThat(items).isNotEmpty();
        }

        @Test
        @DisplayName("하위에 붙은 Backlog도 상위 삭제를 막는다 — 연쇄 삭제 범위 전체를 본다")
        void refusesDeleteWhenDescendantIsLinked() {
            service.createItem(PROJECT_ID, create(null, "단계", WbsNodeType.SUMMARY, null));
            Long stage = byName("단계").getId();
            service.createItem(PROJECT_ID, create(stage, "개발", WbsNodeType.WORK_PACKAGE, ExecutionMode.AGILE));
            linkBacklog(byName("개발").getId(), "하위에 붙은 항목", false);

            assertThatThrownBy(() -> service.deleteItem(PROJECT_ID, stage))
                    .isInstanceOf(InvalidWbsHierarchyException.class)
                    .hasMessageContaining("Backlog");
        }

        @Test
        @DisplayName("보관된 항목은 삭제를 막지 않고, 사유를 남기며 분리된다")
        void archivedItemsDetachWithReason() {
            service.createItem(PROJECT_ID, create(null, "개발", WbsNodeType.WORK_PACKAGE, ExecutionMode.AGILE));
            Long workPackage = byName("개발").getId();
            linkBacklog(workPackage, "접어둔 항목", true);

            service.deleteItem(PROJECT_ID, workPackage);

            assertThat(items).isEmpty();
            assertThat(backlogItems).singleElement().satisfies(item -> {
                // 항목 자체는 남고 귀속만 비워진다.
                assertThat(item.getWbsItemId()).isNull();
                assertThat(item.archived()).isTrue();
            });
            assertThat(backlogChanges()).singleElement().satisfies(change -> {
                assertThat(change.getEntityType()).isEqualTo(ChangeLog.BACKLOG_ITEM);
                assertThat(change.getBeforeValue()).isEqualTo(String.valueOf(workPackage));
                assertThat(change.getAfterValue()).isNull();
                assertThat(change.getReason()).isEqualTo(ChangeReason.WBS_ITEM_DELETED);
            });
        }

        @Test
        @DisplayName("연결이 없으면 예전처럼 그대로 지운다")
        void deletesWhenNothingLinked() {
            service.createItem(PROJECT_ID, create(null, "개발", null, null));
            service.deleteItem(PROJECT_ID, byName("개발").getId());

            assertThat(items).isEmpty();
        }
    }

    /** Adds a Backlog entry straight to the fake store — this test is about the WBS side. */
    private void linkBacklog(Long wbsItemId, String title, boolean archived) {
        BacklogItem item = new BacklogItem(PROJECT_ID, wbsItemId, null, BacklogItemType.STORY,
                title, null, BacklogPriority.MEDIUM, BacklogStatus.TODO, null, null, null, null, 0);
        ReflectionTestUtils.setField(item, "id", ids.incrementAndGet());
        if (archived) {
            item.archive();
        }
        backlogItems.add(item);
    }

    /** Backlog link rows only — WBS mode edits land in the same store. */
    private List<ChangeLog> backlogChanges() {
        return changes.stream()
                .filter(c -> c.getEntityType().equals(ChangeLog.BACKLOG_ITEM))
                .toList();
    }

    /** Execution mode rows only — weight and ratio edits land in the same store. */
    private List<ChangeLog> modeChanges() {
        return changes.stream().filter(c -> c.getField().equals("executionMode")).toList();
    }

    // ------------------------------------------------------------------ 도우미

    private WbsItemCreateRequest create(Long parentId, String name,
                                         WbsNodeType nodeType, ExecutionMode mode) {
        return new WbsItemCreateRequest(parentId, name, null, null, null, null, nodeType,
                mode, null);
    }

    private WbsItemUpdateRequest update(String name, WbsNodeType nodeType, ExecutionMode mode) {
        return new WbsItemUpdateRequest(name, null, null, null, null, nodeType, mode,
                null, null, null, null, null, null);
    }

    private WbsNodeResponse onlyRoot(WbsTreeResponse tree) {
        assertThat(tree.nodes()).hasSize(1);
        return tree.nodes().getFirst();
    }

    private WbsItem byName(String name) {
        return items.stream()
                .filter(item -> item.getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("WBS 항목이 없습니다: " + name));
    }

    /** Upserts, unlike a plain append: the service saves entries it has already loaded. */
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

    /**
     * A real {@link SprintService} over empty Sprint stores. These tests are about the Backlog and
     * WBS rules; the guard that consults Sprints simply finds nothing, which is the state every
     * assertion here assumes.
     */
    private SprintService sprintService() {
        return new SprintService(
                new SprintRepository() {
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
                },
                new SprintItemRepository() {
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
                },
                backlogItemRepository(), wbsItemRepository(), memberRepository(),
                projectRepository(), raid.service());
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

    private BacklogItemRepository backlogItemRepository() {
        return new BacklogItemRepository() {
            @Override
            public BacklogItem save(BacklogItem item) {
                if (item.getId() == null) {
                    ReflectionTestUtils.setField(item, "id", ids.incrementAndGet());
                }
                if (!backlogItems.contains(item)) {
                    backlogItems.add(item);
                }
                return item;
            }

            @Override
            public List<BacklogItem> saveAll(List<BacklogItem> toSave) {
                toSave.forEach(this::save);
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

    private ChangeLogRepository changeLogRepository() {
        return new ChangeLogRepository() {
            @Override
            public ChangeLog save(ChangeLog change) {
                changes.add(change);
                return change;
            }

            @Override
            public List<ChangeLog> saveAll(List<ChangeLog> toSave) {
                changes.addAll(toSave);
                return toSave;
            }

            @Override
            public List<ChangeLog> findByProjectId(Long projectId) {
                return List.copyOf(changes);
            }
        };
    }

    /** Aggregation is not what these tests are about; empty stores make every figure 산정 전. */
    private ProgressService progressService() {
        return new ProgressService(wbsItemRepository(), backlogItemRepository(),
                new AcceptanceCheckpointRepository() {
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
                },
                new BaselineRepository() {
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
                },
                projectRepository());
    }

    private ProjectMemberRepository memberRepository() {
        return new ProjectMemberRepository() {
            @Override
            public ProjectMember save(ProjectMember member) {
                return member;
            }

            @Override
            public Optional<ProjectMember> findById(Long id) {
                return Optional.empty();
            }

            @Override
            public List<ProjectMember> findByProjectId(Long projectId) {
                return List.of();
            }

            @Override
            public void delete(ProjectMember member) {
            }
        };
    }

}
