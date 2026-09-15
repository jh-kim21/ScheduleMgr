import { ref } from 'vue'
import {
  commitApi,
  type CommitCapacity,
  type CommitDetail,
  type CommitInput,
  type CommitMeta,
} from '../../api/commitApi'
import type { Project } from '../../api/projectApi'
import { ApiError } from '../../api/http'

/**
 * Per-project, like `useMembers` — the Projects screen opens this per row, not for "the current
 * project" (a list screen has no such concept). Module scope so reopening the panel for the same
 * project (or leaving and coming back) does not refetch.
 */
const commits = ref<CommitMeta[]>([])
const capacity = ref<CommitCapacity | null>(null)
const loading = ref(false)
const error = ref<string | null>(null)
let cacheProjectId: number | null = null
/** The request currently in flight, so concurrent callers share one fetch. */
let inFlight: { projectId: number; promise: Promise<void> } | null = null

export function useCommits() {
  function describe(e: unknown, fallback: string) {
    return e instanceof ApiError ? e.message : fallback
  }

  async function load(projectId: number) {
    loading.value = true
    error.value = null
    try {
      const result = await commitApi.list(projectId)
      commits.value = result.commits
      capacity.value = result.capacity
      cacheProjectId = projectId
    } catch (e) {
      commits.value = []
      capacity.value = null
      cacheProjectId = null
      error.value = describe(e, '커밋 목록을 불러오지 못했습니다.')
    } finally {
      loading.value = false
    }
  }

  /** Refetches only when the cached list is for another project. */
  function ensureLoaded(projectId: number): Promise<void> {
    if (cacheProjectId === projectId) return Promise.resolve()
    if (inFlight?.projectId === projectId) return inFlight.promise
    const promise = load(projectId).finally(() => {
      if (inFlight?.projectId === projectId) inFlight = null
    })
    inFlight = { projectId, promise }
    return promise
  }

  /**
   * Like `useProjects`'s `create`/`update`/`remove`, errors are left to propagate — the caller
   * decides what a rejection means (a plain message, or — for 409 — a capacity dialog). Wrapping
   * the throw here would hide which one happened.
   */
  async function create(projectId: number, input: CommitInput): Promise<CommitMeta> {
    const result = await commitApi.create(projectId, input)
    commits.value = [result.commit, ...commits.value]
    capacity.value = result.capacity
    cacheProjectId = projectId
    return result.commit
  }

  async function remove(projectId: number, version: number): Promise<void> {
    const result = await commitApi.remove(projectId, version)
    commits.value = result.commits
    capacity.value = result.capacity
    cacheProjectId = projectId
  }

  function view(projectId: number, version: number): Promise<CommitDetail> {
    return commitApi.get(projectId, version)
  }

  function restore(commitId: number): Promise<Project> {
    return commitApi.restore(commitId)
  }

  return { commits, capacity, loading, error, ensureLoaded, create, remove, view, restore }
}
