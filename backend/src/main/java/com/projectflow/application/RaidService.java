package com.projectflow.application;

import com.projectflow.application.dto.RaidItemRequest;
import com.projectflow.application.dto.RaidLogResponse;
import com.projectflow.application.dto.RaidLogResponse.RaidItemResponse;
import com.projectflow.domain.ProjectMember;
import com.projectflow.domain.ProjectMemberNotFoundException;
import com.projectflow.domain.ProjectMemberRepository;
import com.projectflow.domain.ProjectNotFoundException;
import com.projectflow.domain.ProjectRepository;
import com.projectflow.domain.RaidAssessor;
import com.projectflow.domain.RaidItem;
import com.projectflow.domain.RaidItemNotFoundException;
import com.projectflow.domain.RaidItemRepository;
import com.projectflow.domain.WbsItemNotFoundException;
import com.projectflow.domain.WbsItemRepository;
import com.projectflow.domain.WbsNode;
import com.projectflow.domain.WbsTreeAssembler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** The RAID log (요구사항 9). */
@Service
@Transactional(readOnly = true)
public class RaidService {

    /**
     * Type first so the register reads as four sections, then id so a row never jumps around while
     * being edited. Ordering by urgency is left to the screen, which is where a filter lives.
     */
    private static final Comparator<RaidItem> REGISTER_ORDER =
            Comparator.<RaidItem, Integer>comparing(item -> item.getType().ordinal())
                    .thenComparing(RaidItem::getId);

    private final RaidItemRepository raidItemRepository;
    private final ProjectMemberRepository memberRepository;
    private final WbsItemRepository wbsItemRepository;
    private final ProjectRepository projectRepository;

    public RaidService(RaidItemRepository raidItemRepository,
                        ProjectMemberRepository memberRepository,
                        WbsItemRepository wbsItemRepository,
                        ProjectRepository projectRepository) {
        this.raidItemRepository = raidItemRepository;
        this.memberRepository = memberRepository;
        this.wbsItemRepository = wbsItemRepository;
        this.projectRepository = projectRepository;
    }

    public RaidLogResponse getLog(Long projectId) {
        requireProject(projectId);
        return buildLog(projectId);
    }

    @Transactional
    public RaidLogResponse addItem(Long projectId, RaidItemRequest request) {
        requireProject(projectId);
        requireOwnerOfProject(projectId, request.ownerMemberId());
        requireWbsItemOfProject(projectId, request.wbsItemId());

        raidItemRepository.save(new RaidItem(
                projectId,
                request.type(),
                request.title().trim(),
                blankToNull(request.description()),
                request.status(),
                request.probability(),
                request.impact(),
                request.ownerMemberId(),
                request.wbsItemId(),
                request.dueDate(),
                blankToNull(request.response())
        ));
        return buildLog(projectId);
    }

    @Transactional
    public RaidLogResponse updateItem(Long projectId, Long itemId, RaidItemRequest request) {
        requireProject(projectId);
        requireOwnerOfProject(projectId, request.ownerMemberId());
        requireWbsItemOfProject(projectId, request.wbsItemId());
        RaidItem item = requireItemOfProject(projectId, itemId);

        item.update(
                request.type(),
                request.title().trim(),
                blankToNull(request.description()),
                request.status(),
                request.probability(),
                request.impact(),
                request.ownerMemberId(),
                request.wbsItemId(),
                request.dueDate(),
                blankToNull(request.response())
        );
        raidItemRepository.save(item);
        return buildLog(projectId);
    }

    @Transactional
    public RaidLogResponse deleteItem(Long projectId, Long itemId) {
        requireProject(projectId);
        raidItemRepository.delete(requireItemOfProject(projectId, itemId));
        return buildLog(projectId);
    }

    private RaidLogResponse buildLog(Long projectId) {
        List<RaidItem> items = raidItemRepository.findByProjectId(projectId);

        Map<Long, String> ownerNames = new HashMap<>();
        for (ProjectMember member : memberRepository.findByProjectId(projectId)) {
            ownerNames.put(member.getId(), member.getName());
        }

        // WBS codes are derived from tree position, so the tree has to be assembled to name a
        // linked task — the client cannot work the code out from an id.
        Map<Long, WbsNode> wbsNodes = new HashMap<>();
        collectNodes(WbsTreeAssembler.assemble(wbsItemRepository.findByProjectId(projectId)), wbsNodes);

        // One reference date for the whole payload, so every row is judged against the same "today".
        LocalDate referenceDate = LocalDate.now();

        List<RaidItemResponse> responses = items.stream()
                .sorted(REGISTER_ORDER)
                .map(item -> {
                    RaidAssessor.RaidAssessment assessment = RaidAssessor.assess(item, referenceDate);
                    return new RaidItemResponse(
                            item.getId(),
                            item.getType(),
                            item.getTitle(),
                            item.getDescription(),
                            item.getStatus(),
                            item.getProbability(),
                            item.getImpact(),
                            item.getOwnerMemberId(),
                            item.getOwnerMemberId() == null
                                    ? null
                                    : ownerNames.get(item.getOwnerMemberId()),
                            item.getWbsItemId(),
                            wbsNode(wbsNodes, item.getWbsItemId(), WbsNode::code),
                            wbsNode(wbsNodes, item.getWbsItemId(), node -> node.item().getName()),
                            item.getDueDate(),
                            item.getResponse(),
                            assessment.exposure(),
                            assessment.exposureLevel(),
                            assessment.overdue(),
                            assessment.overdueDays()
                    );
                })
                .toList();

        return new RaidLogResponse(referenceDate, responses);
    }

    private void requireProject(Long projectId) {
        if (!projectRepository.existsById(projectId)) {
            throw new ProjectNotFoundException(projectId);
        }
    }

    private RaidItem requireItemOfProject(Long projectId, Long itemId) {
        return raidItemRepository.findByProjectId(projectId).stream()
                .filter(candidate -> candidate.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new RaidItemNotFoundException(itemId));
    }

    private static void collectNodes(List<WbsNode> nodes, Map<Long, WbsNode> byId) {
        for (WbsNode node : nodes) {
            byId.put(node.item().getId(), node);
            collectNodes(node.children(), byId);
        }
    }

    private static String wbsNode(Map<Long, WbsNode> nodes, Long wbsItemId,
                                   Function<WbsNode, String> field) {
        if (wbsItemId == null) {
            return null;
        }
        WbsNode node = nodes.get(wbsItemId);
        return node == null ? null : field.apply(node);
    }

    /** An owner from another project would show a blank name and belong to nobody. */
    private void requireOwnerOfProject(Long projectId, Long ownerMemberId) {
        if (ownerMemberId == null) {
            return;
        }
        boolean present = memberRepository.findByProjectId(projectId).stream()
                .anyMatch(member -> member.getId().equals(ownerMemberId));
        if (!present) {
            throw new ProjectMemberNotFoundException(ownerMemberId);
        }
    }

    /** A task from another project would render as a blank code in the register. */
    private void requireWbsItemOfProject(Long projectId, Long wbsItemId) {
        if (wbsItemId == null) {
            return;
        }
        boolean present = wbsItemRepository.findByProjectId(projectId).stream()
                .anyMatch(item -> item.getId().equals(wbsItemId));
        if (!present) {
            throw new WbsItemNotFoundException(wbsItemId);
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
