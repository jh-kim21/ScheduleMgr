import { computed, reactive, ref } from 'vue'
import { backlogApi, type Backlog, type BacklogItem, type BacklogItemInput } from '../../api/backlogApi'
import { ApiError } from '../../api/http'
import { memberApi, type ProjectMember } from '../../api/memberApi'
import { wbsApi, type WbsNode } from '../../api/wbsApi'
import { backlogCacheKeyFor, markBacklogChanged } from '../../stores/scheduleCache'
import { DEFAULT_FILTERS, visibleRows, type BacklogFilters } from './backlogFilter'

/**
 * Shared at module scope so the backlog survives navigating away and back, like the other feature
 * composables. Filters live here too: coming back to the screen with the filter you left is the
 * point of keeping the state.
 */
const data = ref<Backlog>({ unlinkedCount: 0, items: [] })
const members = ref<ProjectMember[]>([])
/** Work Packages only — Summary entries cannot own Backlog, so they never belong in the picker. */
const workPackages = ref<WorkPackageOption[]>([])
const loading = ref(false)
const error = ref<string | null>(null)
const filters = reactive<BacklogFilters>({ ...DEFAULT_FILTERS })

let cacheKey: string | null = null
let inFlight: { key: string; promise: Promise<void> } | null = null

export interface WorkPackageOption {
  id: number
  code: string
  name: string
  executionMode: WbsNode['executionMode']
}

export function useBacklog() {
  function describe(e: unknown, fallback: string) {
    return e instanceof ApiError ? e.message : fallback
  }

  function apply(result: Backlog, projectId: number) {
    data.value = result
    cacheKey = backlogCacheKeyFor(projectId)
  }

  /**
   * The backlog needs three things: its own entries, the members who can be assignees, and the
   * Work Packages an entry may be attached to. They are fetched together so the form is never
   * offered a picker that is still empty.
   */
  async function load(projectId: number) {
    loading.value = true
    error.value = null
    try {
      const [backlog, memberList, tree] = await Promise.all([
        backlogApi.list(projectId),
        memberApi.list(projectId),
        wbsApi.tree(projectId),
      ])
      members.value = memberList
      workPackages.value = collectWorkPackages(tree.nodes)
      apply(backlog, projectId)
    } catch (e) {
      data.value = { unlinkedCount: 0, items: [] }
      members.value = []
      workPackages.value = []
      cacheKey = null
      error.value = describe(e, 'Backlog를 불러오지 못했습니다.')
    } finally {
      loading.value = false
    }
  }

  function ensureLoaded(projectId: number): Promise<void> {
    const key = backlogCacheKeyFor(projectId)
    if (cacheKey === key) return Promise.resolve()
    // A route change can mount the view and fire its selection watcher in the same tick.
    if (inFlight?.key === key) return inFlight.promise
    const promise = load(projectId).finally(() => {
      if (inFlight?.key === key) inFlight = null
    })
    inFlight = { key, promise }
    return promise
  }

  /**
   * Runs a mutation, then marks the Backlog as changed so the WBS screen's linked counts refetch.
   * The cache key is refreshed *after* the bump, so this view keeps what the server just returned.
   */
  async function mutate(projectId: number, action: () => Promise<Backlog>, fallback: string) {
    error.value = null
    try {
      const result = await action()
      markBacklogChanged()
      apply(result, projectId)
      return true
    } catch (e) {
      error.value = describe(e, fallback)
      return false
    }
  }

  const create = (projectId: number, input: BacklogItemInput) =>
    mutate(projectId, () => backlogApi.create(projectId, input), '항목을 추가하지 못했습니다.')

  const update = (projectId: number, itemId: number, input: BacklogItemInput) =>
    mutate(projectId, () => backlogApi.update(projectId, itemId, input), '항목을 수정하지 못했습니다.')

  const setArchived = (projectId: number, itemId: number, archived: boolean) =>
    mutate(
      projectId,
      () => backlogApi.setArchived(projectId, itemId, archived),
      archived ? '항목을 보관하지 못했습니다.' : '보관을 해제하지 못했습니다.',
    )

  const remove = (projectId: number, itemId: number) =>
    mutate(projectId, () => backlogApi.remove(projectId, itemId), '항목을 삭제하지 못했습니다.')

  const rows = computed(() => visibleRows(data.value.items, filters))

  /** Entries that may be a parent: an Epic can take Stories, a Story or Bug can take Tasks. */
  const parentOptions = computed(() =>
    data.value.items.filter((item) => item.itemType !== 'TASK' && !item.archived),
  )

  function resetFilters() {
    Object.assign(filters, DEFAULT_FILTERS)
  }

  return {
    data,
    members,
    workPackages,
    loading,
    error,
    filters,
    rows,
    parentOptions,
    load,
    ensureLoaded,
    create,
    update,
    setArchived,
    remove,
    resetFilters,
  }
}

function collectWorkPackages(nodes: WbsNode[]): WorkPackageOption[] {
  const found: WorkPackageOption[] = []
  const walk = (list: WbsNode[]) => {
    for (const node of list) {
      if (node.nodeType === 'WORK_PACKAGE') {
        found.push({
          id: node.id,
          code: node.code,
          name: node.name,
          executionMode: node.executionMode,
        })
      }
      walk(node.children)
    }
  }
  walk(nodes)
  return found
}

export type { BacklogItem }
