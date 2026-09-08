import { computed, ref } from 'vue'
import { ApiError } from '../../api/http'
import { backlogApi } from '../../api/backlogApi'
import { memberApi, type ProjectMember } from '../../api/memberApi'
import { raidApi, type RaidItemInput, type RaidLog } from '../../api/raidApi'
import { sprintApi } from '../../api/sprintApi'
import { wbsApi, type WbsNode } from '../../api/wbsApi'
import { raidCacheKeyFor } from '../../stores/scheduleCache'
import { applyFilters, DEFAULT_FILTERS, type RaidFilters } from './raidFilter'

/** One option in the link picker, flattened to the same shape whatever it points at. */
export interface RaidLinkOption {
  id: number
  /** WBS 코드처럼 표시용 식별자. Sprint·Backlog에는 없다. */
  code: string | null
  name: string
  /** Indent level, so a WBS picker reads like the tree. 1 for the flat lists. */
  level: number
}

const EMPTY: RaidLog = { referenceDate: null, items: [] }

/** Shared at module scope so the log survives navigating away and back. */
const data = ref<RaidLog>(EMPTY)
/** Owner options. RAID does not need the RACI matrix, only the member list. */
const members = ref<ProjectMember[]>([])
/** Task options for the WBS link, flattened in tree order so the picker reads like the WBS view. */
const wbsTasks = ref<{ id: number; code: string; name: string; level: number }[]>([])
/**
 * The other two kinds of link target (설계 §9). Loaded here rather than reusing the Sprint and
 * Backlog composables: those hold their own screens' selection and filters, and a picker pulling
 * on them would reset what the user has set up over there.
 */
const sprints = ref<RaidLinkOption[]>([])
const backlogItems = ref<RaidLinkOption[]>([])
const loading = ref(false)
const error = ref<string | null>(null)
/** View state, module scope too: switching tabs should not silently reset a filter. */
const filters = ref<RaidFilters>({ ...DEFAULT_FILTERS })
let cacheKey: string | null = null
/** The request currently in flight, so concurrent callers share one fetch. */
let inFlight: { key: string; promise: Promise<void> } | null = null

function flatten(nodes: WbsNode[], target: typeof wbsTasks.value) {
  for (const node of nodes) {
    target.push({ id: node.id, code: node.code, name: node.name, level: node.level })
    flatten(node.children, target)
  }
}

export function useRaid() {
  function describe(e: unknown, fallback: string) {
    return e instanceof ApiError ? e.message : fallback
  }

  function apply(result: RaidLog, projectId: number) {
    data.value = result
    cacheKey = raidCacheKeyFor(projectId)
  }

  async function load(projectId: number) {
    loading.value = true
    error.value = null
    try {
      const [log, memberList, tree, sprintList, backlog] = await Promise.all([
        raidApi.log(projectId),
        memberApi.list(projectId),
        wbsApi.tree(projectId),
        sprintApi.list(projectId),
        backlogApi.list(projectId),
      ])
      members.value = memberList
      const tasks: typeof wbsTasks.value = []
      flatten(tree.nodes, tasks)
      wbsTasks.value = tasks
      sprints.value = sprintList.sprints.map((sprint) => ({
        id: sprint.id,
        code: null,
        name: `${sprint.name} (${sprint.startDate} ~ ${sprint.endDate})`,
        level: 1,
      }))
      // 보관된 항목은 빼지 않는다 — 보관 전에 걸어 둔 위험이 목록에서 사라지면 편집할 수 없다.
      backlogItems.value = backlog.items.map((item) => ({
        id: item.id,
        code: null,
        name: item.title,
        level: 1,
      }))
      apply(log, projectId)
    } catch (e) {
      data.value = EMPTY
      members.value = []
      wbsTasks.value = []
      sprints.value = []
      backlogItems.value = []
      cacheKey = null
      error.value = describe(e, 'RAID 로그를 불러오지 못했습니다.')
    } finally {
      loading.value = false
    }
  }

  function ensureLoaded(projectId: number): Promise<void> {
    const key = raidCacheKeyFor(projectId)
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

  async function mutate(projectId: number, action: () => Promise<RaidLog>, fallback: string) {
    error.value = null
    try {
      apply(await action(), projectId)
      return true
    } catch (e) {
      error.value = describe(e, fallback)
      return false
    }
  }

  const create = (projectId: number, input: RaidItemInput) =>
    mutate(projectId, () => raidApi.create(projectId, input), '항목을 추가하지 못했습니다.')

  const update = (projectId: number, itemId: number, input: RaidItemInput) =>
    mutate(projectId, () => raidApi.update(projectId, itemId, input), '항목을 수정하지 못했습니다.')

  const remove = (projectId: number, itemId: number) =>
    mutate(projectId, () => raidApi.remove(projectId, itemId), '항목을 삭제하지 못했습니다.')

  /** What the list shows. The banners deliberately keep using the whole register. */
  const visibleItems = computed(() => applyFilters(data.value.items, filters.value))

  function resetFilters() {
    filters.value = { ...DEFAULT_FILTERS }
  }

  return {
    data,
    members,
    wbsTasks,
    sprints,
    backlogItems,
    loading,
    error,
    filters,
    visibleItems,
    load,
    ensureLoaded,
    create,
    update,
    remove,
    resetFilters,
  }
}
