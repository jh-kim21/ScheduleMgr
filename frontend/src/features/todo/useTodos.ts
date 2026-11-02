import { computed, ref } from 'vue'
import { ApiError } from '../../api/http'
import { memberApi, type ProjectMember } from '../../api/memberApi'
import { todoApi, type TodoItemInput, type TodoList } from '../../api/todoApi'
import { todoCacheKeyFor } from '../../stores/scheduleCache'
import { activeCommit, commitPayload, readOnly } from '../../stores/commitView'
import { applyFilters, DEFAULT_FILTERS, type TodoFilters } from './todoFilter'

const EMPTY: TodoList = { referenceDate: '', items: [] }

/** 모듈 스코프 — 탭을 옮겨도 목록과 필터가 유지된다. */
const data = ref<TodoList>(EMPTY)
/** 담당자 선택지. TODO는 RACI 매트릭스가 아니라 구성원 목록만 필요하다. */
const members = ref<ProjectMember[]>([])
const loading = ref(false)
const error = ref<string | null>(null)
/**
 * 구형 커밋(TODO 기록이 생기기 전)을 보고 있는지. 빈 목록으로만 그리면 "그때 할 일이 없었다"로
 * 잘못 읽히므로 화면이 따로 안내한다.
 */
const noRecord = ref(false)
const filters = ref<TodoFilters>({ ...DEFAULT_FILTERS })
let cacheKey: string | null = null
let inFlight: { key: string; promise: Promise<void> } | null = null

export function useTodos() {
  function describe(e: unknown, fallback: string) {
    return e instanceof ApiError ? e.message : fallback
  }

  /** 커밋은 불변이라 커밋 id 하나로 충분하다. */
  function currentCacheKey(projectId: number): string {
    return readOnly.value && activeCommit.value ? `commit:${activeCommit.value.id}` : todoCacheKeyFor(projectId)
  }

  function apply(result: TodoList, projectId: number) {
    data.value = result
    cacheKey = currentCacheKey(projectId)
  }

  async function load(projectId: number) {
    loading.value = true
    error.value = null
    try {
      if (readOnly.value && commitPayload.value) {
        const todos = commitPayload.value.todos
        noRecord.value = todos == null
        members.value = commitPayload.value.members
        apply(todos ?? { referenceDate: commitPayload.value.asOf, items: [] }, projectId)
      } else {
        noRecord.value = false
        const [list, memberList] = await Promise.all([todoApi.list(projectId), memberApi.list(projectId)])
        members.value = memberList
        apply(list, projectId)
      }
    } catch (e) {
      data.value = EMPTY
      members.value = []
      noRecord.value = false
      cacheKey = null
      error.value = describe(e, 'TODO 목록을 불러오지 못했습니다.')
    } finally {
      loading.value = false
    }
  }

  function ensureLoaded(projectId: number): Promise<void> {
    const key = currentCacheKey(projectId)
    if (cacheKey === key) return Promise.resolve()
    if (inFlight?.key === key) return inFlight.promise
    const promise = load(projectId).finally(() => {
      if (inFlight?.key === key) inFlight = null
    })
    inFlight = { key, promise }
    return promise
  }

  async function mutate(projectId: number, action: () => Promise<TodoList>, fallback: string) {
    if (readOnly.value) return false
    error.value = null
    try {
      apply(await action(), projectId)
      return true
    } catch (e) {
      error.value = describe(e, fallback)
      return false
    }
  }

  const create = (projectId: number, input: TodoItemInput) =>
    mutate(projectId, () => todoApi.create(projectId, input), 'TODO를 추가하지 못했습니다.')

  const update = (projectId: number, itemId: number, input: TodoItemInput) =>
    mutate(projectId, () => todoApi.update(projectId, itemId, input), 'TODO를 수정하지 못했습니다.')

  const setCompleted = (projectId: number, itemId: number, completed: boolean) =>
    mutate(
      projectId,
      () => todoApi.setCompleted(projectId, itemId, completed),
      '완료 상태를 바꾸지 못했습니다.',
    )

  const remove = (projectId: number, itemId: number) =>
    mutate(projectId, () => todoApi.remove(projectId, itemId), 'TODO를 삭제하지 못했습니다.')

  /** 표가 보여 주는 것. 배너는 일부러 `data` 전체를 센다. */
  const visibleItems = computed(() => applyFilters(data.value.items, filters.value))

  function resetFilters() {
    filters.value = { ...DEFAULT_FILTERS }
  }

  return {
    data,
    members,
    loading,
    error,
    noRecord,
    filters,
    visibleItems,
    load,
    ensureLoaded,
    create,
    update,
    setCompleted,
    remove,
    resetFilters,
  }
}
