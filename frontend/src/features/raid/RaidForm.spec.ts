// @vitest-environment happy-dom
import { mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, describe, expect, it } from 'vitest'
import RaidForm from './RaidForm.vue'
import type { RaidItem, RaidItemInput } from '../../api/raidApi'

/**
 * 설명·대응 방안이 여러 줄 입력이 된 뒤의 회귀 방지용이다. 지시서(`docs/tasks/raid_multiline.md`)는
 * "템플릿·스타일 변경이라 새 테스트가 필요 없다"고 적었지만, 이 변경의 핵심은 *줄바꿈이 저장 경로
 * 끝까지 살아서 나가는가*이고 그건 emit 되는 값으로 확인할 수 있다 — 눈으로만 볼 일이 아니다.
 *
 * `RaidForm`은 스스로를 `ModalDialog`로 감싸고, 그것은 `<Teleport to="body">`로 렌더한다.
 * 그래서 `wrapper.find(...)`가 아니라 `document.body`를 직접 조회한다(CLAUDE.md 컴포넌트 테스트 절).
 */
describe('RaidForm — 여러 줄 입력', () => {
  let wrapper: VueWrapper | undefined

  afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
  })

  function open(editing: RaidItem | null = null) {
    wrapper = mount(RaidForm, {
      props: { editing, members: [], wbsTasks: [], sprints: [], backlogItems: [] },
    })
    return wrapper
  }

  /** v-model 은 네이티브 input 이벤트로 갱신되므로, 값을 넣을 때 함께 발생시킨다. */
  function type(el: HTMLTextAreaElement | HTMLInputElement, value: string) {
    el.value = value
    el.dispatchEvent(new Event('input'))
  }

  function textareas() {
    return [...document.body.querySelectorAll('textarea')] as HTMLTextAreaElement[]
  }

  function submitted(): RaidItemInput[] {
    return (wrapper?.emitted('submit') ?? []).map((args) => (args as [RaidItemInput])[0])
  }

  function titleInput() {
    return document.body.querySelector<HTMLInputElement>('.raid-form input[type="text"]')!
  }

  it('설명·대응 방안이 textarea 이고 제목은 한 줄 input 으로 남는다', () => {
    open()

    const [description, response] = textareas()
    expect(description.getAttribute('rows')).toBe('4')
    expect(response.getAttribute('rows')).toBe('3')
    // 서버 @Size(max = 2000) 과 같은 값. 다 쓰고 400 을 받는 것보다 못 넘기는 편이 낫다.
    expect(description.getAttribute('maxlength')).toBe('2000')
    expect(response.getAttribute('maxlength')).toBe('2000')

    // 제목이 여러 줄이 되면 표의 행 머리·대화상자 제목·대시보드 카드가 어그러진다.
    expect(titleInput().tagName).toBe('INPUT')
  })

  it('줄바꿈이 든 값을 그대로 emit 한다', async () => {
    open()

    type(titleInput(), '서버 증설 지연')
    const [description, response] = textareas()
    type(description, '첫 줄\n둘째 줄\n셋째 줄')
    type(response, '대응 1\n대응 2')
    await wrapper!.vm.$nextTick()

    document.body.querySelector('form')!.dispatchEvent(new Event('submit'))
    await wrapper!.vm.$nextTick()

    expect(submitted()).toHaveLength(1)
    expect(submitted()[0].description).toBe('첫 줄\n둘째 줄\n셋째 줄')
    expect(submitted()[0].response).toBe('대응 1\n대응 2')
  })

  // 두 수식키를 한 테스트에서 연달아 누를 수 없다 — 추가(=editing 이 null)일 때 첫 저장이 폼을
  // 비우므로 두 번째는 제목이 없어 막힌다. 그래서 마운트를 나눠 각각 확인한다.
  it.each([
    ['Ctrl+Enter', { ctrlKey: true }],
    ['Cmd+Enter', { metaKey: true }],
  ])('%s 로 저장한다 — Enter 가 줄바꿈이 되므로 저장할 길이 필요하다', async (_label, modifier) => {
    for (const area of [0, 1]) {
      open()
      type(titleInput(), '제목')
      await wrapper!.vm.$nextTick()

      textareas()[area].dispatchEvent(
        new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, ...modifier }),
      )
      await wrapper!.vm.$nextTick()

      expect(submitted()).toHaveLength(1)
      wrapper!.unmount()
      wrapper = undefined
    }
  })

  it('수식키 없는 Enter 는 저장하지 않는다 — 그 자리에서 줄이 바뀌어야 한다', async () => {
    open()
    type(titleInput(), '제목')
    await wrapper!.vm.$nextTick()

    textareas()[0].dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }))
    await wrapper!.vm.$nextTick()

    expect(submitted()).toHaveLength(0)
  })

  it('제목이 비면 Ctrl+Enter 로도 저장되지 않는다 — submit 버튼과 같은 규칙', async () => {
    open()

    textareas()[0].dispatchEvent(
      new KeyboardEvent('keydown', { key: 'Enter', ctrlKey: true, bubbles: true }),
    )
    await wrapper!.vm.$nextTick()

    expect(submitted()).toHaveLength(0)
  })

  it('수정으로 열면 여러 줄 값이 그대로 들어 있다 — 왕복 보존', () => {
    const editing: RaidItem = {
      id: 7,
      type: 'RISK',
      title: '서버 증설 지연',
      description: '첫 줄\n둘째 줄',
      status: 'OPEN',
      probability: 'HIGH',
      impact: 'HIGH',
      ownerMemberId: null,
      ownerName: null,
      links: [],
      dueDate: null,
      response: '대응 1\n대응 2',
      exposure: 9,
      exposureLevel: 'HIGH',
      overdue: false,
      overdueDays: 0,
    }
    open(editing)

    const [description, response] = textareas()
    expect(description.value).toBe('첫 줄\n둘째 줄')
    expect(response.value).toBe('대응 1\n대응 2')
  })

  it('비어 있을 때 textarea 의 초기값이 공백이 아니다 — 태그 사이에 아무것도 없어야 한다', () => {
    open()

    for (const area of textareas()) {
      expect(area.value).toBe('')
    }
  })
})

/**
 * 제출 중 잠금 — 느린 네트워크에서 저장 버튼을 두 번 눌러 두 건이 생기는 사고를 막는다.
 * `submitting`은 부모(RaidView)가 create/update 요청 동안 true로 넘기는 prop이다.
 */
describe('RaidForm — 제출 중 상태', () => {
  let wrapper: VueWrapper | undefined

  afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
  })

  function submitButton() {
    return document.body.querySelector<HTMLButtonElement>('.actions button[type="submit"]')!
  }

  function cancelButton() {
    return document.body.querySelector<HTMLButtonElement>('.actions button[type="button"]')!
  }

  function titleInput() {
    return document.body.querySelector<HTMLInputElement>('.raid-form input[type="text"]')!
  }

  it('submitting이 true면 저장 버튼이 비활성화되고 문구가 진행형으로 바뀐다(추가)', async () => {
    wrapper = mount(RaidForm, {
      props: { editing: null, members: [], wbsTasks: [], sprints: [], backlogItems: [], submitting: true },
    })
    titleInput().value = '제목'
    titleInput().dispatchEvent(new Event('input'))
    await wrapper.vm.$nextTick()

    expect(submitButton().disabled).toBe(true)
    expect(submitButton().textContent?.trim()).toBe('추가 중…')
  })

  it('submitting이 true이고 제목이 있으면 취소 버튼도 비활성화된다', async () => {
    wrapper = mount(RaidForm, {
      props: { editing: null, members: [], wbsTasks: [], sprints: [], backlogItems: [], submitting: true },
    })

    expect(cancelButton().disabled).toBe(true)
  })

  it('submitting 중에는 폼을 제출해도 다시 emit하지 않는다', async () => {
    wrapper = mount(RaidForm, {
      props: { editing: null, members: [], wbsTasks: [], sprints: [], backlogItems: [], submitting: true },
    })
    titleInput().value = '제목'
    titleInput().dispatchEvent(new Event('input'))
    await wrapper.vm.$nextTick()

    document.body.querySelector('form')!.dispatchEvent(new Event('submit'))
    await wrapper.vm.$nextTick()

    expect(wrapper.emitted('submit')).toBeUndefined()
  })

  function fieldHints() {
    return [...document.body.querySelectorAll('.field-hint')].map((el) => el.textContent?.trim())
  }

  it('제목이 비어 있으면 저장 버튼이 비활성화된 이유를 문구로 보여준다', () => {
    // members에 값을 채워, "소유자로 지정할 구성원이 없습니다" 힌트와 섞이지 않게 한다.
    wrapper = mount(RaidForm, {
      props: {
        editing: null,
        members: [{ id: 1, name: '김재학', email: null, position: null, createdAt: '', updatedAt: '' }],
        wbsTasks: [],
        sprints: [],
        backlogItems: [],
      },
    })

    expect(fieldHints()).toContain('제목을 입력해야 저장할 수 있습니다.')
  })

  it('제목을 입력하면 그 문구가 사라진다', async () => {
    wrapper = mount(RaidForm, {
      props: {
        editing: null,
        members: [{ id: 1, name: '김재학', email: null, position: null, createdAt: '', updatedAt: '' }],
        wbsTasks: [],
        sprints: [],
        backlogItems: [],
      },
    })

    titleInput().value = '제목'
    titleInput().dispatchEvent(new Event('input'))
    await wrapper.vm.$nextTick()

    expect(fieldHints()).not.toContain('제목을 입력해야 저장할 수 있습니다.')
  })
})

/**
 * 한글 IME 조합 중 Ctrl+Enter — 한때 실제로 있었던 결함의 회귀 테스트다.
 *
 * Vue 의 `v-model`은 조합 중에는 모델을 갱신하지 않는다. `runtime-dom` 의 `vModelText` 가
 * `compositionstart` 에서 `el.composing = true` 로 잠그고, input 리스너가
 * `if (e.target.composing) return` 으로 빠져나가며, `compositionend` 가 되어서야 `input` 을
 * 다시 쏘아 모델을 따라잡게 한다. 그런데 Vue 의 `withKeys`(`runtime-dom:1892-1906`)는
 * `event.key` 만 보고 `isComposing` 을 보지 않는다. 그래서 가드가 없던 동안에는 조합이 끝나기
 * 전에 온 Ctrl+Enter 가 **화면에 보이는 마지막 음절이 빠진 값을 저장**했다.
 * 한국어 문장의 마지막 글자는 거의 항상 조합 중이라 실제로 부딪히는 자리였다.
 *
 * 자동 확인의 경계: happy-dom 은 실제 IME 를 흉내내지 못한다. 이벤트 순서는 여기서 손으로
 * 넣은 것이고, "실제 브라우저가 Ctrl+Enter 앞에서 조합을 커밋하는가"는 브라우저 없이 확정할
 * 수 없다. 다만 가드는 커밋이 먼저인 경우에는 아무 일도 하지 않으므로 어느 쪽이든 안전하다.
 */
describe('RaidForm — 한글 IME 조합 중 Ctrl+Enter', () => {
  function open() {
    const wrapper = mount(RaidForm, {
      props: { editing: null, members: [], wbsTasks: [], sprints: [], backlogItems: [] },
    })
    const inputs = document.body.querySelectorAll<HTMLInputElement>('.raid-form input[type="text"]')
    const title = inputs[inputs.length - 1]
    title.value = '제목'
    title.dispatchEvent(new Event('input'))

    const areas = document.body.querySelectorAll('textarea')
    return { wrapper, description: areas[areas.length - 2] as HTMLTextAreaElement }
  }

  function ctrlEnter(el: HTMLElement, composing: boolean) {
    const event = new KeyboardEvent('keydown', {
      key: 'Enter',
      ctrlKey: true,
      bubbles: true,
      cancelable: true,
      ...(composing ? { isComposing: true } : {}),
    })
    el.dispatchEvent(event)
    return event
  }

  function submitted(wrapper: ReturnType<typeof open>['wrapper']): RaidItemInput[] {
    return (wrapper.emitted('submit') ?? []).map((a) => (a as [RaidItemInput])[0])
  }

  it('조합 중에는 저장하지 않는다 — 마지막 음절이 빠진 값이 저장되면 안 된다', async () => {
    const { wrapper, description } = open()

    // 확정된 부분까지는 평범한 input 으로 모델에 들어간다.
    description.value = '대응 방안을 확인'
    description.dispatchEvent(new Event('input'))
    await wrapper.vm.$nextTick()

    // 마지막 음절 "함"을 조합하는 중 — Vue 는 이 input 을 무시한다.
    description.dispatchEvent(new CompositionEvent('compositionstart', { bubbles: true }))
    description.value = '대응 방안을 확인함'
    description.dispatchEvent(new Event('input'))
    await wrapper.vm.$nextTick()

    ctrlEnter(description, true)
    await wrapper.vm.$nextTick()

    // 예전에는 여기서 "대응 방안을 확인"(한 글자 부족)이 저장됐다.
    expect(submitted(wrapper)).toHaveLength(0)
    wrapper.unmount()
  })

  it('조합 중에는 기본 동작도 막지 않는다 — IME 커밋 경로에 끼어들 이유가 없다', async () => {
    const { wrapper, description } = open()

    description.dispatchEvent(new CompositionEvent('compositionstart', { bubbles: true }))
    const event = ctrlEnter(description, true)
    await wrapper.vm.$nextTick()

    // `.prevent` 가 템플릿에 있으면 수식자 가드가 핸들러보다 먼저 돌아 여기서도 막힌다
    // (`withModifiers`, runtime-dom:1806-1817). 그래서 핸들러 안으로 옮겼다.
    expect(event.defaultPrevented).toBe(false)
    wrapper.unmount()
  })

  it('조합이 끝난 뒤의 Ctrl+Enter 는 마지막 음절까지 저장한다', async () => {
    const { wrapper, description } = open()

    description.dispatchEvent(new CompositionEvent('compositionstart', { bubbles: true }))
    description.value = '대응 방안을 확인함'
    description.dispatchEvent(new Event('input'))
    description.dispatchEvent(new CompositionEvent('compositionend', { bubbles: true }))
    await wrapper.vm.$nextTick()

    const event = ctrlEnter(description, false)
    await wrapper.vm.$nextTick()

    expect(submitted(wrapper)[0]?.description).toBe('대응 방안을 확인함')
    // `.prevent` 를 템플릿에서 뺀 것을 핸들러가 대신한다. 이 단언이 없으면 다음 사람이
    // 사라진 `.prevent` 를 실수로 지운 것으로 보고 되돌린다.
    expect(event.defaultPrevented).toBe(true)
    wrapper.unmount()
  })
})
