import { http } from './http'

export type ProjectStatus = 'PLANNED' | 'IN_PROGRESS' | 'ON_HOLD' | 'COMPLETED'

export interface Project {
  id: number
  name: string
  description: string | null
  status: ProjectStatus
  startDate: string | null
  endDate: string | null
  createdAt: string
  updatedAt: string
}

export interface ProjectInput {
  name: string
  description: string | null
  status: ProjectStatus
  startDate: string | null
  endDate: string | null
}

export const projectApi = {
  list: () => http.get<Project[]>('/projects'),
  get: (id: number) => http.get<Project>(`/projects/${id}`),
  create: (input: ProjectInput) => http.post<Project>('/projects', input),
  update: (id: number, input: ProjectInput) => http.put<Project>(`/projects/${id}`, input),
  remove: (id: number) => http.delete<void>(`/projects/${id}`),
}
