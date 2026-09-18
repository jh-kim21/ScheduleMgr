import { describe, expect, it } from 'vitest'
import type { RaidItem } from '../../api/raidApi'
import {
  applyFilters,
  DEFAULT_FILTERS,
  filterItems,
  filtersFromQuery,
  filtersToQuery,
  isFiltered,
  sortItems,
  typeCounts,
  type RaidFilters,
} from './raidFilter'

function item(overrides: Partial<RaidItem> & { id: number }): RaidItem {
  return {
    type: 'RISK',
    title: '제목',
    description: null,
    status: 'OPEN',
    probability: null,
    impact: null,
    ownerMemberId: null,
    ownerName: null,
    links: [],
    dueDate: null,
    response: null,
    exposure: null,
    exposureLevel: null,
    overdue: false,
    overdueDays: 0,
    ...overrides,
  }
}

const filters = (overrides: Partial<RaidFilters> = {}): RaidFilters => ({
  ...DEFAULT_FILTERS,
  ...overrides,
})

describe('종류 필터', () => {
  const items = [
    item({ id: 1, type: 'RISK' }),
    item({ id: 2, type: 'ISSUE' }),
    item({ id: 3, type: 'RISK' }),
  ]

  it('전체는 모두 통과시킨다', () => {
    expect(filterItems(items, filters()).map((i) => i.id)).toEqual([1, 2, 3])
  })

  it('한 종류만 남긴다', () => {
    expect(filterItems(items, filters({ type: 'RISK' })).map((i) => i.id)).toEqual([1, 3])
  })
})

describe('상태 필터', () => {
  const items = [
    item({ id: 1, status: 'OPEN' }),
    item({ id: 2, status: 'IN_PROGRESS' }),
    item({ id: 3, status: 'CLOSED' }),
  ]

  it('미종결만은 종결을 뺀 나머지를 남긴다', () => {
    expect(filterItems(items, filters({ status: 'OPEN_ONLY' })).map((i) => i.id)).toEqual([1, 2])
  })

  it('특정 상태는 그 상태만 남긴다', () => {
    expect(filterItems(items, filters({ status: 'CLOSED' })).map((i) => i.id)).toEqual([3])
  })
})

describe('검색어 필터', () => {
  const items = [
    item({ id: 1, title: '인력 이탈' }),
    item({ id: 2, title: '성능 미달', description: '응답 시간 초과' }),
    item({ id: 3, title: '외부 납품', ownerName: '김재학' }),
  ]

  it('제목에서 찾는다', () => {
    expect(filterItems(items, filters({ query: '이탈' })).map((i) => i.id)).toEqual([1])
  })

  it('설명과 소유자 이름에서도 찾는다', () => {
    expect(filterItems(items, filters({ query: '응답' })).map((i) => i.id)).toEqual([2])
    expect(filterItems(items, filters({ query: '김재학' })).map((i) => i.id)).toEqual([3])
  })

  it('대소문자를 구분하지 않고 공백만 있으면 무시한다', () => {
    const cased = [item({ id: 1, title: 'API Gateway' })]
    expect(filterItems(cased, filters({ query: 'api' })).map((i) => i.id)).toEqual([1])
    expect(filterItems(items, filters({ query: '   ' })).map((i) => i.id)).toEqual([1, 2, 3])
  })
})

describe('정렬', () => {
  it('등록순은 id 오름차순이다', () => {
    const items = [item({ id: 3 }), item({ id: 1 }), item({ id: 2 })]
    expect(sortItems(items, 'REGISTERED').map((i) => i.id)).toEqual([1, 2, 3])
  })

  it('노출도는 높은 순이고 미지정은 뒤로 보낸다', () => {
    const items = [
      item({ id: 1, exposure: 2 }),
      item({ id: 2, exposure: null }),
      item({ id: 3, exposure: 9 }),
      item({ id: 4, exposure: 6 }),
    ]
    expect(sortItems(items, 'EXPOSURE').map((i) => i.id)).toEqual([3, 4, 1, 2])
  })

  it('기한은 임박한 순이고 기한 없음은 뒤로 보낸다', () => {
    const items = [
      item({ id: 1, dueDate: '2026-10-01' }),
      item({ id: 2, dueDate: null }),
      item({ id: 3, dueDate: '2026-08-01' }),
    ]
    expect(sortItems(items, 'DUE').map((i) => i.id)).toEqual([3, 1, 2])
  })

  it('같은 값이면 id 순으로 안정적이다', () => {
    const items = [
      item({ id: 3, exposure: 6 }),
      item({ id: 1, exposure: 6 }),
      item({ id: 2, exposure: 6 }),
    ]
    expect(sortItems(items, 'EXPOSURE').map((i) => i.id)).toEqual([1, 2, 3])
  })

  it('입력 배열을 바꾸지 않는다', () => {
    const items = [item({ id: 2 }), item({ id: 1 })]
    sortItems(items, 'REGISTERED')
    expect(items.map((i) => i.id)).toEqual([2, 1])
  })
})

describe('필터 + 정렬 조합', () => {
  it('필터를 먼저 적용하고 정렬한다', () => {
    const items = [
      item({ id: 1, type: 'RISK', exposure: 2, status: 'CLOSED' }),
      item({ id: 2, type: 'RISK', exposure: 9 }),
      item({ id: 3, type: 'ISSUE', exposure: 6 }),
      item({ id: 4, type: 'RISK', exposure: 4 }),
    ]

    const result = applyFilters(items, filters({ type: 'RISK', status: 'OPEN_ONLY', sort: 'EXPOSURE' }))

    expect(result.map((i) => i.id)).toEqual([2, 4])
  })
})

describe('보조 함수', () => {
  it('기본 필터는 필터가 걸리지 않은 상태다', () => {
    expect(isFiltered(DEFAULT_FILTERS)).toBe(false)
    expect(isFiltered(filters({ status: 'OPEN_ONLY' }))).toBe(true)
    expect(isFiltered(filters({ query: '  ' }))).toBe(false)
  })

  it('정렬만 바꾸는 것은 필터가 아니다', () => {
    expect(isFiltered(filters({ sort: 'DUE' }))).toBe(false)
  })

  it('종류별 건수는 필터와 무관하게 전체를 센다', () => {
    const items = [
      item({ id: 1, type: 'RISK' }),
      item({ id: 2, type: 'RISK' }),
      item({ id: 3, type: 'DEPENDENCY' }),
    ]
    expect(typeCounts(items)).toEqual([
      { type: 'RISK', count: 2 },
      { type: 'ASSUMPTION', count: 0 },
      { type: 'ISSUE', count: 0 },
      { type: 'DEPENDENCY', count: 1 },
    ])
  })
})

/**
 * 필터↔주소 변환 — RaidView가 이 값으로 뒤로가기·북마크·공유를 지원한다(CLAUDE.md "URL에 화면
 * 상태" 요구, DashboardView의 `?tab=`과 같은 패턴). 서버로는 여전히 아무것도 보내지 않는다 —
 * 이건 화면 상태를 주소창 문자열로 바꾸는 순수 변환일 뿐이다.
 */
describe('filtersToQuery / filtersFromQuery', () => {
  it('기본 필터는 빈 쿼리가 된다 — 아무것도 안 걸었을 때 주소가 깨끗해야 한다', () => {
    expect(filtersToQuery(DEFAULT_FILTERS)).toEqual({})
  })

  it('기본값과 다른 항목만 쿼리에 담는다', () => {
    expect(filtersToQuery(filters({ status: 'OPEN_ONLY' }))).toEqual({ status: 'OPEN_ONLY' })
    expect(filtersToQuery(filters({ type: 'RISK', sort: 'DUE' }))).toEqual({
      type: 'RISK',
      sort: 'DUE',
    })
  })

  it('검색어는 앞뒤 공백만 있으면 쿼리에 넣지 않는다', () => {
    expect(filtersToQuery(filters({ query: '  ' }))).toEqual({})
    expect(filtersToQuery(filters({ query: '서버' }))).toEqual({ q: '서버' })
  })

  it('빈 쿼리에서는 기본 필터로 돌아온다', () => {
    expect(filtersFromQuery({})).toEqual(DEFAULT_FILTERS)
  })

  it('알려진 값은 그대로 복원한다', () => {
    expect(filtersFromQuery({ type: 'RISK', status: 'OPEN_ONLY', sort: 'DUE', q: '서버' })).toEqual(
      filters({ type: 'RISK', status: 'OPEN_ONLY', sort: 'DUE', query: '서버' }),
    )
  })

  it('모르는 값(직접 주소를 편집했거나 예전 값)은 조용히 기본값으로 떨어진다', () => {
    expect(filtersFromQuery({ type: 'NO_SUCH_TYPE', status: 'NO_SUCH_STATUS', sort: 'NO_SUCH_SORT' })).toEqual(
      DEFAULT_FILTERS,
    )
  })

  it('배열 값(같은 키를 여러 번 준 주소)은 문자열이 아니므로 기본값으로 떨어진다', () => {
    expect(filtersFromQuery({ type: ['RISK', 'ISSUE'] })).toEqual(DEFAULT_FILTERS)
  })

  it('왕복해도 값이 그대로다', () => {
    const original = filters({ type: 'ISSUE', status: 'CLOSED', sort: 'EXPOSURE', query: '지연' })
    expect(filtersFromQuery(filtersToQuery(original))).toEqual(original)
  })
})
