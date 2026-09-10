import { describe, expect, it } from 'vitest'
import type { RaciMatrix } from '../api/raciApi'
import { raciCsv, raciLegend } from './exportRows'

/**
 * 결함 4: 화면은 상속된 A를 옅은 글자로 보여 주는데, CSV는 `cell.roles`만 읽어 빈 칸으로
 * 내보냈다 — Excel에서 "책임자 없음"처럼 읽혔다. 자기 글자와 상속 글자를 구분해 함께 담고,
 * 재정의(취소선)로 무효가 된 상속은 화면과 같은 이유로 뺀다.
 */
describe('raciCsv', () => {
  function matrix(overrides: Partial<RaciMatrix> = {}): RaciMatrix {
    return {
      members: [{ id: 1, name: '홍길동', email: null, position: null, createdAt: '', updatedAt: '' }],
      tasks: [{ id: 10, parentId: null, code: '1', level: 1, name: '설계', summary: false, storyAssignees: [] }],
      cells: [],
      issues: [],
      ...overrides,
    }
  }

  it('자기 글자만 있으면 그대로 적는다', () => {
    const table = raciCsv(
      matrix({
        cells: [{ wbsItemId: 10, memberId: 1, roles: ['RESPONSIBLE', 'ACCOUNTABLE'], assignmentIds: [1, 2], inherited: [] }],
      }),
    )
    expect(table.rows).toEqual([['1', '설계', 'Leaf', 'RA']])
  })

  it('상속된 글자를 괄호로 함께 적는다', () => {
    const table = raciCsv(
      matrix({
        cells: [
          {
            wbsItemId: 10,
            memberId: 1,
            roles: ['RESPONSIBLE'],
            assignmentIds: [1],
            inherited: [
              { role: 'ACCOUNTABLE', source: 'INHERITED', sourceItemId: 1, sourceCode: '1', overridden: false },
            ],
          },
        ],
      }),
    )
    expect(table.rows).toEqual([['1', '설계', 'Leaf', 'R(A)']])
  })

  it('자기 글자 없이 상속만 있어도 괄호로 적는다', () => {
    const table = raciCsv(
      matrix({
        cells: [
          {
            wbsItemId: 10,
            memberId: 1,
            roles: [],
            assignmentIds: [],
            inherited: [
              { role: 'ACCOUNTABLE', source: 'INHERITED', sourceItemId: 1, sourceCode: '1', overridden: false },
            ],
          },
        ],
      }),
    )
    expect(table.rows).toEqual([['1', '설계', 'Leaf', '(A)']])
  })

  it('재정의로 무효가 된 상속은 넣지 않는다 — 화면의 취소선과 같은 이유', () => {
    const table = raciCsv(
      matrix({
        cells: [
          {
            wbsItemId: 10,
            memberId: 1,
            roles: [],
            assignmentIds: [],
            inherited: [
              { role: 'ACCOUNTABLE', source: 'INHERITED', sourceItemId: 1, sourceCode: '1', overridden: true },
            ],
          },
        ],
      }),
    )
    expect(table.rows).toEqual([['1', '설계', 'Leaf', '']])
  })

  it('셀 자체가 없으면 빈 칸이다', () => {
    const table = raciCsv(matrix())
    expect(table.rows).toEqual([['1', '설계', 'Leaf', '']])
  })
})

describe('raciLegend', () => {
  it('R/A/C/I 뜻과 괄호 표기 설명을 함께 담는다', () => {
    const legend = raciLegend()
    expect(legend).toContain('R=RESPONSIBLE')
    expect(legend).toContain('상속')
  })
})
