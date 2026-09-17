package com.projectflow.application;

import com.projectflow.application.dto.MemberRef;
import com.projectflow.application.dto.TagRef;
import com.projectflow.application.dto.WbsItemCreateRequest;
import com.projectflow.application.dto.WbsItemMoveRequest;
import com.projectflow.application.dto.WbsItemUpdateRequest;
import com.projectflow.application.dto.WbsNodeResponse;
import com.projectflow.application.dto.WbsTreeResponse;
import com.projectflow.domain.AcceptanceStatus;
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
import com.projectflow.domain.InvalidWbsTagException;
import com.projectflow.domain.Project;
import com.projectflow.domain.ProjectRepository;
import com.projectflow.domain.ProjectStatus;
import com.projectflow.domain.RaciAssignment;
import com.projectflow.domain.RaciAssignmentRepository;
import com.projectflow.domain.RaciRole;
import com.projectflow.domain.Sprint;
import com.projectflow.domain.SprintItem;
import com.projectflow.domain.SprintItemRepository;
import com.projectflow.domain.SprintRepository;
import com.projectflow.domain.ProjectMember;
import com.projectflow.domain.ProjectMemberRepository;
import com.projectflow.domain.WbsItem;
import com.projectflow.domain.WbsItemRepository;
import com.projectflow.domain.WbsItemTag;
import com.projectflow.domain.WbsItemTagRepository;
import com.projectflow.domain.WbsNodeType;
import com.projectflow.domain.WbsTag;
import com.projectflow.domain.WbsTagRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
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
    private final List<ProjectMember> members = new ArrayList<>();
    private final List<RaciAssignment> raciAssignments = new ArrayList<>();
    private final List<WbsTag> tags = new ArrayList<>();
    private final List<WbsItemTag> itemTags = new ArrayList<>();

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
                backlogService, progressService(), raid.service(),
                raciAssignmentRepository(), memberRepository(),
                tagRepository(), itemTagRepository());
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
    @DisplayName("생성 시 부가 필드 — 결함 1")
    class CreateExtras {

        @Test
        @DisplayName("agileRatio·acceptanceStatus·실적/예상 일자가 생성 시점부터 저장된다")
        void persistsBasisAndActualDatesOnCreate() {
            // 프론트의 WbsItemInput은 이 값들을 생성 요청에도 실어 보낸다 — DTO에 필드가 없으면
            // Jackson이 오류 없이 버려서, Hybrid Work Package가 만들자마자 산정 전이 됐다.
            WbsItemCreateRequest request = new WbsItemCreateRequest(
                    null, "인수 테스트", null, null, null, null,
                    WbsNodeType.WORK_PACKAGE, ExecutionMode.HYBRID, 3, 60,
                    AcceptanceStatus.PENDING,
                    LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 7),
                    null);

            service.createItem(PROJECT_ID, request);

            WbsItem saved = byName("인수 테스트");
            assertThat(saved.getWeight()).isEqualTo(3);
            assertThat(saved.getAgileRatio()).isEqualTo(60);
            assertThat(saved.getAcceptanceStatus()).isEqualTo(AcceptanceStatus.PENDING);
            assertThat(saved.getActualStartDate()).isEqualTo(LocalDate.of(2026, 1, 1));
            assertThat(saved.getActualEndDate()).isEqualTo(LocalDate.of(2026, 1, 5));
            assertThat(saved.getForecastEndDate()).isEqualTo(LocalDate.of(2026, 1, 7));
        }

        @Test
        @DisplayName("아무 부가 필드도 보내지 않으면 예전처럼 비워 둔 채 저장된다")
        void leavesExtrasNullWhenOmitted() {
            service.createItem(PROJECT_ID, create(null, "평범한 업무", null, null));

            WbsItem saved = byName("평범한 업무");
            assertThat(saved.getAgileRatio()).isNull();
            assertThat(saved.getAcceptanceStatus()).isNull();
            assertThat(saved.getActualStartDate()).isNull();
            assertThat(saved.getActualEndDate()).isNull();
            assertThat(saved.getForecastEndDate()).isNull();
        }
    }

    @Nested
    @DisplayName("Summary 저장 시 집계값 보호 — 결함 2")
    class RolledUpProtection {

        @Test
        @DisplayName("하위가 있는 항목을 수정하면 요청에 담긴 집계 일정·진행률을 무시하고 저장된 값을 유지한다")
        void ignoresRolledUpFieldsOnUpdate() {
            service.createItem(PROJECT_ID, create(null, "단계", WbsNodeType.SUMMARY, null));
            Long stage = byName("단계").getId();
            service.createItem(PROJECT_ID, create(stage, "하위", WbsNodeType.WORK_PACKAGE, null));

            // 화면의 startDate/endDate/progress 입력칸은 비활성화돼 있지만, 값 자체는 여전히 이
            // 항목의 집계값을 담고 있다가 그대로 제출된다 — 서버가 최종 방어선이어야 한다.
            WbsItemUpdateRequest request = new WbsItemUpdateRequest(
                    "단계 이름 변경", null, LocalDate.of(2099, 1, 1), LocalDate.of(2099, 1, 2), 99,
                    WbsNodeType.SUMMARY, null, null, null, null, null, null, null, null);

            service.updateItem(PROJECT_ID, stage, request);

            WbsItem saved = byName("단계 이름 변경");
            assertThat(saved.getStartDate()).isNull();
            assertThat(saved.getEndDate()).isNull();
            assertThat(saved.getProgress()).isEqualTo(0);
        }

        @Test
        @DisplayName("하위가 없는 항목은 평소대로 요청한 일정·진행률을 저장한다")
        void updatesOwnScheduleWhenLeaf() {
            service.createItem(PROJECT_ID, create(null, "개발", WbsNodeType.WORK_PACKAGE, null));
            Long id = byName("개발").getId();

            WbsItemUpdateRequest request = new WbsItemUpdateRequest(
                    "개발", null, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 10), 40,
                    WbsNodeType.WORK_PACKAGE, null, null, null, null, null, null, null, null);

            service.updateItem(PROJECT_ID, id, request);

            WbsItem saved = byName("개발");
            assertThat(saved.getStartDate()).isEqualTo(LocalDate.of(2026, 3, 1));
            assertThat(saved.getEndDate()).isEqualTo(LocalDate.of(2026, 3, 10));
            assertThat(saved.getProgress()).isEqualTo(40);
        }
    }

    @Nested
    @DisplayName("파일 가져오기")
    class Import {

        @Test
        @DisplayName("들여쓰기 레벨 그대로 부모/자식 트리를 만들고 구분은 자식 유무로 정한다")
        void buildsTreeFromLevels() {
            List<WbsImportRow> rows = List.of(
                    new WbsImportRow(2, 1, "설계", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 10), 0),
                    new WbsImportRow(3, 2, "화면 설계", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 5), 0),
                    new WbsImportRow(4, 3, "로그인 화면", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 3), 50),
                    new WbsImportRow(5, 2, "DB 설계", LocalDate.of(2026, 1, 6), LocalDate.of(2026, 1, 10), 0),
                    new WbsImportRow(6, 1, "개발", LocalDate.of(2026, 1, 11), LocalDate.of(2026, 1, 20), 0)
            );

            service.importRows(PROJECT_ID, null, rows);

            WbsItem design = byName("설계");
            WbsItem screenDesign = byName("화면 설계");
            WbsItem login = byName("로그인 화면");
            WbsItem dbDesign = byName("DB 설계");
            WbsItem dev = byName("개발");

            assertThat(design.getParentId()).isNull();
            assertThat(screenDesign.getParentId()).isEqualTo(design.getId());
            assertThat(login.getParentId()).isEqualTo(screenDesign.getId());
            assertThat(dbDesign.getParentId()).isEqualTo(design.getId());
            assertThat(dev.getParentId()).isNull();

            // 자식이 있는 행은 Summary, leaf는 Work Package — 파일에는 이 값이 없다.
            assertThat(design.getNodeType()).isEqualTo(WbsNodeType.SUMMARY);
            assertThat(screenDesign.getNodeType()).isEqualTo(WbsNodeType.SUMMARY);
            assertThat(login.getNodeType()).isEqualTo(WbsNodeType.WORK_PACKAGE);
            assertThat(dbDesign.getNodeType()).isEqualTo(WbsNodeType.WORK_PACKAGE);
            assertThat(dev.getNodeType()).isEqualTo(WbsNodeType.WORK_PACKAGE);
        }

        @Test
        @DisplayName("실행 방식·가중치는 이 경로로 설정되지 않는다")
        void leavesExecutionModeAndWeightUnset() {
            List<WbsImportRow> rows = List.of(
                    new WbsImportRow(2, 1, "업무", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 10), 30)
            );

            service.importRows(PROJECT_ID, null, rows);

            WbsItem saved = byName("업무");
            assertThat(saved.getExecutionMode()).isNull();
            assertThat(saved.getWeight()).isNull();
            assertThat(saved.getProgress()).isEqualTo(30);
            // 실행 방식을 지정한 적이 없으므로 이력도 없다.
            assertThat(modeChanges()).isEmpty();
        }

        @Test
        @DisplayName("parentId를 지정하면 그 아래에 붙는다")
        void insertsUnderGivenParent() {
            service.createItem(PROJECT_ID, create(null, "기존 단계", WbsNodeType.SUMMARY, null));
            Long parent = byName("기존 단계").getId();

            List<WbsImportRow> rows = List.of(
                    new WbsImportRow(2, 1, "가져온 업무", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 2), 0)
            );
            service.importRows(PROJECT_ID, parent, rows);

            assertThat(byName("가져온 업무").getParentId()).isEqualTo(parent);
        }

        @Test
        @DisplayName("기존 형제 뒤에 정렬 순서를 이어 붙인다")
        void appendsAfterExistingSiblings() {
            service.createItem(PROJECT_ID, create(null, "기존 업무", null, null));

            List<WbsImportRow> rows = List.of(
                    new WbsImportRow(2, 1, "가져온 업무", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 2), 0)
            );
            service.importRows(PROJECT_ID, null, rows);

            assertThat(byName("가져온 업무").getSortOrder())
                    .isGreaterThan(byName("기존 업무").getSortOrder());
        }

        @Test
        @DisplayName("parentId가 Work Package면 거부한다 — 단일 항목 추가와 같은 규칙")
        void rejectsParentThatIsWorkPackage() {
            service.createItem(PROJECT_ID, create(null, "개발", WbsNodeType.WORK_PACKAGE, ExecutionMode.AGILE));
            Long parent = byName("개발").getId();

            List<WbsImportRow> rows = List.of(
                    new WbsImportRow(2, 1, "하위 업무", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 2), 0)
            );

            assertThatThrownBy(() -> service.importRows(PROJECT_ID, parent, rows))
                    .isInstanceOf(InvalidWbsHierarchyException.class)
                    .hasMessageContaining("Work Package");
            // 거부는 삽입보다 앞서야 한다 — 미리 만들어 둔 부모 하나만 남아 있어야 한다.
            assertThat(items).hasSize(1);
        }

        @Test
        @DisplayName("변경된 트리 전체를 반환한다")
        void returnsWholeTree() {
            List<WbsImportRow> rows = List.of(
                    new WbsImportRow(2, 1, "업무1", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 2), 0),
                    new WbsImportRow(3, 1, "업무2", LocalDate.of(2026, 1, 3), LocalDate.of(2026, 1, 4), 0)
            );

            WbsTreeResponse tree = service.importRows(PROJECT_ID, null, rows);

            assertThat(tree.nodes()).extracting(WbsNodeResponse::name)
                    .containsExactly("업무1", "업무2");
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

    @Nested
    @DisplayName("담당자 — RACI의 Responsible을 읽어 싣는다")
    class Responsible {

        @Test
        @DisplayName("자기 배정은 responsible에, 상위에서 온 것은 responsibleInherited에 담는다")
        void splitsOwnAndInherited() {
            service.createItem(PROJECT_ID, create(null, "단계", WbsNodeType.SUMMARY, null));
            Long stage = byName("단계").getId();
            service.createItem(PROJECT_ID, create(stage, "개발", WbsNodeType.WORK_PACKAGE, null));
            Long dev = byName("개발").getId();

            ProjectMember lead = member("김재학");
            assign(stage, lead.getId(), RaciRole.RESPONSIBLE);

            WbsNodeResponse stageNode = onlyRoot(service.getTree(PROJECT_ID));
            WbsNodeResponse devNode = stageNode.children().get(0);

            assertThat(stageNode.responsible()).extracting(MemberRef::memberId)
                    .containsExactly(lead.getId());
            assertThat(stageNode.responsible()).extracting(MemberRef::name)
                    .containsExactly("김재학");
            assertThat(stageNode.responsibleInherited()).isEmpty();

            assertThat(devNode.responsible()).isEmpty();
            assertThat(devNode.responsibleInherited()).extracting(MemberRef::memberId)
                    .containsExactly(lead.getId());
            assertThat(dev).isEqualTo(devNode.id());
        }

        @Test
        @DisplayName("하위가 자기 담당자를 적으면 그것만 유효하다 — 상위 것은 함께 오지 않는다")
        void ownAssignmentOverridesTheInheritedOne() {
            service.createItem(PROJECT_ID, create(null, "단계", WbsNodeType.SUMMARY, null));
            Long stage = byName("단계").getId();
            service.createItem(PROJECT_ID, create(stage, "개발", WbsNodeType.WORK_PACKAGE, null));
            Long dev = byName("개발").getId();

            assign(stage, member("김재학").getId(), RaciRole.RESPONSIBLE);
            ProjectMember worker = member("이승하");
            assign(dev, worker.getId(), RaciRole.RESPONSIBLE);

            WbsNodeResponse devNode = onlyRoot(service.getTree(PROJECT_ID)).children().get(0);
            assertThat(devNode.responsible()).extracting(MemberRef::memberId)
                    .containsExactly(worker.getId());
            assertThat(devNode.responsibleInherited()).isEmpty();
        }

        @Test
        @DisplayName("ACCOUNTABLE은 싣지 않는다 — 요구는 담당자 하나다")
        void carriesResponsibleOnly() {
            service.createItem(PROJECT_ID, create(null, "개발", WbsNodeType.WORK_PACKAGE, null));
            Long dev = byName("개발").getId();
            assign(dev, member("김재학").getId(), RaciRole.ACCOUNTABLE);

            WbsNodeResponse node = onlyRoot(service.getTree(PROJECT_ID));
            assertThat(node.responsible()).isEmpty();
            assertThat(node.responsibleInherited()).isEmpty();
        }
    }

    @Nested
    @DisplayName("분야 (업무 Tag)")
    class Tags {

        @Test
        @DisplayName("수정은 차집합만 처리한다 — 살아남은 연결은 같은 행 그대로다")
        void updatesByDifferenceOnly() {
            WbsTag service_ = tag("Service");
            WbsTag web = tag("Web");
            WbsTag batch = tag("Batch");
            service.createItem(PROJECT_ID, create(null, "개발", WbsNodeType.WORK_PACKAGE, null));
            Long dev = byName("개발").getId();

            service.updateItem(PROJECT_ID, dev, updateWithTags("개발", WbsNodeType.WORK_PACKAGE,
                    List.of(service_.getId(), web.getId())));
            WbsItemTag survivor = linkOf(dev, service_.getId());

            service.updateItem(PROJECT_ID, dev, updateWithTags("개발", WbsNodeType.WORK_PACKAGE,
                    List.of(service_.getId(), batch.getId())));

            assertThat(tagIdsOf(dev)).containsExactlyInAnyOrder(service_.getId(), batch.getId());
            // 전부 지우고 다시 넣었다면 이 행은 새 객체가 된다 (지시서 부록 함정 8).
            assertThat(linkOf(dev, service_.getId())).isSameAs(survivor);
        }

        @Test
        @DisplayName("null은 그대로 두고, 빈 배열은 전부 해제한다")
        void nullKeepsTagsAndEmptyClearsThem() {
            WbsTag web = tag("Web");
            service.createItem(PROJECT_ID, create(null, "개발", WbsNodeType.WORK_PACKAGE, null));
            Long dev = byName("개발").getId();
            service.updateItem(PROJECT_ID, dev,
                    updateWithTags("개발", WbsNodeType.WORK_PACKAGE, List.of(web.getId())));

            // 이 필드를 모르는 호출자의 저장 — 조용히 지워지면 안 된다.
            service.updateItem(PROJECT_ID, dev, update("개발", WbsNodeType.WORK_PACKAGE, null));
            assertThat(tagIdsOf(dev)).containsExactly(web.getId());

            service.updateItem(PROJECT_ID, dev,
                    updateWithTags("개발", WbsNodeType.WORK_PACKAGE, List.of()));
            assertThat(tagIdsOf(dev)).isEmpty();
        }

        @Test
        @DisplayName("다른 프로젝트의 태그 id는 거부한다")
        void rejectsTagsFromAnotherProject() {
            WbsTag foreign = new WbsTag(999L, "남의 분야", null, 0);
            ReflectionTestUtils.setField(foreign, "id", ids.incrementAndGet());
            tags.add(foreign);
            service.createItem(PROJECT_ID, create(null, "개발", WbsNodeType.WORK_PACKAGE, null));
            Long dev = byName("개발").getId();

            assertThatThrownBy(() -> service.updateItem(PROJECT_ID, dev,
                    updateWithTags("개발", WbsNodeType.WORK_PACKAGE, List.of(foreign.getId()))))
                    .isInstanceOf(InvalidWbsTagException.class)
                    .hasMessageContaining("이 프로젝트에 없는 분야");
            assertThat(tagIdsOf(dev)).isEmpty();
        }

        @Test
        @DisplayName("Summary의 분야는 바꿀 수 없되, 보관값을 그대로 되보내면 통과한다")
        void summaryKeepsButCannotChangeTags() {
            WbsTag web = tag("Web");
            WbsTag batch = tag("Batch");
            service.createItem(PROJECT_ID, create(null, "전환 대상", WbsNodeType.WORK_PACKAGE, null));
            Long id = byName("전환 대상").getId();
            service.updateItem(PROJECT_ID, id,
                    updateWithTags("전환 대상", WbsNodeType.WORK_PACKAGE, List.of(web.getId())));

            // 실행 방식과 같은 규칙 — 전환해도 지우지 않고, 폼이 되돌려 보낸 같은 집합은 통과한다.
            service.updateItem(PROJECT_ID, id,
                    updateWithTags("전환 대상", WbsNodeType.SUMMARY, List.of(web.getId())));
            assertThat(tagIdsOf(id)).containsExactly(web.getId());

            assertThatThrownBy(() -> service.updateItem(PROJECT_ID, id,
                    updateWithTags("전환 대상", WbsNodeType.SUMMARY, List.of(batch.getId()))))
                    .isInstanceOf(InvalidWbsTagException.class)
                    .hasMessageContaining("Summary");
            assertThat(tagIdsOf(id)).containsExactly(web.getId());
        }

        @Test
        @DisplayName("생성 요청의 분야도 함께 붙는다. Summary로 만들면서 붙이는 것은 거부하고 항목도 만들지 않는다")
        void appliesTagsOnCreate() {
            WbsTag web = tag("Web");

            service.createItem(PROJECT_ID,
                    createWithTags(null, "개발", WbsNodeType.WORK_PACKAGE, List.of(web.getId())));
            assertThat(tagIdsOf(byName("개발").getId())).containsExactly(web.getId());

            assertThatThrownBy(() -> service.createItem(PROJECT_ID,
                    createWithTags(null, "단계", WbsNodeType.SUMMARY, List.of(web.getId()))))
                    .isInstanceOf(InvalidWbsTagException.class);
            // 거부는 삽입보다 앞에 있다 — 거부된 요청이 항목만 만들어 놓고 끝나면 안 된다.
            assertThat(items).extracting(WbsItem::getName).doesNotContain("단계");
        }

        @Test
        @DisplayName("상위는 하위(손자 포함)의 분야를 마스터 순서로 요약하고, 자식이 없으면 null이다")
        void summarisesTagsFromBelow() {
            WbsTag web = tag("Web");
            WbsTag service_ = tag("Service");
            service.createItem(PROJECT_ID, create(null, "1단계", WbsNodeType.SUMMARY, null));
            Long top = byName("1단계").getId();
            service.createItem(PROJECT_ID, create(top, "1.1단계", WbsNodeType.SUMMARY, null));
            Long mid = byName("1.1단계").getId();
            service.createItem(PROJECT_ID,
                    createWithTags(mid, "손자", WbsNodeType.WORK_PACKAGE, List.of(service_.getId())));
            service.createItem(PROJECT_ID,
                    createWithTags(top, "자식", WbsNodeType.WORK_PACKAGE, List.of(web.getId())));

            WbsNodeResponse topNode = onlyRoot(service.getTree(PROJECT_ID));
            assertThat(topNode.tagSummary()).extracting(TagRef::name)
                    .containsExactly("Web", "Service");
            assertThat(topNode.tags()).isEmpty();

            WbsNodeResponse leaf = topNode.children().stream()
                    .filter(node -> node.name().equals("자식"))
                    .findFirst()
                    .orElseThrow();
            assertThat(leaf.tagSummary()).isNull();
            assertThat(leaf.tags()).extracting(TagRef::name).containsExactly("Web");
        }
    }

    // ------------------------------------------------------------------ 도우미

    private WbsItemCreateRequest create(Long parentId, String name,
                                         WbsNodeType nodeType, ExecutionMode mode) {
        return new WbsItemCreateRequest(parentId, name, null, null, null, null, nodeType,
                mode, null, null, null, null, null, null, null);
    }

    private WbsItemUpdateRequest update(String name, WbsNodeType nodeType, ExecutionMode mode) {
        return new WbsItemUpdateRequest(name, null, null, null, null, nodeType, mode,
                null, null, null, null, null, null, null);
    }

    private WbsItemCreateRequest createWithTags(Long parentId, String name, WbsNodeType nodeType,
                                                  List<Long> tagIds) {
        return new WbsItemCreateRequest(parentId, name, null, null, null, null, nodeType,
                null, null, null, null, null, null, null, tagIds);
    }

    private WbsItemUpdateRequest updateWithTags(String name, WbsNodeType nodeType,
                                                  List<Long> tagIds) {
        return new WbsItemUpdateRequest(name, null, null, null, null, nodeType, null,
                null, null, null, null, null, null, tagIds);
    }

    private ProjectMember member(String name) {
        ProjectMember created = new ProjectMember(PROJECT_ID, name, null, null);
        ReflectionTestUtils.setField(created, "id", ids.incrementAndGet());
        members.add(created);
        return created;
    }

    private void assign(Long wbsItemId, Long memberId, RaciRole role) {
        RaciAssignment assignment = new RaciAssignment(PROJECT_ID, wbsItemId, memberId, role);
        ReflectionTestUtils.setField(assignment, "id", ids.incrementAndGet());
        raciAssignments.add(assignment);
    }

    private WbsTag tag(String name) {
        WbsTag created = new WbsTag(PROJECT_ID, name, null, tags.size());
        ReflectionTestUtils.setField(created, "id", ids.incrementAndGet());
        tags.add(created);
        return created;
    }

    private List<Long> tagIdsOf(Long itemId) {
        return itemTags.stream()
                .filter(link -> link.getWbsItemId().equals(itemId))
                .map(WbsItemTag::getTagId)
                .toList();
    }

    private WbsItemTag linkOf(Long itemId, Long tagId) {
        return itemTags.stream()
                .filter(link -> link.getWbsItemId().equals(itemId) && link.getTagId().equals(tagId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("연결이 없습니다: %d-%d".formatted(itemId, tagId)));
    }

    private WbsNodeResponse onlyRoot(WbsTreeResponse tree) {
        assertThat(tree.nodes()).hasSize(1);
        return tree.nodes().get(0);
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
                if (member.getId() == null) {
                    ReflectionTestUtils.setField(member, "id", ids.incrementAndGet());
                }
                if (!members.contains(member)) {
                    members.add(member);
                }
                return member;
            }

            @Override
            public Optional<ProjectMember> findById(Long id) {
                return members.stream().filter(member -> member.getId().equals(id)).findFirst();
            }

            @Override
            public List<ProjectMember> findByProjectId(Long projectId) {
                return members.stream()
                        .filter(member -> member.getProjectId().equals(projectId))
                        .toList();
            }

            @Override
            public void delete(ProjectMember member) {
                members.remove(member);
            }
        };
    }

    private RaciAssignmentRepository raciAssignmentRepository() {
        return new RaciAssignmentRepository() {
            @Override
            public RaciAssignment save(RaciAssignment assignment) {
                if (assignment.getId() == null) {
                    ReflectionTestUtils.setField(assignment, "id", ids.incrementAndGet());
                }
                if (!raciAssignments.contains(assignment)) {
                    raciAssignments.add(assignment);
                }
                return assignment;
            }

            @Override
            public Optional<RaciAssignment> findById(Long id) {
                return raciAssignments.stream()
                        .filter(assignment -> assignment.getId().equals(id))
                        .findFirst();
            }

            @Override
            public List<RaciAssignment> findByProjectId(Long projectId) {
                return raciAssignments.stream()
                        .filter(assignment -> assignment.getProjectId().equals(projectId))
                        .toList();
            }

            @Override
            public void delete(RaciAssignment assignment) {
                raciAssignments.remove(assignment);
            }
        };
    }

    private WbsTagRepository tagRepository() {
        return new WbsTagRepository() {
            @Override
            public WbsTag save(WbsTag tag) {
                if (tag.getId() == null) {
                    ReflectionTestUtils.setField(tag, "id", ids.incrementAndGet());
                }
                if (!tags.contains(tag)) {
                    tags.add(tag);
                }
                return tag;
            }

            @Override
            public List<WbsTag> findByProjectId(Long projectId) {
                return tags.stream()
                        .filter(tag -> tag.getProjectId().equals(projectId))
                        .toList();
            }

            @Override
            public void delete(WbsTag tag) {
                tags.remove(tag);
            }
        };
    }

    private WbsItemTagRepository itemTagRepository() {
        return new WbsItemTagRepository() {
            @Override
            public List<WbsItemTag> saveAll(List<WbsItemTag> links) {
                itemTags.addAll(links);
                return links;
            }

            @Override
            public List<WbsItemTag> findByWbsItemIdIn(Collection<Long> wbsItemIds) {
                return itemTags.stream()
                        .filter(link -> wbsItemIds.contains(link.getWbsItemId()))
                        .toList();
            }

            @Override
            public List<WbsItemTag> findByTagId(Long tagId) {
                return itemTags.stream()
                        .filter(link -> link.getTagId().equals(tagId))
                        .toList();
            }

            @Override
            public void deleteAll(List<WbsItemTag> links) {
                itemTags.removeAll(links);
            }
        };
    }

}
