import type { ProjectStatus } from '../../api/projectApi'

export const STATUS_LABELS: Record<ProjectStatus, string> = {
  PLANNED: '계획',
  IN_PROGRESS: '진행중',
  ON_HOLD: '보류',
  COMPLETED: '완료',
}

export const STATUS_OPTIONS = Object.entries(STATUS_LABELS) as [ProjectStatus, string][]
