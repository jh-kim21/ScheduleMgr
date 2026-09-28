package com.projectflow.application;

import com.projectflow.application.dto.BacklogResponse;
import com.projectflow.application.dto.BacklogResponse.BacklogItemResponse;
import com.projectflow.application.dto.GanttResponse;
import com.projectflow.application.dto.GanttResponse.GanttTaskResponse;
import com.projectflow.application.dto.ProjectMemberResponse;
import com.projectflow.application.dto.RaciMatrixResponse;
import com.projectflow.application.dto.RaciMatrixResponse.RaciCellResponse;
import com.projectflow.application.dto.RaidLogResponse;
import com.projectflow.application.dto.RaidLogResponse.RaidItemResponse;
import com.projectflow.domain.BacklogStatus;
import com.projectflow.domain.DelayStatus;
import com.projectflow.domain.RaciRole;
import com.projectflow.domain.RaidStatus;
import com.projectflow.domain.WorkloadAssessor.MemberLoad;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 누구에게 일이 몰려 있는지 — RACI·간트·Backlog·RAID 네 화면에 흩어져 사람에게 붙는 것을 프로젝트
 * 전체 × 사람 단위로 모은다 (지시서 workload-balance §2-3, §3-2).
 *
 * <p><b>계산은 여기가, 배치는 {@code DashboardService}가 한다.</b> 넷 다 이미 조회된 payload를
 * 인자로 받는다 — {@code DashboardService}가 이미 이 넷을 부르므로 같은 조회를 반복하지 않는다.
 *
 * <p><b>여러 프로젝트를 합산하지 않는다.</b> 구성원은 프로젝트 스코프이고 이름의 유일성도 프로젝트
 * 안에서만 보장되므로, 다른 프로젝트의 같은 이름이 같은 사람인지 알 방법이 없다(지시서 2-1-b). 이
 * 서비스는 항상 한 프로젝트의 payload 네 개만 받고, 그것을 합치는 호출부를 만들면 안 된다.
 */
@Service
public class WorkloadService {

    /**
     * "진행 중"으로 칠 {@link DelayStatus} 값들. {@code UNSCHEDULED}(일정 없음)·{@code NOT_STARTED}
     * (아직 시작 전)·{@code COMPLETED}(끝남)는 제외한다.
     *
     * <p><b>지시서 3-2 규칙 4의 문면과 5장 수동 확인 6번이 서로 어긋난다.</b> 규칙 4는 "진행 중은
     * {@code startDate <= referenceDate <= endDate}이고 완료되지 않은 것"이라고 날짜 범위로 적었지만,
     * 그대로 구현하면 종료일이 지난(=DELAYED) 업무가 빠져 확인 6번("종료일이 지난 담당 업무는 동시
     * 진행 수에도 남아 있다")과 규칙 5(delayedCount가 activeCount 안에서 세어짐)를 둘 다 어긴다.
     * **5장(관찰 가능한 동작)을 따랐다** — 같은 규칙 4가 "완료 판정은 새로 만들지 말고
     * {@code delayStatus}를 읽는다"고도 말하므로, 날짜를 다시 비교하지 않고 이미 계산된 상태값의
     * 집합으로만 판정하는 쪽이 일관된다. 그 결과 {@code ON_TRACK}·{@code AT_RISK}·{@code DELAYED}
     * 셋이 "진행 중"이고, 이 셋 중 {@code DELAYED}·{@code AT_RISK}가 각각 delayedCount·atRiskCount로
     * 그 안에서 다시 세어진다(규칙 5).
     */
    private static final Set<DelayStatus> ACTIVE_STATUSES =
            EnumSet.of(DelayStatus.ON_TRACK, DelayStatus.AT_RISK, DelayStatus.DELAYED);

    /**
     * 구성원별 부하. 열(사람)은 {@code raci.members()}에서 온다 — 부하 0인 사람도 행에 남긴다
     * (규칙 1). {@code referenceDate}는 {@code gantt.referenceDate()}·{@code raid.referenceDate()}에
     * 이미 실려 온 것 하나만 쓴다 — {@code LocalDate.now()}를 다시 읽지 않는다(규칙 8).
     */
    public List<MemberLoad> summarize(RaciMatrixResponse raci, GanttResponse gantt,
                                       BacklogResponse backlog, RaidLogResponse raid) {
        Map<Long, Set<Long>> responsibleByTask = responsibleMembersByTask(raci);
        List<GanttTaskResponse> activeLeaves = activeLeaves(gantt);

        Map<Long, Integer> activeCounts = new HashMap<>();
        Map<Long, Integer> delayedCounts = new HashMap<>();
        Map<Long, Integer> atRiskCounts = new HashMap<>();
        for (GanttTaskResponse task : activeLeaves) {
            for (Long memberId : responsibleByTask.getOrDefault(task.id(), Set.of())) {
                activeCounts.merge(memberId, 1, Integer::sum);
                if (task.delayStatus() == DelayStatus.DELAYED) {
                    delayedCounts.merge(memberId, 1, Integer::sum);
                } else if (task.delayStatus() == DelayStatus.AT_RISK) {
                    atRiskCounts.merge(memberId, 1, Integer::sum);
                }
            }
        }

        Map<Long, Integer> openStoryCounts = new HashMap<>();
        Map<Long, List<Integer>> storyPointsByMember = new HashMap<>();
        for (BacklogItemResponse item : backlog.items()) {
            // 규칙 6(Story·Bug만): item.aggregated()를 쓴다 — item.itemType().aggregated()를 다시
            // 부르지 않는다. 서버가 이미 계산해 응답에 실어 둔 값이라(BacklogResponse:75), 집계 대상
            // 판정이 나중에 바뀌어도(BacklogItemType.aggregated()) 이쪽이 자동으로 따라간다.
            if (item.assigneeMemberId() == null || !item.aggregated()
                    || item.archived() || item.status() == BacklogStatus.DONE) {
                continue;
            }
            openStoryCounts.merge(item.assigneeMemberId(), 1, Integer::sum);
            List<Integer> points = storyPointsByMember.computeIfAbsent(
                    item.assigneeMemberId(), key -> new ArrayList<>());
            if (item.storyPoint() != null) {
                points.add(item.storyPoint());
            }
        }

        Map<Long, Integer> openRaidCounts = new HashMap<>();
        for (RaidItemResponse item : raid.items()) {
            if (item.ownerMemberId() == null || item.status() == RaidStatus.CLOSED) {
                continue;
            }
            openRaidCounts.merge(item.ownerMemberId(), 1, Integer::sum);
        }

        List<MemberLoad> loads = new ArrayList<>();
        for (ProjectMemberResponse member : raci.members()) {
            Long memberId = member.id();
            List<Integer> points = storyPointsByMember.get(memberId);
            // 담당 Story·Bug가 없거나(points == null), 있어도 아무도 포인트를 적지 않았으면(비어
            // 있으면) 0이 아니라 null — 진척의 "산정 전은 0%가 아니다"와 같은 규칙이다.
            Integer storyPoints = (points == null || points.isEmpty())
                    ? null
                    : points.stream().mapToInt(Integer::intValue).sum();

            loads.add(new MemberLoad(
                    memberId,
                    member.name(),
                    activeCounts.getOrDefault(memberId, 0),
                    delayedCounts.getOrDefault(memberId, 0),
                    atRiskCounts.getOrDefault(memberId, 0),
                    openStoryCounts.getOrDefault(memberId, 0),
                    storyPoints,
                    openRaidCounts.getOrDefault(memberId, 0)));
        }
        return loads;
    }

    /**
     * 진행 중인 leaf 중 담당(R)이 상속까지 봐도 없는 것의 수.
     *
     * <p>{@code ControlCard.missingResponsibleCount}(RACI 위반, {@link com.projectflow.domain.RaciValidator})와
     * 같은 판정을 다시 만든 것이 아니다 — 모집단이 다르고, <b>한쪽이 다른 쪽의 부분집합도 아니다.</b>
     * {@code RaciValidator}는 트리의 leaf 전체를 일정 유무·완료 여부와 무관하게 보고, 이건 오늘
     * {@link #ACTIVE_STATUSES}인 leaf만 본다. 그래서 완료된 leaf에 R이 없으면
     * {@code missingResponsibleCount}에만 잡히고(이건 진행 중이 아니므로 빠짐), 반대로 진행 중이며
     * {@code DELAYED}인 leaf에 R이 없으면 둘 다에 잡힌다. 그러니 여기서는 값을 재사용하지 않고
     * 따로 센다.
     */
    public int unassignedActiveCount(RaciMatrixResponse raci, GanttResponse gantt) {
        Map<Long, Set<Long>> responsibleByTask = responsibleMembersByTask(raci);
        return (int) activeLeaves(gantt).stream()
                .filter(task -> responsibleByTask.getOrDefault(task.id(), Set.of()).isEmpty())
                .count();
    }

    /**
     * 셀마다 R을 쥔 사람을 모은다 — 셀의 {@code roles}(자기 배정)와 {@code inherited}(재정의되지
     * 않은 것) 양쪽을 본다(규칙 2). 단계에서 물려받은 담당도 그 사람의 일이다(RaciInheritance 설계).
     *
     * <p><b>{@code inherited.overridden() == true}인 항목은 반드시 걸러야 한다.</b> 지시서 문장은
     * "inherited 양쪽을 본다"고만 적었지만, {@code RaciService.buildCells}는 하위에서 이미 다른
     * 사람에게 재정의된 letter도 (화면이 취소선으로 무효를 표시할 수 있도록) {@code inherited}
     * 목록에 그대로 남겨 둔다. 그걸 거르지 않으면 하위에서 이미 다른 사람에게 넘어간 담당을 원래
     * 상위 담당자의 부하로 잘못 센다.
     */
    private Map<Long, Set<Long>> responsibleMembersByTask(RaciMatrixResponse raci) {
        Map<Long, Set<Long>> byTask = new HashMap<>();
        for (RaciCellResponse cell : raci.cells()) {
            boolean holdsResponsible = cell.roles().contains(RaciRole.RESPONSIBLE)
                    || cell.inherited().stream().anyMatch(inherited ->
                            inherited.role() == RaciRole.RESPONSIBLE && !inherited.overridden());
            if (holdsResponsible) {
                byTask.computeIfAbsent(cell.wbsItemId(), key -> new HashSet<>())
                        .add(cell.memberId());
            }
        }
        return byTask;
    }

    /**
     * 지금 진행 중인 leaf만 — Summary는 하위를 이미 반영하므로 함께 세면 중복 집계다(규칙 3, 지연
     * 건수가 leaf만 세는 것과 같은 규칙). "진행 중"은 {@link #ACTIVE_STATUSES}를 참고 — 날짜를
     * 다시 비교하지 않고 이미 계산된 {@code delayStatus}만 읽는다.
     */
    private List<GanttTaskResponse> activeLeaves(GanttResponse gantt) {
        return gantt.tasks().stream()
                .filter(task -> !task.summary())
                .filter(task -> ACTIVE_STATUSES.contains(task.delayStatus()))
                .toList();
    }
}
