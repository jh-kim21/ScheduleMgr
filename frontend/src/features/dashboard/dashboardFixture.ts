import type { Dashboard } from '../../api/dashboardApi'

/**
 * 대시보드 카드들의 테스트 fixture.
 *
 * <p><b>`.spec.ts` 안에 두지 않는다.</b> Vitest 에서 한 spec 이 다른 spec 을 import 하면 그
 * 모듈이 평가되면서 안의 `describe`/`it` 이 부르는 쪽 스위트에 다시 등록된다 — fixture 하나를
 * 가져다 쓰는 대가로 남의 테스트가 통째로 한 번 더 도는 것이다. 평범한 모듈로 두면 vitest 가
 * 테스트 파일로 집지 않고, 앱 코드가 import 하지 않으므로 번들에도 들어가지 않는다.
 *
 * <p>두 극단만 둔다: 모든 카드가 값을 갖는 프로젝트와, 아무것도 없는 프로젝트. 중간 상태는
 * 그것을 묻는 테스트가 자기 자리에서 스프레드로 덮어쓴다(`{ ...FULL_DASHBOARD, progress: … }`).
 */

/** 빈 프로젝트 — Work Package 0 / Sprint 0 / RAID 0 / 기준선 없음. 빈 상태 문구를 검증하는 쪽. */
export const EMPTY_DASHBOARD: Dashboard = {
  referenceDate: '2026-09-16',
  project: { id: 1, name: 'P', status: 'IN_PROGRESS', startDate: null, endDate: null, planStart: null, planEnd: null, forecastEnd: null },
  progress: { actualPercent: null, basis: null, workPackageCount: 0, notEstimableCount: 0, incomplete: false, plannedPercent: null, comparablePercent: null, variancePoints: null, acceptancePending: 0 },
  baseline: null,
  schedule: { delayedCount: 0, atRiskCount: 0, worstDelayDays: 0, criticalPathCount: 0, scheduleViolationCount: 0, delayed: [], baselineExceeded: [], acceptancePending: [] },
  execution: { activeSprint: null, backlogUnlinkedCount: 0 },
  velocity: [],
  workPackages: { total: 0, notEstimableCount: 0, acceptancePendingCount: 0, byExecutionMode: [] },
  control: { raciIssueCount: 0, missingAccountableCount: 0, missingResponsibleCount: 0, multipleAccountableCount: 0, openIssueCount: 0, highExposureCount: 0, overdueCount: 0, openIssues: [], highExposure: [], overdue: [] },
  scope: { hasBaseline: false, baselineItemCount: 0, currentItemCount: 0, added: [], removed: [], weightChanged: [] },
  gaps: [],
}

/** 모든 카드가 값을 갖는 프로젝트. 다른 spec 에서도 가져다 쓰라고 내보낸다. */
export const FULL_DASHBOARD: Dashboard = {
  ...EMPTY_DASHBOARD,
  project: { ...EMPTY_DASHBOARD.project, planStart: '2026-03-02', planEnd: '2026-12-18', forecastEnd: '2027-01-09' },
  progress: { actualPercent: 72.4, basis: 'ROLLUP', workPackageCount: 24, notEstimableCount: 1, incomplete: true, plannedPercent: 69.2, comparablePercent: 72.4, variancePoints: 3.24, acceptancePending: 2 },
  baseline: { id: 2, version: 2, approvedBy: '홍길동', approvedAt: '2026-09-01T09:00:00', note: null, itemCount: 42 },
  schedule: { delayedCount: 14, atRiskCount: 6, worstDelayDays: 12, criticalPathCount: 9, scheduleViolationCount: 1,
    delayed: [{ wbsItemId: 5, code: '2.1', name: 'API 설계', detail: '12일 지연' }],
    baselineExceeded: [{ wbsItemId: 7, code: '3.4', name: '통합 테스트', detail: '+5일' }],
    acceptancePending: [{ wbsItemId: 9, code: '4.1', name: '사용자 교육', detail: null }] },
  execution: { backlogUnlinkedCount: 3, activeSprint: { id: 5, name: 'Sprint 5', goal: '결제 연동', startDate: '2026-10-14', endDate: '2026-10-27', plannedItems: 12, plannedPoints: 32, doneItems: 8, donePoints: 21,
    blocked: [{ backlogItemId: 11, title: '결제 연동', reason: '외부 API', assigneeName: null, wbsItemId: 5, wbsCode: '2.1',
      raid: [{ raidItemId: 3, type: 'DEPENDENCY', title: 'PG 계약', ownerName: null, detail: null }] }] } },
  velocity: [
    { sprintId: 3, name: 'Sprint 3', startDate: '2026-09-01', endDate: '2026-09-14', status: 'CLOSED', donePoints: 18, doneItems: 7, carriedOverItems: 1, inProgress: false },
    { sprintId: 4, name: 'Sprint 4', startDate: '2026-09-15', endDate: '2026-09-28', status: 'CLOSED', donePoints: 32, doneItems: 11, carriedOverItems: 0, inProgress: false },
    { sprintId: 5, name: 'Sprint 5', startDate: '2026-10-14', endDate: '2026-10-27', status: 'ACTIVE', donePoints: 21, doneItems: 8, carriedOverItems: 0, inProgress: true },
  ],
  workPackages: { total: 24, notEstimableCount: 1, acceptancePendingCount: 2, byExecutionMode: [
    { mode: 'AGILE', count: 12 }, { mode: 'WATERFALL', count: 8 }, { mode: 'HYBRID', count: 3 }, { mode: null, count: 1 }] },
  control: { raciIssueCount: 6, missingAccountableCount: 3, missingResponsibleCount: 1, multipleAccountableCount: 2, openIssueCount: 4, highExposureCount: 2, overdueCount: 1,
    openIssues: [{ raidItemId: 1, type: 'ISSUE', title: '서버 장애', ownerName: '김', detail: null }],
    highExposure: [{ raidItemId: 2, type: 'RISK', title: '인력 이탈', ownerName: null, detail: '노출도 9' }],
    overdue: [{ raidItemId: 3, type: 'DEPENDENCY', title: 'PG 계약', ownerName: '이', detail: '3일 초과' }] },
  scope: { hasBaseline: true, baselineItemCount: 42, currentItemCount: 43, added: ['5.1'], removed: [], weightChanged: ['1.2'] },
  gaps: [{ kind: 'EXECUTION_MODE_UNSPECIFIED', label: '실행 방식 미지정', count: 1, wbsItemIds: [5], backlogItemIds: [] }],
}
