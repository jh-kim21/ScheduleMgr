import { describe, expect, it } from 'vitest'
import type { WbsNode } from '../../api/wbsApi'
import { containsDescendant, flattenTree, resolveDropPosition } from './wbsTree'

function node(id: number, code: string, level: number, children: WbsNode[] = []): WbsNode {
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
    delayStatus: 'UNSCHEDULED',
    expectedProgress: 0,
    progressGap: 0,
    delayDays: 0,
    children,
  }
}

describe('flattenTree', () => {
  const tree = [
    node(1, '1', 1, [node(2, '1.1', 2, [node(3, '1.1.1', 3)])]),
    node(4, '2', 1),
  ]

  it('깊이 우선 순서로 모든 행을 펼친다', () => {
    const rows = flattenTree(tree, new Set())

    expect(rows.map((row) => row.node.code)).toEqual(['1', '1.1', '1.1.1', '2'])
  })

  it('접힌 노드의 하위는 건너뛴다', () => {
    const rows = flattenTree(tree, new Set([2]))

    expect(rows.map((row) => row.node.code)).toEqual(['1', '1.1', '2'])
  })

  it('각 행에 형제 목록과 인덱스를 함께 담는다', () => {
    const rows = flattenTree(tree, new Set())

    const root2 = rows.find((row) => row.node.id === 4)!
    expect(root2.index).toBe(1)
    expect(root2.siblings).toHaveLength(2)

    const child = rows.find((row) => row.node.id === 2)!
    expect(child.index).toBe(0)
    expect(child.siblings).toHaveLength(1)
  })
})

describe('resolveDropPosition', () => {
  // 서버는 드래그한 항목을 형제 목록에서 빼고 position에 다시 끼워 넣는다.
  const siblings = [node(10, '1', 1), node(11, '2', 1), node(12, '3', 1)]

  it('다른 부모에서 온 항목은 인덱스를 그대로 사용한다', () => {
    expect(resolveDropPosition(99, siblings, 1, 'before')).toBe(1)
    expect(resolveDropPosition(99, siblings, 1, 'after')).toBe(2)
  })

  it('같은 부모 안에서 뒤로 이동하면 제거된 자리만큼 보정한다', () => {
    // [A,B,C]에서 A를 C 뒤로 -> 제거 후 [B,C]의 인덱스 2 -> [B,C,A]
    expect(resolveDropPosition(10, siblings, 2, 'after')).toBe(2)
    // A를 C 앞으로 -> [B,A,C]
    expect(resolveDropPosition(10, siblings, 2, 'before')).toBe(1)
  })

  it('같은 부모 안에서 앞으로 이동하면 보정하지 않는다', () => {
    // [A,B,C]에서 C를 A 앞으로 -> [C,A,B]
    expect(resolveDropPosition(12, siblings, 0, 'before')).toBe(0)
    // C를 A 뒤로 -> [A,C,B]
    expect(resolveDropPosition(12, siblings, 0, 'after')).toBe(1)
  })

  it('이미 마지막 자식인 항목을 같은 부모 끝으로 옮기면 마지막 인덱스가 된다', () => {
    expect(resolveDropPosition(12, siblings, siblings.length, 'before')).toBe(2)
  })
})

describe('containsDescendant', () => {
  const parent = node(1, '1', 1, [node(2, '1.1', 2, [node(3, '1.1.1', 3)])])

  it('직접 하위와 더 깊은 하위를 모두 찾는다', () => {
    expect(containsDescendant(parent, 2)).toBe(true)
    expect(containsDescendant(parent, 3)).toBe(true)
  })

  it('자기 자신이나 무관한 항목은 하위가 아니다', () => {
    expect(containsDescendant(parent, 1)).toBe(false)
    expect(containsDescendant(parent, 99)).toBe(false)
  })
})
