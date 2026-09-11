package com.projectflow.application;

import com.projectflow.application.dto.BacklogItemRequest;
import com.projectflow.application.dto.BacklogResponse;
import com.projectflow.application.dto.BacklogResponse.BacklogItemResponse;
import com.projectflow.application.dto.BacklogSummary;
import com.projectflow.domain.BacklogItem;
import com.projectflow.domain.BacklogItemRepository;
import com.projectflow.domain.BacklogItemType;
import com.projectflow.domain.ChangeLog;
import com.projectflow.domain.ChangeLogRepository;
import com.projectflow.domain.ChangeReason;
import com.projectflow.domain.BacklogPriority;
import com.projectflow.domain.BacklogStatus;
import com.projectflow.domain.ExecutionMode;
import com.projectflow.domain.InvalidBacklogItemException;
import com.projectflow.domain.Project;
import com.projectflow.domain.ProjectMember;
import com.projectflow.domain.ProjectMemberNotFoundException;
import com.projectflow.domain.ProjectMemberRepository;
import com.projectflow.domain.ProjectStatus;
import com.projectflow.domain.Sprint;
import com.projectflow.domain.SprintItem;
import com.projectflow.domain.SprintItemRepository;
import com.projectflow.domain.SprintRepository;
import com.projectflow.domain.ProjectRepository;
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
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The Step 3 rules: what a Backlog entry may be linked to, how the hierarchy may be shaped, and
 * which of the resulting problems are refused rather than reported.
 *
 * <p>In-memory fakes, as in {@code ImportServiceTest} — the interesting behaviour is what ends up
 * stored (an inherited link, a recorded history row), which mocks would hide.
 */
class BacklogServiceTest {

    private static final Long PROJECT_ID = 1L;

    private final AtomicLong ids = new AtomicLong(500);
    private final List<BacklogItem> items = new ArrayList<>();
    private final List<ChangeLog> changes = new ArrayList<>();
    private final List<WbsItem> wbsItems = new ArrayList<>();
    private final List<ProjectMember> members = new ArrayList<>();

    private BacklogService service;

    /** An Agile Work Package, a Waterfall one and a Summary — the three link targets that matter. */
    private Long agilePackage;
    private Long waterfallPackage;
    private Long summary;
    /** Shared link store, so a test can seed a RAID link and check it was detached. */
    private TestRaidService raid;

    @BeforeEach
    void setUp() {
        raid = TestRaidService.create();
        service = new BacklogService(backlogItemRepository(), changeLogRepository(),
                wbsItemRepository(), memberRepository(), projectRepository(), sprintService(),
                raid.service());
        agilePackage = addWbsItem("개발", WbsNodeType.WORK_PACKAGE, ExecutionMode.AGILE);
        waterfallPackage = addWbsItem("인수", WbsNodeType.WORK_PACKAGE, ExecutionMode.WATERFALL);
        summary = addWbsItem("단계", WbsNodeType.SUMMARY, null);
    }

    @Nested
    @DisplayName("Work Package 귀속")
    class Linking {

        @Test
        @DisplayName("Work Package에 귀속시키고 코드·실행 방식과 함께 조회한다")
        void linksToWorkPackage() {
            service.addItem(PROJECT_ID, request(agilePackage, null, BacklogItemType.STORY, "WBS 계층 등록"));

            BacklogItemResponse row = only(service.getBacklog(PROJECT_ID));
            assertThat(row.wbsItemId()).isEqualTo(agilePackage);
            assertThat(row.wbsCode()).isEqualTo("1");
            assertThat(row.wbsExecutionMode()).isEqualTo(ExecutionMode.AGILE);
            assertThat(row.unlinked()).isFalse();
            assertThat(row.requiresExecutionModeChange()).isFalse();
            assertThat(row.readyForSprint()).isTrue();
        }

        @Test
        @DisplayName("Summary에는 귀속시킬 수 없다")
        void rejectsSummary() {
            assertThatThrownBy(() -> service.addItem(PROJECT_ID,
                    request(summary, null, BacklogItemType.STORY, "잘못된 귀속")))
                    .isInstanceOf(InvalidBacklogItemException.class)
                    .hasMessageContaining("Summary");
            assertThat(items).isEmpty();
        }

        @Test
        @DisplayName("미연결 초안은 허용하되 표시한다 — Sprint 투입 대상은 아니다")
        void allowsUnlinkedDraft() {
            service.addItem(PROJECT_ID, request(null, null, BacklogItemType.STORY, "초안"));

            BacklogResponse backlog = service.getBacklog(PROJECT_ID);
            assertThat(backlog.unlinkedCount()).isEqualTo(1);
            assertThat(only(backlog).unlinked()).isTrue();
            assertThat(only(backlog).readyForSprint()).isFalse();
        }

        @Test
        @DisplayName("Waterfall·미지정 Work Package는 막지 않고 실행 방식 변경이 필요하다고 알린다")
        void warnsAboutNonAgilePackage() {
            service.addItem(PROJECT_ID,
                    request(waterfallPackage, null, BacklogItemType.STORY, "폭포수 아래의 이야기"));

            BacklogItemResponse row = only(service.getBacklog(PROJECT_ID));
            assertThat(row.requiresExecutionModeChange()).isTrue();
            // 안내일 뿐이므로 Sprint 배정 자체를 막지는 않는다.
            assertThat(row.readyForSprint()).isTrue();
        }

        @Test
        @DisplayName("귀속 대상이 나중에 Summary가 되면 연결이 쓸 수 없는 상태로 표시된다")
        void reportsLinkThatBecameSummary() {
            service.addItem(PROJECT_ID, request(agilePackage, null, BacklogItemType.STORY, "이야기"));
            // Step 2는 Work Package → Summary 전환을 허용한다. 그때 이 연결이 이렇게 남는다.
            convertToSummary(agilePackage);

            BacklogItemResponse row = only(service.getBacklog(PROJECT_ID));
            assertThat(row.linkedToSummary()).isTrue();
            assertThat(row.readyForSprint()).isFalse();
        }

        @Test
        @DisplayName("다른 프로젝트의 구성원은 담당자가 될 수 없다")
        void rejectsForeignAssignee() {
            assertThatThrownBy(() -> service.addItem(PROJECT_ID, new BacklogItemRequest(
                    agilePackage, null, BacklogItemType.STORY, "이야기", null,
                    BacklogPriority.MEDIUM, BacklogStatus.TODO, 9999L, null, null, null, null)))
                    .isInstanceOf(ProjectMemberNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("계층")
    class Hierarchy {

        @Test
        @DisplayName("Task는 상위 항목이 있어야 한다")
        void rejectsOrphanTask() {
            assertThatThrownBy(() -> service.addItem(PROJECT_ID,
                    request(agilePackage, null, BacklogItemType.TASK, "떠도는 Task")))
                    .isInstanceOf(InvalidBacklogItemException.class)
                    .hasMessageContaining("Task");
        }

        @Test
        @DisplayName("Epic은 다른 항목의 하위가 될 수 없다")
        void rejectsNestedEpic() {
            Long epic = add(request(agilePackage, null, BacklogItemType.EPIC, "묶음"));

            assertThatThrownBy(() -> service.addItem(PROJECT_ID,
                    request(null, epic, BacklogItemType.EPIC, "다른 묶음")))
                    .isInstanceOf(InvalidBacklogItemException.class)
                    .hasMessageContaining("Epic");
        }

        @Test
        @DisplayName("Epic 아래에는 Task를 둘 수 없다 (Story·Bug만)")
        void rejectsTaskUnderEpic() {
            Long epic = add(request(agilePackage, null, BacklogItemType.EPIC, "묶음"));

            assertThatThrownBy(() -> service.addItem(PROJECT_ID,
                    request(null, epic, BacklogItemType.TASK, "잘못된 Task")))
                    .isInstanceOf(InvalidBacklogItemException.class);
        }

        @Test
        @DisplayName("하위는 상위의 귀속을 물려받고, 다른 귀속을 지정하면 거부한다")
        void childInheritsParentLink() {
            Long epic = add(request(agilePackage, null, BacklogItemType.EPIC, "묶음"));

            Long story = add(request(null, epic, BacklogItemType.STORY, "이야기"));
            assertThat(byId(story).getWbsItemId()).isEqualTo(agilePackage);

            assertThatThrownBy(() -> service.addItem(PROJECT_ID,
                    request(waterfallPackage, epic, BacklogItemType.STORY, "다른 귀속을 주장")))
                    .isInstanceOf(InvalidBacklogItemException.class)
                    .hasMessageContaining("상위 항목을 따릅니다");
        }

        @Test
        @DisplayName("상위를 옮기면 하위 전체가 따라가고 각각 이력을 남긴다")
        void reattachCascadesToDescendants() {
            Long epic = add(request(agilePackage, null, BacklogItemType.EPIC, "묶음"));
            Long story = add(request(null, epic, BacklogItemType.STORY, "이야기"));
            Long task = add(request(null, story, BacklogItemType.TASK, "작업"));
            changes.clear();

            service.updateItem(PROJECT_ID, epic,
                    request(waterfallPackage, null, BacklogItemType.EPIC, "묶음"));

            assertThat(byId(epic).getWbsItemId()).isEqualTo(waterfallPackage);
            assertThat(byId(story).getWbsItemId()).isEqualTo(waterfallPackage);
            assertThat(byId(task).getWbsItemId()).isEqualTo(waterfallPackage);

            assertThat(changes).hasSize(3);
            assertThat(changes).extracting(ChangeLog::getReason)
                    .containsExactly(
                            ChangeReason.REASSIGNED,
                            ChangeReason.INHERITED_FROM_PARENT,
                            ChangeReason.INHERITED_FROM_PARENT);
        }

        @Test
        @DisplayName("자기 하위를 상위로 지정할 수 없다 (여기서는 Epic 규칙이 먼저 잡는다)")
        void rejectsCycle() {
            Long epic = add(request(agilePackage, null, BacklogItemType.EPIC, "묶음"));
            Long story = add(request(null, epic, BacklogItemType.STORY, "이야기"));

            assertThatThrownBy(() -> service.updateItem(PROJECT_ID, epic,
                    request(agilePackage, story, BacklogItemType.EPIC, "묶음")))
                    .isInstanceOf(InvalidBacklogItemException.class);
        }

        @Test
        @DisplayName("하위가 있는 항목의 유형은 하위를 고아로 만들도록 바꿀 수 없다")
        void rejectsTypeChangeThatOrphansChildren() {
            Long story = add(request(agilePackage, null, BacklogItemType.STORY, "이야기"));
            add(request(null, story, BacklogItemType.TASK, "작업"));

            assertThatThrownBy(() -> service.updateItem(PROJECT_ID, story,
                    request(agilePackage, null, BacklogItemType.EPIC, "이야기")))
                    .isInstanceOf(InvalidBacklogItemException.class);
        }

        @Test
        @DisplayName("한 가족이 붙어서 보이고 depth가 들여쓰기를 알려준다")
        void ordersFamiliesTogether() {
            Long epic = add(request(agilePackage, null, BacklogItemType.EPIC, "묶음"));
            Long story = add(request(null, epic, BacklogItemType.STORY, "이야기"));
            add(request(null, story, BacklogItemType.TASK, "작업"));
            add(request(agilePackage, null, BacklogItemType.BUG, "결함"));

            assertThat(service.getBacklog(PROJECT_ID).items())
                    .extracting(BacklogItemResponse::title, BacklogItemResponse::depth)
                    .containsExactly(
                            org.assertj.core.groups.Tuple.tuple("묶음", 0),
                            org.assertj.core.groups.Tuple.tuple("이야기", 1),
                            org.assertj.core.groups.Tuple.tuple("작업", 2),
                            org.assertj.core.groups.Tuple.tuple("결함", 0));
        }
    }

    @Nested
    @DisplayName("집계 단위와 보관")
    class AggregationAndArchive {

        @Test
        @DisplayName("Story·Bug만 집계 대상이다 — Epic과 Task는 아니다")
        void marksAggregationUnits() {
            Long epic = add(request(agilePackage, null, BacklogItemType.EPIC, "묶음"));
            Long story = add(request(null, epic, BacklogItemType.STORY, "이야기"));
            add(request(null, story, BacklogItemType.TASK, "작업"));
            add(request(agilePackage, null, BacklogItemType.BUG, "결함"));

            assertThat(service.getBacklog(PROJECT_ID).items())
                    .extracting(BacklogItemResponse::title, BacklogItemResponse::aggregated)
                    .containsExactly(
                            org.assertj.core.groups.Tuple.tuple("묶음", false),
                            org.assertj.core.groups.Tuple.tuple("이야기", true),
                            org.assertj.core.groups.Tuple.tuple("작업", false),
                            org.assertj.core.groups.Tuple.tuple("결함", true));
        }

        @Test
        @DisplayName("Epic과 Task는 Sprint 투입 대상이 아니다")
        void epicAndTaskAreNotSprintReady() {
            Long epic = add(request(agilePackage, null, BacklogItemType.EPIC, "묶음"));
            Long story = add(request(null, epic, BacklogItemType.STORY, "이야기"));
            add(request(null, story, BacklogItemType.TASK, "작업"));

            Map<String, Boolean> ready = new java.util.HashMap<>();
            service.getBacklog(PROJECT_ID).items()
                    .forEach(row -> ready.put(row.title(), row.readyForSprint()));
            assertThat(ready).containsEntry("묶음", false)
                    .containsEntry("이야기", true)
                    .containsEntry("작업", false);
        }

        @Test
        @DisplayName("보관하면 상태는 그대로 두고 Sprint 대상에서만 빠진다")
        void archiveKeepsStatus() {
            Long story = add(new BacklogItemRequest(agilePackage, null, BacklogItemType.STORY,
                    "이야기", null, BacklogPriority.MEDIUM, BacklogStatus.REVIEW, null, null, null,
                    null, null));

            service.setArchived(PROJECT_ID, story, true);

            BacklogItemResponse row = only(service.getBacklog(PROJECT_ID));
            assertThat(row.archived()).isTrue();
            assertThat(row.status()).isEqualTo(BacklogStatus.REVIEW);
            assertThat(row.readyForSprint()).isFalse();

            service.setArchived(PROJECT_ID, story, false);
            assertThat(only(service.getBacklog(PROJECT_ID)).archived()).isFalse();
        }

        @Test
        @DisplayName("하위가 있는 항목은 삭제할 수 없다 — 연쇄 삭제로 하위를 잃지 않는다")
        void refusesDeleteWithChildren() {
            Long story = add(request(agilePackage, null, BacklogItemType.STORY, "이야기"));
            add(request(null, story, BacklogItemType.TASK, "작업"));

            assertThatThrownBy(() -> service.deleteItem(PROJECT_ID, story))
                    .isInstanceOf(InvalidBacklogItemException.class)
                    .hasMessageContaining("하위 항목");
            assertThat(items).hasSize(2);
        }

        @Test
        @DisplayName("WBS 화면이 쓰는 건수는 보관을 따로 센다")
        void summarisesForWbsScreen() {
            add(new BacklogItemRequest(agilePackage, null, BacklogItemType.STORY, "완료된 것", null,
                    BacklogPriority.MEDIUM, BacklogStatus.DONE, null, null, null, null, true));
            add(request(agilePackage, null, BacklogItemType.STORY, "진행 중"));
            Long archived = add(request(agilePackage, null, BacklogItemType.BUG, "접어둘 것"));
            service.setArchived(PROJECT_ID, archived, true);

            assertThat(service.summariesByWbsItem(PROJECT_ID))
                    .containsEntry(agilePackage, new BacklogSummary(2, 1, 1));
        }

        @Test
        @DisplayName("보관된 항목만 붙어 있으면 WBS 삭제를 막지 않는다")
        void archivedItemsDoNotBlockWbsDelete() {
            Long story = add(request(agilePackage, null, BacklogItemType.STORY, "이야기"));
            assertThat(service.activeItemsLinkedTo(PROJECT_ID, Set.of(agilePackage))).hasSize(1);

            service.setArchived(PROJECT_ID, story, true);
            assertThat(service.activeItemsLinkedTo(PROJECT_ID, Set.of(agilePackage))).isEmpty();
        }
    }

    // ------------------------------------------------------------------ 도우미

    private BacklogItemRequest request(Long wbsItemId, Long parentId, BacklogItemType type,
                                        String title) {
        return new BacklogItemRequest(wbsItemId, parentId, type, title, null,
                BacklogPriority.MEDIUM, BacklogStatus.TODO, null, null, null, null, null);
    }

    private Long add(BacklogItemRequest request) {
        service.addItem(PROJECT_ID, request);
        return items.stream()
                .filter(item -> item.getTitle().equals(request.title()))
                .findFirst()
                .orElseThrow()
                .getId();
    }

    private BacklogItem byId(Long id) {
        return items.stream().filter(item -> item.getId().equals(id)).findFirst().orElseThrow();
    }

    private BacklogItemResponse only(BacklogResponse backlog) {
        assertThat(backlog.items()).hasSize(1);
        return backlog.items().get(0);
    }

    private Long addWbsItem(String name, WbsNodeType nodeType, ExecutionMode mode) {
        WbsItem item = new WbsItem(PROJECT_ID, null, name, null, null, null, 0, wbsItems.size(),
                nodeType, mode);
        ReflectionTestUtils.setField(item, "id", ids.incrementAndGet());
        wbsItems.add(item);
        return item.getId();
    }

    private void convertToSummary(Long wbsItemId) {
        WbsItem item = wbsItems.stream()
                .filter(candidate -> candidate.getId().equals(wbsItemId))
                .findFirst()
                .orElseThrow();
        item.update(item.getName(), null, null, null, 0, WbsNodeType.SUMMARY,
                item.getExecutionMode(), null, null, null, null, null, null);
    }

    private BacklogItemRepository backlogItemRepository() {
        return new BacklogItemRepository() {
            @Override
            public BacklogItem save(BacklogItem item) {
                if (item.getId() == null) {
                    ReflectionTestUtils.setField(item, "id", ids.incrementAndGet());
                }
                if (!items.contains(item)) {
                    items.add(item);
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
                return items.stream().filter(item -> item.getId().equals(id)).findFirst();
            }

            @Override
            public List<BacklogItem> findByProjectId(Long projectId) {
                return items.stream().filter(item -> item.getProjectId().equals(projectId)).toList();
            }

            @Override
            public void delete(BacklogItem item) {
                items.remove(item);
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
                return wbsItems.stream().filter(item -> item.getId().equals(id)).findFirst();
            }

            @Override
            public List<WbsItem> findByProjectId(Long projectId) {
                return List.copyOf(wbsItems);
            }

            @Override
            public void delete(WbsItem item) {
                wbsItems.remove(item);
            }
        };
    }

    private ProjectMemberRepository memberRepository() {
        return new ProjectMemberRepository() {
            @Override
            public ProjectMember save(ProjectMember member) {
                members.add(member);
                return member;
            }

            @Override
            public Optional<ProjectMember> findById(Long id) {
                return members.stream().filter(member -> member.getId().equals(id)).findFirst();
            }

            @Override
            public List<ProjectMember> findByProjectId(Long projectId) {
                return List.copyOf(members);
            }

            @Override
            public void delete(ProjectMember member) {
                members.remove(member);
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
}
