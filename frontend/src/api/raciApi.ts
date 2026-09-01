import type { ProjectMember } from './memberApi'
import { http } from './http'
import type { RaciIssueType, RaciRole } from '../shared/raci'

/** Matrix rows: every WBS entry in tree order, so they line up with the WBS and Gantt views. */
export interface RaciTask {
  id: number
  parentId: number | null
  code: string
  level: number
  name: string
  summary: boolean
}

/**
 * One cell of the matrix. A cell holds a *set* of letters — the same person is often both
 * Accountable and Responsible — and `assignmentIds` is aligned with `roles` by index so a single
 * letter can be removed without another lookup.
 */
export interface RaciCell {
  wbsItemId: number
  memberId: number
  roles: RaciRole[]
  assignmentIds: number[]
}

export interface RaciIssue {
  wbsItemId: number
  code: string
  name: string
  type: RaciIssueType
  memberNames: string[]
}

export interface RaciMatrix {
  members: ProjectMember[]
  tasks: RaciTask[]
  cells: RaciCell[]
  issues: RaciIssue[]
}

export interface RaciAssignmentInput {
  wbsItemId: number
  memberId: number
  role: RaciRole
}

export const raciApi = {
  matrix: (projectId: number) => http.get<RaciMatrix>(`/projects/${projectId}/raci`),
  assign: (projectId: number, input: RaciAssignmentInput) =>
    http.post<RaciMatrix>(`/projects/${projectId}/raci/assignments`, input),
  unassign: (projectId: number, assignmentId: number) =>
    http.delete<RaciMatrix>(`/projects/${projectId}/raci/assignments/${assignmentId}`),
}
