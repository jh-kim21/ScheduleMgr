import { describe, expect, it } from 'vitest'
import {
  backlogCacheKeyFor,
  cacheKeyFor,
  dashboardCacheKeyFor,
  markBacklogChanged,
  markMembersChanged,
  markRaciChanged,
  markSprintChanged,
  markWbsChanged,
  progressCacheKeyFor,
  raciCacheKeyFor,
  raidCacheKeyFor,
  sprintCacheKeyFor,
  wbsCacheKeyFor,
} from './scheduleCache'

// 이 모듈의 리비전(ref)은 모듈 스코프라서 테스트를 몇 개 거치든 값이 계속 누적된다.
// 그래서 이 파일의 어떤 테스트도 캐시 키의 절대값을 단언하지 않는다(예: `'1:2:0'` 같은 문자열
// 비교 금지). 항상 "호출 전 키를 찍어 두고, 호출 후 키와 비교"하는 방식만 쓴다 — 이러면 다른
// it()이 먼저 돌아 리비전이 올라가 있어도, 나중에 it()을 하나 더 추가해도 깨지지 않는다.
const PROJECT_ID = 1

// 아래 분류는 실제로 화면이 구성원 이름 필드를 렌더하는지 소스를 직접 훑어서 정했다(추측 금지):
//   바뀐다 — BacklogList.vue:151(row.item.assigneeName), SprintBoard.vue:186(item.assigneeName)
//   +175(entry.ownerName, 카드에 걸린 RAID 참조), RaciMatrix.vue(members 컬럼·storyAssignees[].memberName
//   ·issue.memberNames), RaidList.vue:140(item.ownerName), DashboardView.vue(BlockedItem.assigneeName·
//   RaidRef.ownerName)
//   안 바뀐다 — GanttTask/GanttData(ganttApi.ts), WorkPackageProgress(progressApi.ts) 어디에도
//   구성원 이름 필드가 없다
// WBS는 Phase C에서 "안 바뀐다" 쪽에서 "바뀐다" 쪽으로 넘어왔다 — WbsNode에
// responsible/responsibleInherited(MemberRef[])가 생겨 트리가 담당자 이름을 직접 그린다.
describe('markMembersChanged', () => {
  it('RACI 캐시 키를 바꾼다 — 구성원이 매트릭스의 열이다(RaciMatrix members)', () => {
    const before = raciCacheKeyFor(PROJECT_ID)
    markMembersChanged()
    expect(raciCacheKeyFor(PROJECT_ID)).not.toBe(before)
  })

  it('대시보드 캐시 키를 바꾼다 — 차단 카드가 담당자 이름을, RAID 카드가 소유자 이름을 보여준다(BlockedItem.assigneeName, RaidRef.ownerName)', () => {
    const before = dashboardCacheKeyFor(PROJECT_ID)
    markMembersChanged()
    expect(dashboardCacheKeyFor(PROJECT_ID)).not.toBe(before)
  })

  it('RAID 캐시 키를 바꾼다 — 등록부가 소유자 이름을 보여준다(RaidList.vue의 item.ownerName)', () => {
    const before = raidCacheKeyFor(PROJECT_ID)
    markMembersChanged()
    expect(raidCacheKeyFor(PROJECT_ID)).not.toBe(before)
  })

  it('Backlog 캐시 키를 바꾼다 — 목록이 담당자 이름을 보여준다(BacklogList.vue의 row.item.assigneeName)', () => {
    const before = backlogCacheKeyFor(PROJECT_ID)
    markMembersChanged()
    expect(backlogCacheKeyFor(PROJECT_ID)).not.toBe(before)
  })

  it('Sprint 캐시 키를 바꾼다 — Board 카드가 담당자·연결된 RAID 소유자 이름을 보여준다(SprintBoard.vue의 item.assigneeName, entry.ownerName)', () => {
    const before = sprintCacheKeyFor(PROJECT_ID)
    markMembersChanged()
    expect(sprintCacheKeyFor(PROJECT_ID)).not.toBe(before)
  })

  // Phase C 이전에는 "바꾸지 않는다"를 고정하고 있었다. WbsNode에 담당자가 실리면서 그 단언이
  // 거짓이 됐으므로 방향을 뒤집는다 — 이름을 고치면 트리의 담당자 열이 낡는다.
  it('WBS 캐시 키를 바꾼다 — 트리가 담당자 이름을 보여준다(WbsNode.responsible[].name)', () => {
    const before = wbsCacheKeyFor(PROJECT_ID)
    markMembersChanged()
    expect(wbsCacheKeyFor(PROJECT_ID)).not.toBe(before)
  })

  it('간트 캐시 키는 바꾸지 않는다 — GanttTask/GanttData에 구성원 이름 필드가 없다', () => {
    const before = cacheKeyFor(PROJECT_ID)
    markMembersChanged()
    expect(cacheKeyFor(PROJECT_ID)).toBe(before)
  })

  it('진척 캐시 키는 바꾸지 않는다 — WorkPackageProgress에 구성원 이름 필드가 없다', () => {
    const before = progressCacheKeyFor(PROJECT_ID)
    markMembersChanged()
    expect(progressCacheKeyFor(PROJECT_ID)).toBe(before)
  })
})

// 기존 리비전(WBS/Backlog/Sprint)의 분리도 이번에 함께 고정한다 — 지금까지 이 파일에는 테스트가
// 하나도 없었다. 아래 단언은 scheduleCache.ts를 직접 읽고 실제 동작을 따라 적은 것이다.
describe('markWbsChanged', () => {
  it('WBS 캐시 키를 바꾼다', () => {
    const before = wbsCacheKeyFor(PROJECT_ID)
    markWbsChanged()
    expect(wbsCacheKeyFor(PROJECT_ID)).not.toBe(before)
  })

  it('간트 캐시 키를 바꾼다 — 간트도 WBS의 날짜·구조를 그린다', () => {
    const before = cacheKeyFor(PROJECT_ID)
    markWbsChanged()
    expect(cacheKeyFor(PROJECT_ID)).not.toBe(before)
  })

  it('RACI 캐시 키를 바꾼다 — 행이 WBS 트리에서 온다', () => {
    const before = raciCacheKeyFor(PROJECT_ID)
    markWbsChanged()
    expect(raciCacheKeyFor(PROJECT_ID)).not.toBe(before)
  })

  it('진척 캐시 키를 바꾼다 — WBS 트리를 롤업해서 계산한다', () => {
    const before = progressCacheKeyFor(PROJECT_ID)
    markWbsChanged()
    expect(progressCacheKeyFor(PROJECT_ID)).not.toBe(before)
  })
})

/**
 * 지시서 부록 함정 6: **`raciRev` 없이 담당자만 싣는 것** — RACI에서 배정을 바꿔도 WBS가 옛
 * 담당자를 계속 보여준다. 두 화면은 이 카운터 말고는 서로를 모른다.
 */
describe('markRaciChanged', () => {
  it('WBS 캐시 키를 바꾼다 — 트리의 담당자가 RACI의 Responsible 그 자체다', () => {
    const before = wbsCacheKeyFor(PROJECT_ID)
    markRaciChanged()
    expect(wbsCacheKeyFor(PROJECT_ID)).not.toBe(before)
  })

  it('RACI 캐시 키는 바꾸지 않는다 — 모든 RACI 변경 API가 매트릭스 전체를 돌려주므로 자기 화면은 이미 최신이다', () => {
    const before = raciCacheKeyFor(PROJECT_ID)
    markRaciChanged()
    expect(raciCacheKeyFor(PROJECT_ID)).toBe(before)
  })

  it('간트 캐시 키는 바꾸지 않는다 — 간트는 담당자를 보여주지 않는다', () => {
    const before = cacheKeyFor(PROJECT_ID)
    markRaciChanged()
    expect(cacheKeyFor(PROJECT_ID)).toBe(before)
  })

  it('진척 캐시 키는 바꾸지 않는다 — WorkPackageProgress에 담당자 필드가 없다', () => {
    const before = progressCacheKeyFor(PROJECT_ID)
    markRaciChanged()
    expect(progressCacheKeyFor(PROJECT_ID)).toBe(before)
  })
})

describe('markBacklogChanged', () => {
  it('간트 캐시 키를 바꾼다 — Step 6부터 Sprint 레인·공통 진척을 싣는다', () => {
    const before = cacheKeyFor(PROJECT_ID)
    markBacklogChanged()
    expect(cacheKeyFor(PROJECT_ID)).not.toBe(before)
  })

  it('WBS 캐시 키를 바꾼다 — Work Package 행이 연결 Backlog 수를 보여준다', () => {
    const before = wbsCacheKeyFor(PROJECT_ID)
    markBacklogChanged()
    expect(wbsCacheKeyFor(PROJECT_ID)).not.toBe(before)
  })
})

describe('markSprintChanged', () => {
  it('WBS 캐시 키는 바꾸지 않는다 — WBS 화면은 Sprint를 모른다', () => {
    const before = wbsCacheKeyFor(PROJECT_ID)
    markSprintChanged()
    expect(wbsCacheKeyFor(PROJECT_ID)).toBe(before)
  })

  it('RACI 캐시 키는 바꾸지 않는다 — RACI 매트릭스는 Sprint 상태를 보여주지 않는다', () => {
    const before = raciCacheKeyFor(PROJECT_ID)
    markSprintChanged()
    expect(raciCacheKeyFor(PROJECT_ID)).toBe(before)
  })
})

describe('프로젝트 id', () => {
  it('화면마다 다른 프로젝트 id는 다른 캐시 키를 만든다 — 아니면 프로젝트를 넘나들며 캐시가 섞인다', () => {
    const keyFns = [
      cacheKeyFor,
      wbsCacheKeyFor,
      backlogCacheKeyFor,
      sprintCacheKeyFor,
      progressCacheKeyFor,
      raciCacheKeyFor,
      dashboardCacheKeyFor,
      raidCacheKeyFor,
    ]

    keyFns.forEach((keyFor) => {
      expect(keyFor(1)).not.toBe(keyFor(2))
    })
  })
})
