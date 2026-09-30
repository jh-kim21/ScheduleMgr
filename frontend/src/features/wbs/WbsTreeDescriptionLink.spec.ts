// @vitest-environment happy-dom
import { mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, describe, expect, it } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import type { WbsNode } from '../../api/wbsApi'
import WbsTree from './WbsTree.vue'

/**
 * 설명 칩 · Action Item 링크 · 입력 칸 키 가드의 **상호작용**을 고정한다. 판정 자체는 순수
 * 함수 쪽(`treeKeyTarget.spec.ts`, `shared/url.spec.ts`)이 덮으므로 여기서는 그 판정이 실제로
 * 트리에 연결돼 있는지만 본다 — `WbsTreeReorder.spec.ts`와 같은 분리다.
 *
 * 특히 첫 케이스가 순수 함수로는 덮을 수 없는 자리다: 가드가 `event.altKey` 분기보다 **위에**
 * 있어야 타이핑 중에 누른 Alt+방향키가 항목을 옮기지 않는다. 아래에 두면 `isTextEntry` 자체는
 * 여전히 맞는 답을 내는데 화면만 조용히 깨진다.
 *
 * 타깃 크기(24×24px)와 겹침은 여기서 단정하지 않는다 — `happy-dom`에 레이아웃 엔진이 없고
 * `<style scoped>`도 주입되지 않는다(CLAUDE.md "컴포넌트 테스트"). 지시서 §5의 수동 확인
 * 12·13·17번이 그 자리다.
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

/**
 * `ModalDialog`는 `body`로 teleport 하므로 `wrapper.find(...)`로는 내용을 찾을 수 없고, 언마운트
 * 전까지 `document.body`에 그대로 남는다 — 다음 테스트가 이전 테스트의 대화상자까지 함께
 * 조회하지 않도록 반드시 언마운트한다(CLAUDE.md "컴포넌트 테스트").
 */
let wrapper: VueWrapper | null = null

afterEach(() => {
  wrapper?.unmount()
  wrapper = null
})

function render(tree: WbsNode[], props: Record<string, unknown> = {}) {
  return mount(WbsTree, {
    props: { tree, projectId: 7, ...props },
    global: { plugins: [router] },
  })
}

describe('입력 칸에서 누른 방향키', () => {
  it('체크포인트 입력칸의 Alt+방향키는 WBS 항목을 옮기지 않는다', async () => {
    const first = node({ id: 1, code: '1', executionMode: 'WATERFALL' })
    const second = node({ id: 2, code: '2', executionMode: 'WATERFALL' })
    wrapper = render([first, second], {
      workPackages: {
        1: { checkpoints: [], checkpointApproved: 0, checkpointTotal: 0 },
        2: { checkpoints: [], checkpointApproved: 0, checkpointTotal: 0 },
      },
    })

    await wrapper.get('tr[data-row-id="2"]').trigger('click')
    // 체크포인트 행을 펼치고 추가 폼까지 연다 — 그래야 같은 `<tbody>` 안에 <input>이 들어온다.
    await wrapper.get('tr[data-row-id="2"] .cp-badge').trigger('click')
    const add = wrapper
      .findAll('tr.checkpoint-row button')
      .find((button) => button.text().includes('체크포인트 추가'))
    await add!.trigger('click')

    await wrapper.get('tr.checkpoint-row .cp-form input').trigger('keydown', {
      key: 'ArrowUp',
      altKey: true,
    })
    expect(wrapper.emitted('move')).toBeUndefined()

    // 대조군 — 입력 칸 밖(트리 자신)에서는 같은 키가 예전 그대로 항목을 옮긴다.
    await wrapper.get('tbody').trigger('keydown', { key: 'ArrowUp', altKey: true })
    expect(wrapper.emitted('move')?.[0]).toEqual([2, { parentId: null, position: 0 }])
  })
})

describe('설명 칩', () => {
  it('설명이 없는 행에는 칩이 없다', () => {
    wrapper = render([node({ id: 1, code: '1' })])
    expect(wrapper.find('.desc-chip').exists()).toBe(false)
  })

  it('눌러도 행 선택이 바뀌지 않고, 창이 줄바꿈을 살려 전문을 보여준다', async () => {
    wrapper = render([
      node({ id: 1, code: '1' }),
      node({ id: 2, code: '2', name: '화면 설계', description: '첫 줄\n둘째 줄' }),
    ])

    await wrapper.get('tr[data-row-id="1"]').trigger('click')
    await wrapper.get('tr[data-row-id="2"] .desc-chip').trigger('click')

    // `@click.stop`이 없으면 설명을 들여다보는 것만으로 선택이 2번 행으로 옮겨간다.
    expect(wrapper.get('.selected-label').text()).toBe('선택: 1 요구사항 정의')
    expect(document.body.querySelector('.desc-full')?.textContent).toBe('첫 줄\n둘째 줄')
  })

  it('커밋 조회(readOnly) 중에도 칩과 링크가 살아 있다 — 보는 것은 쓰기가 아니다', () => {
    wrapper = render(
      [node({ id: 1, code: '1', description: '메모', actionItemUrl: 'https://example.com' })],
      { readOnly: true },
    )
    expect(wrapper.find('.desc-chip').exists()).toBe(true)
    expect(wrapper.find('a.action-link').exists()).toBe(true)
  })
})

describe('Action Item 링크', () => {
  it('http(s) 만 링크로 그린다', () => {
    wrapper = render([node({ id: 1, code: '1', actionItemUrl: 'https://example.com/ticket/1' })])

    const link = wrapper.get('tr[data-row-id="1"] a.action-link')
    expect(link.attributes('href')).toBe('https://example.com/ticket/1')
    // 전체 URL은 좁은 행을 밀어내지 않도록 title 에만 둔다.
    expect(link.attributes('title')).toBe('https://example.com/ticket/1')
    expect(link.attributes('rel')).toBe('noopener noreferrer')
    expect(link.attributes('target')).toBe('_blank')
  })

  /*
   * 두 절반을 함께 고정한다. 보안 절반(`<a>`가 없다)만 남기고 발견 절반을 지우면 "아예 그리지
   * 않는다"로 되돌아가는데, 그러면 나쁜 값을 든 행이 주소가 없는 행과 완전히 똑같이 보인다 —
   * 그런 값을 들고 있을 수 있는 행은 정확히 아무도 검증하지 않은 행이라, 보이지 않으면 고칠
   * 사람도 없다(팀 리드 결정, 지시서 §2-D가 §3 마크업보다 우선).
   */
  it('안전하지 않은 주소는 링크가 아니라 평문으로 보인다', () => {
    wrapper = render([node({ id: 1, code: '1', actionItemUrl: 'javascript:alert(1)' })])

    // 보안 절반 — `<a>`가 없다. href 에 들어가지 않으므로 눌러서 실행될 길이 없다.
    expect(wrapper.find('tr[data-row-id="1"] a.action-link').exists()).toBe(false)

    // 발견 절반 — 값 자체는 보인다. Vue 가 이스케이프하므로 글자로 그리는 것은 안전하다.
    const plain = wrapper.get('tr[data-row-id="1"] .action-link.unsafe')
    expect(plain.element.tagName).toBe('SPAN')
    expect(plain.text()).toBe('javascript:alert(1)')
  })

  it('주소가 없으면 링크도 평문도 그리지 않는다', () => {
    wrapper = render([node({ id: 1, code: '1', actionItemUrl: null })])
    expect(wrapper.find('tr[data-row-id="1"] .action-link').exists()).toBe(false)
  })

  it('눌러도 행 선택이 바뀌지 않는다', async () => {
    wrapper = render([
      node({ id: 1, code: '1' }),
      node({ id: 2, code: '2', actionItemUrl: 'https://example.com' }),
    ])

    await wrapper.get('tr[data-row-id="1"]').trigger('click')
    await wrapper.get('tr[data-row-id="2"] a.action-link').trigger('click')
    expect(wrapper.get('.selected-label').text()).toBe('선택: 1 요구사항 정의')
  })
})
