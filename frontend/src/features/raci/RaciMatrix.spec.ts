// @vitest-environment happy-dom
import { afterEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils'
import RaciMatrixView from './RaciMatrix.vue'
import type { RaciMatrix } from '../../api/raciApi'
import type { CellEntry } from './useRaci'

/*
 * happy-dom에는 레이아웃 엔진이 없어 getBoundingClientRect()가 전부 0이다. 그래서 여기서는 px 값이나
 * sticky 동작이 아니라 "측정이 불렸는가"(max-height 인라인 스타일 존재)와 "리스너가 정리되는가"만 본다.
 */

let wrapper: VueWrapper | null = null

afterEach(() => {
  wrapper?.unmount()
  wrapper = null
  vi.restoreAllMocks()
})

function matrix(overrides: Partial<RaciMatrix> = {}): RaciMatrix {
  return {
    members: [
      { id: 1, projectId: 1, name: '김PL', position: 'PL' },
      { id: 2, projectId: 1, name: '이개발', position: null },
    ],
    tasks: [
      { id: 10, parentId: null, code: '1', level: 1, name: '설계', summary: false, storyAssignees: [] },
      { id: 11, parentId: null, code: '2', level: 1, name: '구현', summary: false, storyAssignees: [] },
    ],
    cells: [],
    issues: [],
    ...overrides,
  } as unknown as RaciMatrix
}

function mountMatrix(data: RaciMatrix) {
  wrapper = mount(RaciMatrixView, {
    props: {
      data,
      cellIndex: new Map<string, CellEntry>(),
      issuesByTask: new Map(),
    },
  } as never)
  return wrapper
}

function maxHeightPx(el: HTMLElement): number {
  const value = el.style.maxHeight
  expect(value).toMatch(/^\d+(\.\d+)?px$/)
  return parseFloat(value)
}

describe('RaciMatrix 높이 측정', () => {
  it('데이터가 있는 채로 마운트하면 .matrix-wrap에 max-height가 붙는다', async () => {
    const w = mountMatrix(matrix())
    await flushPromises()

    const wrap = w.find('.matrix-wrap')
    expect(wrap.exists()).toBe(true)
    // 레이아웃이 없어 top=0 이므로 innerHeight-16 이고, 하한 320px 이상이다. 정확한 px은 단정하지 않는다.
    expect(maxHeightPx(wrap.element as HTMLElement)).toBeGreaterThanOrEqual(320)
  })

  it('.matrix-wrap이 처음에 없다가 나타나도(구성원 없음 → 있음) max-height가 붙는다', async () => {
    const w = mountMatrix(matrix({ members: [] }))
    await flushPromises()
    expect(w.find('.matrix-wrap').exists()).toBe(false)

    await w.setProps({ data: matrix() })
    await flushPromises()

    const wrap = w.find('.matrix-wrap')
    expect(wrap.exists()).toBe(true)
    expect(maxHeightPx(wrap.element as HTMLElement)).toBeGreaterThanOrEqual(320)
  })

  it('업무가 없다가 생겨도(빈 목록 → 있음) max-height가 붙는다', async () => {
    const w = mountMatrix(matrix({ tasks: [] }))
    await flushPromises()
    expect(w.find('.matrix-wrap').exists()).toBe(false)

    await w.setProps({ data: matrix() })
    await flushPromises()

    expect(maxHeightPx(w.find('.matrix-wrap').element as HTMLElement)).toBeGreaterThanOrEqual(320)
  })

  it('창이 아주 낮아도 320px 하한이 지켜진다', async () => {
    vi.stubGlobal('innerHeight', 100)
    try {
      const w = mountMatrix(matrix())
      await flushPromises()
      expect(maxHeightPx(w.find('.matrix-wrap').element as HTMLElement)).toBe(320)
    } finally {
      vi.unstubAllGlobals()
    }
  })

  it('resize 시 다시 측정한다 (창 높이 변화가 max-height에 반영)', async () => {
    const w = mountMatrix(matrix())
    await flushPromises()
    const el = w.find('.matrix-wrap').element as HTMLElement
    const before = maxHeightPx(el)

    // top=0 이므로 값은 innerHeight-16. 창을 200px 키우면 같은 만큼 늘어난다.
    vi.stubGlobal('innerHeight', window.innerHeight + 200)
    try {
      window.dispatchEvent(new Event('resize'))
      await flushPromises()
      expect(maxHeightPx(el)).toBe(before + 200)
    } finally {
      vi.unstubAllGlobals()
    }
  })

  it('언마운트하면 등록했던 resize 리스너를 같은 핸들러로 해제한다', async () => {
    const add = vi.spyOn(window, 'addEventListener')
    const remove = vi.spyOn(window, 'removeEventListener')

    const w = mountMatrix(matrix())
    await flushPromises()

    const added = add.mock.calls.filter(([type]) => type === 'resize').map(([, handler]) => handler)
    expect(added.length).toBeGreaterThan(0)

    w.unmount()
    wrapper = null

    const removed = remove.mock.calls.filter(([type]) => type === 'resize').map(([, handler]) => handler)
    for (const handler of added) expect(removed).toContain(handler)
  })
})
