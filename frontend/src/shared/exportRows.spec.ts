import { describe, expect, it } from 'vitest'
import type { RaciMatrix } from '../api/raciApi'
import type { RaidItem } from '../api/raidApi'
import type { WbsNode } from '../api/wbsApi'
import { raciCsv, raciLegend, raidCsv, wbsCsv } from './exportRows'

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

/**
 * 커밋 히스토리(지시서 §5.3): `wbsCsv`/`raidCsv`는 트리·항목과 `referenceDate`를 따로 받으므로,
 * `ExportMenu`가 라이브 응답 대신 커밋 payload의 같은 필드(`wbs.nodes`+`wbs.referenceDate`,
 * `raid.items`+`raid.referenceDate`)를 넘기기만 하면 그대로 동작한다 — 커밋의 `asOf`가 이
 * `referenceDate` 자리로 그대로 흘러 들어간다는 것만 고정해 둔다. 함수 자체는 라이브/커밋을
 * 구분하지 않는다.
 */
function wbsNode(overrides: Partial<WbsNode> = {}): WbsNode {
  return {
    id: 1,
    parentId: null,
    code: '1',
    level: 1,
    name: '설계',
    description: null,
    endDate: '2026-03-10',
    summary: false,
    nodeType: 'WORK_PACKAGE',
    executionMode: null,
    executionModeSummary: null,
    backlogSummary: null,
    weight: null,
    agileRatio: null,
    acceptanceStatus: null,
    computedProgress: 50,
    progressBasis: 'MANUAL',
    progressIncomplete: false,
    progressNote: null,
    acceptancePending: false,
    children: [],
    delayStatus: 'ON_TRACK',
    expectedProgress: 50,
    progressGap: 0,
    delayDays: 0,
    progress: 50,
    startDate: '2026-03-01',
    responsible: [],
    responsibleInherited: [],
    tags: [],
    tagSummary: null,
    ...overrides,
  }
}

describe('wbsCsv', () => {
  it('기준일 머리글에 넘겨받은 referenceDate를 그대로 박는다 — 커밋의 asOf가 여기로 들어온다', () => {
    const table = wbsCsv([wbsNode()], '2026-03-01')
    expect(table.header).toContain('지연 상태 (기준일 2026-03-01)')
  })

  it('referenceDate가 없으면(빈 트리) 기준일을 적지 않는다', () => {
    const table = wbsCsv([], null)
    expect(table.header).toContain('지연 상태')
    expect(table.header).not.toContain('지연 상태 (기준일 2026-03-01)')
  })
})

function raidItem(overrides: Partial<RaidItem> = {}): RaidItem {
  return {
    id: 1,
    type: 'RISK',
    title: '일정 위험',
    description: null,
    status: 'OPEN',
    probability: null,
    impact: null,
    ownerMemberId: null,
    ownerName: null,
    links: [],
    dueDate: null,
    response: null,
    exposure: null,
    exposureLevel: null,
    overdue: false,
    overdueDays: 0,
    ...overrides,
  }
}

describe('raidCsv', () => {
  it('기준일 머리글에 넘겨받은 referenceDate를 그대로 박는다 — 커밋의 asOf가 여기로 들어온다', () => {
    const table = raidCsv([raidItem()], '2026-03-01')
    expect(table.header).toContain('기한 초과 (기준일 2026-03-01)')
  })
})
