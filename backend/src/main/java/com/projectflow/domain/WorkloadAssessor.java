package com.projectflow.domain;

import java.util.Comparator;
import java.util.List;

/**
 * 한 사람의 부하를 놓고 판정할 것 — 정렬과 막대의 분모뿐이다 (지시서 workload-balance §2-3, §3-1).
 *
 * <p>모아진 숫자(어느 서비스의 DTO도 아닌 단순 record)만 받는다. 도메인이 application dto에
 * 의존하면 레이어가 뒤집히므로, 집계 자체(네 payload를 사람별로 모으는 것)는
 * {@code WorkloadService}가 하고 여기서는 하지 않는다 — {@link RaidAssessor}·{@link DelayCalculator}와
 * 같은 자리·같은 성격이다.
 *
 * <p><b>절대 임계값을 두지 않는다</b>(지시서 2-2). 가용 공수(capacity)가 없어 "몇 건이 과부하인지"를
 * 말할 근거가 없다 — "5건 이상은 빨강" 같은 선을 그으면 사용자가 그 선을 사실로 읽는다. 대신
 * 프로젝트 안에서의 최댓값 대비 비율(막대)과 내림차순 정렬만 제공하고, 통각은 사람이 한다.
 */
public final class WorkloadAssessor {

    private WorkloadAssessor() {
    }

    /**
     * @param activeCount    기준일에 진행 중인 담당(R) 업무 수 — 주 지표(지시서 2-1)
     * @param delayedCount   {@code activeCount} 중 {@link DelayStatus#DELAYED}인 것
     * @param atRiskCount    {@code activeCount} 중 {@link DelayStatus#AT_RISK}인 것
     * @param openStoryCount 미완료 담당 Story·Bug 수
     * @param storyPoints    그 Story·Bug의 포인트 합. 담당 Story·Bug가 하나도 없거나, 있어도 아무도
     *                       포인트를 적지 않았으면 {@code 0}이 아니라 {@code null} — 진척의
     *                       "산정 전은 0%가 아니다"와 같은 규칙이다
     * @param openRaidCount  열린(CLOSED가 아닌) 소유 RAID 수
     */
    public record MemberLoad(
            Long memberId,
            String memberName,
            int activeCount,
            int delayedCount,
            int atRiskCount,
            int openStoryCount,
            Integer storyPoints,
            int openRaidCount
    ) {
    }

    /**
     * {@code activeCount} 내림차순, 동점은 이름 순 — 화면이 매번 같은 순서로 그리게 한다. 부하가
     * 0인 사람도 그대로 남는다(지시서 3-2 규칙 1) — 순위를 매길 뿐 걸러내지 않는다.
     */
    public static List<MemberLoad> rank(List<MemberLoad> loads) {
        return loads.stream()
                .sorted(Comparator.comparingInt(MemberLoad::activeCount).reversed()
                        .thenComparing(MemberLoad::memberName))
                .toList();
    }

    /** 막대의 분모 — 프로젝트 안에서의 최댓값. 아무도 활성 업무가 없으면 0이고, 그때 막대는 그리지 않는다. */
    public static int maxActive(List<MemberLoad> loads) {
        return loads.stream().mapToInt(MemberLoad::activeCount).max().orElse(0);
    }
}
