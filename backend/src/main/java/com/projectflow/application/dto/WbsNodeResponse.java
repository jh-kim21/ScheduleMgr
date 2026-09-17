package com.projectflow.application.dto;

import com.projectflow.domain.DelayCalculator;
import com.projectflow.domain.DelayStatus;
import com.projectflow.domain.ExecutionMode;
import com.projectflow.domain.AcceptanceStatus;
import com.projectflow.domain.ExecutionModeSummary;
import com.projectflow.domain.ProgressBasis;
import com.projectflow.domain.ProgressCalculator.ProgressResult;
import com.projectflow.domain.WbsNode;
import com.projectflow.domain.WbsNodeType;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * A WBS tree node as returned by the API. {@code code}, {@code level}, {@code summary}, the delay
 * fields and — for summary nodes — the schedule and progress are all derived server-side, so
 * clients render the tree without recomputing anything.
 *
 * @param summary              whether the schedule and progress above were rolled up from children.
 *                             Distinct from {@code nodeType}: it answers where the dates came from
 * @param nodeType             stored management unit — {@code WORK_PACKAGE} is where the Agile
 *                             execution layer attaches
 * @param executionMode        the entry's own mode, {@code null} for 미지정. Non-null on a
 *                             {@code SUMMARY} entry means a mode retained from before it was
 *                             converted; the screen flags that for cleanup rather than using it
 * @param executionModeSummary how the Work Packages below are executed; {@code null} when the entry
 *                             has no children
 * @param backlogSummary       linked Backlog counts, rolled up from below; {@code null} when there
 *                             is none anywhere in this branch
 * @param weight               share among siblings; {@code null} is "not entered", not 0
 * @param computedProgress     the common aggregation's figure, unrounded. {@code null} = 산정 전,
 *                             which is not 0. {@code progress} above stays the legacy/manual value
 *                             so nothing that read it before Step 5 changed meaning
 * @param progressBasis        how {@code computedProgress} was arrived at
 * @param progressIncomplete   part of the rollup was left out (missing weight, or 산정 전 하위)
 * @param progressNote         why it is 산정 전, in one line
 * @param acceptancePending    진척 100%인데 인수가 남았다 — 최종 완료와 구분해야 한다
 * @param delayStatus          schedule health as of the enclosing {@link WbsTreeResponse#referenceDate}
 * @param expectedProgress     progress the linear baseline expects by that date
 * @param progressGap          percentage points behind that baseline; 0 when on or ahead
 * @param delayDays            days past the planned end date while incomplete; 0 otherwise
 * @param actualStartDate      when work actually started, {@code null} when not recorded — the same
 *                             field {@link GanttResponse.GanttTaskResponse#actualStart()} carries, so
 *                             an edit made here does not have to be re-entered on the Gantt
 * @param actualEndDate        when it actually finished, {@code null} when not recorded
 * @param forecastEndDate      when it now looks like it will finish, separate from the planned
 *                             {@code endDate} so a slip can be stated without rewriting the plan
 * @param responsible          RACI Responsible assigned <em>on this row</em>; empty, never null
 * @param responsibleInherited Responsible inherited from a phase above; empty, never null. Kept
 *                             apart from {@code responsible} for the same reason a RACI cell splits
 *                             {@code roles}/{@code inherited} — an inherited name cannot be removed
 *                             here, the row that holds the letter has to be edited instead
 * @param tags                 업무 분야 attached to this row; empty, never null. A converted Summary
 *                             may still carry some (they are retained, not erased, exactly like
 *                             {@code executionMode})
 * @param tagSummary           the union of the 분야 below, grandchildren included; {@code null}
 *                             when the entry has no children, like {@code executionModeSummary}.
 *                             A Summary's own retained tags are <em>not</em> counted in it
 */
public record WbsNodeResponse(
        Long id,
        Long parentId,
        String code,
        int level,
        String name,
        String description,
        LocalDate startDate,
        LocalDate endDate,
        LocalDate actualStartDate,
        LocalDate actualEndDate,
        LocalDate forecastEndDate,
        int progress,
        boolean summary,
        WbsNodeType nodeType,
        ExecutionMode executionMode,
        ExecutionModeSummary executionModeSummary,
        BacklogSummary backlogSummary,
        Integer weight,
        Integer agileRatio,
        AcceptanceStatus acceptanceStatus,
        Double computedProgress,
        ProgressBasis progressBasis,
        boolean progressIncomplete,
        String progressNote,
        boolean acceptancePending,
        DelayStatus delayStatus,
        int expectedProgress,
        int progressGap,
        long delayDays,
        List<WbsNodeResponse> children,
        List<MemberRef> responsible,
        List<MemberRef> responsibleInherited,
        List<TagRef> tags,
        List<TagRef> tagSummary
) {

    /**
     * Everything a row shows that does not come from the WBS itself: who is responsible (from
     * {@code raci_assignments}) and what 분야 it carries (from {@code wbs_item_tags}).
     *
     * <p>Resolved by {@code WbsService} for the whole project in one pass and handed in keyed by
     * entry id, rather than computed here: both need data this record has no business reaching
     * for, and {@code tagSummary} needs the project's tag order to sort a union by.
     *
     * @param tagSummary {@code null} for an entry with no children — the caller decides, because it
     *                   is the one that can see the tree
     */
    public record RowAnnotations(
            List<MemberRef> responsible,
            List<MemberRef> responsibleInherited,
            List<TagRef> tags,
            List<TagRef> tagSummary
    ) {
        /** Nothing assigned and nothing tagged — also what an entry missing from the map gets. */
        public static final RowAnnotations EMPTY =
                new RowAnnotations(List.of(), List.of(), List.of(), null);
    }

    /**
     * @param backlogByWbsItem Backlog counts linked directly to each entry; rolled up here so a
     *                         collapsed branch still shows that it has execution items
     */
    public static WbsNodeResponse from(WbsNode node, LocalDate referenceDate,
                                        Map<Long, BacklogSummary> backlogByWbsItem,
                                        Map<Long, ProgressResult> progressByWbsItem) {
        return from(node, referenceDate, backlogByWbsItem, progressByWbsItem, Map.of());
    }

    /**
     * @param annotationsByWbsItem per-row 담당자·분야, already resolved for the whole project
     */
    public static WbsNodeResponse from(WbsNode node, LocalDate referenceDate,
                                        Map<Long, BacklogSummary> backlogByWbsItem,
                                        Map<Long, ProgressResult> progressByWbsItem,
                                        Map<Long, RowAnnotations> annotationsByWbsItem) {
        ProgressResult computed = progressByWbsItem.get(node.item().getId());
        // 지연 판정은 저장된 progress가 아니라 실행 방식 기반 값을 우선한다 — 결함 수정(2026-09):
        // Agile Work Package는 진척 칸(computedProgress)이 100%여도 저장된 progress가 그대로면
        // 지연 배지가 낡은 값을 봤다. 미지정(MANUAL)은 computedProgress == 저장값이라 전환 정책
        // ("아무것도 지정하지 않은 프로젝트의 화면 숫자는 그대로")을 그대로 지킨다. 반올림은
        // displayPercent()와 같은 규칙(Math.round)을 쓴다.
        int effectiveProgress = computed != null && computed.percent() != null
                ? (int) Math.round(computed.percent())
                : node.progress();
        DelayCalculator.DelayAssessment delay = DelayCalculator.assess(
                node.startDate(), node.endDate(), effectiveProgress, referenceDate);

        List<WbsNodeResponse> children = node.children().stream()
                .map(child -> from(child, referenceDate, backlogByWbsItem, progressByWbsItem,
                        annotationsByWbsItem))
                .toList();

        RowAnnotations annotations = annotationsByWbsItem
                .getOrDefault(node.item().getId(), RowAnnotations.EMPTY);

        BacklogSummary backlog = backlogByWbsItem
                .getOrDefault(node.item().getId(), BacklogSummary.EMPTY);
        for (WbsNodeResponse child : children) {
            if (child.backlogSummary() != null) {
                backlog = backlog.plus(child.backlogSummary());
            }
        }

        return new WbsNodeResponse(
                node.item().getId(),
                node.item().getParentId(),
                node.code(),
                node.level(),
                node.item().getName(),
                node.item().getDescription(),
                node.startDate(),
                node.endDate(),
                // 실적·예상 종료는 파생값이 아니라 항목 자신의 입력이다 — Summary라고 하위에서
                // 집계하지 않는다(간트의 GanttTaskResponse도 item에서 직접 읽는다).
                node.item().getActualStartDate(),
                node.item().getActualEndDate(),
                node.item().getForecastEndDate(),
                node.progress(),
                node.summary(),
                node.item().getNodeType(),
                node.item().getExecutionMode(),
                node.executionModeSummary(),
                // 아무 것도 없으면 null 로 내려 화면이 빈 칸을 그리지 않게 한다.
                backlog.hasNone() ? null : backlog,
                node.item().getWeight(),
                node.item().getAgileRatio(),
                node.item().getAcceptanceStatus(),
                computed == null ? null : computed.percent(),
                computed == null ? null : computed.basis(),
                computed != null && computed.incomplete(),
                computed == null ? null : computed.note(),
                node.item().getAcceptanceStatus() == AcceptanceStatus.PENDING
                        && computed != null && computed.percent() != null
                        && computed.percent() >= 100,
                delay.status(),
                delay.expectedProgress(),
                delay.progressGap(),
                delay.delayDays(),
                children,
                annotations.responsible(),
                annotations.responsibleInherited(),
                annotations.tags(),
                annotations.tagSummary()
        );
    }
}
