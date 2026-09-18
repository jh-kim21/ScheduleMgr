import { describe, expect, it } from 'vitest'
import type { WorkPackageProgress } from '../../api/progressApi'
import type { WbsNode } from '../../api/wbsApi'
import {
  emptyFilter,
  filterOptions,
  filterTree,
  hasActiveFilter,
  UNSPECIFIED_OWNER,
  type WbsFilterState,
} from './wbsTreeFilter'

/**
 * 지시서 `wbs-tree-filter` 3-2의 순수 함수 판정을 고정한다(`raidFilter.ts`와 같은 자리·같은
 * 이유). 컴포넌트 렌더링·상호작용은 tester의 `WbsTreeFilter.spec.ts`가 덮는다.
 */

function node(overrides: Partial<WbsNode> = {}): WbsNode {
  return {
    id: 1,
    parentId: null,
    code: '1',
    level: 1,
    name: '요구사항 정의',
    description: null,
    startDate: '2026-03-01',
    endDate: '2026-03-31',
    progress: 20,
    summary: false,
    nodeType: 'WORK_PACKAGE',
    executionMode: null,
    executionModeSummary: null,
    backlogSummary: null,
    weight: null,
    agileRatio: null,
    acceptanceStatus: null,
    computedProgress: 20,
    progressBasis: 'MANUAL',
    progressIncomplete: false,
    progressNote: null,
    acceptancePending: false,
    delayStatus: 'ON_TRACK',
    expectedProgress: 20,
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

function filter(overrides: Partial<WbsFilterState> = {}): WbsFilterState {
  return { ...emptyFilter(), ...overrides }
}

describe('hasActiveFilter', () => {
  it('빈 필터는 false다', () => {
    expect(hasActiveFilter(emptyFilter())).toBe(false)
  })

  it('공백만 있는 text 조건은 조건 없음으로 본다', () => {
    expect(hasActiveFilter(filter({ code: '   ' }))).toBe(false)
  })
})

describe('filterTree', () => {
  it('조건이 하나도 없으면 원본 배열과 같은 참조를 돌려준다', () => {
    const nodes = [node()]
    const result = filterTree(nodes, emptyFilter())
    expect(result.nodes).toBe(nodes)
    expect(result.matchIds.size).toBe(0)
  })

  it('손자 하나가 일치하면 부모·조부모가 남고 matchIds에는 손자만 있다', () => {
    const grandchild = node({ id: 3, parentId: 2, code: '1.1.1', name: '화면 설계 - 목록' })
    const child = node({ id: 2, parentId: 1, code: '1.1', name: '화면 설계', children: [grandchild] })
    const root = node({ id: 1, code: '1', name: '요구사항 정의', children: [child] })

    const result = filterTree([root], filter({ name: '목록' }))

    expect(result.nodes).toHaveLength(1)
    expect(result.nodes[0].id).toBe(1)
    expect(result.nodes[0].children).toHaveLength(1)
    expect(result.nodes[0].children[0].id).toBe(2)
    expect(result.nodes[0].children[0].children).toHaveLength(1)
    expect(result.nodes[0].children[0].children[0].id).toBe(3)
    expect(result.matchIds).toEqual(new Set([3]))
  })

  it('일치한 Summary의 하위는 남지 않는다', () => {
    const child = node({ id: 2, parentId: 1, code: '1.1', name: '화면 설계' })
    const root = node({
      id: 1,
      code: '1',
      name: '설계 단계',
      nodeType: 'SUMMARY',
      children: [child],
    })

    const result = filterTree([root], filter({ name: '설계 단계' }))

    expect(result.nodes).toHaveLength(1)
    expect(result.nodes[0].id).toBe(1)
    expect(result.nodes[0].children).toHaveLength(0)
    expect(result.matchIds).toEqual(new Set([1]))
  })

  it('두 열의 조건이 AND로 걸린다', () => {
    const a = node({ id: 1, code: '1', name: '화면 설계', executionMode: 'WATERFALL' })
    const b = node({ id: 2, code: '2', name: '화면 설계', executionMode: 'AGILE' })

    const result = filterTree([a, b], filter({ name: '설계', mode: ['WATERFALL'] }))

    expect(result.matchIds).toEqual(new Set([1]))
  })

  it('한 열의 두 값이 OR로 걸린다', () => {
    const a = node({ id: 1, code: '1', executionMode: 'WATERFALL' })
    const b = node({ id: 2, code: '2', executionMode: 'AGILE' })
    const c = node({ id: 3, code: '3', executionMode: 'HYBRID' })

    const result = filterTree([a, b, c], filter({ mode: ['WATERFALL', 'AGILE'] }))

    expect(result.matchIds).toEqual(new Set([1, 2]))
  })

  it('업무명 필터가 대소문자를 무시하고 description도 본다', () => {
    const a = node({ id: 1, name: 'Screen Design' })
    const b = node({ id: 2, name: '화면 설계', description: 'screen mockup 포함' })
    const c = node({ id: 3, name: '무관한 업무' })

    const result = filterTree([a, b, c], filter({ name: 'SCREEN' }))

    expect(result.matchIds).toEqual(new Set([1, 2]))
  })

  it('WBS 코드 필터는 마디 단위로 경계를 정한다 — "1.2"는 "1.20"을 거른다', () => {
    const exact = node({ id: 1, code: '1.2' })
    const child = node({ id: 2, code: '1.2.1' })
    const sibling = node({ id: 3, code: '1.20' })
    const other = node({ id: 4, code: '11.2' })

    const result = filterTree([exact, child, sibling, other], filter({ code: '1.2' }))

    expect(result.matchIds).toEqual(new Set([1, 2]))
  })

  it('실행 방식 필터에서 보관값을 가진 Summary는 일치하지 않는다', () => {
    const summary = node({
      id: 1,
      nodeType: 'SUMMARY',
      executionMode: 'WATERFALL',
      children: [node({ id: 2, parentId: 1, code: '1.1' })],
    })

    const result = filterTree([summary], filter({ mode: ['WATERFALL'] }))

    expect(result.matchIds.size).toBe(0)
    expect(result.nodes).toHaveLength(0)
  })

  it('분야 필터에서 하위 요약(rolledUp)만 가진 Summary는 일치하지 않는다', () => {
    const tag = { id: 5, name: 'Web', color: null }
    const child = node({ id: 2, parentId: 1, code: '1.1', tags: [tag] })
    const summary = node({ id: 1, nodeType: 'SUMMARY', tagSummary: [tag], children: [child] })

    const result = filterTree([summary], filter({ tagIds: [5] }))

    // Summary 자신은 rolledUp만 있으므로 불일치, 자기 태그를 가진 child만 일치한다.
    expect(result.matchIds).toEqual(new Set([2]))
  })

  it('담당자 필터에서 상속받은 담당자도 일치한다', () => {
    const inherited = node({
      id: 1,
      responsibleInherited: [{ memberId: 9, name: '김재학' }],
    })

    const result = filterTree([inherited], filter({ owners: ['김재학'] }))

    expect(result.matchIds).toEqual(new Set([1]))
  })

  it('담당자 필터의 "미지정"은 자기·상속 배정이 모두 없는 행에만 일치한다', () => {
    const unassigned = node({ id: 1 })
    const assigned = node({ id: 2, responsible: [{ memberId: 1, name: '이승하' }] })

    const result = filterTree([unassigned, assigned], filter({ owners: [UNSPECIFIED_OWNER] }))

    expect(result.matchIds).toEqual(new Set([1]))
  })

  it('연결 Backlog 필터 — 있음/없음', () => {
    const linked = node({ id: 1, backlogSummary: { items: 2, done: 1, archived: 0 } })
    const unlinked = node({ id: 2, backlogSummary: null })

    expect(filterTree([linked, unlinked], filter({ backlog: 'LINKED' })).matchIds).toEqual(
      new Set([1]),
    )
    expect(filterTree([linked, unlinked], filter({ backlog: 'UNLINKED' })).matchIds).toEqual(
      new Set([2]),
    )
  })

  it('진척 필터 — 산정 전·0%·1~99%·100%·불완전·인수 대기', () => {
    const notEstimable = node({ id: 1, computedProgress: null })
    const zero = node({ id: 2, computedProgress: 0 })
    const partial = node({ id: 3, computedProgress: 42 })
    const full = node({ id: 4, computedProgress: 100 })
    const incomplete = node({ id: 5, computedProgress: 60, progressIncomplete: true })
    const pending = node({ id: 6, computedProgress: 100, acceptancePending: true })
    const all = [notEstimable, zero, partial, full, incomplete, pending]

    expect(filterTree(all, filter({ progress: ['NOT_ESTIMABLE'] })).matchIds).toEqual(new Set([1]))
    expect(filterTree(all, filter({ progress: ['ZERO'] })).matchIds).toEqual(new Set([2]))
    // 60%는 부분 진행이면서 동시에 불완전이다 — 두 조건 다 그 행을 잡는다.
    expect(filterTree(all, filter({ progress: ['PARTIAL'] })).matchIds).toEqual(new Set([3, 5]))
    expect(filterTree(all, filter({ progress: ['FULL'] })).matchIds).toEqual(new Set([4, 6]))
    expect(filterTree(all, filter({ progress: ['INCOMPLETE'] })).matchIds).toEqual(new Set([5]))
    expect(filterTree(all, filter({ progress: ['ACCEPTANCE_PENDING'] })).matchIds).toEqual(
      new Set([6]),
    )
  })

  it('체크포인트 필터는 workPackages 맵으로 판정한다', () => {
    const none = node({ id: 1 })
    const partial = node({ id: 2 })
    const approved = node({ id: 3 })
    const workPackages: Record<number, WorkPackageProgress> = {
      2: { checkpointTotal: 3, checkpointApproved: 1 } as WorkPackageProgress,
      3: { checkpointTotal: 2, checkpointApproved: 2 } as WorkPackageProgress,
    }

    const withData = filterTree(
      [none, partial, approved],
      filter({ checkpoint: ['PARTIAL'] }),
      workPackages,
    )
    expect(withData.matchIds).toEqual(new Set([2]))

    // 진짜로 빈 맵(예: Work Package가 없는 프로젝트)을 넘기면 각 행이 정상적으로 "없음"으로
    // 판정된다 — undefined(아래 테스트)와는 다른 경우다.
    const withEmptyMap = filterTree([none, partial, approved], filter({ checkpoint: ['NONE'] }), {})
    expect(withEmptyMap.matchIds).toEqual(new Set([1, 2, 3]))
  })

  it('workPackages를 아예 넘기지 않으면 체크포인트 조건은 no-op이다 — 전부 통과한다', () => {
    // 팀 리드 결정: 지시서 3-2의 2-인자 시그니처를 모르는 호출자가 있을 수 있으므로, "없음"으로
    // 단정하지 않고(트리가 통째로 비어 이유를 알 수 없게 된다) 이 열의 조건만 무시한다.
    const all = [node({ id: 1 }), node({ id: 2 }), node({ id: 3 })]

    const none = filterTree(all, filter({ checkpoint: ['NONE'] }))
    const partial = filterTree(all, filter({ checkpoint: ['PARTIAL'] }))
    const approved = filterTree(all, filter({ checkpoint: ['ALL'] }))

    expect(none.matchIds).toEqual(new Set([1, 2, 3]))
    expect(partial.matchIds).toEqual(new Set([1, 2, 3]))
    expect(approved.matchIds).toEqual(new Set([1, 2, 3]))
  })
})

describe('filterOptions', () => {
  it('트리에 실려 온 담당자·분야만 후보로 뽑고, 이름은 정렬한다', () => {
    const web = { id: 2, name: 'Web', color: null }
    const service = { id: 1, name: 'Service', color: null }
    const tree = [
      node({
        id: 1,
        responsible: [{ memberId: 1, name: '이승하' }],
        tags: [web],
      }),
      node({
        id: 2,
        responsibleInherited: [{ memberId: 2, name: '김재학' }],
        tags: [service],
      }),
      node({ id: 3 }),
    ]

    const options = filterOptions(tree)

    expect(options.owners).toEqual(['김재학', '이승하', UNSPECIFIED_OWNER])
    expect(options.tags).toEqual([
      { id: 1, name: 'Service' },
      { id: 2, name: 'Web' },
    ])
  })

  it('아무도 미배정이 아니면 "미지정"을 후보에 넣지 않는다', () => {
    const tree = [node({ id: 1, responsible: [{ memberId: 1, name: '이승하' }] })]

    expect(filterOptions(tree).owners).toEqual(['이승하'])
  })
})
