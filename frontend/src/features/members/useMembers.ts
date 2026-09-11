import { ref } from 'vue'
import { ApiError } from '../../api/http'
import { memberApi, type MemberInput, type ProjectMember } from '../../api/memberApi'
import { markMembersChanged } from '../../stores/scheduleCache'

/**
 * Shared at module scope so reopening the member dialog for the same project (or navigating away
 * and back) does not refetch. Unlike `useWbs`/`useRaci`, there is no single "current project" here —
 * the Projects screen opens this per row — so the cache key is just the project id, no revision or
 * date needed: nothing here is judged against "today", and this list *is* the source of truth other
 * screens invalidate against (see `markMembersChanged`).
 */
const members = ref<ProjectMember[]>([])
const loading = ref(false)
const error = ref<string | null>(null)
/** Which project's list is cached; null when nothing is cached. */
let cacheProjectId: number | null = null
/** The request currently in flight, so concurrent callers share one fetch. */
let inFlight: { projectId: number; promise: Promise<void> } | null = null

export function useMembers() {
  function describe(e: unknown, fallback: string) {
    return e instanceof ApiError ? e.message : fallback
  }

  async function load(projectId: number) {
    loading.value = true
    error.value = null
    try {
      members.value = await memberApi.list(projectId)
      cacheProjectId = projectId
    } catch (e) {
      members.value = []
      cacheProjectId = null
      error.value = describe(e, '구성원 목록을 불러오지 못했습니다.')
    } finally {
      loading.value = false
    }
  }

  /** Refetches only when the cached list is for another project. */
  function ensureLoaded(projectId: number): Promise<void> {
    if (cacheProjectId === projectId) return Promise.resolve()
    // A route change can mount a view and fire its selection watcher in the same tick; without
    // this both would issue the same request.
    if (inFlight?.projectId === projectId) return inFlight.promise
    const promise = load(projectId).finally(() => {
      if (inFlight?.projectId === projectId) inFlight = null
    })
    inFlight = { projectId, promise }
    return promise
  }

  /**
   * Runs a mutation, then marks members as changed so RACI/dashboard/RAID refetch (they show member
   * names but have no way to know this list moved otherwise — see `markMembersChanged`).
   *
   * Returns whether the server accepted the change, so a rejected save can leave the dialog open
   * with the draft still in it.
   */
  async function mutate(action: () => Promise<void>, fallback: string): Promise<boolean> {
    error.value = null
    try {
      await action()
      markMembersChanged()
      return true
    } catch (e) {
      error.value = describe(e, fallback)
      return false
    }
  }

  const create = (projectId: number, input: MemberInput) =>
    mutate(async () => {
      const created = await memberApi.create(projectId, input)
      members.value = [...members.value, created]
    }, '구성원을 추가하지 못했습니다.')

  const update = (projectId: number, memberId: number, input: MemberInput) =>
    mutate(async () => {
      const updated = await memberApi.update(projectId, memberId, input)
      members.value = members.value.map((member) => (member.id === memberId ? updated : member))
    }, '구성원을 수정하지 못했습니다.')

  const remove = (projectId: number, memberId: number) =>
    mutate(async () => {
      await memberApi.remove(projectId, memberId)
      members.value = members.value.filter((member) => member.id !== memberId)
    }, '구성원을 삭제하지 못했습니다.')

  return { members, loading, error, load, ensureLoaded, create, update, remove }
}
