import { ref } from 'vue'
import { ApiError } from '../../api/http'
import {
  wbsApi,
  type WbsImportInput,
  type WbsItemInput,
  type WbsMoveInput,
  type WbsNode,
  type WbsTree,
} from '../../api/wbsApi'
import { markWbsChanged, wbsCacheKeyFor } from '../../stores/scheduleCache'
import { activeCommit, commitPayload, readOnly } from '../../stores/commitView'

/** Shared at module scope so the tree survives navigating away and back. */
const tree = ref<WbsNode[]>([])
const referenceDate = ref<string | null>(null)
const loading = ref(false)
const error = ref<string | null>(null)
/** Which (project, revision, day) the cached tree belongs to; null when nothing is cached. */
let cacheKey: string | null = null
/**
 * The project `tree` currently belongs to; null when nothing is loaded. Tracked separately from
 * `cacheKey` (which also changes on a same-project revision bump) because `load()` needs to answer
 * a narrower question: "is this a *different* project, or just a fresher read of the same one?"
 *
 * `showsLoadingInsteadOfTree` keeps the tree on screen while `loading` is true so a checkpoint
 * save does not blank the tree mid-refetch (지시서 §3). That only holds while the refetch is for
 * the *same* project — switching projects while a fetch is in flight would otherwise render the
 * previous project's rows (and its `referenceDate`) under the new selection until the response
 * lands. The 404 on a stray click is caught server-side (`WbsService.requireItemOfProject`), but
 * showing another project's data at all is still a lie the screen shouldn't tell. So `load()`
 * clears the tree immediately when the target project differs from this, before awaiting anything.
 */
let loadedProjectId: number | null = null
/** The request currently in flight, so concurrent callers share one fetch. */
let inFlight: { key: string; promise: Promise<void> } | null = null

export function useWbs() {
  function describe(e: unknown, fallback: string) {
    return e instanceof ApiError ? e.message : fallback
  }

  /** Commits are immutable, so a cache key of the commit id alone is enough (지시서 5.2). */
  function currentCacheKey(projectId: number): string {
    return readOnly.value && activeCommit.value ? `commit:${activeCommit.value.id}` : wbsCacheKeyFor(projectId)
  }

  function apply(result: WbsTree, projectId: number) {
    tree.value = result.nodes
    referenceDate.value = result.referenceDate
    cacheKey = currentCacheKey(projectId)
    loadedProjectId = projectId
  }

  async function load(projectId: number) {
    // 다른 프로젝트로 전환하는 요청이면 응답이 오기 전에 즉시 비운다 — 그대로 두면
    // `showsLoadingInsteadOfTree`가 "행이 있으니 갱신 중"으로 읽어 이전 프로젝트의 트리를 새
    // 선택 아래 계속 그린다(§3 회귀). 같은 프로젝트를 다시 읽는 것이면 비우지 않아야 §3이 고친
    // 깜빡임이 돌아오지 않는다.
    if (loadedProjectId !== null && loadedProjectId !== projectId) {
      tree.value = []
      referenceDate.value = null
    }
    loading.value = true
    error.value = null
    try {
      // 커밋 조회 중에는 네트워크 대신 활성 커밋의 payload를 그대로 쓴다 — computed_payload가
      // 라이브 응답과 같은 shape이므로 apply() 를 그대로 재사용할 수 있다.
      if (readOnly.value && commitPayload.value) {
        apply(commitPayload.value.wbs, projectId)
        return
      }
      apply(await wbsApi.tree(projectId), projectId)
    } catch (e) {
      tree.value = []
      referenceDate.value = null
      cacheKey = null
      loadedProjectId = null
      error.value = describe(e, 'WBS를 불러오지 못했습니다.')
    } finally {
      loading.value = false
    }
  }

  /** Refetches only when the cached tree is for another project, another day, or now stale. */
  function ensureLoaded(projectId: number): Promise<void> {
    const key = currentCacheKey(projectId)
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
   *
   * Returns whether the server accepted the change. A rejected save has to leave the input dialog
   * open with the draft in it, so the caller needs to know.
   */
  async function mutate(
    projectId: number,
    action: () => Promise<WbsTree>,
    fallback: string,
  ): Promise<boolean> {
    // 쓰기 진입점은 화면에서 이미 막혀 있어야 하지만, 여기서도 한 번 더 막는다 — 커밋 조회 중에는
    // 서버에 아무것도 보내지 않는다.
    if (readOnly.value) return false
    error.value = null
    try {
      const result = await action()
      markWbsChanged()
      apply(result, projectId)
      return true
    } catch (e) {
      error.value = describe(e, fallback)
      return false
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

  const importFile = (projectId: number, input: WbsImportInput) =>
    mutate(projectId, () => wbsApi.importFile(projectId, input), '파일을 가져오지 못했습니다.')

  return {
    tree,
    referenceDate,
    loading,
    error,
    load,
    ensureLoaded,
    create,
    update,
    move,
    remove,
    importFile,
  }
}
