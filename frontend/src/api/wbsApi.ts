import type { DelayInfo } from '../shared/delay'
import { http } from './http'

/**
 * A WBS tree node. `code`, `level`, `summary`, the delay fields and — for summary nodes —
 * `startDate`, `endDate` and `progress` are derived by the server from tree position, children and
 * the reference date, so the client renders them as-is.
 */
export interface WbsNode extends DelayInfo {
  id: number
  parentId: number | null
  code: string
  level: number
  name: string
  description: string | null
  endDate: string | null
  summary: boolean
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
}

export interface WbsMoveInput {
  parentId: number | null
  position: number
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
}
