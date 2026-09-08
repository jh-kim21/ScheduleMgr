/**
 * Sprint vocabulary, shared rather than owned by the sprint feature: the Backlog screen shows which
 * Sprint an entry sits in, and the Dashboard (Step 7) will report the same values.
 */

export type SprintStatus = 'PLANNED' | 'ACTIVE' | 'CLOSED'

/** What became of one assignment when its Sprint closed. `null` while the Sprint is open. */
export type SprintItemOutcome = 'DONE' | 'CARRIED_OVER' | 'REMOVED'

export const SPRINT_STATUS_LABELS: Record<SprintStatus, string> = {
  PLANNED: '계획',
  ACTIVE: '실행 중',
  CLOSED: '종료',
}

export const SPRINT_OUTCOME_LABELS: Record<SprintItemOutcome, string> = {
  DONE: '완료',
  CARRIED_OVER: '이월',
  REMOVED: '제외',
}

/** `YYYY-MM-DD ~ YYYY-MM-DD`, the way the Sprint list and the Board header read it. */
export function sprintPeriod(startDate: string, endDate: string): string {
  return `${startDate} ~ ${endDate}`
}

/**
 * Days left until the end date, counted inclusively — the same convention the schedule side uses
 * (종료일 포함). Negative once the period has passed; `null` outside an open Sprint's interest.
 */
export function daysRemaining(endDate: string, today: string): number {
  const end = Date.parse(`${endDate}T00:00:00`)
  const now = Date.parse(`${today}T00:00:00`)
  return Math.round((end - now) / 86_400_000)
}

/**
 * How the remaining time reads on a running Sprint. Past the end date it says how far over, since
 * "0일 남음" for a Sprint that ended last week would be a lie.
 */
export function remainingLabel(endDate: string, today: string): string {
  const left = daysRemaining(endDate, today)
  if (left > 0) return `${left}일 남음`
  if (left === 0) return '오늘 종료'
  return `종료일 ${-left}일 경과`
}
