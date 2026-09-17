// @vitest-environment happy-dom
import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import RiskCard from './RiskCard.vue'
import SprintCard from './SprintCard.vue'
import VelocityCard from './VelocityCard.vue'
import BaselineCard from './BaselineCard.vue'
import { EMPTY_DASHBOARD, FULL_DASHBOARD } from './dashboardFixture'

/**
 * 리디자인 전 인벤토리(dashboard-before.md)에서 "깨지기 쉬운 지점"으로 짚은 것들을 값 단위로
 * 고정한다. `dashboardCards.spec.ts`의 NaN/undefined 스모크 테스트와 겹치지 않게, 여기서는
 * 조건 분기 하나하나가 실제로 맞는 문구·값을 내는지 본다. 같은 파일의 fixture를 재사용해
 * 두 벌 만들지 않는다.
 */
const stubs = { RouterLink: { template: '<a><slot /></a>' } }

describe('RiskCard — overflowSuffix는 목록 길이가 아니라 서버 총건수를 쓴다', () => {
  it('총건수가 표시된 목록보다 많으면 "N건 표시 · 총 M건"을 적는다', () => {
    const data = {
      ...FULL_DASHBOARD,
      control: {
        ...FULL_DASHBOARD.control,
        overdueCount: 8, // 목록(overdue)은 fixture 그대로 1건 — 서버가 5건 넘게 잘랐다고 가정
      },
    }
    const wrapper = mount(RiskCard, { props: { data }, global: { stubs } })
    expect(wrapper.text()).toContain('1건 표시 · 총 8건')
  })

  it('총건수와 표시 건수가 같으면(안 잘렸으면) 그 소제목엔 접미사를 붙이지 않는다', () => {
    // FULL_DASHBOARD는 세 분류 중 "기한 초과"만 count(1)와 목록 길이(1)가 같다 — 그 줄만 본다.
    const wrapper = mount(RiskCard, { props: { data: FULL_DASHBOARD }, global: { stubs } })
    const overdueHeading = wrapper.findAll('.subhead').find((el) => el.text().startsWith('기한 초과'))
    expect(overdueHeading?.text()).toBe('기한 초과')
  })

  it('셋 다 0이면 "열린 위험·이슈가 없습니다"로 대체하고 칩만 남는다', () => {
    const wrapper = mount(RiskCard, { props: { data: EMPTY_DASHBOARD }, global: { stubs } })
    expect(wrapper.text()).toContain('열린 위험·이슈가 없습니다')
  })
})

describe('VelocityCard — 0 분모 방어', () => {
  it('종료된 Sprint가 전부 0포인트여도 NaN 없이 막대를 그린다(peak 바닥 1)', () => {
    const data = {
      ...FULL_DASHBOARD,
      velocity: [
        { sprintId: 1, name: 'S1', startDate: '2026-01-01', endDate: '2026-01-14', status: 'CLOSED' as const, donePoints: 0, doneItems: 0, carriedOverItems: 0, inProgress: false },
        { sprintId: 2, name: 'S2', startDate: '2026-01-15', endDate: '2026-01-28', status: 'CLOSED' as const, donePoints: 0, doneItems: 0, carriedOverItems: 0, inProgress: false },
      ],
    }
    const wrapper = mount(VelocityCard, { props: { data }, global: { stubs } })
    expect(wrapper.text()).not.toContain('NaN')
    const bars = wrapper.findAll('.bar')
    expect(bars.length).toBe(2)
    for (const bar of bars) {
      expect(bar.attributes('style')).toContain('height: 0%')
    }
  })

  it('종료된 Sprint가 없으면 "종료된 Sprint가 아직 없습니다"를 그대로 쓴다', () => {
    const wrapper = mount(VelocityCard, { props: { data: EMPTY_DASHBOARD }, global: { stubs } })
    expect(wrapper.text()).toContain('종료된 Sprint가 아직 없습니다.')
  })
})

describe('BaselineCard — scopeChanged는 hasBaseline을 먼저 본다', () => {
  it('기준선 자체가 없으면 scope.added가 있어도 범위 변화 경고를 내지 않는다', () => {
    const data = {
      ...EMPTY_DASHBOARD,
      baseline: null,
      scope: { hasBaseline: false, baselineItemCount: 0, currentItemCount: 5, added: ['1.1', '1.2'], removed: [], weightChanged: [] },
    }
    const wrapper = mount(BaselineCard, { props: { data }, global: { stubs } })
    expect(wrapper.text()).not.toContain('범위가 바뀌었습니다')
    expect(wrapper.text()).toContain('승인된 기준선이 없습니다')
  })

  it('기준선이 있고 범위가 실제로 바뀌었으면 경고를 낸다', () => {
    const wrapper = mount(BaselineCard, { props: { data: FULL_DASHBOARD }, global: { stubs } })
    expect(wrapper.text()).toContain('범위가 바뀌었습니다')
  })
})

describe('SprintCard — 차단 항목 안의 2단 중첩 RAID 목록', () => {
  it('차단 항목에 RAID 참조가 있으면 유형 라벨과 제목을 함께 그린다', () => {
    const wrapper = mount(SprintCard, { props: { data: FULL_DASHBOARD }, global: { stubs } })
    // FULL_DASHBOARD.execution.activeSprint.blocked[0].raid[0] = { type: 'DEPENDENCY', title: 'PG 계약', ownerName: null }
    expect(wrapper.text()).toContain('PG 계약')
    expect(wrapper.text()).toContain('소유자 미지정')
  })

  it('차단 항목에 RAID 참조가 없으면 중첩 목록 자체가 없다', () => {
    const data = {
      ...FULL_DASHBOARD,
      execution: {
        ...FULL_DASHBOARD.execution,
        activeSprint: FULL_DASHBOARD.execution.activeSprint && {
          ...FULL_DASHBOARD.execution.activeSprint,
          blocked: [
            { ...FULL_DASHBOARD.execution.activeSprint.blocked[0], raid: [] },
          ],
        },
      },
    }
    const wrapper = mount(SprintCard, { props: { data }, global: { stubs } })
    expect(wrapper.find('.refs').exists()).toBe(false)
  })
})
