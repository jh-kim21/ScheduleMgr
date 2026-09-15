import type { CommitCapacity } from '../../api/commitApi'

/**
 * Pure formatting kept out of the components — see the capacity gauge's "84% (860MB / 1GB)".
 * Rounding and unit choice are a judgment call, not rendering, so a plain function pins the rule
 * with a fast vitest check instead of a mounted-component assertion; the component test only needs
 * to confirm the gauge calls this and shows the result (CLAUDE.md "컴포넌트 테스트").
 *
 * Binary units (1024-based), matching how the ~1GB-per-project limit is talked about in the design
 * doc — there is no earlier convention in this codebase to follow instead.
 */
const UNITS = ['B', 'KB', 'MB', 'GB', 'TB'] as const

export function formatBytes(bytes: number): string {
  if (!Number.isFinite(bytes) || bytes <= 0) return '0B'

  let value = bytes
  let unitIndex = 0
  while (value >= 1024 && unitIndex < UNITS.length - 1) {
    value /= 1024
    unitIndex += 1
  }

  // 10 미만은 소수점 한 자리(예: 1.5GB)로, 그 이상은 반올림한 정수로 — 큰 값에서 소수점은
  // 눈으로 견주는 데 도움이 안 된다.
  const rounded = value >= 10 || unitIndex === 0 ? Math.round(value) : Math.round(value * 10) / 10
  return `${rounded}${UNITS[unitIndex]}`
}

/** `84% (860MB / 1GB)` — 목록 상단 용량 게이지, 대화상자 안 문구가 함께 쓴다. */
export function formatCapacityUsage(capacity: CommitCapacity): string {
  return `${Math.round(capacity.usedPercent)}% (${formatBytes(capacity.usedBytes)} / ${formatBytes(capacity.maxBytes)})`
}
