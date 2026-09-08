import type { DelayInfo } from '../shared/delay'
import type { ProgressBasis } from '../shared/progress'
import type { SprintStatus } from '../shared/sprint'
import { http } from './http'

/** Schedule health fields come from {@link DelayInfo}; the server computes them. */
export interface GanttTask extends DelayInfo {
  id: number
  parentId: number | null
  code: string
  level: number
  name: string
  summary: boolean
  endDate: string | null
  /** Earliest start allowed by predecessors; null when unconstrained. */
  earliestStart: string | null
  /** The plan contradicting itself: this task starts before a predecessor finishes. */
  scheduleViolation: boolean
  /**
   * Days this task may slip before the project end moves. Null when it takes part in no
   * dependency — there is no chain to be on, so the question does not apply.
   */
  floatDays: number | null
  /** No float left to give: this task's slip moves the project end. */
  criticalPath: boolean
  /**
   * The approved baseline's dates. Null when this row is not in the baseline — including when
   * there is no baseline at all, which `GanttData.hasBaseline` says outright. The current plan is
   * never copied here: a chart that draws the plan as its own baseline can never show a slip.
   */
  baselineStart: string | null
  baselineEnd: string | null
  /** When work really started and finished. Never filled in from a Sprint's dates. */
  actualStart: string | null
  actualEnd: string | null
  /** When it now looks like it will finish, stated without rewriting the plan. */
  forecastEnd: string | null
  /** Forecast (or, without one, the current plan) runs past the approved end. */
  baselineExceeded: boolean
  baselineSlipDays: number
  /** The common aggregation's figure. Null = 산정 전, which is not 0%. */
  computedProgress: number | null
  progressBasis: ProgressBasis | null
  /** Execution reads 100% but acceptance is still outstanding. */
  acceptancePending: boolean
  /** Sprints working on this row — for highlighting the lane, nothing is derived from it. */
  sprintIds: number[]
}

/**
 * One Sprint as its own lane. A Sprint spanning several Work Packages appears once: repeating it
 * per row would invent schedule and progress that do not exist.
 */
export interface GanttSprintLane {
  id: number
  name: string
  goal: string | null
  startDate: string
  endDate: string
  status: SprintStatus
  plannedItems: number
  doneItems: number
  /** Work Packages this Sprint touches, so selecting a row can highlight the lane. */
  wbsItemIds: number[]
}

export interface GanttDependency {
  id: number
  predecessorId: number
  successorId: number
  lagDays: number
  /** This link joins two critical tasks with no slack between them. */
  criticalPath: boolean
}

export interface GanttData {
  chartStart: string | null
  chartEnd: string | null
  /** The "today" the server judged delay against; null only when the payload is empty. */
  referenceDate: string | null
  /** False when no baseline has been approved — the chart must say so, not fake one. */
  hasBaseline: boolean
  baselineVersion: number | null
  tasks: GanttTask[]
  dependencies: GanttDependency[]
  sprints: GanttSprintLane[]
}

export interface DependencyInput {
  predecessorId: number
  successorId: number
  lagDays: number
}

export interface ScheduleRecalculation {
  shiftedTaskCount: number
  gantt: GanttData
}

export const ganttApi = {
  data: (projectId: number) => http.get<GanttData>(`/projects/${projectId}/gantt`),
  addDependency: (projectId: number, input: DependencyInput) =>
    http.post<GanttData>(`/projects/${projectId}/gantt/dependencies`, input),
  updateDependency: (projectId: number, dependencyId: number, input: DependencyInput) =>
    http.put<GanttData>(`/projects/${projectId}/gantt/dependencies/${dependencyId}`, input),
  removeDependency: (projectId: number, dependencyId: number) =>
    http.delete<GanttData>(`/projects/${projectId}/gantt/dependencies/${dependencyId}`),
  recalculate: (projectId: number) =>
    http.post<ScheduleRecalculation>(`/projects/${projectId}/gantt/recalculate`, {}),
}
