import { http } from './http'
import type { ExecutionMode } from '../shared/executionMode'
import type { ProgressBasis } from '../shared/progress'
import type { RaidType } from '../shared/raid'
import type { SprintStatus } from '../shared/sprint'

/**
 * The dashboard payload (Step 7).
 *
 * Every figure here is the one its own screen shows — the server reads them from the same services
 * rather than recomputing, so the WBS, the Gantt and this page cannot disagree. `null` percentages
 * mean 산정 전 and must never be rendered as 0%.
 */
export interface Dashboard {
  /** The single "today" every judgement was made against. Always prefer this over a local clock. */
  referenceDate: string
  project: DashboardProject
  progress: DashboardProgress
  /** Null when no baseline has been approved — then planned progress is 미산정, not 0. */
  baseline: DashboardBaseline | null
  schedule: ScheduleCard
  execution: ExecutionCard
  velocity: SprintVelocity[]
  workPackages: WorkPackageCard
  control: ControlCard
  scope: ScopeComparison
  gaps: DataGap[]
  workload: WorkloadCard
}

export interface DashboardProject {
  id: number
  name: string
  status: string
  startDate: string | null
  endDate: string | null
  /** Earliest/latest dates actually planned in the WBS, which can differ from the project's own. */
  planStart: string | null
  planEnd: string | null
  forecastEnd: string | null
}

export interface DashboardProgress {
  actualPercent: number | null
  basis: ProgressBasis | null
  workPackageCount: number
  /** How much of the project the headline figure is silent about. */
  notEstimableCount: number
  incomplete: boolean
  /** Null without an approved baseline — time alone is never used as progress. */
  plannedPercent: number | null
  /** Actual over the *same* set the plan covers, so the two can be subtracted. */
  comparablePercent: number | null
  variancePoints: number | null
  acceptancePending: number
}

export interface DashboardBaseline {
  id: number
  version: number
  approvedBy: string | null
  approvedAt: string
  note: string | null
  itemCount: number
}

export interface ScheduleCard {
  delayedCount: number
  atRiskCount: number
  worstDelayDays: number
  criticalPathCount: number
  scheduleViolationCount: number
  delayed: TaskRef[]
  baselineExceeded: TaskRef[]
  acceptancePending: TaskRef[]
}

export interface TaskRef {
  wbsItemId: number
  code: string
  name: string
  detail: string | null
}

export interface ExecutionCard {
  /** Null when nothing is running. 단일 팀 전제이므로 최대 하나다. */
  activeSprint: ActiveSprint | null
  backlogUnlinkedCount: number
}

export interface ActiveSprint {
  id: number
  name: string
  goal: string | null
  startDate: string
  endDate: string
  plannedItems: number
  plannedPoints: number
  doneItems: number
  donePoints: number
  blocked: BlockedItem[]
}

export interface BlockedItem {
  backlogItemId: number
  title: string
  reason: string | null
  assigneeName: string | null
  wbsItemId: number | null
  wbsCode: string | null
  raid: RaidRef[]
}

/** Closed Sprints make the trend; the running one is carried alongside it, never inside it. */
export interface SprintVelocity {
  sprintId: number
  name: string
  startDate: string
  endDate: string
  status: SprintStatus
  donePoints: number
  doneItems: number
  carriedOverItems: number
  inProgress: boolean
}

export interface WorkPackageCard {
  total: number
  notEstimableCount: number
  acceptancePendingCount: number
  byExecutionMode: ModeCount[]
}

/** `mode: null` is 미지정 — kept visible, because an unassigned Work Package is what to look at. */
export interface ModeCount {
  mode: ExecutionMode | null
  count: number
}

export interface ControlCard {
  raciIssueCount: number
  missingAccountableCount: number
  missingResponsibleCount: number
  multipleAccountableCount: number
  /** 아래 목록이 잘리기 전의 진짜 건수. 목록은 카드당 5건까지만 싣는다(지시서). */
  openIssueCount: number
  highExposureCount: number
  overdueCount: number
  openIssues: RaidRef[]
  highExposure: RaidRef[]
  overdue: RaidRef[]
}

export interface RaidRef {
  raidItemId: number
  type: RaidType
  title: string
  ownerName: string | null
  detail: string | null
}

export interface ScopeComparison {
  hasBaseline: boolean
  baselineItemCount: number
  currentItemCount: number
  added: string[]
  removed: string[]
  weightChanged: string[]
}

/** Something the numbers cannot answer because the data is not there. Reported, never defaulted. */
export interface DataGap {
  kind: string
  label: string
  count: number
  wbsItemIds: number[]
  backlogItemIds: number[]
}

/**
 * 한 사람의 부하 (workload-balance 지시서). 가용 공수가 없어(1-1-a) 절대 판정은 할 수 없고,
 * 이 프로젝트 안에서의 상대 비교만 가능하다 — 다른 프로젝트와 합산하면 거짓이 된다(1-1-b).
 *
 * <p>`storyPoints`가 `null`인 것과 `0`인 것은 다른 사실이다: `null`은 담당 Story·Bug가 아예
 * 없거나 있어도 포인트가 안 적힌 경우, `0`은 포인트 합이 실제로 0인 경우다.
 */
export interface MemberLoad {
  memberId: number
  memberName: string
  /** 기준일에 진행 중인 담당(R, 상속 포함) 업무 수 — 주 지표. */
  activeCount: number
  delayedCount: number
  atRiskCount: number
  openStoryCount: number
  storyPoints: number | null
  openRaidCount: number
}

/**
 * 전원이 실려 온다(5명으로 자르지 않는다) — 요약 카드가 상위 5명만 추리고, 부하 탭이 전부 그린다.
 * `members`는 이미 activeCount 내림차순 · 동점은 이름 순으로 정렬돼 온다.
 */
export interface WorkloadCard {
  members: MemberLoad[]
  unassignedActiveCount: number
}

export const dashboardApi = {
  get: (projectId: number) => http.get<Dashboard>(`/projects/${projectId}/dashboard`),
}
