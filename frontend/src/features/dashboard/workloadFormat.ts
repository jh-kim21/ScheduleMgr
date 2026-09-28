import type { MemberLoad } from '../../api/dashboardApi'

/**
 * 부하 카드·탭이 함께 쓰는 순수 함수. 판정을 컴포넌트 밖에 두는 이 저장소의 관습을 그대로
 * 따른다(raidFilter.ts·rowSelection.ts와 같은 이유) — DOM 없이 vitest로 고정할 수 있다.
 *
 * <p>여기에 새 산술을 만들지 않는다. `members`는 이미 백엔드(`WorkloadAssessor`)가
 * activeCount 내림차순·동점은 이름 순으로 정렬해 보낸다 — 이 파일은 그것을 어떻게 자르고
 * 무엇을 다시 정렬할지만 정한다(workload-balance 지시서 2-2·2-4).
 */

/**
 * 막대의 분모 — 이 프로젝트 안에서 가장 부하가 큰 사람의 값. 절대 임계값을 두지 않고 최댓값
 * 대비 분포만 보여주기 위한 것이다(지시서 2-2). 0이면(=아무도 부하가 없으면) 막대를 그리지
 * 않는다 — 호출부가 `total`을 비워 `MiniBar`가 채움 없이 트랙만 그리게 한다.
 */
export function maxActive(members: MemberLoad[]): number {
  return members.reduce((max, member) => Math.max(max, member.activeCount), 0)
}

/**
 * 요약 카드는 상위 5명만(지시서 2-4) — 부하 탭이 같은 배열 전부를 그린다. 서버가 이미 정렬해
 * 보내므로 여기서 다시 정렬하지 않는다.
 */
export function topMembers(members: MemberLoad[], limit = 5): MemberLoad[] {
  return members.slice(0, limit)
}

export interface WorkloadBadge {
  label: string
  tone: 'danger' | 'warn'
}

/**
 * 요약 카드가 이름 옆에 붙이는 지연 배지. 지연이 있으면 그것을, 없고 위험만 있으면 그것을 적는다
 * — 둘 다 배지 하나짜리 자리라 동시에 적지 않는다(탭의 표에는 두 숫자 모두 별도 열로 있다).
 * 색은 기존 지연 배지 톤(`--status-delayed`/`--status-at-risk`에 대응하는 StatChip 톤)을 그대로
 * 쓴다 — 부하 자체에는 색을 얹지 않는다(지시서 2-2).
 */
export function delayBadge(member: MemberLoad): WorkloadBadge | null {
  if (member.delayedCount > 0) return { label: `지연 ${member.delayedCount}`, tone: 'danger' }
  if (member.atRiskCount > 0) return { label: `위험 ${member.atRiskCount}`, tone: 'warn' }
  return null
}

export type WorkloadSortKey =
  | 'memberName'
  | 'activeCount'
  | 'delayedCount'
  | 'atRiskCount'
  | 'openStoryCount'
  | 'storyPoints'
  | 'openRaidCount'

export type SortDirection = 'asc' | 'desc'

/**
 * 부하 탭 표의 열 정렬. `null`(산정 없음)은 방향에 상관없이 항상 뒤로 보낸다 — RAID 정렬
 * (raidFilter.ts)과 같은 규칙이다: 담당 Story가 없는 사람이 포인트 0인 사람보다 위로 오면
 * "부하가 크다"로 잘못 읽힌다. 동점은 항상 memberId 순이라 편집 중에 행이 튀지 않는다.
 */
export function sortMembers(
  members: MemberLoad[],
  key: WorkloadSortKey,
  direction: SortDirection,
): MemberLoad[] {
  const sign = direction === 'desc' ? -1 : 1
  const byId = (a: MemberLoad, b: MemberLoad) => a.memberId - b.memberId

  if (key === 'memberName') {
    return members
      .slice()
      .sort((a, b) => sign * a.memberName.localeCompare(b.memberName) || byId(a, b))
  }

  return members.slice().sort((a, b) => {
    const av = a[key]
    const bv = b[key]
    // storyPoints는 `null`(산정 없음)이다. 느슨한 비교(`== null`)로 `undefined`도 같이
    // 잡는다 — 백엔드가 전역 @JsonInclude(NON_NULL)을 쓰면 필드 자체가 생략돼 `undefined`로
    // 오는데, `=== null`로 좁히면 그 경우 `av - bv`가 NaN이 되어 정렬이 흔들린다.
    if (av == bv) return byId(a, b)
    if (av == null) return 1
    if (bv == null) return -1
    return sign * (av - bv)
  })
}
