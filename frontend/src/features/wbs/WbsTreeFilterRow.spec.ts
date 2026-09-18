// @vitest-environment happy-dom
import { mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import type { WbsNode } from '../../api/wbsApi'
import { resetColumnPrefs, setColumnPrefs } from './wbsColumnPrefs'
import WbsTree from './WbsTree.vue'

/**
 * 지시서 `wbs-tree-filter` 3-5절 — 열별 값 필터가 실제 표에 반영되는지 본다. 판정 자체
 * (`filterTree`·`filterOptions`의 마디 단위 일치, AND/OR, Summary 보관값·요약 제외 등)는
 * `wbsTreeFilter.spec.ts`(Frontend3 담당, 순수 함수)가 덮으므로, 여기서는 그 판정을 받은
 * `WbsTree`가 옳은 DOM을 그리는지, 그리고 **드래그를 잠그는지**(2-2, 이 작업에서 가장 중요한
 * 부분)만 본다. 고정 계약(팀 리더 지시서 발췌)에 나온 셀렉터를 그대로 쓴다.
 *
 * 파일명이 `wbsTreeFilter.spec.ts`가 아니라 `WbsTreeFilterRow.spec.ts`인 이유: Windows는
 * 파일시스템이 대소문자를 구분하지 않아 `wbsTreeFilter.spec.ts`(순수 함수, Frontend3)와
 * `WbsTreeFilter.spec.ts`(컴포넌트, 이 파일의 전신)가 같은 파일로 겹쳤다 — 팀 리드가 이름을
 * 다시 배정했다.
 *
 * `columnPrefs`는 모듈 스코프라 테스트끼리 상태가 샌다 — 열 구성을 건드리는 테스트 앞에서
 * `resetColumnPrefs()`를 부른다(`WbsTreeColumns.spec.ts`와 같은 이유).
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

/** 툴바의 [필터] 토글을 누른다. */
async function openFilterRow(wrapper: VueWrapper) {
  await wrapper.get('[data-action="filter"]').trigger('click')
}

describe('WbsTree — 열별 값 필터', () => {
  let wrapper: VueWrapper | undefined

  beforeEach(() => {
    resetColumnPrefs()
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
    resetColumnPrefs()
  })

  it('[필터]를 누르면 필터 행이 나타나고, 다시 누르면 사라진다', async () => {
    wrapper = render([node()])

    expect(wrapper.find('tr.filter-row').exists()).toBe(false)

    await openFilterRow(wrapper)
    expect(wrapper.find('tr.filter-row').exists()).toBe(true)

    await openFilterRow(wrapper)
    expect(wrapper.find('tr.filter-row').exists()).toBe(false)
  })

  it('업무명 칸에 글자를 치면 행 수가 줄고 조상이 남는다', async () => {
    const design = node({ id: 1, code: '1', name: '설계', children: [] })
    const screen = node({ id: 2, parentId: 1, code: '1.1', name: '화면 설계' })
    const other = node({ id: 3, parentId: 1, code: '1.2', name: '기타 업무' })
    wrapper = render([{ ...design, children: [screen, other] }])

    expect(wrapper.findAll('tr[data-row-id]')).toHaveLength(3)

    await openFilterRow(wrapper)
    await wrapper
      .get<HTMLInputElement>('td[data-filter="name"] input[type="search"]')
      .setValue('화면')

    const rowIds = wrapper.findAll('tr[data-row-id]').map((row) => row.attributes('data-row-id'))
    expect(rowIds).toEqual(['1', '2'])
  })

  it('문맥 행에 context 클래스가 붙고 일치 행에는 붙지 않는다', async () => {
    const design = node({ id: 1, code: '1', name: '설계', children: [] })
    const screen = node({ id: 2, parentId: 1, code: '1.1', name: '화면 설계' })
    wrapper = render([{ ...design, children: [screen] }])

    await openFilterRow(wrapper)
    await wrapper
      .get<HTMLInputElement>('td[data-filter="name"] input[type="search"]')
      .setValue('화면')

    expect(wrapper.get('tr[data-row-id="1"]').classes()).toContain('context')
    expect(wrapper.get('tr[data-row-id="2"]').classes()).not.toContain('context')
  })

  it('필터가 걸리면 draggable 행이 하나도 없고 root-dropzone이 사라진다', async () => {
    const design = node({ id: 1, code: '1', name: '설계', children: [] })
    const screen = node({ id: 2, parentId: 1, code: '1.1', name: '화면 설계' })
    wrapper = render([{ ...design, children: [screen] }])

    // 필터 전: 드래그 가능하고 드롭존이 있다.
    expect(wrapper.findAll('tr[draggable="true"]').length).toBeGreaterThan(0)
    expect(wrapper.find('.root-dropzone').exists()).toBe(true)

    await openFilterRow(wrapper)
    await wrapper
      .get<HTMLInputElement>('td[data-filter="name"] input[type="search"]')
      .setValue('화면')

    expect(wrapper.findAll('tr[draggable="true"]')).toHaveLength(0)
    expect(wrapper.find('.root-dropzone').exists()).toBe(false)
  })

  it('필터를 풀면 드래그가 돌아온다', async () => {
    const design = node({ id: 1, code: '1', name: '설계', children: [] })
    const screen = node({ id: 2, parentId: 1, code: '1.1', name: '화면 설계' })
    wrapper = render([{ ...design, children: [screen] }])

    await openFilterRow(wrapper)
    await wrapper
      .get<HTMLInputElement>('td[data-filter="name"] input[type="search"]')
      .setValue('화면')
    expect(wrapper.findAll('tr[draggable="true"]')).toHaveLength(0)

    await wrapper.get('[data-action="clear-filter"]').trigger('click')

    expect(wrapper.findAll('tr[draggable="true"]').length).toBeGreaterThan(0)
    expect(wrapper.find('.root-dropzone').exists()).toBe(true)
  })

  it('접어 둔 조상 아래의 일치 행이 필터 중에는 보이고, 필터를 풀면 다시 접힌 상태로 돌아온다', async () => {
    const design = node({ id: 1, code: '1', name: '설계', children: [] })
    const screen = node({ id: 2, parentId: 1, code: '1.1', name: '화면 설계' })
    wrapper = render([{ ...design, children: [screen] }])

    // 부모를 접는다 — 자식 행이 사라진다.
    await wrapper.get('tr[data-row-id="1"] .toggle').trigger('click')
    expect(wrapper.find('tr[data-row-id="2"]').exists()).toBe(false)

    // 필터를 걸면(2-3) 접힘을 무시하고 자식이 다시 보인다.
    await openFilterRow(wrapper)
    await wrapper
      .get<HTMLInputElement>('td[data-filter="name"] input[type="search"]')
      .setValue('화면')
    expect(wrapper.find('tr[data-row-id="2"]').exists()).toBe(true)

    // 필터를 풀면 접어 둔 상태가 그대로 돌아온다 — collapsed를 비우지 않았다는 결정을 고정한다.
    await wrapper.get('[data-action="clear-filter"]').trigger('click')
    expect(wrapper.find('tr[data-row-id="2"]').exists()).toBe(false)
  })

  it('실행 방식 필터를 걸어도 thead th 수와 체크포인트 서랍 colspan이 어긋나지 않는다', async () => {
    wrapper = render([node({ executionMode: 'WATERFALL' })])

    await openFilterRow(wrapper)
    await wrapper.get('td[data-filter="mode"] button[data-value="WATERFALL"]').trigger('click')

    await wrapper.get('.cp-badge').trigger('click')

    const columns = wrapper.findAll('thead th').length
    const drawer = wrapper.get('tr.checkpoint-row td')
    expect(drawer.attributes('colspan')).toBe(String(columns))
  })

  it('조건이 걸린 열을 숨기면 조건이 유지된 채 배너가 그 열 이름을 알린다', async () => {
    const waterfall = node({ id: 1, code: '1', name: 'Waterfall 업무', executionMode: 'WATERFALL' })
    const agile = node({ id: 2, code: '2', name: 'Agile 업무', executionMode: 'AGILE' })
    wrapper = render([waterfall, agile])

    await openFilterRow(wrapper)
    await wrapper.get('td[data-filter="mode"] button[data-value="WATERFALL"]').trigger('click')

    // 필터가 걸린 상태를 확인해 둔다 — 숨기기 전에 실제로 걸려 있어야 아래 검증이 의미가 있다.
    expect(wrapper.findAll('tr[data-row-id]').map((row) => row.attributes('data-row-id'))).toEqual([
      '1',
    ])

    setColumnPrefs({ hidden: ['mode'], pin: 'none' })
    await wrapper.vm.$nextTick()

    // 숨긴 열의 필터 칸은 그려지지 않는다.
    expect(wrapper.find('td[data-filter="mode"]').exists()).toBe(false)
    // 조건은 유지된다 — 행 수는 그대로 1건이다.
    expect(wrapper.findAll('tr[data-row-id]').map((row) => row.attributes('data-row-id'))).toEqual([
      '1',
    ])
    // 배너가 실행 방식 조건이 (숨겨진 채로) 걸려 있음을 알린다.
    expect(wrapper.get('.filter-banner').text()).toContain('실행 방식')
  })
})
