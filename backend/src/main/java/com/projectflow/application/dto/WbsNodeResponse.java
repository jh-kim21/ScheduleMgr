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
        List<WbsNodeResponse> children
) {
    /**
     * @param backlogByWbsItem Backlog counts linked directly to each entry; rolled up here so a
     *                         collapsed branch still shows that it has execution items
     */
    public static WbsNodeResponse from(WbsNode node, LocalDate referenceDate,
                                        Map<Long, BacklogSummary> backlogByWbsItem,
                                        Map<Long, ProgressResult> progressByWbsItem) {
        DelayCalculator.DelayAssessment delay = DelayCalculator.assess(
                node.startDate(), node.endDate(), node.progress(), referenceDate);

        List<WbsNodeResponse> children = node.children().stream()
                .map(child -> from(child, referenceDate, backlogByWbsItem, progressByWbsItem))
                .toList();

        ProgressResult computed = progressByWbsItem.get(node.item().getId());

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
                children
        );
    }
}
