package com.projectflow.presentation;

import com.projectflow.application.ProjectMemberService;
import com.projectflow.application.dto.ProjectMemberCreateRequest;
import com.projectflow.application.dto.ProjectMemberResponse;
import com.projectflow.application.dto.ProjectMemberUpdateRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Project members (요구사항 4.2). Unlike the WBS and RACI endpoints these return just the member:
 * adding a person changes nothing about the others.
 */
@RestController
@RequestMapping("/api/projects/{projectId}/members")
public class ProjectMemberController {

    private final ProjectMemberService memberService;

    public ProjectMemberController(ProjectMemberService memberService) {
        this.memberService = memberService;
    }

    @GetMapping
    public List<ProjectMemberResponse> listMembers(@PathVariable Long projectId) {
        return memberService.listMembers(projectId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectMemberResponse addMember(@PathVariable Long projectId,
                                            @Valid @RequestBody ProjectMemberCreateRequest request) {
        return memberService.addMember(projectId, request);
    }

    @PutMapping("/{memberId}")
    public ProjectMemberResponse updateMember(@PathVariable Long projectId,
                                               @PathVariable Long memberId,
                                               @Valid @RequestBody ProjectMemberUpdateRequest request) {
        return memberService.updateMember(projectId, memberId, request);
    }

    @DeleteMapping("/{memberId}")
    public ResponseEntity<Void> removeMember(@PathVariable Long projectId, @PathVariable Long memberId) {
        memberService.removeMember(projectId, memberId);
        return ResponseEntity.noContent().build();
    }
}
