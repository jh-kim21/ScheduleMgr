package com.projectflow.application;

import com.projectflow.application.dto.SprintRequests.BoardMoveRequest;
import com.projectflow.application.dto.SprintRequests.SprintAssignRequest;
import com.projectflow.application.dto.SprintRequests.SprintSaveRequest;
import com.projectflow.application.dto.SprintResponse;
import com.projectflow.application.dto.SprintResponse.SprintDetail;
import com.projectflow.application.dto.SprintResponse.SprintItemDetail;
import com.projectflow.domain.BacklogItem;
import com.projectflow.domain.BacklogItemRepository;
import com.projectflow.domain.BacklogItemType;
import com.projectflow.domain.BacklogPriority;
import com.projectflow.domain.BacklogStatus;
import com.projectflow.domain.ExecutionMode;
import com.projectflow.domain.InvalidSprintException;
import com.projectflow.domain.Project;
import com.projectflow.domain.ProjectMember;
import com.projectflow.domain.ProjectMemberRepository;
import com.projectflow.domain.ProjectRepository;
import com.projectflow.domain.ProjectStatus;
import com.projectflow.domain.Sprint;
import com.projectflow.domain.SprintItem;
import com.projectflow.domain.SprintItemRepository;
import com.projectflow.domain.SprintRepository;
import com.projectflow.domain.SprintStatus;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Sprint 시작 취소({@code ACTIVE → PLANNED}) 계약을 고정한다 (리더가 배정한 스펙).
 *
 * <p>Tester 전용 파일이다. {@code SprintServiceTest.java}는 Developer1이 동시에 편집 중이라 건드리지
 * 않고, 그 파일의 가짜 리포지토리 구성 방식만 그대로 옮겨왔다 — 두 파일은 서로 독립적으로 같은
 * 계약을 검증한다.
 *
 * <p>계약(리더 고정, 지시서 그대로):
 * <ul>
 *   <li>{@code ACTIVE → PLANNED}만 허용, 그 외 상태는 {@link InvalidSprintException}</li>
 *   <li>{@code pointsAtStart}는 지워진다</li>
 *   <li>배정 행은 남는다(제거되지 않는다)</li>
 *   <li>Backlog 항목 상태는 바뀌지 않는다 — 완료된 항목이 있어도 취소를 막지 않는다</li>
 * </ul>
 *
 * <p>서비스 메서드 이름은 Developer1이 정하기로 되어 있었다. 읽어보니 이미
 * {@code SprintService.cancelStart(projectId, sprintId)}로 구현되어 있어 추측할 필요가 없었다.
 */
class SprintCancelStartTest {

    private static final Long PROJECT_ID = 1L;
    private static final LocalDate FROM = LocalDate.parse("2026-09-01");
    private static final LocalDate TO = LocalDate.parse("2026-09-14");

    private final AtomicLong ids = new AtomicLong(900);
    private final List<Sprint> sprints = new ArrayList<>();
    private final List<SprintItem> assignments = new ArrayList<>();
    private final List<BacklogItem> backlogItems = new ArrayList<>();
    private final List<WbsItem> wbsItems = new ArrayList<>();

    private SprintService service;
    private Long workPackage;

    @BeforeEach
    void setUp() {
        TestRaidService raid = TestRaidService.create();
        service = new SprintService(sprintRepository(), sprintItemRepository(),
                backlogItemRepository(), wbsItemRepository(), memberRepository(),
                projectRepository(), raid.service());
        workPackage = addWbsItem("개발", ExecutionMode.AGILE);
    }

    @Test
    @DisplayName("1. 되돌아간다 — ACTIVE Sprint를 취소하면 PLANNED로 바뀐다")
    void revertsActiveSprintToPlanned() {
        Long sprintId = startedSprintWith("이야기", 5);
        assertThat(detail(sprintId).status()).isEqualTo(SprintStatus.ACTIVE);

        service.cancelStart(PROJECT_ID, sprintId);

        assertThat(detail(sprintId).status()).isEqualTo(SprintStatus.PLANNED);
    }

    @Test
    @DisplayName("2. 약속값이 지워진다 — 시작 때 각인된 pointsAtStart가 null이 된다")
    void clearsPointsAtStart() {
        Long sprintId = startedSprintWith("이야기", 5);
        Long story = byTitle("이야기").getId();
        assertThat(item(sprintId, story).pointsAtStart()).isEqualTo(5);

        service.cancelStart(PROJECT_ID, sprintId);

        assertThat(item(sprintId, story).pointsAtStart()).isNull();
    }

    @Test
    @DisplayName("3. 배정은 남는다 — 취소 후에도 배정 행이 살아 있다(제거되지 않는다)")
    void keepsAssignmentAlive() {
        Long sprintId = startedSprintWith("이야기", 5);
        Long story = byTitle("이야기").getId();

        service.cancelStart(PROJECT_ID, sprintId);

        // 다시 계획하려면 배정이 있어야 한다 — 취소가 배정까지 지우면 이미 넣었던 항목을
        // 처음부터 다시 골라야 한다.
        assertThat(assignments).singleElement().matches(SprintItem::active);
        assertThat(detail(sprintId).items()).extracting(SprintItemDetail::backlogItemId)
                .containsExactly(story);
    }

    @Test
    @DisplayName("4. 일은 건드리지 않는다 — Board에서 완료로 옮긴 항목의 상태가 취소 후에도 DONE 그대로다")
    void doesNotTouchWorkStatus() {
        // 원칙: "종료가 항목의 상태를 바꾸지 않는다"(SprintServiceTest의 closingLeavesItemStatusAlone)와
        // 같은 원칙이 시작 취소에도 적용된다 — 취소는 Sprint에 대한 진술이지, 그 Sprint에 배정된
        // 일에 대한 진술이 아니다. 이 분리가 무너지면 담당자가 이미 마친 작업이 취소 한 번에
        // 되돌려질 수 있다.
        Long sprintId = startedSprintWith("이야기", 5);
        Long story = byTitle("이야기").getId();
        service.move(PROJECT_ID, sprintId, story,
                new BoardMoveRequest(BacklogStatus.DONE, null, null, true));
        assertThat(byTitle("이야기").getStatus()).isEqualTo(BacklogStatus.DONE);

        service.cancelStart(PROJECT_ID, sprintId);

        assertThat(byTitle("이야기").getStatus()).isEqualTo(BacklogStatus.DONE);
        assertThat(byTitle("이야기").getDoneAt()).isNotNull();
    }

    @Test
    @DisplayName("5. 완료된 항목이 있어도 취소를 막지 않는다 — 서버는 완료 여부로 거부하지 않는다")
    void allowsCancelEvenWithDoneItems() {
        Long sprintId = startedSprintWith("이야기", 5);
        Long story = byTitle("이야기").getId();
        service.move(PROJECT_ID, sprintId, story,
                new BoardMoveRequest(BacklogStatus.DONE, null, null, true));

        // 예외 없이 성공해야 한다.
        service.cancelStart(PROJECT_ID, sprintId);

        assertThat(detail(sprintId).status()).isEqualTo(SprintStatus.PLANNED);
    }

    @Test
    @DisplayName("6. 취소 후 다시 시작할 수 있고, pointsAtStart가 다시 찍힌다")
    void canStartAgainAfterCancel() {
        Long sprintId = startedSprintWith("이야기", 5);
        Long story = byTitle("이야기").getId();
        service.cancelStart(PROJECT_ID, sprintId);
        assertThat(item(sprintId, story).pointsAtStart()).isNull();

        service.start(PROJECT_ID, sprintId);

        assertThat(detail(sprintId).status()).isEqualTo(SprintStatus.ACTIVE);
        assertThat(item(sprintId, story).pointsAtStart()).isEqualTo(5);
    }

    @Test
    @DisplayName("6-보조. 재시작 전에 추정치가 바뀌면 취소 뒤 재시작은 바뀐 값으로 다시 찍는다")
    void restartRestampsFromCurrentEstimate() {
        // "재시작이 다시 각인한다"는 계약을 더 엄격히: 취소와 재시작 사이에 story point가
        // 바뀌면 재시작 시점의 값이 찍혀야 한다(취소 시점에 지운 값이 아니라).
        Long sprintId = startedSprintWith("이야기", 5);
        Long story = byTitle("이야기").getId();
        service.cancelStart(PROJECT_ID, sprintId);

        byId(story).update(byId(story).getWbsItemId(), byId(story).getParentId(),
                byId(story).getItemType(), byId(story).getTitle(), byId(story).getDescription(),
                byId(story).getPriority(), byId(story).getStatus(),
                byId(story).getAssigneeMemberId(), byId(story).getAcceptanceCriteria(),
                8, byId(story).getProgressWeight());

        service.start(PROJECT_ID, sprintId);

        assertThat(item(sprintId, story).pointsAtStart()).isEqualTo(8);
    }

    @Test
    @DisplayName("7. 활성 슬롯이 풀린다 — 취소 후 다른 Sprint를 시작할 수 있다(단일 활성 규칙)")
    void freesActiveSlotForAnotherSprint() {
        Long first = createSprint("Sprint 1");
        Long second = createSprint("Sprint 2");
        service.start(PROJECT_ID, first);

        assertThatThrownBy(() -> service.start(PROJECT_ID, second))
                .isInstanceOf(InvalidSprintException.class);

        service.cancelStart(PROJECT_ID, first);
        service.start(PROJECT_ID, second);

        assertThat(detail(first).status()).isEqualTo(SprintStatus.PLANNED);
        assertThat(detail(second).status()).isEqualTo(SprintStatus.ACTIVE);
    }

    @Test
    @DisplayName("8-a. 잘못된 상태는 거부 — PLANNED 상태에서 취소하면 InvalidSprintException")
    void rejectsCancelFromPlanned() {
        Long sprintId = createSprint("Sprint 1");
        assertThat(detail(sprintId).status()).isEqualTo(SprintStatus.PLANNED);

        assertThatThrownBy(() -> service.cancelStart(PROJECT_ID, sprintId))
                .isInstanceOf(InvalidSprintException.class);
    }

    @Test
    @DisplayName("8-b. 잘못된 상태는 거부 — CLOSED 상태에서 취소하면 InvalidSprintException")
    void rejectsCancelFromClosed() {
        Long sprintId = createSprint("Sprint 1");
        service.start(PROJECT_ID, sprintId);
        service.close(PROJECT_ID, sprintId, null);
        assertThat(detail(sprintId).status()).isEqualTo(SprintStatus.CLOSED);

        assertThatThrownBy(() -> service.cancelStart(PROJECT_ID, sprintId))
                .isInstanceOf(InvalidSprintException.class);
    }

    @Test
    @DisplayName("9. 종료 결과는 여전히 불변 — CLOSED 상태에서 취소를 시도해도 실패하고 상태·결과가 그대로다")
    void closedOutcomeStaysImmutableEvenAfterFailedCancelAttempt() {
        Long sprintId = createSprint("Sprint 1");
        Long story = addStory("완료할 것", 5);
        service.assign(PROJECT_ID, sprintId, new SprintAssignRequest(story));
        service.start(PROJECT_ID, sprintId);
        service.move(PROJECT_ID, sprintId, story,
                new BoardMoveRequest(BacklogStatus.DONE, null, null, true));
        service.close(PROJECT_ID, sprintId, null);
        SprintDetail closedBefore = detail(sprintId);

        assertThatThrownBy(() -> service.cancelStart(PROJECT_ID, sprintId))
                .isInstanceOf(InvalidSprintException.class);

        SprintDetail closedAfter = detail(sprintId);
        assertThat(closedAfter.status()).isEqualTo(SprintStatus.CLOSED);
        assertThat(closedAfter.closedAt()).isEqualTo(closedBefore.closedAt());
        assertThat(closedAfter.doneItems()).isEqualTo(closedBefore.doneItems());
        assertThat(closedAfter.donePoints()).isEqualTo(closedBefore.donePoints());
    }

    // ------------------------------------------------------------------ 도우미

    private Long createSprint(String name) {
        service.create(PROJECT_ID, new SprintSaveRequest(name, "목표", FROM, TO));
        return sprints.stream()
                .filter(sprint -> sprint.getName().equals(name))
                .findFirst()
                .orElseThrow()
                .getId();
    }

    /** A started Sprint holding one Story — the setup most cancel-start tests need. */
    private Long startedSprintWith(String title, int storyPoint) {
        Long sprintId = createSprint("Sprint 1");
        Long story = addStory(title, storyPoint);
        service.assign(PROJECT_ID, sprintId, new SprintAssignRequest(story));
        service.start(PROJECT_ID, sprintId);
        return sprintId;
    }

    private Long addStory(String title, Integer storyPoint) {
        return addItem(title, BacklogItemType.STORY, null, workPackage, storyPoint);
    }

    private Long addItem(String title, BacklogItemType type, Long parentId, Long wbsItemId,
                          Integer storyPoint) {
        BacklogItem item = new BacklogItem(PROJECT_ID, wbsItemId, parentId, type, title, null,
                BacklogPriority.MEDIUM, BacklogStatus.TODO, null, null, storyPoint, null,
                backlogItems.size());
        ReflectionTestUtils.setField(item, "id", ids.incrementAndGet());
        backlogItems.add(item);
        return item.getId();
    }

    private Long addWbsItem(String name, ExecutionMode mode) {
        WbsItem item = new WbsItem(PROJECT_ID, null, name, null, null, null, 0, wbsItems.size(),
                WbsNodeType.WORK_PACKAGE, mode);
        ReflectionTestUtils.setField(item, "id", ids.incrementAndGet());
        wbsItems.add(item);
        return item.getId();
    }

    private SprintDetail detail(Long sprintId) {
        SprintResponse response = service.getSprints(PROJECT_ID);
        return response.sprints().stream()
                .filter(sprint -> sprint.id().equals(sprintId))
                .findFirst()
                .orElseThrow();
    }

    private SprintItemDetail item(Long sprintId, Long backlogItemId) {
        return detail(sprintId).items().stream()
                .filter(row -> row.backlogItemId().equals(backlogItemId))
                .findFirst()
                .orElseThrow();
    }

    private BacklogItem byId(Long id) {
        return backlogItems.stream().filter(i -> i.getId().equals(id)).findFirst().orElseThrow();
    }

    private BacklogItem byTitle(String title) {
        return backlogItems.stream()
                .filter(item -> item.getTitle().equals(title))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Backlog 항목이 없습니다: " + title));
    }

    private SprintRepository sprintRepository() {
        return new SprintRepository() {
            @Override
            public Sprint save(Sprint sprint) {
                if (sprint.getId() == null) {
                    ReflectionTestUtils.setField(sprint, "id", ids.incrementAndGet());
                }
                if (!sprints.contains(sprint)) {
                    sprints.add(sprint);
                }
                return sprint;
            }

            @Override
            public Optional<Sprint> findById(Long id) {
                return sprints.stream().filter(sprint -> sprint.getId().equals(id)).findFirst();
            }

            @Override
            public List<Sprint> findByProjectId(Long projectId) {
                return sprints.stream()
                        .filter(sprint -> sprint.getProjectId().equals(projectId))
                        .toList();
            }

            @Override
            public void delete(Sprint sprint) {
                sprints.remove(sprint);
            }
        };
    }

    private SprintItemRepository sprintItemRepository() {
        return new SprintItemRepository() {
            @Override
            public SprintItem save(SprintItem item) {
                if (item.getId() == null) {
                    ReflectionTestUtils.setField(item, "id", ids.incrementAndGet());
                }
                if (!assignments.contains(item)) {
                    assignments.add(item);
                }
                return item;
            }

            @Override
            public List<SprintItem> saveAll(List<SprintItem> items) {
                items.forEach(this::save);
                return items;
            }

            @Override
            public List<SprintItem> findByProjectId(Long projectId) {
                return assignments.stream()
                        .filter(item -> item.getProjectId().equals(projectId))
                        .toList();
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
            public List<BacklogItem> saveAll(List<BacklogItem> items) {
                items.forEach(this::save);
                return items;
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

    private WbsItemRepository wbsItemRepository() {
        return new WbsItemRepository() {
            @Override
            public WbsItem save(WbsItem item) {
                return item;
            }

            @Override
            public List<WbsItem> saveAll(List<WbsItem> items) {
                return items;
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
