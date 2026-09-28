import { describe, expect, it } from 'vitest'
import type { MemberLoad } from '../../api/dashboardApi'
import { delayBadge, maxActive, sortMembers, topMembers } from './workloadFormat'

function member(overrides: Partial<MemberLoad> & { memberId: number }): MemberLoad {
  return {
    memberName: `M${overrides.memberId}`,
    activeCount: 0,
    delayedCount: 0,
    atRiskCount: 0,
    openStoryCount: 0,
    storyPoints: null,
    openRaidCount: 0,
    ...overrides,
  }
}

describe('maxActive', () => {
  it('가장 큰 activeCount를 돌려준다', () => {
    const members = [member({ memberId: 1, activeCount: 2 }), member({ memberId: 2, activeCount: 5 })]
    expect(maxActive(members)).toBe(5)
  })

  it('구성원이 없으면 0이다 — 막대를 그리지 않는 신호로 쓴다', () => {
    expect(maxActive([])).toBe(0)
  })

  it('전원의 activeCount가 0이어도 0이다', () => {
    const members = [member({ memberId: 1, activeCount: 0 }), member({ memberId: 2, activeCount: 0 })]
    expect(maxActive(members)).toBe(0)
  })
})

describe('topMembers', () => {
  it('기본은 상위 5명만 자른다 — 정렬은 서버가 이미 했으므로 다시 하지 않는다', () => {
    const members = Array.from({ length: 8 }, (_, i) => member({ memberId: i + 1 }))
    const top = topMembers(members)
    expect(top).toHaveLength(5)
    expect(top.map((m) => m.memberId)).toEqual([1, 2, 3, 4, 5])
  })

  it('5명 이하면 그대로 돌려준다', () => {
    const members = [member({ memberId: 1 }), member({ memberId: 2 })]
    expect(topMembers(members)).toHaveLength(2)
  })
})

describe('delayBadge', () => {
  it('지연이 있으면 지연 배지를 danger 톤으로 낸다', () => {
    const badge = delayBadge(member({ memberId: 1, delayedCount: 2, atRiskCount: 3 }))
    expect(badge).toEqual({ label: '지연 2', tone: 'danger' })
  })

  it('지연이 없고 위험만 있으면 위험 배지를 warn 톤으로 낸다', () => {
    const badge = delayBadge(member({ memberId: 1, atRiskCount: 1 }))
    expect(badge).toEqual({ label: '위험 1', tone: 'warn' })
  })

  it('둘 다 없으면 배지를 만들지 않는다', () => {
    expect(delayBadge(member({ memberId: 1 }))).toBeNull()
  })
})

describe('sortMembers', () => {
  const members = [
    member({ memberId: 1, memberName: '나', storyPoints: 5 }),
    member({ memberId: 2, memberName: '다', storyPoints: null }),
    member({ memberId: 3, memberName: '가', storyPoints: 0 }),
  ]

  it('원본 배열을 바꾸지 않고 정렬한 복사본을 돌려준다', () => {
    const copy = [...members]
    sortMembers(members, 'storyPoints', 'desc')
    expect(members).toEqual(copy)
  })

  it('값이 없는(null) 항목은 방향과 무관하게 항상 뒤로 간다 — 0과 다른 취급이다', () => {
    const desc = sortMembers(members, 'storyPoints', 'desc')
    expect(desc.map((m) => m.memberId)).toEqual([1, 3, 2])

    const asc = sortMembers(members, 'storyPoints', 'asc')
    expect(asc.map((m) => m.memberId)).toEqual([3, 1, 2])
  })

  it('이름은 로케일 비교로 정렬한다', () => {
    const asc = sortMembers(members, 'memberName', 'asc')
    expect(asc.map((m) => m.memberName)).toEqual(['가', '나', '다'])
  })

  it('백엔드가 @JsonInclude(NON_NULL)이라 필드가 생략돼 undefined로 와도 NaN 없이 뒤로 간다', () => {
    // 타입은 number | null 이지만, 느슨한 비교(== null)로 undefined 도 같은 취급을 받는지 고정한다
    // — === null 로 좁히면 여기서 av - bv 가 NaN 이 되어 정렬이 흔들린다.
    const withUndefined = [
      member({ memberId: 1, storyPoints: 5 }),
      { ...member({ memberId: 2 }), storyPoints: undefined } as unknown as (typeof members)[number],
    ]
    const desc = sortMembers(withUndefined, 'storyPoints', 'desc')
    expect(desc.map((m) => m.memberId)).toEqual([1, 2])
    expect(desc.some((m) => Number.isNaN(m.storyPoints))).toBe(false)
  })

  it('동점은 memberId 순으로 떨어져 정렬 중 행이 튀지 않는다', () => {
    const tied = [
      member({ memberId: 2, activeCount: 3 }),
      member({ memberId: 1, activeCount: 3 }),
    ]
    expect(sortMembers(tied, 'activeCount', 'desc').map((m) => m.memberId)).toEqual([1, 2])
  })
})
