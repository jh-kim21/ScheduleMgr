import { ref } from 'vue'
import { localToday } from '../shared/delay'

/**
 * Cache bookkeeping for the schedule screens.
 *
 * Views keep their loaded data across tab switches instead of refetching, which means they need a
 * way to know when that data has gone stale. Two things can invalidate it:
 *
 * 1. **WBS items changed** — dates, structure or progress. This affects both the WBS tree and the
 *    Gantt payload, so any such change bumps {@link wbsRevision}. Note that adding or removing a
 *    dependency does *not* bump it: dependencies never appear in the WBS tree.
 * 2. **The day rolled over** — delay verdicts are relative to a date, so a tab left open overnight
 *    is showing yesterday's judgement. The local date is only used to expire the cache; the date
 *    actually displayed always comes from the server's `referenceDate`.
 */
const revision = ref(0)

export function wbsRevision(): number {
  return revision.value
}

/** Call after any change to WBS items so cached views refetch. */
export function markWbsChanged() {
  revision.value += 1
}

/** Identity of a cached response: same key means the cached data is still good. */
export function cacheKeyFor(projectId: number): string {
  return `${projectId}:${revision.value}:${localToday()}`
}
