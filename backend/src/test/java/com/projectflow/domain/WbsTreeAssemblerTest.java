package com.projectflow.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WbsTreeAssemblerTest {

    private static final Long PROJECT_ID = 1L;

    @Test
    @DisplayName("WBS 코드는 트리 위치에서 자동 생성된다")
    void assignsCodesFromTreePosition() {
        List<WbsItem> items = List.of(
                item(1L, null, "설계", 0, null, null, 0),
                item(2L, 1L, "요구사항 정의", 0, null, null, 0),
                item(3L, 1L, "화면 설계", 1, null, null, 0),
                item(4L, 3L, "와이어프레임", 0, null, null, 0),
                item(5L, null, "개발", 1, null, null, 0)
        );

        List<WbsNode> tree = WbsTreeAssembler.assemble(items);

        assertThat(tree).hasSize(2);
        assertThat(tree.get(0).code()).isEqualTo("1");
        assertThat(tree.get(0).level()).isEqualTo(1);
        assertThat(tree.get(0).children()).extracting(WbsNode::code).containsExactly("1.1", "1.2");
        assertThat(tree.get(0).children().get(1).children()).extracting(WbsNode::code).containsExactly("1.2.1");
        assertThat(tree.get(0).children().get(1).children().get(0).level()).isEqualTo(3);
        assertThat(tree.get(1).code()).isEqualTo("2");
    }

    @Test
    @DisplayName("형제 순서는 sortOrder를 따른다")
    void ordersSiblingsBySortOrder() {
        List<WbsItem> items = List.of(
                item(1L, null, "세 번째", 2, null, null, 0),
                item(2L, null, "첫 번째", 0, null, null, 0),
                item(3L, null, "두 번째", 1, null, null, 0)
        );

        List<WbsNode> tree = WbsTreeAssembler.assemble(items);

        assertThat(tree).extracting(node -> node.item().getName())
                .containsExactly("첫 번째", "두 번째", "세 번째");
    }

    @Test
    @DisplayName("Summary 항목의 일정은 하위 항목에서 집계된다")
    void rollsUpSummarySchedule() {
        List<WbsItem> items = List.of(
                item(1L, null, "설계", 0, null, null, 0),
                item(2L, 1L, "요구사항 정의", 0, date("2026-01-05"), date("2026-01-10"), 100),
                item(3L, 1L, "화면 설계", 1, date("2026-01-01"), date("2026-01-20"), 0)
        );

        WbsNode summary = WbsTreeAssembler.assemble(items).get(0);

        assertThat(summary.summary()).isTrue();
        assertThat(summary.startDate()).isEqualTo(date("2026-01-01"));
        assertThat(summary.endDate()).isEqualTo(date("2026-01-20"));
        assertThat(summary.progress()).isEqualTo(50);
    }

    @Test
    @DisplayName("일정이 비어 있는 하위 항목은 집계에서 무시된다")
    void ignoresChildrenWithoutDates() {
        List<WbsItem> items = List.of(
                item(1L, null, "설계", 0, null, null, 0),
                item(2L, 1L, "미정 업무", 0, null, null, 0),
                item(3L, 1L, "확정 업무", 1, date("2026-03-02"), date("2026-03-06"), 0)
        );

        WbsNode summary = WbsTreeAssembler.assemble(items).get(0);

        assertThat(summary.startDate()).isEqualTo(date("2026-03-02"));
        assertThat(summary.endDate()).isEqualTo(date("2026-03-06"));
    }

    @Test
    @DisplayName("Summary 진행률은 하위 leaf 개수로 가중 평균된다")
    void weightsSummaryProgressByLeafCount() {
        // "개발"은 leaf 3개가 모두 0%, "설계"는 leaf 1개가 100% -> (100*1 + 0*3) / 4 = 25
        List<WbsItem> items = List.of(
                item(1L, null, "프로젝트", 0, null, null, 0),
                item(2L, 1L, "설계", 0, null, null, 100),
                item(3L, 1L, "개발", 1, null, null, 0),
                item(4L, 3L, "화면 개발", 0, null, null, 0),
                item(5L, 3L, "API 개발", 1, null, null, 0),
                item(6L, 3L, "배치 개발", 2, null, null, 0)
        );

        WbsNode root = WbsTreeAssembler.assemble(items).get(0);

        assertThat(root.progress()).isEqualTo(25);
    }

    @Test
    @DisplayName("leaf 항목은 자신의 일정과 진행률을 그대로 유지한다")
    void keepsLeafValues() {
        List<WbsItem> items = List.of(
                item(1L, null, "단독 업무", 0, date("2026-05-01"), date("2026-05-31"), 40)
        );

        WbsNode leaf = WbsTreeAssembler.assemble(items).get(0);

        assertThat(leaf.summary()).isFalse();
        assertThat(leaf.startDate()).isEqualTo(date("2026-05-01"));
        assertThat(leaf.endDate()).isEqualTo(date("2026-05-31"));
        assertThat(leaf.progress()).isEqualTo(40);
    }

    @Test
    @DisplayName("descendantIds는 모든 하위 항목을 재귀적으로 찾는다")
    void findsAllDescendants() {
        List<WbsItem> items = List.of(
                item(1L, null, "설계", 0, null, null, 0),
                item(2L, 1L, "화면 설계", 0, null, null, 0),
                item(3L, 2L, "와이어프레임", 0, null, null, 0),
                item(4L, null, "개발", 1, null, null, 0)
        );

        assertThat(WbsTreeAssembler.descendantIds(items, 1L)).containsExactlyInAnyOrder(2L, 3L);
        assertThat(WbsTreeAssembler.descendantIds(items, 4L)).isEmpty();
    }

    private static WbsItem item(Long id, Long parentId, String name, int sortOrder,
                                 LocalDate startDate, LocalDate endDate, int progress) {
        WbsItem item = new WbsItem(PROJECT_ID, parentId, name, null, startDate, endDate, progress, sortOrder);
        ReflectionTestUtils.setField(item, "id", id);
        return item;
    }

    private static LocalDate date(String iso) {
        return LocalDate.parse(iso);
    }
}
