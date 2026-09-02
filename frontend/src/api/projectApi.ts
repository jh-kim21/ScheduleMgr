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

/**
 * 내보낸 파일을 그대로 본문으로 보낸다. 서버가 export 응답 타입을 그대로 입력으로 받으므로
 * 여기서 모양을 다시 정의하지 않는다 — 정의를 두 벌 두면 왕복이 어긋날 수 있다.
 */
export interface ImportResult {
  project: Project
}

export const projectApi = {
  list: () => http.get<Project[]>('/projects'),
  get: (id: number) => http.get<Project>(`/projects/${id}`),
  create: (input: ProjectInput) => http.post<Project>('/projects', input),
  update: (id: number, input: ProjectInput) => http.put<Project>(`/projects/${id}`, input),
  remove: (id: number) => http.delete<void>(`/projects/${id}`),
  /** 파일 내용(JSON 텍스트)을 그대로 보낸다. 실패하면 서버가 무엇이 잘못됐는지 문장으로 준다. */
  importProject: (fileContents: string) =>
    http.postRaw<Project>('/projects/import', fileContents),
}
