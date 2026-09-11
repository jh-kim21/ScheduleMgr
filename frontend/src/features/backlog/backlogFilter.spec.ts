import { describe, expect, it } from 'vitest'
import type { BacklogItem } from '../../api/backlogApi'
import {
  DEFAULT_FILTERS,
  defaultLinkFromFilter,
  isFiltered,
  visibleRows,
  type BacklogFilters,
} from './backlogFilter'

let nextId = 1

function item(over: Partial<BacklogItem> = {}): BacklogItem {
  return {
    id: nextId++,
    wbsItemId: 10,
    wbsCode: '1',
    wbsName: '개발',
    wbsExecutionMode: 'AGILE',
    parentId: null,
    parentTitle: null,
    depth: 0,
    itemType: 'STORY',
    title: `항목 ${nextId}`,
    description: null,
    priority: 'MEDIUM',
    status: 'TODO',
    assigneeMemberId: null,
    assigneeName: null,
    acceptanceCriteria: null,
    storyPoint: null,
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
    ...over,
  }
}

const filters = (over: Partial<BacklogFilters> = {}): BacklogFilters => ({
  ...DEFAULT_FILTERS,
  ...over,
})

describe('visibleRows', () => {
  it('기본값은 보관된 항목을 감춘다 — 접어둔 것이 열린 일처럼 보이면 안 된다', () => {
    const open = item()
    const archived = item({ archived: true })

    expect(visibleRows([open, archived], filters()).map((row) => row.item.id)).toEqual([open.id])
  })

  it('보관만 볼 수도 있다', () => {
    const open = item()
    const archived = item({ archived: true })

    expect(visibleRows([open, archived], filters({ archive: 'ARCHIVED' })).map((r) => r.item.id))
      .toEqual([archived.id])
  })

  it('Work Package로 걸러낸다 — WBS 화면에서 건너올 때 쓰는 경로', () => {
    const mine = item({ wbsItemId: 10 })
    const other = item({ wbsItemId: 20 })

    expect(visibleRows([mine, other], filters({ wbsItemId: 10 })).map((r) => r.item.id))
      .toEqual([mine.id])
  })

  it('미연결만 골라낼 수 있다', () => {
    const linked = item({ wbsItemId: 10 })
    const draft = item({ wbsItemId: null, unlinked: true })

    expect(visibleRows([linked, draft], filters({ link: 'UNLINKED' })).map((r) => r.item.id))
      .toEqual([draft.id])
  })

  it('유형과 상태로 걸러낸다', () => {
    const story = item({ itemType: 'STORY', status: 'TODO' })
    const bug = item({ itemType: 'BUG', status: 'DONE' })

    expect(visibleRows([story, bug], filters({ type: 'BUG' })).map((r) => r.item.id)).toEqual([bug.id])
    expect(visibleRows([story, bug], filters({ status: 'DONE' })).map((r) => r.item.id)).toEqual([bug.id])
  })

  it('걸러진 하위의 상위는 맥락으로 남긴다 — Task만 떠 있으면 어디 소속인지 알 수 없다', () => {
    const epic = item({ itemType: 'EPIC', title: '묶음', aggregated: false })
    const story = item({ itemType: 'STORY', parentId: epic.id, depth: 1 })
    const task = item({ itemType: 'TASK', parentId: story.id, depth: 2, aggregated: false })

    const rows = visibleRows([epic, story, task], filters({ type: 'TASK' }))

    expect(rows.map((row) => [row.item.id, row.context])).toEqual([
      [epic.id, true],
      [story.id, true],
      [task.id, false],
    ])
  })

  it('아무것도 맞지 않으면 맥락도 남기지 않는다', () => {
    expect(visibleRows([item({ itemType: 'STORY' })], filters({ type: 'BUG' }))).toEqual([])
  })
})

describe('isFiltered', () => {
  it('기본값은 필터가 걸리지 않은 상태다', () => {
    expect(isFiltered(filters())).toBe(false)
  })

  it('하나라도 바뀌면 걸린 상태다', () => {
    expect(isFiltered(filters({ wbsItemId: 10 }))).toBe(true)
    expect(isFiltered(filters({ archive: 'ALL' }))).toBe(true)
    expect(isFiltered(filters({ link: 'UNLINKED' }))).toBe(true)
  })
})

describe('defaultLinkFromFilter', () => {
  it('필터가 안 걸렸으면 기본 귀속도 없다', () => {
    expect(defaultLinkFromFilter(null, [{ id: 10 }])).toBeNull()
  })

  it('필터가 가리키는 Work Package가 목록에 있으면 그 값을 쓴다', () => {
    expect(defaultLinkFromFilter(10, [{ id: 10 }, { id: 20 }])).toBe(10)
  })

  it(
    '목록에 없는 id면 null이다 — 필터 값은 WBS 화면의 ?wbs= 쿼리로도 들어오는데, ' +
      '그 사이 항목이 지워지거나 Summary로 바뀌어 옵션에서 빠졌을 수 있다. ' +
      '없는 id를 그대로 폼에 넣으면 드롭다운이 빈 것처럼 보이면서 저장이 거부된다',
    () => {
      expect(defaultLinkFromFilter(99, [{ id: 10 }, { id: 20 }])).toBeNull()
    },
  )

  it('후보가 비어 있으면 null', () => {
    expect(defaultLinkFromFilter(10, [])).toBeNull()
  })

  it('id 0처럼 falsy한 값도 목록에 있으면 제대로 돌려준다', () => {
    expect(defaultLinkFromFilter(0, [{ id: 0 }, { id: 1 }])).toBe(0)
  })
})
