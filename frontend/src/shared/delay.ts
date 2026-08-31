/**
 * Delay judgement is computed server-side (see `DelayCalculator` on the backend) and travels with
 * both the WBS tree and the Gantt payload. This module owns the shared type and the labels, so the
 * two screens cannot drift into describing the same status differently.
 */

export type DelayStatus =
  | 'UNSCHEDULED'
  | 'NOT_STARTED'
  | 'ON_TRACK'
  | 'AT_RISK'
  | 'DELAYED'
  | 'COMPLETED'

/** The delay fields every schedule-bearing row carries. */
export interface DelayInfo {
  delayStatus: DelayStatus
  expectedProgress: number
  /** Percentage points behind the linear baseline; 0 when on or ahead of schedule. */
  progressGap: number
  /** Days past the planned end date while incomplete; 0 otherwise. */
  delayDays: number
  progress: number
  startDate: string | null
}

export const DELAY_LABELS: Record<DelayStatus, string> = {
  UNSCHEDULED: '일정 미정',
  NOT_STARTED: '시작 전',
  ON_TRACK: '정상',
  AT_RISK: '지연 위험',
  DELAYED: '지연',
  COMPLETED: '완료',
}

/** The statuses worth interrupting the user about, worst first. */
export const ATTENTION_STATUSES: DelayStatus[] = ['DELAYED', 'AT_RISK']

export function needsAttention(row: DelayInfo): boolean {
  return ATTENTION_STATUSES.includes(row.delayStatus)
}

/**
 * One-line explanation of why a row carries its status, used as the badge tooltip. Spelling out
 * the numbers matters here: the status alone does not say whether a task is one day or one month
 * behind.
 */
export function delayDescription(row: DelayInfo): string {
  switch (row.delayStatus) {
    case 'DELAYED':
      return `종료일에서 ${row.delayDays}일 경과, 진행률 ${row.progress}% (${row.progressGap}%p 부족)`
    case 'AT_RISK':
      return `기대 진행률 ${row.expectedProgress}%, 실제 ${row.progress}% (${row.progressGap}%p 부족)`
    case 'ON_TRACK':
      return `기대 진행률 ${row.expectedProgress}%, 실제 ${row.progress}%`
    case 'COMPLETED':
      return '진행률 100%'
    case 'NOT_STARTED':
      return `시작일 ${row.startDate} 이전`
    case 'UNSCHEDULED':
      return '시작일 또는 종료일이 입력되지 않았습니다'
  }
}

/** Short badge text: the label, plus the elapsed days when a task is actually late. */
export function delayBadge(row: DelayInfo): string {
  const label = DELAY_LABELS[row.delayStatus]
  return row.delayStatus === 'DELAYED' ? `${label} ${row.delayDays}일` : label
}

export function countByStatus(rows: DelayInfo[], status: DelayStatus): number {
  return rows.filter((row) => row.delayStatus === status).length
}

/**
 * The browser's date as `YYYY-MM-DD`. Used only to expire cached responses when the day rolls
 * over — the date actually displayed always comes from the server's `referenceDate`.
 */
export function localToday(): string {
  const now = new Date()
  const month = String(now.getMonth() + 1).padStart(2, '0')
  const day = String(now.getDate()).padStart(2, '0')
  return `${now.getFullYear()}-${month}-${day}`
}
