// @vitest-environment happy-dom
import { mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, describe, expect, it } from 'vitest'
import CommitForm from './CommitForm.vue'

/**
 * `ModalDialog`(Teleport to="body")를 감싸므로 `document.body`를 직접 조회한다(CLAUDE.md 컴포넌트
 * 테스트 절). `submitting`은 부모(CommitPanel)가 커밋 요청 중일 때 넘기는 prop이다 — 느린
 * 네트워크에서 커밋 버튼을 두 번 눌러 커밋이 두 개 생기는 사고를 막는다.
 */
describe('CommitForm — 제출 중 상태', () => {
  let wrapper: VueWrapper | undefined

  afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
  })

  function submitButton() {
    return document.body.querySelector<HTMLButtonElement>('.actions button[type="submit"]')!
  }

  function cancelButton() {
    return document.body.querySelector<HTMLButtonElement>('.actions button.ghost')!
  }

  it('기본 상태에서는 버튼이 활성 상태고 문구가 "커밋"이다', () => {
    wrapper = mount(CommitForm, { props: {} })

    expect(submitButton().disabled).toBe(false)
    expect(submitButton().textContent?.trim()).toBe('커밋')
  })

  it('submitting이 true면 커밋·취소 버튼이 모두 비활성화되고 문구가 진행형으로 바뀐다', () => {
    wrapper = mount(CommitForm, { props: { submitting: true } })

    expect(submitButton().disabled).toBe(true)
    expect(submitButton().textContent?.trim()).toBe('커밋하는 중…')
    expect(cancelButton().disabled).toBe(true)
  })

  it('submitting 중에는 폼을 제출해도 다시 emit하지 않는다', async () => {
    wrapper = mount(CommitForm, { props: { submitting: true } })

    document.body.querySelector('form')!.dispatchEvent(new Event('submit'))
    await wrapper.vm.$nextTick()

    expect(wrapper.emitted('submit')).toBeUndefined()
  })

  it('submitting이 아니면 정상적으로 제출된다', async () => {
    wrapper = mount(CommitForm, { props: {} })

    document.body.querySelector('form')!.dispatchEvent(new Event('submit'))
    await wrapper.vm.$nextTick()

    expect(wrapper.emitted('submit')).toHaveLength(1)
  })
})
