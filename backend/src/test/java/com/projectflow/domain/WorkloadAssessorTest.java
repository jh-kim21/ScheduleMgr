package com.projectflow.domain;

import com.projectflow.domain.WorkloadAssessor.MemberLoad;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WorkloadAssessorTest {

    @Nested
    @DisplayName("정렬")
    class Rank {

        @Test
        @DisplayName("activeCount 내림차순으로 정렬한다")
        void sortsByActiveCountDescending() {
            MemberLoad low = load(1L, "김철수", 2);
            MemberLoad high = load(2L, "이영희", 5);
            MemberLoad mid = load(3L, "박민수", 3);

            List<MemberLoad> ranked = WorkloadAssessor.rank(List.of(low, high, mid));

            assertThat(ranked).extracting(MemberLoad::memberName)
                    .containsExactly("이영희", "박민수", "김철수");
        }

        @Test
        @DisplayName("activeCount가 같으면 이름 순으로 묶는다")
        void tiesBreakByName() {
            MemberLoad b = load(1L, "나철수", 3);
            MemberLoad a = load(2L, "가영희", 3);

            List<MemberLoad> ranked = WorkloadAssessor.rank(List.of(b, a));

            assertThat(ranked).extracting(MemberLoad::memberName)
                    .containsExactly("가영희", "나철수");
        }

        @Test
        @DisplayName("부하가 0인 사람도 걸러내지 않고 남긴다")
        void keepsZeroLoadMembers() {
            MemberLoad zero = load(1L, "김철수", 0);
            MemberLoad five = load(2L, "이영희", 5);

            List<MemberLoad> ranked = WorkloadAssessor.rank(List.of(five, zero));

            assertThat(ranked).hasSize(2);
            assertThat(ranked.get(1).memberName()).isEqualTo("김철수");
            assertThat(ranked.get(1).activeCount()).isZero();
        }
    }

    @Nested
    @DisplayName("막대의 분모")
    class MaxActive {

        @Test
        @DisplayName("최댓값을 돌려준다")
        void returnsTheMaximum() {
            List<MemberLoad> loads = List.of(load(1L, "김철수", 2), load(2L, "이영희", 7));

            assertThat(WorkloadAssessor.maxActive(loads)).isEqualTo(7);
        }

        @Test
        @DisplayName("아무도 활성 업무가 없으면 0이다 — 막대를 그리지 않는 신호")
        void zeroWhenNobodyIsActive() {
            List<MemberLoad> loads = List.of(load(1L, "김철수", 0), load(2L, "이영희", 0));

            assertThat(WorkloadAssessor.maxActive(loads)).isZero();
        }

        @Test
        @DisplayName("구성원이 없어도 0이다")
        void zeroWhenEmpty() {
            assertThat(WorkloadAssessor.maxActive(List.of())).isZero();
        }
    }

    @Nested
    @DisplayName("storyPoints의 null/0 구분")
    class StoryPointsNullVsZero {

        @Test
        @DisplayName("담당 Story가 없으면 null, 실제로 0점이면 0 — rank()가 서로 뒤섞지 않는다")
        void nullAndZeroStayDistinct() {
            MemberLoad noStories = new MemberLoad(1L, "김철수", 3, 0, 0, 0, null, 0);
            MemberLoad zeroPointStory = new MemberLoad(2L, "이영희", 3, 0, 0, 1, 0, 0);

            List<MemberLoad> ranked = WorkloadAssessor.rank(List.of(noStories, zeroPointStory));

            MemberLoad kim = ranked.stream()
                    .filter(load -> load.memberId().equals(1L)).findFirst().orElseThrow();
            MemberLoad lee = ranked.stream()
                    .filter(load -> load.memberId().equals(2L)).findFirst().orElseThrow();
            assertThat(kim.storyPoints()).isNull();
            assertThat(lee.storyPoints()).isZero();
        }
    }

    private MemberLoad load(Long id, String name, int activeCount) {
        return new MemberLoad(id, name, activeCount, 0, 0, 0, null, 0);
    }
}
