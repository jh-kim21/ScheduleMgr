package com.projectflow.application;

import com.projectflow.domain.BacklogItem;
import com.projectflow.domain.BacklogItemRepository;
import com.projectflow.domain.ProjectMember;
import com.projectflow.domain.ProjectMemberRepository;
import com.projectflow.domain.ProjectRepository;
import com.projectflow.domain.RaidItem;
import com.projectflow.domain.RaidItemRepository;
import com.projectflow.domain.RaidLink;
import com.projectflow.domain.RaidLinkRepository;
import com.projectflow.domain.Sprint;
import com.projectflow.domain.SprintRepository;
import com.projectflow.domain.WbsItem;
import com.projectflow.domain.WbsItemRepository;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A {@link RaidService} for tests that only need it as a collaborator.
 *
 * <p>The WBS, Backlog and Sprint services call exactly one method on it — {@code detachTargets},
 * which drops the RAID links pointing at something being deleted — and that method touches only the
 * link store. Everything else is an empty in-memory stub, so a test of WBS deletion does not have
 * to hand-roll seven repositories to say "there are no RAID entries".
 *
 * <p>{@link #links} is exposed so a test can seed a link and assert it was detached.
 */
final class TestRaidService {

    private final AtomicLong ids = new AtomicLong(9000);
    final List<RaidLink> links = new ArrayList<>();

    private TestRaidService() {
    }

    static TestRaidService create() {
        return new TestRaidService();
    }

    RaidService service() {
        return new RaidService(raidItems(), raidLinks(), members(), wbsItems(), sprints(),
                backlogItems(), projects());
    }

    private RaidLinkRepository raidLinks() {
        return new RaidLinkRepository() {
            @Override
            public RaidLink save(RaidLink link) {
                if (link.getId() == null) {
                    ReflectionTestUtils.setField(link, "id", ids.incrementAndGet());
                }
                links.removeIf(existing -> existing.getId().equals(link.getId()));
                links.add(link);
                return link;
            }

            @Override
            public List<RaidLink> saveAll(List<RaidLink> batch) {
                batch.forEach(this::save);
                return batch;
            }

            @Override
            public List<RaidLink> findByProjectId(Long projectId) {
                return links.stream().filter(link -> link.getProjectId().equals(projectId)).toList();
            }

            @Override
            public void deleteAll(List<RaidLink> removed) {
                links.removeAll(removed);
            }
        };
    }

    private RaidItemRepository raidItems() {
        return new RaidItemRepository() {
            @Override
            public RaidItem save(RaidItem item) {
                return item;
            }

            @Override
            public Optional<RaidItem> findById(Long id) {
                return Optional.empty();
            }

            @Override
            public List<RaidItem> findByProjectId(Long projectId) {
                return List.of();
            }

            @Override
            public void delete(RaidItem item) {
            }
        };
    }

    private SprintRepository sprints() {
        return new SprintRepository() {
            @Override
            public Sprint save(Sprint sprint) {
                return sprint;
            }

            @Override
            public Optional<Sprint> findById(Long id) {
                return Optional.empty();
            }

            @Override
            public List<Sprint> findByProjectId(Long projectId) {
                return List.of();
            }

            @Override
            public void delete(Sprint sprint) {
            }
        };
    }

    private BacklogItemRepository backlogItems() {
        return new BacklogItemRepository() {
            @Override
            public BacklogItem save(BacklogItem item) {
                return item;
            }

            @Override
            public List<BacklogItem> saveAll(List<BacklogItem> batch) {
                return batch;
            }

            @Override
            public Optional<BacklogItem> findById(Long id) {
                return Optional.empty();
            }

            @Override
            public List<BacklogItem> findByProjectId(Long projectId) {
                return List.of();
            }

            @Override
            public void delete(BacklogItem item) {
            }
        };
    }

    private WbsItemRepository wbsItems() {
        return new WbsItemRepository() {
            @Override
            public WbsItem save(WbsItem item) {
                return item;
            }

            @Override
            public List<WbsItem> saveAll(List<WbsItem> batch) {
                return batch;
            }

            @Override
            public Optional<WbsItem> findById(Long id) {
                return Optional.empty();
            }

            @Override
            public List<WbsItem> findByProjectId(Long projectId) {
                return List.of();
            }

            @Override
            public void delete(WbsItem item) {
            }
        };
    }

    private ProjectMemberRepository members() {
        return new ProjectMemberRepository() {
            @Override
            public ProjectMember save(ProjectMember member) {
                return member;
            }

            @Override
            public Optional<ProjectMember> findById(Long id) {
                return Optional.empty();
            }

            @Override
            public List<ProjectMember> findByProjectId(Long projectId) {
                return List.of();
            }

            @Override
            public void delete(ProjectMember member) {
            }
        };
    }

    private ProjectRepository projects() {
        return new ProjectRepository() {
            @Override
            public com.projectflow.domain.Project save(com.projectflow.domain.Project project) {
                return project;
            }

            @Override
            public Optional<com.projectflow.domain.Project> findById(Long id) {
                return Optional.empty();
            }

            @Override
            public List<com.projectflow.domain.Project> findAll() {
                return List.of();
            }

            @Override
            public boolean existsById(Long id) {
                return true;
            }

            @Override
            public void deleteById(Long id) {
            }
        };
    }
}
