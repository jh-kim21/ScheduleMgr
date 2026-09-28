package com.projectflow.application;

import com.projectflow.application.dto.BacklogResponse;
import com.projectflow.application.dto.BacklogResponse.BacklogItemResponse;
import com.projectflow.application.dto.GanttResponse;
import com.projectflow.application.dto.GanttResponse.GanttTaskResponse;
import com.projectflow.application.dto.ProjectMemberResponse;
import com.projectflow.application.dto.RaciMatrixResponse;
import com.projectflow.application.dto.RaciMatrixResponse.InheritedRoleResponse;
import com.projectflow.application.dto.RaciMatrixResponse.RaciCellResponse;
import com.projectflow.application.dto.RaidLogResponse;
import com.projectflow.application.dto.RaidLogResponse.RaidItemResponse;
import com.projectflow.domain.BacklogItemType;
import com.projectflow.domain.BacklogPriority;
import com.projectflow.domain.BacklogStatus;
import com.projectflow.domain.DelayStatus;
import com.projectflow.domain.ProgressBasis;
import com.projectflow.domain.RaciInheritance.RoleSource;
import com.projectflow.domain.RaciRole;
import com.projectflow.domain.RaidLevel;
import com.projectflow.domain.RaidStatus;
import com.projectflow.domain.RaidType;
import com.projectflow.domain.WorkloadAssessor.MemberLoad;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 규칙은 지시서 workload-balance §3-2에 번호가 매겨져 있다 — 각 테스트는 그 번호 하나씩을 고정한다.
 */
class WorkloadServiceTest {

    private static final LocalDate TODAY = LocalDate.parse("2026-09-10");

    private final WorkloadService service = new WorkloadService();

    @Test
    @DisplayName("구성원이 없으면 빈 목록이다")
    void noMembersMeansEmptyList() {
        RaciMatrixResponse raci = new RaciMatrixResponse(List.of(), List.of(), List.of(), List.of());
        GanttResponse gantt = new GanttResponse(null, null, TODAY, false, null, List.of(), List.of(), List.of());

        List<MemberLoad> loads = service.summarize(raci, gantt, emptyBacklog(), emptyRaid());

        assertThat(loads).isEmpty();
    }

    @Test
    @DisplayName("규칙 2 — 상속된 R도 그 사람의 일이다")
    void countsInheritedResponsible() {
        ProjectMemberResponse member = member(1L, "김철수");
        // R은 상위(100)에만 배정되고, leaf(101)는 그것을 상속만 한다(own roles 없음).
        RaciCellResponse inheritedCell = new RaciCellResponse(101L, 1L, List.of(), List.of(),
                List.of(new InheritedRoleResponse(RaciRole.RESPONSIBLE, RoleSource.INHERITED, 100L,
                        "1", false)));
        RaciMatrixResponse raci = new RaciMatrixResponse(
                List.of(member), List.of(), List.of(inheritedCell), List.of());
        GanttResponse gantt = ganttWith(activeLeaf(101L, false));

        List<MemberLoad> loads = service.summarize(raci, gantt, emptyBacklog(), emptyRaid());

        assertThat(loads).hasSize(1);
        assertThat(loads.get(0).activeCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("규칙 2 — 이 행에서 재정의되어 상속이 무효면 세지 않는다")
    void ignoresOverriddenInheritance() {
        ProjectMemberResponse member = member(1L, "김철수");
        RaciCellResponse overriddenCell = new RaciCellResponse(101L, 1L, List.of(), List.of(),
                List.of(new InheritedRoleResponse(RaciRole.RESPONSIBLE, RoleSource.INHERITED, 100L,
                        "1", true)));
        RaciMatrixResponse raci = new RaciMatrixResponse(
                List.of(member), List.of(), List.of(overriddenCell), List.of());
        GanttResponse gantt = ganttWith(activeLeaf(101L, false));

        List<MemberLoad> loads = service.summarize(raci, gantt, emptyBacklog(), emptyRaid());

        assertThat(loads.get(0).activeCount()).isZero();
    }

    @Test
    @DisplayName("규칙 3 — Summary 행에 R이 있어도 leaf가 아니므로 세지 않는다")
    void doesNotCountSummaryRows() {
        ProjectMemberResponse member = member(1L, "김철수");
        RaciCellResponse ownCell = new RaciCellResponse(200L, 1L,
                List.of(RaciRole.RESPONSIBLE), List.of(10L), List.of());
        RaciMatrixResponse raci = new RaciMatrixResponse(
                List.of(member), List.of(), List.of(ownCell), List.of());
        GanttResponse gantt = ganttWith(activeLeaf(200L, true));

        List<MemberLoad> loads = service.summarize(raci, gantt, emptyBacklog(), emptyRaid());

        assertThat(loads.get(0).activeCount()).isZero();
    }

    @Test
    @DisplayName("규칙 4 — 기간 밖(아직 시작 전)이거나 완료된 업무는 진행 중으로 세지 않는다")
    void periodAndCompletionGateActiveCount() {
        ProjectMemberResponse member = member(1L, "김철수");
        RaciCellResponse onEach = ownResponsible(1L);
        GanttTaskResponse notStarted = task(101L, false, TODAY.plusDays(5), TODAY.plusDays(10),
                DelayStatus.NOT_STARTED);
        GanttTaskResponse completed = task(102L, false, TODAY.minusDays(10), TODAY.minusDays(1),
                DelayStatus.COMPLETED);
        GanttTaskResponse unscheduled = task(103L, false, null, null, DelayStatus.UNSCHEDULED);
        GanttTaskResponse active = task(104L, false, TODAY.minusDays(1), TODAY.plusDays(1),
                DelayStatus.ON_TRACK);

        RaciMatrixResponse raci = new RaciMatrixResponse(List.of(member), List.of(),
                List.of(cellFor(101L, onEach), cellFor(102L, onEach), cellFor(103L, onEach),
                        cellFor(104L, onEach)),
                List.of());
        GanttResponse gantt = ganttWith(notStarted, completed, unscheduled, active);

        List<MemberLoad> loads = service.summarize(raci, gantt, emptyBacklog(), emptyRaid());

        assertThat(loads.get(0).activeCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("규칙 4 — DELAYED·AT_RISK는 activeCount 안에서 함께 센다")
    void delayedAndAtRiskAreCountedWithinActive() {
        ProjectMemberResponse member = member(1L, "김철수");
        RaciCellResponse onEach = ownResponsible(1L);
        GanttTaskResponse delayed = task(101L, false, TODAY.minusDays(10), TODAY.minusDays(1),
                DelayStatus.DELAYED);
        GanttTaskResponse atRisk = task(102L, false, TODAY.minusDays(5), TODAY.plusDays(5),
                DelayStatus.AT_RISK);

        RaciMatrixResponse raci = new RaciMatrixResponse(List.of(member), List.of(),
                List.of(cellFor(101L, onEach), cellFor(102L, onEach)), List.of());
        GanttResponse gantt = ganttWith(delayed, atRisk);

        List<MemberLoad> loads = service.summarize(raci, gantt, emptyBacklog(), emptyRaid());

        MemberLoad load = loads.get(0);
        assertThat(load.activeCount()).isEqualTo(2);
        assertThat(load.delayedCount()).isEqualTo(1);
        assertThat(load.atRiskCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("규칙 6 — Backlog는 Story·Bug만 센다, Epic·Task는 세지 않는다")
    void countsOnlyStoryAndBugInBacklog() {
        ProjectMemberResponse member = member(1L, "김철수");
        RaciMatrixResponse raci = new RaciMatrixResponse(List.of(member), List.of(), List.of(), List.of());
        GanttResponse gantt = ganttWith();

        BacklogItemResponse epic = backlogItem(1L, BacklogItemType.EPIC, BacklogStatus.TODO, 5);
        BacklogItemResponse task = backlogItem(2L, BacklogItemType.TASK, BacklogStatus.TODO, 5);
        BacklogItemResponse story = backlogItem(3L, BacklogItemType.STORY, BacklogStatus.IN_PROGRESS, 3);
        BacklogItemResponse bug = backlogItem(4L, BacklogItemType.BUG, BacklogStatus.TODO, 2);
        BacklogItemResponse doneStory = backlogItem(5L, BacklogItemType.STORY, BacklogStatus.DONE, 8);
        BacklogResponse backlog = new BacklogResponse(0, List.of(epic, task, story, bug, doneStory));

        List<MemberLoad> loads = service.summarize(raci, gantt, backlog, emptyRaid());

        MemberLoad load = loads.get(0);
        assertThat(load.openStoryCount()).isEqualTo(2);
        assertThat(load.storyPoints()).isEqualTo(5);
    }

    @Test
    @DisplayName("담당 Story·Bug가 없으면 storyPoints는 0이 아니라 null이다")
    void storyPointsIsNullWhenNoOpenStories() {
        ProjectMemberResponse member = member(1L, "김철수");
        RaciMatrixResponse raci = new RaciMatrixResponse(List.of(member), List.of(), List.of(), List.of());

        List<MemberLoad> loads = service.summarize(raci, ganttWith(), emptyBacklog(), emptyRaid());

        assertThat(loads.get(0).storyPoints()).isNull();
    }

    @Test
    @DisplayName("RAID는 CLOSED가 아닌 소유 항목만 센다")
    void countsOnlyOpenOwnedRaid() {
        ProjectMemberResponse member = member(1L, "김철수");
        RaciMatrixResponse raci = new RaciMatrixResponse(List.of(member), List.of(), List.of(), List.of());
        RaidItemResponse open = raidItem(1L, 1L, RaidStatus.OPEN);
        RaidItemResponse closed = raidItem(2L, 1L, RaidStatus.CLOSED);
        RaidItemResponse unowned = raidItem(3L, null, RaidStatus.OPEN);
        RaidLogResponse raid = new RaidLogResponse(TODAY, List.of(open, closed, unowned));

        List<MemberLoad> loads = service.summarize(raci, ganttWith(), emptyBacklog(), raid);

        assertThat(loads.get(0).openRaidCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("unassignedActiveCount — 진행 중인데 R이 아무에게도 없는 leaf만 센다")
    void unassignedActiveCountCountsLeavesWithoutResponsible() {
        RaciMatrixResponse raci = new RaciMatrixResponse(
                List.of(member(1L, "김철수")), List.of(), List.of(), List.of());
        GanttTaskResponse activeUnassigned = task(101L, false, TODAY.minusDays(1), TODAY.plusDays(1),
                DelayStatus.ON_TRACK);
        GanttTaskResponse notStarted = task(102L, false, TODAY.plusDays(5), TODAY.plusDays(10),
                DelayStatus.NOT_STARTED);
        GanttResponse gantt = ganttWith(activeUnassigned, notStarted);

        assertThat(service.unassignedActiveCount(raci, gantt)).isEqualTo(1);
    }

    @Test
    @DisplayName("unassignedActiveCount — R이 있으면 세지 않는다")
    void unassignedActiveCountExcludesAssignedLeaves() {
        RaciMatrixResponse raci = new RaciMatrixResponse(
                List.of(member(1L, "김철수")), List.of(),
                List.of(cellFor(101L, ownResponsible(1L))), List.of());
        GanttTaskResponse activeAssigned = task(101L, false, TODAY.minusDays(1), TODAY.plusDays(1),
                DelayStatus.ON_TRACK);
        GanttResponse gantt = ganttWith(activeAssigned);

        assertThat(service.unassignedActiveCount(raci, gantt)).isZero();
    }

    /**
     * missingResponsibleCount(RaciValidator, ControlCard)는 완료 여부와 무관하게 leaf 전체를 본다.
     * unassignedActiveCount는 진행 중인 leaf만 본다 — 완료된 leaf에 R이 없어도 여기서는 안 잡힌다.
     * 한쪽이 다른 쪽의 부분집합이 아니라는 것을 고정한다(지시서 3-2, 팀 리드 지적 사항 5).
     */
    @Test
    @DisplayName("unassignedActiveCount — 완료된 leaf에 R이 없어도 세지 않는다 (missingResponsibleCount와 다른 모집단)")
    void unassignedActiveCountExcludesCompletedLeavesEvenWithoutResponsible() {
        RaciMatrixResponse raci = new RaciMatrixResponse(
                List.of(member(1L, "김철수")), List.of(), List.of(), List.of());
        GanttTaskResponse completedUnassigned = task(101L, false, TODAY.minusDays(10),
                TODAY.minusDays(1), DelayStatus.COMPLETED);
        GanttResponse gantt = ganttWith(completedUnassigned);

        assertThat(service.unassignedActiveCount(raci, gantt)).isZero();
    }

    // ------------------------------------------------------------------ 헬퍼

    private ProjectMemberResponse member(Long id, String name) {
        return new ProjectMemberResponse(id, name, null, null, null, null);
    }

    private GanttResponse ganttWith(GanttTaskResponse... tasks) {
        return new GanttResponse(null, null, TODAY, false, null, List.of(tasks), List.of(), List.of());
    }

    private GanttTaskResponse activeLeaf(Long id, boolean summary) {
        return task(id, summary, TODAY.minusDays(1), TODAY.plusDays(1), DelayStatus.ON_TRACK);
    }

    private GanttTaskResponse task(Long id, boolean summary, LocalDate start, LocalDate end,
                                    DelayStatus delayStatus) {
        return new GanttTaskResponse(id, null, String.valueOf(id), 1, "업무 " + id, summary,
                start, end, 0, null, false, delayStatus, 0, 0, 0, null, false,
                null, null, null, null, null, false, 0, null, ProgressBasis.MANUAL, false, List.of());
    }

    /** 이 wbsItemId에 대해 그 셀 하나만 담긴 RaciCellResponse. */
    private RaciCellResponse cellFor(Long wbsItemId, RaciCellResponse template) {
        return new RaciCellResponse(wbsItemId, template.memberId(), template.roles(),
                template.assignmentIds(), template.inherited());
    }

    private RaciCellResponse ownResponsible(Long memberId) {
        return new RaciCellResponse(null, memberId, List.of(RaciRole.RESPONSIBLE), List.of(1L),
                List.of());
    }

    private BacklogItemResponse backlogItem(Long id, BacklogItemType type, BacklogStatus status,
                                             Integer storyPoint) {
        return new BacklogItemResponse(id, null, null, null, null, null, null, 0, type,
                "제목 " + id, null, BacklogPriority.MEDIUM, status, 1L, "김철수", null, storyPoint,
                null, null, false, false, null, null, null, type.aggregated(), 0, true, false,
                false, false, false);
    }

    private RaidItemResponse raidItem(Long id, Long ownerMemberId, RaidStatus status) {
        return new RaidItemResponse(id, RaidType.RISK, "제목 " + id, null, status, null, null,
                ownerMemberId, ownerMemberId == null ? null : "김철수", List.of(), null, null, null,
                null, false, 0);
    }

    private BacklogResponse emptyBacklog() {
        return new BacklogResponse(0, List.of());
    }

    private RaidLogResponse emptyRaid() {
        return new RaidLogResponse(TODAY, List.of());
    }
}
