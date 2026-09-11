/**
 * Progress vocabulary, shared because every screen reads the same figures: the WBS tree, the
 * progress screen and (from Step 7) the dashboard.
 *
 * <p><b>산정 전은 0%가 아니다.</b> A `null` percentage means the aggregation has nothing to measure
 * yet. Rendering it as 0% would claim no work has been done, which is a different — and usually
 * wrong — statement. Everything here keeps the two apart.
 */

export type ProgressBasis =
  | 'MANUAL'
  | 'AGILE'
  | 'WATERFALL'
  | 'HYBRID'
  | 'ROLLUP'
  | 'LEGACY_ROLLUP'
  | 'NOT_ESTIMABLE'

export type AcceptanceStatus = 'PENDING' | 'ACCEPTED'

export const PROGRESS_BASIS_LABELS: Record<ProgressBasis, string> = {
  MANUAL: '수동 입력',
  AGILE: 'Agile',
  WATERFALL: 'Waterfall',
  HYBRID: 'Hybrid',
  ROLLUP: '가중 집계',
  LEGACY_ROLLUP: '하위 평균',
  NOT_ESTIMABLE: '산정 전',
}

export const ACCEPTANCE_STATUS_LABELS: Record<AcceptanceStatus, string> = {
  PENDING: '인수 대기',
  ACCEPTED: '인수 완료',
}

export const ACCEPTANCE_STATUS_ORDER: AcceptanceStatus[] = ['PENDING', 'ACCEPTED']

/** One line saying where a number came from, for the cell's tooltip. */
export const PROGRESS_BASIS_HINTS: Record<ProgressBasis, string> = {
  MANUAL: '실행 방식을 정하기 전이라 입력한 진행률을 그대로 씁니다.',
  AGILE: '완료된 Story·Bug의 가중치 비율입니다.',
  WATERFALL: '승인된 체크포인트의 가중치 비율입니다.',
  HYBRID: 'Agile 요소와 승인 요소를 합의된 비중으로 합산했습니다.',
  ROLLUP: '직계 하위의 가중치 가중 평균입니다.',
  LEGACY_ROLLUP: '가중치가 입력되지 않아 기존과 같은 하위 평균을 씁니다.',
  NOT_ESTIMABLE: '아직 셀 수 있는 근거가 없습니다. 0%와는 다릅니다.',
}

/** Rounds for display only — the server sends the unrounded value on purpose. */
export function progressText(percent: number | null): string {
  return percent === null ? '산정 전' : `${Math.round(percent)}%`
}

/**
 * Rounds to one decimal place — the precision `varianceText` displays. `varianceTone` reuses this
 * so a value like `0.04` reads as "±0%p" (계획과 같음) and colors as level, never one and the other.
 * Exported so screens building their own variance sentence (DashboardView's "앞섬/뒤짐" text) round
 * the same way instead of writing a second rounding rule that can drift from this one.
 */
export function roundPoints(points: number): number {
  return Math.round(points * 10) / 10
}

/** Percentage points, signed, for a variance figure. `null` stays 미산정. */
export function varianceText(points: number | null): string {
  if (points === null) return '미산정'
  const rounded = roundPoints(points)
  if (rounded === 0) return '±0%p'
  return `${rounded > 0 ? '+' : ''}${rounded}%p`
}

export type VarianceTone = 'ahead' | 'behind' | 'level' | null

/**
 * Which way a variance leans, for coloring. Uses the same rounding as `varianceText` — see
 * `roundPoints` — so the color never contradicts the text (e.g. `0.04` is `level`, not `ahead`).
 */
export function varianceTone(points: number | null): VarianceTone {
  if (points === null) return null
  const rounded = roundPoints(points)
  if (rounded === 0) return 'level'
  return rounded > 0 ? 'ahead' : 'behind'
}

/** Width for a progress bar. 산정 전 draws nothing rather than an empty bar that reads as 0%. */
export function progressBarWidth(percent: number | null): string {
  return percent === null ? '0%' : `${Math.max(0, Math.min(100, percent))}%`
}
