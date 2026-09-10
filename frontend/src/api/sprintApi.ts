import type {
  BacklogItemType,
  BacklogPriority,
  BacklogStatus,
} from '../shared/backlog'
import type { SprintItemOutcome, SprintStatus } from '../shared/sprint'
import { http } from './http'

/** One assignment as the Sprint list and the Board show it. */
export interface SprintItem {
  assignmentId: number
  backlogItemId: number
  itemType: BacklogItemType
  title: string
  priority: BacklogPriority
  status: BacklogStatus
  blocked: boolean
  blockedReason: string | null
  assigneeMemberId: number | null
  assigneeName: string | null
  acceptanceCriteria: string | null
  storyPoint: number | null
  /** The Work Package this entry belongs to, so the Board can find related RAID entries. */
  wbsItemId: number | null
  wbsCode: string | null
  wbsName: string | null
  pointsAtStart: number | null
  pointsAtClose: number | null
  /** Set when the Sprint closed; `null` while it is open. */
  outcome: SprintItemOutcome | null
  removed: boolean
  /** Children that are not Done. Does not block completion — it is there to be seen. */
  openChildCount: number
  /** The Sprint recorded this as complete but the entry is not complete now. */
  reopened: boolean
  doneAt: string | null
}

export interface Sprint {
  id: number
  name: string
  goal: string | null
  startDate: string
  endDate: string
  status: SprintStatus
  closedAt: string | null
  plannedItems: number
  plannedPoints: number
  doneItems: number
  donePoints: number
  blockedItems: number
  carriedOverItems: number
  canStart: boolean
  canDelete: boolean
  items: SprintItem[]
}

/**
 * Every Sprint of the project. Mutations return all of them: closing with carry-over changes two,
 * and whether a Sprint may be started depends on whether another is running.
 */
export interface SprintList {
  activeSprintId: number | null
  sprints: Sprint[]
}

export interface SprintInput {
  name: string
  goal: string | null
  startDate: string
  endDate: string
}

export interface BoardMoveInput {
  status: BacklogStatus
  blocked?: boolean | null
  blockedReason?: string | null
  /** Required to move an entry into 완료 — the minimum completion procedure. */
  acceptanceConfirmed?: boolean
}

export const sprintApi = {
  list: (projectId: number) => http.get<SprintList>(`/projects/${projectId}/sprints`),
  create: (projectId: number, input: SprintInput) =>
    http.post<SprintList>(`/projects/${projectId}/sprints`, input),
  update: (projectId: number, sprintId: number, input: SprintInput) =>
    http.put<SprintList>(`/projects/${projectId}/sprints/${sprintId}`, input),
  remove: (projectId: number, sprintId: number) =>
    http.delete<SprintList>(`/projects/${projectId}/sprints/${sprintId}`),
  start: (projectId: number, sprintId: number) =>
    http.post<SprintList>(`/projects/${projectId}/sprints/${sprintId}/start`, {}),
  /** Undo, not stop: reverts an ACTIVE Sprint to PLANNED and clears the imprinted Story Points. */
  cancelStart: (projectId: number, sprintId: number) =>
    http.post<SprintList>(`/projects/${projectId}/sprints/${sprintId}/cancel-start`, {}),
  close: (projectId: number, sprintId: number, carryOverToSprintId: number | null) =>
    http.post<SprintList>(`/projects/${projectId}/sprints/${sprintId}/close`, {
      carryOverToSprintId,
    }),
  assign: (projectId: number, sprintId: number, backlogItemId: number) =>
    http.post<SprintList>(`/projects/${projectId}/sprints/${sprintId}/items`, { backlogItemId }),
  unassign: (projectId: number, sprintId: number, backlogItemId: number) =>
    http.delete<SprintList>(`/projects/${projectId}/sprints/${sprintId}/items/${backlogItemId}`),
  move: (projectId: number, sprintId: number, backlogItemId: number, input: BoardMoveInput) =>
    http.put<SprintList>(
      `/projects/${projectId}/sprints/${sprintId}/board/${backlogItemId}`,
      input,
    ),
}
