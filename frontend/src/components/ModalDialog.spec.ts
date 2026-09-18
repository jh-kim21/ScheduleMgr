// @vitest-environment happy-dom
import { mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, describe, expect, it } from 'vitest'
import ModalDialog from './ModalDialog.vue'

/**
 * 이 저장소 최초의 컴포넌트 마운트 테스트다. `@vue/test-utils` + `happy-dom` 도입의 증명용으로
 * `ModalDialog`를 골랐다 — 작고, 여러 화면이 쓰며, 지금 아무도 건드리지 않는다.
 *
 * `ModalDialog`는 `<Teleport to="body">`로 렌더한다(CLAUDE.md 화면 레이아웃 규칙). Teleport는
 * 컴포넌트 자신의 렌더 트리가 아니라 실제 DOM 대상에 붙기 때문에, `wrapper.find(...)`는 이
 * 내용을 찾지 못한다(`wrapper.element`는 Teleport가 남긴 빈 자리표시자일 뿐이다). 그래서 아래
 * 테스트는 `document.body`를 직접 조회한다.
 *
 * 같은 이유로 teleport된 내용은 `wrapper.unmount()`를 부르기 전에는 `document.body`에 그대로
 * 남는다 — 다음 테스트가 이전 테스트의 대화상자를 함께 조회하게 되므로 매 테스트 뒤 언마운트한다.
 */
describe('ModalDialog', () => {
  let wrapper: VueWrapper | undefined

  afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
  })

  it('title을 header에 렌더하고, body로 teleport한다', () => {
    wrapper = mount(ModalDialog, {
      props: { title: '항목 추가' },
    })

    const panel = document.body.querySelector('[role="dialog"]')
    expect(panel).not.toBeNull()
    expect(panel?.getAttribute('aria-label')).toBe('항목 추가')
    expect(document.body.querySelector('.head h2')?.textContent).toBe('항목 추가')
  })

  it('error prop이 없으면 오류 영역을 렌더하지 않는다', () => {
    wrapper = mount(ModalDialog, {
      props: { title: '항목 추가' },
    })

    expect(document.body.querySelector('[role="alert"]')).toBeNull()
  })

  it('error prop이 있을 때만 오류 메시지를 렌더한다 — 저장 거부는 대화상자 안에서 보여야 한다', () => {
    wrapper = mount(ModalDialog, {
      props: { title: '항목 추가', error: '이름은 필수입니다' },
    })

    const alert = document.body.querySelector('[role="alert"]')
    expect(alert).not.toBeNull()
    expect(alert?.textContent).toBe('이름은 필수입니다')
  })

  it('닫기 버튼을 누르면 close 이벤트를 발행한다', async () => {
    wrapper = mount(ModalDialog, {
      props: { title: '항목 추가' },
    })

    const closeButton = document.body.querySelector<HTMLButtonElement>('.close')
    closeButton?.click()
    await wrapper.vm.$nextTick()

    expect(wrapper.emitted('close')).toHaveLength(1)
  })

  it('배경(backdrop)을 누르면 close를 발행하지만, 패널 안 클릭은 무시한다', async () => {
    wrapper = mount(ModalDialog, {
      props: { title: '항목 추가' },
    })

    const panel = document.body.querySelector<HTMLElement>('.panel')
    panel?.dispatchEvent(new MouseEvent('mousedown', { bubbles: true }))
    await wrapper.vm.$nextTick()
    expect(wrapper.emitted('close')).toBeUndefined()

    const backdrop = document.body.querySelector<HTMLElement>('.backdrop')
    backdrop?.dispatchEvent(new MouseEvent('mousedown', { bubbles: true }))
    await wrapper.vm.$nextTick()
    expect(wrapper.emitted('close')).toHaveLength(1)
  })

  /**
   * 중첩 시나리오 — MemberEditor의 "구성원 관리" 안 "구성원 추가"처럼, 이 앱은 실제로
   * ModalDialog를 겹쳐 쓴다. 아래 테스트들은 모듈 스코프 스택이 "최상단만 반응한다"를
   * 지키는지, 그리고 배경 스크롤 잠금이 참조 카운트로 다뤄지는지를 고정한다.
   */
  describe('중첩', () => {
    let outer: VueWrapper | undefined
    let inner: VueWrapper | undefined

    afterEach(() => {
      inner?.unmount()
      outer?.unmount()
      inner = undefined
      outer = undefined
    })

    it('안쪽에서 Escape를 눌러도 바깥은 닫히지 않고, 안쪽만 닫힌다', async () => {
      outer = mount(ModalDialog, { props: { title: '바깥' } })
      inner = mount(ModalDialog, { props: { title: '안쪽' } })

      document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))
      await outer.vm.$nextTick()
      await inner.vm.$nextTick()

      expect(inner.emitted('close')).toHaveLength(1)
      expect(outer.emitted('close')).toBeUndefined()
    })

    it('안쪽이 닫혀도 바깥이 아직 열려 있으면 배경 스크롤은 계속 잠긴 채로 남는다', async () => {
      outer = mount(ModalDialog, { props: { title: '바깥' } })
      inner = mount(ModalDialog, { props: { title: '안쪽' } })

      expect(document.body.style.overflow).toBe('hidden')

      inner.unmount()
      inner = undefined
      await outer.vm.$nextTick()

      // 안쪽 하나가 닫혔을 뿐 바깥은 여전히 열려 있으므로, 표가 움직이면 안 된다.
      expect(document.body.style.overflow).toBe('hidden')
    })

    it('마지막 대화상자까지 닫히면 배경 스크롤 잠금이 원래 값으로 되돌아간다', async () => {
      const original = document.body.style.overflow
      outer = mount(ModalDialog, { props: { title: '바깥' } })
      inner = mount(ModalDialog, { props: { title: '안쪽' } })

      inner.unmount()
      outer.unmount()
      inner = undefined
      outer = undefined

      expect(document.body.style.overflow).toBe(original)
    })

    it('안쪽이 열려 있는 동안 바깥 패널은 inert 로 표시된다', () => {
      outer = mount(ModalDialog, { props: { title: '바깥' } })
      const outerBackdrop = document.body.querySelectorAll<HTMLElement>('.backdrop')[0]
      expect(outerBackdrop.hasAttribute('inert')).toBe(false)

      inner = mount(ModalDialog, { props: { title: '안쪽' } })
      expect(outerBackdrop.hasAttribute('inert')).toBe(true)
      expect(outerBackdrop.getAttribute('aria-hidden')).toBe('true')

      inner.unmount()
      inner = undefined
      expect(outerBackdrop.hasAttribute('inert')).toBe(false)
    })

    it('Shift+Tab 이 안쪽 패널 안에서만 순환한다 — 바깥으로 새지 않는다', async () => {
      outer = mount(ModalDialog, { props: { title: '바깥' } })
      inner = mount(ModalDialog, {
        props: { title: '안쪽' },
        slots: { default: '<input type="text" />' },
      })
      await inner.vm.$nextTick()

      const panels = document.body.querySelectorAll<HTMLElement>('.panel')
      const innerPanel = panels[panels.length - 1]
      const innerInput = innerPanel.querySelector<HTMLInputElement>('input')!
      const innerClose = innerPanel.querySelector<HTMLButtonElement>('.close')!

      // 안쪽 패널의 첫 포커스 가능 항목(닫기 버튼)에서 Shift+Tab — 경계에서 안쪽의 마지막
      // 항목(입력칸)으로 순환해야 한다. 바깥 패널로 새 나가면(예: 바깥의 닫기 버튼) 회귀다.
      // (jsdom/happy-dom은 실제 브라우저의 기본 Tab 이동을 구현하지 않으므로, 경계가 아닌
      // 중간 항목의 Tab은 우리 핸들러가 아무 것도 하지 않아 검증할 수 없다 — 그래서 경계값을
      // 확인한다.)
      innerClose.focus()
      expect(document.activeElement).toBe(innerClose)

      const event = new KeyboardEvent('keydown', { key: 'Tab', shiftKey: true, bubbles: true, cancelable: true })
      document.dispatchEvent(event)

      expect(document.activeElement).toBe(innerInput)
    })
  })
})
