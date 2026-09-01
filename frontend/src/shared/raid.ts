/**
 * RAID vocabulary, shared by the log screen and its form so the two cannot drift into naming the
 * same thing differently — the same reason `shared/delay.ts` and `shared/raci.ts` exist.
 */

export type RaidType = 'RISK' | 'ASSUMPTION' | 'ISSUE' | 'DEPENDENCY'
export type RaidStatus = 'OPEN' | 'IN_PROGRESS' | 'CLOSED'
export type RaidLevel = 'LOW' | 'MEDIUM' | 'HIGH'

export const RAID_TYPE_ORDER: RaidType[] = ['RISK', 'ASSUMPTION', 'ISSUE', 'DEPENDENCY']

export const RAID_TYPE_LABELS: Record<RaidType, string> = {
  RISK: '위험',
  ASSUMPTION: '가정',
  ISSUE: '이슈',
  DEPENDENCY: '의존성',
}

export const RAID_TYPE_DESCRIPTIONS: Record<RaidType, string> = {
  RISK: '아직 일어나지 않았지만 일어날 수 있는 일.',
  ASSUMPTION: '사실이라고 전제하고 계획한 것. 틀리면 계획이 흔들립니다.',
  ISSUE: '이미 일어나서 대응이 필요한 일.',
  DEPENDENCY: '프로젝트 밖에서 받아야 하는 것. WBS의 선후행 관계와는 다릅니다.',
}

export const RAID_STATUS_ORDER: RaidStatus[] = ['OPEN', 'IN_PROGRESS', 'CLOSED']

export const RAID_STATUS_LABELS: Record<RaidStatus, string> = {
  OPEN: '열림',
  IN_PROGRESS: '대응중',
  CLOSED: '종결',
}

export const RAID_LEVEL_ORDER: RaidLevel[] = ['LOW', 'MEDIUM', 'HIGH']

export const RAID_LEVEL_LABELS: Record<RaidLevel, string> = {
  LOW: '낮음',
  MEDIUM: '보통',
  HIGH: '높음',
}

/** 기한 열의 문구. 서버가 판정한 값을 그대로 쓴다. */
export function dueLabel(dueDate: string | null, overdue: boolean, overdueDays: number): string {
  if (!dueDate) return '-'
  return overdue ? `${dueDate} (${overdueDays}일 초과)` : dueDate
}
