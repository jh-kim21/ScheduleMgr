import { ref } from 'vue'
import { ApiError } from '../../api/http'
import { projectApi, type Project, type ProjectInput } from '../../api/projectApi'
import { clearSelectionIf } from '../../stores/projectSelection'

/**
 * Module-scope state: every view that calls {@link useProjects} shares one project list, so moving
 * between tabs neither refetches nor flashes a loading state.
 */
const projects = ref<Project[]>([])
const loading = ref(false)
const error = ref<string | null>(null)
let loaded = false
/** The request currently in flight, so concurrent callers share one fetch. */
let inFlight: Promise<void> | null = null

export function useProjects() {
  function describe(e: unknown, fallback: string) {
    return e instanceof ApiError ? e.message : fallback
  }

  async function load() {
    loading.value = true
    error.value = null
    try {
      projects.value = await projectApi.list()
      loaded = true
    } catch (e) {
      error.value = describe(e, '프로젝트 목록을 불러오지 못했습니다.')
    } finally {
      loading.value = false
    }
  }

  /** Fetches only on the first call; later mounts reuse the list already in memory. */
  function ensureLoaded(): Promise<void> {
    if (loaded) return Promise.resolve()
    if (inFlight) return inFlight
    inFlight = load().finally(() => {
      inFlight = null
    })
    return inFlight
  }

  async function create(input: ProjectInput) {
    const created = await projectApi.create(input)
    projects.value = [...projects.value, created]
    return created
  }

  async function update(id: number, input: ProjectInput) {
    const updated = await projectApi.update(id, input)
    projects.value = projects.value.map((p) => (p.id === id ? updated : p))
    return updated
  }

  async function remove(id: number) {
    await projectApi.remove(id)
    projects.value = projects.value.filter((p) => p.id !== id)
    // The schedule screens must not keep pointing at a project that no longer exists.
    clearSelectionIf(id)
  }

  return { projects, loading, error, load, ensureLoaded, create, update, remove }
}
