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
  /**
   * Backlog 담당자. **RACI 역할이 아니다** — shown beside the matrix so you can see who is
   * carrying execution, never counted as a letter. Changing a Story's assignee does not touch
   * this task's A, and assigning A does not reassign any Story.
   */
  storyAssignees: StoryAssignee[]
}

export interface StoryAssignee {
  memberId: number
  memberName: string
  /** How many Backlog entries they hold here. */
  itemCount: number
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
  /**
   * Letters that apply here but are assigned on an ancestor. They carry no id: they cannot be
   * removed from this row, only from the row that declares them.
   */
  inherited: InheritedRole[]
}

export interface InheritedRole {
  role: RaciRole
  source: 'OWN' | 'INHERITED'
  sourceItemId: number
  /** WBS code of the row the letter is assigned on, so the screen can say where it came from. */
  sourceCode: string | null
  /** This row assigns the same role to somebody else, so the inherited letter is not in force. */
  overridden: boolean
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
