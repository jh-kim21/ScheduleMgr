// @vitest-environment happy-dom
import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import DashboardHeader from './DashboardHeader.vue'

const project = {
  id: 1,
  name: 'P',
  status: 'IN_PROGRESS' as const,
  startDate: null,
  endDate: null,
  description: null,
  createdAt: '2026-01-01T00:00:00',
  updatedAt: '2026-01-01T00:00:00',
}

function mountHeader(tab: 'summary' | 'progress' | 'workload') {
  return mount(DashboardHeader, {
    props: { projects: [project], tab, referenceDate: '2026-09-28', loading: false },
  })
}

/**
 * 탭이 둘에서 셋으로 늘면서 `onTabsKeydown`이 `TAB_ORDER`를 순환하는 방식으로 바뀌었다 —
 * 이 스위트는 그 순환이 세 자리에서 양방향으로 정확히 도는지 고정한다.
 */
describe('DashboardHeader — 탭 셋의 방향키 순환', () => {
  it('요약에서 오른쪽 → 진척, 진척에서 오른쪽 → 부하, 부하에서 오른쪽 → 다시 요약', async () => {
    const wrapper = mountHeader('summary')
    await wrapper.find('[role="tablist"]').trigger('keydown', { key: 'ArrowRight' })
    expect(wrapper.emitted('update:tab')?.[0]).toEqual(['progress'])
  })

  it('요약에서 왼쪽 → 부하로 간다(반대 방향으로도 순환한다)', async () => {
    const wrapper = mountHeader('summary')
    await wrapper.find('[role="tablist"]').trigger('keydown', { key: 'ArrowLeft' })
    expect(wrapper.emitted('update:tab')?.[0]).toEqual(['workload'])
  })

  it('부하에서 오른쪽 → 요약으로 돌아온다', async () => {
    const wrapper = mountHeader('workload')
    await wrapper.find('[role="tablist"]').trigger('keydown', { key: 'ArrowRight' })
    expect(wrapper.emitted('update:tab')?.[0]).toEqual(['summary'])
  })

  it('진척에서 오른쪽 → 부하로 간다', async () => {
    const wrapper = mountHeader('progress')
    await wrapper.find('[role="tablist"]').trigger('keydown', { key: 'ArrowRight' })
    expect(wrapper.emitted('update:tab')?.[0]).toEqual(['workload'])
  })

  it('탭 키가 아니면 아무것도 하지 않는다', async () => {
    const wrapper = mountHeader('summary')
    await wrapper.find('[role="tablist"]').trigger('keydown', { key: 'Enter' })
    expect(wrapper.emitted('update:tab')).toBeUndefined()
  })
})

describe('DashboardHeader — 새로고침 버튼', () => {
  it('요약·부하 탭에서는 보이고 진척 탭에서는 숨는다(진척은 자기 composable을 쓴다)', () => {
    expect(mountHeader('summary').find('.refresh').exists()).toBe(true)
    expect(mountHeader('workload').find('.refresh').exists()).toBe(true)
    expect(mountHeader('progress').find('.refresh').exists()).toBe(false)
  })
})

describe('DashboardHeader — aria 배선', () => {
  it('부하 탭 버튼이 대응하는 패널 id를 가리킨다', () => {
    const wrapper = mountHeader('workload')
    const tab = wrapper.get('#dashboard-tab-workload')
    expect(tab.attributes('aria-controls')).toBe('dashboard-panel-workload')
    expect(tab.attributes('aria-selected')).toBe('true')
  })
})
