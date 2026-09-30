// @vitest-environment happy-dom
import { mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, describe, expect, it } from 'vitest'
import type { WbsItemInput, WbsNode } from '../../api/wbsApi'
import type { WbsTag } from '../../api/wbsTagApi'
import { WEIGHT_GRADES } from '../../shared/weight'
import WbsForm from './WbsForm.vue'

/**
 * 규모(형제 가중치) 입력은 한 번 삭제됐다가 `docs/tasks/weight-grade-scale.md`로 되살아났다 —
 * 이제 1·2·3·5·8 다섯 등급 `<select>`이고, 등급마다 형제 사이의 판단 기준 문장이 붙는다. 입력칸이
 * 돌아왔어도 이 파일이 고정하는 계약은 그대로 둘이다.
 *
 * 1. **미지정을 실체화하지 않는다.** `null`은 `null`인 채로 열리고 그대로 나가야 한다 — WBS 형제의
 *    미입력 몫은 상수가 아니라 `unitPerLeaf × leafCount`(형제 크기 비례)라, 폼이 `3`을 박아 넣으면
 *    제목만 고치려던 저장이 그 프로젝트의 진척 숫자를 바꾼다(지시서 §2-c). 그래서 체크포인트·
 *    Backlog가 쓰는 `weightForForm`·`weightLabel`을 여기서는 쓰지 않는다 — 이름이 비슷하다고
 *    돌려쓰면 안 된다.
 * 2. **폼이 고를 수 없는 값도 payload에 그대로 실린다.** 척도 밖 값(`7`)과 `0`은 드롭다운에서 고를
 *    수 없지만 선택 상태로 남아 그대로 저장돼야 한다 — `weight`를 payload에서 빼거나 임의의 등급으로
 *    덮으면 커밋 `da96ebe`(실적/예상 종료일이 저장마다 사라지던 결함)와 같은 종류의 사고다.
 *    `nodeToFormInput`만 보는 `wbsFormMapping.spec.ts`로는 드러나지 않는다(그 함수는 값을 폼에
 *    *넣는* 쪽이고, 여기서 확인하려는 것은 값이 *나가는지*다).
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
  availableTags?: WbsTag[]
}) {
  return mount(WbsForm, {
    props: { availableTags: [], error: null, ...props },
  })
}

/**
 * 라벨 글자로 찾는다 — 클래스가 없는 칸이라 위치(`nth-child`)로 잡으면 열이 바뀔 때 조용히 빗나간다.
 * 이름은 `가중치`가 아니라 **`규모`** 다: 폼·진척 탭·CSV가 같은 말을 써야 한다(지시서 §3-E).
 */
function scaleLabel(): HTMLLabelElement | null {
  const labels = [...document.body.querySelectorAll<HTMLLabelElement>('.wbs-form label')]
  return labels.find((label) => label.textContent?.trim().startsWith('규모')) ?? null
}

function scaleSelect(): HTMLSelectElement | null {
  return scaleLabel()?.querySelector('select') ?? null
}

function scaleOptions(): HTMLOptionElement[] {
  return [...(scaleSelect()?.querySelectorAll('option') ?? [])]
}

function selectedOption(): HTMLOptionElement | null {
  return scaleOptions().find((option) => option.selected) ?? null
}

/**
 * 옵션을 글자로 고른다. `select.value = …` 로 고르지 않는 이유는 Vue가 `:value="null"` 인 옵션의
 * `value` **속성을 지우고** `_value` 프로퍼티에만 `null`을 담기 때문이다 — happy-dom의 `value`
 * getter는 속성이 없으면 textContent로 떨어지므로 빈 문자열로는 `미지정`을 잡을 수 없다.
 * `selectedIndex`는 그 표현과 무관하고, Vue의 change 핸들러도 `selected` 여부만 본다.
 */
function choose(optionPrefix: string) {
  const select = scaleSelect()
  expect(select, '규모 셀렉트를 찾지 못했습니다').not.toBeNull()
  const index = scaleOptions().findIndex((option) =>
    option.textContent?.trim().startsWith(optionPrefix),
  )
  expect(index, `옵션을 찾지 못했습니다: ${optionPrefix}`).toBeGreaterThanOrEqual(0)
  select!.selectedIndex = index
  select!.dispatchEvent(new Event('change', { bubbles: true }))
}

function submit() {
  document.body.querySelector<HTMLFormElement>('form.wbs-form')?.dispatchEvent(
    new Event('submit', { bubbles: true, cancelable: true }),
  )
}

describe('WbsForm — 규모(형제 가중치) 입력', () => {
  let wrapper: VueWrapper | undefined

  afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
  })

  it('최상위 항목 추가에 규모 셀렉트가 있고, 미지정이 선택돼 있다', () => {
    wrapper = renderForm({ editing: null, parent: null })

    expect(scaleLabel()).not.toBeNull()
    expect(selectedOption()?.textContent?.trim()).toBe('미지정')
  })

  it('하위 항목 추가에도 있다 — 레벨과 무관하다', () => {
    wrapper = renderForm({ editing: null, parent: node({ id: 9, nodeType: 'SUMMARY' }) })

    expect(scaleLabel()).not.toBeNull()
    expect(selectedOption()?.textContent?.trim()).toBe('미지정')
  })

  /**
   * 기존 항목을 열었을 때 `보통`이 골라져 있으면 §2-c의 사고다 — 제목만 고치려는 저장이 미지정을
   * `3`으로 박제하고, `3 ≠ unitPerLeaf × leafCount` 이므로 형제 전원의 몫이 재계산된다.
   */
  it('최상위 항목 수정에도 있고, 값이 없으면 미지정이다 — 보통으로 미리 골라 두지 않는다', () => {
    wrapper = renderForm({ editing: node({ parentId: null, weight: null }), parent: null })

    expect(scaleLabel()).not.toBeNull()
    expect(selectedOption()?.textContent?.trim()).toBe('미지정')
  })

  it('하위 항목 수정에도 있다', () => {
    wrapper = renderForm({ editing: node({ id: 2, parentId: 1, code: '1.1' }), parent: null })

    expect(scaleLabel()).not.toBeNull()
  })

  /** 형제로 나란한 Summary끼리 크기가 다른 것은 정상이라 막지 않는다(지시서 §2-g). */
  it('Summary 항목에서도 규모를 고를 수 있다 — 실행 방식·분야와 달리 잠그지 않는다', () => {
    wrapper = renderForm({
      editing: node({
        id: 3,
        parentId: 1,
        nodeType: 'SUMMARY',
        summary: true,
        children: [node({ id: 4, parentId: 3 })],
      }),
      parent: null,
    })

    expect(scaleLabel()).not.toBeNull()
    expect(scaleSelect()?.disabled).toBe(false)
  })

  /**
   * 같은 등급이라도 견주는 대상이 달라 기준 문장이 둘이다(`hint` = 한 Work Package 안,
   * `wbsHint` = 형제 사이). WBS 폼이 체크포인트·Backlog용 `hint`를 실어 나르면 "이 업무의 절반
   * 이상"처럼 형제 비교와 무관한 말이 뜬다. 문자열을 손으로 베끼지 않고 척도와 맞대어 본다.
   */
  it('등급 옵션 다섯 개가 큰 것부터 있고, 형제 사이 기준 문장(wbsHint)을 단다', () => {
    wrapper = renderForm({ editing: null, parent: null })

    const grades = scaleOptions()
      .map((option) => option.textContent?.trim())
      .filter((text) => text !== '미지정')
    expect(grades).toEqual(
      WEIGHT_GRADES.map((grade) => `${grade.label} (${grade.value}) — ${grade.wbsHint}`),
    )
  })

  /**
   * `<option disabled>`는 *고를 수 없다*는 뜻이지 *선택 상태로 둘 수 없다*는 뜻이 아니다
   * (지시서 §7-3). 시각적 선택 여부는 이 구성으로 볼 수 없으므로(`happy-dom`에 레이아웃 엔진이
   * 없다) **셀렉트가 실제로 들고 있는 값**(`select.value`)으로 단정한다.
   */
  it('척도 밖 값(7)은 비활성 옵션으로 선택돼 있고, 저장하면 7 그대로 실려 나간다 — 고를 수 없다는 것이 선택 상태로 둘 수 없다는 뜻은 아니다', async () => {
    wrapper = renderForm({ editing: node({ parentId: null, weight: 7 }), parent: null })

    expect(scaleSelect()?.value).toBe('7')
    const selected = selectedOption()
    expect(selected?.textContent?.trim()).toBe('사용자 지정 7')
    expect(selected?.disabled).toBe(true)

    submit()
    await wrapper.vm.$nextTick()

    expect(submitted(wrapper).weight).toBe(7)
  })

  it('0은 집계 제외(0)로 선택돼 있고, 제목만 고쳐 저장해도 0 그대로다 — 0은 "작다"가 아니라 별도의 뜻이다', async () => {
    wrapper = renderForm({ editing: node({ parentId: null, weight: 0 }), parent: null })

    expect(scaleSelect()?.value).toBe('0')
    const selected = selectedOption()
    expect(selected?.textContent?.trim()).toBe('집계 제외(0)')
    expect(selected?.disabled).toBe(true)

    submit()
    await wrapper.vm.$nextTick()

    expect(submitted(wrapper).weight).toBe(0)
  })

  it('척도 위의 값(3)은 정상 옵션으로 선택돼 있고, 저장하면 3 그대로다', async () => {
    wrapper = renderForm({ editing: node({ id: 2, parentId: 1, code: '1.1', weight: 3 }), parent: null })

    expect(scaleSelect()?.value).toBe('3')
    const selected = selectedOption()
    expect(selected?.textContent?.trim()).toContain('보통 (3)')
    // 이제 척도 위의 값이라 레거시 옵션이 아니다 — 비활성이면 되돌릴 수 없는 값이 된다.
    expect(selected?.disabled).toBe(false)

    submit()
    await wrapper.vm.$nextTick()

    expect(submitted(wrapper).weight).toBe(3)
  })

  /**
   * §2-c를 고정하는 가장 중요한 테스트다. 폼이 미입력을 기본 등급으로 실체화하면 아무것도 고치지
   * 않은 저장이 그 프로젝트의 진척 숫자를 바꾼다 — 그래서 `weightForForm`을 쓰지 않는다.
   */
  it('가중치가 없던 항목을 저장하면 null 그대로 나간다 — 새로 채워 넣지 않는다', async () => {
    wrapper = renderForm({ editing: node({ parentId: null, weight: null }), parent: null })

    submit()
    await wrapper.vm.$nextTick()

    expect(submitted(wrapper).weight).toBeNull()
  })

  /**
   * 위 테스트와 **다른 줄**을 지킨다. 편집 모드는 `nodeToFormInput`(`wbsFormMapping.ts:23`)을 타고
   * 추가 모드는 `WbsForm.vue`의 모듈 스코프 `empty`를 탄다 — 둘 중 하나만 기본 등급으로 바꿔도
   * 나머지 테스트는 전부 통과한다. 선택 상태만 보는 것으로는 모자라 **payload**를 본다.
   *
   * `onSubmit`의 첫 줄이 `if (!form.name.trim()) return`이라, 추가 모드는 업무명을 먼저 채우지
   * 않으면 `emit` 자체가 나가지 않는다(편집 모드는 이름이 이미 차 있다).
   */
  it('새 항목도 미지정을 그대로 보낸다 — 추가 모드의 초기값도 null이다', async () => {
    wrapper = renderForm({ editing: null, parent: null })

    typeName('새 업무')
    await wrapper.vm.$nextTick()
    submit()
    await wrapper.vm.$nextTick()

    expect(submitted(wrapper).weight).toBeNull()
  })

  it('등급을 고르면 그 숫자가 실려 나간다', async () => {
    wrapper = renderForm({ editing: node({ parentId: null, weight: null }), parent: null })

    choose('큼 (5)')
    submit()
    await wrapper.vm.$nextTick()

    expect(submitted(wrapper).weight).toBe(5)
  })

  /**
   * `v-model.number`를 붙이면 `null` 옵션이 `0`으로 바뀔 수 있는데, `0`은 미지정이 아니라
   * **집계 제외**라는 다른 뜻이다(지시서 §7-2). 되돌린 결과는 `null`이어야 한다.
   */
  it('고른 뒤 다시 미지정으로 되돌리면 null이 나간다 — 0이 아니다', async () => {
    wrapper = renderForm({ editing: node({ parentId: null, weight: null }), parent: null })

    choose('큼 (5)')
    await wrapper.vm.$nextTick()
    choose('미지정')
    submit()
    await wrapper.vm.$nextTick()

    expect(submitted(wrapper).weight).toBeNull()
  })

  /**
   * `weightSuggestion.ts`(제안 기능)는 되살리지 않았으므로 "제안"이라는 말 자체가 어느 레벨에서도
   * 다시 나타나면 안 된다 — 등급마다 붙는 기준 문장이 그 역할을 대신한다(지시서 §4).
   * `'형제'`는 별건이라 못 쓴다 — 등급의 기준 문장과 도움말이 정당하게 그 단어를 쓴다.
   */
  it('가중치 제안 힌트는 최상위·하위 어디에도 남아 있지 않다', () => {
    const root = renderForm({ editing: null, parent: null })
    // `ModalDialog`가 `<Teleport to="body">`라 내용은 `wrapper.element`가 아니라 `document.body`에 있다.
    expect(document.body.textContent).not.toContain('제안')
    root.unmount()

    wrapper = renderForm({ editing: null, parent: node({ id: 9, nodeType: 'SUMMARY' }) })
    expect(document.body.textContent).not.toContain('제안')
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
