import type { DelayInfo } from '../shared/delay'
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
  tasks: GanttTask[]
  dependencies: GanttDependency[]
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
