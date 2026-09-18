// @vitest-environment happy-dom
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { MemberInput, ProjectMember } from '../../api/memberApi'
import MemberEditor from './MemberEditor.vue'

/**
 * `ModalDialog`(Teleport to="body")를 감싼 컴포넌트라 `wrapper.find(...)`가 아니라
 * `document.body`를 직접 조회한다 — `ModalDialog.spec.ts`와 같은 이유·같은 방식이다. 추가·수정
 * 대화상자는 "구성원 관리" 대화상자 안에 중첩되므로(Step 2의 중첩 지원 위에서 동작), 두 패널이
 * 모두 `document.body`에 형제로 붙는다.
 *
 * 추가·수정 제출은 emit이 아니라 함수 prop(`onSubmitAdd`/`onSubmitUpdate`)이다 — `useMembers`의
 * `create`/`update`가 서버가 받아들였는지를 `Promise<boolean>`으로 돌려주므로, 그 값을 그대로
 * 받아 "성공했을 때만 닫는다"를 목록 변화 추측 없이 검증한다. 목록 갱신은 이 컴포넌트가 신경 쓰지
 * 않으므로 `members` prop은 테스트 내내 고정해 둔다.
 */
function member(overrides: Partial<ProjectMember> = {}): ProjectMember {
  return {
    id: 1,
    name: '김재학',
    email: null,
    position: null,
    createdAt: '2026-01-01T00:00:00',
    updatedAt: '2026-01-01T00:00:00',
    ...overrides,
  }
}

function panels() {
  return document.body.querySelectorAll<HTMLElement>('.panel')
}

/** 항상 최상단(가장 안쪽) 패널 — 열려 있으면 추가/수정 대화상자, 아니면 "구성원 관리" 자체. */
function topPanel(): HTMLElement {
  const all = panels()
  return all[all.length - 1]
}

function addButton(): HTMLButtonElement {
  return document.body.querySelector<HTMLButtonElement>('.add')!
}

function nameInput(): HTMLInputElement {
  return topPanel().querySelector<HTMLInputElement>('input[type="text"]')!
}

function submitButton(): HTMLButtonElement {
  return topPanel().querySelector<HTMLButtonElement>('button[type="submit"]')!
}

function cancelButton(): HTMLButtonElement {
  return [...topPanel().querySelectorAll<HTMLButtonElement>('.dialog-actions button')].find(
    (b) => b.type === 'button',
  )!
}

async function setName(value: string) {
  nameInput().value = value
  nameInput().dispatchEvent(new Event('input'))
}

describe('MemberEditor — 추가·수정 통합 대화상자', () => {
  let wrapper: VueWrapper | undefined

  afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
    vi.unstubAllGlobals()
  })

  function renderEditor(
    members: ProjectMember[],
    overrides: Partial<{
      onSubmitAdd: (input: MemberInput) => Promise<boolean>
      onSubmitUpdate: (memberId: number, input: MemberInput) => Promise<boolean>
    }> = {},
  ) {
    return mount(MemberEditor, {
      props: {
        projectName: '웹사이트 개편',
        members,
        loading: false,
        error: null,
        onSubmitAdd: vi.fn().mockResolvedValue(true),
        onSubmitUpdate: vi.fn().mockResolvedValue(true),
        ...overrides,
      },
      attachTo: document.body,
    })
  }

  it('기본 상태에서는 추가·수정 대화상자가 열려 있지 않다 — 패널이 하나(구성원 관리)뿐이다', () => {
    wrapper = renderEditor([member()])

    expect(panels()).toHaveLength(1)
  })

  it('＋ 구성원 추가를 누르면 "구성원 추가" 대화상자가 중첩해서 열리고, 입력칸은 비어 있다', async () => {
    wrapper = renderEditor([member()])

    await addButton().click()

    expect(panels()).toHaveLength(2)
    expect(topPanel().getAttribute('aria-label')).toBe('구성원 추가')
    expect(nameInput().value).toBe('')
  })

  it('수정 버튼을 누르면 "구성원 수정" 대화상자가 열리고, 기존 값이 채워진다', async () => {
    wrapper = renderEditor([member({ name: '이승하', position: 'PM', email: 'lee@example.com' })])

    const editButton = [...document.body.querySelectorAll<HTMLButtonElement>('.list button')].find(
      (b) => b.textContent === '수정',
    )!
    await editButton.click()

    expect(topPanel().getAttribute('aria-label')).toBe('구성원 수정')
    expect(nameInput().value).toBe('이승하')
    const [, positionInput, emailInput] = topPanel().querySelectorAll<HTMLInputElement>('input')
    expect(positionInput.value).toBe('PM')
    expect(emailInput.value).toBe('lee@example.com')
  })

  it('추가 제출 성공 — onSubmitAdd가 정리된 값과 함께 호출되고, true를 돌려주면 대화상자가 닫힌다', async () => {
    const onSubmitAdd = vi.fn().mockResolvedValue(true)
    wrapper = renderEditor([member()], { onSubmitAdd })

    await addButton().click()
    await setName('  박서준  ')
    await wrapper.vm.$nextTick()
    submitButton().click()
    await flushPromises()

    expect(onSubmitAdd).toHaveBeenCalledWith({ name: '박서준', email: null, position: null })
    expect(panels()).toHaveLength(1)
  })

  it('추가 제출 거부 — onSubmitAdd가 false를 돌려주면 대화상자가 열린 채 입력값이 남는다', async () => {
    const onSubmitAdd = vi.fn().mockResolvedValue(false)
    wrapper = renderEditor([member()], { onSubmitAdd })

    await addButton().click()
    await setName('중복된이름')
    await wrapper.vm.$nextTick()
    submitButton().click()
    await flushPromises()

    expect(onSubmitAdd).toHaveBeenCalledTimes(1)
    expect(panels()).toHaveLength(2)
    expect(nameInput().value).toBe('중복된이름')
  })

  it('수정 제출 성공 — onSubmitUpdate가 memberId와 정리된 값으로 호출되고, true면 닫힌다', async () => {
    const onSubmitUpdate = vi.fn().mockResolvedValue(true)
    wrapper = renderEditor([member({ id: 5, name: '이승하' })], { onSubmitUpdate })

    const editButton = [...document.body.querySelectorAll<HTMLButtonElement>('.list button')].find(
      (b) => b.textContent === '수정',
    )!
    await editButton.click()
    await setName('이승하(수정)')
    await wrapper.vm.$nextTick()
    submitButton().click()
    await flushPromises()

    expect(onSubmitUpdate).toHaveBeenCalledWith(5, { name: '이승하(수정)', email: null, position: null })
    expect(panels()).toHaveLength(1)
  })

  it('수정 제출 거부 — onSubmitUpdate가 false를 돌려주면 대화상자가 열린 채 입력값이 남는다', async () => {
    const onSubmitUpdate = vi.fn().mockResolvedValue(false)
    wrapper = renderEditor([member({ id: 5, name: '이승하' })], { onSubmitUpdate })

    const editButton = [...document.body.querySelectorAll<HTMLButtonElement>('.list button')].find(
      (b) => b.textContent === '수정',
    )!
    await editButton.click()
    await setName('네트워크 오류로 안 먹힘')
    await wrapper.vm.$nextTick()
    submitButton().click()
    await flushPromises()

    expect(onSubmitUpdate).toHaveBeenCalledTimes(1)
    expect(panels()).toHaveLength(2)
    expect(nameInput().value).toBe('네트워크 오류로 안 먹힘')
  })

  it('제출 중에는 저장 버튼이 비활성화되고 문구가 진행형으로 바뀐다 — 응답이 올 때까지 다시 제출을 막는다', async () => {
    let resolvePending!: (ok: boolean) => void
    const onSubmitAdd = vi.fn(() => new Promise<boolean>((resolve) => { resolvePending = resolve }))
    wrapper = renderEditor([member()], { onSubmitAdd })

    await addButton().click()
    await setName('박서준')
    await wrapper.vm.$nextTick()
    submitButton().click()
    await wrapper.vm.$nextTick()

    expect(submitButton().disabled).toBe(true)
    expect(submitButton().textContent?.trim()).toBe('추가 중…')
    expect(cancelButton().disabled).toBe(true)

    resolvePending(true)
    await flushPromises()

    expect(panels()).toHaveLength(1)
  })

  it('취소를 누르면 onSubmitAdd/onSubmitUpdate를 부르지 않고 대화상자만 닫힌다', async () => {
    const onSubmitAdd = vi.fn().mockResolvedValue(true)
    const onSubmitUpdate = vi.fn().mockResolvedValue(true)
    wrapper = renderEditor([member()], { onSubmitAdd, onSubmitUpdate })

    await addButton().click()
    await setName('지우고 취소할 이름')
    await wrapper.vm.$nextTick()
    cancelButton().click()
    await wrapper.vm.$nextTick()

    expect(panels()).toHaveLength(1)
    expect(onSubmitAdd).not.toHaveBeenCalled()
    expect(onSubmitUpdate).not.toHaveBeenCalled()
  })

  /**
   * `ModalDialog`의 `dirty` prop(opt-in) 실전 배선 — 이름을 입력한 채 Escape를 누르면 한 번
   * 확인을 거친다. happy-dom이 `window.confirm`을 구현하지 않으므로 `vi.stubGlobal`로 대신한다
   * (CLAUDE.md 컴포넌트 테스트 절).
   */
  describe('dirty — 미저장 변경 확인', () => {
    it('이름을 입력하지 않은 채 Escape를 누르면 확인 없이 바로 닫힌다', async () => {
      const confirmSpy = vi.fn()
      vi.stubGlobal('confirm', confirmSpy)
      wrapper = renderEditor([member()])

      await addButton().click()
      document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))
      await wrapper.vm.$nextTick()

      expect(confirmSpy).not.toHaveBeenCalled()
      expect(panels()).toHaveLength(1)
    })

    it('이름을 입력한 채 Escape를 누르면 확인을 거치고, 거부하면 열린 채 남는다', async () => {
      vi.stubGlobal('confirm', vi.fn().mockReturnValue(false))
      wrapper = renderEditor([member()])

      await addButton().click()
      await setName('쓰다 만 이름')
      await wrapper.vm.$nextTick()
      document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))
      await wrapper.vm.$nextTick()

      expect(panels()).toHaveLength(2)
      expect(nameInput().value).toBe('쓰다 만 이름')
    })

    it('확인하면 닫힌다', async () => {
      vi.stubGlobal('confirm', vi.fn().mockReturnValue(true))
      wrapper = renderEditor([member()])

      await addButton().click()
      await setName('쓰다 만 이름')
      await wrapper.vm.$nextTick()
      document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))
      await wrapper.vm.$nextTick()

      expect(panels()).toHaveLength(1)
    })

    it('수정 모드에서 값을 바꾸지 않았으면 Escape에 확인이 없다', async () => {
      const confirmSpy = vi.fn()
      vi.stubGlobal('confirm', confirmSpy)
      wrapper = renderEditor([member({ name: '이승하' })])

      const editButton = [...document.body.querySelectorAll<HTMLButtonElement>('.list button')].find(
        (b) => b.textContent === '수정',
      )!
      await editButton.click()
      document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))
      await wrapper.vm.$nextTick()

      expect(confirmSpy).not.toHaveBeenCalled()
      expect(panels()).toHaveLength(1)
    })
  })
})
