package com.projectflow.application;

import com.projectflow.application.dto.ProjectMemberCreateRequest;
import com.projectflow.application.dto.ProjectMemberResponse;
import com.projectflow.application.dto.ProjectMemberUpdateRequest;
import com.projectflow.domain.InvalidRaciAssignmentException;
import com.projectflow.domain.ProjectMember;
import com.projectflow.domain.ProjectMemberNotFoundException;
import com.projectflow.domain.ProjectMemberRepository;
import com.projectflow.domain.ProjectNotFoundException;
import com.projectflow.domain.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

/** Project members (요구사항 4.2), the columns of the RACI matrix. */
@Service
@Transactional(readOnly = true)
public class ProjectMemberService {

    /** Creation order, so matrix columns do not reshuffle when a member is renamed. */
    private static final Comparator<ProjectMember> COLUMN_ORDER =
            Comparator.comparing(ProjectMember::getId);

    private final ProjectMemberRepository memberRepository;
    private final ProjectRepository projectRepository;

    public ProjectMemberService(ProjectMemberRepository memberRepository,
                                ProjectRepository projectRepository) {
        this.memberRepository = memberRepository;
        this.projectRepository = projectRepository;
    }

    public List<ProjectMemberResponse> listMembers(Long projectId) {
        requireProject(projectId);
        return memberRepository.findByProjectId(projectId).stream()
                .sorted(COLUMN_ORDER)
                .map(ProjectMemberResponse::from)
                .toList();
    }

    @Transactional
    public ProjectMemberResponse addMember(Long projectId, ProjectMemberCreateRequest request) {
        requireProject(projectId);
        requireNameAvailable(projectId, request.name(), null);

        ProjectMember member = memberRepository.save(new ProjectMember(
                projectId, request.name().trim(), blankToNull(request.email()), blankToNull(request.position())));
        return ProjectMemberResponse.from(member);
    }

    @Transactional
    public ProjectMemberResponse updateMember(Long projectId, Long memberId,
                                               ProjectMemberUpdateRequest request) {
        requireProject(projectId);
        ProjectMember member = requireMemberOfProject(projectId, memberId);
        requireNameAvailable(projectId, request.name(), memberId);

        member.update(request.name().trim(), blankToNull(request.email()), blankToNull(request.position()));
        return ProjectMemberResponse.from(memberRepository.save(member));
    }

    /** RACI assignments go with the member — {@code raci_assignments.member_id} cascades. */
    @Transactional
    public void removeMember(Long projectId, Long memberId) {
        requireProject(projectId);
        memberRepository.delete(requireMemberOfProject(projectId, memberId));
    }

    private void requireProject(Long projectId) {
        if (!projectRepository.existsById(projectId)) {
            throw new ProjectNotFoundException(projectId);
        }
    }

    private ProjectMember requireMemberOfProject(Long projectId, Long memberId) {
        return memberRepository.findByProjectId(projectId).stream()
                .filter(member -> member.getId().equals(memberId))
                .findFirst()
                .orElseThrow(() -> new ProjectMemberNotFoundException(memberId));
    }

    /**
     * Checked here as well as by the unique constraint, so the user gets a sentence instead of a
     * constraint-violation 500. {@code excludedId} lets a member keep its own name on update.
     */
    private void requireNameAvailable(Long projectId, String name, Long excludedId) {
        String candidate = name.trim();
        boolean taken = memberRepository.findByProjectId(projectId).stream()
                .anyMatch(member -> !member.getId().equals(excludedId)
                        && member.getName().equalsIgnoreCase(candidate));
        if (taken) {
            throw new InvalidRaciAssignmentException("이미 등록된 구성원 이름입니다: " + candidate);
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
