import { http } from './http'

export interface ProjectMember {
  id: number
  name: string
  email: string | null
  position: string | null
  createdAt: string
  updatedAt: string
}

export interface MemberInput {
  name: string
  email: string | null
  position: string | null
}

/**
 * Members are a project concern (요구사항 4.2), so these endpoints return just the member — unlike
 * the WBS/RACI endpoints, adding a person changes nothing about the others. The RACI screen
 * reloads the matrix itself after a member change, since the matrix columns come from this list.
 */
export const memberApi = {
  list: (projectId: number) => http.get<ProjectMember[]>(`/projects/${projectId}/members`),
  create: (projectId: number, input: MemberInput) =>
    http.post<ProjectMember>(`/projects/${projectId}/members`, input),
  update: (projectId: number, memberId: number, input: MemberInput) =>
    http.put<ProjectMember>(`/projects/${projectId}/members/${memberId}`, input),
  remove: (projectId: number, memberId: number) =>
    http.delete<void>(`/projects/${projectId}/members/${memberId}`),
}
