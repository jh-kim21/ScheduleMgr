package com.projectflow.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DependencyGraphTest {

    private static final Long PROJECT_ID = 1L;

    @Test
    @DisplayName("직접 연결된 후행에 도달한다")
    void reachesDirectSuccessor() {
        DependencyGraph graph = DependencyGraph.of(List.of(dependency(1L, 2L)));

        assertThat(graph.reaches(1L, 2L)).isTrue();
    }

    @Test
    @DisplayName("여러 단계를 거친 후행에도 도달한다")
    void reachesTransitiveSuccessor() {
        DependencyGraph graph = DependencyGraph.of(
                List.of(dependency(1L, 2L), dependency(2L, 3L), dependency(3L, 4L)));

        assertThat(graph.reaches(1L, 4L)).isTrue();
    }

    @Test
    @DisplayName("반대 방향으로는 도달하지 않는다")
    void doesNotReachBackwards() {
        DependencyGraph graph = DependencyGraph.of(List.of(dependency(1L, 2L), dependency(2L, 3L)));

        assertThat(graph.reaches(3L, 1L)).isFalse();
    }

    @Test
    @DisplayName("연결되지 않은 항목에는 도달하지 않는다")
    void doesNotReachUnrelated() {
        DependencyGraph graph = DependencyGraph.of(List.of(dependency(1L, 2L), dependency(3L, 4L)));

        assertThat(graph.reaches(1L, 4L)).isFalse();
    }

    @Test
    @DisplayName("A→B가 있을 때 B→A 추가 여부를 판정할 수 있다")
    void detectsThatReverseEdgeWouldCloseCycle() {
        // GanttService는 successor에서 predecessor로 도달 가능한지 확인해 순환을 막는다.
        DependencyGraph graph = DependencyGraph.of(List.of(dependency(1L, 2L), dependency(2L, 3L)));

        // 3 -> 1 을 추가하면 순환이 된다: 1이 3에서 도달 가능한지가 아니라, 1에서 3이 도달 가능한지로 판정
        assertThat(graph.reaches(1L, 3L)).isTrue();
    }

    @Test
    @DisplayName("빈 그래프에서는 자기 자신에만 도달한다")
    void emptyGraphReachesOnlySelf() {
        DependencyGraph graph = DependencyGraph.of(List.of());

        assertThat(graph.reaches(1L, 1L)).isTrue();
        assertThat(graph.reaches(1L, 2L)).isFalse();
    }

    private static WbsDependency dependency(Long predecessorId, Long successorId) {
        return new WbsDependency(PROJECT_ID, predecessorId, successorId, 0);
    }
}
