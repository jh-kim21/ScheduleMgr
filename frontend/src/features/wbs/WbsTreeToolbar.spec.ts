// @vitest-environment happy-dom
import { mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, describe, expect, it } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import type { WbsNode } from '../../api/wbsApi'
import WbsTree from './WbsTree.vue'

/**
 * 지시서 `wbs-tree-toolbar` 3-3절 — 행 액션이 툴바로 옮겨진 뒤의 렌더링·상호작용을 고정한다.
 * 판정 자체(`toolbarState`)는 `wbsToolbar.spec.ts`(Frontend1 담당)가 순수 함수로 덮으므로, 여기서는
 * 그 판정이 DOM에 올바르게 반영되는지만 본다 — 고정 계약(팀 리더 지시서 발췌)에 나온 셀렉터
 * (`[data-action]`, `.selected-label`, `tr[data-row-id]`)를 그대로 쓴다.
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

const READONLY_HINT = '커밋 시점 조회 중에는 변경할 수 없습니다'

function buttons(wrapper: VueWrapper) {
  return {
    addChild: wrapper.get('[data-action="add-child"]'),
    edit: wrapper.get('[data-action="edit"]'),
    remove: wrapper.get('[data-action="remove"]'),
  }
}

describe('WbsTree — 툴바', () => {
  let wrapper: VueWrapper | undefined

  afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
  })

  it('선택이 없으면 세 버튼이 모두 disabled다', () => {
    wrapper = render([node()])

    const { addChild, edit, remove } = buttons(wrapper)
    expect(addChild.attributes('disabled')).toBeDefined()
    expect(edit.attributes('disabled')).toBeDefined()
    expect(remove.attributes('disabled')).toBeDefined()
    expect(wrapper.get('.selected-label').text()).toBe('행을 고르세요')
  })

  it('행을 클릭하면 수정·삭제가 활성되고, 버튼이 그 행의 노드로 emit 한다', async () => {
    const target = node({ id: 2, code: '1.1', name: '화면 설계' })
    wrapper = render([node({ children: [target] })])

    await wrapper.get('tr[data-row-id="2"]').trigger('click')

    const { edit, remove } = buttons(wrapper)
    expect(edit.attributes('disabled')).toBeUndefined()
    expect(remove.attributes('disabled')).toBeUndefined()
    expect(wrapper.get('.selected-label').text()).toBe('선택: 1.1 화면 설계')

    await edit.trigger('click')
    expect(wrapper.emitted('edit')?.[0]).toEqual([target])

    await remove.trigger('click')
    expect(wrapper.emitted('remove')?.[0]).toEqual([target])
  })

  it('선택 없을 때 title이 이유를 말한다', () => {
    wrapper = render([node()])

    const { addChild, edit, remove } = buttons(wrapper)
    expect(addChild.attributes('title')).toBe('행을 먼저 고르세요')
    expect(edit.attributes('title')).toBe('행을 먼저 고르세요')
    expect(remove.attributes('title')).toBe('행을 먼저 고르세요')
  })

  it('Work Package 행을 고르면 하위 추가만 disabled이고 title이 이유를 말한다', async () => {
    wrapper = render([node({ nodeType: 'WORK_PACKAGE' })])

    await wrapper.get('tr[data-row-id="1"]').trigger('click')

    const { addChild, edit, remove } = buttons(wrapper)
    expect(addChild.attributes('disabled')).toBeDefined()
    expect(addChild.attributes('title')).toBe(
      'Work Package에는 하위 항목을 둘 수 없습니다. 수정에서 구분을 Summary로 바꾸세요.',
    )
    expect(edit.attributes('disabled')).toBeUndefined()
    expect(remove.attributes('disabled')).toBeUndefined()
  })

  it('Summary 행을 고르면 하위 추가도 활성이다', async () => {
    wrapper = render([node({ nodeType: 'SUMMARY', children: [node({ id: 2, code: '1.1' })] })])

    await wrapper.get('tr[data-row-id="1"]').trigger('click')

    const { addChild } = buttons(wrapper)
    expect(addChild.attributes('disabled')).toBeUndefined()
  })

  it('readOnly면 선택해도 세 버튼이 모두 disabled이고 title이 READONLY_HINT다', async () => {
    wrapper = render([node()], { readOnly: true })

    await wrapper.get('tr[data-row-id="1"]').trigger('click')

    const { addChild, edit, remove } = buttons(wrapper)
    expect(addChild.attributes('disabled')).toBeDefined()
    expect(edit.attributes('disabled')).toBeDefined()
    expect(remove.attributes('disabled')).toBeDefined()
    expect(addChild.attributes('title')).toBe(READONLY_HINT)
    expect(edit.attributes('title')).toBe(READONLY_HINT)
    expect(remove.attributes('title')).toBe(READONLY_HINT)
  })

  it('행 안에는 액션 버튼이 없다 — thead th가 10개이고 행에 td.actions가 없다', () => {
    wrapper = render([node()])

    expect(wrapper.findAll('thead th')).toHaveLength(10)
    expect(wrapper.find('tr[data-row-id="1"] td.actions').exists()).toBe(false)
  })

  it('조상이 접힌 상태에서도 툴바가 선택한 행을 계속 가리킨다', async () => {
    const child = node({ id: 2, code: '1.1', name: '화면 설계' })
    const parent = node({ id: 1, code: '1', name: '설계', children: [child] })
    wrapper = render([parent])

    await wrapper.get('tr[data-row-id="2"]').trigger('click')
    expect(wrapper.get('.selected-label').text()).toBe('선택: 1.1 화면 설계')

    // 부모 행의 토글(▼)을 눌러 접는다 — 자식 행이 rows에서 사라진다.
    await wrapper.get('tr[data-row-id="1"] .toggle').trigger('click')
    expect(wrapper.find('tr[data-row-id="2"]').exists()).toBe(false)

    // 그래도 툴바는 여전히 그 자식을 가리켜야 한다 (rows가 아니라 트리에서 찾는다는 결정).
    expect(wrapper.get('.selected-label').text()).toBe('선택: 1.1 화면 설계')

    const { edit } = buttons(wrapper)
    expect(edit.attributes('disabled')).toBeUndefined()
    await edit.trigger('click')
    expect(wrapper.emitted('edit')?.[0]).toEqual([child])
  })
})
