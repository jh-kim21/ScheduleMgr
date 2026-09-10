import { describe, expect, it } from 'vitest'
import { raciCsv } from './exportRows'
import type { RaciCell, RaciMatrix, RaciTask } from '../api/raciApi'
import type { ProjectMember } from '../api/memberApi'

/**
 * 결함 (설계서/발견한_결함.md "RACI·RAID" §4, CLAUDE.md "RACI 상속 설계상 알아둘 점"):
 *
 * `exportRows.ts`의 `raciCsv`가 `cell.roles`(자기 글자)만 읽고 `cell.inherited`(상위에서
 * 물려받은 글자)를 무시했다. 화면에서는 흐린 글씨로 보이는 상속된 A가 CSV에서는 빈칸이
 * 되어 "이 Work Package는 책임자가 없다"로 잘못 읽혔다.
 *
 * 이 테스트는 표기 형식(괄호인지 소문자인지)까지는 고정하지 않는다 — Developer2가 정할
 * 몫이다. 대신 계약만 고정한다:
 *   1. 상속된 글자가 있는 셀은 빈칸이 아니다.
 *   2. 상속 표기는 자기 글자만 있는 셀과 구분된다(같은 문자열이 아니다).
 *   3. 재정의(overridden)로 무효가 된 상속 글자는 CSV에 나타나지 않는다.
 */
describe('raciCsv — 상속된 글자', () => {
  const members: ProjectMember[] = [
    { id: 1, name: '박PM', email: null, position: null, createdAt: '', updatedAt: '' },
    { id: 2, name: '김개발', email: null, position: null, createdAt: '', updatedAt: '' },
  ]

  function task(id: number, code: string, name: string, summary: boolean): RaciTask {
    return { id, parentId: null, code, level: code.split('.').length, name, summary, storyAssignees: [] }
  }

  function cell(wbsItemId: number, memberId: number, partial: Partial<RaciCell> = {}): RaciCell {
    return { wbsItemId, memberId, roles: [], assignmentIds: [], inherited: [], ...partial }
  }

  it('상속만 있고 자기 글자가 없는 셀은 빈칸이 아니다', () => {
    const matrix: RaciMatrix = {
      members,
      tasks: [
        task(10, '1', '단계', true),
        task(11, '1.1', '개발', false),
      ],
      cells: [
        // 단계(10)에 박PM이 직접 A를 배정
        cell(10, 1, { roles: ['ACCOUNTABLE'], assignmentIds: [901] }),
        // 개발(11)은 자기 글자는 없고, 단계에서 A를 물려받는다 — 재정의 아님(overridden: false)
        cell(11, 1, {
          inherited: [
            { role: 'ACCOUNTABLE', source: 'INHERITED', sourceItemId: 10, sourceCode: '1', overridden: false },
          ],
        }),
      ],
      issues: [],
    }

    const table = raciCsv(matrix)
    const ownRow = table.rows.find((row) => row[0] === '1')!
    const inheritedRow = table.rows.find((row) => row[0] === '1.1')!

    // 열 순서: WBS, 업무명, 구분, 이후 members 순.
    const ownCell = ownRow[3]
    const inheritedCell = inheritedRow[3]

    expect(inheritedCell).not.toBe('')
    expect(inheritedCell).not.toBeUndefined()
    // 자기 글자만 있는 셀("A")과 표기가 달라야 한다 — 같으면 상속인지 자기 것인지 구분할 수 없다.
    expect(inheritedCell).not.toBe(ownCell)
  })

  it('재정의로 무효가 된 상속 글자는 CSV에 나타나지 않는다', () => {
    const matrix: RaciMatrix = {
      members,
      tasks: [
        task(10, '1', '단계', true),
        task(12, '1.2', '설계', false),
      ],
      cells: [
        cell(10, 1, { roles: ['ACCOUNTABLE'], assignmentIds: [901] }),
        // 설계(12)가 같은 역할을 김개발에게 다시 배정 — 박PM 쪽 상속은 재정의로 무효.
        cell(12, 1, {
          inherited: [
            { role: 'ACCOUNTABLE', source: 'INHERITED', sourceItemId: 10, sourceCode: '1', overridden: true },
          ],
        }),
        cell(12, 2, { roles: ['ACCOUNTABLE'], assignmentIds: [902] }),
      ],
      issues: [],
    }

    const table = raciCsv(matrix)
    const row = table.rows.find((r) => r[0] === '1.2')!

    const overriddenCell = row[3] // 박PM 열 — 무효가 된 상속만 있다.
    const ownCell = row[4] // 김개발 열 — 실제로 배정받은 사람.

    expect(overriddenCell).toBe('')
    expect(ownCell).not.toBe('')
  })
})
