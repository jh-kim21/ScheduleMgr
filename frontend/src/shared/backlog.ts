/**
 * Product Backlog vocabulary, shared rather than owned by the backlog feature: the WBS screen shows
 * linked counts, and Sprint/Board (Step 4) and the Dashboard (Step 7) will describe the same values.
 * Labels live in one place so the screens cannot drift (same reasoning as `shared/delay.ts`).
 */

export type BacklogItemType = 'EPIC' | 'STORY' | 'BUG' | 'TASK'
export type BacklogPriority = 'HIGH' | 'MEDIUM' | 'LOW'
export type BacklogStatus = 'TODO' | 'IN_PROGRESS' | 'REVIEW' | 'DONE'

export const BACKLOG_TYPE_LABELS: Record<BacklogItemType, string> = {
  EPIC: 'Epic',
  STORY: 'Story',
  BUG: 'Bug',
  TASK: 'Task',
}

export const BACKLOG_PRIORITY_LABELS: Record<BacklogPriority, string> = {
  HIGH: '높음',
  MEDIUM: '보통',
  LOW: '낮음',
}

export const BACKLOG_STATUS_LABELS: Record<BacklogStatus, string> = {
  TODO: '할 일',
  IN_PROGRESS: '진행 중',
  REVIEW: '검토',
  DONE: '완료',
}

/** Declaration order matches the server's, so a dropdown reads like the sorted list. */
export const BACKLOG_TYPE_ORDER: BacklogItemType[] = ['EPIC', 'STORY', 'BUG', 'TASK']
export const BACKLOG_PRIORITY_ORDER: BacklogPriority[] = ['HIGH', 'MEDIUM', 'LOW']
export const BACKLOG_STATUS_ORDER: BacklogStatus[] = ['TODO', 'IN_PROGRESS', 'REVIEW', 'DONE']

/** Which kinds progress will be counted on (Step 5). Epic groups, Task is detail. */
export function aggregatedType(type: BacklogItemType): boolean {
  return type === 'STORY' || type === 'BUG'
}

/** Kinds that may sit directly under a Work Package — everything but Task. */
export const TOP_LEVEL_TYPES: BacklogItemType[] = ['EPIC', 'STORY', 'BUG']

/** How much Backlog hangs off a WBS entry, as the WBS screen shows it. */
export interface BacklogSummary {
  items: number
  done: number
  archived: number
}

/**
 * The WBS row's linked-Backlog cell, e.g. `8개 항목 / 4개 완료` (설계 §4.3).
 *
 * Archived entries are named separately rather than folded into the total: they are not open work,
 * but hiding them entirely would make a Work Package look empty when it is not.
 */
export function backlogSummaryText(summary: BacklogSummary | null): string {
  if (!summary) return ''
  const parts: string[] = []
  if (summary.items > 0) {
    parts.push(`${summary.items}개 항목 / ${summary.done}개 완료`)
  }
  if (summary.archived > 0) {
    parts.push(`보관 ${summary.archived}`)
  }
  return parts.join(' · ')
}
