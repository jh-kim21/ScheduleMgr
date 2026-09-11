/**
 * Pure decisions behind the Sprint "항목 배정" table — filtering by search text and summarising a
 * checkbox selection. Split out from `SprintAssignTable.vue` for the same reason `backlogFilter.ts`
 * and `raidFilter.ts` are: this repo has no `@vue/test-utils`/DOM environment, so anything worth
 * pinning with vitest has to live outside the component.
 */

export interface AssignCandidate {
  id: number
  title: string
  /** 화면에 보이는 유형 이름(예: "Story"). 호출부가 BACKLOG_TYPE_LABELS 로 만들어 넘긴다. */
  typeLabel: string
  /** 팀의 추정. null 은 "추정 없음"이며 0 이 아니다. */
  storyPoint: number | null
}

/**
 * `query`가 (trim 후) 비면 원본 순서를 그대로 돌려준다 — 서버가 준 순서가 곧 Backlog 우선순위
 * 순서이므로 검색이 없을 때 다시 정렬하면 그 순서가 흐트러진다. 대소문자를 무시하고 title 또는
 * typeLabel에 부분 문자열로 포함되면 통과시킨다(유형으로만 좁히는 것도 실제로 자주 필요하다).
 */
export function filterAssignable(candidates: AssignCandidate[], query: string): AssignCandidate[] {
  const needle = query.trim().toLowerCase()
  if (needle === '') return candidates
  return candidates.filter(
    (candidate) =>
      candidate.title.toLowerCase().includes(needle) ||
      candidate.typeLabel.toLowerCase().includes(needle),
  )
}

export interface AssignSelectionSummary {
  count: number
  points: number
  /** 고른 것 중 storyPoint 가 null 인 건수. */
  unestimated: number
}

/**
 * 검색으로 표가 줄어도 체크박스 선택은 유지되므로, 이 함수에는 항상 *전체* 후보(필터를 거치지
 * 않은 목록)를 넘기게 된다 — 필터링된 목록만 넘기면 화면에 안 보이는 선택이 계산에서 빠져 요약이
 * 실제 선택보다 작게 보인다.
 */
export function assignSelectionSummary(
  candidates: AssignCandidate[],
  selectedIds: number[],
): AssignSelectionSummary {
  // 검색으로 줄어든 목록에 없는 id, 중복 id가 섞여 들어와도 실제 후보에 있는 것만, 한 번씩 센다.
  const uniqueIds = new Set(selectedIds)
  const chosen = candidates.filter((candidate) => uniqueIds.has(candidate.id))

  let points = 0
  let unestimated = 0
  for (const candidate of chosen) {
    // null 은 0으로 합치지 않는다 — "추정 없음"과 "0포인트"를 구분하는 것은 이 제품의 핵심
    // 원칙이고(ProgressService의 미산정 ≠ 0%와 같은 태도), 합쳐 버리면 화면의 "N SP"가 실제보다
    // 작게 보이면서도 그 사실을 아무도 알 수 없다.
    if (candidate.storyPoint === null) {
      unestimated += 1
    } else {
      points += candidate.storyPoint
    }
  }

  return { count: chosen.length, points, unestimated }
}
