import type { BacklogSummary } from '../shared/backlog'
import type { DelayInfo } from '../shared/delay'
import type { AcceptanceStatus, ProgressBasis } from '../shared/progress'
import type {
  ExecutionMode,
  ExecutionModeSummary,
  WbsNodeType,
} from '../shared/executionMode'
import { http } from './http'

/**
 * A WBS tree node. `code`, `level`, `summary`, `executionModeSummary`, the delay fields and — for
 * summary nodes — `startDate`, `endDate` and `progress` are derived by the server from tree
 * position, children and the reference date, so the client renders them as-is.
 */
export interface WbsNode extends DelayInfo {
  id: number
  parentId: number | null
  code: string
  level: number
  name: string
  description: string | null
  endDate: string | null
  /** Whether the schedule and progress above were rolled up from children. */
  summary: boolean
  nodeType: WbsNodeType
  /**
   * The entry's own mode; `null` is 미지정. On a `SUMMARY` entry a non-null value is a mode
   * retained from before it was converted — the screen flags it rather than using it.
   */
  executionMode: ExecutionMode | null
  /** How the Work Packages below are executed; `null` when the entry has no children. */
  executionModeSummary: ExecutionModeSummary | null
  /**
   * Linked Backlog counts, rolled up from below; `null` when this branch has none. Only a
   * `WORK_PACKAGE` row's count points at one place, so only that row links to the Backlog screen.
   */
  backlogSummary: BacklogSummary | null
  weight: number | null
  agileRatio: number | null
  acceptanceStatus: AcceptanceStatus | null
  /**
   * The common aggregation's figure, unrounded. `null` means 산정 전 — not 0. `progress` above is
   * still the stored/legacy value, so nothing that read it before Step 5 changed meaning.
   */
  computedProgress: number | null
  progressBasis: ProgressBasis | null
  progressIncomplete: boolean
  progressNote: string | null
  acceptancePending: boolean
  children: WbsNode[]
}

/**
 * The tree plus the date its delay verdicts were measured from. Wrapped rather than a bare array
 * because "6일 지연" means nothing without saying which day it was judged against.
 */
export interface WbsTree {
  referenceDate: string | null
  nodes: WbsNode[]
}

export interface WbsItemInput {
  name: string
  description: string | null
  startDate: string | null
  endDate: string | null
  progress: number
  weight: number | null
  agileRatio: number | null
  acceptanceStatus: AcceptanceStatus | null
  nodeType: WbsNodeType
  /**
   * `null` is 미지정. On a `SUMMARY` entry this must repeat the retained value — the server rejects
   * an attempt to *change* a summary's mode, which is what the form sends back unchanged.
   */
  executionMode: ExecutionMode | null
}

export interface WbsMoveInput {
  parentId: number | null
  position: number
}

/**
 * 열 순서는 고정: 레벨 · 업무명 · 시작일 · 종료일 · 진행률. 실행 방식·가중치 같은 이 앱 고유 값은
 * 받지 않는다 — PM이 이미 갖고 있는 평범한 표를 그대로 올릴 수 있어야 하기 때문이다.
 */
export interface WbsImportInput {
  file: File
  /** null이면 프로젝트 최상위에 붙는다. */
  parentId: number | null
}

/** Every mutation returns the whole rebuilt tree, since codes and rollups shift on any change. */
export const wbsApi = {
  tree: (projectId: number) => http.get<WbsTree>(`/projects/${projectId}/wbs`),
  create: (projectId: number, parentId: number | null, input: WbsItemInput) =>
    http.post<WbsTree>(`/projects/${projectId}/wbs`, { parentId, ...input }),
  update: (projectId: number, itemId: number, input: WbsItemInput) =>
    http.put<WbsTree>(`/projects/${projectId}/wbs/${itemId}`, input),
  move: (projectId: number, itemId: number, input: WbsMoveInput) =>
    http.put<WbsTree>(`/projects/${projectId}/wbs/${itemId}/move`, input),
  remove: (projectId: number, itemId: number) =>
    http.delete<WbsTree>(`/projects/${projectId}/wbs/${itemId}`),
  importFile: (projectId: number, input: WbsImportInput) => {
    const form = new FormData()
    form.append('file', input.file)
    const query = input.parentId !== null ? `?parentId=${input.parentId}` : ''
    return http.postForm<WbsTree>(`/projects/${projectId}/wbs/import${query}`, form)
  },
}
