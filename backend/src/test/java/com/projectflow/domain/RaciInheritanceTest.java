package com.projectflow.domain;

import com.projectflow.domain.RaciInheritance.EffectiveRole;
import com.projectflow.domain.RaciInheritance.RoleSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RACI 상속")
class RaciInheritanceTest {

    private static final Long PROJECT_ID = 1L;

    private final List<WbsItem> items = new ArrayList<>();
    private final List<RaciAssignment> assignments = new ArrayList<>();
    private long nextId = 100;

    @Nested
    @DisplayName("상위에서 내려온다")
    class Downwards {

        @Test
        @DisplayName("상위의 A는 하위 Work Package에 적용된다")
        void inheritsAccountable() {
            Long phase = item(null, "설계");
            Long work = item(phase, "화면 설계");
            assign(phase, 10L, RaciRole.ACCOUNTABLE);

            Map<Long, Map<RaciRole, EffectiveRole>> resolved = resolve();

            EffectiveRole inherited = resolved.get(work).get(RaciRole.ACCOUNTABLE);
            assertThat(inherited.memberIds()).containsExactly(10L);
            assertThat(inherited.source()).isEqualTo(RoleSource.INHERITED);
            assertThat(inherited.sourceItemId()).isEqualTo(phase);
            assertThat(inherited.overrides()).isFalse();
        }

        @Test
        @DisplayName("손자까지 내려간다")
        void reachesGrandchildren() {
            Long phase = item(null, "설계");
            Long group = item(phase, "화면");
            Long work = item(group, "목록 화면");
            assign(phase, 10L, RaciRole.ACCOUNTABLE);

            assertThat(RaciInheritance.holders(resolve(), work, RaciRole.ACCOUNTABLE))
                    .containsExactly(10L);
        }

        @Test
        @DisplayName("역할별로 따로 상속한다 — 자기 R이 있어도 상위 A는 그대로 온다")
        void inheritsPerRole() {
            Long phase = item(null, "설계");
            Long work = item(phase, "화면 설계");
            assign(phase, 10L, RaciRole.ACCOUNTABLE);
            assign(work, 20L, RaciRole.RESPONSIBLE);

            Map<RaciRole, EffectiveRole> effective = resolve().get(work);

            assertThat(effective.get(RaciRole.ACCOUNTABLE).memberIds()).containsExactly(10L);
            assertThat(effective.get(RaciRole.RESPONSIBLE).memberIds()).containsExactly(20L);
            assertThat(effective.get(RaciRole.RESPONSIBLE).source()).isEqualTo(RoleSource.OWN);
        }
    }

    @Nested
    @DisplayName("하위가 다시 정할 수 있다")
    class Override {

        @Test
        @DisplayName("가장 가까운 상위가 이긴다")
        void nearestWins() {
            Long phase = item(null, "설계");
            Long group = item(phase, "화면");
            Long work = item(group, "목록 화면");
            assign(phase, 10L, RaciRole.ACCOUNTABLE);
            assign(group, 20L, RaciRole.ACCOUNTABLE);

            assertThat(RaciInheritance.holders(resolve(), work, RaciRole.ACCOUNTABLE))
                    .containsExactly(20L);
        }

        @Test
        @DisplayName("자기 배정이 있으면 재정의로 표시한다")
        void marksOverride() {
            Long phase = item(null, "설계");
            Long work = item(phase, "화면 설계");
            assign(phase, 10L, RaciRole.ACCOUNTABLE);
            assign(work, 20L, RaciRole.ACCOUNTABLE);

            EffectiveRole own = resolve().get(work).get(RaciRole.ACCOUNTABLE);

            assertThat(own.source()).isEqualTo(RoleSource.OWN);
            assertThat(own.overrides()).isTrue();
            assertThat(own.memberIds()).containsExactly(20L);
        }

        @Test
        @DisplayName("상위가 없는 자기 배정은 재정의가 아니다")
        void plainAssignmentIsNotAnOverride() {
            Long phase = item(null, "설계");
            Long work = item(phase, "화면 설계");
            assign(work, 20L, RaciRole.ACCOUNTABLE);

            assertThat(resolve().get(work).get(RaciRole.ACCOUNTABLE).overrides()).isFalse();
        }
    }

    @Test
    @DisplayName("아무도 없는 역할은 아예 없다 — '아무도 없음'과 '빈 목록'을 구분하지 않는다")
    void absentRoleIsAbsent() {
        Long work = item(null, "화면 설계");

        assertThat(resolve().get(work)).doesNotContainKey(RaciRole.CONSULTED);
        assertThat(RaciInheritance.holders(resolve(), work, RaciRole.CONSULTED)).isEmpty();
    }

    // ------------------------------------------------------------------ 도우미

    private Map<Long, Map<RaciRole, EffectiveRole>> resolve() {
        return RaciInheritance.resolve(WbsTreeAssembler.assemble(items), assignments);
    }

    private Long item(Long parentId, String name) {
        WbsItem item = new WbsItem(PROJECT_ID, parentId, name, null, null, null, 0, items.size());
        Long id = nextId++;
        ReflectionTestUtils.setField(item, "id", id);
        items.add(item);
        return id;
    }

    private void assign(Long wbsItemId, Long memberId, RaciRole role) {
        RaciAssignment assignment = new RaciAssignment(PROJECT_ID, wbsItemId, memberId, role);
        ReflectionTestUtils.setField(assignment, "id", nextId++);
        assignments.add(assignment);
    }
}
