// @vitest-environment happy-dom
import { mount, type VueWrapper } from '@vue/test-utils'
import { nextTick } from 'vue'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { columnPrefs, resetColumnPrefs, setColumnPrefs } from './wbsColumnPrefs'
import WbsColumnSettings from './WbsColumnSettings.vue'

/**
 * `wbs-tree-columns` 지시서 3-3 — 열 설정 대화상자의 렌더링·상호작용을 고정한다. 판정 자체
 * (`visibleColumns`·`pinOffsets`·`normalizePrefs`)는 `wbsColumns.spec.ts`가 덮으므로, 여기서는
 * 이 폼이 그 모델을 옳게 읽고 쓰는지만 본다.
 *
 * `ModalDialog` 기반이라 `<Teleport to="body">`로 렌더한다 — `wrapper.find(...)`로는 내용을 찾을
 * 수 없으므로 `document.body`를 직접 조회한다(`ModalDialog.spec.ts`가 같은 방식). 같은 이유로
 * 매 테스트 뒤 `wrapper.unmount()`를 불러야 다음 테스트가 이전 대화상자까지 함께 조회하지 않는다.
 *
 * `columnPrefs`는 모듈 스코프라 테스트끼리 상태가 샌다 — 매 테스트 앞뒤로 `resetColumnPrefs()`를
 * 부른다.
 */
describe('WbsColumnSettings', () => {
  let wrapper: VueWrapper | undefined

  beforeEach(() => {
    resetColumnPrefs()
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
    resetColumnPrefs()
  })

  it('대화상자 제목이 "열 설정"이고 열 체크박스가 열 개다', () => {
    wrapper = mount(WbsColumnSettings)

    expect(document.body.querySelector('.head h2')?.textContent).toBe('열 설정')
    const checkboxes = document.body.querySelectorAll('input[type="checkbox"][data-column]')
    expect(checkboxes.length).toBe(10)
  })

  it('업무명 체크박스는 disabled이고 title이 이유를 말한다', () => {
    wrapper = mount(WbsColumnSettings)

    const nameCheckbox = document.body.querySelector<HTMLInputElement>(
      'input[type="checkbox"][data-column="name"]',
    )
    expect(nameCheckbox).not.toBeNull()
    expect(nameCheckbox?.disabled).toBe(true)
    expect(nameCheckbox?.title.length ?? 0).toBeGreaterThan(0)
  })

  it('체크박스를 끄면 columnPrefs.hidden에 그 키가 들어간다 — 즉시 반영', async () => {
    wrapper = mount(WbsColumnSettings)

    const ownerCheckbox = document.body.querySelector<HTMLInputElement>(
      'input[type="checkbox"][data-column="owner"]',
    )
    expect(ownerCheckbox).not.toBeNull()
    expect(ownerCheckbox!.checked).toBe(true)

    ownerCheckbox!.checked = false
    ownerCheckbox!.dispatchEvent(new Event('change'))
    await nextTick()

    expect(columnPrefs.value.hidden).toContain('owner')
  })

  it('고정 라디오를 고르면 columnPrefs.pin이 바뀐다', async () => {
    wrapper = mount(WbsColumnSettings)

    const radio = document.body.querySelector<HTMLInputElement>(
      'input[type="radio"][name="wbs-pin"][value="name"]',
    )
    expect(radio).not.toBeNull()

    radio!.checked = true
    radio!.dispatchEvent(new Event('change'))
    await nextTick()

    expect(columnPrefs.value.pin).toBe('name')
  })

  it('[data-action="reset"] → 기본값(hidden: [], pin: "none")으로 돌아간다', async () => {
    setColumnPrefs({ hidden: ['owner', 'tags'], pin: 'code' })
    wrapper = mount(WbsColumnSettings)

    const resetButton = document.body.querySelector<HTMLButtonElement>('button[data-action="reset"]')
    expect(resetButton).not.toBeNull()

    resetButton!.click()
    await nextTick()

    expect(columnPrefs.value).toEqual({ hidden: [], pin: 'none' })
  })

  it('[닫기]가 close를 emit 한다', async () => {
    wrapper = mount(WbsColumnSettings)

    const closeButton = [...document.body.querySelectorAll('button')].find(
      (button) => button.textContent?.trim() === '닫기',
    )
    expect(closeButton).toBeTruthy()

    closeButton!.dispatchEvent(new Event('click', { bubbles: true }))
    await nextTick()

    expect(wrapper.emitted('close')).toBeTruthy()
  })
})
