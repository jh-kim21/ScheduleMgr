import { ref } from 'vue'
import { dashboardApi, type Dashboard } from '../../api/dashboardApi'
import { ApiError } from '../../api/http'
import { dashboardCacheKeyFor } from '../../stores/scheduleCache'

/**
 * Shared at module scope like the other feature composables, so the dashboard survives navigating
 * away and back.
 *
 * <p>Read-only: the dashboard has no mutations of its own. Every number it shows is changed on the
 * screen that owns it, and the cache key covers all three revisions so any of those edits brings
 * the page back for a fresh read.
 */
const data = ref<Dashboard | null>(null)
const loading = ref(false)
const error = ref<string | null>(null)

let cacheKey: string | null = null
let inFlight: { key: string; promise: Promise<void> } | null = null

export function useDashboard() {
  async function load(projectId: number) {
    loading.value = true
    error.value = null
    try {
      data.value = await dashboardApi.get(projectId)
      cacheKey = dashboardCacheKeyFor(projectId)
    } catch (e) {
      data.value = null
      cacheKey = null
      error.value = e instanceof ApiError ? e.message : '대시보드를 불러오지 못했습니다.'
    } finally {
      loading.value = false
    }
  }

  function ensureLoaded(projectId: number): Promise<void> {
    const key = dashboardCacheKeyFor(projectId)
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

  /** Forces the next visit to refetch — used by the manual refresh button. */
  function invalidate() {
    cacheKey = null
  }

  return { data, loading, error, load, ensureLoaded, invalidate }
}
