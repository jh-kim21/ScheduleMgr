import { describe, expect, it } from 'vitest'
import {
  DEFAULT_PREFS,
  normalizePrefs,
  pinOffsets,
  visibleColumns,
  WBS_COLUMNS,
  type WbsColumnPrefs,
} from './wbsColumns'

describe('visibleColumns', () => {
  it('업무명은 hidden에 넣어도 표시된다 — 2-2를 고정한다', () => {
    const prefs: WbsColumnPrefs = { hidden: ['name', 'code'], pin: 'none' }

    const keys = visibleColumns(prefs).map((column) => column.key)

    expect(keys).toContain('name')
    expect(keys).not.toContain('code')
  })

  it('hidden이 비었으면 WBS_COLUMNS 순서 그대로 전부 보인다', () => {
    const keys = visibleColumns(DEFAULT_PREFS).map((column) => column.key)

    expect(keys).toEqual(WBS_COLUMNS.map((column) => column.key))
  })
})

describe('pinOffsets', () => {
  it("pin: 'none'이면 빈 객체다", () => {
    expect(pinOffsets({ hidden: [], pin: 'none' })).toEqual({})
  })

  it("pin: 'code'면 code만 0이다", () => {
    expect(pinOffsets({ hidden: [], pin: 'code' })).toEqual({ code: 0 })
  })

  it("pin: 'name'이면 code는 0, name은 code의 폭(5.5)만큼 밀린다", () => {
    expect(pinOffsets({ hidden: [], pin: 'name' })).toEqual({ code: 0, name: 5.5 })
  })

  it('code를 숨긴 채 pin: \'name\'이면 name의 오프셋은 0이다 — 숨긴 열이 오프셋을 밀면 안 된다', () => {
    expect(pinOffsets({ hidden: ['code'], pin: 'name' })).toEqual({ name: 0 })
  })
})

describe('WBS_COLUMNS 필터 종류', () => {
  it('지시서 3-1의 표와 같다 — 필터 행(wbsTreeFilter)이 이 값을 그대로 읽는다', () => {
    const kinds = Object.fromEntries(WBS_COLUMNS.map((column) => [column.key, column.filter]))
    expect(kinds).toEqual({
      code: 'text',
      name: 'text',
      startDate: 'none',
      endDate: 'none',
      mode: 'enum',
      backlog: 'enum',
      progress: 'enum',
      checkpoint: 'enum',
      owner: 'names',
      tags: 'tags',
    })
  })
})

describe('normalizePrefs', () => {
  it('null은 기본값으로 떨어진다', () => {
    expect(normalizePrefs(null)).toEqual(DEFAULT_PREFS)
  })

  it('문자열은 기본값으로 떨어진다 — localStorage 값은 신뢰할 수 없다', () => {
    expect(normalizePrefs('아무 문자열')).toEqual(DEFAULT_PREFS)
  })

  it('모르는 열 키는 걸러진다', () => {
    expect(normalizePrefs({ hidden: ['owner', 'notAColumn', 42], pin: 'none' })).toEqual({
      hidden: ['owner'],
      pin: 'none',
    })
  })

  it('잘못된 pin 값은 none으로 떨어진다', () => {
    expect(normalizePrefs({ hidden: [], pin: 'everything' })).toEqual({ hidden: [], pin: 'none' })
  })

  it('hidden이 배열이 아니면 빈 배열로 떨어진다', () => {
    expect(normalizePrefs({ hidden: 'owner', pin: 'code' })).toEqual({ hidden: [], pin: 'code' })
  })
})
