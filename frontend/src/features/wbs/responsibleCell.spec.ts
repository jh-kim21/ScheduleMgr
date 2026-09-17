import { describe, expect, it } from 'vitest'
import type { WbsNode } from '../../api/wbsApi'
import { responsibleNames, responsibleTitle } from './responsibleCell'

type Source = Pick<WbsNode, 'responsible' | 'responsibleInherited'>

function source(overrides: Partial<Source> = {}): Source {
  return { responsible: [], responsibleInherited: [], ...overrides }
}

describe('responsibleNames', () => {
  it('자기 담당자가 먼저, 물려받은 담당자가 뒤다 — 이 행이 선언한 사람이 먼저 읽혀야 한다', () => {
    const names = responsibleNames(
      source({
        responsible: [{ memberId: 2, name: '이승하' }],
        responsibleInherited: [{ memberId: 1, name: '김재학' }],
      }),
    )

    expect(names).toEqual([
      { memberId: 2, name: '이승하', inherited: false },
      { memberId: 1, name: '김재학', inherited: true },
    ])
  })

  it('물려받은 담당자를 자기 담당자와 구분해 싣는다 — 이 행에서는 지울 수 없는 값이다', () => {
    const names = responsibleNames(
      source({ responsibleInherited: [{ memberId: 1, name: '김재학' }] }),
    )

    expect(names).toEqual([{ memberId: 1, name: '김재학', inherited: true }])
  })

  it('아무도 없으면 빈 배열이다 — 화면은 이때 "-"를 적는다', () => {
    expect(responsibleNames(source())).toEqual([])
  })

  it('한 사람이 양쪽에 들어와도 한 번만 그린다 — 계약상 겹치지 않지만, 겹치면 화면에 이름이 두 번 찍힌다', () => {
    const names = responsibleNames(
      source({
        responsible: [{ memberId: 1, name: '김재학' }],
        responsibleInherited: [{ memberId: 1, name: '김재학' }],
      }),
    )

    expect(names).toEqual([{ memberId: 1, name: '김재학', inherited: false }])
  })

  it('필드가 아예 없는 payload도 읽는다 — Phase C 이전에 찍은 커밋에는 이 필드가 없다', () => {
    expect(responsibleNames({} as Source)).toEqual([])
  })
})

describe('responsibleTitle', () => {
  it('잘린 목록 전체를 담고, 물려받은 이름에 꼬리표를 붙인다 — 툴팁에서는 흐린 글씨 구분이 사라진다', () => {
    const names = responsibleNames(
      source({
        responsible: [{ memberId: 2, name: '이승하' }],
        responsibleInherited: [{ memberId: 1, name: '김재학' }],
      }),
    )

    expect(responsibleTitle(names)).toBe(
      '담당자 이승하, 김재학 (상위 단계에서 물려받음) — RACI 화면에서 바꿉니다',
    )
  })

  it('아무도 없으면 null이라 title 속성 자체가 붙지 않는다', () => {
    expect(responsibleTitle([])).toBeNull()
  })
})
