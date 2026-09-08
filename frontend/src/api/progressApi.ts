import type { ExecutionMode } from '../shared/executionMode'
import type { AcceptanceStatus, ProgressBasis } from '../shared/progress'
import { http } from './http'

export interface CheckpointDetail {
  id: number
  title: string
  weight: number | null
  completionCriteria: string | null
  approved: boolean
  approvedBy: string | null
  approvedAt: string | null
}

export interface WorkPackageProgress {
  wbsItemId: number
  code: string | null
  name: string
  executionMode: ExecutionMode | null
  weight: number | null
  agileRatio: number | null
  acceptanceStatus: AcceptanceStatus | null
  /** Unrounded; `null` means 산정 전, which is not 0. */
  percent: number | null
  basis: ProgressBasis
  note: string | null
  backlogTotal: number
  backlogDone: number
  checkpointTotal: number
  checkpointApproved: number
  acceptancePending: boolean
  checkpoints: CheckpointDetail[]
}

export interface ProjectProgress {
  actualPercent: number | null
  basis: ProgressBasis
  workPackageCount: number
  notEstimableCount: number
  incomplete: boolean
  /** `null` until a baseline is approved — time alone is never planned progress. */
  plannedPercent: number | null
  comparablePercent: number | null
  variancePoints: number | null
  acceptancePending: number
}

export interface BaselineSummary {
  id: number
  version: number
  approvedBy: string
  approvedAt: string
  note: string | null
  itemCount: number
}

export interface ScopeComparison {
  hasBaseline: boolean
  baselineItemCount: number
  currentItemCount: number
  added: string[]
  removed: string[]
  weightChanged: string[]
}

export interface Progress {
  referenceDate: string
  project: ProjectProgress
  workPackages: WorkPackageProgress[]
  /** `null` when nothing has been approved — no baseline is ever invented. */
  baseline: BaselineSummary | null
  scope: ScopeComparison
}

export interface SnapshotDetail {
  id: number
  asOf: string
  baselineId: number | null
  baselineVersion: number | null
  scopeItemCount: number
  scopeWeightTotal: number | null
  /** JSON string, stored verbatim at report time rather than recomputed. */
  metrics: string
  note: string | null
  createdAt: string
}

export interface SnapshotList {
  snapshots: SnapshotDetail[]
}

export interface CheckpointInput {
  wbsItemId: number
  title: string
  weight: number | null
  completionCriteria: string | null
}

/**
 * Every mutation returns the whole progress payload: approving one checkpoint moves its Work
 * Package, every summary above it and the project figure.
 */
export const progressApi = {
  get: (projectId: number) => http.get<Progress>(`/projects/${projectId}/progress`),
  addCheckpoint: (projectId: number, input: CheckpointInput) =>
    http.post<Progress>(`/projects/${projectId}/progress/checkpoints`, input),
  updateCheckpoint: (projectId: number, checkpointId: number, input: CheckpointInput) =>
    http.put<Progress>(`/projects/${projectId}/progress/checkpoints/${checkpointId}`, input),
  setApproval: (projectId: number, checkpointId: number, approved: boolean, approvedBy: string | null) =>
    http.put<Progress>(`/projects/${projectId}/progress/checkpoints/${checkpointId}/approval`, {
      approved,
      approvedBy,
    }),
  deleteCheckpoint: (projectId: number, checkpointId: number) =>
    http.delete<Progress>(`/projects/${projectId}/progress/checkpoints/${checkpointId}`),
  approveBaseline: (projectId: number, approvedBy: string, note: string | null) =>
    http.post<Progress>(`/projects/${projectId}/progress/baselines`, { approvedBy, note }),
  snapshots: (projectId: number) =>
    http.get<SnapshotList>(`/projects/${projectId}/progress/snapshots`),
  saveSnapshot: (projectId: number, note: string | null) =>
    http.post<SnapshotList>(`/projects/${projectId}/progress/snapshots`, { note }),
}
