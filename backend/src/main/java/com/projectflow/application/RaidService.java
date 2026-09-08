package com.projectflow.application;

import com.projectflow.application.dto.RaidItemRequest;
import com.projectflow.application.dto.RaidLinkRequest;
import com.projectflow.application.dto.RaidLogResponse;
import com.projectflow.application.dto.RaidLogResponse.RaidItemResponse;
import com.projectflow.application.dto.RaidLogResponse.RaidLinkResponse;
import com.projectflow.domain.BacklogItem;
import com.projectflow.domain.BacklogItemRepository;
import com.projectflow.domain.InvalidRaidLinkException;
import com.projectflow.domain.ProjectMember;
import com.projectflow.domain.ProjectMemberNotFoundException;
import com.projectflow.domain.ProjectMemberRepository;
import com.projectflow.domain.ProjectNotFoundException;
import com.projectflow.domain.ProjectRepository;
import com.projectflow.domain.RaidAssessor;
import com.projectflow.domain.RaidItem;
import com.projectflow.domain.RaidItemNotFoundException;
import com.projectflow.domain.RaidItemRepository;
import com.projectflow.domain.RaidLink;
import com.projectflow.domain.RaidLinkRepository;
import com.projectflow.domain.RaidLinkTarget;
import com.projectflow.domain.Sprint;
import com.projectflow.domain.SprintRepository;
import com.projectflow.domain.WbsItemRepository;
import com.projectflow.domain.WbsNode;
import com.projectflow.domain.WbsTreeAssembler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The RAID log (요구사항 9), attachable to WBS 업무·Sprint·Backlog 항목 (설계 §9). */
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
    private final RaidLinkRepository raidLinkRepository;
    private final ProjectMemberRepository memberRepository;
    private final WbsItemRepository wbsItemRepository;
    private final SprintRepository sprintRepository;
    private final BacklogItemRepository backlogItemRepository;
    private final ProjectRepository projectRepository;

    public RaidService(RaidItemRepository raidItemRepository,
                        RaidLinkRepository raidLinkRepository,
                        ProjectMemberRepository memberRepository,
                        WbsItemRepository wbsItemRepository,
                        SprintRepository sprintRepository,
                        BacklogItemRepository backlogItemRepository,
                        ProjectRepository projectRepository) {
        this.raidItemRepository = raidItemRepository;
        this.raidLinkRepository = raidLinkRepository;
        this.memberRepository = memberRepository;
        this.wbsItemRepository = wbsItemRepository;
        this.sprintRepository = sprintRepository;
        this.backlogItemRepository = backlogItemRepository;
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
        List<RaidLinkRequest> links = validateLinks(projectId, request.links());

        RaidItem saved = raidItemRepository.save(new RaidItem(
                projectId,
                request.type(),
                request.title().trim(),
                blankToNull(request.description()),
                request.status(),
                request.probability(),
                request.impact(),
                request.ownerMemberId(),
                request.dueDate(),
                blankToNull(request.response())
        ));
        replaceLinks(projectId, saved.getId(), links, List.of());
        return buildLog(projectId);
    }

    @Transactional
    public RaidLogResponse updateItem(Long projectId, Long itemId, RaidItemRequest request) {
        requireProject(projectId);
        requireOwnerOfProject(projectId, request.ownerMemberId());
        List<RaidLinkRequest> links = validateLinks(projectId, request.links());
        RaidItem item = requireItemOfProject(projectId, itemId);

        item.update(
                request.type(),
                request.title().trim(),
                blankToNull(request.description()),
                request.status(),
                request.probability(),
                request.impact(),
                request.ownerMemberId(),
                request.dueDate(),
                blankToNull(request.response())
        );
        raidItemRepository.save(item);
        replaceLinks(projectId, itemId, links, linksOf(projectId, itemId));
        return buildLog(projectId);
    }

    @Transactional
    public RaidLogResponse deleteItem(Long projectId, Long itemId) {
        requireProject(projectId);
        RaidItem item = requireItemOfProject(projectId, itemId);
        // raid_links.raid_item_id is ON DELETE CASCADE; going through the port as well keeps the
        // in-memory repositories the service tests use honest about it.
        raidLinkRepository.deleteAll(linksOf(projectId, itemId));
        raidItemRepository.delete(item);
        return buildLog(projectId);
    }

    /**
     * Drops the links pointing at things that are being deleted (지시서 6-C).
     *
     * <p>The RAID entry itself stays: a risk logged against a Work Package is still a risk when the
     * Work Package is reorganised away, and losing the register with the plan is the silent loss
     * this codebase refuses everywhere else. {@code raid_links.target_id} carries no foreign key —
     * it points at three tables — so nothing does this for us.
     *
     * <p><b>보관은 해당하지 않습니다.</b> Archiving says "접어둔다", not "없던 일이다"; the entry can
     * be restored, and a link that vanished on archive could not come back with it.
     */
    @Transactional
    public void detachTargets(Long projectId, RaidLinkTarget targetType, Set<Long> targetIds) {
        if (targetIds.isEmpty()) {
            return;
        }
        List<RaidLink> orphaned = raidLinkRepository.findByProjectId(projectId).stream()
                .filter(link -> link.getTargetType() == targetType)
                .filter(link -> targetIds.contains(link.getTargetId()))
                .toList();
        if (!orphaned.isEmpty()) {
            raidLinkRepository.deleteAll(orphaned);
        }
    }

    private RaidLogResponse buildLog(Long projectId) {
        List<RaidItem> items = raidItemRepository.findByProjectId(projectId);

        Map<Long, String> ownerNames = new HashMap<>();
        for (ProjectMember member : memberRepository.findByProjectId(projectId)) {
            ownerNames.put(member.getId(), member.getName());
        }

        TargetIndex targets = targetIndex(projectId);
        Map<Long, List<RaidLink>> linksByItem = new HashMap<>();
        for (RaidLink link : raidLinkRepository.findByProjectId(projectId)) {
            linksByItem.computeIfAbsent(link.getRaidItemId(), key -> new ArrayList<>()).add(link);
        }

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
                            linkResponses(linksByItem.getOrDefault(item.getId(), List.of()), targets),
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

    /**
     * Display names for every link target in the project, resolved once per request.
     *
     * <p>WBS codes are derived from tree position, so the tree has to be assembled to name a linked
     * task — the client cannot work the code out from an id.
     */
    private TargetIndex targetIndex(Long projectId) {
        Map<Long, WbsNode> wbsNodes = new HashMap<>();
        collectNodes(WbsTreeAssembler.assemble(wbsItemRepository.findByProjectId(projectId)), wbsNodes);

        Map<Long, String> sprintNames = new HashMap<>();
        for (Sprint sprint : sprintRepository.findByProjectId(projectId)) {
            sprintNames.put(sprint.getId(), sprint.getName());
        }
        Map<Long, String> backlogTitles = new HashMap<>();
        for (BacklogItem item : backlogItemRepository.findByProjectId(projectId)) {
            backlogTitles.put(item.getId(), item.getTitle());
        }
        return new TargetIndex(wbsNodes, sprintNames, backlogTitles);
    }

    private List<RaidLinkResponse> linkResponses(List<RaidLink> links, TargetIndex targets) {
        return links.stream()
                .sorted(Comparator.<RaidLink, Integer>comparing(link -> link.getTargetType().ordinal())
                        .thenComparing(RaidLink::getId))
                .map(link -> {
                    Long targetId = link.getTargetId();
                    return switch (link.getTargetType()) {
                        case WBS_ITEM -> {
                            WbsNode node = targets.wbsNodes().get(targetId);
                            yield new RaidLinkResponse(link.getId(), link.getTargetType(), targetId,
                                    node == null ? null : node.code(),
                                    node == null ? null : node.item().getName());
                        }
                        case SPRINT -> new RaidLinkResponse(link.getId(), link.getTargetType(),
                                targetId, null, targets.sprintNames().get(targetId));
                        case BACKLOG_ITEM -> new RaidLinkResponse(link.getId(), link.getTargetType(),
                                targetId, null, targets.backlogTitles().get(targetId));
                    };
                })
                .toList();
    }

    /**
     * Rejects a target from another project, one that does not exist, and one named twice.
     *
     * <p>The duplicate check is the application's job as well as the table's: the UNIQUE index would
     * surface as a 500, while "already linked" is a plain 400 the screen can show.
     */
    private List<RaidLinkRequest> validateLinks(Long projectId, List<RaidLinkRequest> requested) {
        if (requested == null || requested.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        List<RaidLinkRequest> links = new ArrayList<>();
        for (RaidLinkRequest link : requested) {
            if (!seen.add(key(link.targetType(), link.targetId()))) {
                throw new InvalidRaidLinkException("같은 대상을 두 번 연결할 수 없습니다.");
            }
            requireTargetOfProject(projectId, link.targetType(), link.targetId());
            links.add(link);
        }
        return links;
    }

    /**
     * Applies the requested set of links, keeping the rows that are already there.
     *
     * <p>Deleting and re-inserting all of them would be shorter, but every surviving link would get
     * a new id and a new {@code createdAt} on each unrelated edit — "이 위험을 언제 이 Story에
     * 붙였나"가 매번 오늘로 바뀝니다.
     */
    private void replaceLinks(Long projectId, Long raidItemId, List<RaidLinkRequest> requested,
                               List<RaidLink> existing) {
        LinkedHashSet<String> wanted = new LinkedHashSet<>();
        for (RaidLinkRequest link : requested) {
            wanted.add(key(link.targetType(), link.targetId()));
        }

        List<RaidLink> removed = new ArrayList<>();
        LinkedHashSet<String> kept = new LinkedHashSet<>();
        for (RaidLink link : existing) {
            String key = key(link.getTargetType(), link.getTargetId());
            if (wanted.contains(key)) {
                kept.add(key);
            } else {
                removed.add(link);
            }
        }
        if (!removed.isEmpty()) {
            raidLinkRepository.deleteAll(removed);
        }

        List<RaidLink> added = new ArrayList<>();
        for (RaidLinkRequest link : requested) {
            if (!kept.contains(key(link.targetType(), link.targetId()))) {
                added.add(new RaidLink(projectId, raidItemId, link.targetType(), link.targetId()));
            }
        }
        if (!added.isEmpty()) {
            raidLinkRepository.saveAll(added);
        }
    }

    private List<RaidLink> linksOf(Long projectId, Long raidItemId) {
        return raidLinkRepository.findByProjectId(projectId).stream()
                .filter(link -> link.getRaidItemId().equals(raidItemId))
                .toList();
    }

    private static String key(RaidLinkTarget targetType, Long targetId) {
        return targetType + ":" + targetId;
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

    /**
     * {@code raid_links.target_id} carries no foreign key — it points at three different tables —
     * so this is the only thing standing between the register and a link into somebody else's
     * project (설계 §11.3-7).
     */
    private void requireTargetOfProject(Long projectId, RaidLinkTarget targetType, Long targetId) {
        boolean present = switch (targetType) {
            case WBS_ITEM -> wbsItemRepository.findByProjectId(projectId).stream()
                    .anyMatch(item -> item.getId().equals(targetId));
            case SPRINT -> sprintRepository.findByProjectId(projectId).stream()
                    .anyMatch(sprint -> sprint.getId().equals(targetId));
            case BACKLOG_ITEM -> backlogItemRepository.findByProjectId(projectId).stream()
                    .anyMatch(item -> item.getId().equals(targetId));
        };
        if (!present) {
            throw new InvalidRaidLinkException(
                    "이 프로젝트에 없는 연결 대상입니다: %s #%d".formatted(targetType, targetId));
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** Display names for the three kinds of link target, resolved once per request. */
    private record TargetIndex(Map<Long, WbsNode> wbsNodes,
                                Map<Long, String> sprintNames,
                                Map<Long, String> backlogTitles) {
    }
}
