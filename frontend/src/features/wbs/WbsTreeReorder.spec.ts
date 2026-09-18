// @vitest-environment happy-dom
import { mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, describe, expect, it } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import type { WbsNode } from '../../api/wbsApi'
import WbsTree from './WbsTree.vue'

/**
 * 드래그 앤 드롭 재정렬·재부모화의 키보드·비-마우스 대안(WCAG 2.5.7·2.1.1) — 툴바 네 버튼과
 * Alt+화살표 단축키가 `wbsMove.ts`의 판정을 올바르게 emit·disabled로 옮기는지 고정한다.
 * 판정 자체(`moveAvailability`/`moveState`/각 `*Input`)는 `wbsMove.spec.ts`가 순수 함수로
 * 덮으므로, 여기서는 DOM 상호작용만 본다 — `WbsTreeToolbar.spec.ts`와 같은 분리다.
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

function moveButtons(wrapper: VueWrapper) {
  return {
    up: wrapper.get('[data-action="move-up"]'),
    down: wrapper.get('[data-action="move-down"]'),
    indent: wrapper.get('[data-action="indent"]'),
    outdent: wrapper.get('[data-action="outdent"]'),
  }
}

describe('WbsTree — 이동 툴바(드래그 대안)', () => {
  let wrapper: VueWrapper | undefined

  afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
  })

  it('선택이 없으면 네 버튼 모두 disabled다', () => {
    wrapper = render([node({ id: 1 }), node({ id: 2, code: '2' })])

    const { up, down, indent, outdent } = moveButtons(wrapper)
    expect(up.attributes('disabled')).toBeDefined()
    expect(down.attributes('disabled')).toBeDefined()
    expect(indent.attributes('disabled')).toBeDefined()
    expect(outdent.attributes('disabled')).toBeDefined()
  })

  it('가운데 형제를 고르면 위/아래가 활성이고, 클릭하면 move가 emit된다', async () => {
    const first = node({ id: 1, code: '1' })
    const middle = node({ id: 2, code: '2' })
    const last = node({ id: 3, code: '3' })
    wrapper = render([first, middle, last])

    await wrapper.get('tr[data-row-id="2"]').trigger('click')
    const { up, down } = moveButtons(wrapper)
    expect(up.attributes('disabled')).toBeUndefined()
    expect(down.attributes('disabled')).toBeUndefined()

    await up.trigger('click')
    expect(wrapper.emitted('move')?.[0]).toEqual([2, { parentId: null, position: 0 }])

    await down.trigger('click')
    expect(wrapper.emitted('move')?.[1]).toEqual([2, { parentId: null, position: 2 }])
  })

  it('맨 위 행은 위로가 비활성이고 title이 이유를 말한다', async () => {
    wrapper = render([node({ id: 1, code: '1' }), node({ id: 2, code: '2' })])

    await wrapper.get('tr[data-row-id="1"]').trigger('click')
    const { up } = moveButtons(wrapper)
    expect(up.attributes('disabled')).toBeDefined()
    expect(up.attributes('title')).toBe('이미 첫 행입니다.')
  })

  it('바로 위 형제가 SUMMARY면 들여쓰기가 가능하고, 클릭하면 그 자식이 된다', async () => {
    const summary = node({ id: 1, code: '1', nodeType: 'SUMMARY', children: [node({ id: 9, code: '1.1' })] })
    const target = node({ id: 2, code: '2' })
    wrapper = render([summary, target])

    await wrapper.get('tr[data-row-id="2"]').trigger('click')
    const { indent } = moveButtons(wrapper)
    expect(indent.attributes('disabled')).toBeUndefined()

    await indent.trigger('click')
    expect(wrapper.emitted('move')?.[0]).toEqual([2, { parentId: 1, position: 1 }])
  })

  it('바로 위 형제가 Work Package면 들여쓰기가 비활성이고 이유를 말한다', async () => {
    wrapper = render([node({ id: 1, code: '1' }), node({ id: 2, code: '2' })])

    await wrapper.get('tr[data-row-id="2"]').trigger('click')
    const { indent } = moveButtons(wrapper)
    expect(indent.attributes('disabled')).toBeDefined()
    expect(indent.attributes('title')).toContain('Work Package')
  })

  it('최상위 항목은 내어쓰기가 비활성이고, 하위 항목은 활성이다', async () => {
    const child = node({ id: 9, code: '1.1' })
    const summary = node({ id: 1, code: '1', nodeType: 'SUMMARY', children: [child] })
    wrapper = render([summary])

    await wrapper.get('tr[data-row-id="1"]').trigger('click')
    expect(moveButtons(wrapper).outdent.attributes('disabled')).toBeDefined()

    await wrapper.get('tr[data-row-id="9"]').trigger('click')
    expect(moveButtons(wrapper).outdent.attributes('disabled')).toBeUndefined()

    await moveButtons(wrapper).outdent.trigger('click')
    expect(wrapper.emitted('move')?.[0]).toEqual([9, { parentId: null, position: 1 }])
  })

  it('readOnly면 선택해도 네 버튼 모두 비활성이다', async () => {
    wrapper = render([node({ id: 1 }), node({ id: 2, code: '2' })], { readOnly: true })

    await wrapper.get('tr[data-row-id="2"]').trigger('click')
    const { up, down, indent, outdent } = moveButtons(wrapper)
    expect(up.attributes('disabled')).toBeDefined()
    expect(down.attributes('disabled')).toBeDefined()
    expect(indent.attributes('disabled')).toBeDefined()
    expect(outdent.attributes('disabled')).toBeDefined()
  })

  it('Alt+ArrowUp/Down 은 같은 move를 tbody 키다운으로도 emit한다', async () => {
    const first = node({ id: 1, code: '1' })
    const middle = node({ id: 2, code: '2' })
    const last = node({ id: 3, code: '3' })
    wrapper = render([first, middle, last])

    await wrapper.get('tr[data-row-id="2"]').trigger('click')
    await wrapper.get('tbody').trigger('keydown', { key: 'ArrowUp', altKey: true })
    expect(wrapper.emitted('move')?.[0]).toEqual([2, { parentId: null, position: 0 }])
  })

  it('Alt+ArrowRight/Left 는 들여쓰기/내어쓰기를 emit한다', async () => {
    const summary = node({ id: 1, code: '1', nodeType: 'SUMMARY', children: [node({ id: 9, code: '1.1' })] })
    const target = node({ id: 2, code: '2' })
    wrapper = render([summary, target])

    await wrapper.get('tr[data-row-id="2"]').trigger('click')
    await wrapper.get('tbody').trigger('keydown', { key: 'ArrowRight', altKey: true })
    expect(wrapper.emitted('move')?.[0]).toEqual([2, { parentId: 1, position: 1 }])
  })

  it('일반 화살표(Alt 없이)는 여전히 행 선택 탐색이지 이동이 아니다', async () => {
    const first = node({ id: 1, code: '1' })
    const second = node({ id: 2, code: '2' })
    wrapper = render([first, second])

    await wrapper.get('tr[data-row-id="1"]').trigger('click')
    await wrapper.get('tbody').trigger('keydown', { key: 'ArrowDown' })
    expect(wrapper.emitted('move')).toBeUndefined()
    expect(wrapper.get('.selected-label').text()).toBe('선택: 2 요구사항 정의')
  })
})
