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

/**
 * Backlog changes are tracked separately from WBS changes.
 *
 * Both matter to the WBS screen — it shows each Work Package's linked-Backlog count — but only the
 * WBS revision matters to the Gantt, which knows nothing about Backlog. Two counters keep a Backlog
 * edit from making the Gantt refetch a payload that cannot have changed.
 */
const backlogRev = ref(0)

/** Sprint and Board changes. Board moves change Backlog statuses, so both screens key on this. */
const sprintRev = ref(0)

export function wbsRevision(): number {
  return revision.value
}

/** Call after any change to WBS items so cached views refetch. */
export function markWbsChanged() {
  revision.value += 1
}

/** Call after any change to Backlog entries: the WBS screen's linked counts come from them. */
export function markBacklogChanged() {
  backlogRev.value += 1
}

/**
 * Identity of a cached Gantt response: same key means the cached data is still good.
 *
 * Since Step 6 the Gantt carries Sprint lanes and the common progress figure, so a Backlog or
 * Sprint edit can change it — which is why both revisions are in the key now. The comment above
 * about dependency edits still holds: they are applied from the response the mutation returns.
 */
export function cacheKeyFor(projectId: number): string {
  return `${projectId}:${revision.value}:${backlogRev.value}:${sprintRev.value}:${localToday()}`
}

/**
 * The WBS tree keys on the Backlog revision as well, since every Work Package row carries a
 * linked-item count that a Backlog edit changes.
 */
export function wbsCacheKeyFor(projectId: number): string {
  return `${projectId}:${revision.value}:${backlogRev.value}:${localToday()}`
}

/**
 * Sprint changes move Board columns, which are Backlog statuses — so a Sprint edit makes the
 * Backlog stale too. Tracked separately from {@link markBacklogChanged} so a Backlog edit does not
 * make the Sprint payload refetch when nothing about the Sprints can have changed.
 */
export function markSprintChanged() {
  sprintRev.value += 1
}

/**
 * The Sprint payload keys on the Backlog revision as well: every card shows an entry's title,
 * status and estimate, and the Sprint aggregates are computed from them.
 */
export function sprintCacheKeyFor(projectId: number): string {
  return `${projectId}:${revision.value}:${backlogRev.value}:${sprintRev.value}`
}

/**
 * The Backlog keys on the WBS revision but not on the date: each row shows its Work Package's code
 * and execution mode, which change when the WBS is renamed, moved or re-moded, and nothing in it is
 * judged against "today". Same shape as {@link raciCacheKeyFor}, for the same reason.
 */
export function backlogCacheKeyFor(projectId: number): string {
  return `${projectId}:${revision.value}:${backlogRev.value}:${sprintRev.value}`
}

/**
 * The progress payload keys on all three: it counts Backlog entries, reads Sprint-driven statuses
 * and rolls up over the WBS tree. The local date is in there because planned progress is measured
 * against today — a tab left open overnight would otherwise show yesterday's plan curve.
 */
export function progressCacheKeyFor(projectId: number): string {
  return `${projectId}:${revision.value}:${backlogRev.value}:${sprintRev.value}:${localToday()}`
}

/**
 * The RACI matrix keys on the WBS and Backlog revisions but not on the date: its rows come from the
 * WBS tree, each row now carries its Backlog 담당자 (Step 6), and nothing in it is judged against
 * "today". Member and assignment changes are made through the RACI screen itself, which applies the
 * response it gets back, so they need no invalidation — the same reasoning that keeps dependency
 * edits out of {@link markWbsChanged}.
 */
export function raciCacheKeyFor(projectId: number): string {
  return `${projectId}:${revision.value}:${backlogRev.value}`
}

/**
 * The dashboard keys on everything, because it reads everything.
 *
 * It composes the progress, Gantt, Sprint, Backlog, RACI and RAID payloads, so any edit on any of
 * those screens can move a card. The local date is in there for the same reason it is in the Gantt
 * key: delay and overdue are judged against a date, and a tab left open overnight would show
 * yesterday's verdicts.
 */
export function dashboardCacheKeyFor(projectId: number): string {
  return `${projectId}:${revision.value}:${backlogRev.value}:${sprintRev.value}:${localToday()}`
}

/**
 * The RAID log keys on all three revisions and the local date.
 *
 * An entry can link to a WBS 업무, a Sprint or a Backlog 항목, and the register shows each target's
 * name — so a rename or a move on any of the three makes it stale. The date is there because
 * overdue-ness is judged against one; the date shown is always the server's `referenceDate`.
 */
export function raidCacheKeyFor(projectId: number): string {
  return `${projectId}:${revision.value}:${backlogRev.value}:${sprintRev.value}:${localToday()}`
}
