/**
 * Execution mode and management-unit kind, shared rather than owned by the WBS feature: the Agile,
 * Gantt and Dashboard screens all describe the same values in later steps, and the labels must not
 * drift between them (same reasoning as `shared/delay.ts`).
 */

/**
 * What kind of management unit a WBS entry is, as stored by the server.
 *
 * Not the same question as `WbsNode.summary`, which says whether the dates and progress on a row
 * were rolled up from children. The two agree everywhere except while an entry has been converted
 * to `SUMMARY` but has no children yet.
 */
export type WbsNodeType = 'SUMMARY' | 'WORK_PACKAGE'

/** How a Work Package is executed. `null` is 미지정 — keep using the manually entered progress. */
export type ExecutionMode = 'WATERFALL' | 'AGILE' | 'HYBRID'

/** How the Work Packages under a summary are executed, counted per mode. */
export interface ExecutionModeSummary {
  waterfall: number
  agile: number
  hybrid: number
  /** Work Packages with no mode chosen yet. */
  unspecified: number
}

export const NODE_TYPE_LABELS: Record<WbsNodeType, string> = {
  SUMMARY: 'Summary',
  WORK_PACKAGE: 'Work Package',
}

export const EXECUTION_MODE_LABELS: Record<ExecutionMode, string> = {
  WATERFALL: 'Waterfall',
  AGILE: 'Agile',
  HYBRID: 'Hybrid',
}

/** Order used in dropdowns and in the summary text. */
export const EXECUTION_MODE_ORDER: ExecutionMode[] = ['WATERFALL', 'AGILE', 'HYBRID']

export const UNSPECIFIED_MODE_LABEL = '미지정'

export function executionModeLabel(mode: ExecutionMode | null): string {
  return mode ? EXECUTION_MODE_LABELS[mode] : UNSPECIFIED_MODE_LABEL
}

/**
 * A summary row's execution-mode cell, e.g. `Agile 3 · 미지정 1`.
 *
 * Modes with no Work Packages are left out rather than shown as zero: the point is what the branch
 * actually contains. 미지정 is listed last because it is the absence of a choice, and it is
 * deliberately *not* hidden — an unassigned Work Package is the thing a PM needs to notice.
 * Returns an empty string when there is nothing below to report.
 */
export function executionModeSummaryText(summary: ExecutionModeSummary | null): string {
  if (!summary) return ''
  const parts = EXECUTION_MODE_ORDER.filter((mode) => countOf(summary, mode) > 0).map(
    (mode) => `${EXECUTION_MODE_LABELS[mode]} ${countOf(summary, mode)}`,
  )
  if (summary.unspecified > 0) {
    parts.push(`${UNSPECIFIED_MODE_LABEL} ${summary.unspecified}`)
  }
  return parts.join(' · ')
}

function countOf(summary: ExecutionModeSummary, mode: ExecutionMode): number {
  switch (mode) {
    case 'WATERFALL':
      return summary.waterfall
    case 'AGILE':
      return summary.agile
    case 'HYBRID':
      return summary.hybrid
  }
}
