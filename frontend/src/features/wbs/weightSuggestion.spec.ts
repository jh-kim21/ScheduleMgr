import { describe, expect, it } from 'vitest'
import { suggestWeight } from './weightSuggestion'
import type { WeightSuggestionSibling } from './weightSuggestion'

/**
 * `suggestWeight`는 아직 구현되지 않았다(Developer1 작업 중). 이 스펙은 리더가 확정한 계약을
 * 기준으로 구현보다 먼저 작성한다 — 계약:
 *
 *   suggestWeight(
 *     self: { startDate: string | null; endDate: string | null },
 *     siblings: WeightSuggestionSibling[],
 *   ): WeightSuggestion | null
 *
 * 1. `weighted` = weight !== null && weight > 0 인 형제. 비면 null.
 * 2. `dated` = weighted 중 startDate·endDate 둘 다 있고 endDate >= startDate 인 것.
 *    self도 두 날짜가 있고 endDate >= startDate이며 dated가 비어있지 않으면 기간 비례(DURATION).
 * 3. 그 밖에는 weighted 가중치 평균(SIBLING_AVERAGE), 반올림, 최소 1.
 *
 * 기간일수는 종료일 포함(inclusive) = 날짜 차이 + 1.
 */

function sibling(overrides: Partial<WeightSuggestionSibling> = {}): WeightSuggestionSibling {
  return {
    weight: null,
    startDate: null,
    endDate: null,
    ...overrides,
  }
}

describe('suggestWeight — 계약 1: 근거가 없으면 null (숫자를 지어내지 않는다)', () => {
  it('형제가 아예 없으면 null이다 — 비교할 대상이 없다', () => {
    expect(suggestWeight({ startDate: '2026-01-01', endDate: '2026-01-10' }, [])).toBeNull()
  })

  it('형제는 있는데 가중치가 전부 null이면 null이다 — weighted 집합이 비어 근거가 없다', () => {
    const siblings = [
      sibling({ weight: null, startDate: '2026-01-01', endDate: '2026-01-10' }),
      sibling({ weight: null, startDate: '2026-01-11', endDate: '2026-01-20' }),
    ]
    expect(suggestWeight({ startDate: '2026-01-01', endDate: '2026-01-10' }, siblings)).toBeNull()
  })

  it('가중치 0만 있는 형제는 근거로 치지 않는다 → null (0은 "기여하지 않음"이지 비중이 아니다)', () => {
    const siblings = [
      sibling({ weight: 0, startDate: '2026-01-01', endDate: '2026-01-10' }),
      sibling({ weight: 0, startDate: '2026-01-11', endDate: '2026-01-20' }),
    ]
    expect(suggestWeight({ startDate: '2026-01-01', endDate: '2026-01-10' }, siblings)).toBeNull()
  })
})

describe('suggestWeight — 계약 2: 기간 비례(DURATION)', () => {
  it('기간 비례가 맞는 기본 사례: 형제 weight 2 / 10일, 자기 20일 → 4, basis DURATION', () => {
    const siblings = [sibling({ weight: 2, startDate: '2026-01-01', endDate: '2026-01-10' })]
    // 형제 기간: 01-01 ~ 01-10 inclusive = 10일 → unit = 2 / 10 = 0.2
    // 자기 기간: 01-01 ~ 01-20 inclusive = 20일 → value = round(0.2 * 20) = 4
    expect(suggestWeight({ startDate: '2026-01-01', endDate: '2026-01-20' }, siblings)).toEqual({
      value: 4,
      basis: 'DURATION',
    })
  })

  it('하루짜리 업무도 inclusive라 기간 1일로 센다(0일이 아니다)', () => {
    // 형제: 하루(1일)짜리, weight 3 → unit = 3
    const siblings = [sibling({ weight: 3, startDate: '2026-01-05', endDate: '2026-01-05' })]
    // 자기도 하루(1일) → value = round(3 * 1) = 3
    expect(suggestWeight({ startDate: '2026-01-05', endDate: '2026-01-05' }, siblings)).toEqual({
      value: 3,
      basis: 'DURATION',
    })
  })

  it('여러 dated 형제의 가중치·기간을 합산해 단위 비중을 구한다', () => {
    const siblings = [
      sibling({ weight: 2, startDate: '2026-01-01', endDate: '2026-01-10' }), // 10일
      sibling({ weight: 3, startDate: '2026-01-11', endDate: '2026-01-15' }), // 5일
    ]
    // 합계 weight = 5, 합계 기간 = 15일 → unit = 1/3
    // 자기 기간: 01-01 ~ 01-09 inclusive = 9일 → value = round(9 * 1/3) = round(3) = 3
    expect(suggestWeight({ startDate: '2026-01-01', endDate: '2026-01-09' }, siblings)).toEqual({
      value: 3,
      basis: 'DURATION',
    })
  })

  it('endDate < startDate인 형제는 dated에서 빠진다 — 남은 형제만으로 기간 비례를 계산한다', () => {
    const siblings = [
      sibling({ weight: 5, startDate: '2026-01-20', endDate: '2026-01-01' }), // 뒤집힌 기간, 제외
      sibling({ weight: 2, startDate: '2026-01-01', endDate: '2026-01-10' }), // 10일, unit = 0.2
    ]
    expect(suggestWeight({ startDate: '2026-01-01', endDate: '2026-01-20' }, siblings)).toEqual({
      value: 4,
      basis: 'DURATION',
    })
  })

  it('자기 자신의 endDate < startDate이면 기간 비례로 가지 않고 형제 평균으로 떨어진다', () => {
    const siblings = [
      sibling({ weight: 4, startDate: '2026-01-01', endDate: '2026-01-10' }),
      sibling({ weight: 2, startDate: null, endDate: null }),
    ]
    // weighted = [4, 2] → 평균 3, basis SIBLING_AVERAGE (자기 기간이 뒤집혀 DURATION 불가)
    expect(suggestWeight({ startDate: '2026-01-20', endDate: '2026-01-01' }, siblings)).toEqual({
      value: 3,
      basis: 'SIBLING_AVERAGE',
    })
  })

  it('타임존 비의존: 월 경계를 넘는 기간도 로컬 파싱 오차 없이 정확히 계산한다', () => {
    // 2026-02-27 ~ 2026-03-02 inclusive: 27,28(윤년 아님),1,2 = 4일
    const siblings = [sibling({ weight: 4, startDate: '2026-02-27', endDate: '2026-03-02' })]
    // unit = 4/4 = 1
    // 자기: 2025-12-30 ~ 2026-01-02 inclusive: 30,31,1,2 = 4일 (연 경계)
    expect(suggestWeight({ startDate: '2025-12-30', endDate: '2026-01-02' }, siblings)).toEqual({
      value: 4,
      basis: 'DURATION',
    })
  })
})

describe('suggestWeight — 계약 3: 형제 평균(SIBLING_AVERAGE)', () => {
  it('자기 날짜가 없으면 기간 비례로 못 가고 형제 평균으로 떨어진다', () => {
    const siblings = [
      sibling({ weight: 2, startDate: '2026-01-01', endDate: '2026-01-10' }),
      sibling({ weight: 4, startDate: '2026-01-11', endDate: '2026-01-20' }),
    ]
    // weighted 평균 = (2+4)/2 = 3
    expect(suggestWeight({ startDate: null, endDate: null }, siblings)).toEqual({
      value: 3,
      basis: 'SIBLING_AVERAGE',
    })
  })

  it('가중치는 있는데 날짜 있는 형제가 하나도 없으면 형제 평균으로 떨어진다', () => {
    const siblings = [
      sibling({ weight: 2, startDate: null, endDate: null }),
      sibling({ weight: 6, startDate: null, endDate: null }),
    ]
    expect(suggestWeight({ startDate: '2026-01-01', endDate: '2026-01-10' }, siblings)).toEqual({
      value: 4,
      basis: 'SIBLING_AVERAGE',
    })
  })

  it('형제 평균이 반올림된다: 가중치 [1, 2] → 평균 1.5 → 2', () => {
    const siblings = [
      sibling({ weight: 1, startDate: null, endDate: null }),
      sibling({ weight: 2, startDate: null, endDate: null }),
    ]
    expect(suggestWeight({ startDate: null, endDate: null }, siblings)).toEqual({
      value: 2,
      basis: 'SIBLING_AVERAGE',
    })
  })

  it('반올림 결과가 0이 될 상황에서도 최소 1이다 (아주 짧은 자기 기간 × 작은 unit)', () => {
    // 형제: weight 1 / 100일 → unit = 0.01
    const siblings = [sibling({ weight: 1, startDate: '2026-01-01', endDate: '2026-04-10' })]
    // 자기: 하루(1일) → round(0.01 * 1) = round(0.01) = 0 → 최소 1로 보정
    expect(suggestWeight({ startDate: '2026-06-01', endDate: '2026-06-01' }, siblings)).toEqual({
      value: 1,
      basis: 'DURATION',
    })
  })

  it('형제 평균에서도 반올림 결과가 0 이하로 내려가지 않는다 (최소 1)', () => {
    const siblings = [
      sibling({ weight: 0.1, startDate: null, endDate: null }),
      sibling({ weight: 0.2, startDate: null, endDate: null }),
    ]
    // 평균 0.15 → round(0.15) = 0 → 최소 1로 보정
    expect(suggestWeight({ startDate: null, endDate: null }, siblings)).toEqual({
      value: 1,
      basis: 'SIBLING_AVERAGE',
    })
  })
})
