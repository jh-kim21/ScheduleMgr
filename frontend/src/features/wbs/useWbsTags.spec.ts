import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { CommitPayload } from '../../api/commitApi'
import { enterCommitView, exitCommitView } from '../../stores/commitView'
import { wbsRevision } from '../../stores/scheduleCache'
import { useWbsTags } from './useWbsTags'
import { wbsTagApi } from '../../api/wbsTagApi'

vi.mock('../../api/wbsTagApi', () => ({
  wbsTagApi: {
    list: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    remove: vi.fn(),
  },
}))

const mocked = vi.mocked(wbsTagApi)

const TAG = { id: 1, name: 'Service', color: null, sortOrder: 0 }

function enterCommit() {
  enterCommitView(
    {
      projectId: 7,
      id: 42,
      version: 3,
      asOf: '2026-03-01',
      message: null,
      committedBy: null,
      formatVersion: 7,
    },
    {} as CommitPayload,
  )
}

describe('useWbsTags', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocked.create.mockResolvedValue([TAG])
    mocked.update.mockResolvedValue([TAG])
    mocked.remove.mockResolvedValue([])
  })

  afterEach(() => {
    exitCommitView()
  })

  /**
   * 커밋 조회 잠금은 화면(프로젝트 목록의 `분야` 버튼, `ProjectsView.openTags`)이 먼저 막지만,
   * 마지막 문은 여기다 — 새 쓰기 진입점이 전부 같은 파생값 하나(`readOnly`)를 본다는 것을
   * 여기서 못 박는다(CLAUDE.md "플래그를 하나로 묶어야 한다").
   */
  it('커밋 조회 중에는 서버에 아무것도 보내지 않고 거부로 답한다', async () => {
    const { create, update, remove } = useWbsTags()
    enterCommit()

    expect(await create(7, { name: 'Web', color: null, sortOrder: null })).toBe(false)
    expect(await update(7, 1, { name: 'Web', color: null, sortOrder: 0 })).toBe(false)
    expect(await remove(7, 1)).toBe(false)

    expect(mocked.create).not.toHaveBeenCalled()
    expect(mocked.update).not.toHaveBeenCalled()
    expect(mocked.remove).not.toHaveBeenCalled()
  })

  it('평소에는 서버가 돌려준 전체 목록으로 갈아 끼우고 성공을 알린다', async () => {
    const { tags, create } = useWbsTags()

    expect(await create(7, { name: 'Service', color: null, sortOrder: null })).toBe(true)
    expect(tags.value).toEqual([TAG])
  })

  /**
   * 태그 전용 리비전을 만들지 않은 대신 WBS 리비전을 올린다 — 트리의 칩 이름·색은 WBS 응답에
   * 실려 오므로 트리를 다시 읽으면 따라온다. 이것을 빠뜨리면 이름을 고쳐도 트리가 옛 이름을 계속
   * 보여 준다.
   */
  it('변경하면 WBS를 낡은 것으로 표시한다 — 트리의 칩 이름·색이 거기서 온다', async () => {
    const { create } = useWbsTags()
    const before = wbsRevision()

    await create(7, { name: 'Service', color: null, sortOrder: null })

    expect(wbsRevision()).toBeGreaterThan(before)
  })

  it('거부되면 사유를 남기고 false를 돌려준다 — 대화상자가 입력값을 들고 남아 있어야 한다', async () => {
    const { create, error } = useWbsTags()
    mocked.create.mockRejectedValue(new Error('boom'))

    expect(await create(7, { name: 'Service', color: null, sortOrder: null })).toBe(false)
    expect(error.value).toBe('분야를 추가하지 못했습니다.')
  })
})
