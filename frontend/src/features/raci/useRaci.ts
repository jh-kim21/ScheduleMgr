import { computed, ref } from 'vue'
import { ApiError } from '../../api/http'
import { memberApi, type MemberInput } from '../../api/memberApi'
import { raciApi, type RaciAssignmentInput, type RaciMatrix } from '../../api/raciApi'
import { raciCacheKeyFor } from '../../stores/scheduleCache'
import type { RaciRole } from '../../shared/raci'

const EMPTY: RaciMatrix = { members: [], tasks: [], cells: [], issues: [] }

/** Shared at module scope so the matrix survives navigating away and back. */
const data = ref<RaciMatrix>(EMPTY)
const loading = ref(false)
const error = ref<string | null>(null)
let cacheKey: string | null = null
/** The request currently in flight, so concurrent callers share one fetch. */
let inFlight: { key: string; promise: Promise<void> } | null = null

export function useRaci() {
  function describe(e: unknown, fallback: string) {
    return e instanceof ApiError ? e.message : fallback
  }

  function apply(result: RaciMatrix, projectId: number) {
    data.value = result
    cacheKey = raciCacheKeyFor(projectId)
  }

  async function load(projectId: number) {
    loading.value = true
    error.value = null
    try {
      apply(await raciApi.matrix(projectId), projectId)
    } catch (e) {
      data.value = EMPTY
      cacheKey = null
      error.value = describe(e, 'RACI 매트릭스를 불러오지 못했습니다.')
    } finally {
      loading.value = false
    }
  }

  /** Refetches only when the cached matrix is for another project or the WBS has changed. */
  function ensureLoaded(projectId: number): Promise<void> {
    const key = raciCacheKeyFor(projectId)
    if (cacheKey === key) return Promise.resolve()
    // A route change can mount a view and fire its selection watcher in the same tick; without
    // this both would issue the same request.
    if (inFlight?.key === key) return inFlight.promise
    const promise = load(projectId).finally(() => {
      if (inFlight?.key === key) inFlight = null
    })
    inFlight = { key, promise }
    return promise
  }

  async function mutate(projectId: number, action: () => Promise<RaciMatrix>, fallback: string) {
    error.value = null
    try {
      apply(await action(), projectId)
    } catch (e) {
      error.value = describe(e, fallback)
    }
  }

  const assign = (projectId: number, input: RaciAssignmentInput) =>
    mutate(projectId, () => raciApi.assign(projectId, input), '역할을 배정하지 못했습니다.')

  const unassign = (projectId: number, assignmentId: number) =>
    mutate(projectId, () => raciApi.unassign(projectId, assignmentId), '역할을 해제하지 못했습니다.')

  /**
   * The member endpoints return only the member, so the matrix is reloaded afterwards — its
   * columns come from that list, and a removed member takes its assignments with it.
   */
  async function withMemberChange(
    projectId: number,
    action: () => Promise<unknown>,
    fallback: string,
  ) {
    error.value = null
    try {
      await action()
    } catch (e) {
      error.value = describe(e, fallback)
      return
    }
    apply(await raciApi.matrix(projectId), projectId)
  }

  const addMember = (projectId: number, input: MemberInput) =>
    withMemberChange(projectId, () => memberApi.create(projectId, input), '구성원을 추가하지 못했습니다.')

  const updateMember = (projectId: number, memberId: number, input: MemberInput) =>
    withMemberChange(
      projectId,
      () => memberApi.update(projectId, memberId, input),
      '구성원을 수정하지 못했습니다.',
    )

  const removeMember = (projectId: number, memberId: number) =>
    withMemberChange(
      projectId,
      () => memberApi.remove(projectId, memberId),
      '구성원을 삭제하지 못했습니다.',
    )

  /** Cell lookup keyed by `wbsItemId:memberId`, built once per matrix rather than per cell render. */
  const cellIndex = computed(() => {
    const index = new Map<string, { roles: RaciRole[]; assignmentIds: number[] }>()
    for (const cell of data.value.cells) {
      index.set(`${cell.wbsItemId}:${cell.memberId}`, {
        roles: cell.roles,
        assignmentIds: cell.assignmentIds,
      })
    }
    return index
  })

  /** Issues grouped by row, so the matrix can mark a row without scanning the list each time. */
  const issuesByTask = computed(() => {
    const grouped = new Map<number, typeof data.value.issues>()
    for (const issue of data.value.issues) {
      const existing = grouped.get(issue.wbsItemId)
      if (existing) existing.push(issue)
      else grouped.set(issue.wbsItemId, [issue])
    }
    return grouped
  })

  return {
    data,
    loading,
    error,
    cellIndex,
    issuesByTask,
    load,
    ensureLoaded,
    assign,
    unassign,
    addMember,
    updateMember,
    removeMember,
  }
}
