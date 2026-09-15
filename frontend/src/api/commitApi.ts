import type { Backlog } from './backlogApi'
import type { Dashboard } from './dashboardApi'
import type { GanttData } from './ganttApi'
import { ApiError, http } from './http'
import type { ProjectMember } from './memberApi'
import type { Project } from './projectApi'
import type { Progress, SnapshotList } from './progressApi'
import type { RaciMatrix } from './raciApi'
import type { RaidLog } from './raidApi'
import type { SprintList } from './sprintApi'
import type { WbsTree } from './wbsApi'

/**
 * 커밋 시점의 화면별 조회 응답 모음(`computed_payload`). 백엔드가 기존 조회 서비스를 한 번씩
 * 불러 배치한 것뿐이라 shape이 라이브 응답과 같다 — `DashboardService`가 다른 서비스 응답을
 * 그대로 모으는 것과 같은 패턴이다. 그래서 여기서도 각 화면 API의 응답 타입을 그대로 재사용한다
 * (모양을 두 벌 정의하면 왕복이 어긋날 수 있다).
 *
 * `checkpoints`는 별도 키가 없다 — 이미 `progress.workPackages[].checkpoints`에 들어 있다.
 */
export interface CommitPayload {
  asOf: string
  wbs: WbsTree
  gantt: GanttData
  members: ProjectMember[]
  raci: RaciMatrix
  raid: RaidLog
  backlog: Backlog
  sprints: SprintList
  progress: Progress
  snapshots: SnapshotList
  dashboard: Dashboard
}

export interface CommitMeta {
  id: number
  version: number
  asOf: string
  committedAt: string
  /** 로그인이 없어 자유 입력 텍스트다 (baselines.approved_by와 같은 한계). */
  committedBy: string | null
  message: string | null
  /** 화면에 필드가 추가되면 옛 커밋에는 그 값이 없다 — 이 값으로 "구형 커밋"임을 드러낸다. */
  formatVersion: number
  payloadBytes: number
}

export interface CommitCapacity {
  usedBytes: number
  maxBytes: number
  /** 0~100. 80 이상이면 `warning`이 선다. */
  usedPercent: number
  warning: boolean
}

export interface CommitListResult {
  capacity: CommitCapacity
  commits: CommitMeta[]
}

export interface CommitCreateResult {
  commit: CommitMeta
  capacity: CommitCapacity
}

export interface CommitDetail {
  commit: CommitMeta
  payload: CommitPayload
}

export interface CommitInput {
  committedBy: string | null
  message: string | null
}

/**
 * 용량 100% 초과 시 409로 거부되며, 몸체에 `{ message, capacity, commits }`가 함께 온다 —
 * 화면이 이 목록으로 삭제 대화상자를 띄운다. `http.post`가 던지는 평범한 `ApiError`는 이 여분의
 * 필드를 담지 못하므로(공용 `http.ts`는 손대지 않는다 — 다른 화면들이 함께 쓰는 파일이다),
 * 이 요청만 직접 `fetch`를 불러 몸체를 마저 읽는다.
 */
export class CommitCapacityExceededError extends ApiError {
  capacity: CommitCapacity
  commits: CommitMeta[]

  constructor(message: string, capacity: CommitCapacity, commits: CommitMeta[]) {
    super(409, message)
    this.name = 'CommitCapacityExceededError'
    this.capacity = capacity
    this.commits = commits
  }
}

async function create(projectId: number, input: CommitInput): Promise<CommitCreateResult> {
  const response = await fetch(`/api/projects/${projectId}/commits`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(input),
  })

  if (response.status === 409) {
    const body = await response.json().catch(() => null)
    throw new CommitCapacityExceededError(
      body?.message ?? '커밋 용량이 가득 찼습니다.',
      body?.capacity,
      body?.commits ?? [],
    )
  }
  if (!response.ok) {
    const body = await response.json().catch(() => null)
    throw new ApiError(response.status, body?.message ?? `요청 실패 (${response.status})`)
  }
  return (await response.json()) as CommitCreateResult
}

export const commitApi = {
  list: (projectId: number) => http.get<CommitListResult>(`/projects/${projectId}/commits`),
  create,
  get: (projectId: number, version: number) =>
    http.get<CommitDetail>(`/projects/${projectId}/commits/${version}`),
  remove: (projectId: number, version: number) =>
    http.delete<CommitListResult>(`/projects/${projectId}/commits/${version}`),
  /** 서버가 `Content-Disposition: attachment`로 내려준다 — 평범한 링크로 받는다. */
  exportUrl: (projectId: number, version: number) =>
    `/api/projects/${projectId}/commits/${version}/export`,
  restore: (commitId: number) => http.post<Project>(`/projects/commits/${commitId}/restore`, {}),
}
