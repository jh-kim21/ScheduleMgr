import { computed, ref } from 'vue'
import { backlogApi, type BacklogItem } from '../../api/backlogApi'
import { ApiError } from '../../api/http'
import {
  sprintApi,
  type BoardMoveInput,
  type Sprint,
  type SprintInput,
  type SprintList,
} from '../../api/sprintApi'
import { markBacklogChanged, markSprintChanged, sprintCacheKeyFor } from '../../stores/scheduleCache'

/**
 * Shared at module scope like the other feature composables, so moving between Sprint and Board
 * keeps the data and the selected Sprint.
 *
 * <p>The backlog is loaded alongside: planning needs the list of entries that may be assigned, and
 * only the server can say which are eligible ({@code readyForSprint}).
 */
const data = ref<SprintList>({ activeSprintId: null, sprints: [] })
const backlog = ref<BacklogItem[]>([])
const loading = ref(false)
const error = ref<string | null>(null)
/** Which Sprint the screens are looking at; defaults to the running one. */
const selectedSprintId = ref<number | null>(null)

let cacheKey: string | null = null
let inFlight: { key: string; promise: Promise<void> } | null = null

export function useSprints() {
  function describe(e: unknown, fallback: string) {
    return e instanceof ApiError ? e.message : fallback
  }

  function apply(result: SprintList, projectId: number) {
    data.value = result
    cacheKey = sprintCacheKeyFor(projectId)
    // 선택이 사라졌으면(삭제 등) 실행 중인 것으로, 그것도 없으면 첫 Sprint로 돌아간다.
    const ids = result.sprints.map((sprint) => sprint.id)
    if (selectedSprintId.value === null || !ids.includes(selectedSprintId.value)) {
      selectedSprintId.value = result.activeSprintId ?? ids[0] ?? null
    }
  }

  async function load(projectId: number) {
    loading.value = true
    error.value = null
    try {
      const [sprints, backlogList] = await Promise.all([
        sprintApi.list(projectId),
        backlogApi.list(projectId),
      ])
      backlog.value = backlogList.items
      apply(sprints, projectId)
    } catch (e) {
      data.value = { activeSprintId: null, sprints: [] }
      backlog.value = []
      cacheKey = null
      error.value = describe(e, 'Sprint를 불러오지 못했습니다.')
    } finally {
      loading.value = false
    }
  }

  function ensureLoaded(projectId: number): Promise<void> {
    const key = sprintCacheKeyFor(projectId)
    if (cacheKey === key) return Promise.resolve()
    if (inFlight?.key === key) return inFlight.promise
    const promise = load(projectId).finally(() => {
      if (inFlight?.key === key) inFlight = null
    })
    inFlight = { key, promise }
    return promise
  }

  /**
   * Runs a mutation and marks both revisions changed: a Board move is a Backlog status change, and
   * an assignment changes what the Backlog screen shows as "이 Sprint에 있음".
   *
   * <p>The Sprint payload comes back from the call, but the backlog list does not — it is refetched
   * so the assignable list and the per-entry Sprint name stay right.
   */
  async function mutate(projectId: number, action: () => Promise<SprintList>, fallback: string) {
    error.value = null
    try {
      const result = await action()
      markSprintChanged()
      markBacklogChanged()
      apply(result, projectId)
      backlog.value = (await backlogApi.list(projectId)).items
      cacheKey = sprintCacheKeyFor(projectId)
      return true
    } catch (e) {
      error.value = describe(e, fallback)
      return false
    }
  }

  const create = (projectId: number, input: SprintInput) =>
    mutate(projectId, () => sprintApi.create(projectId, input), 'Sprint를 만들지 못했습니다.')

  const update = (projectId: number, sprintId: number, input: SprintInput) =>
    mutate(projectId, () => sprintApi.update(projectId, sprintId, input), 'Sprint를 수정하지 못했습니다.')

  const remove = (projectId: number, sprintId: number) =>
    mutate(projectId, () => sprintApi.remove(projectId, sprintId), 'Sprint를 삭제하지 못했습니다.')

  const start = (projectId: number, sprintId: number) =>
    mutate(projectId, () => sprintApi.start(projectId, sprintId), 'Sprint를 시작하지 못했습니다.')

  const close = (projectId: number, sprintId: number, carryOverToSprintId: number | null) =>
    mutate(
      projectId,
      () => sprintApi.close(projectId, sprintId, carryOverToSprintId),
      'Sprint를 종료하지 못했습니다.',
    )

  const assign = (projectId: number, sprintId: number, backlogItemId: number) =>
    mutate(projectId, () => sprintApi.assign(projectId, sprintId, backlogItemId), '항목을 배정하지 못했습니다.')

  const unassign = (projectId: number, sprintId: number, backlogItemId: number) =>
    mutate(
      projectId,
      () => sprintApi.unassign(projectId, sprintId, backlogItemId),
      '항목을 제거하지 못했습니다.',
    )

  const move = (projectId: number, sprintId: number, backlogItemId: number, input: BoardMoveInput) =>
    mutate(
      projectId,
      () => sprintApi.move(projectId, sprintId, backlogItemId, input),
      '항목을 옮기지 못했습니다.',
    )

  const selected = computed<Sprint | null>(
    () => data.value.sprints.find((sprint) => sprint.id === selectedSprintId.value) ?? null,
  )

  const activeSprint = computed<Sprint | null>(
    () => data.value.sprints.find((sprint) => sprint.id === data.value.activeSprintId) ?? null,
  )

  /**
   * Entries that may still be assigned: eligible, and not already live in an open Sprint. The
   * server enforces both — this only keeps the picker from offering a guaranteed failure.
   */
  const assignable = computed(() =>
    backlog.value.filter((item) => item.readyForSprint && item.openSprintName === null),
  )

  /** Open Sprints other than the one closing, for the carry-over target picker. */
  const carryOverTargets = computed(() =>
    data.value.sprints.filter(
      (sprint) => sprint.status !== 'CLOSED' && sprint.id !== selectedSprintId.value,
    ),
  )

  return {
    data,
    backlog,
    loading,
    error,
    selectedSprintId,
    selected,
    activeSprint,
    assignable,
    carryOverTargets,
    ensureLoaded,
    create,
    update,
    remove,
    start,
    close,
    assign,
    unassign,
    move,
  }
}
