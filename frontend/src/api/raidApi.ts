import { http } from './http'
import type { RaidLevel, RaidLinkTarget, RaidStatus, RaidType } from '../shared/raid'

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
  /**
   * What this entry is attached to — WBS 업무, Sprint, Backlog 항목. Empty when it concerns the
   * project as a whole. Names are resolved server-side: a WBS code is derived from tree position,
   * and the register does not otherwise hold Sprint or Story names.
   */
  links: RaidLink[]
  dueDate: string | null
  response: string | null
  /** 확률 × 영향 (1–9). Null unless both are set. */
  exposure: number | null
  exposureLevel: RaidLevel | null
  overdue: boolean
  overdueDays: number
}

/** @see RaidItem.links */
export interface RaidLink {
  id: number
  targetType: RaidLinkTarget
  targetId: number
  /** WBS 코드처럼 표시용 식별자. Sprint·Backlog에는 없다. */
  targetCode: string | null
  /** Null when the target is gone — the link is not a foreign key, and that is not hidden. */
  targetName: string | null
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
  links: RaidLinkInput[]
  dueDate: string | null
  response: string | null
}

export interface RaidLinkInput {
  targetType: RaidLinkTarget
  targetId: number
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
