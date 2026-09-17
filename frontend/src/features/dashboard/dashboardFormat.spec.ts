import { describe, expect, it } from 'vitest'
import type { DataGap, RaidRef, SprintVelocity, TaskRef } from '../../api/dashboardApi'
import {
  PROGRESS_TAB,
  barPercent,
  closedSprints,
  gapRoute,
  latestClosedSprint,
  overflowSuffix,
  raidRefLabel,
  runningSprint,
  taskRoute,
  varianceKpiTone,
  varianceSentence,
  varianceToneClass,
  type BarListItem,
} from './dashboardFormat'

/**
 * 순수 함수 단위 테스트 — DOM 이 필요 없어 카드 마운트 없이도 판정 규칙을 고정한다
 * (`rowSelection.ts` · `weightSuggestion.ts` 와 같은 관습). 컴포넌트 테스트
 * (`dashboardCards.spec.ts` · `KpiCard.spec.ts`)와 겹치지 않게, 여기서는 조건 분기 자체의
 * 정확성만 본다.
 */

function task(overrides: Partial<TaskRef> = {}): TaskRef {
  return { wbsItemId: 5, code: '2.1', name: 'API 설계', detail: '12일 지연', ...overrides }
}

function gap(overrides: Partial<DataGap> = {}): DataGap {
  return { kind: 'EXECUTION_MODE_MISSING', label: '실행 방식 미지정', count: 1, wbsItemIds: [5], backlogItemIds: [], ...overrides }
}

function raid(overrides: Partial<RaidRef> = {}): RaidRef {
  return { raidItemId: 1, type: 'ISSUE', title: '서버 장애', ownerName: '김', detail: null, ...overrides }
}

function sprint(overrides: Partial<SprintVelocity> = {}): SprintVelocity {
  return {
    sprintId: 1, name: 'Sprint 1', startDate: '2026-01-01', endDate: '2026-01-14',
    status: 'CLOSED', donePoints: 10, doneItems: 3, carriedOverItems: 0, inProgress: false,
    ...overrides,
  }
}

describe('taskRoute', () => {
  it('WBS 화면으로, focus 쿼리에 wbsItemId를 싣는다', () => {
    expect(taskRoute(task({ wbsItemId: 42 }))).toEqual({ path: '/wbs', query: { focus: 42 } })
  })
})

describe('gapRoute — kind 3갈래 분기', () => {
  it('BACKLOG로 시작하는 kind는 Backlog 화면으로 보낸다', () => {
    expect(gapRoute(gap({ kind: 'BACKLOG_UNLINKED' }))).toEqual({ path: '/backlog' })
  })

  it('NOT_ESTIMABLE은 진척 탭으로 보낸다', () => {
    expect(gapRoute(gap({ kind: 'NOT_ESTIMABLE' }))).toEqual(PROGRESS_TAB)
  })

  it('WEIGHT_MISSING도 진척 탭으로 보낸다', () => {
    expect(gapRoute(gap({ kind: 'WEIGHT_MISSING' }))).toEqual(PROGRESS_TAB)
  })

  it('그 외 kind는 WBS 화면으로, focus에 첫 wbsItemId를 싣는다', () => {
    expect(gapRoute(gap({ kind: 'EXECUTION_MODE_MISSING', wbsItemIds: [7, 8] }))).toEqual({
      path: '/wbs',
      query: { focus: 7 },
    })
  })
})

describe('raidRefLabel', () => {
  it('detail이 있으면 제목 · 상세 · 소유자 순으로 잇는다', () => {
    expect(raidRefLabel(raid({ title: 'PG 계약', detail: '3일 초과', ownerName: '이' }))).toBe(
      'PG 계약 · 3일 초과 · 이',
    )
  })

  it('detail이 없으면 제목 · 소유자만 잇는다', () => {
    expect(raidRefLabel(raid({ title: '서버 장애', detail: null, ownerName: '김' }))).toBe('서버 장애 · 김')
  })

  it('소유자가 없으면 "소유자 미지정"으로 채운다', () => {
    expect(raidRefLabel(raid({ title: '인력 이탈', detail: null, ownerName: null }))).toBe(
      '인력 이탈 · 소유자 미지정',
    )
  })
})

describe('overflowSuffix', () => {
  it('총건수가 표시 건수보다 많으면 두 숫자를 함께 적는다', () => {
    expect(overflowSuffix(8, 5)).toBe(' (5건 표시 · 총 8건)')
  })

  it('총건수가 표시 건수 이하이면 빈 문자열이다(잘리지 않았다는 뜻)', () => {
    expect(overflowSuffix(3, 3)).toBe('')
    expect(overflowSuffix(0, 0)).toBe('')
  })
})

describe('varianceSentence / varianceToneClass / varianceKpiTone', () => {
  it('null은 미산정이라 문장이 없다', () => {
    expect(varianceSentence(null)).toBeNull()
    expect(varianceToneClass(null)).toBeNull()
    expect(varianceKpiTone(null)).toBe('gray')
  })

  it('반올림 후 0이면 "계획과 같음", 톤은 gray', () => {
    expect(varianceSentence(0.04)).toBe('계획과 같음')
    expect(varianceToneClass(0.04)).toBeNull()
    expect(varianceKpiTone(0.04)).toBe('gray')
  })

  it('양수는 "앞섬", 톤은 ahead/green', () => {
    expect(varianceSentence(3.24)).toBe('계획보다 3.2%p 앞섬')
    expect(varianceToneClass(3.24)).toBe('variance-ahead')
    expect(varianceKpiTone(3.24)).toBe('green')
  })

  it('음수는 "뒤짐", 톤은 behind/red — 부호를 잃지 않는다', () => {
    expect(varianceSentence(-1.5)).toBe('계획보다 1.5%p 뒤짐')
    expect(varianceToneClass(-1.5)).toBe('variance-behind')
    expect(varianceKpiTone(-1.5)).toBe('red')
  })
})

describe('closedSprints / runningSprint / latestClosedSprint', () => {
  const velocity = [
    sprint({ sprintId: 3, name: 'Sprint 3', donePoints: 18, inProgress: false }),
    sprint({ sprintId: 4, name: 'Sprint 4', donePoints: 32, inProgress: false }),
    sprint({ sprintId: 5, name: 'Sprint 5', donePoints: 21, inProgress: true, status: 'ACTIVE' }),
  ]

  it('closedSprints는 진행 중 Sprint를 뺀다', () => {
    expect(closedSprints(velocity).map((s) => s.sprintId)).toEqual([3, 4])
  })

  it('runningSprint는 진행 중인 것 하나, 없으면 null', () => {
    expect(runningSprint(velocity)?.sprintId).toBe(5)
    expect(runningSprint(closedSprints(velocity))).toBeNull()
  })

  it('latestClosedSprint는 서버 정렬(오름차순)의 마지막 종료 Sprint다 — 재정렬하지 않는다', () => {
    expect(latestClosedSprint(velocity)?.sprintId).toBe(4)
  })

  it('종료된 Sprint가 없으면 latestClosedSprint는 null이다', () => {
    expect(latestClosedSprint([sprint({ sprintId: 5, inProgress: true })])).toBeNull()
  })

  it('velocity가 비어 있으면 셋 다 빈 값이다', () => {
    expect(closedSprints([])).toEqual([])
    expect(runningSprint([])).toBeNull()
    expect(latestClosedSprint([])).toBeNull()
  })
})

describe('barPercent', () => {
  it('percent가 명시되어 있으면 그대로 쓴다(계산하지 않는다)', () => {
    const item: BarListItem = { key: 'a', label: 'A', value: 1, percent: 55 }
    expect(barPercent(item)).toBe(55)
  })

  it('percent가 null이면 산정 전으로 null을 그대로 돌려준다', () => {
    const item: BarListItem = { key: 'a', label: 'A', value: 1, percent: null }
    expect(barPercent(item)).toBeNull()
  })

  it('percent가 없고 total이 있으면 value/total 비율을 낸다', () => {
    const item: BarListItem = { key: 'a', label: 'A', value: 3, total: 12 }
    expect(barPercent(item)).toBe(25)
  })

  it('total이 0이면 0으로 나누지 않고 null을 돌려준다', () => {
    const item: BarListItem = { key: 'a', label: 'A', value: 0, total: 0 }
    expect(barPercent(item)).toBeNull()
  })

  it('percent도 total도 없으면 null이다', () => {
    const item: BarListItem = { key: 'a', label: 'A', value: 3 }
    expect(barPercent(item)).toBeNull()
  })
})
