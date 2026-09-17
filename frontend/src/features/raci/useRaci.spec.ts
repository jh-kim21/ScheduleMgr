import { beforeEach, describe, expect, it, vi } from 'vitest'
import { raciCacheKeyFor, wbsCacheKeyFor } from '../../stores/scheduleCache'
import { useRaci } from './useRaci'
import { raciApi, type RaciMatrix } from '../../api/raciApi'

vi.mock('../../api/raciApi', () => ({
  raciApi: {
    matrix: vi.fn(),
    assign: vi.fn(),
    unassign: vi.fn(),
  },
}))

const mocked = vi.mocked(raciApi)

const EMPTY: RaciMatrix = { members: [], tasks: [], cells: [], issues: [] }

/**
 * 지시서 부록 함정 6: **`raciRev` 없이 담당자만 싣는 것.** 카운터를 만들어 두고 부르지 않으면
 * 결과는 같다 — RACI에서 배정을 바꿔도 WBS 트리가 옛 담당자를 계속 보여준다. `scheduleCache.spec.ts`
 * 가 카운터의 동작을 고정하고, 이 파일이 **호출부**를 고정한다.
 */
describe('useRaci — 배정 변경이 WBS를 낡게 만든다', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocked.assign.mockResolvedValue(EMPTY)
    mocked.unassign.mockResolvedValue(EMPTY)
  })

  it('배정하면 WBS 캐시 키가 바뀐다', async () => {
    const { assign } = useRaci()
    const before = wbsCacheKeyFor(1)

    await assign(1, { wbsItemId: 10, memberId: 2, role: 'RESPONSIBLE' })

    expect(wbsCacheKeyFor(1)).not.toBe(before)
  })

  it('해제해도 WBS 캐시 키가 바뀐다 — 사람이 빠지는 것도 담당자 열의 변화다', async () => {
    const { unassign } = useRaci()
    const before = wbsCacheKeyFor(1)

    await unassign(1, 99)

    expect(wbsCacheKeyFor(1)).not.toBe(before)
  })

  it('자기 화면은 다시 읽지 않는다 — 응답으로 받은 매트릭스가 이미 최신이다', async () => {
    const { assign } = useRaci()

    await assign(1, { wbsItemId: 10, memberId: 2, role: 'RESPONSIBLE' })

    // 응답을 적용한 뒤의 키가 곧 캐시 키가 되어야 ensureLoaded 가 곧바로 다시 부르지 않는다.
    const { ensureLoaded } = useRaci()
    await ensureLoaded(1)
    expect(mocked.matrix).not.toHaveBeenCalled()
    expect(raciCacheKeyFor(1)).toBeTruthy()
  })
})
