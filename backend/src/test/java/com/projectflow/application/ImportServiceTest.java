package com.projectflow.application;

import com.projectflow.application.dto.ProjectExportResponse;
import com.projectflow.application.dto.ProjectExportResponse.ExportedBacklogItem;
import com.projectflow.application.dto.ProjectExportResponse.ExportedDependency;
import com.projectflow.application.dto.ProjectExportResponse.ExportedMember;
import com.projectflow.application.dto.ProjectExportResponse.ExportedProject;
import com.projectflow.application.dto.ProjectExportResponse.ExportedRaciAssignment;
import com.projectflow.application.dto.ProjectExportResponse.ExportedRaidItem;
import com.projectflow.application.dto.ProjectExportResponse.ExportedSprint;
import com.projectflow.application.dto.ProjectExportResponse.ExportedRaidLink;
import com.projectflow.application.dto.ProjectExportResponse.ExportedWbsItem;
import com.projectflow.domain.AcceptanceCheckpoint;
import com.projectflow.domain.AcceptanceCheckpointRepository;
import com.projectflow.domain.BacklogItem;
import com.projectflow.domain.Baseline;
import com.projectflow.domain.BaselineItem;
import com.projectflow.domain.BaselineRepository;
import com.projectflow.domain.ProgressSnapshot;
import com.projectflow.domain.ProgressSnapshotRepository;
import com.projectflow.domain.BacklogItemRepository;
import com.projectflow.domain.BacklogItemType;
import com.projectflow.domain.BacklogPriority;
import com.projectflow.domain.BacklogStatus;
import com.projectflow.domain.ExecutionMode;
import com.projectflow.domain.InvalidImportException;
import com.projectflow.domain.Project;
import com.projectflow.domain.ProjectMember;
import com.projectflow.domain.ProjectMemberRepository;
import com.projectflow.domain.ProjectRepository;
import com.projectflow.domain.ProjectStatus;
import com.projectflow.domain.RaciAssignment;
import com.projectflow.domain.RaciAssignmentRepository;
import com.projectflow.domain.RaciRole;
import com.projectflow.domain.RaidItem;
import com.projectflow.domain.RaidLink;
import com.projectflow.domain.RaidLinkRepository;
import com.projectflow.domain.RaidLinkTarget;
import com.projectflow.domain.RaidItemRepository;
import com.projectflow.domain.RaidStatus;
import com.projectflow.domain.RaidType;
import com.projectflow.domain.Sprint;
import com.projectflow.domain.SprintStatus;
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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * In-memory fakes rather than mocks: the id remapping is the whole point of import, and it can
 * only be checked by letting saves hand back generated ids and inspecting what was stored.
 */
class ImportServiceTest {

    private final AtomicLong ids = new AtomicLong(100);

    private final List<Project> projects = new ArrayList<>();
    private final List<ProjectMember> members = new ArrayList<>();
    private final List<WbsItem> wbsItems = new ArrayList<>();
    private final List<WbsDependency> dependencies = new ArrayList<>();
    private final List<RaciAssignment> raciAssignments = new ArrayList<>();
    private final List<RaidItem> raidItems = new ArrayList<>();
    private final List<RaidLink> raidLinks = new ArrayList<>();
    private final List<BacklogItem> backlogItems = new ArrayList<>();
    private final List<Sprint> sprints = new ArrayList<>();
    private final List<SprintItem> sprintItems = new ArrayList<>();
    private final List<AcceptanceCheckpoint> checkpoints = new ArrayList<>();
    private final List<Baseline> baselines = new ArrayList<>();
    private final List<BaselineItem> baselineItems = new ArrayList<>();
    private final List<ProgressSnapshot> snapshots = new ArrayList<>();

    private ImportService service;

    @BeforeEach
    void setUp() {
        service = new ImportService(
                projectRepository(), memberRepository(), wbsItemRepository(),
                dependencyRepository(), raciAssignmentRepository(), raidItemRepository(),
                raidLinkRepository(), backlogItemRepository(), sprintRepository(),
                sprintItemRepository(), checkpointRepository(), baselineRepository(),
                snapshotRepository());
    }

    @Nested
    @DisplayName("정상 가져오기")
    class HappyPath {

        @Test
        @DisplayName("파일의 id를 새 id로 다시 매핑하고 참조를 이어 붙인다")
        void remapsIds() {
            service.importProject(file(
                    List.of(member(7L, "김재학")),
                    List.of(
                            wbs(1L, null, "설계"),
                            wbs(2L, 1L, "화면 설계"),
                            wbs(3L, 1L, "DB 설계")
                    ),
                    List.of(new ExportedDependency(50L, 2L, 3L, 2)),
                    List.of(new ExportedRaciAssignment(60L, 2L, 7L, RaciRole.ACCOUNTABLE)),
                    List.of(raid(70L, "위험 하나", 7L, 2L))
            ));

            assertThat(projects).hasSize(1);
            Long projectId = projects.get(0).getId();

            // 파일의 id(1,2,3,7,...)는 어디에도 남아 있지 않아야 한다.
            assertThat(wbsItems).allSatisfy(item ->
                    assertThat(item.getProjectId()).isEqualTo(projectId));
            assertThat(wbsItems).extracting(WbsItem::getId).doesNotContain(1L, 2L, 3L);

            WbsItem design = byName("설계");
            WbsItem screen = byName("화면 설계");
            WbsItem db = byName("DB 설계");

            assertThat(design.getParentId()).isNull();
            assertThat(screen.getParentId()).isEqualTo(design.getId());
            assertThat(db.getParentId()).isEqualTo(design.getId());

            assertThat(dependencies).singleElement().satisfies(dependency -> {
                assertThat(dependency.getPredecessorId()).isEqualTo(screen.getId());
                assertThat(dependency.getSuccessorId()).isEqualTo(db.getId());
                assertThat(dependency.getLagDays()).isEqualTo(2);
            });

            assertThat(raciAssignments).singleElement().satisfies(assignment -> {
                assertThat(assignment.getWbsItemId()).isEqualTo(screen.getId());
                assertThat(assignment.getMemberId()).isEqualTo(members.get(0).getId());
            });

            assertThat(raidItems).singleElement().satisfies(item ->
                    assertThat(item.getOwnerMemberId()).isEqualTo(members.get(0).getId()));
            // 예전 파일의 단일 wbsItemId는 WBS_ITEM 링크 하나가 되고, 그 id도 새로 매겨진다.
            assertThat(raidLinks).singleElement().satisfies(link -> {
                assertThat(link.getTargetType()).isEqualTo(RaidLinkTarget.WBS_ITEM);
                assertThat(link.getTargetId()).isEqualTo(screen.getId());
                assertThat(link.getRaidItemId()).isEqualTo(raidItems.get(0).getId());
            });
        }

        @Test
        @DisplayName("파일 순서가 자식 먼저여도 상위부터 넣는다")
        void insertsParentsFirstRegardlessOfFileOrder() {
            service.importProject(file(
                    List.of(),
                    // 손으로 편집한 파일은 트리 순서가 아닐 수 있다.
                    List.of(wbs(3L, 2L, "손자"), wbs(2L, 1L, "자식"), wbs(1L, null, "부모")),
                    List.of(), List.of(), List.of()
            ));

            assertThat(byName("부모").getParentId()).isNull();
            assertThat(byName("자식").getParentId()).isEqualTo(byName("부모").getId());
            assertThat(byName("손자").getParentId()).isEqualTo(byName("자식").getId());
        }

        @Test
        @DisplayName("빈 절이 있어도(null) 가져온다")
        void toleratesMissingSections() {
            ProjectExportResponse bare = new ProjectExportResponse(
                    1, LocalDateTime.now(), project("맨몸 프로젝트"), null, null, null, null, null, null, null, null,
                    null, null, null);

            service.importProject(bare);

            assertThat(projects).hasSize(1);
            assertThat(wbsItems).isEmpty();
        }

        @Test
        @DisplayName("이름이 겹치면 접미사를 붙여 목록에서 구분되게 한다")
        void avoidsNameCollision() {
            projects.add(existingProject("AEGIS"));

            service.importProject(file(List.of(), List.of(), List.of(), List.of(), List.of()));

            assertThat(projects).extracting(Project::getName)
                    .containsExactly("AEGIS", "AEGIS (가져옴)");

            service.importProject(file(List.of(), List.of(), List.of(), List.of(), List.of()));
            assertThat(projects).extracting(Project::getName)
                    .containsExactly("AEGIS", "AEGIS (가져옴)", "AEGIS (가져옴 2)");
        }
    }

    @Nested
    @DisplayName("거부하는 파일")
    class Rejected {

        @Test
        @DisplayName("지원하지 않는 형식 버전")
        void newerFormat() {
            ProjectExportResponse future = new ProjectExportResponse(
                    99, LocalDateTime.now(), project("미래"), List.of(), List.of(), List.of(),
                    List.of(), List.of(), List.of(), List.of(), List.of(),
                    List.of(), List.of(), List.of());

            assertThatThrownBy(() -> service.importProject(future))
                    .isInstanceOf(InvalidImportException.class)
                    .hasMessageContaining("읽을 수 없는 형식");
        }

        @Test
        @DisplayName("파일에 없는 상위 항목을 가리키는 WBS")
        void danglingParent() {
            assertThatThrownBy(() -> service.importProject(file(
                    List.of(), List.of(wbs(2L, 999L, "고아")), List.of(), List.of(), List.of())))
                    .isInstanceOf(InvalidImportException.class)
                    .hasMessageContaining("상위 항목");
        }

        @Test
        @DisplayName("순환하는 상위 참조")
        void parentCycle() {
            assertThatThrownBy(() -> service.importProject(file(
                    List.of(), List.of(wbs(1L, 2L, "가"), wbs(2L, 1L, "나")),
                    List.of(), List.of(), List.of())))
                    .isInstanceOf(InvalidImportException.class)
                    .hasMessageContaining("순환");
        }

        @Test
        @DisplayName("id 중복")
        void duplicateIds() {
            assertThatThrownBy(() -> service.importProject(file(
                    List.of(), List.of(wbs(1L, null, "가"), wbs(1L, null, "나")),
                    List.of(), List.of(), List.of())))
                    .isInstanceOf(InvalidImportException.class)
                    .hasMessageContaining("중복");
        }

        @Test
        @DisplayName("선행과 후행이 같은 선후행 관계 — DB 제약에 걸리기 전에 잡는다")
        void selfDependency() {
            assertThatThrownBy(() -> service.importProject(file(
                    List.of(), List.of(wbs(1L, null, "가")),
                    List.of(new ExportedDependency(9L, 1L, 1L, 0)), List.of(), List.of())))
                    .isInstanceOf(InvalidImportException.class)
                    .hasMessageContaining("선행과 후행이 같은");
        }

        @Test
        @DisplayName("중복된 선후행 관계 — UNIQUE 제약에 걸리기 전에 잡는다")
        void duplicateDependency() {
            assertThatThrownBy(() -> service.importProject(file(
                    List.of(), List.of(wbs(1L, null, "가"), wbs(2L, null, "나")),
                    List.of(new ExportedDependency(9L, 1L, 2L, 0), new ExportedDependency(10L, 1L, 2L, 3)),
                    List.of(), List.of())))
                    .isInstanceOf(InvalidImportException.class)
                    .hasMessageContaining("두 번");
        }

        @Test
        @DisplayName("중복된 RACI 배정 — UNIQUE 제약에 걸리기 전에 잡는다")
        void duplicateRaci() {
            assertThatThrownBy(() -> service.importProject(file(
                    List.of(member(7L, "김")), List.of(wbs(1L, null, "가")), List.of(),
                    List.of(
                            new ExportedRaciAssignment(1L, 1L, 7L, RaciRole.RESPONSIBLE),
                            new ExportedRaciAssignment(2L, 1L, 7L, RaciRole.RESPONSIBLE)),
                    List.of())))
                    .isInstanceOf(InvalidImportException.class)
                    .hasMessageContaining("두 번");
        }

        @Test
        @DisplayName("파일에 없는 구성원을 가리키는 RAID 소유자")
        void danglingRaidOwner() {
            assertThatThrownBy(() -> service.importProject(file(
                    List.of(), List.of(), List.of(), List.of(),
                    List.of(raid(1L, "위험", 999L, null)))))
                    .isInstanceOf(InvalidImportException.class)
                    .hasMessageContaining("소유자");
        }

        @Test
        @DisplayName("거부된 파일은 아무것도 남기지 않는다")
        void rejectionLeavesNothing() {
            assertThatThrownBy(() -> service.importProject(file(
                    List.of(), List.of(wbs(2L, 999L, "고아")), List.of(), List.of(), List.of())))
                    .isInstanceOf(InvalidImportException.class);

            // 검증이 삽입보다 앞에 있으므로 트랜잭션 롤백에 기대지 않아도 비어 있다.
            assertThat(projects).isEmpty();
            assertThat(wbsItems).isEmpty();
        }
    }

    // ------------------------------------------------------------- 파일 조립

    @Nested
    @DisplayName("RAID 연결 (formatVersion 6)")
    class RaidLinks {

        @Test
        @DisplayName("여러 대상에 걸린 연결을 모두 새 id로 다시 매긴다")
        void remapsEveryLink() {
            service.importProject(new ProjectExportResponse(
                    6, LocalDateTime.now(), project("AEGIS"),
                    List.of(), List.of(wbs(1L, null, "개발")), List.of(), List.of(),
                    List.of(raidWithLinks(1L, "외부 API 지연", null, List.of(
                            raidLink(1L, RaidLinkTarget.WBS_ITEM, 1L),
                            raidLink(2L, RaidLinkTarget.BACKLOG_ITEM, 50L),
                            raidLink(3L, RaidLinkTarget.SPRINT, 60L)))),
                    List.of(backlog(50L, 1L, null, BacklogItemType.STORY, "로그인 화면", null)),
                    List.of(new ExportedSprint(60L, "Sprint 1", null,
                            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 14),
                            SprintStatus.PLANNED, null)), List.of(),
                    List.of(), List.of(), List.of()));

            Long newWbsId = byName("개발").getId();
            assertThat(raidLinks).hasSize(3);
            assertThat(raidLinks)
                    .filteredOn(link -> link.getTargetType() == RaidLinkTarget.WBS_ITEM)
                    .singleElement()
                    .satisfies(link -> assertThat(link.getTargetId()).isEqualTo(newWbsId));
            // 파일의 id(50, 60)를 그대로 쓰지 않는다 — 다른 설치본의 번호다.
            assertThat(raidLinks).extracting(RaidLink::getTargetId).doesNotContain(50L, 60L);
        }

        @Test
        @DisplayName("파일에 없는 대상을 가리키면 거부한다")
        void refusesDanglingLink() {
            assertThatThrownBy(() -> service.importProject(new ProjectExportResponse(
                    6, LocalDateTime.now(), project("AEGIS"),
                    List.of(), List.of(wbs(1L, null, "개발")), List.of(), List.of(),
                    List.of(raidWithLinks(1L, "위험", null, List.of(
                            raidLink(1L, RaidLinkTarget.SPRINT, 999L)))),
                    List.of(), List.of(), List.of(), List.of(), List.of(), List.of())))
                    .isInstanceOf(InvalidImportException.class)
                    .hasMessageContaining("연결 대상");
            assertThat(projects).isEmpty();
        }
    }

    private ProjectExportResponse file(List<ExportedMember> members,
                                        List<ExportedWbsItem> wbs,
                                        List<ExportedDependency> deps,
                                        List<ExportedRaciAssignment> raci,
                                        List<ExportedRaidItem> raid) {
        return new ProjectExportResponse(
                1, LocalDateTime.now(), project("AEGIS"), members, wbs, deps, raci, raid, List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of());
    }

    @Nested
    @DisplayName("실행 방식과 관리 단위 구분")
    class ExecutionModeFields {

        @Test
        @DisplayName("구형 파일(formatVersion 1)은 자식 유무로 구분을 채우고 실행 방식은 미지정으로 둔다")
        void fillsDefaultsForOlderFiles() {
            service.importProject(wbsFile(1, List.of(
                    wbs(1L, null, "부모"),
                    wbs(2L, 1L, "자식"))));

            assertThat(byName("부모").getNodeType()).isEqualTo(WbsNodeType.SUMMARY);
            assertThat(byName("자식").getNodeType()).isEqualTo(WbsNodeType.WORK_PACKAGE);
            assertThat(byName("부모").getExecutionMode()).isNull();
            assertThat(byName("자식").getExecutionMode()).isNull();
        }

        @Test
        @DisplayName("새 필드는 그대로 왕복한다")
        void keepsNewFields() {
            service.importProject(wbsFile(2, List.of(
                    wbs(1L, null, "개발", WbsNodeType.WORK_PACKAGE, ExecutionMode.AGILE))));

            assertThat(byName("개발").getNodeType()).isEqualTo(WbsNodeType.WORK_PACKAGE);
            assertThat(byName("개발").getExecutionMode()).isEqualTo(ExecutionMode.AGILE);
        }

        @Test
        @DisplayName("하위가 있으면 파일이 Work Package라 해도 Summary로 바로잡고, 실행 방식은 보관한다")
        void normalisesParentClaimingToBeWorkPackage() {
            service.importProject(wbsFile(2, List.of(
                    wbs(1L, null, "부모", WbsNodeType.WORK_PACKAGE, ExecutionMode.AGILE),
                    wbs(2L, 1L, "자식", WbsNodeType.WORK_PACKAGE, ExecutionMode.WATERFALL))));

            assertThat(byName("부모").getNodeType()).isEqualTo(WbsNodeType.SUMMARY);
            // 지우지 않는다 — 구분을 되돌리면 살아나야 하는 값이다.
            assertThat(byName("부모").getExecutionMode()).isEqualTo(ExecutionMode.AGILE);
            assertThat(byName("자식").getExecutionMode()).isEqualTo(ExecutionMode.WATERFALL);
        }
    }

    @Nested
    @DisplayName("Backlog")
    class Backlog {

        @Test
        @DisplayName("귀속·상위·담당자 참조를 새 id로 이어 붙인다")
        void remapsBacklogReferences() {
            service.importProject(backlogFile(
                    List.of(member(7L, "김재학")),
                    List.of(wbs(1L, null, "개발", WbsNodeType.WORK_PACKAGE, ExecutionMode.AGILE)),
                    List.of(
                            backlog(80L, 1L, null, BacklogItemType.EPIC, "WBS 관리 기능 구현", null),
                            backlog(81L, 1L, 80L, BacklogItemType.STORY, "WBS 계층 등록", 7L),
                            backlog(82L, 1L, 81L, BacklogItemType.TASK, "API 작성", null))));

            Long wbsId = wbsItems.get(0).getId();
            Long memberId = members.get(0).getId();

            assertThat(backlogItems).extracting(BacklogItem::getId).doesNotContain(80L, 81L, 82L);
            assertThat(backlogItems).allSatisfy(item ->
                    assertThat(item.getWbsItemId()).isEqualTo(wbsId));
            assertThat(backlogTitled("WBS 계층 등록").getParentId())
                    .isEqualTo(backlogTitled("WBS 관리 기능 구현").getId());
            assertThat(backlogTitled("API 작성").getParentId())
                    .isEqualTo(backlogTitled("WBS 계층 등록").getId());
            assertThat(backlogTitled("WBS 계층 등록").getAssigneeMemberId()).isEqualTo(memberId);
        }

        @Test
        @DisplayName("하위의 귀속은 파일 값이 아니라 상위에서 물려받는다")
        void childInheritsParentLink() {
            service.importProject(backlogFile(
                    List.of(),
                    List.of(
                            wbs(1L, null, "개발", WbsNodeType.WORK_PACKAGE, ExecutionMode.AGILE),
                            wbs(2L, null, "인수", WbsNodeType.WORK_PACKAGE, ExecutionMode.WATERFALL)),
                    List.of(
                            backlog(80L, 1L, null, BacklogItemType.EPIC, "묶음", null),
                            // 파일이 다른 Work Package를 가리켜도 상위를 따른다 — 이 모델에서 하위의
                            // 귀속 값은 정보를 담고 있지 않다.
                            backlog(81L, 2L, 80L, BacklogItemType.STORY, "이야기", null))));

            assertThat(backlogTitled("이야기").getWbsItemId())
                    .isEqualTo(backlogTitled("묶음").getWbsItemId());
        }

        @Test
        @DisplayName("보관 상태는 파일과 함께 넘어온다")
        void keepsArchivedState() {
            LocalDateTime archivedAt = LocalDateTime.parse("2026-09-01T10:00:00");
            service.importProject(backlogFile(
                    List.of(),
                    List.of(wbs(1L, null, "개발", WbsNodeType.WORK_PACKAGE, ExecutionMode.AGILE)),
                    List.of(new ExportedBacklogItem(80L, 1L, null, BacklogItemType.STORY, "접어둔 것",
                            null, BacklogPriority.LOW, BacklogStatus.TODO, null, null, null, null,
                            archivedAt, 0, false, null, null))));

            assertThat(backlogTitled("접어둔 것").archived()).isTrue();
            assertThat(backlogTitled("접어둔 것").getArchivedAt()).isEqualTo(archivedAt);
        }

        @Test
        @DisplayName("미연결(초안)은 그대로 가져온다 — 표시할 문제이지 거부할 문제가 아니다")
        void keepsUnlinkedDraft() {
            service.importProject(backlogFile(List.of(), List.of(),
                    List.of(backlog(80L, null, null, BacklogItemType.STORY, "초안", null))));

            assertThat(backlogTitled("초안").getWbsItemId()).isNull();
        }

        @Test
        @DisplayName("상위 없는 Task는 거부한다")
        void rejectsOrphanTask() {
            assertThatThrownBy(() -> service.importProject(backlogFile(List.of(), List.of(),
                    List.of(backlog(80L, null, null, BacklogItemType.TASK, "떠도는 Task", null)))))
                    .isInstanceOf(InvalidImportException.class)
                    .hasMessageContaining("Task");
            assertThat(projects).isEmpty();
        }

        @Test
        @DisplayName("허용되지 않는 계층(Epic 아래 Task)은 거부한다")
        void rejectsInvalidHierarchy() {
            assertThatThrownBy(() -> service.importProject(backlogFile(List.of(), List.of(),
                    List.of(
                            backlog(80L, null, null, BacklogItemType.EPIC, "묶음", null),
                            backlog(81L, null, 80L, BacklogItemType.TASK, "잘못된 Task", null)))))
                    .isInstanceOf(InvalidImportException.class)
                    .hasMessageContaining("하위가 될 수 없습니다");
            assertThat(projects).isEmpty();
        }

        @Test
        @DisplayName("파일에 없는 상위를 가리키면 거부한다")
        void rejectsDanglingParent() {
            assertThatThrownBy(() -> service.importProject(backlogFile(List.of(), List.of(),
                    List.of(backlog(81L, null, 999L, BacklogItemType.STORY, "고아", null)))))
                    .isInstanceOf(InvalidImportException.class)
                    .hasMessageContaining("상위 항목");
            assertThat(projects).isEmpty();
        }

        @Test
        @DisplayName("서로를 상위로 가리키면 거부한다 (유형 규칙이 순환보다 먼저 잡는다)")
        void rejectsMutualParents() {
            // Story의 상위는 Epic뿐이고 Epic은 상위를 가질 수 없어, 유형 규칙만으로 순환이 이미
            // 불가능하다. 그래서 여기서는 계층 위반으로 먼저 거부된다 —
            // ImportService의 순환 검사는 그 뒤를 받치는 안전장치다.
            assertThatThrownBy(() -> service.importProject(backlogFile(List.of(), List.of(),
                    List.of(
                            backlog(80L, null, 81L, BacklogItemType.STORY, "가", null),
                            backlog(81L, null, 80L, BacklogItemType.STORY, "나", null)))))
                    .isInstanceOf(InvalidImportException.class)
                    .hasMessageContaining("하위가 될 수 없습니다");
            assertThat(projects).isEmpty();
        }
    }

    private ProjectExportResponse backlogFile(List<ExportedMember> members,
                                               List<ExportedWbsItem> wbs,
                                               List<ExportedBacklogItem> backlog) {
        return new ProjectExportResponse(3, LocalDateTime.now(), project("AEGIS"), members, wbs,
                List.of(), List.of(), List.of(), backlog, List.of(), List.of(),
                List.of(), List.of(), List.of());
    }

    private ExportedBacklogItem backlog(Long id, Long wbsItemId, Long parentId,
                                         BacklogItemType type, String title, Long assigneeId) {
        return new ExportedBacklogItem(id, wbsItemId, parentId, type, title, null,
                BacklogPriority.MEDIUM, BacklogStatus.TODO, assigneeId, null, null, null, null, 0,
                false, null, null);
    }

    private BacklogItem backlogTitled(String title) {
        return backlogItems.stream()
                .filter(item -> item.getTitle().equals(title))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Backlog 항목이 없습니다: " + title));
    }

    private ProjectExportResponse wbsFile(int formatVersion, List<ExportedWbsItem> wbs) {
        return new ProjectExportResponse(formatVersion, LocalDateTime.now(), project("AEGIS"),
                List.of(), wbs, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of());
    }

    private ExportedProject project(String name) {
        return new ExportedProject(1L, name, "설명", ProjectStatus.IN_PROGRESS,
                LocalDate.parse("2026-09-01"), LocalDate.parse("2026-10-31"), null, null);
    }

    private ExportedMember member(Long id, String name) {
        return new ExportedMember(id, name, null, "PM");
    }

    /** The formatVersion 1 shape: no {@code nodeType}, no {@code executionMode}. */
    private ExportedWbsItem wbs(Long id, Long parentId, String name) {
        return new ExportedWbsItem(id, parentId, "무시됨", name, null, null, null, 0, 0, null, null,
                null, null, null, null, null, null);
    }

    private ExportedWbsItem wbs(Long id, Long parentId, String name,
                                 WbsNodeType nodeType, ExecutionMode executionMode) {
        return new ExportedWbsItem(id, parentId, "무시됨", name, null, null, null, 0, 0,
                nodeType, executionMode, null, null, null, null, null, null);
    }

    /** formatVersion 5 이하의 모양 — 단일 {@code wbsItemId}, links 없음. */
    private ExportedRaidItem raid(Long id, String title, Long ownerId, Long wbsItemId) {
        return new ExportedRaidItem(id, RaidType.RISK, title, null, RaidStatus.OPEN,
                null, null, ownerId, wbsItemId, null, null, null);
    }

    /** formatVersion 6의 모양 — 연결이 여럿일 수 있다. */
    private ExportedRaidItem raidWithLinks(Long id, String title, Long ownerId,
                                            List<ExportedRaidLink> links) {
        return new ExportedRaidItem(id, RaidType.RISK, title, null, RaidStatus.OPEN,
                null, null, ownerId, null, links, null, null);
    }

    private ExportedRaidLink raidLink(Long id, RaidLinkTarget targetType, Long targetId) {
        return new ExportedRaidLink(id, targetType, targetId);
    }

    private RaidLinkRepository raidLinkRepository() {
        return new RaidLinkRepository() {
            @Override
            public RaidLink save(RaidLink link) {
                ReflectionTestUtils.setField(link, "id", ids.incrementAndGet());
                raidLinks.add(link);
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

    private WbsItem byName(String name) {
        return wbsItems.stream()
                .filter(item -> item.getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("WBS 항목이 없습니다: " + name));
    }

    private Project existingProject(String name) {
        Project project = new Project(name, null, ProjectStatus.PLANNED, null, null);
        ReflectionTestUtils.setField(project, "id", ids.incrementAndGet());
        return project;
    }

    // ------------------------------------------------------------- 인메모리 저장소

    private BacklogItemRepository backlogItemRepository() {
        return new BacklogItemRepository() {
            @Override
            public BacklogItem save(BacklogItem item) {
                backlogItems.add(withId(item));
                return item;
            }

            @Override
            public List<BacklogItem> saveAll(List<BacklogItem> items) {
                items.forEach(this::save);
                return items;
            }

            @Override
            public Optional<BacklogItem> findById(Long id) {
                return backlogItems.stream().filter(i -> i.getId().equals(id)).findFirst();
            }

            @Override
            public List<BacklogItem> findByProjectId(Long projectId) {
                return backlogItems.stream().filter(i -> i.getProjectId().equals(projectId)).toList();
            }

            @Override
            public void delete(BacklogItem item) {
                backlogItems.remove(item);
            }
        };
    }

    private SprintRepository sprintRepository() {
        return new SprintRepository() {
            @Override
            public Sprint save(Sprint sprint) {
                sprints.add(withId(sprint));
                return sprint;
            }

            @Override
            public Optional<Sprint> findById(Long id) {
                return sprints.stream().filter(s -> s.getId().equals(id)).findFirst();
            }

            @Override
            public List<Sprint> findByProjectId(Long projectId) {
                return sprints.stream().filter(s -> s.getProjectId().equals(projectId)).toList();
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
                sprintItems.add(withId(item));
                return item;
            }

            @Override
            public List<SprintItem> saveAll(List<SprintItem> items) {
                items.forEach(this::save);
                return items;
            }

            @Override
            public List<SprintItem> findByProjectId(Long projectId) {
                return sprintItems.stream().filter(i -> i.getProjectId().equals(projectId)).toList();
            }
        };
    }

    private AcceptanceCheckpointRepository checkpointRepository() {
        return new AcceptanceCheckpointRepository() {
            @Override
            public AcceptanceCheckpoint save(AcceptanceCheckpoint checkpoint) {
                if (checkpoint.getId() == null) {
                    withId(checkpoint);
                }
                if (!checkpoints.contains(checkpoint)) {
                    checkpoints.add(checkpoint);
                }
                return checkpoint;
            }

            @Override
            public Optional<AcceptanceCheckpoint> findById(Long id) {
                return checkpoints.stream().filter(c -> c.getId().equals(id)).findFirst();
            }

            @Override
            public List<AcceptanceCheckpoint> findByProjectId(Long projectId) {
                return checkpoints.stream().filter(c -> c.getProjectId().equals(projectId)).toList();
            }

            @Override
            public void delete(AcceptanceCheckpoint checkpoint) {
                checkpoints.remove(checkpoint);
            }
        };
    }

    private BaselineRepository baselineRepository() {
        return new BaselineRepository() {
            @Override
            public Baseline save(Baseline baseline) {
                baselines.add(withId(baseline));
                return baseline;
            }

            @Override
            public List<BaselineItem> saveItems(List<BaselineItem> items) {
                items.forEach(item -> baselineItems.add(withId(item)));
                return items;
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

    private ProgressSnapshotRepository snapshotRepository() {
        return new ProgressSnapshotRepository() {
            @Override
            public ProgressSnapshot save(ProgressSnapshot snapshot) {
                snapshots.add(withId(snapshot));
                return snapshot;
            }

            @Override
            public List<ProgressSnapshot> findByProjectId(Long projectId) {
                return snapshots.stream().filter(s -> s.getProjectId().equals(projectId)).toList();
            }
        };
    }

    private <T> T withId(T entity) {
        ReflectionTestUtils.setField(entity, "id", ids.incrementAndGet());
        return entity;
    }

    private ProjectRepository projectRepository() {
        return new ProjectRepository() {
            @Override
            public Project save(Project project) {
                projects.add(withId(project));
                return project;
            }

            @Override
            public Optional<Project> findById(Long id) {
                return projects.stream().filter(p -> p.getId().equals(id)).findFirst();
            }

            @Override
            public List<Project> findAll() {
                return List.copyOf(projects);
            }

            @Override
            public void deleteById(Long id) {
                projects.removeIf(p -> p.getId().equals(id));
            }

            @Override
            public boolean existsById(Long id) {
                return findById(id).isPresent();
            }
        };
    }

    private ProjectMemberRepository memberRepository() {
        return new ProjectMemberRepository() {
            @Override
            public ProjectMember save(ProjectMember member) {
                members.add(withId(member));
                return member;
            }

            @Override
            public Optional<ProjectMember> findById(Long id) {
                return members.stream().filter(m -> m.getId().equals(id)).findFirst();
            }

            @Override
            public List<ProjectMember> findByProjectId(Long projectId) {
                return members.stream().filter(m -> m.getProjectId().equals(projectId)).toList();
            }

            @Override
            public void delete(ProjectMember member) {
                members.remove(member);
            }
        };
    }

    private WbsItemRepository wbsItemRepository() {
        return new WbsItemRepository() {
            @Override
            public WbsItem save(WbsItem item) {
                wbsItems.add(withId(item));
                return item;
            }

            @Override
            public List<WbsItem> saveAll(List<WbsItem> items) {
                items.forEach(this::save);
                return items;
            }

            @Override
            public Optional<WbsItem> findById(Long id) {
                return wbsItems.stream().filter(i -> i.getId().equals(id)).findFirst();
            }

            @Override
            public List<WbsItem> findByProjectId(Long projectId) {
                return wbsItems.stream().filter(i -> i.getProjectId().equals(projectId)).toList();
            }

            @Override
            public void delete(WbsItem item) {
                wbsItems.remove(item);
            }
        };
    }

    private WbsDependencyRepository dependencyRepository() {
        return new WbsDependencyRepository() {
            @Override
            public WbsDependency save(WbsDependency dependency) {
                dependencies.add(withId(dependency));
                return dependency;
            }

            @Override
            public Optional<WbsDependency> findById(Long id) {
                return dependencies.stream().filter(d -> d.getId().equals(id)).findFirst();
            }

            @Override
            public List<WbsDependency> findByProjectId(Long projectId) {
                return dependencies.stream().filter(d -> d.getProjectId().equals(projectId)).toList();
            }

            @Override
            public void delete(WbsDependency dependency) {
                dependencies.remove(dependency);
            }
        };
    }

    private RaciAssignmentRepository raciAssignmentRepository() {
        return new RaciAssignmentRepository() {
            @Override
            public RaciAssignment save(RaciAssignment assignment) {
                raciAssignments.add(withId(assignment));
                return assignment;
            }

            @Override
            public Optional<RaciAssignment> findById(Long id) {
                return raciAssignments.stream().filter(a -> a.getId().equals(id)).findFirst();
            }

            @Override
            public List<RaciAssignment> findByProjectId(Long projectId) {
                return raciAssignments.stream().filter(a -> a.getProjectId().equals(projectId)).toList();
            }

            @Override
            public void delete(RaciAssignment assignment) {
                raciAssignments.remove(assignment);
            }
        };
    }

    private RaidItemRepository raidItemRepository() {
        return new RaidItemRepository() {
            @Override
            public RaidItem save(RaidItem item) {
                raidItems.add(withId(item));
                return item;
            }

            @Override
            public Optional<RaidItem> findById(Long id) {
                return raidItems.stream().filter(i -> i.getId().equals(id)).findFirst();
            }

            @Override
            public List<RaidItem> findByProjectId(Long projectId) {
                return raidItems.stream().filter(i -> i.getProjectId().equals(projectId)).toList();
            }

            @Override
            public void delete(RaidItem item) {
                raidItems.remove(item);
            }
        };
    }
}
