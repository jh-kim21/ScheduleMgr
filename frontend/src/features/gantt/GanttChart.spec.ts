// @vitest-environment happy-dom
import { mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, describe, expect, it } from 'vitest'
import type { GanttData, GanttTask } from '../../api/ganttApi'
import GanttChart from './GanttChart.vue'

/**
 * 막대 상세의 키보드 접근(WCAG 2.1.1) — Tab만 쓰는 사용자는 SVG의 `rect.bar`에 닿을 수 없으므로
 * (수백 개를 tab stop으로 만드는 것도 그것대로 문제다), 이미 tabindex가 있는 과업 패널 행
 * (`.task-row`)의 포커스에 같은 상세를 얹는다(`onTaskFocus`) — 마우스 툴팁을 재사용하고,
 * 좌표만 그 행의 위치로 계산한다. `aria-label`은 포커스 이동 여부와 무관하게 항상 전체 상세를
 * 담아, 스크린리더 브라우즈 커서로도 같은 정보에 닿을 수 있게 한다.
 */
function task(overrides: Partial<GanttTask> = {}): GanttTask {
  return {
    id: 1,
    parentId: null,
    code: '1',
    level: 1,
    name: '요구사항 정의',
    summary: false,
    endDate: '2026-03-10',
    earliestStart: null,
    scheduleViolation: false,
    floatDays: null,
    criticalPath: false,
    baselineStart: null,
    baselineEnd: null,
    actualStart: null,
    actualEnd: null,
    forecastEnd: null,
    baselineExceeded: false,
    baselineSlipDays: 0,
    computedProgress: 40,
    progressBasis: 'MANUAL',
    acceptancePending: false,
    sprintIds: [],
    delayStatus: 'ON_TRACK',
    expectedProgress: 40,
    progressGap: 0,
    delayDays: 0,
    progress: 40,
    startDate: '2026-03-01',
    ...overrides,
  }
}

function ganttData(tasks: GanttTask[]): GanttData {
  return {
    chartStart: '2026-03-01',
    chartEnd: '2026-03-10',
    referenceDate: '2026-03-05',
    hasBaseline: false,
    baselineVersion: null,
    tasks,
    dependencies: [],
    sprints: [],
  }
}

function render(tasks: GanttTask[]) {
  return mount(GanttChart, { props: { data: ganttData(tasks) } })
}

describe('GanttChart — 막대 상세의 키보드 접근', () => {
  let wrapper: VueWrapper | undefined

  afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
  })

  it('과업 행은 tabindex가 있고 aria-label에 전체 상세가 담긴다', () => {
    wrapper = render([task({ floatDays: 2 })])

    const row = wrapper.get('.task-row')
    expect(row.attributes('tabindex')).toBe('0')
    const label = row.attributes('aria-label')!
    expect(label).toContain('1 요구사항 정의')
    expect(label).toContain('2026-03-01 ~ 2026-03-10')
    expect(label).toContain('여유 2일')
  })

  it('행에 포커스하면 마우스 툴팁과 같은 상세가 뜬다', async () => {
    wrapper = render([task()])

    await wrapper.get('.task-row').trigger('focus')

    const tooltip = wrapper.get('.tooltip')
    expect(tooltip.get('.tooltip-name').text()).toBe('1 요구사항 정의')
  })

  it('포커스를 잃으면 툴팁이 사라진다', async () => {
    wrapper = render([task()])

    await wrapper.get('.task-row').trigger('focus')
    expect(wrapper.find('.tooltip').exists()).toBe(true)

    await wrapper.get('.task-row').trigger('blur')
    expect(wrapper.find('.tooltip').exists()).toBe(false)
  })

  it('일정이 없는(막대가 없는) 행은 포커스해도 툴팁을 띄우지 않는다', async () => {
    wrapper = render([task({ startDate: null, endDate: null })])

    await wrapper.get('.task-row').trigger('focus')

    expect(wrapper.find('.tooltip').exists()).toBe(false)
  })

  it('기준 종료일 초과·인수 대기 등 마우스 툴팁의 항목이 aria-label에도 있다', () => {
    wrapper = render([
      task({
        acceptancePending: true,
        scheduleViolation: true,
        earliestStart: '2026-03-03',
      }),
    ])

    const label = wrapper.get('.task-row').attributes('aria-label')!
    expect(label).toContain('실행 100% · 인수 대기')
    expect(label).toContain('가장 이른 시작 2026-03-03')
  })
})
