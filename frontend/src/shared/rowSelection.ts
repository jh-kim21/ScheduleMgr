/**
 * Pure decision functions behind row selection, Up/Down keyboard navigation, and the
 * double-click-to-edit guard — the interaction WbsTree.vue (features/wbs/WbsTree.vue) pioneered
 * and every other table now shares via `useRowSelection.ts`.
 *
 * <p>Split out as plain functions rather than living inside the composable because this repo has
 * no `@vue/test-utils` and no DOM environment (see ganttProgress.ts, wbsFormMapping.ts for the same
 * pattern) — a function that only takes ids and tag-name strings can be exercised by vitest without
 * mounting anything or touching `document`.
 */

/**
 * The id Up (`delta: -1`)/Down (`delta: 1`) should land the selection on.
 *
 * No selection yet resolves to the first row regardless of direction — there is no "previous" row
 * to step from, so both arrows can only mean "start reading from the top". An id that no longer
 * appears in `ids` (a stale selection a caller forgot to reconcile) is treated the same way. Moving
 * past either end is a no-op — the selection stays where it is rather than wrapping around, so
 * repeatedly pressing an arrow key at an edge does not fight a "snap to the other end" surprise. An
 * empty list leaves whatever selection was passed in untouched (there is nothing to move to, and
 * clearing selection is `reconcileSelection`'s job, not this function's).
 */
export function nextSelectionId(
  ids: number[],
  selectedId: number | null,
  delta: 1 | -1,
): number | null {
  if (ids.length === 0) return selectedId
  if (selectedId === null) return ids[0]
  const currentIndex = ids.indexOf(selectedId)
  if (currentIndex === -1) return ids[0]
  const nextIndex = currentIndex + delta
  if (nextIndex < 0 || nextIndex >= ids.length) return selectedId
  return ids[nextIndex]
}

/**
 * Drops a selection once its row is no longer in `ids` — deleted, filtered out, archived out of
 * view. Without this, arrow-key navigation would resume from a row the user can no longer see, and
 * the highlight would linger on a row that has moved on to mean something else (e.g. a reused id in
 * an unrelated list further down the page).
 */
export function reconcileSelection(ids: number[], selectedId: number | null): number | null {
  if (selectedId === null) return null
  return ids.includes(selectedId) ? selectedId : null
}

/**
 * Whether a double-click landing on `ancestorTagNames` should be swallowed instead of opening the
 * row's edit action.
 *
 * <p>`ancestorTagNames` is the chain of tag names from the click target up to (and including) the
 * element the double-click listener is attached to, e.g. `['SPAN', 'BUTTON', 'TD', 'TR']` for a
 * click on text inside a button inside a cell inside the row. A `button`/`a` anywhere in that chain
 * already has its own single-click behaviour (WBS's 하위/수정/삭제, a Backlog link, …) — a
 * double-click on it fires two clicks *and* a dblclick, so without this guard the row's edit dialog
 * would open *in addition to* whatever the control itself just did.
 */
export function isDoubleClickGuarded(ancestorTagNames: string[]): boolean {
  return ancestorTagNames.includes('BUTTON') || ancestorTagNames.includes('A')
}
