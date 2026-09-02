package com.projectflow.application;

import com.projectflow.application.dto.ProjectExportResponse;
import com.projectflow.application.dto.ProjectExportResponse.ExportedDependency;
import com.projectflow.application.dto.ProjectExportResponse.ExportedMember;
import com.projectflow.application.dto.ProjectExportResponse.ExportedProject;
import com.projectflow.application.dto.ProjectExportResponse.ExportedRaciAssignment;
import com.projectflow.application.dto.ProjectExportResponse.ExportedRaidItem;
import com.projectflow.application.dto.ProjectExportResponse.ExportedWbsItem;
import com.projectflow.domain.InvalidImportException;
import com.projectflow.domain.Project;
import com.projectflow.domain.ProjectMember;
import com.projectflow.domain.ProjectMemberRepository;
import com.projectflow.domain.ProjectRepository;
import com.projectflow.domain.ProjectStatus;
import com.projectflow.domain.RaciAssignment;
import com.projectflow.domain.RaciAssignmentRepository;
import com.projectflow.domain.RaciRole;
import com.projectflow.domain.RaidItem;
import com.projectflow.domain.RaidItemRepository;
import com.projectflow.domain.RaidStatus;
import com.projectflow.domain.RaidType;
import com.projectflow.domain.WbsDependency;
import com.projectflow.domain.WbsDependencyRepository;
import com.projectflow.domain.WbsItem;
import com.projectflow.domain.WbsItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * In-memory fakes rather than mocks: the id remapping is the whole point of import, and it can
 * only be checked by letting saves hand back generated ids and inspecting what was stored.
 */
class ImportServiceTest {

    private final AtomicLong ids = new AtomicLong(100);

    private final List<Project> projects = new ArrayList<>();
    private final List<ProjectMember> members = new ArrayList<>();
    private final List<WbsItem> wbsItems = new ArrayList<>();
    private final List<WbsDependency> dependencies = new ArrayList<>();
    private final List<RaciAssignment> raciAssignments = new ArrayList<>();
    private final List<RaidItem> raidItems = new ArrayList<>();

    private ImportService service;

    @BeforeEach
    void setUp() {
        service = new ImportService(
                projectRepository(), memberRepository(), wbsItemRepository(),
                dependencyRepository(), raciAssignmentRepository(), raidItemRepository());
    }

    @Nested
    @DisplayName("정상 가져오기")
    class HappyPath {

        @Test
        @DisplayName("파일의 id를 새 id로 다시 매핑하고 참조를 이어 붙인다")
        void remapsIds() {
            service.importProject(file(
                    List.of(member(7L, "김재학")),
                    List.of(
                            wbs(1L, null, "설계"),
                            wbs(2L, 1L, "화면 설계"),
                            wbs(3L, 1L, "DB 설계")
                    ),
                    List.of(new ExportedDependency(50L, 2L, 3L, 2)),
                    List.of(new ExportedRaciAssignment(60L, 2L, 7L, RaciRole.ACCOUNTABLE)),
                    List.of(raid(70L, "위험 하나", 7L, 2L))
            ));

            assertThat(projects).hasSize(1);
            Long projectId = projects.getFirst().getId();

            // 파일의 id(1,2,3,7,...)는 어디에도 남아 있지 않아야 한다.
            assertThat(wbsItems).allSatisfy(item ->
                    assertThat(item.getProjectId()).isEqualTo(projectId));
            assertThat(wbsItems).extracting(WbsItem::getId).doesNotContain(1L, 2L, 3L);

            WbsItem design = byName("설계");
            WbsItem screen = byName("화면 설계");
            WbsItem db = byName("DB 설계");

            assertThat(design.getParentId()).isNull();
            assertThat(screen.getParentId()).isEqualTo(design.getId());
            assertThat(db.getParentId()).isEqualTo(design.getId());

            assertThat(dependencies).singleElement().satisfies(dependency -> {
                assertThat(dependency.getPredecessorId()).isEqualTo(screen.getId());
                assertThat(dependency.getSuccessorId()).isEqualTo(db.getId());
                assertThat(dependency.getLagDays()).isEqualTo(2);
            });

            assertThat(raciAssignments).singleElement().satisfies(assignment -> {
                assertThat(assignment.getWbsItemId()).isEqualTo(screen.getId());
                assertThat(assignment.getMemberId()).isEqualTo(members.getFirst().getId());
            });

            assertThat(raidItems).singleElement().satisfies(item -> {
                assertThat(item.getOwnerMemberId()).isEqualTo(members.getFirst().getId());
                assertThat(item.getWbsItemId()).isEqualTo(screen.getId());
            });
        }

        @Test
        @DisplayName("파일 순서가 자식 먼저여도 상위부터 넣는다")
        void insertsParentsFirstRegardlessOfFileOrder() {
            service.importProject(file(
                    List.of(),
                    // 손으로 편집한 파일은 트리 순서가 아닐 수 있다.
                    List.of(wbs(3L, 2L, "손자"), wbs(2L, 1L, "자식"), wbs(1L, null, "부모")),
                    List.of(), List.of(), List.of()
            ));

            assertThat(byName("부모").getParentId()).isNull();
            assertThat(byName("자식").getParentId()).isEqualTo(byName("부모").getId());
            assertThat(byName("손자").getParentId()).isEqualTo(byName("자식").getId());
        }

        @Test
        @DisplayName("빈 절이 있어도(null) 가져온다")
        void toleratesMissingSections() {
            ProjectExportResponse bare = new ProjectExportResponse(
                    1, LocalDateTime.now(), project("맨몸 프로젝트"), null, null, null, null, null);

            service.importProject(bare);

            assertThat(projects).hasSize(1);
            assertThat(wbsItems).isEmpty();
        }

        @Test
        @DisplayName("이름이 겹치면 접미사를 붙여 목록에서 구분되게 한다")
        void avoidsNameCollision() {
            projects.add(existingProject("AEGIS"));

            service.importProject(file(List.of(), List.of(), List.of(), List.of(), List.of()));

            assertThat(projects).extracting(Project::getName)
                    .containsExactly("AEGIS", "AEGIS (가져옴)");

            service.importProject(file(List.of(), List.of(), List.of(), List.of(), List.of()));
            assertThat(projects).extracting(Project::getName)
                    .containsExactly("AEGIS", "AEGIS (가져옴)", "AEGIS (가져옴 2)");
        }
    }

    @Nested
    @DisplayName("거부하는 파일")
    class Rejected {

        @Test
        @DisplayName("지원하지 않는 형식 버전")
        void newerFormat() {
            ProjectExportResponse future = new ProjectExportResponse(
                    99, LocalDateTime.now(), project("미래"), List.of(), List.of(), List.of(),
                    List.of(), List.of());

            assertThatThrownBy(() -> service.importProject(future))
                    .isInstanceOf(InvalidImportException.class)
                    .hasMessageContaining("읽을 수 없는 형식");
        }

        @Test
        @DisplayName("파일에 없는 상위 항목을 가리키는 WBS")
        void danglingParent() {
            assertThatThrownBy(() -> service.importProject(file(
                    List.of(), List.of(wbs(2L, 999L, "고아")), List.of(), List.of(), List.of())))
                    .isInstanceOf(InvalidImportException.class)
                    .hasMessageContaining("상위 항목");
        }

        @Test
        @DisplayName("순환하는 상위 참조")
        void parentCycle() {
            assertThatThrownBy(() -> service.importProject(file(
                    List.of(), List.of(wbs(1L, 2L, "가"), wbs(2L, 1L, "나")),
                    List.of(), List.of(), List.of())))
                    .isInstanceOf(InvalidImportException.class)
                    .hasMessageContaining("순환");
        }

        @Test
        @DisplayName("id 중복")
        void duplicateIds() {
            assertThatThrownBy(() -> service.importProject(file(
                    List.of(), List.of(wbs(1L, null, "가"), wbs(1L, null, "나")),
                    List.of(), List.of(), List.of())))
                    .isInstanceOf(InvalidImportException.class)
                    .hasMessageContaining("중복");
        }

        @Test
        @DisplayName("선행과 후행이 같은 선후행 관계 — DB 제약에 걸리기 전에 잡는다")
        void selfDependency() {
            assertThatThrownBy(() -> service.importProject(file(
                    List.of(), List.of(wbs(1L, null, "가")),
                    List.of(new ExportedDependency(9L, 1L, 1L, 0)), List.of(), List.of())))
                    .isInstanceOf(InvalidImportException.class)
                    .hasMessageContaining("선행과 후행이 같은");
        }

        @Test
        @DisplayName("중복된 선후행 관계 — UNIQUE 제약에 걸리기 전에 잡는다")
        void duplicateDependency() {
            assertThatThrownBy(() -> service.importProject(file(
                    List.of(), List.of(wbs(1L, null, "가"), wbs(2L, null, "나")),
                    List.of(new ExportedDependency(9L, 1L, 2L, 0), new ExportedDependency(10L, 1L, 2L, 3)),
                    List.of(), List.of())))
                    .isInstanceOf(InvalidImportException.class)
                    .hasMessageContaining("두 번");
        }

        @Test
        @DisplayName("중복된 RACI 배정 — UNIQUE 제약에 걸리기 전에 잡는다")
        void duplicateRaci() {
            assertThatThrownBy(() -> service.importProject(file(
                    List.of(member(7L, "김")), List.of(wbs(1L, null, "가")), List.of(),
                    List.of(
                            new ExportedRaciAssignment(1L, 1L, 7L, RaciRole.RESPONSIBLE),
                            new ExportedRaciAssignment(2L, 1L, 7L, RaciRole.RESPONSIBLE)),
                    List.of())))
                    .isInstanceOf(InvalidImportException.class)
                    .hasMessageContaining("두 번");
        }

        @Test
        @DisplayName("파일에 없는 구성원을 가리키는 RAID 소유자")
        void danglingRaidOwner() {
            assertThatThrownBy(() -> service.importProject(file(
                    List.of(), List.of(), List.of(), List.of(),
                    List.of(raid(1L, "위험", 999L, null)))))
                    .isInstanceOf(InvalidImportException.class)
                    .hasMessageContaining("소유자");
        }

        @Test
        @DisplayName("거부된 파일은 아무것도 남기지 않는다")
        void rejectionLeavesNothing() {
            assertThatThrownBy(() -> service.importProject(file(
                    List.of(), List.of(wbs(2L, 999L, "고아")), List.of(), List.of(), List.of())))
                    .isInstanceOf(InvalidImportException.class);

            // 검증이 삽입보다 앞에 있으므로 트랜잭션 롤백에 기대지 않아도 비어 있다.
            assertThat(projects).isEmpty();
            assertThat(wbsItems).isEmpty();
        }
    }

    // ------------------------------------------------------------- 파일 조립

    private ProjectExportResponse file(List<ExportedMember> members,
                                        List<ExportedWbsItem> wbs,
                                        List<ExportedDependency> deps,
                                        List<ExportedRaciAssignment> raci,
                                        List<ExportedRaidItem> raid) {
        return new ProjectExportResponse(
                1, LocalDateTime.now(), project("AEGIS"), members, wbs, deps, raci, raid);
    }

    private ExportedProject project(String name) {
        return new ExportedProject(1L, name, "설명", ProjectStatus.IN_PROGRESS,
                LocalDate.parse("2026-09-01"), LocalDate.parse("2026-10-31"), null, null);
    }

    private ExportedMember member(Long id, String name) {
        return new ExportedMember(id, name, null, "PM");
    }

    private ExportedWbsItem wbs(Long id, Long parentId, String name) {
        return new ExportedWbsItem(id, parentId, "무시됨", name, null, null, null, 0, 0);
    }

    private ExportedRaidItem raid(Long id, String title, Long ownerId, Long wbsItemId) {
        return new ExportedRaidItem(id, RaidType.RISK, title, null, RaidStatus.OPEN,
                null, null, ownerId, wbsItemId, null, null);
    }

    private WbsItem byName(String name) {
        return wbsItems.stream()
                .filter(item -> item.getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("WBS 항목이 없습니다: " + name));
    }

    private Project existingProject(String name) {
        Project project = new Project(name, null, ProjectStatus.PLANNED, null, null);
        ReflectionTestUtils.setField(project, "id", ids.incrementAndGet());
        return project;
    }

    // ------------------------------------------------------------- 인메모리 저장소

    private <T> T withId(T entity) {
        ReflectionTestUtils.setField(entity, "id", ids.incrementAndGet());
        return entity;
    }

    private ProjectRepository projectRepository() {
        return new ProjectRepository() {
            @Override
            public Project save(Project project) {
                projects.add(withId(project));
                return project;
            }

            @Override
            public Optional<Project> findById(Long id) {
                return projects.stream().filter(p -> p.getId().equals(id)).findFirst();
            }

            @Override
            public List<Project> findAll() {
                return List.copyOf(projects);
            }

            @Override
            public void deleteById(Long id) {
                projects.removeIf(p -> p.getId().equals(id));
            }

            @Override
            public boolean existsById(Long id) {
                return findById(id).isPresent();
            }
        };
    }

    private ProjectMemberRepository memberRepository() {
        return new ProjectMemberRepository() {
            @Override
            public ProjectMember save(ProjectMember member) {
                members.add(withId(member));
                return member;
            }

            @Override
            public Optional<ProjectMember> findById(Long id) {
                return members.stream().filter(m -> m.getId().equals(id)).findFirst();
            }

            @Override
            public List<ProjectMember> findByProjectId(Long projectId) {
                return members.stream().filter(m -> m.getProjectId().equals(projectId)).toList();
            }

            @Override
            public void delete(ProjectMember member) {
                members.remove(member);
            }
        };
    }

    private WbsItemRepository wbsItemRepository() {
        return new WbsItemRepository() {
            @Override
            public WbsItem save(WbsItem item) {
                wbsItems.add(withId(item));
                return item;
            }

            @Override
            public List<WbsItem> saveAll(List<WbsItem> items) {
                items.forEach(this::save);
                return items;
            }

            @Override
            public Optional<WbsItem> findById(Long id) {
                return wbsItems.stream().filter(i -> i.getId().equals(id)).findFirst();
            }

            @Override
            public List<WbsItem> findByProjectId(Long projectId) {
                return wbsItems.stream().filter(i -> i.getProjectId().equals(projectId)).toList();
            }

            @Override
            public void delete(WbsItem item) {
                wbsItems.remove(item);
            }
        };
    }

    private WbsDependencyRepository dependencyRepository() {
        return new WbsDependencyRepository() {
            @Override
            public WbsDependency save(WbsDependency dependency) {
                dependencies.add(withId(dependency));
                return dependency;
            }

            @Override
            public Optional<WbsDependency> findById(Long id) {
                return dependencies.stream().filter(d -> d.getId().equals(id)).findFirst();
            }

            @Override
            public List<WbsDependency> findByProjectId(Long projectId) {
                return dependencies.stream().filter(d -> d.getProjectId().equals(projectId)).toList();
            }

            @Override
            public void delete(WbsDependency dependency) {
                dependencies.remove(dependency);
            }
        };
    }

    private RaciAssignmentRepository raciAssignmentRepository() {
        return new RaciAssignmentRepository() {
            @Override
            public RaciAssignment save(RaciAssignment assignment) {
                raciAssignments.add(withId(assignment));
                return assignment;
            }

            @Override
            public Optional<RaciAssignment> findById(Long id) {
                return raciAssignments.stream().filter(a -> a.getId().equals(id)).findFirst();
            }

            @Override
            public List<RaciAssignment> findByProjectId(Long projectId) {
                return raciAssignments.stream().filter(a -> a.getProjectId().equals(projectId)).toList();
            }

            @Override
            public void delete(RaciAssignment assignment) {
                raciAssignments.remove(assignment);
            }
        };
    }

    private RaidItemRepository raidItemRepository() {
        return new RaidItemRepository() {
            @Override
            public RaidItem save(RaidItem item) {
                raidItems.add(withId(item));
                return item;
            }

            @Override
            public Optional<RaidItem> findById(Long id) {
                return raidItems.stream().filter(i -> i.getId().equals(id)).findFirst();
            }

            @Override
            public List<RaidItem> findByProjectId(Long projectId) {
                return raidItems.stream().filter(i -> i.getProjectId().equals(projectId)).toList();
            }

            @Override
            public void delete(RaidItem item) {
                raidItems.remove(item);
            }
        };
    }
}
