package com.projectflow.application;

import com.projectflow.application.dto.SprintRequests.BoardMoveRequest;
import com.projectflow.application.dto.SprintRequests.SprintAssignRequest;
import com.projectflow.application.dto.SprintRequests.SprintCloseRequest;
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
import com.projectflow.domain.SprintItemOutcome;
import com.projectflow.domain.SprintItemRepository;
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

/**
 * The Step 4 flow and the rules that keep one piece of work from being counted twice: one running
 * Sprint, one live assignment per entry, outcomes settled at close and never rewritten.
 *
 * <p>In-memory fakes as elsewhere — what matters is what ends up stored on the assignment rows,
 * which is exactly what a mock would hide.
 */
class SprintServiceTest {

    private static final Long PROJECT_ID = 1L;
    private static final LocalDate FROM = LocalDate.parse("2026-09-01");
    private static final LocalDate TO = LocalDate.parse("2026-09-14");

    private final AtomicLong ids = new AtomicLong(700);
    private final List<Sprint> sprints = new ArrayList<>();
    private final List<SprintItem> assignments = new ArrayList<>();
    private final List<BacklogItem> backlogItems = new ArrayList<>();
    private final List<WbsItem> wbsItems = new ArrayList<>();

    private SprintService service;
    private Long workPackage;
    /** Shared link store, so a test can seed a RAID link and check it was detached. */
    private TestRaidService raid;

    @BeforeEach
    void setUp() {
        raid = TestRaidService.create();
        service = new SprintService(sprintRepository(), sprintItemRepository(),
                backlogItemRepository(), wbsItemRepository(), memberRepository(),
                projectRepository(), raid.service());
        workPackage = addWbsItem("개발", ExecutionMode.AGILE);
    }

    @Nested
    @DisplayName("Sprint 수명주기")
    class Lifecycle {

        @Test
        @DisplayName("생성 → 시작 → 종료 순으로만 움직인다")
        void movesForwardOnly() {
            Long sprintId = createSprint("Sprint 1");
            assertThat(detail(sprintId).status()).isEqualTo(SprintStatus.PLANNED);

            service.start(PROJECT_ID, sprintId);
            assertThat(detail(sprintId).status()).isEqualTo(SprintStatus.ACTIVE);

            service.close(PROJECT_ID, sprintId, new SprintCloseRequest(null));
            assertThat(detail(sprintId).status()).isEqualTo(SprintStatus.CLOSED);
            assertThat(detail(sprintId).closedAt()).isNotNull();

            assertThatThrownBy(() -> service.start(PROJECT_ID, sprintId))
                    .isInstanceOf(InvalidSprintException.class);
            assertThatThrownBy(() -> service.close(PROJECT_ID, sprintId, null))
                    .isInstanceOf(InvalidSprintException.class)
                    .hasMessageContaining("이미 종료");
        }

        @Test
        @DisplayName("실행 중인 Sprint가 있으면 다른 Sprint를 시작할 수 없다 — 단일 팀")
        void onlyOneActiveSprint() {
            Long first = createSprint("Sprint 1");
            Long second = createSprint("Sprint 2");
            service.start(PROJECT_ID, first);

            assertThatThrownBy(() -> service.start(PROJECT_ID, second))
                    .isInstanceOf(InvalidSprintException.class)
                    .hasMessageContaining("이미 실행 중");
            // 그동안 두 번째는 시작 버튼을 내려야 한다.
            assertThat(detail(second).canStart()).isFalse();
        }

        @Test
        @DisplayName("같은 이름의 Sprint는 만들 수 없다")
        void rejectsDuplicateName() {
            createSprint("Sprint 1");
            assertThatThrownBy(() -> createSprint("Sprint 1"))
                    .isInstanceOf(InvalidSprintException.class)
                    .hasMessageContaining("같은 이름");
        }

        @Test
        @DisplayName("종료일이 시작일보다 앞설 수 없다")
        void rejectsBackwardsPeriod() {
            assertThatThrownBy(() -> service.create(PROJECT_ID,
                    new SprintSaveRequest("거꾸로", null, TO, FROM)))
                    .isInstanceOf(InvalidSprintException.class)
                    .hasMessageContaining("앞설 수 없습니다");
        }

        @Test
        @DisplayName("실행 중·종료된 Sprint는 삭제할 수 없고, 계획 상태의 빈 Sprint만 지운다")
        void deleteOnlyPlannedAndEmpty() {
            Long sprintId = createSprint("Sprint 1");
            Long story = addStory("이야기", 5);
            service.assign(PROJECT_ID, sprintId, new SprintAssignRequest(story));

            assertThatThrownBy(() -> service.delete(PROJECT_ID, sprintId))
                    .isInstanceOf(InvalidSprintException.class)
                    .hasMessageContaining("배정된 항목");

            service.unassign(PROJECT_ID, sprintId, story);
            service.delete(PROJECT_ID, sprintId);
            assertThat(sprints).isEmpty();
        }

        @Test
        @DisplayName("종료된 Sprint는 수정할 수 없다")
        void closedSprintIsImmutable() {
            Long sprintId = createSprint("Sprint 1");
            service.start(PROJECT_ID, sprintId);
            service.close(PROJECT_ID, sprintId, null);

            assertThatThrownBy(() -> service.update(PROJECT_ID, sprintId,
                    new SprintSaveRequest("바뀐 이름", null, FROM, TO)))
                    .isInstanceOf(InvalidSprintException.class)
                    .hasMessageContaining("이력");
        }
    }

    @Nested
    @DisplayName("배정")
    class Assignment {

        @Test
        @DisplayName("Story·Bug만 배정할 수 있다 — Epic과 Task는 거부")
        void onlyAggregationUnits() {
            Long sprintId = createSprint("Sprint 1");
            Long epic = addItem("묶음", BacklogItemType.EPIC, null, workPackage, null);
            Long task = addItem("작업", BacklogItemType.TASK, null, workPackage, null);

            assertThatThrownBy(() -> service.assign(PROJECT_ID, sprintId, new SprintAssignRequest(epic)))
                    .isInstanceOf(InvalidSprintException.class)
                    .hasMessageContaining("Story 또는 Bug");
            assertThatThrownBy(() -> service.assign(PROJECT_ID, sprintId, new SprintAssignRequest(task)))
                    .isInstanceOf(InvalidSprintException.class);
        }

        @Test
        @DisplayName("미연결 항목은 배정할 수 없다")
        void rejectsUnlinked() {
            Long sprintId = createSprint("Sprint 1");
            Long draft = addItem("초안", BacklogItemType.STORY, null, null, null);

            assertThatThrownBy(() -> service.assign(PROJECT_ID, sprintId, new SprintAssignRequest(draft)))
                    .isInstanceOf(InvalidSprintException.class)
                    .hasMessageContaining("귀속 Work Package");
        }

        @Test
        @DisplayName("보관된 항목은 배정할 수 없다")
        void rejectsArchived() {
            Long sprintId = createSprint("Sprint 1");
            Long story = addStory("접어둔 것", 3);
            byId(story).archive();

            assertThatThrownBy(() -> service.assign(PROJECT_ID, sprintId, new SprintAssignRequest(story)))
                    .isInstanceOf(InvalidSprintException.class)
                    .hasMessageContaining("보관");
        }

        @Test
        @DisplayName("같은 항목을 같은 Sprint에 두 번 배정할 수 없다")
        void rejectsDuplicateAssignment() {
            Long sprintId = createSprint("Sprint 1");
            Long story = addStory("이야기", 5);
            service.assign(PROJECT_ID, sprintId, new SprintAssignRequest(story));

            assertThatThrownBy(() -> service.assign(PROJECT_ID, sprintId, new SprintAssignRequest(story)))
                    .isInstanceOf(InvalidSprintException.class)
                    .hasMessageContaining("이미 이 Sprint에");
        }

        @Test
        @DisplayName("열려 있는 다른 Sprint에 이미 들어 있으면 배정할 수 없다")
        void rejectsSecondLiveAssignment() {
            Long first = createSprint("Sprint 1");
            Long second = createSprint("Sprint 2");
            Long story = addStory("이야기", 5);
            service.assign(PROJECT_ID, first, new SprintAssignRequest(story));

            assertThatThrownBy(() -> service.assign(PROJECT_ID, second, new SprintAssignRequest(story)))
                    .isInstanceOf(InvalidSprintException.class)
                    .hasMessageContaining("이미 배정되어 있습니다");
        }

        @Test
        @DisplayName("여러 Work Package의 항목을 한 Sprint에 담을 수 있다")
        void spansWorkPackages() {
            Long other = addWbsItem("인수", ExecutionMode.HYBRID);
            Long sprintId = createSprint("Sprint 1");
            Long a = addStory("개발 쪽", 3);
            Long b = addItem("인수 쪽", BacklogItemType.STORY, null, other, 5);

            service.assign(PROJECT_ID, sprintId, new SprintAssignRequest(a));
            service.assign(PROJECT_ID, sprintId, new SprintAssignRequest(b));

            assertThat(detail(sprintId).items()).hasSize(2);
            assertThat(detail(sprintId).plannedPoints()).isEqualTo(8);
        }

        @Test
        @DisplayName("제거는 행을 지우지 않고 REMOVED로 남긴다")
        void removalIsRecorded() {
            Long sprintId = createSprint("Sprint 1");
            Long story = addStory("이야기", 5);
            service.assign(PROJECT_ID, sprintId, new SprintAssignRequest(story));

            service.unassign(PROJECT_ID, sprintId, story);

            assertThat(assignments).singleElement().satisfies(assignment -> {
                assertThat(assignment.active()).isFalse();
                assertThat(assignment.getOutcome()).isEqualTo(SprintItemOutcome.REMOVED);
            });
            // 열린 Sprint는 지금 들어 있는 것만 보여준다.
            assertThat(detail(sprintId).items()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Board 실행과 완료 절차")
    class Board {

        @Test
        @DisplayName("확인 표시가 없으면 완료로 옮길 수 없다")
        void completionNeedsConfirmation() {
            Long sprintId = startedSprintWith("이야기", 5);
            Long story = byTitle("이야기").getId();

            assertThatThrownBy(() -> service.move(PROJECT_ID, sprintId, story,
                    new BoardMoveRequest(BacklogStatus.DONE, null, null, null)))
                    .isInstanceOf(InvalidSprintException.class)
                    .hasMessageContaining("확인");

            service.move(PROJECT_ID, sprintId, story,
                    new BoardMoveRequest(BacklogStatus.DONE, null, null, true));
            assertThat(byTitle("이야기").getStatus()).isEqualTo(BacklogStatus.DONE);
            assertThat(byTitle("이야기").getDoneAt()).isNotNull();
        }

        @Test
        @DisplayName("차단된 항목은 완료할 수 없다")
        void blockedCannotComplete() {
            Long sprintId = startedSprintWith("이야기", 5);
            Long story = byTitle("이야기").getId();

            service.move(PROJECT_ID, sprintId, story,
                    new BoardMoveRequest(BacklogStatus.IN_PROGRESS, true, "외부 API 대기", null));
            assertThat(byTitle("이야기").blocked()).isTrue();
            assertThat(detail(sprintId).blockedItems()).isEqualTo(1);

            assertThatThrownBy(() -> service.move(PROJECT_ID, sprintId, story,
                    new BoardMoveRequest(BacklogStatus.DONE, null, null, true)))
                    .isInstanceOf(InvalidSprintException.class)
                    .hasMessageContaining("차단");

            // 차단 해제와 완료를 한 번에 보낼 수 있다.
            service.move(PROJECT_ID, sprintId, story,
                    new BoardMoveRequest(BacklogStatus.DONE, false, null, true));
            assertThat(byTitle("이야기").getStatus()).isEqualTo(BacklogStatus.DONE);
        }

        @Test
        @DisplayName("Task를 모두 완료해도 상위 Story가 자동 완료되지 않는다")
        void tasksDoNotCompleteTheStory() {
            Long sprintId = startedSprintWith("이야기", 5);
            Long story = byTitle("이야기").getId();
            Long task = addItem("작업", BacklogItemType.TASK, story, workPackage, null);

            byId(task).changeStatus(BacklogStatus.DONE);

            assertThat(byTitle("이야기").getStatus()).isEqualTo(BacklogStatus.TODO);
            // 남은 하위가 없다는 사실은 보드에 실려 사람이 판단한다.
            assertThat(item(sprintId, story).openChildCount()).isZero();
        }

        @Test
        @DisplayName("완료되지 않은 하위 수를 보드에 함께 싣는다 (완료를 막지는 않는다)")
        void reportsOpenChildren() {
            Long sprintId = startedSprintWith("이야기", 5);
            Long story = byTitle("이야기").getId();
            addItem("작업 1", BacklogItemType.TASK, story, workPackage, null);
            addItem("작업 2", BacklogItemType.TASK, story, workPackage, null);

            assertThat(item(sprintId, story).openChildCount()).isEqualTo(2);

            service.move(PROJECT_ID, sprintId, story,
                    new BoardMoveRequest(BacklogStatus.DONE, null, null, true));
            assertThat(byTitle("이야기").getStatus()).isEqualTo(BacklogStatus.DONE);
        }

        @Test
        @DisplayName("종료된 Sprint의 항목은 보드에서 옮길 수 없다")
        void closedSprintBoardIsFrozen() {
            Long sprintId = startedSprintWith("이야기", 5);
            Long story = byTitle("이야기").getId();
            service.close(PROJECT_ID, sprintId, null);

            assertThatThrownBy(() -> service.move(PROJECT_ID, sprintId, story,
                    new BoardMoveRequest(BacklogStatus.IN_PROGRESS, null, null, null)))
                    .isInstanceOf(InvalidSprintException.class);
        }
    }

    @Nested
    @DisplayName("종료·이월·재오픈")
    class CloseAndCarryOver {

        @Test
        @DisplayName("종료 시 완료는 DONE, 미완료는 CARRIED_OVER로 찍는다")
        void settlesEachAssignment() {
            Long sprintId = startedSprintWith("완료할 것", 5);
            Long done = byTitle("완료할 것").getId();
            Long open = addStory("남을 것", 3);
            service.assign(PROJECT_ID, sprintId, new SprintAssignRequest(open));
            service.move(PROJECT_ID, sprintId, done,
                    new BoardMoveRequest(BacklogStatus.DONE, null, null, true));

            service.close(PROJECT_ID, sprintId, new SprintCloseRequest(null));

            assertThat(assignments).extracting(SprintItem::getOutcome)
                    .containsExactlyInAnyOrder(SprintItemOutcome.DONE, SprintItemOutcome.CARRIED_OVER);
            SprintDetail closed = detail(sprintId);
            assertThat(closed.doneItems()).isEqualTo(1);
            assertThat(closed.donePoints()).isEqualTo(5);
            assertThat(closed.carriedOverItems()).isEqualTo(1);
        }

        @Test
        @DisplayName("종료가 항목의 상태를 바꾸지 않는다")
        void closingLeavesItemStatusAlone() {
            Long sprintId = startedSprintWith("검토 중", 3);
            Long story = byTitle("검토 중").getId();
            service.move(PROJECT_ID, sprintId, story,
                    new BoardMoveRequest(BacklogStatus.REVIEW, null, null, null));

            service.close(PROJECT_ID, sprintId, null);

            assertThat(byTitle("검토 중").getStatus()).isEqualTo(BacklogStatus.REVIEW);
        }

        @Test
        @DisplayName("이월 대상 Sprint를 주면 미완료 항목을 곧바로 재배정한다")
        void carriesOverToNextSprint() {
            Long first = startedSprintWith("미완료", 3);
            Long story = byTitle("미완료").getId();
            Long next = createSprint("Sprint 2");

            service.close(PROJECT_ID, first, new SprintCloseRequest(next));

            // 원 Sprint의 결과는 미완료로 남는다.
            assertThat(detail(first).carriedOverItems()).isEqualTo(1);
            assertThat(detail(first).doneItems()).isZero();
            // 다음 Sprint에 새 배정이 생긴다.
            assertThat(detail(next).items()).extracting(SprintItemDetail::backlogItemId)
                    .containsExactly(story);
        }

        @Test
        @DisplayName("이월한 항목을 다음 Sprint에서 완료해도 지난 Sprint의 실적은 그대로다")
        void completedWorkIsCreditedOnce() {
            Long first = startedSprintWith("이월될 것", 8);
            Long story = byTitle("이월될 것").getId();
            Long next = createSprint("Sprint 2");
            service.close(PROJECT_ID, first, new SprintCloseRequest(next));

            service.start(PROJECT_ID, next);
            service.move(PROJECT_ID, next, story,
                    new BoardMoveRequest(BacklogStatus.DONE, null, null, true));
            service.close(PROJECT_ID, next, null);

            // 완료 실적은 두 번째 Sprint에만 쌓인다.
            assertThat(detail(first).donePoints()).isZero();
            assertThat(detail(first).carriedOverItems()).isEqualTo(1);
            assertThat(detail(next).donePoints()).isEqualTo(8);
            assertThat(detail(next).doneItems()).isEqualTo(1);
        }

        @Test
        @DisplayName("재오픈해도 지난 Sprint의 완료 결과는 바뀌지 않고, 재오픈 사실이 표시된다")
        void reopenKeepsPastResult() {
            Long sprintId = startedSprintWith("완료했던 것", 5);
            Long story = byTitle("완료했던 것").getId();
            service.move(PROJECT_ID, sprintId, story,
                    new BoardMoveRequest(BacklogStatus.DONE, null, null, true));
            service.close(PROJECT_ID, sprintId, null);
            assertThat(detail(sprintId).donePoints()).isEqualTo(5);

            // 종료 뒤에 다시 열었다 — 보드가 아니라 Backlog 쪽 편집으로 일어날 수 있는 일이다.
            byId(story).changeStatus(BacklogStatus.IN_PROGRESS);

            SprintDetail closed = detail(sprintId);
            assertThat(closed.doneItems()).isEqualTo(1);
            assertThat(closed.donePoints()).isEqualTo(5);
            assertThat(item(sprintId, story).reopened()).isTrue();
            assertThat(byTitle("완료했던 것").getDoneAt()).isNull();
        }

        @Test
        @DisplayName("종료된 Sprint로는 이월할 수 없고, 자기 자신으로도 이월할 수 없다")
        void rejectsInvalidCarryOverTarget() {
            // 두 Sprint를 동시에 실행할 수 없으므로 순서가 중요하다: 종료된 Sprint를 먼저 만들어
            // 두고, 그 다음에 이월 대상을 시험할 Sprint를 시작한다.
            Long closed = createSprint("Sprint 0");
            service.start(PROJECT_ID, closed);
            service.close(PROJECT_ID, closed, null);

            Long first = startedSprintWith("미완료", 3);

            assertThatThrownBy(() -> service.close(PROJECT_ID, first, new SprintCloseRequest(first)))
                    .isInstanceOf(InvalidSprintException.class)
                    .hasMessageContaining("종료하는 Sprint로");
            assertThatThrownBy(() -> service.close(PROJECT_ID, first, new SprintCloseRequest(closed)))
                    .isInstanceOf(InvalidSprintException.class)
                    .hasMessageContaining("종료된 Sprint로는");
        }

        @Test
        @DisplayName("종료된 Sprint는 제거된 배정까지 보여준다 — 왜 빠졌는지가 이력이다")
        void closedSprintShowsRemovedAssignments() {
            Long sprintId = startedSprintWith("빠질 것", 3);
            Long story = byTitle("빠질 것").getId();
            service.unassign(PROJECT_ID, sprintId, story);
            service.close(PROJECT_ID, sprintId, null);

            assertThat(detail(sprintId).items()).singleElement().satisfies(row -> {
                assertThat(row.removed()).isTrue();
                assertThat(row.outcome()).isEqualTo(SprintItemOutcome.REMOVED);
            });
            // 제거된 것은 계획에도 완료에도 세지 않는다.
            assertThat(detail(sprintId).plannedItems()).isZero();
        }
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

    /** A started Sprint holding one Story — the setup most Board and close tests need. */
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
