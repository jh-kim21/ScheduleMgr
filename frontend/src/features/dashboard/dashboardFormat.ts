import type { DataGap, RaidRef, SprintVelocity, TaskRef } from '../../api/dashboardApi'
import { roundPoints, varianceTone } from '../../shared/progress'
import type { Tone } from './icons'

/**
 * 대시보드 카드들이 함께 쓰는 순수 함수와 타입. 판정 · 문구를 컴포넌트 밖에 두는 이 저장소의
 * 관습을 그대로 따른다(rowSelection.ts · weightSuggestion.ts 와 같은 이유) — 규칙이 한 곳에
 * 고정되고 DOM 없이 검증할 수 있다.
 *
 * <p>여기에 산술을 새로 만들지 않는다. 서버가 낸 값을 어떻게 *적을지*만 정한다 — 대시보드가 자기
 * 식으로 계산하면 같은 프로젝트에 네 번째 의견이 생긴다(CLAUDE.md 대시보드 절).
 */

/** 같은 화면의 진척 탭. 카드에서 숫자의 근거를 고치러 갈 때 쓴다. */
export const PROGRESS_TAB = { path: '/dashboard', query: { tab: 'progress' } }

export function taskRoute(task: TaskRef) {
  return { path: '/wbs', query: { focus: task.wbsItemId } }
}

/** 데이터 누락이 향하는 화면. kind 마다 원인이 있는 곳이 다르다. */
export function gapRoute(gap: DataGap) {
  if (gap.kind.startsWith('BACKLOG')) return { path: '/backlog' }
  if (gap.kind === 'NOT_ESTIMABLE' || gap.kind === 'WEIGHT_MISSING') return PROGRESS_TAB
  return { path: '/wbs', query: { focus: gap.wbsItemIds[0] } }
}

export function raidRefLabel(entry: RaidRef): string {
  const owner = entry.ownerName ?? '소유자 미지정'
  return entry.detail ? `${entry.title} · ${entry.detail} · ${owner}` : `${entry.title} · ${owner}`
}

/**
 * 카드당 5건만 싣는 목록에 총건수가 더 있으면 그 사실을 적는다 — 그러지 않으면 6건째부터는
 * 조용히 사라진 것처럼 보인다. 전체는 상세 링크에서 본다.
 */
export function overflowSuffix(total: number, shown: number): string {
  return total > shown ? ` (${shown}건 표시 · 총 ${total}건)` : ''
}

/**
 * 편차를 문장으로. 부호를 잃지 않으려고 KPI 는 `+3.2%p` 로 적고, 상세 카드는 이 문장을 쓴다 —
 * 같은 값의 두 표기이므로 반올림을 `roundPoints` 한 곳에서 공유한다.
 */
export function varianceSentence(points: number | null): string | null {
  if (points === null) return null
  const rounded = roundPoints(points)
  if (rounded === 0) return '계획과 같음'
  return rounded > 0 ? `계획보다 ${rounded}%p 앞섬` : `계획보다 ${-rounded}%p 뒤짐`
}

/** 색을 고르는 것은 화면의 몫이지만, 방향 판정은 shared/progress.ts 한 곳에서만 한다. */
export function varianceToneClass(points: number | null): string | null {
  const tone = varianceTone(points)
  return tone === 'ahead' ? 'variance-ahead' : tone === 'behind' ? 'variance-behind' : null
}

/** 편차 KPI 의 아이콘 원 색. 앞섬 초록 · 뒤짐 빨강 · 계획과 같음과 미산정은 중립. */
export function varianceKpiTone(points: number | null): Tone {
  const tone = varianceTone(points)
  return tone === 'ahead' ? 'green' : tone === 'behind' ? 'red' : 'gray'
}

/** 종료된 Sprint 만 추세다. 진행 중인 것은 아직 움직이는 숫자라 옆에 따로 둔다. */
export function closedSprints(velocity: SprintVelocity[]): SprintVelocity[] {
  return velocity.filter((sprint) => !sprint.inProgress)
}

export function runningSprint(velocity: SprintVelocity[]): SprintVelocity | null {
  return velocity.find((sprint) => sprint.inProgress) ?? null
}

/** 속도 KPI 가 쓰는 값 — 가장 최근에 *끝난* Sprint. 없으면 아직 실적이 없다는 뜻이다. */
export function latestClosedSprint(velocity: SprintVelocity[]): SprintVelocity | null {
  const closed = closedSprints(velocity)
  return closed.length > 0 ? closed[closed.length - 1] : null
}

/**
 * BarList 한 줄. `<script setup>` 은 타입을 내보낼 수 없어서 여기에 둔다.
 *
 * <p>`total` 이 있으면 우측에 `n / m` 을 적고 percent 가 없을 때 비율의 분모가 된다. `total` 이
 * 0 이면 비율을 만들지 않는다 — 0 으로 나눈 NaN 이 막대 폭으로 흘러가면 화면이 조용히 깨진다.
 */
export interface BarListItem {
  key: string
  label: string
  value: number
  total?: number
  /** 직접 계산한 비율. `null` 은 산정 전이라 막대를 그리지 않는다. */
  percent?: number | null
  tone?: Tone
}

export function barPercent(item: BarListItem): number | null {
  if (item.percent !== undefined) return item.percent
  if (item.total === undefined || item.total <= 0) return null
  return (item.value / item.total) * 100
}
