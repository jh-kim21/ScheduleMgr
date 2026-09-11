import { nextTick, ref, watch, type Ref } from 'vue'
import {
  isDoubleClickGuarded,
  isKeyboardNavGuarded,
  nextSelectionId,
  reconcileSelection,
} from './rowSelection'

export interface RowSelection {
  selectedId: Ref<number | null>
  select: (id: number) => void
  clear: () => void
  isSelected: (id: number) => boolean
  onKeydown: (event: KeyboardEvent) => void
  onRowDblClick: (event: MouseEvent, action: () => void) => void
}

/**
 * Walks from the click target up to (and including) the element the listener is attached to,
 * collecting tag names — the DOM-touching half of the double-click guard. Kept out of
 * `rowSelection.ts` (the decision itself, `isDoubleClickGuarded`, is pure and tested there) because
 * this repo has no DOM environment to exercise `Element.closest`/`parentElement` against.
 */
function ancestorTagNames(event: MouseEvent): string[] {
  const tags: string[] = []
  const container = event.currentTarget as HTMLElement | null
  let node = event.target as HTMLElement | null
  while (node) {
    tags.push(node.tagName)
    if (node === container) break
    node = node.parentElement
  }
  return tags
}

/**
 * Click-to-select, Up/Down keyboard navigation, and a double-click guard — factored out of
 * WbsTree.vue (features/wbs/WbsTree.vue) so every other table shares the same behaviour instead of
 * re-implementing its watchers five times.
 *
 * <p>A factory, not a module-scope singleton like `useProjects`/`useWbs`: row selection is
 * per-screen, throwaway state (it does not need to survive a tab switch the way the selected
 * project does), so call this once inside each consuming component's `<script setup>` and let each
 * call get its own `selectedId` — do not hoist the return value to module scope.
 *
 * @param ids Getter for the ids of the currently selectable rows, in display order (e.g.
 *   `() => rows.value.map((r) => r.id)`). A getter rather than a `Ref` so callers do not need to
 *   wrap an existing `computed` a second time, and so `watch` here always sees the latest list
 *   without the caller re-wiring anything when filtering/paging changes it.
 * @param container The element the keyboard listener lives on and rows are queried under via
 *   `[data-row-id]` — typically a `<tbody ref>` with `tabindex="0"`, as in WbsTree. A wrapping
 *   element works too for a table split across several `<tbody>`s (RaidList sections by RAID type).
 */
export function useRowSelection(ids: () => number[], container: Ref<HTMLElement | null>): RowSelection {
  const selectedId = ref<number | null>(null)

  function select(id: number) {
    selectedId.value = id
  }

  function clear() {
    selectedId.value = null
  }

  function isSelected(id: number): boolean {
    return selectedId.value === id
  }

  // Data changing out from under the selection (delete, filter, archive, a WBS move that drops a
  // row out of the visible tree) must not leave arrow-key navigation resuming from a row that is
  // no longer there.
  watch(ids, (list) => {
    selectedId.value = reconcileSelection(list, selectedId.value)
  })

  // Keyboard navigation can land the selection off-screen; nudge it into view without the
  // smooth/centered treatment a "focus on arrival" effect would use (every key press would
  // otherwise feel sluggish) — the same split WbsTree makes between its `focusId` and `selectedId`.
  watch(selectedId, async (id) => {
    if (id === null) return
    await nextTick()
    const row = container.value?.querySelector(`[data-row-id="${id}"]`)
    row?.scrollIntoView({ block: 'nearest' })
  })

  /** One listener on the container, not per row — same reasoning as WbsTree and the Gantt tooltip. */
  function onKeydown(event: KeyboardEvent) {
    if (event.key !== 'ArrowDown' && event.key !== 'ArrowUp') return
    // A focused form control inside the row (an inline-edit input/select) owns Up/Down for its own
    // value — stepping a number input or opening a select must not also move the row selection.
    if (isKeyboardNavGuarded((event.target as HTMLElement | null)?.tagName ?? '')) return
    event.preventDefault()
    selectedId.value = nextSelectionId(ids(), selectedId.value, event.key === 'ArrowDown' ? 1 : -1)
  }

  /**
   * Runs `action` on double-click, unless the click landed on a nested `button`/`a` that already
   * has its own single-click behaviour (see `isDoubleClickGuarded`).
   */
  function onRowDblClick(event: MouseEvent, action: () => void) {
    if (isDoubleClickGuarded(ancestorTagNames(event))) return
    action()
  }

  return { selectedId, select, clear, isSelected, onKeydown, onRowDblClick }
}
