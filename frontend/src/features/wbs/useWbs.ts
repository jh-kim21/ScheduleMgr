import { ref } from 'vue'
import { ApiError } from '../../api/http'
import { wbsApi, type WbsItemInput, type WbsMoveInput, type WbsNode, type WbsTree } from '../../api/wbsApi'
import { cacheKeyFor, markWbsChanged } from '../../stores/scheduleCache'

/** Shared at module scope so the tree survives navigating away and back. */
const tree = ref<WbsNode[]>([])
const referenceDate = ref<string | null>(null)
const loading = ref(false)
const error = ref<string | null>(null)
/** Which (project, revision, day) the cached tree belongs to; null when nothing is cached. */
let cacheKey: string | null = null
/** The request currently in flight, so concurrent callers share one fetch. */
let inFlight: { key: string; promise: Promise<void> } | null = null

export function useWbs() {
  function describe(e: unknown, fallback: string) {
    return e instanceof ApiError ? e.message : fallback
  }

  function apply(result: WbsTree, projectId: number) {
    tree.value = result.nodes
    referenceDate.value = result.referenceDate
    cacheKey = cacheKeyFor(projectId)
  }

  async function load(projectId: number) {
    loading.value = true
    error.value = null
    try {
      apply(await wbsApi.tree(projectId), projectId)
    } catch (e) {
      tree.value = []
      referenceDate.value = null
      cacheKey = null
      error.value = describe(e, 'WBS를 불러오지 못했습니다.')
    } finally {
      loading.value = false
    }
  }

  /** Refetches only when the cached tree is for another project, another day, or now stale. */
  function ensureLoaded(projectId: number): Promise<void> {
    const key = cacheKeyFor(projectId)
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

  /**
   * Runs a mutation, then marks WBS items as changed so the Gantt view refetches. The cache key is
   * refreshed *after* the bump, so this view keeps the tree the server just returned.
   */
  async function mutate(projectId: number, action: () => Promise<WbsTree>, fallback: string) {
    error.value = null
    try {
      const result = await action()
      markWbsChanged()
      apply(result, projectId)
    } catch (e) {
      error.value = describe(e, fallback)
    }
  }

  const create = (projectId: number, parentId: number | null, input: WbsItemInput) =>
    mutate(projectId, () => wbsApi.create(projectId, parentId, input), '항목을 추가하지 못했습니다.')

  const update = (projectId: number, itemId: number, input: WbsItemInput) =>
    mutate(projectId, () => wbsApi.update(projectId, itemId, input), '항목을 수정하지 못했습니다.')

  const move = (projectId: number, itemId: number, input: WbsMoveInput) =>
    mutate(projectId, () => wbsApi.move(projectId, itemId, input), '항목을 이동하지 못했습니다.')

  const remove = (projectId: number, itemId: number) =>
    mutate(projectId, () => wbsApi.remove(projectId, itemId), '항목을 삭제하지 못했습니다.')

  return { tree, referenceDate, loading, error, load, ensureLoaded, create, update, move, remove }
}
