// @vitest-environment happy-dom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

/**
 * `columnPrefs`는 모듈 스코프 `ref`라 모듈이 로드되는 순간 `localStorage`를 한 번 읽는다(`theme.ts`와
 * 같은 모양). 그래서 "저장소가 비어 있을 때/이미 값이 있을 때"를 각각 보려면 값을 넣어 두고 나서
 * `vi.resetModules()` + 동적 import로 매번 새로 로드해야 한다 — 정적 import로는 첫 테스트가 읽은
 * 모듈이 캐시되어 이후 테스트에 그대로 남는다.
 */
async function freshModule() {
  vi.resetModules()
  return await import('./wbsColumnPrefs')
}

const STORAGE_KEY = 'project-flow.wbs-columns'

describe('wbsColumnPrefs', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  afterEach(() => {
    vi.restoreAllMocks()
    localStorage.clear()
  })

  it('저장된 값이 없으면 기본값(hidden: [], pin: none)으로 뜬다', async () => {
    const { columnPrefs } = await freshModule()

    expect(columnPrefs.value).toEqual({ hidden: [], pin: 'none' })
  })

  it('저장된 값을 정규화해 되돌린다', async () => {
    localStorage.setItem(STORAGE_KEY, JSON.stringify({ hidden: ['owner'], pin: 'code' }))

    const { columnPrefs } = await freshModule()

    expect(columnPrefs.value).toEqual({ hidden: ['owner'], pin: 'code' })
  })

  it('setColumnPrefs가 ref를 갱신하고 localStorage에 쓴다', async () => {
    const { columnPrefs, setColumnPrefs } = await freshModule()

    setColumnPrefs({ hidden: ['tags'], pin: 'name' })

    expect(columnPrefs.value).toEqual({ hidden: ['tags'], pin: 'name' })
    expect(JSON.parse(localStorage.getItem(STORAGE_KEY) ?? 'null')).toEqual({
      hidden: ['tags'],
      pin: 'name',
    })
  })

  it('resetColumnPrefs가 ref를 기본값으로 되돌리고 저장소를 지운다', async () => {
    const { columnPrefs, setColumnPrefs, resetColumnPrefs } = await freshModule()
    setColumnPrefs({ hidden: ['tags'], pin: 'name' })

    resetColumnPrefs()

    expect(columnPrefs.value).toEqual({ hidden: [], pin: 'none' })
    expect(localStorage.getItem(STORAGE_KEY)).toBeNull()
  })

  it('localStorage.getItem이 던지면(사생활 보호 모드 등) 기본값으로 뜬다', async () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('blocked')
    })

    const { columnPrefs } = await freshModule()

    expect(columnPrefs.value).toEqual({ hidden: [], pin: 'none' })
  })

  it('localStorage.setItem이 던져도 ref는 갱신된다 — 저장 실패가 화면을 막지 않는다', async () => {
    const { columnPrefs, setColumnPrefs } = await freshModule()
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('blocked')
    })

    expect(() => setColumnPrefs({ hidden: ['tags'], pin: 'name' })).not.toThrow()
    expect(columnPrefs.value).toEqual({ hidden: ['tags'], pin: 'name' })
  })
})
