// @vitest-environment happy-dom
import { mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, describe, expect, it } from 'vitest'
import type { WbsItemInput, WbsNode } from '../../api/wbsApi'
import type { WbsTag } from '../../api/wbsTagApi'
import WbsForm from './WbsForm.vue'

/**
 * 최상위 항목에서만 가중치를 묻지 않는다(사용자 결정 — 지시서의 "레벨 무관 삭제"보다 좁은 범위).
 * 이 계약은 두 갈래라 순수 함수로 뺄 수 없다:
 *
 *   1. **렌더링** — 최상위에서만 입력칸이 사라지고 하위 레벨에는 남는다.
 *   2. **제출 payload** — 입력칸을 감췄어도 저장하면 기존 `weight`가 그대로 실려 나간다.
 *
 * 2번이 이 파일의 진짜 이유다. 입력을 없앤 김에 payload에서도 빼면 기존에 가중치가 있던 최상위
 * 항목이 저장하는 순간 `null`로 덮인다 — 커밋 `da96ebe`(실적/예상 종료일이 저장마다 사라지던
 * 결함)와 같은 종류의 사고이고, `nodeToFormInput`만 보는 `wbsFormMapping.spec.ts`로는 드러나지
 * 않는다(그 함수는 값을 폼에 *넣는* 쪽이고, 여기서 확인하려는 것은 감춰진 값이 *나가는지*다).
 *
 * `WbsForm`은 `ModalDialog`로 자기를 감싸고 `ModalDialog`는 `<Teleport to="body">`이므로
 * `wrapper.find(...)`로는 내용을 찾을 수 없다 — `document.body`를 직접 조회한다
 * (CLAUDE.md "컴포넌트 테스트").
 */
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
    delayDays: 0,
    expectedProgress: null,
    responsible: [],
    responsibleInherited: [],
    tags: [],
    tagSummary: null,
    children: [],
    ...overrides,
  } as WbsNode
}

function renderForm(props: {
  editing: WbsNode | null
  parent: WbsNode | null
  siblings?: WbsNode[]
  availableTags?: WbsTag[]
}) {
  return mount(WbsForm, {
    props: { siblings: [], availableTags: [], error: null, ...props },
  })
}

/** 라벨 글자로 찾는다 — 클래스가 없는 칸이라 위치(`nth-child`)로 잡으면 열이 바뀔 때 조용히 빗나간다. */
function weightLabel(): HTMLLabelElement | null {
  const labels = [...document.body.querySelectorAll<HTMLLabelElement>('.wbs-form label')]
  return labels.find((label) => label.textContent?.trim().startsWith('가중치')) ?? null
}

function submit() {
  document.body.querySelector<HTMLFormElement>('form.wbs-form')?.dispatchEvent(
    new Event('submit', { bubbles: true, cancelable: true }),
  )
}

describe('WbsForm — 가중치 입력', () => {
  let wrapper: VueWrapper | undefined

  afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
  })

  it('최상위 항목 추가에는 가중치 입력이 없다', () => {
    wrapper = renderForm({ editing: null, parent: null })

    expect(weightLabel()).toBeNull()
  })

  it('하위 항목 추가에는 가중치 입력이 그대로 있다 — 이번 결정은 최상위만 없앤 것이다', () => {
    wrapper = renderForm({ editing: null, parent: node({ id: 9, nodeType: 'SUMMARY' }) })

    expect(weightLabel()).not.toBeNull()
  })

  it('최상위 항목 수정에도 가중치 입력이 없다 — parentId가 null인지로 판단한다', () => {
    wrapper = renderForm({ editing: node({ parentId: null }), parent: null })

    expect(weightLabel()).toBeNull()
  })

  it('하위 항목 수정에는 가중치 입력이 있다', () => {
    wrapper = renderForm({ editing: node({ id: 2, parentId: 1, code: '1.1' }), parent: null })

    expect(weightLabel()).not.toBeNull()
  })

  it('입력칸을 감춘 최상위 항목을 저장해도 기존 가중치가 그대로 실려 나간다 — payload에서 빼면 저장하는 순간 null로 덮인다', async () => {
    wrapper = renderForm({ editing: node({ parentId: null, weight: 7 }), parent: null })

    expect(weightLabel()).toBeNull()
    submit()
    await wrapper.vm.$nextTick()

    const emitted = wrapper.emitted('submit')
    expect(emitted).toHaveLength(1)
    expect((emitted![0][0] as { weight: number | null }).weight).toBe(7)
  })

  it('가중치 안내문도 최상위에서는 사라진다 — 입력칸 없이 설명만 남으면 무엇에 대한 말인지 알 수 없다', () => {
    wrapper = renderForm({ editing: null, parent: null })

    expect(document.body.textContent).not.toContain('가중치를 비워 두면')
  })

  it('하위 레벨의 안내문은 새 폴백을 말한다 — "하위 평균으로 집계"는 형제에 값이 있으면 거짓이었다', () => {
    wrapper = renderForm({ editing: null, parent: node({ id: 9, nodeType: 'SUMMARY' }) })

    const text = document.body.textContent ?? ''
    expect(text).toContain('가중치를 비워 두면 1로 계산됩니다')
    expect(text).not.toContain('하위 평균으로 집계')
  })

  it('최상위 항목에 저장된 가중치가 있으면 보관 중임을 알린다 — 감춘 값이 집계에는 계속 쓰인다', () => {
    wrapper = renderForm({ editing: node({ parentId: null, weight: 7 }), parent: null })

    expect(document.body.textContent).toContain('저장된 가중치(7)')
  })
})

/**
 * 계약 §1-D-3: **`tagIds`는 `null`=변경 없음, `[]`=전부 해제.** 이 구분이 있어야 태그를 모르는
 * 호출자가 항목을 저장해도 태그가 조용히 지워지지 않는다. 그리고 그 구분을 유지하려면 **폼은 언제나
 * 배열을 명시적으로 보내야** 한다 — 폼이 보여 준 집합이 곧 저장될 집합이고, "변경 없음"으로 보낼
 * 이유가 없다. `null`이 새어 나가면 화면에서 태그를 전부 떼도 저장이 아무 일도 하지 않는다.
 */
const SERVICE: WbsTag = { id: 1, name: 'Service', color: null, sortOrder: 0 }
const WEB: WbsTag = { id: 2, name: 'Web', color: 'blue', sortOrder: 1 }

function submitted(wrapper: VueWrapper): WbsItemInput {
  const emitted = wrapper.emitted('submit')
  expect(emitted).toHaveLength(1)
  return emitted![0][0] as WbsItemInput
}

/** 새 항목은 업무명이 비어 있으면 제출 자체가 막힌다(`onSubmit`의 첫 줄). 먼저 채워 준다. */
function typeName(value: string) {
  const input = document.body.querySelector<HTMLInputElement>('form.wbs-form input[type="text"]')
  expect(input, '업무명 입력을 찾지 못했습니다').not.toBeNull()
  input!.value = value
  input!.dispatchEvent(new Event('input', { bubbles: true }))
}

function tagToggle(name: string): HTMLButtonElement | null {
  const buttons = [...document.body.querySelectorAll<HTMLButtonElement>('.tag-toggle')]
  return buttons.find((button) => button.textContent?.trim() === name) ?? null
}

describe('WbsForm — 업무 분야', () => {
  let wrapper: VueWrapper | undefined

  afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
  })

  it('새 항목은 빈 배열을 보낸다 — null(= 변경 없음)이 아니다', async () => {
    wrapper = renderForm({ editing: null, parent: null, availableTags: [SERVICE] })

    typeName('새 업무')
    await wrapper.vm.$nextTick()
    submit()
    await wrapper.vm.$nextTick()

    expect(submitted(wrapper).tagIds).toEqual([])
  })

  it('붙어 있던 분야를 전부 떼면 빈 배열이 나간다 — null 이면 아무 일도 안 일어난다', async () => {
    wrapper = renderForm({
      editing: node({ id: 3, parentId: 1, tags: [{ id: 1, name: 'Service', color: null }] }),
      parent: null,
      availableTags: [SERVICE, WEB],
    })

    tagToggle('Service')?.click()
    submit()
    await wrapper.vm.$nextTick()

    expect(submitted(wrapper).tagIds).toEqual([])
  })

  it('고른 분야가 그대로 실려 나간다', async () => {
    wrapper = renderForm({ editing: null, parent: null, availableTags: [SERVICE, WEB] })

    typeName('새 업무')
    await wrapper.vm.$nextTick()
    tagToggle('Web')?.click()
    submit()
    await wrapper.vm.$nextTick()

    expect(submitted(wrapper).tagIds).toEqual([2])
  })

  it('Summary는 고를 수 없되 보관값을 그대로 되돌려 보낸다 — 비워 보내면 무변경 저장이 거부된다', async () => {
    wrapper = renderForm({
      editing: node({
        id: 3,
        parentId: 1,
        nodeType: 'SUMMARY',
        tags: [{ id: 2, name: 'Web', color: 'blue' }],
        children: [node({ id: 4, parentId: 3 })],
      }),
      parent: null,
      availableTags: [SERVICE, WEB],
    })

    expect(tagToggle('Web')?.disabled).toBe(true)
    submit()
    await wrapper.vm.$nextTick()

    expect(submitted(wrapper).tagIds).toEqual([2])
  })

  it('등록된 분야가 없으면 어디서 만드는지 알린다 — 여기서는 만들 수 없다', () => {
    wrapper = renderForm({ editing: null, parent: null, availableTags: [] })

    expect(document.body.textContent).toContain('등록된 분야가 없습니다')
  })
})
