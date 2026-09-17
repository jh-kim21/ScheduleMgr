package com.projectflow.application;

import com.projectflow.application.dto.WbsTagRequest;
import com.projectflow.application.dto.WbsTagResponse;
import com.projectflow.domain.InvalidWbsTagException;
import com.projectflow.domain.Project;
import com.projectflow.domain.ProjectRepository;
import com.projectflow.domain.WbsItemTag;
import com.projectflow.domain.WbsItemTagRepository;
import com.projectflow.domain.WbsTag;
import com.projectflow.domain.WbsTagNotFoundException;
import com.projectflow.domain.WbsTagRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The 업무 분야 master list. In-memory fakes rather than mocks, as elsewhere in this package: the
 * rules here are about what ends up stored, which only shows in what was saved.
 */
class WbsTagServiceTest {

    private static final Long PROJECT_ID = 1L;
    private static final Long OTHER_PROJECT_ID = 2L;

    private final AtomicLong ids = new AtomicLong(100);
    private final List<WbsTag> tags = new ArrayList<>();
    private final List<WbsItemTag> itemTags = new ArrayList<>();

    private WbsTagService service;

    @BeforeEach
    void setUp() {
        service = new WbsTagService(tagRepository(), itemTagRepository(), projectRepository());
    }

    @Test
    @DisplayName("추가하면 갱신된 전체 목록을 sortOrder 순으로 돌려준다")
    void addsAndReturnsWholeList() {
        service.createTag(PROJECT_ID, new WbsTagRequest("Service", "#336699", null));
        List<WbsTagResponse> list = service.createTag(PROJECT_ID, new WbsTagRequest("Web", null, null));

        assertThat(list).extracting(WbsTagResponse::name).containsExactly("Service", "Web");
        assertThat(list).extracting(WbsTagResponse::sortOrder).containsExactly(0, 1);
        assertThat(list.get(0).color()).isEqualTo("#336699");
        // 색은 선택이다 — 없으면 화면이 이름 해시로 정한다.
        assertThat(list.get(1).color()).isNull();
    }

    @Test
    @DisplayName("같은 이름은 거부한다 — UNIQUE 위반이 500으로 새어 나가면 안 된다")
    void rejectsDuplicateName() {
        service.createTag(PROJECT_ID, new WbsTagRequest("Service", null, null));

        assertThatThrownBy(() -> service.createTag(PROJECT_ID, new WbsTagRequest(" service ", null, null)))
                .isInstanceOf(InvalidWbsTagException.class)
                .hasMessageContaining("이미 등록된 분야 이름");
        assertThat(tags).hasSize(1);
    }

    @Test
    @DisplayName("다른 프로젝트에는 같은 이름을 쓸 수 있다 — 태그는 프로젝트 스코프다")
    void nameIsUniqueOnlyWithinTheProject() {
        service.createTag(PROJECT_ID, new WbsTagRequest("Service", null, null));
        service.createTag(OTHER_PROJECT_ID, new WbsTagRequest("Service", null, null));

        assertThat(tags).hasSize(2);
    }

    @Test
    @DisplayName("수정할 때 자기 이름은 그대로 둘 수 있다")
    void keepsItsOwnNameOnUpdate() {
        Long id = service.createTag(PROJECT_ID, new WbsTagRequest("Service", null, null))
                .get(0).id();

        List<WbsTagResponse> list = service.updateTag(PROJECT_ID, id,
                new WbsTagRequest("Service", "#000000", 3));

        assertThat(list).singleElement().satisfies(tag -> {
            assertThat(tag.name()).isEqualTo("Service");
            assertThat(tag.color()).isEqualTo("#000000");
            assertThat(tag.sortOrder()).isEqualTo(3);
        });
    }

    @Test
    @DisplayName("삭제하면 연결만 사라진다 — 다른 태그의 연결은 그대로다")
    void deletingDropsOnlyItsOwnLinks() {
        Long doomed = service.createTag(PROJECT_ID, new WbsTagRequest("Service", null, null))
                .get(0).id();
        Long kept = service.createTag(PROJECT_ID, new WbsTagRequest("Web", null, null))
                .stream().filter(tag -> tag.name().equals("Web")).findFirst().orElseThrow().id();
        itemTags.add(new WbsItemTag(500L, doomed));
        itemTags.add(new WbsItemTag(500L, kept));
        itemTags.add(new WbsItemTag(501L, doomed));

        List<WbsTagResponse> list = service.deleteTag(PROJECT_ID, doomed);

        assertThat(list).extracting(WbsTagResponse::name).containsExactly("Web");
        assertThat(itemTags).extracting(WbsItemTag::getTagId).containsExactly(kept);
    }

    @Test
    @DisplayName("다른 프로젝트의 태그는 수정·삭제할 수 없다")
    void refusesTagsOfAnotherProject() {
        Long foreign = service.createTag(OTHER_PROJECT_ID, new WbsTagRequest("Web", null, null))
                .get(0).id();

        assertThatThrownBy(() -> service.updateTag(PROJECT_ID, foreign,
                new WbsTagRequest("Web", null, null)))
                .isInstanceOf(WbsTagNotFoundException.class);
        assertThatThrownBy(() -> service.deleteTag(PROJECT_ID, foreign))
                .isInstanceOf(WbsTagNotFoundException.class);
    }

    private WbsTagRepository tagRepository() {
        return new WbsTagRepository() {
            @Override
            public WbsTag save(WbsTag tag) {
                if (tag.getId() == null) {
                    ReflectionTestUtils.setField(tag, "id", ids.incrementAndGet());
                }
                if (!tags.contains(tag)) {
                    tags.add(tag);
                }
                return tag;
            }

            @Override
            public List<WbsTag> findByProjectId(Long projectId) {
                return tags.stream()
                        .filter(tag -> tag.getProjectId().equals(projectId))
                        .toList();
            }

            @Override
            public void delete(WbsTag tag) {
                tags.remove(tag);
            }
        };
    }

    private WbsItemTagRepository itemTagRepository() {
        return new WbsItemTagRepository() {
            @Override
            public List<WbsItemTag> saveAll(List<WbsItemTag> links) {
                itemTags.addAll(links);
                return links;
            }

            @Override
            public List<WbsItemTag> findByWbsItemIdIn(Collection<Long> wbsItemIds) {
                return itemTags.stream()
                        .filter(link -> wbsItemIds.contains(link.getWbsItemId()))
                        .toList();
            }

            @Override
            public List<WbsItemTag> findByTagId(Long tagId) {
                return itemTags.stream()
                        .filter(link -> link.getTagId().equals(tagId))
                        .toList();
            }

            @Override
            public void deleteAll(List<WbsItemTag> links) {
                itemTags.removeAll(links);
            }
        };
    }

    private ProjectRepository projectRepository() {
        return new ProjectRepository() {
            @Override
            public Project save(Project project) {
                return project;
            }

            @Override
            public Optional<Project> findById(Long id) {
                return Optional.empty();
            }

            @Override
            public List<Project> findAll() {
                return List.of();
            }

            @Override
            public void deleteById(Long id) {
            }

            @Override
            public boolean existsById(Long id) {
                return PROJECT_ID.equals(id) || OTHER_PROJECT_ID.equals(id);
            }
        };
    }

}
