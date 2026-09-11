package com.projectflow.application;

import com.projectflow.application.dto.BacklogResponse;
import com.projectflow.application.dto.DashboardResponse;
import com.projectflow.application.dto.DashboardResponse.ActiveSprint;
import com.projectflow.application.dto.DashboardResponse.BlockedItem;
import com.projectflow.application.dto.DashboardResponse.ControlCard;
import com.projectflow.application.dto.DashboardResponse.DataGap;
import com.projectflow.application.dto.DashboardResponse.ExecutionCard;
import com.projectflow.application.dto.DashboardResponse.ModeCount;
import com.projectflow.application.dto.DashboardResponse.ProjectCard;
import com.projectflow.application.dto.DashboardResponse.RaidRef;
import com.projectflow.application.dto.DashboardResponse.ScheduleCard;
import com.projectflow.application.dto.DashboardResponse.SprintVelocity;
import com.projectflow.application.dto.DashboardResponse.TaskRef;
import com.projectflow.application.dto.DashboardResponse.WorkPackageCard;
import com.projectflow.application.dto.GanttResponse;
import com.projectflow.application.dto.GanttResponse.GanttTaskResponse;
import com.projectflow.application.dto.ProgressResponse;
import com.projectflow.application.dto.ProgressResponse.WorkPackageProgress;
import com.projectflow.application.dto.RaciMatrixResponse;
import com.projectflow.application.dto.RaidLogResponse;
import com.projectflow.application.dto.RaidLogResponse.RaidItemResponse;
import com.projectflow.application.dto.SprintResponse;
import com.projectflow.application.dto.SprintResponse.SprintDetail;
import com.projectflow.application.dto.SprintResponse.SprintItemDetail;
import com.projectflow.domain.ExecutionMode;
import com.projectflow.domain.Project;
import com.projectflow.domain.ProjectNotFoundException;
import com.projectflow.domain.ProjectRepository;
import com.projectflow.domain.RaciValidator;
import com.projectflow.domain.RaidLevel;
import com.projectflow.domain.RaidStatus;
import com.projectflow.domain.RaidType;
import com.projectflow.domain.SprintStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The project dashboard (지시서 Step 7).
 *
 * <p><b>This service computes nothing.</b> It calls the services that already own each figure and
 * arranges what they return. The completion criterion is that the WBS, the Gantt and the dashboard
 * agree; the only way to guarantee that is to read the same numbers rather than derive them again.
 * The one thing it does do is <em>select</em> — which rows are worth putting on a single screen —
 * and even that keeps every id so the number leads back to the row.
 *
 * <p>The reference date comes from the progress payload, and every other payload is asked in the
 * same request; a row judged against a different "today" would make two cards disagree.
 */
@Service
@Transactional(readOnly = true)
public class DashboardService {

    /** Enough to name the problem on a card. The full list is one click away on its own screen. */
    private static final int LIST_LIMIT = 5;

    private final ProjectRepository projectRepository;
    private final ProgressService progressService;
    private final GanttService ganttService;
    private final SprintService sprintService;
    private final BacklogService backlogService;
    private final RaciService raciService;
    private final RaidService raidService;

    public DashboardService(ProjectRepository projectRepository,
                             ProgressService progressService,
                             GanttService ganttService,
                             SprintService sprintService,
                             BacklogService backlogService,
                             RaciService raciService,
                             RaidService raidService) {
        this.projectRepository = projectRepository;
        this.progressService = progressService;
        this.ganttService = ganttService;
        this.sprintService = sprintService;
        this.backlogService = backlogService;
        this.raciService = raciService;
        this.raidService = raidService;
    }

    public DashboardResponse getDashboard(Long projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ProjectNotFoundException(projectId));

        ProgressResponse progress = progressService.getProgress(projectId);
        GanttResponse gantt = ganttService.getGantt(projectId);
        SprintResponse sprints = sprintService.getSprints(projectId);
        BacklogResponse backlog = backlogService.getBacklog(projectId);
        RaciMatrixResponse raci = raciService.getMatrix(projectId);
        RaidLogResponse raid = raidService.getLog(projectId);

        return new DashboardResponse(
                progress.referenceDate(),
                projectCard(project, gantt),
                progress.project(),
                progress.baseline(),
                scheduleCard(gantt),
                executionCard(sprints, backlog, raid),
                velocity(sprints),
                workPackageCard(progress),
                controlCard(raci, raid),
                progress.scope(),
                gaps(progress, backlog)
        );
    }

    private ProjectCard projectCard(Project project, GanttResponse gantt) {
        LocalDate planStart = gantt.tasks().stream()
                .map(GanttTaskResponse::startDate)
                .filter(java.util.Objects::nonNull)
                .min(LocalDate::compareTo)
                .orElse(null);
        LocalDate planEnd = gantt.tasks().stream()
                .map(GanttTaskResponse::endDate)
                .filter(java.util.Objects::nonNull)
                .max(LocalDate::compareTo)
                .orElse(null);
        // 예상 종료를 적지 않은 항목은 계획대로 끝난다고 본다 — 예측의 부재는 "늦지 않는다"가
        // 아니라 "달리 알려진 바 없다"이고, 그 경우 계획이 가장 좋은 추정이다.
        LocalDate forecastEnd = gantt.tasks().stream()
                .map(task -> task.forecastEnd() != null ? task.forecastEnd() : task.endDate())
                .filter(java.util.Objects::nonNull)
                .max(LocalDate::compareTo)
                .orElse(null);

        return new ProjectCard(project.getId(), project.getName(), project.getStatus(),
                project.getStartDate(), project.getEndDate(), planStart, planEnd, forecastEnd);
    }

    /**
     * Schedule health as the Gantt already judged it.
     *
     * <p>Counts are over leaves only. A summary reflects its children's delay, so counting both
     * reports one late task once per level — the rule the WBS and Gantt screens already follow.
     */
    private ScheduleCard scheduleCard(GanttResponse gantt) {
        List<GanttTaskResponse> leaves = gantt.tasks().stream()
                .filter(task -> !task.summary())
                .toList();
        List<GanttTaskResponse> delayed = leaves.stream()
                .filter(task -> task.delayStatus() == com.projectflow.domain.DelayStatus.DELAYED)
                .sorted(Comparator.comparingLong(GanttTaskResponse::delayDays).reversed())
                .toList();
        List<GanttTaskResponse> atRisk = leaves.stream()
                .filter(task -> task.delayStatus() == com.projectflow.domain.DelayStatus.AT_RISK)
                .toList();
        List<GanttTaskResponse> exceeded = gantt.tasks().stream()
                .filter(GanttTaskResponse::baselineExceeded)
                .sorted(Comparator.comparingLong(GanttTaskResponse::baselineSlipDays).reversed())
                .toList();
        List<GanttTaskResponse> pending = gantt.tasks().stream()
                .filter(GanttTaskResponse::acceptancePending)
                .toList();

        return new ScheduleCard(
                delayed.size(),
                atRisk.size(),
                delayed.stream().mapToLong(GanttTaskResponse::delayDays).max().orElse(0),
                (int) gantt.tasks().stream().filter(GanttTaskResponse::criticalPath).count(),
                (int) gantt.tasks().stream().filter(GanttTaskResponse::scheduleViolation).count(),
                taskRefs(delayed, task -> task.delayDays() + "일 지연"),
                taskRefs(exceeded, task -> "기준 대비 " + task.baselineSlipDays() + "일 초과"),
                taskRefs(pending, task -> "실행 100% · 인수 대기")
        );
    }

    private List<TaskRef> taskRefs(List<GanttTaskResponse> tasks,
                                    java.util.function.Function<GanttTaskResponse, String> detail) {
        return tasks.stream()
                .limit(LIST_LIMIT)
                .map(task -> new TaskRef(task.id(), task.code(), task.name(), detail.apply(task)))
                .toList();
    }

    /**
     * What is running now.
     *
     * <p>단일 팀 전제라 활성 Sprint는 최대 하나다 (Step 4). With teams this becomes a list, and the
     * velocity series below has to split with it — points from different teams are never added.
     */
    private ExecutionCard executionCard(SprintResponse sprints, BacklogResponse backlog,
                                         RaidLogResponse raid) {
        SprintDetail active = sprints.sprints().stream()
                .filter(sprint -> sprint.status() == SprintStatus.ACTIVE)
                .findFirst()
                .orElse(null);
        if (active == null) {
            return new ExecutionCard(null, backlog.unlinkedCount());
        }

        List<BlockedItem> blocked = active.items().stream()
                .filter(item -> item.blocked() && !item.removed())
                .map(item -> new BlockedItem(
                        item.backlogItemId(),
                        item.title(),
                        item.blockedReason(),
                        item.assigneeName(),
                        wbsItemIdOf(item),
                        item.wbsCode(),
                        relatedRaid(raid, item)))
                .toList();

        return new ExecutionCard(
                new ActiveSprint(active.id(), active.name(), active.goal(),
                        active.startDate(), active.endDate(),
                        active.plannedItems(), active.plannedPoints(),
                        active.doneItems(), active.donePoints(), blocked),
                backlog.unlinkedCount());
    }

    private Long wbsItemIdOf(SprintItemDetail item) {
        return item.wbsItemId();
    }

    /**
     * Open RAID entries related to a blocked card: attached to the entry itself, or to the Work
     * Package it belongs to. Both are what somebody staring at a blocked card is asking about, and
     * an Issue is as often logged against the Work Package as against the Story (지시서 6-C).
     */
    private List<RaidRef> relatedRaid(RaidLogResponse raid, SprintItemDetail item) {
        return raid.items().stream()
                .filter(entry -> entry.status() != RaidStatus.CLOSED)
                .filter(entry -> entry.links().stream().anyMatch(link -> switch (link.targetType()) {
                    case BACKLOG_ITEM -> link.targetId().equals(item.backlogItemId());
                    case WBS_ITEM -> item.wbsItemId() != null
                            && link.targetId().equals(item.wbsItemId());
                    case SPRINT -> false;
                }))
                .map(entry -> raidRef(entry, null))
                .toList();
    }

    /**
     * Completed points per closed Sprint, oldest first, plus the running one kept apart.
     *
     * <p>An open Sprint's number is still moving; drawing it on the same line reads as a drop every
     * time you look before the Sprint ends. {@code inProgress} lets the screen show it beside the
     * trend rather than in it.
     *
     * <p>One series, because there is one team. Should teams arrive, this splits per team and the
     * series must never be summed (지시서 4항).
     */
    private List<SprintVelocity> velocity(SprintResponse sprints) {
        return sprints.sprints().stream()
                .filter(sprint -> sprint.status() != SprintStatus.PLANNED)
                .sorted(Comparator.comparing(SprintDetail::startDate)
                        .thenComparing(SprintDetail::id))
                .map(sprint -> new SprintVelocity(
                        sprint.id(), sprint.name(), sprint.startDate(), sprint.endDate(),
                        sprint.status(), sprint.donePoints(), sprint.doneItems(),
                        sprint.carriedOverItems(),
                        sprint.status() == SprintStatus.ACTIVE))
                .toList();
    }

    private WorkPackageCard workPackageCard(ProgressResponse progress) {
        Map<ExecutionMode, Integer> counts = new LinkedHashMap<>();
        int unspecified = 0;
        for (WorkPackageProgress workPackage : progress.workPackages()) {
            if (workPackage.executionMode() == null) {
                unspecified++;
            } else {
                counts.merge(workPackage.executionMode(), 1, Integer::sum);
            }
        }

        List<ModeCount> byMode = new ArrayList<>();
        for (ExecutionMode mode : ExecutionMode.values()) {
            Integer count = counts.get(mode);
            if (count != null) {
                byMode.add(new ModeCount(mode, count));
            }
        }
        // 미지정은 0건이 아니면 항상 보인다 — 배정되지 않은 Work Package가 확인 대상이다
        // (ExecutionModeSummary와 같은 규칙).
        if (unspecified > 0) {
            byMode.add(new ModeCount(null, unspecified));
        }

        return new WorkPackageCard(
                progress.project().workPackageCount(),
                progress.project().notEstimableCount(),
                progress.project().acceptancePending(),
                byMode);
    }

    private ControlCard controlCard(RaciMatrixResponse raci, RaidLogResponse raid) {
        int missingAccountable = countIssues(raci, RaciValidator.IssueType.MISSING_ACCOUNTABLE);
        int missingResponsible = countIssues(raci, RaciValidator.IssueType.MISSING_RESPONSIBLE);
        int multipleAccountable = countIssues(raci, RaciValidator.IssueType.MULTIPLE_ACCOUNTABLE);

        List<RaidItemResponse> open = raid.items().stream()
                .filter(item -> item.status() != RaidStatus.CLOSED)
                .toList();

        // 총건수는 자르기 전 리스트에서 센다 — 화면이 카드당 5건만 보여줘도(지시서 "카드당 5건"),
        // 헤드라인 숫자까지 5에서 멈추면 안 된다. 필터를 여기서 한 번만 적용하고 size()와
        // limit(LIST_LIMIT) 양쪽에 같은 결과를 먹여, 목록과 숫자가 다른 조건으로 어긋나지 않게 한다.
        List<RaidItemResponse> openIssues = open.stream()
                .filter(item -> item.type() == RaidType.ISSUE)
                .toList();
        List<RaidItemResponse> highExposure = open.stream()
                .filter(item -> item.exposureLevel() == RaidLevel.HIGH)
                .toList();
        List<RaidItemResponse> overdue = open.stream()
                .filter(RaidItemResponse::overdue)
                .sorted(Comparator.comparingLong(RaidItemResponse::overdueDays).reversed())
                .toList();

        return new ControlCard(
                raci.issues().size(),
                missingAccountable,
                missingResponsible,
                multipleAccountable,
                openIssues.size(),
                highExposure.size(),
                overdue.size(),
                openIssues.stream()
                        .limit(LIST_LIMIT)
                        .map(item -> raidRef(item, null))
                        .toList(),
                highExposure.stream()
                        .limit(LIST_LIMIT)
                        .map(item -> raidRef(item, "노출도 " + item.exposure()))
                        .toList(),
                overdue.stream()
                        .limit(LIST_LIMIT)
                        .map(item -> raidRef(item, item.overdueDays() + "일 초과"))
                        .toList());
    }

    private int countIssues(RaciMatrixResponse raci, RaciValidator.IssueType type) {
        return (int) raci.issues().stream().filter(issue -> issue.type() == type).count();
    }

    private RaidRef raidRef(RaidItemResponse item, String detail) {
        return new RaidRef(item.id(), item.type().name(), item.title(), item.ownerName(), detail);
    }

    /**
     * What the figures above cannot answer because nobody has decided it yet (지시서 3항).
     *
     * <p>Reported, not filled in. A missing weight is an open decision, and defaulting it would move
     * every number that reads it without anyone having chosen.
     */
    private List<DataGap> gaps(ProgressResponse progress, BacklogResponse backlog) {
        List<DataGap> gaps = new ArrayList<>();

        List<Long> unspecifiedMode = progress.workPackages().stream()
                .filter(workPackage -> workPackage.executionMode() == null)
                .map(WorkPackageProgress::wbsItemId)
                .toList();
        if (!unspecifiedMode.isEmpty()) {
            gaps.add(new DataGap("EXECUTION_MODE_UNSPECIFIED", "실행 방식 미지정 Work Package",
                    unspecifiedMode.size(), unspecifiedMode, List.of()));
        }

        List<Long> noWeight = progress.workPackages().stream()
                .filter(workPackage -> workPackage.weight() == null)
                .map(WorkPackageProgress::wbsItemId)
                .toList();
        if (!noWeight.isEmpty()) {
            gaps.add(new DataGap("WEIGHT_MISSING", "가중치가 없는 Work Package",
                    noWeight.size(), noWeight, List.of()));
        }

        List<Long> notEstimable = progress.workPackages().stream()
                .filter(workPackage -> workPackage.percent() == null)
                .map(WorkPackageProgress::wbsItemId)
                .toList();
        if (!notEstimable.isEmpty()) {
            gaps.add(new DataGap("NOT_ESTIMABLE", "진척을 산정할 수 없는 Work Package",
                    notEstimable.size(), notEstimable, List.of()));
        }

        List<Long> unlinked = backlog.items().stream()
                .filter(item -> item.unlinked() && item.archivedAt() == null)
                .map(BacklogResponse.BacklogItemResponse::id)
                .toList();
        if (!unlinked.isEmpty()) {
            gaps.add(new DataGap("BACKLOG_UNLINKED", "Work Package에 연결되지 않은 Backlog 항목",
                    unlinked.size(), List.of(), unlinked));
        }

        // Step 5부터 새로 만들 수 없는 상태지만, 그전 데이터에는 남아 있을 수 있다.
        List<Long> toSummary = backlog.items().stream()
                .filter(item -> item.linkedToSummary() || item.danglingLink())
                .map(BacklogResponse.BacklogItemResponse::id)
                .toList();
        if (!toSummary.isEmpty()) {
            gaps.add(new DataGap("BACKLOG_LINK_BROKEN", "귀속 Work Package가 유효하지 않은 Backlog 항목",
                    toSummary.size(), List.of(), toSummary));
        }

        return gaps;
    }
}
