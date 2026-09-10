import { describe, expect, it } from 'vitest'
import type { WbsNode } from '../../api/wbsApi'
import { nodeToFormInput } from './wbsFormMapping'

/**
 * 결함 3: `WbsItemInput`에 `actualStartDate`/`actualEndDate`/`forecastEndDate`가 없어서, 이름만
 * 바꾸고 저장해도 이 세 값이 `null`로 지워졌다. `nodeToFormInput`은 `WbsForm.vue`가 편집 대상을
 * 폼에 담을 때 쓰는 매핑 그대로다 — 아무것도 고치지 않고 곧장 `emit('submit', {...form})`하면
 * 이 함수가 돌려준 값이 그대로 나가므로, 여기서 값이 보존되면 폼도 보존한다.
 */
function baseNode(overrides: Partial<WbsNode> = {}): WbsNode {
  return {
    id: 1,
    parentId: null,
    code: '1',
    level: 1,
    name: '개발',
    description: null,
    startDate: '2026-01-01',
    endDate: '2026-01-10',
    progress: 40,
    summary: false,
    nodeType: 'WORK_PACKAGE',
    executionMode: null,
    executionModeSummary: null,
    backlogSummary: null,
    weight: null,
    agileRatio: null,
    acceptanceStatus: null,
    computedProgress: 40,
    progressBasis: null,
    progressIncomplete: false,
    progressNote: null,
    acceptancePending: false,
    delayStatus: 'ON_TRACK',
    expectedProgress: 40,
    progressGap: 0,
    delayDays: 0,
    children: [],
    ...overrides,
  }
}

describe('nodeToFormInput', () => {
  it('실적 시작일·종료일·예상 종료일이 있으면 그대로 담는다', () => {
    const node = baseNode({
      actualStartDate: '2026-01-02',
      actualEndDate: '2026-01-09',
      forecastEndDate: '2026-01-12',
    })

    const input = nodeToFormInput(node)

    expect(input.actualStartDate).toBe('2026-01-02')
    expect(input.actualEndDate).toBe('2026-01-09')
    expect(input.forecastEndDate).toBe('2026-01-12')
  })

  it('노드가 그 값을 갖고 있지 않으면(오늘의 WBS 트리 응답처럼) null로 담는다 — undefined를 그대로 보내 서버가 헷갈리지 않게', () => {
    const node = baseNode()

    const input = nodeToFormInput(node)

    expect(input.actualStartDate).toBeNull()
    expect(input.actualEndDate).toBeNull()
    expect(input.forecastEndDate).toBeNull()
  })

  it('나머지 필드도 편집 없이 저장할 수 있도록 그대로 옮긴다', () => {
    const node = baseNode({ weight: 3, agileRatio: 60, acceptanceStatus: 'PENDING' })

    const input = nodeToFormInput(node)

    expect(input.name).toBe('개발')
    expect(input.startDate).toBe('2026-01-01')
    expect(input.endDate).toBe('2026-01-10')
    expect(input.progress).toBe(40)
    expect(input.weight).toBe(3)
    expect(input.agileRatio).toBe(60)
    expect(input.acceptanceStatus).toBe('PENDING')
    expect(input.nodeType).toBe('WORK_PACKAGE')
  })
})
