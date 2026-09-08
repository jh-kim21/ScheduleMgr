import type {
  BacklogItemType,
  BacklogPriority,
  BacklogStatus,
} from '../shared/backlog'
import type { ExecutionMode } from '../shared/executionMode'
import { http } from './http'

/**
 * A Product Backlog entry. `wbsCode`, `wbsName`, `wbsExecutionMode`, `assigneeName`, `depth`,
 * `aggregated`, `childCount` and every warning flag are derived by the server — the WBS code comes
 * from tree position and the warnings depend on the Work Package the entry points at, so only the
 * server can produce them.
 */
export interface BacklogItem {
  id: number
  wbsItemId: number | null
  wbsCode: string | null
  wbsName: string | null
  wbsExecutionMode: ExecutionMode | null
  parentId: number | null
  parentTitle: string | null
  /** 0 top-level, 1 under an Epic, 2 for a Task. Indentation only. */
  depth: number
  itemType: BacklogItemType
  title: string
  description: string | null
  priority: BacklogPriority
  status: BacklogStatus
  assigneeMemberId: number | null
  assigneeName: string | null
  acceptanceCriteria: string | null
  storyPoint: number | null
  progressWeight: number | null
  archivedAt: string | null
  archived: boolean
  /** Blocked on the board. Separate from `status`, and it blocks completion. */
  blocked: boolean
  blockedReason: string | null
  /** When it was accepted as done; cleared on re-open. */
  doneAt: string | null
  /** The open Sprint this entry is in, or `null` — why delete/archive may be refused. */
  openSprintName: string | null
  /** Whether progress will be counted on this entry from Step 5 (Story·Bug only). */
  aggregated: boolean
  childCount: number
  unlinked: boolean
  linkedToSummary: boolean
  danglingLink: boolean
  requiresExecutionModeChange: boolean
  readyForSprint: boolean
}

/**
 * The whole backlog. `unlinkedCount` counts the entire project rather than the current filter —
 * "미연결 3건" is a fact about the project, and a filtered count would read as a smaller problem
 * than it is (the same rule the RAID banner follows).
 */
export interface Backlog {
  unlinkedCount: number
  items: BacklogItem[]
}

export interface BacklogItemInput {
  wbsItemId: number | null
  parentId: number | null
  itemType: BacklogItemType
  title: string
  description: string | null
  priority: BacklogPriority
  status: BacklogStatus
  assigneeMemberId: number | null
  acceptanceCriteria: string | null
  storyPoint: number | null
  progressWeight: number | null
  /** Required to save an entry as 완료 — the minimum completion procedure. */
  acceptanceConfirmed?: boolean
}

/** Every mutation returns the whole backlog: re-attaching a parent moves everything beneath it. */
export const backlogApi = {
  list: (projectId: number) => http.get<Backlog>(`/projects/${projectId}/backlog`),
  create: (projectId: number, input: BacklogItemInput) =>
    http.post<Backlog>(`/projects/${projectId}/backlog`, input),
  update: (projectId: number, itemId: number, input: BacklogItemInput) =>
    http.put<Backlog>(`/projects/${projectId}/backlog/${itemId}`, input),
  setArchived: (projectId: number, itemId: number, archived: boolean) =>
    http.put<Backlog>(`/projects/${projectId}/backlog/${itemId}/archive`, { archived }),
  remove: (projectId: number, itemId: number) =>
    http.delete<Backlog>(`/projects/${projectId}/backlog/${itemId}`),
}
