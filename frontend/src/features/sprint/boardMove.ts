import { BACKLOG_STATUS_ORDER, type BacklogStatus } from '../../shared/backlog'

/**
 * Keyboard/non-drag alternative to the Board's card drag-and-drop (WCAG 2.5.7 Dragging Movements,
 * 2.1.1 Keyboard, and native HTML5 DnD does not fire on touch either). Judgement lives here as
 * pure functions; `SprintBoard.vue` renders the result (a `<select>` of target columns, and an
 * `Alt+←/→` shortcut) and reuses the existing `move` emit — no new API, same as the WBS tree's
 * `wbsMove.ts`.
 */

/** Columns this card could move to — every column except the one it is already in. */
export function moveTargets(current: BacklogStatus): BacklogStatus[] {
  return BACKLOG_STATUS_ORDER.filter((status) => status !== current)
}

/** What `Alt+←/→` points at. `null` at either end — the columns do not wrap around. */
export function adjacentStatus(current: BacklogStatus, delta: -1 | 1): BacklogStatus | null {
  const index = BACKLOG_STATUS_ORDER.indexOf(current)
  const nextIndex = index + delta
  if (nextIndex < 0 || nextIndex >= BACKLOG_STATUS_ORDER.length) return null
  return BACKLOG_STATUS_ORDER[nextIndex]
}
