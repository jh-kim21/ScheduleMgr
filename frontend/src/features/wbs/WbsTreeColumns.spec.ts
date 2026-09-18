// @vitest-environment happy-dom
import { mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import type { TagRef, WbsNode } from '../../api/wbsApi'
import { resetColumnPrefs, setColumnPrefs } from './wbsColumnPrefs'
import WbsTree from './WbsTree.vue'

/**
 * Phase C·D로 이 표에 두 열이 붙었다(9 → 11). 순수 함수로 뺄 수 없는 것만 여기서 고정한다 —
 * 판정 자체는 `responsibleCell.spec.ts`·`tagCell.spec.ts`가 덮고, 이 파일은 **렌더링**을 본다.
 *
 * 가장 중요한 것은 `colspan`이다(지시서 부록 함정 4). 체크포인트 서랍은 `rows`에 없는 별도 `<tr>`
 * 이라 열을 통으로 먹는데, 열을 늘리면서 그 숫자를 안 고치면 표가 어긋난다. 눈으로 보지 않으면
 * 모르는 종류의 결함이라 여기서 못 박는다 — **숫자를 적어 비교하지 않고 `<thead>`의 열 수와
 * 맞춰 본다**(다음에 열이 또 늘어도 이 테스트가 그대로 잡는다).
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

const SERVICE: TagRef = { id: 1, name: 'Service', color: null }
const WEB: TagRef = { id: 2, name: 'Web', color: 'blue' }

function render(tree: WbsNode[], props: Record<string, unknown> = {}) {
  return mount(WbsTree, {
    props: { tree, projectId: 7, ...props },
    global: { plugins: [router] },
  })
}

describe('WbsTree — 담당자·분야 열', () => {
  let wrapper: VueWrapper | undefined

  afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
  })

  it('체크포인트를 펼쳐도 서랍의 colspan이 열 수와 맞는다 — 어긋나면 표가 깨진다', async () => {
    wrapper = render([node({ executionMode: 'WATERFALL' })])

    await wrapper.get('.cp-badge').trigger('click')

    const columns = wrapper.findAll('thead th').length
    const drawer = wrapper.get('tr.checkpoint-row td')
    expect(drawer.attributes('colspan')).toBe(String(columns))
  })

  it('담당자가 없으면 "-"를 적는다 — Summary·Work Package 모두', () => {
    wrapper = render([
      node({ id: 1, nodeType: 'SUMMARY', children: [node({ id: 2, parentId: 1, code: '1.1' })] }),
    ])

    const cells = wrapper.findAll('td.owner')
    expect(cells).toHaveLength(2)
    for (const cell of cells) {
      expect(cell.text()).toBe('-')
    }
  })

  it('자기 담당자와 물려받은 담당자를 흐리게 구분한다 — 상속된 이름은 이 행에서 지울 수 없다', () => {
    wrapper = render([
      node({
        responsible: [{ memberId: 2, name: '이승하' }],
        responsibleInherited: [{ memberId: 1, name: '김재학' }],
      }),
    ])

    const names = wrapper.findAll('td.owner .who')
    expect(names.map((n) => n.text())).toEqual(['이승하', '김재학'])
    expect(names[0].classes()).not.toContain('inherited')
    expect(names[1].classes()).toContain('inherited')
    expect(names[1].attributes('title')).toBe('상위 단계에서 물려받음')
  })

  it('담당자 셀은 RACI로 가는 링크다 — WBS에서 바꾸지 않는다는 것을 동선으로 알린다', () => {
    wrapper = render([node({ responsible: [{ memberId: 2, name: '이승하' }] })])

    expect(wrapper.get('td.owner a').attributes('href')).toBe('/raci')
  })

  it('여러 명이면 쉼표로 잇고 전체를 title에 담는다 — 폭이 좁아 잘려도 읽을 수 있어야 한다', () => {
    wrapper = render([
      node({
        responsible: [
          { memberId: 1, name: '김재학' },
          { memberId: 2, name: '이승하' },
        ],
      }),
    ])

    const link = wrapper.get('td.owner a')
    expect(link.text()).toBe('김재학, 이승하')
    expect(link.classes()).toContain('cell-clip')
    expect(link.attributes('title')).toContain('김재학, 이승하')
  })

  it('Work Package 행은 자기 분야를 칩으로 그린다', () => {
    wrapper = render([node({ tags: [SERVICE, WEB] })])

    const chips = wrapper.findAll('td.tags .tag-chip')
    expect(chips.map((chip) => chip.text())).toEqual(['Service', 'Web'])
    // 지정한 색이 있으면 그것을, 없으면 이름 해시를. 어느 쪽이든 팔레트 슬롯 이름이 실린다.
    expect(chips[1].attributes('data-color')).toBe('blue')
    expect(chips[0].attributes('data-color')).toBeTruthy()
  })

  it('Summary 행은 하위 분야를 요약해 흐리게 그린다 — 자기 분야가 아니다', () => {
    wrapper = render([
      node({
        id: 1,
        nodeType: 'SUMMARY',
        tagSummary: [SERVICE],
        children: [node({ id: 2, parentId: 1, code: '1.1', tags: [SERVICE] })],
      }),
    ])

    const summaryChip = wrapper.findAll('td.tags')[0].get('.tag-chip')
    expect(summaryChip.text()).toBe('Service')
    expect(summaryChip.classes()).toContain('muted')
  })

  it('분야가 없으면 "-"를 적는다', () => {
    wrapper = render([node()])

    expect(wrapper.get('td.tags').text()).toBe('-')
  })

  it('칩에 인라인 색을 칠하지 않는다 — 하드코딩 색이 하나라도 있으면 다크 모드에서 그 자리만 라이트로 남는다', () => {
    wrapper = render([node({ tags: [SERVICE, WEB] })])

    for (const chip of wrapper.findAll('td.tags .tag-chip')) {
      expect(chip.attributes('style')).toBeUndefined()
    }
  })
})

/**
 * `wbs-tree-columns` 지시서 3-5 — 열 표시/숨김·고정이 실제 표에 반영되는지 본다. 판정 자체
 * (`visibleColumns`·`pinOffsets`)는 `wbsColumns.spec.ts`가 순수 함수로 덮으므로, 여기서는 그
 * 판정을 받은 `WbsTree`가 옳은 DOM을 그리는지만 본다.
 *
 * `columnPrefs`는 모듈 스코프라 테스트끼리 상태가 샌다 — 매 테스트 앞에 `resetColumnPrefs()`를
 * 부른다.
 */
describe('WbsTree — 열 표시/숨김·고정', () => {
  let wrapper: VueWrapper | undefined

  beforeEach(() => {
    resetColumnPrefs()
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
    resetColumnPrefs()
  })

  it('열을 숨기면 thead th 수와 체크포인트 서랍의 colspan이 함께 줄어든다', async () => {
    setColumnPrefs({ hidden: ['owner', 'tags'], pin: 'none' })
    wrapper = render([node({ executionMode: 'WATERFALL' })])

    await wrapper.get('.cp-badge').trigger('click')

    const columns = wrapper.findAll('thead th').length
    expect(columns).toBe(8)
    const drawer = wrapper.get('tr.checkpoint-row td')
    expect(drawer.attributes('colspan')).toBe(String(columns))
  })

  it('숨긴 열의 td가 사라진다 — 담당자를 숨기면 td.owner도 RACI 링크도 없다', () => {
    setColumnPrefs({ hidden: ['owner'], pin: 'none' })
    wrapper = render([node({ responsible: [{ memberId: 2, name: '이승하' }] })])

    expect(wrapper.find('td.owner').exists()).toBe(false)
    expect(wrapper.find('td.owner a').exists()).toBe(false)
    expect(wrapper.get('thead').text()).not.toContain('담당자')
  })

  it('업무명은 hidden에 넣어도 남는다', () => {
    setColumnPrefs({ hidden: ['name'], pin: 'none' })
    wrapper = render([node({ name: '요구사항 정의' })])

    const cell = wrapper.get('td.col-name')
    expect(cell.text()).toContain('요구사항 정의')
  })

  it('pin: "code" → th.code/td.code에 sticky가 붙고, 고정하지 않은 열(.mode)에는 없다', () => {
    setColumnPrefs({ hidden: [], pin: 'code' })
    wrapper = render([node()])

    expect(wrapper.get<HTMLElement>('th.code').element.style.position).toBe('sticky')
    expect(wrapper.get<HTMLElement>('td.code').element.style.position).toBe('sticky')
    expect(wrapper.get<HTMLElement>('th.mode').element.style.position).not.toBe('sticky')
    expect(wrapper.get<HTMLElement>('td.mode').element.style.position).not.toBe('sticky')
  })

  it('pin: "name" → .code와 .col-name 둘 다 sticky이고 left가 0/5.5rem 계열이다', () => {
    setColumnPrefs({ hidden: [], pin: 'name' })
    wrapper = render([node()])

    const code = wrapper.get<HTMLElement>('td.code').element
    const name = wrapper.get<HTMLElement>('td.col-name').element
    expect(code.style.position).toBe('sticky')
    expect(name.style.position).toBe('sticky')
    expect(parseFloat(code.style.left)).toBe(0)
    expect(parseFloat(name.style.left)).toBeCloseTo(5.5, 1)
  })

  it('WBS 코드를 숨긴 채 pin: "name" → .col-name의 left가 0이다 (숨긴 열이 오프셋을 밀면 안 된다)', () => {
    setColumnPrefs({ hidden: ['code'], pin: 'name' })
    wrapper = render([node()])

    expect(wrapper.find('td.code').exists()).toBe(false)
    const name = wrapper.get<HTMLElement>('td.col-name').element
    expect(name.style.position).toBe('sticky')
    expect(parseFloat(name.style.left)).toBe(0)
  })

  it('툴바에 [열 설정] 버튼이 있고, readOnly에서도 disabled가 아니다', () => {
    wrapper = render([node()], { readOnly: true })

    const button = wrapper.get('[data-action="columns"]')
    expect(button.text()).toBe('열 설정')
    expect(button.attributes('disabled')).toBeUndefined()
  })
})
