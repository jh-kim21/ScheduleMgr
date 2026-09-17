package com.projectflow.application;

import com.projectflow.application.dto.WbsTagRequest;
import com.projectflow.application.dto.WbsTagResponse;
import com.projectflow.domain.InvalidWbsTagException;
import com.projectflow.domain.ProjectNotFoundException;
import com.projectflow.domain.ProjectRepository;
import com.projectflow.domain.WbsItemTag;
import com.projectflow.domain.WbsItemTagRepository;
import com.projectflow.domain.WbsTag;
import com.projectflow.domain.WbsTagNotFoundException;
import com.projectflow.domain.WbsTagRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

/**
 * The project's 업무 분야 master list (지시서 D-2).
 *
 * <p>Attaching a tag to a WBS entry is <em>not</em> here: it rides along on
 * {@code PUT /wbs/{itemId}} so saving the edit form is one request, and {@code WbsService} owns it.
 * This service only maintains the list the form picks from.
 *
 * <p>Every mutation answers with the whole list, like RACI and RAID: a partial response would make
 * the client merge rows itself, and reordering one tag moves others.
 */
@Service
@Transactional(readOnly = true)
public class WbsTagService {

    /** What the chips and the picker are ordered by; id breaks ties so the order never wobbles. */
    private static final Comparator<WbsTag> LIST_ORDER =
            Comparator.comparingInt(WbsTag::getSortOrder).thenComparing(WbsTag::getId);

    private final WbsTagRepository tagRepository;
    private final WbsItemTagRepository itemTagRepository;
    private final ProjectRepository projectRepository;

    public WbsTagService(WbsTagRepository tagRepository,
                          WbsItemTagRepository itemTagRepository,
                          ProjectRepository projectRepository) {
        this.tagRepository = tagRepository;
        this.itemTagRepository = itemTagRepository;
        this.projectRepository = projectRepository;
    }

    public List<WbsTagResponse> listTags(Long projectId) {
        requireProject(projectId);
        return sorted(projectId);
    }

    @Transactional
    public List<WbsTagResponse> createTag(Long projectId, WbsTagRequest request) {
        requireProject(projectId);
        List<WbsTag> existing = tagRepository.findByProjectId(projectId);
        requireNameAvailable(existing, request.name(), null);

        int sortOrder = request.sortOrder() != null
                ? request.sortOrder()
                : existing.stream().mapToInt(WbsTag::getSortOrder).max().orElse(-1) + 1;
        tagRepository.save(new WbsTag(
                projectId, request.name().trim(), blankToNull(request.color()), sortOrder));
        return sorted(projectId);
    }

    @Transactional
    public List<WbsTagResponse> updateTag(Long projectId, Long tagId, WbsTagRequest request) {
        requireProject(projectId);
        List<WbsTag> existing = tagRepository.findByProjectId(projectId);
        WbsTag tag = requireTagOfProject(existing, tagId);
        requireNameAvailable(existing, request.name(), tagId);

        tag.update(request.name().trim(), blankToNull(request.color()),
                request.sortOrder() != null ? request.sortOrder() : tag.getSortOrder());
        tagRepository.save(tag);
        return sorted(projectId);
    }

    /**
     * Deletes the tag. Its links go with it and nothing else does — a WBS entry that carried the
     * tag stays exactly as it was, minus one chip.
     *
     * <p>The links are removed here rather than being left to {@code wbs_item_tags}' cascade: the
     * database is the last line of defence, not the mechanism, and stating it in code is what makes
     * the rule testable.
     */
    @Transactional
    public List<WbsTagResponse> deleteTag(Long projectId, Long tagId) {
        requireProject(projectId);
        WbsTag tag = requireTagOfProject(tagRepository.findByProjectId(projectId), tagId);

        List<WbsItemTag> links = itemTagRepository.findByTagId(tagId);
        if (!links.isEmpty()) {
            itemTagRepository.deleteAll(links);
        }
        tagRepository.delete(tag);
        return sorted(projectId);
    }

    private List<WbsTagResponse> sorted(Long projectId) {
        return tagRepository.findByProjectId(projectId).stream()
                .sorted(LIST_ORDER)
                .map(WbsTagResponse::from)
                .toList();
    }

    private void requireProject(Long projectId) {
        if (!projectRepository.existsById(projectId)) {
            throw new ProjectNotFoundException(projectId);
        }
    }

    private WbsTag requireTagOfProject(List<WbsTag> projectTags, Long tagId) {
        return projectTags.stream()
                .filter(tag -> tag.getId().equals(tagId))
                .findFirst()
                .orElseThrow(() -> new WbsTagNotFoundException(tagId));
    }

    /**
     * Checked here as well as by the unique constraint, so a repeated name reads as a sentence
     * instead of a constraint-violation 500 (the same reason {@code ProjectMemberService} does it).
     * {@code excludedId} lets a tag keep its own name while being edited.
     */
    private void requireNameAvailable(List<WbsTag> projectTags, String name, Long excludedId) {
        String candidate = name.trim();
        boolean taken = projectTags.stream()
                .anyMatch(tag -> !tag.getId().equals(excludedId)
                        && tag.getName().equalsIgnoreCase(candidate));
        if (taken) {
            throw new InvalidWbsTagException("이미 등록된 분야 이름입니다: " + candidate);
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
