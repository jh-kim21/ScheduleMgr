// @vitest-environment happy-dom
import { mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import type { WbsNode } from '../../api/wbsApi'
import { resetColumnPrefs, setColumnPrefs } from './wbsColumnPrefs'
import WbsTree from './WbsTree.vue'

/**
 * 지시서 `wbs-tree-density` 3-D — 행 밀도 클래스와 도움말 토글이 실제 표에 반영되는지 본다.
 * `normalizePrefs`/`defaultPrefs`의 판정 자체는 `wbsColumns.spec.ts`가 순수 함수로 덮으므로,
 * 여기서는 그 값을 받은 `WbsTree`가 옳은 DOM을 그리는지만 본다.
 *
 * `columnPrefs`는 모듈 스코프라 테스트끼리 상태가 샌다 — `WbsTreeColumns.spec.ts`와 같은 이유로
 * 매 테스트 앞뒤에 `resetColumnPrefs()`를 부른다.
 */
const router = createRouter({
  history: createMemoryHistory(),
  routes: [
    { path: '/', component: { template: '<div />' } },
    { path: '/raci', component: { template: '<div />' } },
    { path: '/backlog', component: { template: '<div />' } },
  ],
})

function node(overrides: Partial<WbsNode> = {}): WbsNode {
  return {
    id: 1,
    parentId: null,
    code: '1',
    level: 1,
    name: '요구사항 정의',
    description: null,
    startDate: '2026-03-01',
    endDate: '2026-03-31',
    progress: 20,
    summary: false,
    nodeType: 'WORK_PACKAGE',
    executionMode: null,
    executionModeSummary: null,
    backlogSummary: null,
    weight: null,
    agileRatio: null,
    acceptanceStatus: null,
    computedProgress: 20,
    progressBasis: 'MANUAL',
    progressIncomplete: false,
    progressNote: null,
    acceptancePending: false,
    delayStatus: 'ON_TRACK',
    expectedProgress: 20,
    progressGap: 0,
    delayDays: 0,
    responsible: [],
    responsibleInherited: [],
    tags: [],
    tagSummary: null,
    children: [],
    ...overrides,
  }
}

function render(tree: WbsNode[], props: Record<string, unknown> = {}) {
  return mount(WbsTree, {
    props: { tree, projectId: 7, ...props },
    global: { plugins: [router] },
  })
}

describe('WbsTree — 행 밀도', () => {
  let wrapper: VueWrapper | undefined

  beforeEach(() => {
    resetColumnPrefs()
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
    resetColumnPrefs()
  })

  it("density: 'dense'면 표에 .dense 클래스가 붙는다", () => {
    setColumnPrefs({ hidden: [], pin: 'none', density: 'dense' })
    wrapper = render([node()])

    expect(wrapper.get('table.wbs-tree').classes()).toContain('dense')
  })

  it("density: 'normal'(기본값)이면 .dense 클래스가 없다", () => {
    wrapper = render([node()])

    expect(wrapper.get('table.wbs-tree').classes()).not.toContain('dense')
  })
})

describe('WbsTree — 도움말 토글(?)', () => {
  let wrapper: VueWrapper | undefined

  afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
  })

  it('기본은 접혀 있어 설명문이 없다', () => {
    wrapper = render([node()])

    expect(wrapper.find('[data-action="help"]').attributes('aria-expanded')).toBe('false')
    expect(wrapper.find('.legend').exists()).toBe(false)
  })

  it('누르면 펼쳐지고, 최상위로 옮기는 방법과 Alt+화살표가 적혀 있다 — 드롭존이 하던 안내를 대신 받는다', async () => {
    wrapper = render([node()])

    await wrapper.get('[data-action="help"]').trigger('click')

    expect(wrapper.get('[data-action="help"]').attributes('aria-expanded')).toBe('true')
    const legend = wrapper.get('.legend')
    expect(legend.text()).toContain('최상위로 옮기기')
    expect(legend.text()).toContain('Alt+←')
  })

  it('다시 누르면 접힌다', async () => {
    wrapper = render([node()])

    await wrapper.get('[data-action="help"]').trigger('click')
    expect(wrapper.find('.legend').exists()).toBe(true)

    await wrapper.get('[data-action="help"]').trigger('click')
    expect(wrapper.find('.legend').exists()).toBe(false)
  })

  it('필터가 걸리면 도움말이 접힌 채로도 필터 문구가 보인다 — 이건 도움말이 아니라 왜 드래그가 안 되는지에 대한 답이다', async () => {
    const design = node({ id: 1, code: '1', name: '설계', children: [] })
    const screen = node({ id: 2, parentId: 1, code: '1.1', name: '화면 설계' })
    wrapper = render([{ ...design, children: [screen] }])

    await wrapper.get('[data-action="filter"]').trigger('click')
    await wrapper
      .get<HTMLInputElement>('td[data-filter="name"] input[type="search"]')
      .setValue('화면')

    expect(wrapper.get('[data-action="help"]').attributes('aria-expanded')).toBe('false')
    expect(wrapper.get('.legend').text()).toContain('필터가 걸려 있는 동안에는 순서를 바꿀 수 없습니다')
  })

  it('필터 중에는 도움말을 펼쳐도 도움말 대신 필터 문구가 보인다', async () => {
    const design = node({ id: 1, code: '1', name: '설계', children: [] })
    const screen = node({ id: 2, parentId: 1, code: '1.1', name: '화면 설계' })
    wrapper = render([{ ...design, children: [screen] }])

    await wrapper.get('[data-action="filter"]').trigger('click')
    await wrapper
      .get<HTMLInputElement>('td[data-filter="name"] input[type="search"]')
      .setValue('화면')
    await wrapper.get('[data-action="help"]').trigger('click')

    expect(wrapper.get('.legend').text()).toContain('필터가 걸려 있는 동안에는 순서를 바꿀 수 없습니다')
    expect(wrapper.get('.legend').text()).not.toContain('최상위로 옮기기')
  })
})
