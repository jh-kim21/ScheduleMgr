package com.projectflow.application;

import com.projectflow.application.dto.ProjectCreateRequest;
import com.projectflow.application.dto.ProjectResponse;
import com.projectflow.application.dto.ProjectUpdateRequest;
import com.projectflow.domain.Project;
import com.projectflow.domain.ProjectNotFoundException;
import com.projectflow.domain.ProjectRepository;
import com.projectflow.domain.ProjectStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class ProjectService {

    private final ProjectRepository projectRepository;

    public ProjectService(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    public List<ProjectResponse> listProjects() {
        return projectRepository.findAll().stream()
                .map(ProjectResponse::from)
                .toList();
    }

    public ProjectResponse getProject(Long id) {
        return ProjectResponse.from(findProjectOrThrow(id));
    }

    @Transactional
    public ProjectResponse createProject(ProjectCreateRequest request) {
        Project project = new Project(
                request.name(),
                request.description(),
                request.status() != null ? request.status() : ProjectStatus.PLANNED,
                request.startDate(),
                request.endDate()
        );
        return ProjectResponse.from(projectRepository.save(project));
    }

    @Transactional
    public ProjectResponse updateProject(Long id, ProjectUpdateRequest request) {
        Project project = findProjectOrThrow(id);
        project.update(
                request.name(),
                request.description(),
                request.status(),
                request.startDate(),
                request.endDate()
        );
        return ProjectResponse.from(projectRepository.save(project));
    }

    @Transactional
    public void deleteProject(Long id) {
        if (!projectRepository.existsById(id)) {
            throw new ProjectNotFoundException(id);
        }
        projectRepository.deleteById(id);
    }

    private Project findProjectOrThrow(Long id) {
        return projectRepository.findById(id)
                .orElseThrow(() -> new ProjectNotFoundException(id));
    }
}
