import type { RaidItem } from '../../api/raidApi'
import { RAID_TYPE_ORDER, type RaidStatus, type RaidType } from '../../shared/raid'

/**
 * Filtering and sorting the register happens on the client: the log is a page-sized list, and
 * these are questions about *this view* rather than about the data — pushing them to the server
 * would mean a round trip for every dropdown change and a cache key per combination.
 */

/** `OPEN_ONLY` is the useful default filter: it hides closed entries without hiding a status. */
export type RaidStatusFilter = 'ALL' | 'OPEN_ONLY' | RaidStatus

export type RaidSort = 'REGISTERED' | 'EXPOSURE' | 'DUE'

export interface RaidFilters {
  type: RaidType | 'ALL'
  status: RaidStatusFilter
  sort: RaidSort
  /** Free text, matched against title, description and owner name. */
  query: string
}

export const DEFAULT_FILTERS: RaidFilters = {
  type: 'ALL',
  status: 'ALL',
  sort: 'REGISTERED',
  query: '',
}

export const SORT_LABELS: Record<RaidSort, string> = {
  REGISTERED: '등록순',
  EXPOSURE: '노출도 높은 순',
  DUE: '기한 임박순',
}

export const STATUS_FILTER_LABELS: Record<RaidStatusFilter, string> = {
  ALL: '전체 상태',
  OPEN_ONLY: '미종결만',
  OPEN: '열림',
  IN_PROGRESS: '대응중',
  CLOSED: '종결',
}

export const STATUS_FILTER_ORDER: RaidStatusFilter[] = [
  'ALL',
  'OPEN_ONLY',
  'OPEN',
  'IN_PROGRESS',
  'CLOSED',
]

export const SORT_ORDER: RaidSort[] = ['REGISTERED', 'EXPOSURE', 'DUE']

function matchesQuery(item: RaidItem, query: string): boolean {
  const needle = query.trim().toLowerCase()
  if (needle.length === 0) return true
  return [item.title, item.description, item.ownerName]
    .filter((field): field is string => typeof field === 'string')
    .some((field) => field.toLowerCase().includes(needle))
}

function matchesStatus(item: RaidItem, status: RaidStatusFilter): boolean {
  if (status === 'ALL') return true
  if (status === 'OPEN_ONLY') return item.status !== 'CLOSED'
  return item.status === status
}

/**
 * Sorts a copy, never the input. Entries missing the sort key go last rather than first: a risk
 * with no due date is not more urgent than one due tomorrow, and an unrated risk is not the most
 * exposed. Ties fall back to id so a row never jumps while it is being edited.
 */
export function sortItems(items: RaidItem[], sort: RaidSort): RaidItem[] {
  const byId = (a: RaidItem, b: RaidItem) => a.id - b.id

  if (sort === 'EXPOSURE') {
    return items.slice().sort((a, b) => {
      if (a.exposure === b.exposure) return byId(a, b)
      if (a.exposure === null) return 1
      if (b.exposure === null) return -1
      return b.exposure - a.exposure
    })
  }

  if (sort === 'DUE') {
    return items.slice().sort((a, b) => {
      if (a.dueDate === b.dueDate) return byId(a, b)
      if (a.dueDate === null) return 1
      if (b.dueDate === null) return -1
      return a.dueDate < b.dueDate ? -1 : 1
    })
  }

  return items.slice().sort(byId)
}

export function filterItems(items: RaidItem[], filters: RaidFilters): RaidItem[] {
  return items.filter(
    (item) =>
      (filters.type === 'ALL' || item.type === filters.type) &&
      matchesStatus(item, filters.status) &&
      matchesQuery(item, filters.query),
  )
}

export function applyFilters(items: RaidItem[], filters: RaidFilters): RaidItem[] {
  return sortItems(filterItems(items, filters), filters.sort)
}

/** True when the view is showing less than everything, so the screen can say so. */
export function isFiltered(filters: RaidFilters): boolean {
  return (
    filters.type !== 'ALL' || filters.status !== 'ALL' || filters.query.trim().length > 0
  )
}

/** Type options for the filter bar, with the counts of the *unfiltered* register. */
export function typeCounts(items: RaidItem[]): { type: RaidType; count: number }[] {
  return RAID_TYPE_ORDER.map((type) => ({
    type,
    count: items.filter((item) => item.type === type).length,
  }))
}

/**
 * 필터·정렬·검색을 주소(쿼리 문자열)에 반영한다 — 북마크·공유·뒤로가기가 가능해야 한다
 * (`DashboardView`의 `?tab=`과 같은 패턴, CLAUDE.md "URL에 화면 상태" 요구). 기본값과 같은
 * 항목은 아예 넣지 않는다 — 필터를 하나도 안 걸었을 때 주소가 깨끗해야 한다.
 *
 * <p>여기 있는 것은 화면 상태를 주소로 바꾸는 순수 변환일 뿐이다 — RAID 필터 자체는 여전히
 * 클라이언트 순수 함수(위 `filterItems`/`sortItems`)이고 서버로 보내지 않는다.
 */
export function filtersToQuery(filters: RaidFilters): Record<string, string> {
  const query: Record<string, string> = {}
  if (filters.type !== DEFAULT_FILTERS.type) query.type = filters.type
  if (filters.status !== DEFAULT_FILTERS.status) query.status = filters.status
  if (filters.sort !== DEFAULT_FILTERS.sort) query.sort = filters.sort
  if (filters.query.trim().length > 0) query.q = filters.query
  return query
}

/**
 * 주소에서 필터를 복원한다. 모르는 값(직접 주소를 편집했거나 예전 버전의 값)은 조용히 기본값으로
 * 떨어진다 — 잘못된 쿼리로 화면이 깨지면 안 된다.
 */
export function filtersFromQuery(query: Record<string, unknown>): RaidFilters {
  const type = query.type
  const status = query.status
  const sort = query.sort
  const q = query.q
  return {
    type:
      typeof type === 'string' && (RAID_TYPE_ORDER as string[]).includes(type)
        ? (type as RaidType)
        : DEFAULT_FILTERS.type,
    status:
      typeof status === 'string' && (STATUS_FILTER_ORDER as string[]).includes(status)
        ? (status as RaidStatusFilter)
        : DEFAULT_FILTERS.status,
    sort:
      typeof sort === 'string' && (SORT_ORDER as string[]).includes(sort)
        ? (sort as RaidSort)
        : DEFAULT_FILTERS.sort,
    query: typeof q === 'string' ? q : DEFAULT_FILTERS.query,
  }
}
