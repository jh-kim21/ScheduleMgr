import { ref } from 'vue'

/**
 * The project the schedule screens are looking at.
 *
 * Declared at module scope, so every view shares one value: switching between WBS and 간트 keeps
 * the selection instead of snapping back to the first project on each mount.
 */
export const selectedProjectId = ref<number | null>(null)

/** Picks a default only when nothing valid is selected yet, so an existing choice survives. */
export function ensureSelection(availableIds: number[]) {
  const current = selectedProjectId.value
  if (current !== null && availableIds.includes(current)) return
  selectedProjectId.value = availableIds[0] ?? null
}

export function clearSelectionIf(projectId: number) {
  if (selectedProjectId.value === projectId) {
    selectedProjectId.value = null
  }
}
