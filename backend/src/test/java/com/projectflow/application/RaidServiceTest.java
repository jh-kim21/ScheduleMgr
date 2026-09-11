package com.projectflow.application;

import com.projectflow.application.dto.RaidItemRequest;
import com.projectflow.application.dto.RaidLinkRequest;
import com.projectflow.application.dto.RaidLogResponse;
import com.projectflow.domain.BacklogItem;
import com.projectflow.domain.BacklogItemRepository;
import com.projectflow.domain.BacklogItemType;
import com.projectflow.domain.BacklogPriority;
import com.projectflow.domain.BacklogStatus;
import com.projectflow.domain.InvalidRaidLinkException;
import com.projectflow.domain.Project;
import com.projectflow.domain.ProjectMember;
import com.projectflow.domain.ProjectMemberRepository;
import com.projectflow.domain.ProjectRepository;
import com.projectflow.domain.ProjectStatus;
import com.projectflow.domain.RaidItem;
import com.projectflow.domain.RaidItemRepository;
import com.projectflow.domain.RaidLink;
import com.projectflow.domain.RaidLinkRepository;
import com.projectflow.domain.RaidLinkTarget;
import com.projectflow.domain.RaidStatus;
import com.projectflow.domain.RaidType;
import com.projectflow.domain.Sprint;
import com.projectflow.domain.SprintRepository;
import com.projectflow.domain.SprintStatus;
import com.projectflow.domain.WbsItem;
import com.projectflow.domain.WbsItemRepository;
import com.projectflow.domain.WbsNodeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("RAID 연결 (Step 6)")
class RaidServiceTest {

    private static final Long PROJECT_ID = 1L;
    private static final Long OTHER_PROJECT_ID = 2L;

    private final AtomicLong ids = new AtomicLong(100);
    private final List<RaidItem> raidItems = new ArrayList<>();
    private final List<RaidLink> raidLinks = new ArrayList<>();
    private final List<WbsItem> wbsItems = new ArrayList<>();
    private final List<Sprint> sprints = new ArrayList<>();
    private final List<BacklogItem> backlogItems = new ArrayList<>();

    private RaidService service;
    private Long workPackage;
    private Long sprint;
    private Long story;

    @BeforeEach
    void setUp() {
        service = new RaidService(raidItemRepository(), raidLinkRepository(), memberRepository(),
                wbsItemRepository(), sprintRepository(), backlogItemRepository(),
                projectRepository());
        workPackage = addWbsItem(PROJECT_ID, "개발");
        sprint = addSprint(PROJECT_ID, "Sprint 1");
        story = addBacklogItem(PROJECT_ID, "로그인 화면");
    }

    @Nested
    @DisplayName("한 항목을 여러 대상에 건다")
    class MultipleLinks {

        @Test
        @DisplayName("WBS·Sprint·Backlog를 한 항목에 함께 연결한다")
        void linksAllThreeKinds() {
            RaidLogResponse log = service.addItem(PROJECT_ID, request("외부 API 지연", List.of(
                    link(RaidLinkTarget.WBS_ITEM, workPackage),
                    link(RaidLinkTarget.SPRINT, sprint),
                    link(RaidLinkTarget.BACKLOG_ITEM, story))));

            assertThat(log.items()).singleElement().satisfies(item ->
                    assertThat(item.links())
                            .extracting(RaidLogResponse.RaidLinkResponse::targetType)
                            .containsExactly(RaidLinkTarget.WBS_ITEM, RaidLinkTarget.SPRINT,
                                    RaidLinkTarget.BACKLOG_ITEM));
        }

        @Test
        @DisplayName("대상의 이름은 서버가 붙인다 — WBS는 코드까지")
        void resolvesTargetNames() {
            RaidLogResponse log = service.addItem(PROJECT_ID, request("외부 API 지연", List.of(
                    link(RaidLinkTarget.WBS_ITEM, workPackage),
                    link(RaidLinkTarget.SPRINT, sprint))));

            List<RaidLogResponse.RaidLinkResponse> links = log.items().get(0).links();
            assertThat(links.get(0).targetCode()).isEqualTo("1");
            assertThat(links.get(0).targetName()).isEqualTo("개발");
            assertThat(links.get(1).targetCode()).isNull();
            assertThat(links.get(1).targetName()).isEqualTo("Sprint 1");
        }

        @Test
        @DisplayName("같은 대상을 두 번 걸면 거부한다")
        void refusesDuplicateLink() {
            assertThatThrownBy(() -> service.addItem(PROJECT_ID, request("중복", List.of(
                    link(RaidLinkTarget.WBS_ITEM, workPackage),
                    link(RaidLinkTarget.WBS_ITEM, workPackage)))))
                    .isInstanceOf(InvalidRaidLinkException.class)
                    .hasMessageContaining("두 번");
        }

        @Test
        @DisplayName("다른 프로젝트의 대상은 거부한다 — target_id에 FK가 없어 여기가 유일한 방어선")
        void refusesTargetFromAnotherProject() {
            Long foreign = addWbsItem(OTHER_PROJECT_ID, "남의 업무");

            assertThatThrownBy(() -> service.addItem(PROJECT_ID,
                    request("잘못된 연결", List.of(link(RaidLinkTarget.WBS_ITEM, foreign)))))
                    .isInstanceOf(InvalidRaidLinkException.class)
                    .hasMessageContaining("이 프로젝트에 없는");
        }

        @Test
        @DisplayName("연결 없이도 등록된다 — 프로젝트 전체에 대한 항목")
        void allowsNoLinks() {
            RaidLogResponse log = service.addItem(PROJECT_ID, request("전사 인력난", List.of()));

            assertThat(log.items()).singleElement()
                    .satisfies(item -> assertThat(item.links()).isEmpty());
        }
    }

    @Nested
    @DisplayName("수정은 남길 것을 남긴다")
    class Editing {

        @Test
        @DisplayName("그대로 둔 연결은 같은 행이다 — 붙인 시점이 매번 오늘로 바뀌지 않는다")
        void keepsUnchangedLinks() {
            service.addItem(PROJECT_ID, request("외부 API 지연", List.of(
                    link(RaidLinkTarget.WBS_ITEM, workPackage))));
            Long linkId = raidLinks.get(0).getId();
            Long itemId = raidItems.get(0).getId();

            service.updateItem(PROJECT_ID, itemId, request("외부 API 지연", List.of(
                    link(RaidLinkTarget.WBS_ITEM, workPackage),
                    link(RaidLinkTarget.SPRINT, sprint))));

            assertThat(raidLinks).hasSize(2);
            assertThat(raidLinks)
                    .filteredOn(link -> link.getTargetType() == RaidLinkTarget.WBS_ITEM)
                    .singleElement()
                    .satisfies(link -> assertThat(link.getId()).isEqualTo(linkId));
        }

        @Test
        @DisplayName("빠진 연결은 지운다")
        void dropsRemovedLinks() {
            service.addItem(PROJECT_ID, request("외부 API 지연", List.of(
                    link(RaidLinkTarget.WBS_ITEM, workPackage),
                    link(RaidLinkTarget.SPRINT, sprint))));
            Long itemId = raidItems.get(0).getId();

            service.updateItem(PROJECT_ID, itemId, request("외부 API 지연", List.of(
                    link(RaidLinkTarget.SPRINT, sprint))));

            assertThat(raidLinks).singleElement().satisfies(link ->
                    assertThat(link.getTargetType()).isEqualTo(RaidLinkTarget.SPRINT));
        }
    }

    @Nested
    @DisplayName("대상이 사라질 때")
    class Detaching {

        @Test
        @DisplayName("연결만 끊고 항목은 남긴다")
        void detachesWithoutDeletingTheEntry() {
            service.addItem(PROJECT_ID, request("외부 API 지연", List.of(
                    link(RaidLinkTarget.WBS_ITEM, workPackage),
                    link(RaidLinkTarget.SPRINT, sprint))));

            service.detachTargets(PROJECT_ID, RaidLinkTarget.WBS_ITEM, java.util.Set.of(workPackage));

            assertThat(raidItems).hasSize(1);
            assertThat(raidLinks).singleElement().satisfies(link ->
                    assertThat(link.getTargetType()).isEqualTo(RaidLinkTarget.SPRINT));
        }

        @Test
        @DisplayName("다른 프로젝트의 같은 id는 건드리지 않는다")
        void leavesOtherProjectsAlone() {
            service.addItem(PROJECT_ID, request("외부 API 지연", List.of(
                    link(RaidLinkTarget.WBS_ITEM, workPackage))));
            raidLinks.add(new RaidLink(OTHER_PROJECT_ID, 999L, RaidLinkTarget.WBS_ITEM, workPackage));

            service.detachTargets(PROJECT_ID, RaidLinkTarget.WBS_ITEM, java.util.Set.of(workPackage));

            assertThat(raidLinks).singleElement().satisfies(link ->
                    assertThat(link.getProjectId()).isEqualTo(OTHER_PROJECT_ID));
        }
    }

    @Test
    @DisplayName("항목을 지우면 그 연결도 함께 사라진다")
    void deletingAnEntryTakesItsLinks() {
        service.addItem(PROJECT_ID, request("외부 API 지연", List.of(
                link(RaidLinkTarget.WBS_ITEM, workPackage))));

        service.deleteItem(PROJECT_ID, raidItems.get(0).getId());

        assertThat(raidItems).isEmpty();
        assertThat(raidLinks).isEmpty();
    }

    // ------------------------------------------------------------------ 도우미

    private RaidItemRequest request(String title, List<RaidLinkRequest> links) {
        return new RaidItemRequest(RaidType.RISK, title, null, RaidStatus.OPEN, null, null, null,
                links, LocalDate.of(2026, 1, 1), null);
    }

    private RaidLinkRequest link(RaidLinkTarget targetType, Long targetId) {
        return new RaidLinkRequest(targetType, targetId);
    }

    private Long addWbsItem(Long projectId, String name) {
        WbsItem item = new WbsItem(projectId, null, name, null, null, null, 0, wbsItems.size(),
                WbsNodeType.WORK_PACKAGE, null);
        ReflectionTestUtils.setField(item, "id", ids.incrementAndGet());
        wbsItems.add(item);
        return item.getId();
    }

    private Long addSprint(Long projectId, String name) {
        Sprint entry = new Sprint(projectId, name, null, LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 14), SprintStatus.PLANNED, null);
        ReflectionTestUtils.setField(entry, "id", ids.incrementAndGet());
        sprints.add(entry);
        return entry.getId();
    }

    private Long addBacklogItem(Long projectId, String title) {
        BacklogItem item = new BacklogItem(projectId, workPackage, null, BacklogItemType.STORY,
                title, null, BacklogPriority.MEDIUM, BacklogStatus.TODO, null, null, null, null,
                backlogItems.size());
        ReflectionTestUtils.setField(item, "id", ids.incrementAndGet());
        backlogItems.add(item);
        return item.getId();
    }

    private RaidItemRepository raidItemRepository() {
        return new RaidItemRepository() {
            @Override
            public RaidItem save(RaidItem item) {
                if (item.getId() == null) {
                    ReflectionTestUtils.setField(item, "id", ids.incrementAndGet());
                    raidItems.add(item);
                }
                return item;
            }

            @Override
            public Optional<RaidItem> findById(Long id) {
                return raidItems.stream().filter(item -> item.getId().equals(id)).findFirst();
            }

            @Override
            public List<RaidItem> findByProjectId(Long projectId) {
                return raidItems.stream()
                        .filter(item -> item.getProjectId().equals(projectId))
                        .toList();
            }

            @Override
            public void delete(RaidItem item) {
                raidItems.remove(item);
            }
        };
    }

    private RaidLinkRepository raidLinkRepository() {
        return new RaidLinkRepository() {
            @Override
            public RaidLink save(RaidLink link) {
                if (link.getId() == null) {
                    ReflectionTestUtils.setField(link, "id", ids.incrementAndGet());
                    raidLinks.add(link);
                }
                return link;
            }

            @Override
            public List<RaidLink> saveAll(List<RaidLink> batch) {
                batch.forEach(this::save);
                return batch;
            }

            @Override
            public List<RaidLink> findByProjectId(Long projectId) {
                return raidLinks.stream()
                        .filter(link -> link.getProjectId().equals(projectId))
                        .toList();
            }

            @Override
            public void deleteAll(List<RaidLink> removed) {
                raidLinks.removeAll(removed);
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
            public List<WbsItem> saveAll(List<WbsItem> batch) {
                return batch;
            }

            @Override
            public Optional<WbsItem> findById(Long id) {
                return wbsItems.stream().filter(item -> item.getId().equals(id)).findFirst();
            }

            @Override
            public List<WbsItem> findByProjectId(Long projectId) {
                return wbsItems.stream()
                        .filter(item -> item.getProjectId().equals(projectId))
                        .toList();
            }

            @Override
            public void delete(WbsItem item) {
                wbsItems.remove(item);
            }
        };
    }

    private SprintRepository sprintRepository() {
        return new SprintRepository() {
            @Override
            public Sprint save(Sprint entry) {
                return entry;
            }

            @Override
            public Optional<Sprint> findById(Long id) {
                return sprints.stream().filter(entry -> entry.getId().equals(id)).findFirst();
            }

            @Override
            public List<Sprint> findByProjectId(Long projectId) {
                return sprints.stream()
                        .filter(entry -> entry.getProjectId().equals(projectId))
                        .toList();
            }

            @Override
            public void delete(Sprint entry) {
                sprints.remove(entry);
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
            public List<BacklogItem> saveAll(List<BacklogItem> batch) {
                return batch;
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

    private ProjectRepository projectRepository() {
        Project project = new Project("AEGIS", null, ProjectStatus.IN_PROGRESS, null, null);
        ReflectionTestUtils.setField(project, "id", PROJECT_ID);
        return new ProjectRepository() {
            @Override
            public Project save(Project entry) {
                return entry;
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
                return PROJECT_ID.equals(id) || OTHER_PROJECT_ID.equals(id);
            }
        };
    }
}
