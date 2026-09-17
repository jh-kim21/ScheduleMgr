import { describe, expect, it } from 'vitest'
import type { TagRef, WbsNode } from '../../api/wbsApi'
import { tagCell } from './tagCell'

type Source = Pick<WbsNode, 'tags' | 'tagSummary' | 'children'>

const SERVICE: TagRef = { id: 1, name: 'Service', color: null }
const WEB: TagRef = { id: 2, name: 'Web', color: 'blue' }

function source(overrides: Partial<Source> = {}): Source {
  return { tags: [], tagSummary: null, children: [], ...overrides }
}

describe('tagCell', () => {
  it('자식이 없으면 자기 태그를 그대로 그린다', () => {
    expect(tagCell(source({ tags: [SERVICE, WEB] }))).toEqual({
      chips: [SERVICE, WEB],
      rolledUp: false,
      retained: [],
    })
  })

  it('자식이 있으면 하위 요약을 그리고 흐리게 표시한다 — 이 행 자신의 분야가 아니다', () => {
    const cell = tagCell(
      source({ tagSummary: [SERVICE], children: [{} as WbsNode] }),
    )

    expect(cell.chips).toEqual([SERVICE])
    expect(cell.rolledUp).toBe(true)
  })

  it('전환 전부터 들고 있던 자기 태그는 요약에 섞지 않고 "보관"으로만 알린다 — 실행 방식과 같다', () => {
    const cell = tagCell(
      source({ tags: [WEB], tagSummary: [SERVICE], children: [{} as WbsNode] }),
    )

    expect(cell.chips).toEqual([SERVICE])
    expect(cell.retained).toEqual([WEB])
  })

  it('자식이 있는데 하위에 태그가 하나도 없으면 빈 목록이다 — 화면은 "-"를 적는다', () => {
    expect(tagCell(source({ tagSummary: [], children: [{} as WbsNode] })).chips).toEqual([])
  })

  it('자식이 있는데 tagSummary가 null이면(구형 payload) 빈 목록으로 읽는다', () => {
    expect(tagCell(source({ tagSummary: null, children: [{} as WbsNode] })).chips).toEqual([])
  })

  it('자식 유무로 가른다 — Summary로 전환했지만 아직 자식이 없으면 자기 태그를 그린다', () => {
    // 서버가 tagSummary 를 채우는 조건이 "자식이 있는가"라서, 화면이 nodeType 으로 물으면 이
    // 과도 상태에서 요약 자리에 null 을 그리게 된다(실행 방식 열과 같은 판정을 쓴다).
    expect(tagCell(source({ tags: [WEB], tagSummary: null, children: [] })).chips).toEqual([WEB])
  })

  it('필드가 아예 없는 payload도 읽는다 — Phase D 이전에 찍은 커밋에는 이 필드가 없다', () => {
    expect(tagCell({ children: [] } as unknown as Source)).toEqual({
      chips: [],
      rolledUp: false,
      retained: [],
    })
  })
})
