import { http } from './http'
import type { RaidLevel, RaidStatus, RaidType } from '../shared/raid'

/** Judgement fields (`exposure`, `overdue`) are computed server-side; see `RaidAssessor`. */
export interface RaidItem {
  id: number
  type: RaidType
  title: string
  description: string | null
  status: RaidStatus
  probability: RaidLevel | null
  impact: RaidLevel | null
  ownerMemberId: number | null
  /** Resolved server-side, so the list needs no second lookup. */
  ownerName: string | null
  /** The WBS task this entry is about, or null when it concerns the project as a whole. */
  wbsItemId: number | null
  /** WBS code and name, resolved server-side — the code is derived from tree position. */
  wbsCode: string | null
  wbsName: string | null
  dueDate: string | null
  response: string | null
  /** 확률 × 영향 (1–9). Null unless both are set. */
  exposure: number | null
  exposureLevel: RaidLevel | null
  overdue: boolean
  overdueDays: number
}

export interface RaidLog {
  /** The "today" overdue-ness was judged against; null only when the payload is empty. */
  referenceDate: string | null
  items: RaidItem[]
}

export interface RaidItemInput {
  type: RaidType
  title: string
  description: string | null
  status: RaidStatus
  probability: RaidLevel | null
  impact: RaidLevel | null
  ownerMemberId: number | null
  wbsItemId: number | null
  dueDate: string | null
  response: string | null
}

export const raidApi = {
  log: (projectId: number) => http.get<RaidLog>(`/projects/${projectId}/raid`),
  create: (projectId: number, input: RaidItemInput) =>
    http.post<RaidLog>(`/projects/${projectId}/raid`, input),
  update: (projectId: number, itemId: number, input: RaidItemInput) =>
    http.put<RaidLog>(`/projects/${projectId}/raid/${itemId}`, input),
  remove: (projectId: number, itemId: number) =>
    http.delete<RaidLog>(`/projects/${projectId}/raid/${itemId}`),
}
