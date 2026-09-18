/**
 * WBS 트리 열의 단일 정의(지시서 `wbs-tree-columns` 3-1). 폭은 CSS(`WbsTree.vue`)에 두고, 여기에는
 * 고정 오프셋 계산에 필요한 것만 둔다(= 고정 가능한 열의 폭만) — 그러지 않으면 같은 값이 두 곳에
 * 생겨 어긋난다.
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
  /** 고정 단계에 참여하는 열의 폭(rem). 고정 오프셋 계산에만 쓴다. */
  pinWidthRem?: number
  filter: WbsFilterKind
}

/**
 * 화면 순서 그대로. `code.pinWidthRem`(5.5)은 `WbsTree.vue`의 `.code` 폭과, `name.pinWidthRem`(22)은
 * `.pin-name .col-name`의 폭과 반드시 같은 값이어야 한다 — 어긋나면 오른쪽 열이 고정 영역 아래로
 * 밀려 들어간다.
 */
export const WBS_COLUMNS: readonly WbsColumn[] = [
  { key: 'code', label: 'WBS', hideable: true, pinWidthRem: 5.5, filter: 'text' },
  { key: 'name', label: '업무명', hideable: false, pinWidthRem: 22, filter: 'text' },
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
 * 고정된 열의 left 오프셋(rem). 고정되지 않은 열은 키가 없다. 숨겨진 열은 누적에서 빠진다 —
 * WBS 코드를 숨긴 채 업무명을 고정하면 업무명의 left는 0이어야 한다(2-3).
 */
export function pinOffsets(prefs: WbsColumnPrefs): Partial<Record<WbsColumnKey, number>> {
  if (prefs.pin === 'none') return {}

  const visible = new Set(visibleColumns(prefs).map((column) => column.key))
  const offsets: Partial<Record<WbsColumnKey, number>> = {}
  let cursor = 0

  for (const key of PIN_SEQUENCE[prefs.pin]) {
    if (!visible.has(key)) continue
    offsets[key] = cursor
    const column = WBS_COLUMNS.find((c) => c.key === key)
    cursor += column?.pinWidthRem ?? 0
  }

  return offsets
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
