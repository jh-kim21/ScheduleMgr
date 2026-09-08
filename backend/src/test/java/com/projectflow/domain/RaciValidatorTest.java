package com.projectflow.domain;

import com.projectflow.domain.RaciValidator.IssueType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RaciValidatorTest {

    private static final Long PROJECT_ID = 1L;

    @Nested
    @DisplayName("Accountable 검증 (요구사항 7.3)")
    class Accountable {

        @Test
        @DisplayName("책임자가 둘이면 이름과 함께 중복으로 보고한다")
        void reportsDuplicateAccountableWithNames() {
            List<WbsItem> items = List.of(item(1L, null, "설계"));
            List<ProjectMember> members = List.of(member(10L, "김"), member(11L, "이"));
            List<RaciAssignment> assignments = List.of(
                    assignment(100L, 1L, 10L, RaciRole.ACCOUNTABLE),
                    assignment(101L, 1L, 11L, RaciRole.ACCOUNTABLE),
                    assignment(102L, 1L, 10L, RaciRole.RESPONSIBLE)
            );

            var issues = RaciValidator.validate(WbsTreeAssembler.assemble(items), assignments, members);

            assertThat(issues).hasSize(1);
            assertThat(issues.getFirst().type()).isEqualTo(IssueType.MULTIPLE_ACCOUNTABLE);
            assertThat(issues.getFirst().memberNames()).containsExactly("김", "이");
        }

        @Test
        @DisplayName("책임자가 없으면 누락으로 보고한다")
        void reportsMissingAccountable() {
            List<WbsItem> items = List.of(item(1L, null, "설계"));
            List<RaciAssignment> assignments = List.of(assignment(100L, 1L, 10L, RaciRole.RESPONSIBLE));

            var issues = RaciValidator.validate(
                    WbsTreeAssembler.assemble(items), assignments, List.of(member(10L, "김")));

            assertThat(issues).singleElement()
                    .extracting(RaciValidator.RaciIssue::type).isEqualTo(IssueType.MISSING_ACCOUNTABLE);
        }

        @Test
        @DisplayName("책임자가 정확히 한 명이면 문제가 없다")
        void singleAccountableIsFine() {
            List<WbsItem> items = List.of(item(1L, null, "설계"));
            List<RaciAssignment> assignments = List.of(
                    assignment(100L, 1L, 10L, RaciRole.ACCOUNTABLE),
                    assignment(101L, 1L, 11L, RaciRole.RESPONSIBLE)
            );

            var issues = RaciValidator.validate(WbsTreeAssembler.assemble(items), assignments,
                    List.of(member(10L, "김"), member(11L, "이")));

            assertThat(issues).isEmpty();
        }
    }

    @Nested
    @DisplayName("Responsible 검증 (요구사항 7.4)")
    class Responsible {

        @Test
        @DisplayName("담당자가 없으면 누락으로 보고한다")
        void reportsMissingResponsible() {
            List<WbsItem> items = List.of(item(1L, null, "설계"));
            List<RaciAssignment> assignments = List.of(assignment(100L, 1L, 10L, RaciRole.ACCOUNTABLE));

            var issues = RaciValidator.validate(
                    WbsTreeAssembler.assemble(items), assignments, List.of(member(10L, "김")));

            assertThat(issues).singleElement()
                    .extracting(RaciValidator.RaciIssue::type).isEqualTo(IssueType.MISSING_RESPONSIBLE);
        }

        @Test
        @DisplayName("한 사람이 책임자와 담당자를 겸해도 누락이 아니다")
        void oneMemberCanHoldBothLetters() {
            List<WbsItem> items = List.of(item(1L, null, "설계"));
            List<RaciAssignment> assignments = List.of(
                    assignment(100L, 1L, 10L, RaciRole.ACCOUNTABLE),
                    assignment(101L, 1L, 10L, RaciRole.RESPONSIBLE)
            );

            var issues = RaciValidator.validate(
                    WbsTreeAssembler.assemble(items), assignments, List.of(member(10L, "김")));

            assertThat(issues).isEmpty();
        }

        @Test
        @DisplayName("C와 I만 있으면 책임자와 담당자 둘 다 누락으로 보고한다")
        void consultedAndInformedDoNotCount() {
            List<WbsItem> items = List.of(item(1L, null, "설계"));
            List<RaciAssignment> assignments = List.of(
                    assignment(100L, 1L, 10L, RaciRole.CONSULTED),
                    assignment(101L, 1L, 11L, RaciRole.INFORMED)
            );

            var issues = RaciValidator.validate(WbsTreeAssembler.assemble(items), assignments,
                    List.of(member(10L, "김"), member(11L, "이")));

            assertThat(issues).extracting(RaciValidator.RaciIssue::type)
                    .containsExactlyInAnyOrder(IssueType.MISSING_ACCOUNTABLE, IssueType.MISSING_RESPONSIBLE);
        }
    }

    @Nested
    @DisplayName("검증 대상")
    class Scope {

        @Test
        @DisplayName("Summary는 검증하지 않는다 — 하위마다 중복 보고된다")
        void summariesAreNotChecked() {
            List<WbsItem> items = List.of(
                    item(1L, null, "설계"),
                    item(2L, 1L, "화면 설계"),
                    item(3L, 1L, "DB 설계")
            );
            // Summary(1)에는 아무 배정이 없지만 leaf 두 개는 완전하다.
            List<RaciAssignment> assignments = List.of(
                    assignment(100L, 2L, 10L, RaciRole.ACCOUNTABLE),
                    assignment(101L, 2L, 10L, RaciRole.RESPONSIBLE),
                    assignment(102L, 3L, 10L, RaciRole.ACCOUNTABLE),
                    assignment(103L, 3L, 10L, RaciRole.RESPONSIBLE)
            );

            var issues = RaciValidator.validate(
                    WbsTreeAssembler.assemble(items), assignments, List.of(member(10L, "김")));

            assertThat(issues).isEmpty();
        }

        @Test
        @DisplayName("배정이 하나도 없으면 leaf마다 두 건씩 보고한다")
        void emptyMatrixReportsBothPerLeaf() {
            List<WbsItem> items = List.of(
                    item(1L, null, "설계"),
                    item(2L, 1L, "화면 설계"),
                    item(3L, null, "개발")
            );

            var issues = RaciValidator.validate(WbsTreeAssembler.assemble(items), List.of(), List.of());

            // leaf는 2.1(화면 설계)와 개발 두 개 → 2 × (A 누락 + R 누락)
            assertThat(issues).hasSize(4);
            assertThat(issues).extracting(RaciValidator.RaciIssue::wbsItemId)
                    .containsExactlyInAnyOrder(2L, 2L, 3L, 3L);
        }

        @Test
        @DisplayName("WBS 항목이 없으면 보고할 것도 없다")
        void noTasksNoIssues() {
            assertThat(RaciValidator.validate(List.of(), List.of(), List.of())).isEmpty();
        }
    }

    @Nested
    @DisplayName("상속을 반영한 판정 (Step 6)")
    class Inheritance {

        @Test
        @DisplayName("상위의 A·R을 물려받은 leaf는 누락이 아니다")
        void inheritedRolesFillTheGap() {
            List<WbsItem> items = List.of(
                    item(1L, null, "설계"),
                    item(2L, 1L, "화면 설계")
            );
            List<RaciAssignment> assignments = List.of(
                    assignment(10L, 1L, 100L, RaciRole.ACCOUNTABLE),
                    assignment(11L, 1L, 200L, RaciRole.RESPONSIBLE)
            );

            var issues = RaciValidator.validate(
                    WbsTreeAssembler.assemble(items), assignments,
                    List.of(member(100L, "PM"), member(200L, "PL")));

            assertThat(issues).isEmpty();
        }

        @Test
        @DisplayName("책임자가 둘인 것은 그 글자를 실제로 가진 행에 한 번만 보고한다")
        void clashIsReportedWhereItIsDeclared() {
            List<WbsItem> items = List.of(
                    item(1L, null, "설계"),
                    item(2L, 1L, "화면 설계"),
                    item(3L, 1L, "데이터 설계")
            );
            List<RaciAssignment> assignments = List.of(
                    assignment(10L, 1L, 100L, RaciRole.ACCOUNTABLE),
                    assignment(11L, 1L, 200L, RaciRole.ACCOUNTABLE),
                    assignment(12L, 1L, 300L, RaciRole.RESPONSIBLE)
            );

            var issues = RaciValidator.validate(
                    WbsTreeAssembler.assemble(items), assignments,
                    List.of(member(100L, "PM"), member(200L, "PL"), member(300L, "개발")));

            // 하위 둘이 각각 물려받지만, 정리해야 할 행은 배정을 가진 상위 하나다.
            assertThat(issues)
                    .filteredOn(issue -> issue.type() == RaciValidator.IssueType.MULTIPLE_ACCOUNTABLE)
                    .singleElement()
                    .satisfies(issue -> {
                        assertThat(issue.wbsItemId()).isEqualTo(1L);
                        assertThat(issue.memberNames()).containsExactly("PL", "PM");
                    });
        }

        @Test
        @DisplayName("하위가 다시 정하면 상위의 A는 그 행에 적용되지 않는다")
        void overrideReplacesTheInheritedRole() {
            List<WbsItem> items = List.of(
                    item(1L, null, "설계"),
                    item(2L, 1L, "화면 설계")
            );
            List<RaciAssignment> assignments = List.of(
                    assignment(10L, 1L, 100L, RaciRole.ACCOUNTABLE),
                    assignment(11L, 2L, 200L, RaciRole.ACCOUNTABLE),
                    assignment(12L, 2L, 300L, RaciRole.RESPONSIBLE)
            );

            var issues = RaciValidator.validate(
                    WbsTreeAssembler.assemble(items), assignments,
                    List.of(member(100L, "PM"), member(200L, "PL"), member(300L, "개발")));

            // 두 A가 서로 다른 행에 있으므로 충돌이 아니다.
            assertThat(issues).isEmpty();
        }
    }

    private static WbsItem item(Long id, Long parentId, String name) {
        WbsItem item = new WbsItem(PROJECT_ID, parentId, name, null, null, null, 0, 0);
        ReflectionTestUtils.setField(item, "id", id);
        return item;
    }

    private static ProjectMember member(Long id, String name) {
        ProjectMember member = new ProjectMember(PROJECT_ID, name, null, null);
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }

    private static RaciAssignment assignment(Long id, Long wbsItemId, Long memberId, RaciRole role) {
        RaciAssignment assignment = new RaciAssignment(PROJECT_ID, wbsItemId, memberId, role);
        ReflectionTestUtils.setField(assignment, "id", id);
        return assignment;
    }
}
