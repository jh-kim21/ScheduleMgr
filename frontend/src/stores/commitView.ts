import { computed, ref } from 'vue'
import type { CommitPayload } from '../api/commitApi'

/**
 * The commit a screen is currently pinned to, in the shape every screen needs to render its
 * banner and to tell the live API client from the commit one.
 */
export interface ActiveCommit {
  projectId: number
  id: number
  version: number
  asOf: string
  message: string | null
  committedBy: string | null
  formatVersion: number
}

/**
 * Module scope, like `useProjects`/`useWbs` — a screen switch must not drop out of commit view.
 * A `ref` declared inside a component would reset to `null` on every mount, and the banner
 * (mounted once in `App.vue`) would never see it change.
 */
export const activeCommit = ref<ActiveCommit | null>(null)

/** The activated commit's whole payload; `null` outside commit view. */
export const commitPayload = ref<CommitPayload | null>(null)

/**
 * The one flag every write entry point across the nine screens checks. Keeping it a plain
 * derivation of `activeCommit` — rather than a second boolean someone could forget to flip — means
 * there is exactly one way to be in commit view and exactly one way to leave it.
 */
export const readOnly = computed(() => activeCommit.value !== null)

export function enterCommitView(commit: ActiveCommit, payload: CommitPayload) {
  activeCommit.value = commit
  commitPayload.value = payload
}

export function exitCommitView() {
  activeCommit.value = null
  commitPayload.value = null
}
