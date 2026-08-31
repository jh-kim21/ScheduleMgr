import { ref } from 'vue'
import { ganttApi, type DependencyInput, type GanttData } from '../../api/ganttApi'
import { ApiError } from '../../api/http'
import { cacheKeyFor, markWbsChanged } from '../../stores/scheduleCache'

const EMPTY: GanttData = {
  chartStart: null,
  chartEnd: null,
  referenceDate: null,
  tasks: [],
  dependencies: [],
}

/** Shared at module scope so the chart survives navigating away and back. */
const data = ref<GanttData>(EMPTY)
const loading = ref(false)
const error = ref<string | null>(null)
/** Result of the last recalculation, shown until the next action. */
const lastRecalculation = ref<number | null>(null)
/**
 * Chart display option, module scope for the same reason the data is: switching tabs should not
 * silently turn the highlight back on after the user turned it off.
 */
const showCriticalPath = ref(true)
let cacheKey: string | null = null
/** The request currently in flight, so concurrent callers share one fetch. */
let inFlight: { key: string; promise: Promise<void> } | null = null

export function useGantt() {
  function describe(e: unknown, fallback: string) {
    return e instanceof ApiError ? e.message : fallback
  }

  function apply(result: GanttData, projectId: number) {
    data.value = result
    cacheKey = cacheKeyFor(projectId)
  }

  async function load(projectId: number) {
    loading.value = true
    error.value = null
    lastRecalculation.value = null
    try {
      apply(await ganttApi.data(projectId), projectId)
    } catch (e) {
      data.value = EMPTY
      cacheKey = null
      error.value = describe(e, '간트 데이터를 불러오지 못했습니다.')
    } finally {
      loading.value = false
    }
  }

  /** Refetches only when the cached chart is for another project, another day, or now stale. */
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
   * Dependency edits change no WBS item, so they must not invalidate the WBS view — only
   * recalculation does that.
   */
  async function mutate(projectId: number, action: () => Promise<GanttData>, fallback: string) {
    error.value = null
    lastRecalculation.value = null
    try {
      apply(await action(), projectId)
    } catch (e) {
      error.value = describe(e, fallback)
    }
  }

  const addDependency = (projectId: number, input: DependencyInput) =>
    mutate(projectId, () => ganttApi.addDependency(projectId, input), '선후행 관계를 추가하지 못했습니다.')

  const updateDependency = (projectId: number, dependencyId: number, input: DependencyInput) =>
    mutate(
      projectId,
      () => ganttApi.updateDependency(projectId, dependencyId, input),
      '선후행 관계를 수정하지 못했습니다.',
    )

  const removeDependency = (projectId: number, dependencyId: number) =>
    mutate(
      projectId,
      () => ganttApi.removeDependency(projectId, dependencyId),
      '선후행 관계를 삭제하지 못했습니다.',
    )

  /** Shifts WBS dates, so the WBS view is invalidated too. */
  async function recalculate(projectId: number) {
    error.value = null
    lastRecalculation.value = null
    try {
      const result = await ganttApi.recalculate(projectId)
      markWbsChanged()
      apply(result.gantt, projectId)
      lastRecalculation.value = result.shiftedTaskCount
    } catch (e) {
      error.value = describe(e, '일정을 재계산하지 못했습니다.')
    }
  }

  return {
    data,
    loading,
    error,
    lastRecalculation,
    showCriticalPath,
    load,
    ensureLoaded,
    addDependency,
    updateDependency,
    removeDependency,
    recalculate,
  }
}
