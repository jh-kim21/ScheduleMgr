import { afterEach, describe, expect, it, vi } from 'vitest'
import type { WbsNode, WbsTree } from '../../api/wbsApi'
import { wbsApi } from '../../api/wbsApi'
import { useWbs } from './useWbs'

/**
 * `useWbs`의 `tree`는 모듈 스코프 상태라(CLAUDE.md "화면 간 상태 유지") 이 파일의 테스트는 서로
 * 같은 인스턴스를 공유한다. 그래서 각 테스트가 스스로 `load()`를 불러 알고 있는 상태로 시작하고,
 * 뒤 테스트가 앞 테스트의 결과에 의존하지 않도록 짠다.
 */
vi.mock('../../api/wbsApi', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../api/wbsApi')>()
  return {
    ...actual,
    wbsApi: {
      ...actual.wbsApi,
      tree: vi.fn(),
    },
  }
})

const mockedTree = vi.mocked(wbsApi.tree)

function node(id: number): WbsNode {
  return {
    id,
    parentId: null,
    code: String(id),
    level: 1,
    name: `업무 ${id}`,
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
  }
}

function payload(nodes: WbsNode[], referenceDate = '2026-03-01'): WbsTree {
  return { referenceDate, nodes }
}

/** A promise this test can resolve on demand, to inspect `tree.value` while a fetch is in flight. */
function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((r) => {
    resolve = r
  })
  return { promise, resolve }
}

describe('useWbs — 프로젝트 전환 중 트리 표시', () => {
  afterEach(() => {
    mockedTree.mockReset()
  })

  /**
   * 회귀 재현: `showsLoadingInsteadOfTree(loading=true, rowCount>0)`는 false를 돌려주므로,
   * `load()`가 시작할 때 이전 프로젝트의 트리를 그대로 두면 `WbsTree`가 응답이 오기 전까지 다른
   * 프로젝트의 행을 계속 그린다. `load()`는 이 화면(WbsView)이 아니라 컴포저블 책임이므로 여기서
   * 고정한다.
   */
  it('다른 프로젝트로 load()하면 응답이 오기 전에 즉시 tree를 비운다', async () => {
    const { tree, referenceDate, load } = useWbs()

    mockedTree.mockResolvedValueOnce(payload([node(1), node(2)]))
    await load(1)
    expect(tree.value).toHaveLength(2)

    const project2 = deferred<WbsTree>()
    mockedTree.mockReturnValueOnce(project2.promise)
    const loading = load(2)

    // 응답이 아직 오지 않았다 — 그런데도 이전 프로젝트(1)의 행이 남아 있으면 안 된다.
    expect(tree.value).toHaveLength(0)
    expect(referenceDate.value).toBeNull()

    project2.resolve(payload([node(3)]))
    await loading

    expect(tree.value.map((n) => n.id)).toEqual([3])
  })

  /**
   * §3(체크포인트 저장 시 트리가 깜빡이는 문제)의 목적이 여기서 유지되는지 확인한다 — 같은
   * 프로젝트를 다시 읽는 것은 "프로젝트 전환"이 아니므로 즉시 비우면 안 된다.
   */
  it('같은 프로젝트를 다시 load()하면 응답이 오기 전에도 이전 목록을 그대로 둔다', async () => {
    const { tree, load } = useWbs()

    mockedTree.mockResolvedValueOnce(payload([node(1), node(2)]))
    await load(1)
    expect(tree.value).toHaveLength(2)

    const reload = deferred<WbsTree>()
    mockedTree.mockReturnValueOnce(reload.promise)
    const loading = load(1)

    expect(tree.value).toHaveLength(2)

    reload.resolve(payload([node(1), node(2), node(4)]))
    await loading

    expect(tree.value).toHaveLength(3)
  })
})
