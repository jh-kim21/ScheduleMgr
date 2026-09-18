import { describe, expect, it } from 'vitest'
import type { WbsNode } from '../../api/wbsApi'
import {
  actionHint,
  NO_SELECTION_HINT,
  READONLY_HINT,
  toolbarState,
  WORK_PACKAGE_HINT,
} from './wbsToolbar'

function node(overrides: Partial<WbsNode> = {}): WbsNode {
  return {
    id: 1,
    parentId: null,
    code: '1',
    level: 1,
    name: '요구사항 정의',
    description: null,
    startDate: null,
    endDate: null,
    progress: 0,
    summary: false,
    nodeType: 'WORK_PACKAGE',
    executionMode: null,
    executionModeSummary: null,
    backlogSummary: null,
    weight: null,
    agileRatio: null,
    acceptanceStatus: null,
    computedProgress: 0,
    progressBasis: 'MANUAL',
    progressIncomplete: false,
    progressNote: null,
    acceptancePending: false,
    delayStatus: 'UNSCHEDULED',
    expectedProgress: 0,
    progressGap: 0,
    delayDays: 0,
    responsible: [],
    responsibleInherited: [],
    tags: [],
    tagSummary: null,
    children: [],
    ...overrides,
  }
}

describe('toolbarState', () => {
  it('선택이 없으면 세 버튼 모두 비활성이고, [하위 추가]의 이유는 선택 없음이다', () => {
    const state = toolbarState(null, false)

    expect(state).toEqual({
      canAddChild: false,
      canEdit: false,
      canRemove: false,
      addChildHint: NO_SELECTION_HINT,
    })
  })

  it('Summary를 고르면 세 버튼 모두 활성이다', () => {
    const summary = node({ nodeType: 'SUMMARY', children: [node({ id: 2 })] })
    const state = toolbarState(summary, false)

    expect(state).toEqual({
      canAddChild: true,
      canEdit: true,
      canRemove: true,
      addChildHint: null,
    })
  })

  it('Work Package를 고르면 [하위 추가]만 비활성이고 이유가 그 규칙을 말한다', () => {
    const workPackage = node({ nodeType: 'WORK_PACKAGE' })
    const state = toolbarState(workPackage, false)

    expect(state).toEqual({
      canAddChild: false,
      canEdit: true,
      canRemove: true,
      addChildHint: WORK_PACKAGE_HINT,
    })
  })

  it('readOnly면 선택이 있어도 세 버튼 모두 비활성이고 이유는 커밋 조회 중임을 말한다', () => {
    const state = toolbarState(node(), true)

    expect(state).toEqual({
      canAddChild: false,
      canEdit: false,
      canRemove: false,
      addChildHint: READONLY_HINT,
    })
  })
})

describe('actionHint', () => {
  it('readOnly가 선택 없음보다 우선한다', () => {
    expect(actionHint(null, true)).toBe(READONLY_HINT)
  })

  it('선택이 없으면 선택하라고 말한다', () => {
    expect(actionHint(null, false)).toBe(NO_SELECTION_HINT)
  })

  it('선택이 있고 readOnly가 아니면 null이다 — 누를 수 있으니 이유가 없다', () => {
    expect(actionHint(node(), false)).toBeNull()
  })
})
