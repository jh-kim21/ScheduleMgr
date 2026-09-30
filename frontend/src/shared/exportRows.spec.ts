import { describe, expect, it } from 'vitest'
import type { BacklogItem } from '../api/backlogApi'
import type { RaciMatrix } from '../api/raciApi'
import type { RaidItem } from '../api/raidApi'
import type { WbsNode } from '../api/wbsApi'
import { toCsv } from './csv'
import { backlogCsv, raciCsv, raciLegend, raidCsv, wbsCsv } from './exportRows'

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

  /**
   * CSV는 사람이 Excel에서 읽는 것이라 enum이 아니라 라벨을 쓴다(CLAUDE.md 내보내기 규칙).
   * 열 이름도 화면(WBS 폼·진척 탭)과 같은 `규모`여야 한다 — 같은 값을 두 이름으로 부르면
   * 파일을 받은 사람이 다른 값으로 읽는다.
   */
  describe('규모(WBS 형제 가중치)', () => {
    const sizeOf = (node: WbsNode) => {
      const table = wbsCsv([node], null)
      return table.rows[0][table.header.indexOf('규모')]
    }

    it('열 이름이 "규모"다 — 옛 이름 "가중치"는 남기지 않는다', () => {
      const table = wbsCsv([wbsNode()], null)
      expect(table.header).toContain('규모')
      expect(table.header).not.toContain('가중치')
    })

    it('미지정(null)은 빈 칸이 아니라 "미지정"이다 — 빈 칸이면 "값이 없다"와 "0"이 한 모양이 된다', () => {
      expect(sizeOf(wbsNode({ weight: null }))).toBe('미지정')
    })

    it('WBS 형제의 미입력을 "보통"이라 적지 않는다 — 그 몫은 상수가 아니라 형제의 선언값에서 나온다(지시서 §2-c). 체크포인트·Backlog와 같은 라벨 함수를 돌려쓰면 여기서 거짓이 된다', () => {
      expect(sizeOf(wbsNode({ weight: null }))).not.toBe('보통')
    })

    it('척도 위의 값은 등급 이름으로 적는다', () => {
      expect(sizeOf(wbsNode({ weight: 8 }))).toBe('아주 큼')
      expect(sizeOf(wbsNode({ weight: 3 }))).toBe('보통')
      expect(sizeOf(wbsNode({ weight: 1 }))).toBe('아주 작음')
    })

    it('0과 척도 밖 값은 숫자째로 드러낸다 — 0("집계 제외")과 미지정은 다른 값이다', () => {
      expect(sizeOf(wbsNode({ weight: 0 }))).toBe('집계 제외(0)')
      expect(sizeOf(wbsNode({ weight: 30 }))).toBe('사용자 지정 30')
    })
  })

  /**
   * 열을 하나 끼워 넣을 때 머리글과 행은 **따로** 고쳐야 하는 두 배열이라, 한쪽만 고치면 그
   * 지점부터 모든 값이 한 칸씩 밀린다 — 파일은 멀쩡해 보이고 Excel에서 열어야 드러난다.
   * `header.indexOf(...)`로 값을 집으면 그 어긋남이 여기서 바로 빨개진다(`규모`와 같은 방식).
   */
  describe('Action Item', () => {
    const actionItemOf = (node: WbsNode) => {
      const table = wbsCsv([node], null)
      return table.rows[0][table.header.indexOf('Action Item')]
    }

    it('머리글과 행의 칸 수가 같다 — 한쪽에만 열을 더하면 그 뒤가 통째로 밀린다', () => {
      const table = wbsCsv([wbsNode()], null)

      expect(table.header).toContain('Action Item')
      expect(table.rows[0]).toHaveLength(table.header.length)
    })

    it('주소가 있으면 그대로 적는다 — 링크는 사람이 Excel에서 눌러야 하므로 가공하지 않는다', () => {
      expect(actionItemOf(wbsNode({ actionItemUrl: 'https://example.com/tickets/1' })))
        .toBe('https://example.com/tickets/1')
    })

    /**
     * 빈 칸인지는 표(`CsvTable`)가 아니라 **글자로 만든 뒤** 봐야 한다 — 표에 남는 것은 `null`
     * 이나 `undefined` 그 자체이고, 그것을 빈 칸으로 바꾸는 것은 `toCsv`의 `toField`다. 표만
     * 보면 "빈 칸이 된다"가 아니라 "값이 없다"까지만 확인한 셈이 된다.
     */
    const cellText = (node: WbsNode) => {
      const value = actionItemOf(node)
      // 그 칸 하나만 다시 그린다 — 행 전체를 쉼표로 쪼개면 다른 칸에 쉼표가 하나 생기는 순간
      // 자리가 밀려 엉뚱한 값을 보게 된다(따옴표로 감싼 칸을 naive split이 못 읽는다).
      return toCsv([], [[value]]).split('\r\n')[1]
    }

    it('없으면(null) 빈 칸이 된다 — "null"이라 적지 않는다', () => {
      expect(cellText(wbsNode({ actionItemUrl: null }))).toBe('')
    })

    it('노드가 그 필드를 아예 갖고 있지 않아도(선택 필드) 빈 칸일 뿐 열은 그대로 남는다', () => {
      const table = wbsCsv([wbsNode()], null)

      expect(cellText(wbsNode())).toBe('')
      expect(table.rows[0]).toHaveLength(table.header.length)
    })
  })
})

function backlogItem(overrides: Partial<BacklogItem> = {}): BacklogItem {
  return {
    id: 1,
    wbsItemId: 10,
    wbsCode: '1.1',
    wbsName: '화면 설계',
    wbsExecutionMode: 'AGILE',
    parentId: null,
    parentTitle: null,
    depth: 0,
    itemType: 'STORY',
    title: 'WBS 계층 등록',
    description: null,
    priority: 'MEDIUM',
    status: 'TODO',
    assigneeMemberId: null,
    assigneeName: null,
    acceptanceCriteria: null,
    storyPoint: 5,
    progressWeight: null,
    archivedAt: null,
    archived: false,
    blocked: false,
    blockedReason: null,
    doneAt: null,
    openSprintName: null,
    aggregated: true,
    childCount: 0,
    unlinked: false,
    linkedToSummary: false,
    danglingLink: false,
    requiresExecutionModeChange: false,
    readyForSprint: true,
    ...overrides,
  }
}

describe('backlogCsv', () => {
  const weightOf = (item: BacklogItem) => {
    const table = backlogCsv([item])
    return table.rows[0][table.header.indexOf('진척 가중치')]
  }

  it('미입력(null)은 "보통"이다 — 서버가 실제로 그 값으로 계산하므로(weightOf) 표시와 계산이 어긋나지 않는다. WBS의 "미지정"과 다른 말인 것이 맞다', () => {
    expect(weightOf(backlogItem({ progressWeight: null }))).toBe('보통')
  })

  it('척도 위의 값은 등급 이름, 0과 척도 밖 값은 숫자째로 적는다', () => {
    expect(weightOf(backlogItem({ progressWeight: 5 }))).toBe('큼')
    expect(weightOf(backlogItem({ progressWeight: 0 }))).toBe('집계 제외(0)')
    expect(weightOf(backlogItem({ progressWeight: 30 }))).toBe('사용자 지정 30')
  })

  it('집계 대상이 아닌 유형(Epic·Task)은 가중치를 말하지 않는다 — 쓰이지 않는 값을 적으면 그 몫이 있는 것처럼 읽힌다', () => {
    expect(weightOf(backlogItem({ itemType: 'EPIC', aggregated: false, progressWeight: 5 }))).toBe('')
    expect(weightOf(backlogItem({ itemType: 'TASK', aggregated: false, progressWeight: null }))).toBe('')
  })

  it('Story Point는 가중치가 아니라 팀의 추정치라 숫자 그대로 나간다 — 같은 단위로 합산하지 말라고 못박은 별개 값이다', () => {
    const table = backlogCsv([backlogItem({ storyPoint: 5, progressWeight: 5 })])
    expect(table.rows[0][table.header.indexOf('Story Point')]).toBe(5)
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
