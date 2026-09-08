import type { BacklogItem } from '../../api/backlogApi'
import type { BacklogItemType, BacklogStatus } from '../../shared/backlog'

/**
 * Filtering for the Backlog screen, done on the client for the same reasons the RAID log is: the
 * backlog is one screenful, and this is a question about "the view right now" rather than about the
 * data. Sending it to the server would add a round trip per dropdown and a cache key per
 * combination. Pure functions, so vitest can pin the rules.
 */

export type LinkFilter = 'ALL' | 'LINKED' | 'UNLINKED'
export type ArchiveFilter = 'ACTIVE' | 'ARCHIVED' | 'ALL'

export interface BacklogFilters {
  /** A specific Work Package, or null for every one. Set when arriving from the WBS screen. */
  wbsItemId: number | null
  type: BacklogItemType | 'ALL'
  status: BacklogStatus | 'ALL'
  link: LinkFilter
  archive: ArchiveFilter
}

export const DEFAULT_FILTERS: BacklogFilters = {
  wbsItemId: null,
  type: 'ALL',
  status: 'ALL',
  link: 'ALL',
  // 보관은 접어둔 것이므로 기본으로 감춘다. 목록에 섞이면 열린 일이 실제보다 많아 보인다.
  archive: 'ACTIVE',
}

export const LINK_FILTER_LABELS: Record<LinkFilter, string> = {
  ALL: '연결 여부 전체',
  LINKED: '연결됨',
  UNLINKED: '미연결',
}

export const ARCHIVE_FILTER_LABELS: Record<ArchiveFilter, string> = {
  ACTIVE: '보관 제외',
  ARCHIVED: '보관만',
  ALL: '보관 포함',
}

export const LINK_FILTER_ORDER: LinkFilter[] = ['ALL', 'LINKED', 'UNLINKED']
export const ARCHIVE_FILTER_ORDER: ArchiveFilter[] = ['ACTIVE', 'ARCHIVED', 'ALL']

export function isFiltered(filters: BacklogFilters): boolean {
  return (
    filters.wbsItemId !== null ||
    filters.type !== DEFAULT_FILTERS.type ||
    filters.status !== DEFAULT_FILTERS.status ||
    filters.link !== DEFAULT_FILTERS.link ||
    filters.archive !== DEFAULT_FILTERS.archive
  )
}

/**
 * Applies the filters, keeping the server's order.
 *
 * <p>Ancestors of a matching row are kept even when they do not match themselves, so a Task never
 * appears with no sign of the Story it belongs to. They are marked so the screen can render them
 * as context rather than as results.
 */
export function visibleRows(items: BacklogItem[], filters: BacklogFilters): BacklogRow[] {
  const matched = new Set(items.filter((item) => matches(item, filters)).map((item) => item.id))
  if (matched.size === 0) return []

  const byId = new Map(items.map((item) => [item.id, item]))
  const kept = new Set<number>(matched)
  for (const id of matched) {
    let parentId = byId.get(id)?.parentId ?? null
    while (parentId !== null && !kept.has(parentId)) {
      kept.add(parentId)
      parentId = byId.get(parentId)?.parentId ?? null
    }
  }

  return items
    .filter((item) => kept.has(item.id))
    .map((item) => ({ item, context: !matched.has(item.id) }))
}

/** @param context kept only to explain a matching descendant, not a result itself */
export interface BacklogRow {
  item: BacklogItem
  context: boolean
}

function matches(item: BacklogItem, filters: BacklogFilters): boolean {
  if (filters.archive === 'ACTIVE' && item.archived) return false
  if (filters.archive === 'ARCHIVED' && !item.archived) return false
  if (filters.wbsItemId !== null && item.wbsItemId !== filters.wbsItemId) return false
  if (filters.type !== 'ALL' && item.itemType !== filters.type) return false
  if (filters.status !== 'ALL' && item.status !== filters.status) return false
  if (filters.link === 'LINKED' && item.wbsItemId === null) return false
  if (filters.link === 'UNLINKED' && item.wbsItemId !== null) return false
  return true
}
