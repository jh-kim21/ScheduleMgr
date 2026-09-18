import type { WorkPackageProgress } from '../../api/progressApi'
import type { WbsNode } from '../../api/wbsApi'

/**
 * WBS 트리의 열별 값 필터(지시서 `wbs-tree-filter`). 판정을 전부 여기 순수 함수로 두는 이유는
 * `raidFilter.ts`와 같다 — 이건 데이터가 아니라 "지금 이 화면"에 대한 질문이라 서버로 보내지
 * 않는다. 다만 RAID는 평면 목록이고 WBS는 트리라서, 일치 행 하나를 남기려면 그 조상까지 함께
 * 남겨야 한다(2-1) — 그 차이가 `filterItems`(평면 필터)와 `filterTree`(아래)의 모양이 다른
 * 이유다.
 */

/** '미지정' 담당자 선택지 — 아무도 배정되지 않은 행을 고르는 한 가지 값이다. */
export const UNSPECIFIED_OWNER = '미지정'

export type ModeFilterValue = 'WATERFALL' | 'AGILE' | 'HYBRID' | 'UNSPECIFIED'
export const MODE_FILTER_ORDER: ModeFilterValue[] = ['WATERFALL', 'AGILE', 'HYBRID', 'UNSPECIFIED']
export const MODE_FILTER_LABELS: Record<ModeFilterValue, string> = {
  WATERFALL: 'Waterfall',
  AGILE: 'Agile',
  HYBRID: 'Hybrid',
  UNSPECIFIED: '미지정',
}

export type BacklogFilterValue = 'LINKED' | 'UNLINKED'
export const BACKLOG_FILTER_ORDER: BacklogFilterValue[] = ['LINKED', 'UNLINKED']
export const BACKLOG_FILTER_LABELS: Record<BacklogFilterValue, string> = {
  LINKED: '있음',
  UNLINKED: '없음',
}

export type ProgressFilterValue =
  | 'NOT_ESTIMABLE'
  | 'ZERO'
  | 'PARTIAL'
  | 'FULL'
  | 'INCOMPLETE'
  | 'ACCEPTANCE_PENDING'
export const PROGRESS_FILTER_ORDER: ProgressFilterValue[] = [
  'NOT_ESTIMABLE',
  'ZERO',
  'PARTIAL',
  'FULL',
  'INCOMPLETE',
  'ACCEPTANCE_PENDING',
]
export const PROGRESS_FILTER_LABELS: Record<ProgressFilterValue, string> = {
  NOT_ESTIMABLE: '산정 전',
  ZERO: '0%',
  PARTIAL: '1~99%',
  FULL: '100%',
  INCOMPLETE: '불완전',
  ACCEPTANCE_PENDING: '인수 대기',
}

export type CheckpointFilterValue = 'NONE' | 'PARTIAL' | 'ALL'
export const CHECKPOINT_FILTER_ORDER: CheckpointFilterValue[] = ['NONE', 'PARTIAL', 'ALL']
export const CHECKPOINT_FILTER_LABELS: Record<CheckpointFilterValue, string> = {
  NONE: '없음',
  PARTIAL: '일부 승인',
  ALL: '전부 승인',
}

export interface WbsFilterState {
  /** WBS 코드 앞자리 — 마디(`.`) 단위로 비교한다(`matchesCode` 참고). */
  code: string
  /** 업무명 + 설명, 대소문자 무시 부분 일치. */
  name: string
  /** 빈 배열 = 조건 없음. 여러 값은 OR. */
  mode: ModeFilterValue[]
  /** 단일 선택 — `null` = 조건 없음. */
  backlog: BacklogFilterValue | null
  progress: ProgressFilterValue[]
  checkpoint: CheckpointFilterValue[]
  /** 이름. `UNSPECIFIED_OWNER`를 포함할 수 있다. */
  owners: string[]
  tagIds: number[]
}

/**
 * `EMPTY_FILTER`를 공유 가변 객체로 두지 않는다 — 배열 필드(`mode`·`owners`·`tagIds` 등)를 한
 * 곳에서 `push`하면 다른 곳의 "빈 필터"까지 오염된다(`wbsColumns.ts`의 `defaultPrefs()`/
 * `DEFAULT_PREFS`와 같은 이유·같은 처방). **값을 쓸 때는 이 팩토리를 부른다.**
 */
export function emptyFilter(): WbsFilterState {
  return {
    code: '',
    name: '',
    mode: [],
    backlog: null,
    progress: [],
    checkpoint: [],
    owners: [],
    tagIds: [],
  }
}

export const EMPTY_FILTER: WbsFilterState = emptyFilter()

export function hasActiveFilter(state: WbsFilterState): boolean {
  return (
    state.code.trim() !== '' ||
    state.name.trim() !== '' ||
    state.mode.length > 0 ||
    state.backlog !== null ||
    state.progress.length > 0 ||
    state.checkpoint.length > 0 ||
    state.owners.length > 0 ||
    state.tagIds.length > 0
  )
}

export interface FilteredTree {
  /** 일치 행 + 그 조상만 남은 트리. 구조는 원본과 같다(children 배열이 잘려 있을 뿐). */
  nodes: WbsNode[]
  /** 조건에 실제로 맞은 행. 나머지는 문맥이다. */
  matchIds: Set<number>
}

/**
 * WBS 코드의 앞자리 일치 — **마디(`.`) 단위**다(팀 리드 결정 1). `1.2`를 넣으면 `1.2`·`1.2.1`은
 * 남고 `1.20`은 남지 않는다: 코드는 `.`로 나뉜 마디의 나열이고, `1.20`은 `1.2`의 하위가 아니라
 * `1.2`의 부모 밑에 있는 형제(`1.2`, `1.20`, `1.21`, …)다. 문자열 `startsWith`이라면 `1.2`가
 * `1.20`에도 걸려 서로 다른 가지가 같은 조건에 섞인다.
 */
function matchesCode(code: string, query: string): boolean {
  const needle = query.trim()
  if (needle === '') return true
  const needleParts = needle.split('.')
  const codeParts = code.split('.')
  if (needleParts.length > codeParts.length) return false
  return needleParts.every((part, index) => part === codeParts[index])
}

function matchesName(node: WbsNode, query: string): boolean {
  const needle = query.trim().toLowerCase()
  if (needle === '') return true
  const haystack = `${node.name} ${node.description ?? ''}`.toLowerCase()
  return haystack.includes(needle)
}

/**
 * 실행 방식은 최하위 Work Package만 판정한다(3-1의 표) — Summary가 전환 전부터 보관 중인
 * `executionMode`는 지금 적용되는 값이 아니므로 일치로 보지 않는다. 이 판정을 `nodeType`
 * 검사로 앞에서 끊어 두면, 전환 전 값이 무엇이었든 Summary는 이 필터에 걸리지 않는다.
 */
function matchesMode(node: WbsNode, values: ModeFilterValue[]): boolean {
  if (node.nodeType !== 'WORK_PACKAGE') return false
  const own: ModeFilterValue = node.executionMode ?? 'UNSPECIFIED'
  return values.includes(own)
}

/** `backlogSummary`는 하위까지 합친 값이라 이 행(또는 그 가지)에 무엇이든 걸려 있으면 있음이다. */
function hasLinkedBacklog(node: WbsNode): boolean {
  const summary = node.backlogSummary
  return summary !== null && summary.items + summary.archived > 0
}

function matchesBacklog(node: WbsNode, value: BacklogFilterValue): boolean {
  return hasLinkedBacklog(node) === (value === 'LINKED')
}

function matchesProgressValue(node: WbsNode, value: ProgressFilterValue): boolean {
  const percent = node.computedProgress
  switch (value) {
    case 'NOT_ESTIMABLE':
      return percent === null
    case 'ZERO':
      return percent === 0
    case 'PARTIAL':
      return percent !== null && percent > 0 && percent < 100
    case 'FULL':
      return percent === 100
    case 'INCOMPLETE':
      return node.progressIncomplete
    case 'ACCEPTANCE_PENDING':
      return node.acceptancePending
  }
}

/**
 * 없음(체크포인트가 하나도 없음) / 일부 승인(있지만 전부는 아님, 0건 승인도 여기) / 전부 승인.
 * `checkpointBadge`(`checkpointRow.ts`)가 같은 두 숫자로 "체크포인트 없음"/"승인 N/M"을
 * 그리는 것과 같은 기준선이다.
 */
function checkpointStatus(total: number, approved: number): CheckpointFilterValue {
  if (total === 0) return 'NONE'
  return approved >= total ? 'ALL' : 'PARTIAL'
}

/**
 * `workPackages`가 아예 없으면(호출자가 인자를 안 넘김) 이 열의 조건은 **no-op**이다 — 걸려
 * 있는 값이 무엇이든 모든 행을 통과시킨다. `{}`(진짜로 비어 있는 맵, 예: Work Package가 하나도
 * 없는 프로젝트)와는 다르다 — 그 경우는 아래에서 정상적으로 "없음"으로 판정한다. 구분하지
 * 않으면(둘 다 "없음"으로 본다면) 이 데이터를 모르는 호출자가 체크포인트 필터를 걸었을 때
 * 트리가 통째로 비어 사용자가 이유를 알 수 없다.
 */
function matchesCheckpoint(
  node: WbsNode,
  values: CheckpointFilterValue[],
  workPackages: Record<number, WorkPackageProgress> | undefined,
): boolean {
  if (workPackages === undefined) return true
  const wp = workPackages[node.id]
  const status = checkpointStatus(wp?.checkpointTotal ?? 0, wp?.checkpointApproved ?? 0)
  return values.includes(status)
}

/** 담당자 열이 보여주는 이름 그대로 — 자기 배정과 상속 배정을 모두 본다(화면이 둘 다 보여준다). */
function ownerNames(node: WbsNode): string[] {
  return [...(node.responsible ?? []), ...(node.responsibleInherited ?? [])].map(
    (member) => member.name,
  )
}

function matchesOwners(node: WbsNode, owners: string[]): boolean {
  const names = ownerNames(node)
  return owners.some((owner) =>
    owner === UNSPECIFIED_OWNER ? names.length === 0 : names.includes(owner),
  )
}

/**
 * 분야는 **자기 태그만** 본다(`node.tags`) — `tagSummary`(하위 요약)는 자기 분야가 아니다.
 * Summary가 전환 전부터 보관 중인 자기 태그는 `node.tags`에 그대로 있으므로(실행 방식과 같은
 * 규칙) 그건 정상적으로 일치한다.
 */
function matchesTags(node: WbsNode, tagIds: number[]): boolean {
  const own = (node.tags ?? []).map((tag) => tag.id)
  return tagIds.some((id) => own.includes(id))
}

/** 열 간 AND, 한 열 안의 값은 OR(조건이 없는 열은 통과). */
function matchesNode(
  node: WbsNode,
  state: WbsFilterState,
  workPackages: Record<number, WorkPackageProgress> | undefined,
): boolean {
  if (!matchesCode(node.code, state.code)) return false
  if (!matchesName(node, state.name)) return false
  if (state.mode.length > 0 && !matchesMode(node, state.mode)) return false
  if (state.backlog !== null && !matchesBacklog(node, state.backlog)) return false
  if (state.progress.length > 0 && !state.progress.some((v) => matchesProgressValue(node, v))) {
    return false
  }
  if (state.checkpoint.length > 0 && !matchesCheckpoint(node, state.checkpoint, workPackages)) {
    return false
  }
  if (state.owners.length > 0 && !matchesOwners(node, state.owners)) return false
  if (state.tagIds.length > 0 && !matchesTags(node, state.tagIds)) return false
  return true
}

/**
 * 한 노드를 재귀적으로 거른다. **일치 행의 하위는 끌고 오지 않는다**(2-1) — 자식은 언제나 이
 * 재귀 결과(`filteredChildren`)로 바뀐다. 자기 자신이 일치했다고 원래 `children`을 그대로
 * 쓰면, 그 자식이 조건에 맞지 않아도 함께 살아남는다. 반대로 자기 자신은 일치하지 않아도 자식
 * 중 하나가 일치하면(문맥으로) 살아남아야 한다.
 */
function filterNode(
  node: WbsNode,
  state: WbsFilterState,
  workPackages: Record<number, WorkPackageProgress> | undefined,
  matchIds: Set<number>,
): WbsNode | null {
  const selfMatch = matchesNode(node, state, workPackages)
  if (selfMatch) matchIds.add(node.id)

  const filteredChildren: WbsNode[] = []
  for (const child of node.children) {
    const filtered = filterNode(child, state, workPackages, matchIds)
    if (filtered) filteredChildren.push(filtered)
  }

  if (!selfMatch && filteredChildren.length === 0) return null
  return { ...node, children: filteredChildren }
}

/**
 * 조건이 하나도 없으면 원본 배열을 그대로 돌려준다(참조가 같다) — 필터 토글을 막 켰을 때, 아직
 * 아무 조건도 안 걸렸다면 트리를 다시 만들 이유가 없다.
 *
 * `workPackages`는 **지시서 3-2가 정한 2-인자 시그니처에 없는 세 번째 인자**다 — 체크포인트
 * 수(`checkpointTotal`·`checkpointApproved`)는 `WbsNode`가 아니라 `WorkPackageProgress`
 * (`api/progressApi.ts`, 진척 서비스가 따로 계산해 `WbsTree.vue`에 `workPackages` prop으로
 * 흘려준다)에만 있어서, 트리 노드만으로는 체크포인트 필터를 판정할 수 없다. 이유를 남기는 것은
 * 다음에 이 파일을 고치는 사람이 "지시서대로" 이 인자를 지우지 않게 하기 위해서다.
 *
 * 넘기지 않으면(`undefined`) 체크포인트 열의 조건은 **no-op**이다 — 어떤 값을 골랐든 모든 행을
 * 통과시킨다(`matchesCheckpoint` 참고). 반대로 "아무것도 일치하지 않음"으로 하면, 이 데이터를
 * 모르는 호출자가 체크포인트 조건을 걸었을 때 트리가 통째로 비어 사용자가 이유를 알 수 없다.
 * `{}`(진짜로 비어 있는 맵)는 다르게 다룬다 — 그때는 각 행이 정상적으로 "없음"으로 판정된다.
 */
export function filterTree(
  nodes: WbsNode[],
  state: WbsFilterState,
  workPackages?: Record<number, WorkPackageProgress>,
): FilteredTree {
  if (!hasActiveFilter(state)) {
    return { nodes, matchIds: new Set() }
  }

  const matchIds = new Set<number>()
  const filtered: WbsNode[] = []
  for (const node of nodes) {
    const result = filterNode(node, state, workPackages, matchIds)
    if (result) filtered.push(result)
  }
  return { nodes: filtered, matchIds }
}

/**
 * 필터 행의 선택지 — 지금 트리에 있는 값만(지시서 2-4). 마스터 목록(구성원 API·`useWbsTags`)을
 * 부르지 않는다.
 */
export function filterOptions(nodes: WbsNode[]): {
  owners: string[]
  tags: { id: number; name: string }[]
} {
  const owners = new Set<string>()
  const tags = new Map<number, string>()
  let hasUnspecified = false

  const visit = (list: WbsNode[]) => {
    for (const node of list) {
      const names = ownerNames(node)
      if (names.length === 0) hasUnspecified = true
      for (const name of names) owners.add(name)
      for (const tag of node.tags ?? []) tags.set(tag.id, tag.name)
      visit(node.children)
    }
  }
  visit(nodes)

  const sortedOwners = [...owners].sort((a, b) => a.localeCompare(b, 'ko'))
  if (hasUnspecified) sortedOwners.push(UNSPECIFIED_OWNER)

  const sortedTags = [...tags.entries()]
    .map(([id, name]) => ({ id, name }))
    .sort((a, b) => a.name.localeCompare(b.name, 'ko'))

  return { owners: sortedOwners, tags: sortedTags }
}
