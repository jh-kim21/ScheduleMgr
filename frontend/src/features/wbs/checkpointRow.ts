import type { WbsNode } from '../../api/wbsApi'

/**
 * Whether this row can carry checkpoints — only a Work Package running `WATERFALL`/`HYBRID`
 * (Waterfall progress's denominator is the checkpoint set).
 *
 * Checks `nodeType` in addition to `executionMode` on purpose: a `SUMMARY` retains its
 * pre-conversion execution mode rather than clearing it (CLAUDE.md "실행 방식(Execution Mode)
 * 설계상 알아둘 점"), so a Summary that used to be `WATERFALL` still carries that value. Without
 * this check, that retained value would make checkpoints appear on a row that can no longer have
 * any (`WbsForm` hit the same trap for the same reason).
 */
export function supportsCheckpoints(node: Pick<WbsNode, 'nodeType' | 'executionMode'>): boolean {
  return (
    node.nodeType === 'WORK_PACKAGE' &&
    (node.executionMode === 'WATERFALL' || node.executionMode === 'HYBRID')
  )
}

/**
 * The collapsed-state badge text — shown so the approval state is visible without expanding.
 * `0/0` reads as "체크포인트 없음" rather than "승인 0/0": the row can still be expanded to add
 * one, so the badge should not look like a completed (or broken) fraction.
 */
export function approvalBadge(approved: number, total: number): string {
  if (total === 0) return '체크포인트 없음'
  return `승인 ${approved}/${total}`
}
