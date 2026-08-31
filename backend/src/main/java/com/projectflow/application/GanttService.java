package com.projectflow.application;

import com.projectflow.application.dto.DependencyCreateRequest;
import com.projectflow.application.dto.DependencyUpdateRequest;
import com.projectflow.application.dto.GanttResponse;
import com.projectflow.application.dto.GanttResponse.DependencyResponse;
import com.projectflow.application.dto.GanttResponse.GanttTaskResponse;
import com.projectflow.application.dto.ScheduleRecalculationResponse;
import com.projectflow.domain.CircularDependencyException;
import com.projectflow.domain.CriticalPathCalculator;
import com.projectflow.domain.DelayCalculator;
import com.projectflow.domain.DependencyGraph;
import com.projectflow.domain.InvalidDependencyException;
import com.projectflow.domain.ProjectNotFoundException;
import com.projectflow.domain.ProjectRepository;
import com.projectflow.domain.ScheduleCalculator;
import com.projectflow.domain.WbsDependency;
import com.projectflow.domain.WbsDependencyRepository;
import com.projectflow.domain.WbsItem;
import com.projectflow.domain.WbsItemNotFoundException;
import com.projectflow.domain.WbsItemRepository;
import com.projectflow.domain.WbsNode;
import com.projectflow.domain.WbsTreeAssembler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class GanttService {

    private final WbsItemRepository wbsItemRepository;
    private final WbsDependencyRepository dependencyRepository;
    private final ProjectRepository projectRepository;

    public GanttService(WbsItemRepository wbsItemRepository,
                         WbsDependencyRepository dependencyRepository,
                         ProjectRepository projectRepository) {
        this.wbsItemRepository = wbsItemRepository;
        this.dependencyRepository = dependencyRepository;
        this.projectRepository = projectRepository;
    }

    /** Chart rows, dependencies and constraint violations in one payload (요구사항 6.4). */
    public GanttResponse getGantt(Long projectId) {
        requireProject(projectId);
        return buildGantt(projectId);
    }

    @Transactional
    public GanttResponse addDependency(Long projectId, DependencyCreateRequest request) {
        requireProject(projectId);
        List<WbsDependency> existing = dependencyRepository.findByProjectId(projectId);
        int lagDays = validateEndpoints(
                projectId, existing, null, request.predecessorId(), request.successorId(), request.lagDays());

        dependencyRepository.save(new WbsDependency(
                projectId,
                request.predecessorId(),
                request.successorId(),
                lagDays
        ));
        return buildGantt(projectId);
    }

    /** Retargets an existing link (요구사항 6.2), re-running the same checks as creating one. */
    @Transactional
    public GanttResponse updateDependency(Long projectId, Long dependencyId, DependencyUpdateRequest request) {
        requireProject(projectId);
        List<WbsDependency> existing = dependencyRepository.findByProjectId(projectId);
        WbsDependency dependency = requireDependencyOfProject(existing, dependencyId);
        int lagDays = validateEndpoints(
                projectId, existing, dependencyId, request.predecessorId(), request.successorId(), request.lagDays());

        dependency.update(request.predecessorId(), request.successorId(), lagDays);
        dependencyRepository.save(dependency);
        return buildGantt(projectId);
    }

    @Transactional
    public GanttResponse deleteDependency(Long projectId, Long dependencyId) {
        requireProject(projectId);
        WbsDependency dependency =
                requireDependencyOfProject(dependencyRepository.findByProjectId(projectId), dependencyId);
        dependencyRepository.delete(dependency);
        return buildGantt(projectId);
    }

    /** Pushes violating tasks later until every dependency is satisfied (요구사항 6.6). */
    @Transactional
    public ScheduleRecalculationResponse recalculate(Long projectId) {
        requireProject(projectId);
        List<WbsItem> items = wbsItemRepository.findByProjectId(projectId);
        List<WbsDependency> dependencies = dependencyRepository.findByProjectId(projectId);

        requireRelaxable(items, dependencies);
        Set<Long> shifted = ScheduleCalculator.relax(items, dependencies);
        if (!shifted.isEmpty()) {
            wbsItemRepository.saveAll(items);
        }
        return new ScheduleRecalculationResponse(shifted.size(), buildGantt(projectId));
    }

    private GanttResponse buildGantt(Long projectId) {
        List<WbsItem> items = wbsItemRepository.findByProjectId(projectId);
        List<WbsDependency> dependencies = dependencyRepository.findByProjectId(projectId);

        List<WbsNode> tree = WbsTreeAssembler.assemble(items);
        ScheduleCalculator.ScheduleAnalysis analysis = ScheduleCalculator.analyze(tree, dependencies);
        CriticalPathCalculator.CriticalPathAnalysis criticalPath =
                CriticalPathCalculator.analyze(tree, dependencies);

        List<WbsNode> flattened = new ArrayList<>();
        flatten(tree, flattened);

        // Delay is judged server-side against a single reference date, so every row in one response
        // is measured against the same "today" and a long-open browser tab cannot drift.
        LocalDate referenceDate = LocalDate.now();

        List<GanttTaskResponse> tasks = flattened.stream()
                .map(node -> {
                    DelayCalculator.DelayAssessment delay = DelayCalculator.assess(
                            node.startDate(), node.endDate(), node.progress(), referenceDate);
                    return new GanttTaskResponse(
                            node.item().getId(),
                            node.item().getParentId(),
                            node.code(),
                            node.level(),
                            node.item().getName(),
                            node.summary(),
                            node.startDate(),
                            node.endDate(),
                            node.progress(),
                            analysis.earliestStarts().get(node.item().getId()),
                            analysis.violatedTaskIds().contains(node.item().getId()),
                            delay.status(),
                            delay.expectedProgress(),
                            delay.progressGap(),
                            delay.delayDays(),
                            criticalPath.floatDays().get(node.item().getId()),
                            criticalPath.criticalTaskIds().contains(node.item().getId())
                    );
                })
                .toList();

        LocalDate chartStart = tasks.stream()
                .map(GanttTaskResponse::startDate)
                .filter(Objects::nonNull)
                .min(LocalDate::compareTo)
                .orElse(null);
        LocalDate chartEnd = tasks.stream()
                .map(GanttTaskResponse::endDate)
                .filter(Objects::nonNull)
                .max(LocalDate::compareTo)
                .orElse(null);

        List<DependencyResponse> dependencyResponses = dependencies.stream()
                .map(dependency -> new DependencyResponse(
                        dependency.getId(),
                        dependency.getPredecessorId(),
                        dependency.getSuccessorId(),
                        dependency.getLagDays(),
                        criticalPath.criticalDependencyIds().contains(dependency.getId())
                ))
                .toList();

        return new GanttResponse(chartStart, chartEnd, referenceDate, tasks, dependencyResponses);
    }

    private void flatten(List<WbsNode> nodes, List<WbsNode> target) {
        for (WbsNode node : nodes) {
            target.add(node);
            flatten(node.children(), target);
        }
    }

    private void requireProject(Long projectId) {
        if (!projectRepository.existsById(projectId)) {
            throw new ProjectNotFoundException(projectId);
        }
    }

    private void requireItemOfProject(List<WbsItem> projectItems, Long itemId) {
        boolean present = projectItems.stream().anyMatch(item -> item.getId().equals(itemId));
        if (!present) {
            throw new WbsItemNotFoundException(itemId);
        }
    }

    /**
     * Same-branch links are refused when they are created, but a project saved before that check
     * existed can still hold one — and {@link ScheduleCalculator#relax} cannot converge on it. Name
     * the offending links so the user can delete them, instead of failing with a bare 500.
     */
    private void requireRelaxable(List<WbsItem> items, List<WbsDependency> dependencies) {
        List<WbsDependency> selfReferential =
                ScheduleCalculator.selfReferentialDependencies(items, dependencies);
        if (selfReferential.isEmpty()) {
            return;
        }

        Map<Long, String> labels = labelsByItemId(items);
        String offending = selfReferential.stream()
                .map(dependency -> labels.getOrDefault(dependency.getPredecessorId(), "?")
                        + " → " + labels.getOrDefault(dependency.getSuccessorId(), "?"))
                .collect(Collectors.joining(", "));
        throw new InvalidDependencyException(
                "상위·하위 관계인 항목끼리 걸린 선후행 관계가 있어 재계산할 수 없습니다: " + offending
                        + ". 해당 선후행 관계를 삭제한 뒤 다시 시도하세요.");
    }

    /** "1.2 상세 설계" style labels, so the message matches what the dependency list shows. */
    private Map<Long, String> labelsByItemId(List<WbsItem> items) {
        List<WbsNode> flattened = new ArrayList<>();
        flatten(WbsTreeAssembler.assemble(items), flattened);
        return flattened.stream()
                .collect(Collectors.toMap(
                        node -> node.item().getId(),
                        node -> node.code() + " " + node.item().getName()));
    }

    private WbsDependency requireDependencyOfProject(List<WbsDependency> projectDependencies, Long dependencyId) {
        return projectDependencies.stream()
                .filter(candidate -> candidate.getId().equals(dependencyId))
                .findFirst()
                .orElseThrow(() -> new InvalidDependencyException("선후행 관계를 찾을 수 없습니다: id=" + dependencyId));
    }

    /**
     * Validates the endpoints a link is about to point at and resolves the effective lag.
     *
     * <p>{@code excludedId} is the link being edited, or {@code null} when creating one. Its own
     * edge has to be left out of both checks: otherwise an edit would collide with itself as a
     * duplicate, and its existing edge would look like a cycle the edit closes.
     *
     * @return the lag to store — {@code null} in the request means 0 (start the next day)
     */
    private int validateEndpoints(Long projectId,
                                   List<WbsDependency> existing,
                                   Long excludedId,
                                   Long predecessorId,
                                   Long successorId,
                                   Integer lagDays) {
        if (predecessorId.equals(successorId)) {
            throw new InvalidDependencyException("같은 항목을 선행/후행으로 지정할 수 없습니다.");
        }

        List<WbsItem> items = wbsItemRepository.findByProjectId(projectId);
        requireItemOfProject(items, predecessorId);
        requireItemOfProject(items, successorId);

        List<WbsDependency> others = existing.stream()
                .filter(dependency -> !dependency.getId().equals(excludedId))
                .toList();

        boolean duplicate = others.stream().anyMatch(dependency ->
                dependency.getPredecessorId().equals(predecessorId)
                        && dependency.getSuccessorId().equals(successorId));
        if (duplicate) {
            throw new InvalidDependencyException("이미 등록된 선후행 관계입니다.");
        }

        // An edge predecessor → successor closes a cycle exactly when the predecessor is already
        // reachable from the successor through the remaining edges (요구사항 6.3).
        if (DependencyGraph.of(others).reaches(successorId, predecessorId)) {
            throw new CircularDependencyException("선후행 관계에 순환이 생깁니다.");
        }

        // Rejected for the same reason as a cycle: no valid schedule exists, so neither the chart
        // nor recalculation could do anything sensible with it.
        if (ScheduleCalculator.onSameBranch(items, predecessorId, successorId)) {
            throw new InvalidDependencyException(
                    "상위·하위 관계인 항목끼리는 선후행 관계를 걸 수 없습니다. "
                            + "Summary 일정은 하위 항목에서 계산되므로 만족할 수 있는 일정이 없습니다.");
        }

        return lagDays != null ? lagDays : 0;
    }
}
