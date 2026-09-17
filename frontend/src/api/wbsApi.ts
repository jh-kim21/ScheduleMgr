import type { BacklogSummary } from '../shared/backlog'
import type { DelayInfo } from '../shared/delay'
import type { AcceptanceStatus, ProgressBasis } from '../shared/progress'
import type {
  ExecutionMode,
  ExecutionModeSummary,
  WbsNodeType,
} from '../shared/executionMode'
import { http } from './http'

/**
 * A person on a WBS row, as the tree carries them. Just `(memberId, name)`: the tree renders 담당자
 * read-only and links to `/raci` to change them, so it never needs an assignment id.
 */
export interface MemberRef {
  memberId: number
  name: string
}

/**
 * A 업무 분야 tag. `color` is optional — when it is null the screen picks a palette slot from the
 * name (`tagColor.ts`), never at random, so the same tag keeps the same colour on every repaint.
 */
export interface TagRef {
  id: number
  name: string
  color: string | null
}

/**
 * A WBS tree node. `code`, `level`, `summary`, `executionModeSummary`, the delay fields and — for
 * summary nodes — `startDate`, `endDate` and `progress` are derived by the server from tree
 * position, children and the reference date, so the client renders them as-is.
 */
export interface WbsNode extends DelayInfo {
  id: number
  parentId: number | null
  code: string
  level: number
  name: string
  description: string | null
  endDate: string | null
  /** Whether the schedule and progress above were rolled up from children. */
  summary: boolean
  nodeType: WbsNodeType
  /**
   * The entry's own mode; `null` is 미지정. On a `SUMMARY` entry a non-null value is a mode
   * retained from before it was converted — the screen flags it rather than using it.
   */
  executionMode: ExecutionMode | null
  /** How the Work Packages below are executed; `null` when the entry has no children. */
  executionModeSummary: ExecutionModeSummary | null
  /**
   * Linked Backlog counts, rolled up from below; `null` when this branch has none. Only a
   * `WORK_PACKAGE` row's count points at one place, so only that row links to the Backlog screen.
   */
  backlogSummary: BacklogSummary | null
  weight: number | null
  agileRatio: number | null
  acceptanceStatus: AcceptanceStatus | null
  /**
   * The common aggregation's figure, unrounded. `null` means 산정 전 — not 0. `progress` above is
   * still the stored/legacy value, so nothing that read it before Step 5 changed meaning.
   */
  computedProgress: number | null
  progressBasis: ProgressBasis | null
  progressIncomplete: boolean
  progressNote: string | null
  acceptancePending: boolean
  /**
   * When work really started/finished, and when it now looks like it will finish (지시서 6-A).
   * Optional — as of this fix the WBS tree endpoint (`WbsNodeResponse`) does not yet return these
   * three fields, only the Gantt endpoint does. The edit form still lets a user set them (they are
   * sent on save), but cannot pre-fill a previously saved value here until the tree response is
   * extended to carry them too — see the note in `WbsForm.vue`.
   */
  actualStartDate?: string | null
  actualEndDate?: string | null
  forecastEndDate?: string | null
  /**
   * 담당자 — the RACI `RESPONSIBLE` letter, computed server-side with the same `RaciInheritance` the
   * matrix uses so the two screens cannot name different people. Split in two the way a RACI cell
   * splits `roles`/`inherited`: an inherited name is declared on an ancestor and cannot be removed
   * from this row. A member appears in at most one of the lists.
   *
   * Empty arrays rather than null when nobody is assigned. Code that reads them still falls back to
   * `[]`, because a commit taken before Phase C replays a stored payload that has no such field.
   */
  responsible: MemberRef[]
  responsibleInherited: MemberRef[]
  /**
   * 업무 분야 — the tags attached to this row. Tags belong on real work, so only a Work Package
   * normally carries them; a Summary may still hold a set retained from before it was converted
   * (the server keeps it rather than clearing it, exactly like `executionMode`).
   */
  tags: TagRef[]
  /**
   * The union of the tags below this row (grandchildren included), or `null` when it has no
   * children — the same rule as `executionModeSummary`. A row's own retained tags are not in it.
   */
  tagSummary: TagRef[] | null
  children: WbsNode[]
}

/**
 * The tree plus the date its delay verdicts were measured from. Wrapped rather than a bare array
 * because "6일 지연" means nothing without saying which day it was judged against.
 */
export interface WbsTree {
  referenceDate: string | null
  nodes: WbsNode[]
}

export interface WbsItemInput {
  name: string
  description: string | null
  startDate: string | null
  endDate: string | null
  progress: number
  weight: number | null
  agileRatio: number | null
  acceptanceStatus: AcceptanceStatus | null
  nodeType: WbsNodeType
  /**
   * `null` is 미지정. On a `SUMMARY` entry this must repeat the retained value — the server rejects
   * an attempt to *change* a summary's mode, which is what the form sends back unchanged.
   */
  executionMode: ExecutionMode | null
  /**
   * When work really started/finished, and when it now looks like it will finish. Missing from
   * this type used to mean every update silently wiped these three columns to null — the update
   * endpoint takes "not present" as "clear it", not "leave it alone" (결함 3). Always send back
   * whatever the form is currently holding, `null` included, so a plain no-op edit is a no-op here.
   */
  actualStartDate: string | null
  actualEndDate: string | null
  forecastEndDate: string | null
  /**
   * 업무 분야 태그의 전체 집합.
   *
   * **`null` = 변경 없음, `[]` = 전부 해제.** 이 구분이 있어야 태그를 모르는 호출자(가져오기,
   * 구형 클라이언트, 다른 화면)가 항목을 저장해도 태그가 조용히 지워지지 않는다 — 커밋
   * `da96ebe`(실적/예상 종료일이 저장마다 사라지던 결함)와 같은 종류의 사고다.
   *
   * **`WbsForm`은 항상 배열을 명시적으로 보낸다** — 폼이 화면에 보여 준 집합이 곧 저장될 집합이고,
   * "변경 없음"으로 보낼 이유가 없다. Summary 항목은 서버가 변경을 거부하므로 보관값을 그대로
   * 되돌려 보낸다(실행 방식과 같다).
   */
  tagIds: number[] | null
}

export interface WbsMoveInput {
  parentId: number | null
  position: number
}

/**
 * 열 순서는 고정: 레벨 · 업무명 · 시작일 · 종료일 · 진행률. 실행 방식·가중치 같은 이 앱 고유 값은
 * 받지 않는다 — PM이 이미 갖고 있는 평범한 표를 그대로 올릴 수 있어야 하기 때문이다.
 */
export interface WbsImportInput {
  file: File
  /** null이면 프로젝트 최상위에 붙는다. */
  parentId: number | null
}

/** Every mutation returns the whole rebuilt tree, since codes and rollups shift on any change. */
export const wbsApi = {
  tree: (projectId: number) => http.get<WbsTree>(`/projects/${projectId}/wbs`),
  create: (projectId: number, parentId: number | null, input: WbsItemInput) =>
    http.post<WbsTree>(`/projects/${projectId}/wbs`, { parentId, ...input }),
  update: (projectId: number, itemId: number, input: WbsItemInput) =>
    http.put<WbsTree>(`/projects/${projectId}/wbs/${itemId}`, input),
  move: (projectId: number, itemId: number, input: WbsMoveInput) =>
    http.put<WbsTree>(`/projects/${projectId}/wbs/${itemId}/move`, input),
  remove: (projectId: number, itemId: number) =>
    http.delete<WbsTree>(`/projects/${projectId}/wbs/${itemId}`),
  importFile: (projectId: number, input: WbsImportInput) => {
    const form = new FormData()
    form.append('file', input.file)
    const query = input.parentId !== null ? `?parentId=${input.parentId}` : ''
    return http.postForm<WbsTree>(`/projects/${projectId}/wbs/import${query}`, form)
  },
}
