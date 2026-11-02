import type { TodoItem } from '../../api/todoApi'
import { TODO_PRIORITY_ORDER, type TodoPriority } from '../../shared/todo'

/**
 * 필터·정렬은 클라이언트의 순수 함수다(`raidFilter.ts`와 같은 이유): 한 화면 분량의 목록이고,
 * "지금 이 화면"에 대한 질문이라 서버로 보내면 조합마다 왕복과 캐시 키가 필요해진다.
 */
export interface TodoFilters {
  /** `ALL` 전체, `NONE` 담당자 미지정, 숫자는 구성원 id. */
  assignee: 'ALL' | 'NONE' | number
  priority: TodoPriority | 'ALL'
  overdueOnly: boolean
  /** 완료 항목은 기본으로 숨긴다. */
  includeCompleted: boolean
}

export const DEFAULT_FILTERS: TodoFilters = {
  assignee: 'ALL',
  priority: 'ALL',
  overdueOnly: false,
  includeCompleted: false,
}

const PRIORITY_RANK: Record<TodoPriority, number> = Object.fromEntries(
  TODO_PRIORITY_ORDER.map((priority, index) => [priority, index]),
) as Record<TodoPriority, number>

/**
 * 미완료 → 우선순위(높음 → 낮음) → 기한(빠른 순, 없는 항목은 뒤) → id.
 * 마지막을 id로 두어 편집 중에 행이 튀지 않는다. 입력을 바꾸지 않고 복사본을 돌려준다.
 */
export function sortItems(items: TodoItem[]): TodoItem[] {
  return items.slice().sort((a, b) => {
    if (a.completed !== b.completed) return a.completed ? 1 : -1
    if (a.priority !== b.priority) return PRIORITY_RANK[a.priority] - PRIORITY_RANK[b.priority]
    if (a.dueDate !== b.dueDate) {
      if (a.dueDate === null) return 1
      if (b.dueDate === null) return -1
      return a.dueDate < b.dueDate ? -1 : 1
    }
    return a.id - b.id
  })
}

export function filterItems(items: TodoItem[], filters: TodoFilters): TodoItem[] {
  return items.filter((item) => {
    if (!filters.includeCompleted && item.completed) return false
    if (filters.priority !== 'ALL' && item.priority !== filters.priority) return false
    if (filters.assignee === 'NONE' && item.assigneeMemberId !== null) return false
    if (typeof filters.assignee === 'number' && item.assigneeMemberId !== filters.assignee) return false
    if (filters.overdueOnly && !item.overdue) return false
    return true
  })
}

export function applyFilters(items: TodoItem[], filters: TodoFilters): TodoItem[] {
  return sortItems(filterItems(items, filters))
}

/** 화면이 "일부만 보이고 있다"고 말해야 하는지. 완료 숨김은 기본값이라 필터로 치지 않는다. */
export function isFiltered(filters: TodoFilters): boolean {
  return filters.assignee !== 'ALL' || filters.priority !== 'ALL' || filters.overdueOnly
}

/** 배너용 — 필터와 무관하게 전체에서 센다. 완료 항목은 서버가 이미 초과로 보지 않는다. */
export function overdueItems(items: TodoItem[]): TodoItem[] {
  return items.filter((item) => item.overdue)
}
