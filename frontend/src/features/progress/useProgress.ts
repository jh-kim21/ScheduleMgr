import { ref } from 'vue'
import { ApiError } from '../../api/http'
import {
  progressApi,
  type CheckpointInput,
  type Progress,
  type SnapshotDetail,
  type WorkPackageBasisInput,
} from '../../api/progressApi'
import { markWbsChanged, progressCacheKeyFor } from '../../stores/scheduleCache'

/**
 * Shared at module scope like the other feature composables, so the progress screen keeps its data
 * across tab switches.
 */
const data = ref<Progress | null>(null)
const snapshots = ref<SnapshotDetail[]>([])
const loading = ref(false)
const error = ref<string | null>(null)

let cacheKey: string | null = null
let inFlight: { key: string; promise: Promise<void> } | null = null

export function useProgress() {
  function describe(e: unknown, fallback: string) {
    return e instanceof ApiError ? e.message : fallback
  }

  async function load(projectId: number) {
    loading.value = true
    error.value = null
    try {
      const [progress, snapshotList] = await Promise.all([
        progressApi.get(projectId),
        progressApi.snapshots(projectId),
      ])
      data.value = progress
      snapshots.value = snapshotList.snapshots
      cacheKey = progressCacheKeyFor(projectId)
    } catch (e) {
      data.value = null
      snapshots.value = []
      cacheKey = null
      error.value = describe(e, '진척을 불러오지 못했습니다.')
    } finally {
      loading.value = false
    }
  }

  function ensureLoaded(projectId: number): Promise<void> {
    const key = progressCacheKeyFor(projectId)
    if (cacheKey === key) return Promise.resolve()
    if (inFlight?.key === key) return inFlight.promise
    const promise = load(projectId).finally(() => {
      if (inFlight?.key === key) inFlight = null
    })
    inFlight = { key, promise }
    return promise
  }

  /**
   * Runs a mutation and marks the WBS stale: the tree carries the same computed figures, so a
   * checkpoint approval changes what that screen shows too.
   */
  async function mutate(projectId: number, action: () => Promise<Progress>, fallback: string) {
    error.value = null
    try {
      const result = await action()
      markWbsChanged()
      data.value = result
      cacheKey = progressCacheKeyFor(projectId)
      return true
    } catch (e) {
      error.value = describe(e, fallback)
      return false
    }
  }

  const addCheckpoint = (projectId: number, input: CheckpointInput) =>
    mutate(projectId, () => progressApi.addCheckpoint(projectId, input), '체크포인트를 추가하지 못했습니다.')

  const updateCheckpoint = (projectId: number, checkpointId: number, input: CheckpointInput) =>
    mutate(
      projectId,
      () => progressApi.updateCheckpoint(projectId, checkpointId, input),
      '체크포인트를 수정하지 못했습니다.',
    )

  const setApproval = (
    projectId: number,
    checkpointId: number,
    approved: boolean,
    approvedBy: string | null,
  ) =>
    mutate(
      projectId,
      () => progressApi.setApproval(projectId, checkpointId, approved, approvedBy),
      approved ? '승인하지 못했습니다.' : '승인을 취소하지 못했습니다.',
    )

  const deleteCheckpoint = (projectId: number, checkpointId: number) =>
    mutate(
      projectId,
      () => progressApi.deleteCheckpoint(projectId, checkpointId),
      '체크포인트를 삭제하지 못했습니다.',
    )

  const approveBaseline = (projectId: number, approvedBy: string, note: string | null) =>
    mutate(
      projectId,
      () => progressApi.approveBaseline(projectId, approvedBy, note),
      '기준선을 승인하지 못했습니다.',
    )

  const updateBasis = (projectId: number, wbsItemId: number, input: WorkPackageBasisInput) =>
    mutate(
      projectId,
      () => progressApi.updateBasis(projectId, wbsItemId, input),
      '가중치를 저장하지 못했습니다.',
    )

  async function saveSnapshot(projectId: number, note: string | null) {
    error.value = null
    try {
      snapshots.value = (await progressApi.saveSnapshot(projectId, note)).snapshots
      return true
    } catch (e) {
      error.value = describe(e, '스냅샷을 저장하지 못했습니다.')
      return false
    }
  }

  return {
    data,
    snapshots,
    loading,
    error,
    ensureLoaded,
    addCheckpoint,
    updateCheckpoint,
    setApproval,
    deleteCheckpoint,
    approveBaseline,
    updateBasis,
    saveSnapshot,
  }
}
