import { describe, expect, it } from 'vitest'
import type { WbsNode } from '../../api/wbsApi'
import {
  FIRST_ROW_HINT,
  LAST_ROW_HINT,
  NO_PARENT_HINT,
  NO_PREVIOUS_SIBLING_HINT,
  WORK_PACKAGE_INDENT_HINT,
  indentInput,
  moveAvailability,
  moveDownInput,
  moveState,
  moveUpInput,
  outdentInput,
} from './wbsMove'
import { NO_SELECTION_HINT, READONLY_HINT } from './wbsToolbar'

function node(
  id: number,
  code: string,
  level: number,
  children: WbsNode[] = [],
  nodeType?: WbsNode['nodeType'],
): WbsNode {
  return {
    id,
    parentId: null,
    code,
    level,
    name: `업무 ${id}`,
    description: null,
    startDate: null,
    endDate: null,
    progress: 0,
    summary: children.length > 0,
    nodeType: nodeType ?? (children.length > 0 ? 'SUMMARY' : 'WORK_PACKAGE'),
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
    tagSummary: children.length > 0 ? [] : null,
    children,
  }
}

// 1(SUMMARY, 자식 2) · 3(WORK_PACKAGE) · 4(WORK_PACKAGE) — 최상위 세 형제.
// id=3의 바로 위 형제(id=1)는 SUMMARY라 들여쓰기 가능, id=4의 바로 위 형제(id=3)는
// WORK_PACKAGE라 들여쓰기 불가 — 두 갈래를 한 트리에서 확인한다.
const child = node(2, '1.1', 2)
const tree = [node(1, '1', 1, [child], 'SUMMARY'), node(3, '2', 1), node(4, '3', 1)]

describe('moveAvailability', () => {
  it('맨 위 형제는 위로·들여쓰기·내어쓰기가 모두 불가능하다', () => {
    expect(moveAvailability(tree, 1)).toEqual({ up: false, down: true, indent: false, outdent: false })
  })

  it('가운데 형제는 위/아래가 가능하고, 바로 위가 SUMMARY면 들여쓰기도 가능하다', () => {
    expect(moveAvailability(tree, 3)).toEqual({ up: true, down: true, indent: true, outdent: false })
  })

  it('바로 위 형제가 WORK_PACKAGE면 들여쓰기가 불가능하다', () => {
    expect(moveAvailability(tree, 4)).toEqual({ up: true, down: false, indent: false, outdent: false })
  })

  it('부모가 있는 항목은 내어쓰기가 가능하고, 형제가 자기 하나뿐이면 위/아래는 불가능하다', () => {
    expect(moveAvailability(tree, 2)).toEqual({ up: false, down: false, indent: false, outdent: true })
  })

  it('트리에 없는 id는 네 값 모두 false다', () => {
    expect(moveAvailability(tree, 999)).toEqual({ up: false, down: false, indent: false, outdent: false })
  })
})

describe('moveState', () => {
  it('readOnly면 선택과 무관하게 모두 비활성이고 title이 READONLY_HINT다', () => {
    const state = moveState(tree, 3, true)
    expect(state).toEqual({
      up: false,
      down: false,
      indent: false,
      outdent: false,
      upHint: READONLY_HINT,
      downHint: READONLY_HINT,
      indentHint: READONLY_HINT,
      outdentHint: READONLY_HINT,
    })
  })

  it('선택이 없으면 모두 비활성이고 title이 NO_SELECTION_HINT다', () => {
    const state = moveState(tree, null, false)
    expect(state.up).toBe(false)
    expect(state.upHint).toBe(NO_SELECTION_HINT)
  })

  it('가능한 동작은 title이 null이다', () => {
    const state = moveState(tree, 3, false)
    expect(state.up).toBe(true)
    expect(state.upHint).toBeNull()
    expect(state.indent).toBe(true)
    expect(state.indentHint).toBeNull()
  })

  it('맨 위 행은 up이 불가능하고 이유가 FIRST_ROW_HINT다', () => {
    expect(moveState(tree, 1, false).upHint).toBe(FIRST_ROW_HINT)
  })

  it('맨 아래 행은 down이 불가능하고 이유가 LAST_ROW_HINT다', () => {
    expect(moveState(tree, 4, false).downHint).toBe(LAST_ROW_HINT)
  })

  it('바로 위 형제가 없으면 이유가 NO_PREVIOUS_SIBLING_HINT다', () => {
    expect(moveState(tree, 1, false).indentHint).toBe(NO_PREVIOUS_SIBLING_HINT)
  })

  it('바로 위 형제가 Work Package면 이유가 WORK_PACKAGE_INDENT_HINT다', () => {
    expect(moveState(tree, 4, false).indentHint).toBe(WORK_PACKAGE_INDENT_HINT)
  })

  it('최상위 항목은 outdent가 불가능하고 이유가 NO_PARENT_HINT다', () => {
    expect(moveState(tree, 3, false).outdentHint).toBe(NO_PARENT_HINT)
  })
})

describe('moveUpInput / moveDownInput', () => {
  it('가운데 형제를 위로 옮기면 그 앞자리로 간다', () => {
    // [1,3,4]에서 3을 위로 -> [3,1,4]
    expect(moveUpInput(tree, 3)).toEqual({ parentId: null, position: 0 })
  })

  it('맨 위 형제는 위로 옮길 수 없다', () => {
    expect(moveUpInput(tree, 1)).toBeNull()
  })

  it('가운데 형제를 아래로 옮기면 그 다음 자리로 간다', () => {
    // [1,3,4]에서 3을 아래로 -> [1,4,3] (제거 보정으로 position 2)
    expect(moveDownInput(tree, 3)).toEqual({ parentId: null, position: 2 })
  })

  it('맨 아래 형제는 아래로 옮길 수 없다', () => {
    expect(moveDownInput(tree, 4)).toBeNull()
  })

  it('하위 항목의 위/아래는 형제 목록(부모의 children) 기준이다', () => {
    // child(2)는 부모(1)의 유일한 자식이라 위/아래 모두 불가능하다.
    expect(moveUpInput(tree, 2)).toBeNull()
    expect(moveDownInput(tree, 2)).toBeNull()
  })
})

describe('indentInput', () => {
  it('바로 위 형제가 SUMMARY면 그 형제의 마지막 자식이 된다', () => {
    // id=1의 기존 자식은 [child(2)] 하나뿐이라 새 자식은 위치 1(끝)에 붙는다.
    expect(indentInput(tree, 3)).toEqual({ parentId: 1, position: 1 })
  })

  it('바로 위 형제가 Work Package면 null이다', () => {
    expect(indentInput(tree, 4)).toBeNull()
  })

  it('맨 위 형제는 들여쓸 대상이 없어 null이다', () => {
    expect(indentInput(tree, 1)).toBeNull()
  })
})

describe('outdentInput', () => {
  it('부모의 다음 형제가 된다', () => {
    // child(2)의 부모(1)는 최상위 목록 [1,3,4]의 0번 — 내어쓰면 parentId:null, position:1(1 바로 뒤).
    expect(outdentInput(tree, 2)).toEqual({ parentId: null, position: 1 })
  })

  it('이미 최상위 항목은 null이다', () => {
    expect(outdentInput(tree, 3)).toBeNull()
  })

  it('손자를 내어쓰면 조부모가 아니라 부모의 다음 형제가 된다', () => {
    // grandchild(5)는 child(2)의 자식 — 내어쓰면 child(2)의 부모(1)의 자식 목록에서 child 바로 뒤.
    const grandchild = node(5, '1.1.1', 3)
    const deepChild = node(2, '1.1', 2, [grandchild])
    const deepTree = [node(1, '1', 1, [deepChild], 'SUMMARY'), node(3, '2', 1)]
    expect(outdentInput(deepTree, 5)).toEqual({ parentId: 1, position: 1 })
  })
})
