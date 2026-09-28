/**
 * WBS 트리 열의 단일 정의(지시서 `wbs-tree-columns` 3-1). 폭은 CSS(`WbsTree.vue`)에만 둔다 —
 * 여기에 복사해 두면 그 값이 렌더 폭과 어긋날 수 있다(지시서 `wbs-tree-pin-offset`).
 */
export type WbsColumnKey =
  | 'code'
  | 'name'
  | 'startDate'
  | 'endDate'
  | 'mode'
  | 'backlog'
  | 'progress'
  | 'checkpoint'
  | 'owner'
  | 'tags'

/**
 * 필터 행이 이 열에 어떤 컨트롤을 그려야 하는가(지시서 `wbs-tree-filter` 3-1). 판정 자체는
 * `wbsTreeFilter.ts`가 갖고, 여기서는 "무엇으로 거를 수 있는가"만 열 모델에 얹는다 —
 * `WbsColumnSettings`가 열 표시 여부를 이미 이 파일에서 읽으므로, 필터 종류도 같은 자리에
 * 두어야 두 화면(열 설정·필터 행)이 다른 답을 하지 않는다.
 *
 * - `none`: 이번에 필터를 두지 않는다(시작일·종료일 — 기간 필터는 후속 과제).
 * - `text`: 자유 입력 한 칸(WBS 코드·업무명).
 * - `enum`: 정해진 값 중 여러 개를 고르는 칩 토글(실행 방식·연결 Backlog·진척·체크포인트).
 * - `names`/`tags`: 지금 트리에 있는 값에서 뽑은 다중 선택(`<select multiple>`) — 후보를
 *   마스터 목록에서 가져오지 않는다(지시서 2-4).
 */
export type WbsFilterKind = 'none' | 'text' | 'enum' | 'names' | 'tags'

export interface WbsColumn {
  key: WbsColumnKey
  label: string
  hideable: boolean
  filter: WbsFilterKind
}

/** 화면 순서 그대로. */
export const WBS_COLUMNS: readonly WbsColumn[] = [
  { key: 'code', label: 'WBS', hideable: true, filter: 'text' },
  { key: 'name', label: '업무명', hideable: false, filter: 'text' },
  { key: 'startDate', label: '시작일', hideable: true, filter: 'none' },
  { key: 'endDate', label: '종료일', hideable: true, filter: 'none' },
  { key: 'mode', label: '실행 방식', hideable: true, filter: 'enum' },
  { key: 'backlog', label: '연결 Backlog', hideable: true, filter: 'enum' },
  { key: 'progress', label: '진척', hideable: true, filter: 'enum' },
  { key: 'checkpoint', label: '체크포인트', hideable: true, filter: 'enum' },
  { key: 'owner', label: '담당자', hideable: true, filter: 'names' },
  { key: 'tags', label: '분야', hideable: true, filter: 'tags' },
]

const COLUMN_KEYS = new Set<string>(WBS_COLUMNS.map((column) => column.key))

/** `없음 / WBS / WBS + 업무명` — 앞에서부터 연속으로만 고정한다(지시서 2-3). */
export type PinLevel = 'none' | 'code' | 'name'

const PIN_LEVELS = new Set<string>(['none', 'code', 'name'])

/** 고정 단계에서 앞에서부터 참여하는 열 — `pin: 'code'`는 `['code']`, `pin: 'name'`은 `['code', 'name']`. */
const PIN_SEQUENCE: Record<Exclude<PinLevel, 'none'>, WbsColumnKey[]> = {
  code: ['code'],
  name: ['code', 'name'],
}

export interface WbsColumnPrefs {
  hidden: WbsColumnKey[]
  pin: PinLevel
}

export function defaultPrefs(): WbsColumnPrefs {
  return { hidden: [], pin: 'none' }
}

/** 참조를 공유하면 호출한 쪽이 실수로 고치는 순간 다른 곳에도 번진다 — 값을 쓸 때는 `defaultPrefs()`를 쓴다. */
export const DEFAULT_PREFS: WbsColumnPrefs = defaultPrefs()

/** 표시할 열 — 순서는 WBS_COLUMNS 그대로. `name`은 hidden에 있어도 남는다(2-2). */
export function visibleColumns(prefs: WbsColumnPrefs): WbsColumn[] {
  const hidden = new Set(prefs.hidden)
  return WBS_COLUMNS.filter((column) => column.key === 'name' || !hidden.has(column.key))
}

/**
 * 고정 단계에서 앞에서부터 참여하는 열 — 순서만 답하고 폭은 답하지 않는다(지시서
 * `wbs-tree-pin-offset` 2-2/2-3). 선언한 CSS 폭은 `content-box`이고 표가 `table-layout: auto`라
 * 내용에 따라 더 넓어질 수 있어(`.code`는 코드가 길어지면, `.pin-name .col-name`은 패딩만큼)
 * 여기서 값을 알 수 없다 — 렌더된 실제 폭은 `WbsTree.vue`의 `measurePins()`가 머리글 셀에서 잰다.
 * 숨긴 열은 순서에서 빠진다.
 */
export function pinnedSequence(prefs: WbsColumnPrefs): WbsColumnKey[] {
  if (prefs.pin === 'none') return []

  const visible = new Set(visibleColumns(prefs).map((column) => column.key))
  return PIN_SEQUENCE[prefs.pin].filter((key) => visible.has(key))
}

/** 모르는 키·잘못된 pin 값을 걸러 낸다 — localStorage 값은 신뢰할 수 없다. */
export function normalizePrefs(raw: unknown): WbsColumnPrefs {
  if (typeof raw !== 'object' || raw === null) return defaultPrefs()

  const candidate = raw as Record<string, unknown>
  const hiddenRaw = candidate.hidden
  const hidden = Array.isArray(hiddenRaw)
    ? hiddenRaw.filter(
        (key): key is WbsColumnKey => typeof key === 'string' && COLUMN_KEYS.has(key),
      )
    : []
  const pin = typeof candidate.pin === 'string' && PIN_LEVELS.has(candidate.pin)
    ? (candidate.pin as PinLevel)
    : 'none'

  return { hidden, pin }
}
