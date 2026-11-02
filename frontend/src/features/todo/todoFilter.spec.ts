import { describe, expect, it } from 'vitest'
import type { TodoItem } from '../../api/todoApi'
import {
  applyFilters,
  DEFAULT_FILTERS,
  filterItems,
  isFiltered,
  overdueItems,
  sortItems,
} from './todoFilter'

function todo(id: number, overrides: Partial<TodoItem> = {}): TodoItem {
  return {
    id,
    title: `할 일 ${id}`,
    priority: 'MEDIUM',
    assigneeMemberId: null,
    assigneeName: null,
    dueDate: null,
    memo: null,
    completed: false,
    completedAt: null,
    overdue: false,
    overdueDays: 0,
    createdAt: '2026-01-01T00:00:00',
    updatedAt: '2026-01-01T00:00:00',
    ...overrides,
  }
}

const ids = (items: TodoItem[]) => items.map((item) => item.id)

describe('sortItems', () => {
  it('미완료가 완료보다 앞이다 — 완료 항목은 우선순위가 높아도 뒤로 간다', () => {
    const items = [todo(1, { completed: true, priority: 'HIGH' }), todo(2, { priority: 'LOW' })]
    expect(ids(sortItems(items))).toEqual([2, 1])
  })

  it('우선순위는 높음 → 보통 → 낮음', () => {
    const items = [todo(1, { priority: 'LOW' }), todo(2, { priority: 'HIGH' }), todo(3, { priority: 'MEDIUM' })]
    expect(ids(sortItems(items))).toEqual([2, 3, 1])
  })

  it('같은 우선순위에서는 기한이 빠른 순이고 기한 없는 항목은 뒤로 간다', () => {
    const items = [
      todo(1, { dueDate: null }),
      todo(2, { dueDate: '2026-03-10' }),
      todo(3, { dueDate: '2026-03-01' }),
    ]
    expect(ids(sortItems(items))).toEqual([3, 2, 1])
  })

  it('우선순위가 기한보다 먼저다 — 낮음의 임박한 기한이 높음을 앞서지 않는다', () => {
    const items = [
      todo(1, { priority: 'LOW', dueDate: '2026-03-01' }),
      todo(2, { priority: 'HIGH', dueDate: null }),
    ]
    expect(ids(sortItems(items))).toEqual([2, 1])
  })

  it('동점은 id 순이다 — 편집 중에 행이 튀지 않는다', () => {
    const items = [todo(3), todo(1), todo(2)]
    expect(ids(sortItems(items))).toEqual([1, 2, 3])
  })

  it('입력 배열을 바꾸지 않는다', () => {
    const items = [todo(2), todo(1)]
    sortItems(items)
    expect(ids(items)).toEqual([2, 1])
  })
})

describe('filterItems', () => {
  it('완료 항목은 기본으로 숨기고 완료 포함을 켜면 보인다', () => {
    const items = [todo(1), todo(2, { completed: true })]
    expect(ids(filterItems(items, DEFAULT_FILTERS))).toEqual([1])
    expect(ids(filterItems(items, { ...DEFAULT_FILTERS, includeCompleted: true }))).toEqual([1, 2])
  })

  it('담당자 — 구성원 id, 미지정', () => {
    const items = [todo(1, { assigneeMemberId: 7 }), todo(2, { assigneeMemberId: 8 }), todo(3)]
    expect(ids(filterItems(items, { ...DEFAULT_FILTERS, assignee: 7 }))).toEqual([1])
    expect(ids(filterItems(items, { ...DEFAULT_FILTERS, assignee: 'NONE' }))).toEqual([3])
  })

  it('우선순위와 기한 초과만', () => {
    const items = [todo(1, { priority: 'HIGH', overdue: true }), todo(2, { priority: 'HIGH' }), todo(3, { overdue: true })]
    expect(ids(filterItems(items, { ...DEFAULT_FILTERS, priority: 'HIGH' }))).toEqual([1, 2])
    expect(ids(filterItems(items, { ...DEFAULT_FILTERS, overdueOnly: true }))).toEqual([1, 3])
  })
})

describe('applyFilters', () => {
  it('거른 뒤 정렬한다', () => {
    const items = [todo(1, { priority: 'LOW' }), todo(2, { completed: true }), todo(3, { priority: 'HIGH' })]
    expect(ids(applyFilters(items, DEFAULT_FILTERS))).toEqual([3, 1])
  })
})

describe('isFiltered / overdueItems', () => {
  it('완료 숨김은 기본값이라 필터로 치지 않는다', () => {
    expect(isFiltered(DEFAULT_FILTERS)).toBe(false)
    expect(isFiltered({ ...DEFAULT_FILTERS, includeCompleted: true })).toBe(false)
    expect(isFiltered({ ...DEFAULT_FILTERS, overdueOnly: true })).toBe(true)
    expect(isFiltered({ ...DEFAULT_FILTERS, assignee: 'NONE' })).toBe(true)
  })

  it('기한 초과 건수는 전체 목록에서 센다', () => {
    const items = [todo(1, { overdue: true }), todo(2), todo(3, { overdue: true })]
    expect(ids(overdueItems(items))).toEqual([1, 3])
  })
})
