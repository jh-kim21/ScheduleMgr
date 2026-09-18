<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import type { WbsMoveInput, WbsNode } from '../../api/wbsApi'
import type { WorkPackageProgress } from '../../api/progressApi'
import CheckpointList from '../progress/CheckpointList.vue'
import TagChip from './TagChip.vue'
import { approvalBadge, supportsCheckpoints } from './checkpointRow'
import { responsibleNames, responsibleTitle, type ResponsibleName } from './responsibleCell'
import { tagCell, type TagCell } from './tagCell'
import { actionHint, toolbarState } from './wbsToolbar'
import {
  indentInput,
  moveDownInput,
  moveState as computeMoveState,
  moveUpInput,
  outdentInput,
} from './wbsMove'
import { pinOffsets, visibleColumns, WBS_COLUMNS, type WbsColumnKey } from './wbsColumns'
import { columnPrefs } from './wbsColumnPrefs'
import WbsColumnSettings from './WbsColumnSettings.vue'
import {
  BACKLOG_FILTER_LABELS,
  BACKLOG_FILTER_ORDER,
  CHECKPOINT_FILTER_LABELS,
  CHECKPOINT_FILTER_ORDER,
  emptyFilter,
  filterOptions,
  filterTree,
  hasActiveFilter,
  MODE_FILTER_LABELS,
  MODE_FILTER_ORDER,
  PROGRESS_FILTER_LABELS,
  PROGRESS_FILTER_ORDER,
  type BacklogFilterValue,
  type CheckpointFilterValue,
  type ModeFilterValue,
  type ProgressFilterValue,
  type WbsFilterState,
} from './wbsTreeFilter'
import { backlogSummaryText } from '../../shared/backlog'
import { delayBadge, delayDescription, needsAttention } from '../../shared/delay'
import { executionModeLabel, executionModeSummaryText } from '../../shared/executionMode'
import {
  ACCEPTANCE_STATUS_LABELS,
  PROGRESS_BASIS_HINTS,
  PROGRESS_BASIS_LABELS,
  progressBarWidth,
  progressText,
} from '../../shared/progress'
import {
  containsDescendant,
  findNode,
  findParent,
  flattenTree,
  resolveDropPosition,
  type DropPlacement,
  type WbsRow,
} from './wbsTree'

const props = defineProps<{
  tree: WbsNode[]
  /** Row to reveal and highlight — set when arriving from a Backlog entry's WBS link. */
  focusId?: number | null
  /** 커밋 시점 조회 중이면 드래그 이동·더블클릭 편집·행 액션을 모두 막는다. */
  readOnly?: boolean
  /** Needed to call the checkpoint API from an expanded row (`CheckpointList`). */
  projectId?: number | null
  /**
   * Each Work Package's checkpoint data, keyed by `wbsItemId` (지시서 4.3 권장안) — this tree
   * fetches its own nodes via `useWbs`, which knows nothing about checkpoints, so the view reads
   * `useProgress` separately and indexes it here. Nodes without an entry (Summary, Agile,
   * unspecified) simply render no badge.
   */
  workPackages?: Record<number, WorkPackageProgress>
}>()

const emit = defineEmits<{
  addChild: [parent: WbsNode]
  edit: [node: WbsNode]
  remove: [node: WbsNode]
  move: [itemId: number, input: WbsMoveInput]
}>()

const collapsed = ref(new Set<number>())

/**
 * 열별 값 필터(지시서 `wbs-tree-filter`). 판정은 전부 `wbsTreeFilter.ts`에 있고, 여기서는 그
 * 결과를 렌더링에 흘려 넣기만 한다. `filterRowOpen`은 필터 행이 보이는지(툴바의 [필터] 토글)
 * 이고, `filter` 자체의 조건과는 독립이다 — 행을 접어도 걸린 조건은 유지된다. [필터 해제]만
 * 조건을 비운다.
 */
const filter = ref<WbsFilterState>(emptyFilter())
const filterRowOpen = ref(false)
const filtering = computed(() => hasActiveFilter(filter.value))
/*
 * `props.workPackages`를 `?? {}`로 채우지 않고 그대로 넘긴다 — `filterTree`는 "인자를 안
 * 넘겼다"(`undefined`, 체크포인트 조건 no-op)와 "진짜로 빈 맵"(각 행이 정상적으로 "없음"으로
 * 판정)을 구분한다(`wbsTreeFilter.ts`의 `filterTree` 주석 참고). 여기서 `{}`로 뭉개면 이 화면이
 * `workPackages`를 안 받는 다른 호출자에서도 항상 "빈 맵"으로 보여 그 구분이 무의미해진다.
 */
const filtered = computed(() => filterTree(props.tree, filter.value, props.workPackages))
const filterOptionValues = computed(() => filterOptions(props.tree))

function clearFilter() {
  filter.value = emptyFilter()
}

function toggleValue<T>(list: T[], value: T): T[] {
  return list.includes(value) ? list.filter((v) => v !== value) : [...list, value]
}

function toggleMode(value: ModeFilterValue) {
  filter.value.mode = toggleValue(filter.value.mode, value)
}

function toggleBacklog(value: BacklogFilterValue) {
  filter.value.backlog = filter.value.backlog === value ? null : value
}

function toggleProgress(value: ProgressFilterValue) {
  filter.value.progress = toggleValue(filter.value.progress, value)
}

function toggleCheckpointFilter(value: CheckpointFilterValue) {
  filter.value.checkpoint = toggleValue(filter.value.checkpoint, value)
}

/**
 * 필터 중에는 접힘을 무시한다(지시서 2-3) — 조상이 접혀 있으면 일치 행이 화면에 없다. 빈
 * `Set`은 매번 새로 만들지 않는다 — 그러면 `rows`가 불필요하게 다시 계산된다.
 */
const EMPTY_SET = new Set<number>()
const rows = computed(() =>
  flattenTree(filtered.value.nodes, filtering.value ? EMPTY_SET : collapsed.value),
)

/**
 * 담당자·분야 두 열의 판정을 보이는 행마다 한 번씩만 계산해 둔다.
 *
 * 템플릿에서 `responsibleNames(row.node)`를 직접 부르면 한 행에서만 서너 번(길이 확인 · `v-for` ·
 * `title`) 배열을 새로 만들고, 그 값이 매번 새 참조라 렌더 캐시도 듣지 않는다. 여기서 한 번 만들어
 * 맵으로 나눠 준다.
 */
interface RowCells {
  responsible: ResponsibleName[]
  /** 셀 전체의 `title`. 이름이 없으면 null이라 `title` 속성 자체가 붙지 않는다. */
  responsibleHint: string | null
  tags: TagCell
}

const EMPTY_CELLS: RowCells = {
  responsible: [],
  responsibleHint: null,
  tags: { chips: [], rolledUp: false, retained: [] },
}

const cellsByRow = computed(() => {
  const map = new Map<number, RowCells>()
  for (const row of rows.value) {
    const responsible = responsibleNames(row.node)
    map.set(row.node.id, {
      responsible,
      responsibleHint: responsibleTitle(responsible),
      tags: tagCell(row.node),
    })
  }
  return map
})

function cellsFor(node: WbsNode): RowCells {
  return cellsByRow.value.get(node.id) ?? EMPTY_CELLS
}

/**
 * Which Work Packages have their checkpoints expanded — deliberately its own `Set`, not folded
 * into `collapsed`. `collapsed` drives `flattenTree` (WBS hierarchy: which rows exist, `↑↓`
 * order, `←/→`), and a checkpoint is not a WBS child (지시서 4.2) — mixing the two would make a
 * Work Package with checkpoints start behaving like a Summary with children (extra `rows` entries,
 * arrow keys stepping into it) despite CLAUDE.md's "Work Package에는 하위를 둘 수 없다".
 */
const checkpointsOpen = ref(new Set<number>())

function toggleCheckpoints(node: WbsNode) {
  if (checkpointsOpen.value.has(node.id)) {
    checkpointsOpen.value.delete(node.id)
  } else {
    checkpointsOpen.value.add(node.id)
  }
}

function checkpointsFor(nodeId: number) {
  return props.workPackages?.[nodeId]?.checkpoints ?? []
}

function checkpointBadge(nodeId: number): string {
  const wp = props.workPackages?.[nodeId]
  return approvalBadge(wp?.checkpointApproved ?? 0, wp?.checkpointTotal ?? 0)
}

/**
 * Reveals the focused row: its ancestors are expanded, then it is scrolled to and highlighted.
 *
 * The highlight fades on its own rather than staying until the next click — it is there to answer
 * "which row did I come here for?", and a permanent marker would read as a selection the rest of
 * the screen does not honour.
 */
const highlighted = ref<number | null>(null)
const body = ref<HTMLElement | null>(null)
let highlightTimer: ReturnType<typeof setTimeout> | null = null

watch(
  [() => props.focusId, () => props.tree],
  async ([focusId]) => {
    if (focusId === null || focusId === undefined) return
    for (const id of ancestorIds(props.tree, focusId)) {
      collapsed.value.delete(id)
    }
    await nextTick()
    const row = body.value?.querySelector(`[data-row-id="${focusId}"]`)
    row?.scrollIntoView({ behavior: 'smooth', block: 'center' })
    highlighted.value = focusId
    if (highlightTimer) clearTimeout(highlightTimer)
    highlightTimer = setTimeout(() => {
      highlighted.value = null
    }, 4000)
  },
  { immediate: true },
)

onUnmounted(() => {
  if (highlightTimer) clearTimeout(highlightTimer)
  window.removeEventListener('resize', measure)
})

/**
 * The table becomes its own scroll panel (지시서 2-2) so its horizontal scrollbar stays inside
 * the viewport and the header can stick to the panel's own top, instead of the page's. The
 * available height depends on what sits above the panel (commit banner, delay warning line,
 * project picker) — all of that varies by screen state, so it can't be a CSS constant. Measuring
 * `pane`'s own `top` and subtracting from the viewport height lets the browser account for
 * whatever is above without this code needing to know what that is.
 */
const pane = ref<HTMLElement | null>(null)
const maxHeight = ref<string | null>(null)
const BOTTOM_GAP = 16

/**
 * 필터 행이 머리글 행 바로 아래에 고정되려면(지시서 3-3-c, `wb-tree-filter` 계약 H) 필터 행의
 * `top`이 머리글 행의 실제 높이여야 한다. px 상수로 박으면 글꼴이 바뀌는 순간 어긋나므로,
 * 머리글 행(`headRow`)의 높이를 여기서 함께 재 CSS 변수(`--head-h`)로 넘긴다.
 */
const headRow = ref<HTMLElement | null>(null)
const headHeight = ref<number | null>(null)

function measure() {
  const el = pane.value
  if (!el) return
  const available = window.innerHeight - el.getBoundingClientRect().top - BOTTOM_GAP
  // 좁은 창에서 패널이 사라지지 않도록 하한을 둔다.
  maxHeight.value = `${Math.max(320, available)}px`
  if (headRow.value) {
    headHeight.value = headRow.value.getBoundingClientRect().height
  }
}

onMounted(() => {
  measure()
  window.addEventListener('resize', measure)
})

// 지연 경고줄(WbsView.vue)이 생기거나 사라지면 패널의 top이 움직인다.
watch(
  () => props.tree,
  () => nextTick(measure),
)

// 필터 행이 열리거나 닫히면 머리글 높이가 그대로여도 필터 행의 top 기준이 바뀔 수 있으므로 다시 잰다.
watch(filterRowOpen, () => nextTick(measure))

/** Ids on the path from a root down to (but excluding) the target. */
function ancestorIds(nodes: WbsNode[], targetId: number): number[] {
  const walk = (list: WbsNode[], trail: number[]): number[] | null => {
    for (const node of list) {
      if (node.id === targetId) return trail
      const found = walk(node.children, [...trail, node.id])
      if (found) return found
    }
    return null
  }
  return walk(nodes, []) ?? []
}

const dragging = ref<WbsNode | null>(null)
const dropTarget = ref<{ id: number | 'root'; placement: DropPlacement } | null>(null)

/**
 * Persistent row selection — unlike `highlighted` above, this does not fade on its own. It stays
 * until the user picks another row, or until the node is deleted and so is gone from the tree
 * entirely. Moving it under another parent keeps the selection: the check below only asks whether
 * the id is still somewhere in the tree, and a moved row is still the row the user picked.
 * Kept as its own primitive (name + type) because a later feature (keyboard navigation) reuses it.
 */
const selectedId = ref<number | null>(null)

function selectRow(id: number) {
  selectedId.value = id
}

/**
 * A plain click on the row selects it — except when it lands on a button/link inside the row
 * (toggle, 체크포인트 배지, Backlog link, 담당자 link). Those bubble up to the row's own click
 * handler too, and without this guard, toggling a collapsed ancestor's arrow (지시서 3-3 마지막
 * 케이스) silently reassigns the selection to that ancestor instead of leaving the previously
 * selected descendant alone — exactly the case the toolbar depends on getting right, since it now
 * shows and acts on whatever `selectedId` points to. Same guard shape as `onRowDblClick` below.
 */
function onRowClick(event: MouseEvent, node: WbsNode) {
  if ((event.target as HTMLElement).closest('button, a')) return
  selectRow(node.id)
}

watch(
  () => props.tree,
  (tree) => {
    if (selectedId.value !== null && findNode(tree, selectedId.value) === null) {
      selectedId.value = null
    }
  },
)

/**
 * The selected node itself, for the toolbar. Looked up in the whole tree, not `rows` — a
 * collapsed ancestor hides the row from `rows` (`resolveVisibleSelection` below exists precisely
 * because that happens), but the selection is still valid and the toolbar must keep pointing at
 * it (지시서 3-2-a).
 */
const selectedNode = computed(() =>
  selectedId.value === null ? null : findNode(props.tree, selectedId.value),
)

const toolbar = computed(() => toolbarState(selectedNode.value, props.readOnly ?? false))
/** [수정]·[삭제] 둘 다 같은 조건으로 비활성화되므로 title도 하나로 공유한다. */
const editRemoveHint = computed(() => actionHint(selectedNode.value, props.readOnly ?? false))

/**
 * 드래그 앤 드롭의 키보드·터치 대안(WCAG 2.5.7·2.1.1) — 위로·아래로·들여쓰기·내어쓰기 네
 * 버튼의 활성 여부와 title. 판정은 `wbsMove.ts`에 있다. 필터 중에는 드래그도 막혀 있으므로
 * (`filtering`, 아래 `onDragStart` 참고) 이 네 버튼도 같은 이유로 같은 문구를 보여준다.
 */
const moveToolbar = computed(() => {
  if (filtering.value) {
    const hint = legendText.value
    return {
      up: false,
      down: false,
      indent: false,
      outdent: false,
      upHint: hint,
      downHint: hint,
      indentHint: hint,
      outdentHint: hint,
    }
  }
  return computeMoveState(props.tree, selectedId.value, props.readOnly ?? false)
})

/** 네 버튼 공통 가드 — 필터 중이거나 읽기 전용이면 판정 함수를 부르지도 않는다. */
function canMove(): boolean {
  return !props.readOnly && !filtering.value && selectedId.value !== null
}

function doMoveUp() {
  if (!canMove()) return
  const input = moveUpInput(props.tree, selectedId.value!)
  if (input) emit('move', selectedId.value!, input)
}

function doMoveDown() {
  if (!canMove()) return
  const input = moveDownInput(props.tree, selectedId.value!)
  if (input) emit('move', selectedId.value!, input)
}

function doIndent() {
  if (!canMove()) return
  const input = indentInput(props.tree, selectedId.value!)
  if (input) emit('move', selectedId.value!, input)
}

function doOutdent() {
  if (!canMove()) return
  const input = outdentInput(props.tree, selectedId.value!)
  if (input) emit('move', selectedId.value!, input)
}

/**
 * Keyboard navigation lands on `selectedId`, which may be off-screen (a long tree, a row near
 * the container's edge) — scroll it into view without stealing the smooth/centered treatment
 * `focusId` uses above, since every arrow-key press would make that feel sluggish.
 */
watch(selectedId, async (id) => {
  if (id === null) return
  await nextTick()
  const row = body.value?.querySelector(`[data-row-id="${id}"]`)
  row?.scrollIntoView({ block: 'nearest' })
})

/**
 * Pulls a hidden selection up to its nearest visible ancestor and returns it.
 *
 * Collapsing an ancestor with the mouse leaves `selectedId` pointing at a row that is no longer
 * rendered. Without this, each arrow key improvised its own answer (Up/Down jumped to the first
 * row on `findIndex` returning -1, Left/Right did nothing at all). Following the selection up to
 * the point that was collapsed keeps all four keys working from the same visible row.
 */
function resolveVisibleSelection(): number | null {
  if (selectedId.value === null) return null
  let current: number | null = selectedId.value
  while (current !== null && !rows.value.some((row) => row.node.id === current)) {
    current = findParent(props.tree, current)?.id ?? null
  }
  selectedId.value = current
  return current
}

/** Moves the selection to the next/previous visible row; clamps at the first/last row. */
function moveSelection(delta: number) {
  const list = rows.value
  if (list.length === 0) return
  if (selectedId.value === null) {
    selectRow(list[0].node.id)
    return
  }
  const currentIndex = list.findIndex((row) => row.node.id === selectedId.value)
  if (currentIndex === -1) {
    selectRow(list[0].node.id)
    return
  }
  const nextIndex = currentIndex + delta
  if (nextIndex < 0 || nextIndex >= list.length) return
  selectRow(list[nextIndex].node.id)
}

/** → : expand a collapsed parent in place, or step into an already-expanded one's first child. */
function expandOrDescend() {
  const id = selectedId.value
  if (id === null) return
  const row = rows.value.find((r) => r.node.id === id)
  if (!row || row.node.children.length === 0) return
  if (collapsed.value.has(id)) {
    collapsed.value.delete(id)
  } else {
    selectRow(row.node.children[0].id)
  }
}

/** ← : collapse an expanded parent in place, or step out to its parent. */
function collapseOrAscend() {
  const id = selectedId.value
  if (id === null) return
  const row = rows.value.find((r) => r.node.id === id)
  if (!row) return
  if (row.node.children.length > 0 && !collapsed.value.has(id)) {
    collapsed.value.add(id)
    return
  }
  const parent = findParent(props.tree, id)
  if (parent) selectRow(parent.id)
}

const ARROW_KEYS = ['ArrowDown', 'ArrowUp', 'ArrowRight', 'ArrowLeft']

/**
 * One listener on the tbody rather than one per row (same reasoning as the Gantt tooltip: a
 * per-row listener would still need to know about neighbouring rows for Up/Down anyway).
 */
function onKeydown(event: KeyboardEvent) {
  if (!ARROW_KEYS.includes(event.key)) return
  /*
   * Alt+화살표는 드래그 앤 드롭의 키보드 대안이다(WCAG 2.5.7·2.1.1, `wbsMove.ts`) — 일반
   * 화살표(탐색)와 겹치지 않도록 수정자 하나를 붙였다. ↑/↓는 형제 사이 순서, →/←는 들여쓰기/
   * 내어쓰기다(오른쪽 = 더 깊이, 왼쪽 = 한 단계 위로 — 들여쓰기 방향과 같다).
   */
  if (event.altKey) {
    if (props.readOnly || filtering.value) return
    event.preventDefault()
    switch (event.key) {
      case 'ArrowUp':
        doMoveUp()
        break
      case 'ArrowDown':
        doMoveDown()
        break
      case 'ArrowRight':
        doIndent()
        break
      case 'ArrowLeft':
        doOutdent()
        break
    }
    return
  }
  event.preventDefault()
  resolveVisibleSelection()
  switch (event.key) {
    case 'ArrowDown':
      moveSelection(1)
      break
    case 'ArrowUp':
      moveSelection(-1)
      break
    case 'ArrowRight':
      expandOrDescend()
      break
    case 'ArrowLeft':
      collapseOrAscend()
      break
  }
}

function toggle(node: WbsNode) {
  if (collapsed.value.has(node.id)) {
    collapsed.value.delete(node.id)
  } else {
    collapsed.value.add(node.id)
  }
}

/**
 * Double-clicking a row opens its edit dialog — but not when the double-click landed on a
 * button/link inside the row (toggle, 체크포인트 배지, Backlog link, 담당자 link). Those already
 * have their own single-click behaviour; double-clicking one bubbles two clicks up to the row and
 * would otherwise open edit *in addition to* whatever the control itself just did (e.g. toggling
 * the collapse arrow twice quickly reopened the edit dialog on top of the restored collapse
 * state). [하위 추가]·[수정]·[삭제]는 더 이상 행 안에 없다(툴바로 옮겼다) — 이 가드는 남아 있는
 * 나머지 컨트롤을 위해 그대로 둔다.
 */
function onRowDblClick(event: MouseEvent, node: WbsNode) {
  if (props.readOnly) return
  if ((event.target as HTMLElement).closest('button, a')) return
  emit('edit', node)
}

function onDragStart(event: DragEvent, node: WbsNode) {
  // 속성(`:draggable`)만 믿지 않는다 — 필터가 걸린 동안은 여기서도 끝낸다(지시서 2-2, F).
  if (filtering.value) return
  if (props.readOnly) return
  dragging.value = node
  if (event.dataTransfer) {
    // Firefox refuses to start a drag unless some payload is set.
    event.dataTransfer.setData('text/plain', String(node.id))
    event.dataTransfer.effectAllowed = 'move'
  }
}

function onDragEnd() {
  dragging.value = null
  dropTarget.value = null
}

/**
 * Dropping onto the dragged node itself, or anywhere inside its own subtree, would create a
 * cycle — for `before`/`after` too, since the new parent would then sit inside that subtree.
 */
function canDrop(target: WbsNode): boolean {
  const dragged = dragging.value
  if (!dragged) return false
  return target.id !== dragged.id && !containsDescendant(dragged, target.id)
}

/**
 * A Work Package is the lowest management unit, so nothing can be dropped *into* one — the server
 * rejects it too. Reordering next to it (`before`/`after`) is fine: that only changes sibling
 * order, and the new parent is the Work Package's parent, not the Work Package.
 */
function canDropAt(target: WbsNode, placement: DropPlacement): boolean {
  if (!canDrop(target)) return false
  return placement !== 'inside' || target.nodeType !== 'WORK_PACKAGE'
}

/** Top and bottom edges reorder among siblings; the middle re-parents into the row. */
function placementFor(event: DragEvent, element: HTMLElement): DropPlacement {
  const rect = element.getBoundingClientRect()
  const ratio = (event.clientY - rect.top) / rect.height
  if (ratio < 0.25) return 'before'
  if (ratio > 0.75) return 'after'
  return 'inside'
}

function onDragOver(event: DragEvent, row: WbsRow) {
  const placement = placementFor(event, event.currentTarget as HTMLElement)
  if (!canDropAt(row.node, placement)) {
    dropTarget.value = null
    return
  }
  event.preventDefault()
  if (event.dataTransfer) event.dataTransfer.dropEffect = 'move'
  dropTarget.value = { id: row.node.id, placement }
}

function onDrop(event: DragEvent, row: WbsRow) {
  const dragged = dragging.value
  const placement = placementFor(event, event.currentTarget as HTMLElement)
  if (!dragged || !canDropAt(row.node, placement)) return
  event.preventDefault()

  const input: WbsMoveInput =
    placement === 'inside'
      ? {
          parentId: row.node.id,
          position: resolveDropPosition(
            dragged.id,
            row.node.children,
            row.node.children.length,
            'before',
          ),
        }
      : {
          parentId: row.node.parentId,
          position: resolveDropPosition(dragged.id, row.siblings, row.index, placement),
        }

  emit('move', dragged.id, input)
  onDragEnd()
}

function onDragOverRoot(event: DragEvent) {
  if (!dragging.value) return
  event.preventDefault()
  if (event.dataTransfer) event.dataTransfer.dropEffect = 'move'
  dropTarget.value = { id: 'root', placement: 'inside' }
}

function onDropRoot(event: DragEvent) {
  const dragged = dragging.value
  if (!dragged) return
  event.preventDefault()
  emit('move', dragged.id, {
    parentId: null,
    position: resolveDropPosition(dragged.id, props.tree, props.tree.length, 'before'),
  })
  onDragEnd()
}

/**
 * 열 표시·숨김·고정(지시서 `wbs-tree-columns`). 판정 자체는 `wbsColumns.ts`가 답하고, 여기서는
 * `columnPrefs`(사람 단위로 하나, 프로젝트를 옮겨도 유지)를 그 판정에 흘려 넣기만 한다.
 */
const columnSettingsOpen = ref(false)

const visibleColumnKeys = computed(
  () => new Set(visibleColumns(columnPrefs.value).map((column) => column.key)),
)

function shows(key: WbsColumnKey): boolean {
  return visibleColumnKeys.value.has(key)
}

const visibleColumnCount = computed(() => visibleColumns(columnPrefs.value).length)

const pinnedOffsets = computed(() => pinOffsets(columnPrefs.value))

function isPinned(key: WbsColumnKey): boolean {
  return pinnedOffsets.value[key] !== undefined
}

/** 고정된 열 중 가장 오른쪽(오프셋이 가장 큰) 열 — 그 열에만 경계선을 그어 고정 영역의 끝을 보인다. */
const lastPinnedKey = computed<WbsColumnKey | null>(() => {
  const entries = Object.entries(pinnedOffsets.value) as [WbsColumnKey, number][]
  if (entries.length === 0) return null
  return entries.reduce((furthest, entry) => (entry[1] > furthest[1] ? entry : furthest))[0]
})

function isPinnedEdge(key: WbsColumnKey): boolean {
  return lastPinnedKey.value === key
}

/**
 * z-index 층이 셋이다: 고정된 본문 셀(1) < 고정 머리글(이미 있는 `thead th`의 2) < 고정 열과 고정
 * 머리글이 겹치는 칸(3). `header`를 받아 어느 층인지 가른다.
 */
function pinStyle(key: WbsColumnKey, header: boolean) {
  const left = pinnedOffsets.value[key]
  if (left === undefined) return undefined
  return { position: 'sticky' as const, left: `${left}rem`, zIndex: header ? 3 : 1 }
}

/** 그 열에 조건이 걸려 있는가 — 머리글 표시(계약 B)와 숨긴 열 안내(계약 D)가 함께 쓴다. */
function columnFiltered(key: WbsColumnKey): boolean {
  switch (key) {
    case 'code':
      return filter.value.code.trim() !== ''
    case 'name':
      return filter.value.name.trim() !== ''
    case 'mode':
      return filter.value.mode.length > 0
    case 'backlog':
      return filter.value.backlog !== null
    case 'progress':
      return filter.value.progress.length > 0
    case 'checkpoint':
      return filter.value.checkpoint.length > 0
    case 'owner':
      return filter.value.owners.length > 0
    case 'tags':
      return filter.value.tagIds.length > 0
    case 'startDate':
    case 'endDate':
      return false
  }
}

/**
 * 숨긴 열에도 조건이 남아 있으면 알린다(팀 리드 결정 2) — 조용히 유지만 하면 사용자가 모르게
 * 결과가 좁아진 채로 남는다.
 */
const hiddenFilteredColumns = computed(() =>
  WBS_COLUMNS.filter((column) => !shows(column.key) && columnFiltered(column.key)).map(
    (column) => column.label,
  ),
)

const headHeightPx = computed(() => (headHeight.value ? `${headHeight.value}px` : '0px'))

const legendText = computed(() =>
  filtering.value
    ? '필터가 걸려 있는 동안에는 순서를 바꿀 수 없습니다. 이동은 필터를 해제한 뒤에 하세요.'
    : null,
)

function rowClass(row: WbsRow) {
  const target = dropTarget.value
  return {
    dragging: dragging.value?.id === row.node.id,
    'drop-before': target?.id === row.node.id && target.placement === 'before',
    'drop-after': target?.id === row.node.id && target.placement === 'after',
    'drop-inside': target?.id === row.node.id && target.placement === 'inside',
    focused: highlighted.value === row.node.id,
    selected: selectedId.value === row.node.id,
    context: filtering.value && !filtered.value.matchIds.has(row.node.id),
  }
}
</script>

<template>
  <div v-if="tree.length === 0" class="empty">
    등록된 WBS 항목이 없습니다. 위 ＋ 최상위 항목 추가로 첫 항목을 만들어 보세요.
  </div>

  <template v-else>
    <!--
      선택된 행의 동작(하위 추가·수정·삭제)을 모아 두는 자리다 — 예전에는 행마다 버튼 세 개가
      있어 열 11개 중 맨 오른쪽에 있었다(지시서 1-1). selectedId는 이미 있으므로 여기서는
      읽기만 한다.
    -->
    <div class="tree-toolbar">
      <button
        type="button"
        data-action="add-child"
        :disabled="!toolbar.canAddChild"
        :title="toolbar.addChildHint ?? '하위 항목 추가'"
        @click="selectedNode && emit('addChild', selectedNode)"
      >하위 추가</button>
      <button
        type="button"
        data-action="edit"
        :disabled="!toolbar.canEdit"
        :title="editRemoveHint ?? undefined"
        @click="selectedNode && emit('edit', selectedNode)"
      >수정</button>
      <button
        type="button"
        class="danger"
        data-action="remove"
        :disabled="!toolbar.canRemove"
        :title="editRemoveHint ?? undefined"
        @click="selectedNode && emit('remove', selectedNode)"
      >삭제</button>
      <!--
        드래그 앤 드롭의 키보드·터치 대안이다(WCAG 2.5.7·2.1.1) — 판정은 `wbsMove.ts`. 같은
        동작을 Alt+↑/↓/←/→로도 걸어 뒀다(`onKeydown`) — 단축키만 두면 발견할 수 없고, 버튼만
        두면 키보드 사용자가 매번 툴바까지 Tab 해야 한다.
      -->
      <button
        type="button"
        data-action="move-up"
        :disabled="!moveToolbar.up"
        :title="moveToolbar.upHint ?? '위로 이동 (Alt+↑)'"
        @click="doMoveUp"
      >위로</button>
      <button
        type="button"
        data-action="move-down"
        :disabled="!moveToolbar.down"
        :title="moveToolbar.downHint ?? '아래로 이동 (Alt+↓)'"
        @click="doMoveDown"
      >아래로</button>
      <button
        type="button"
        data-action="indent"
        :disabled="!moveToolbar.indent"
        :title="moveToolbar.indentHint ?? '들여쓰기 (Alt+→)'"
        @click="doIndent"
      >들여쓰기</button>
      <button
        type="button"
        data-action="outdent"
        :disabled="!moveToolbar.outdent"
        :title="moveToolbar.outdentHint ?? '내어쓰기 (Alt+←)'"
        @click="doOutdent"
      >내어쓰기</button>
      <span class="selected-label">{{
        selectedNode ? `선택: ${selectedNode.code} ${selectedNode.name}` : '행을 고르세요'
      }}</span>
      <!--
        선택과 무관하므로 항상 활성이다 — 커밋 조회 중에도 활성이다(열 구성은 쓰기가 아니다,
        지시서 3-4-g).
      -->
      <button
        type="button"
        data-action="columns"
        @click="columnSettingsOpen = true"
      >열 설정</button>
      <!--
        선택·readOnly와 무관하게 항상 활성이다 — 필터는 읽기다(지시서 `wbs-tree-filter` 계약 C).
        조건이 있으면 점(`.active`)으로 알린다 — 행을 닫아도 조건은 남아 있으므로, 행이 닫힌
        상태에서도 "지금 걸려 있다"를 알 방법이 있어야 한다.
      -->
      <button
        type="button"
        data-action="filter"
        :class="{ active: filtering }"
        :aria-expanded="filterRowOpen"
        @click="filterRowOpen = !filterRowOpen"
      >필터</button>
    </div>

    <!--
      계약 D: 조건이 하나라도 있으면 [필터 해제]와 함께 일치·문맥 건수, 숨긴 열 안내를 보인다.
      건수는 입력할 때마다 바뀌므로 `aria-live="polite"`로 알려 스크린리더 사용자도 타이핑
      결과를 들을 수 있게 한다 — 즉각 주의가 필요한 오류가 아니라 assertive 는 쓰지 않는다.
    -->
    <div v-if="filtering" class="filter-banner" aria-live="polite">
      필터 적용 중 — 일치 {{ filtered.matchIds.size }}건, 문맥 {{ rows.length - filtered.matchIds.size }}행
      <span v-if="hiddenFilteredColumns.length > 0" class="hidden-hint">
        숨겨진 열에 조건이 있습니다 ({{ hiddenFilteredColumns.join(', ') }})
      </span>
      <button type="button" data-action="clear-filter" @click="clearFilter">필터 해제</button>
    </div>

    <WbsColumnSettings v-if="columnSettingsOpen" @close="columnSettingsOpen = false" />

    <!--
      표를 뷰포트 높이에 맞춘 스크롤 패널로 만든다(지시서 2-2) — 그러면 가로 스크롤바가 항상
      화면 안에 있고, 머리글 고정이 공짜로 따라온다. maxHeight는 measure()가 한 번 잰 값이고,
      드롭존·설명문은 패널 안에서 늘어나지 않는 flex 아이템으로 항상 아래에 붙는다.
    -->
    <div ref="pane" class="tree-pane" :style="{ maxHeight: maxHeight ?? undefined }">
    <div class="table-scroll tree-scroll">
      <table
        class="wbs-tree"
        role="grid"
        :class="{ 'pin-name': columnPrefs.pin === 'name', filtering }"
        :style="{ '--head-h': headHeightPx }"
      >
        <thead>
          <tr ref="headRow">
            <th
              v-if="shows('code')"
              class="code"
              :class="{ pinned: isPinned('code'), 'pinned-edge': isPinnedEdge('code'), filtered: columnFiltered('code') }"
              :style="pinStyle('code', true)"
            >WBS</th>
            <th
              v-if="shows('name')"
              class="col-name"
              :class="{ pinned: isPinned('name'), 'pinned-edge': isPinnedEdge('name'), filtered: columnFiltered('name') }"
              :style="pinStyle('name', true)"
            >업무명</th>
            <th v-if="shows('startDate')" class="date">시작일</th>
            <th v-if="shows('endDate')" class="date">종료일</th>
            <th v-if="shows('mode')" class="mode" :class="{ filtered: columnFiltered('mode') }">실행 방식</th>
            <th v-if="shows('backlog')" class="backlog" :class="{ filtered: columnFiltered('backlog') }">연결 Backlog</th>
            <th v-if="shows('progress')" class="progress" :class="{ filtered: columnFiltered('progress') }">진척</th>
            <th v-if="shows('checkpoint')" class="checkpoint" :class="{ filtered: columnFiltered('checkpoint') }">체크포인트</th>
            <th v-if="shows('owner')" class="owner" :class="{ filtered: columnFiltered('owner') }">담당자</th>
            <th v-if="shows('tags')" class="tags" :class="{ filtered: columnFiltered('tags') }">분야</th>
          </tr>
          <!--
            머리글 바로 아래 줄 — 열마다 그 종류에 맞는 컨트롤을 하나씩 둔다(지시서 2-6). 보이는
            열만 그린다(`shows`) — 숨긴 열은 칸도 없다. 같은 `<thead>` 안에 두고 `top`을
            `--head-h`(위에서 잰 머리글 실제 높이)로 줘서 두 줄이 함께 고정된다(계약 H).
          -->
          <tr v-if="filterRowOpen" class="filter-row">
            <td
              v-if="shows('code')"
              data-filter="code"
              :class="{ pinned: isPinned('code'), 'pinned-edge': isPinnedEdge('code') }"
              :style="pinStyle('code', true)"
            ><input v-model="filter.code" type="search" aria-label="WBS 코드 필터" placeholder="예: 1.2" /></td>
            <td
              v-if="shows('name')"
              data-filter="name"
              :class="{ pinned: isPinned('name'), 'pinned-edge': isPinnedEdge('name') }"
              :style="pinStyle('name', true)"
            ><input v-model="filter.name" type="search" aria-label="업무명 필터" placeholder="업무명 · 설명" /></td>
            <td v-if="shows('startDate')" data-filter="startDate"></td>
            <td v-if="shows('endDate')" data-filter="endDate"></td>
            <td v-if="shows('mode')" data-filter="mode" class="chip-cell">
              <button
                v-for="value in MODE_FILTER_ORDER"
                :key="value"
                type="button"
                class="chip"
                :class="{ active: filter.mode.includes(value) }"
                :aria-pressed="filter.mode.includes(value)"
                :data-value="value"
                @click="toggleMode(value)"
              >{{ MODE_FILTER_LABELS[value] }}</button>
            </td>
            <td v-if="shows('backlog')" data-filter="backlog" class="chip-cell">
              <button
                v-for="value in BACKLOG_FILTER_ORDER"
                :key="value"
                type="button"
                class="chip"
                :class="{ active: filter.backlog === value }"
                :aria-pressed="filter.backlog === value"
                :data-value="value"
                @click="toggleBacklog(value)"
              >{{ BACKLOG_FILTER_LABELS[value] }}</button>
            </td>
            <td v-if="shows('progress')" data-filter="progress" class="chip-cell">
              <button
                v-for="value in PROGRESS_FILTER_ORDER"
                :key="value"
                type="button"
                class="chip"
                :class="{ active: filter.progress.includes(value) }"
                :aria-pressed="filter.progress.includes(value)"
                :data-value="value"
                @click="toggleProgress(value)"
              >{{ PROGRESS_FILTER_LABELS[value] }}</button>
            </td>
            <td v-if="shows('checkpoint')" data-filter="checkpoint" class="chip-cell">
              <button
                v-for="value in CHECKPOINT_FILTER_ORDER"
                :key="value"
                type="button"
                class="chip"
                :class="{ active: filter.checkpoint.includes(value) }"
                :aria-pressed="filter.checkpoint.includes(value)"
                :data-value="value"
                @click="toggleCheckpointFilter(value)"
              >{{ CHECKPOINT_FILTER_LABELS[value] }}</button>
            </td>
            <td v-if="shows('owner')" data-filter="owner">
              <select v-model="filter.owners" multiple aria-label="담당자 필터">
                <option v-for="name in filterOptionValues.owners" :key="name" :value="name">{{ name }}</option>
              </select>
            </td>
            <td v-if="shows('tags')" data-filter="tags">
              <select v-model="filter.tagIds" multiple aria-label="분야 필터">
                <option v-for="tag in filterOptionValues.tags" :key="tag.id" :value="tag.id">{{ tag.name }}</option>
              </select>
            </td>
          </tr>
        </thead>
        <!--
          커스텀 grid 위젯 시맨틱(WCAG 4.1.2) — `role="grid"`(table)의 자식이라 `tr`/`td`는
          HTML-ARIA 매핑으로 row/gridcell 역할을 자동으로 받는다. 화살표 키로 옮긴 선택은
          `.selected` 클래스뿐 아니라 `aria-activedescendant`로도 알려, 스크린리더 사용자가 지금
          어느 행이 선택됐는지 알 수 있게 한다. DOM 포커스는 여전히 tbody에 머문다(roving
          tabindex를 행마다 두지 않는다) — 화살표 키 핸들러가 이미 tbody 하나에 있다.
        -->
        <tbody
          ref="body"
          tabindex="0"
          :aria-activedescendant="selectedId !== null ? `wbs-row-${selectedId}` : undefined"
          @keydown="onKeydown"
        >
          <template v-for="row in rows" :key="row.node.id">
          <tr
            :id="`wbs-row-${row.node.id}`"
            :data-row-id="row.node.id"
            :class="rowClass(row)"
            :aria-selected="selectedId === row.node.id"
            :draggable="!readOnly && !filtering"
            @click="onRowClick($event, row.node)"
            @dblclick="onRowDblClick($event, row.node)"
            @dragstart="onDragStart($event, row.node)"
            @dragend="onDragEnd"
            @dragover="onDragOver($event, row)"
            @drop="onDrop($event, row)"
          >
            <td
              v-if="shows('code')"
              class="code"
              :class="{ pinned: isPinned('code'), 'pinned-edge': isPinnedEdge('code') }"
              :style="pinStyle('code', false)"
            >{{ row.node.code }}</td>
            <td
              v-if="shows('name')"
              class="col-name"
              :class="{ pinned: isPinned('name'), 'pinned-edge': isPinnedEdge('name') }"
              :style="pinStyle('name', false)"
            >
              <div class="name" :style="{ paddingLeft: `${(row.node.level - 1) * 1.25}rem` }">
                <button
                  v-if="row.node.children.length > 0"
                  class="toggle"
                  type="button"
                  :aria-label="collapsed.has(row.node.id) ? '펼치기' : '접기'"
                  :aria-expanded="!collapsed.has(row.node.id)"
                  @click="toggle(row.node)"
                >
                  {{ collapsed.has(row.node.id) ? '▶' : '▼' }}
                </button>
                <span v-else class="toggle-spacer"></span>
                <span :class="{ summary: row.node.summary }">{{ row.node.name }}</span>
                <!--
                  지연/지연 위험만 표시한다. WBS는 구조를 다루는 화면이라 모든 행에 상태를 달면
                  소음이 되고, 전체 상태는 간트에서 본다.
                -->
                <span
                  v-if="needsAttention(row.node)"
                  class="delay-badge"
                  :data-status="row.node.delayStatus"
                  :title="delayDescription(row.node)"
                >{{ delayBadge(row.node) }}</span>
                <span
                  v-if="row.node.description"
                  class="desc cell-clip"
                  :title="row.node.description"
                >{{ row.node.description }}</span>
              </div>
            </td>
            <td v-if="shows('startDate')" class="date">{{ row.node.startDate ?? '-' }}</td>
            <td v-if="shows('endDate')" class="date">{{ row.node.endDate ?? '-' }}</td>
            <!--
              실행 방식은 최하위 Work Package의 것이다. 하위가 있는 항목은 자기 값을 쓰지 않고
              하위의 요약을 보여준다 (설계 §5).
            -->
            <td v-if="shows('mode')" class="mode">
              <template v-if="row.node.children.length > 0">
                <span class="mode-summary">
                  {{ executionModeSummaryText(row.node.executionModeSummary) || '-' }}
                </span>
                <!--
                  전환 전 값이 남아 있는 경우. 지우지 않는 것이 의도이므로, 지금 적용되는 값이
                  아니라는 사실만 따로 알린다.
                -->
                <span
                  v-if="row.node.executionMode"
                  class="retained"
                  :title="`구분을 Work Package로 되돌리면 다시 적용됩니다.`"
                >보관 {{ executionModeLabel(row.node.executionMode) }}</span>
              </template>
              <span
                v-else
                :class="{ unspecified: !row.node.executionMode }"
              >{{ executionModeLabel(row.node.executionMode) }}</span>
            </td>
            <!--
              연결 Backlog 수 (설계 §4.3). 눌러서 그 Work Package로 필터링한 Backlog로 이동한다.
              상위 행의 숫자는 하위 전체를 합친 것이라 링크 대상이 하나가 아니므로 글자만 보여준다.
            -->
            <td v-if="shows('backlog')" class="backlog">
              <template v-if="row.node.backlogSummary">
                <RouterLink
                  v-if="row.node.nodeType === 'WORK_PACKAGE'"
                  :to="{ path: '/backlog', query: { wbs: String(row.node.id) } }"
                  :title="`'${row.node.name}'에 귀속된 Backlog 보기`"
                >{{ backlogSummaryText(row.node.backlogSummary) }}</RouterLink>
                <span v-else class="rolled-up" title="하위 항목들의 합계입니다.">
                  {{ backlogSummaryText(row.node.backlogSummary) }}
                </span>
              </template>
              <span v-else class="none">-</span>
            </td>
            <!--
              공통 집계 결과를 그대로 보여준다 (지시서 5-C). 산정 전은 0%가 아니므로 막대를 그리지
              않고 그렇게 적는다.
            -->
            <td v-if="shows('progress')" class="progress">
              <div
                class="bar"
                role="progressbar"
                aria-valuemin="0"
                aria-valuemax="100"
                :aria-valuenow="row.node.computedProgress ?? undefined"
                :aria-label="progressText(row.node.computedProgress)"
                :title="row.node.progressNote ?? (row.node.progressBasis ? PROGRESS_BASIS_HINTS[row.node.progressBasis] : '')"
              >
                <div class="fill" :style="{ width: progressBarWidth(row.node.computedProgress) }"></div>
              </div>
              <span class="pct" :class="{ none: row.node.computedProgress === null }">
                {{ progressText(row.node.computedProgress) }}
              </span>
              <span
                v-if="row.node.progressBasis && row.node.progressBasis !== 'MANUAL'"
                class="basis"
              >{{ PROGRESS_BASIS_LABELS[row.node.progressBasis] }}</span>
              <span v-if="row.node.progressIncomplete" class="incomplete" title="일부 하위가 아직 산정 전입니다.">불완전</span>
              <span v-if="row.node.acceptancePending" class="pending">
                {{ ACCEPTANCE_STATUS_LABELS.PENDING }}
              </span>
            </td>
            <!--
              WATERFALL·HYBRID Work Package만 펼칠 수 있다 (지시서 4.1). 접힌 상태에서도
              `승인 N/M` 배지로 상태를 보여주고, 그 배지 자체가 펼침 버튼이다 — Summary의
              삼각형(▼/▶)과 모양을 다르게 해 "하위가 있다"로 오인되지 않게 한다.
            -->
            <td v-if="shows('checkpoint')" class="checkpoint">
              <button
                v-if="supportsCheckpoints(row.node)"
                type="button"
                class="cp-badge"
                :class="{ open: checkpointsOpen.has(row.node.id) }"
                :aria-expanded="checkpointsOpen.has(row.node.id)"
                :title="checkpointsOpen.has(row.node.id) ? '체크포인트 접기' : '체크포인트 펼치기'"
                @click="toggleCheckpoints(row.node)"
              >{{ checkpointBadge(row.node.id) }}</button>
              <span v-else class="none">-</span>
            </td>
            <!--
              담당자는 RACI의 Responsible을 그대로 읽는다 — 새 컬럼이 아니라 같은 사실이다. 여기서
              바꾸지 않는 것이 의도이고(쓰기 경로가 둘이면 RaciValidator 규칙을 두 벌 관리하게 된다),
              셀 전체를 RACI로 가는 링크로 만들어 그 동선을 알린다.
            -->
            <td v-if="shows('owner')" class="owner">
              <RouterLink
                v-if="cellsFor(row.node).responsible.length > 0"
                to="/raci"
                class="who-link cell-clip"
                :title="cellsFor(row.node).responsibleHint ?? undefined"
              >
                <template
                  v-for="(who, index) in cellsFor(row.node).responsible"
                  :key="who.memberId"
                ><span v-if="index > 0" class="sep">, </span><span
                  class="who"
                  :class="{ inherited: who.inherited }"
                  :title="who.inherited ? '상위 단계에서 물려받음' : undefined"
                >{{ who.name }}</span></template>
              </RouterLink>
              <span v-else class="none">-</span>
            </td>
            <!--
              분야(태그). 실행 방식 열과 같은 규칙이다 — 자식이 있으면 자기 값 대신 하위 요약을
              보여주고, 전환 전부터 들고 있던 자기 태그는 "보관"으로만 알린다.
            -->
            <td v-if="shows('tags')" class="tags">
              <span
                v-if="cellsFor(row.node).tags.chips.length > 0"
                class="chips cell-clip"
                :title="
                  cellsFor(row.node).tags.rolledUp
                    ? `하위 항목들의 분야입니다 — ${cellsFor(row.node).tags.chips.map((tag) => tag.name).join(', ')}`
                    : cellsFor(row.node).tags.chips.map((tag) => tag.name).join(', ')
                "
              >
                <TagChip
                  v-for="tag in cellsFor(row.node).tags.chips"
                  :key="tag.id"
                  :tag="tag"
                  :muted="cellsFor(row.node).tags.rolledUp"
                />
              </span>
              <span v-else class="none">-</span>
              <span
                v-if="cellsFor(row.node).tags.retained.length > 0"
                class="retained"
                title="구분을 Work Package로 되돌리면 다시 적용됩니다."
              >보관 {{ cellsFor(row.node).tags.retained.length }}</span>
            </td>
          </tr>
          <!--
            체크포인트 행 — `rows`(flattenTree)에 없는 별도 <tr>이다. WBS 코드가 없고
            (`data-row-id` 자체를 주지 않는다), `↑↓`가 지나가는 목록에도 없으며, 드래그·드롭
            핸들러도 없다. `CheckpointList`가 목록·추가·수정·삭제·승인을 모두 처리하므로 여기서는
            펼침 상태만 관리한다(지시서 4.1-4.2).
          -->
          <tr
            v-if="checkpointsOpen.has(row.node.id) && projectId != null"
            class="checkpoint-row"
          >
            <!--
              열 수와 반드시 같아야 한다 — 계산값으로 둔다(지시서 `wbs-tree-columns` 3-4-d). 숨긴
              열이 있으면 `<thead th>` 수도 그만큼 줄어드는데 여기 숫자를 고정해 두면 어긋난다.
            -->
            <td :colspan="visibleColumnCount">
              <CheckpointList
                :project-id="projectId"
                :wbs-item-id="row.node.id"
                :checkpoints="checkpointsFor(row.node.id)"
                :editable="!readOnly"
              />
            </td>
          </tr>
          </template>
        </tbody>
      </table>
    </div>

    <div
      v-if="!readOnly && !filtering"
      class="root-dropzone"
      :class="{ active: dropTarget?.id === 'root' }"
      @dragover="onDragOverRoot"
      @drop="onDropRoot"
    >
      여기로 끌어다 놓으면 최상위 항목이 됩니다
    </div>

    <p v-if="!filtering" class="legend">
      행을 드래그해 순서를 바꾸거나 다른 항목의 <strong>가운데</strong>에 놓아 하위 항목으로 만들 수 있습니다.
      위/아래 가장자리에 놓으면 같은 계층에서 순서만 바뀝니다.
    </p>
    <p v-else class="legend">{{ legendText }}</p>
    </div>
  </template>
</template>

<style scoped>
.wbs-tree {
  width: 100%;
  border-collapse: collapse;
}

th,
td {
  text-align: left;
  padding: 0.5rem 0.6rem;
  border-bottom: 1px solid var(--border-soft);
  border-top: 2px solid transparent;
  font-size: 0.9rem;
  /* 값이 세로로 접히지 않게 한다 — 넘치면 .table-scroll 이 가로로 넘긴다. */
  white-space: nowrap;
}

th {
  font-size: 0.8rem;
  color: var(--text-dim);
  font-weight: 600;
}

/*
 * 표 자체가 스크롤 패널이 되므로(.tree-pane) 머리글을 그 패널의 top에 고정한다 — 세로로
 * 스크롤해도 어느 열을 보고 있는지 알 수 있어야 한다(지시서 2-2). 배경이 불투명하지 않으면
 * 스크롤된 행이 머리글을 통과해 보인다.
 *
 * --surface가 아니라 --surface-alt를 쓰는 이유: 이 표는 카드 패널로 감싸여 있지 않고 페이지
 * 배경(--bg) 바로 위에 놓인다. 라이트 모드는 --bg와 --surface가 같은 색(#fff)이라 차이가
 * 없지만, 다크 모드는 --surface(#1a1d23)가 --bg(#14161a)보다 밝아 머리글만 도드라진 사각형으로
 * 보인다 — RaciMatrix.vue:270-277가 같은 이유로 이미 --surface-alt를 골랐다.
 */
thead th {
  position: sticky;
  top: 0;
  z-index: 2;
  background: var(--surface-alt);
  /* sticky 요소의 border-bottom은 스크롤 중 겹쳐 보일 수 있어 box-shadow로 대신한다. */
  border-bottom: none;
  box-shadow: inset 0 -1px 0 var(--border);
}

/* 조건이 걸린 열의 머리글 — 필터 행을 접어도 왜 행이 적은지 알 수 있어야 한다. */
th.filtered {
  color: var(--accent);
}

/*
 * 필터 행(지시서 `wbs-tree-filter` 계약 H) — 머리글과 같은 `<thead>` 안의 두 번째 sticky 줄이다.
 * `top`을 0이 아니라 `--head-h`(머리글 행의 실제 높이, `WbsTree.vue`의 `measure()`가 잰다)로 주어야
 * 머리글 밑에 정확히 붙는다. z-index는 머리글과 같은 층(2)을 쓴다 — 두 줄이 물리적으로 겹치지
 * 않으므로 같은 층이어도 순서가 흔들리지 않는다.
 */
thead tr.filter-row td {
  position: sticky;
  top: var(--head-h, 0px);
  z-index: 2;
  background: var(--surface-alt);
  padding: 0.35rem 0.6rem;
  border-bottom: 1px solid var(--border);
  vertical-align: middle;
}

/* 고정 열과 겹치는 칸은 머리글의 교차 칸(위 thead th.pinned)과 같은 층(3)으로 올린다. */
thead tr.filter-row td.pinned {
  z-index: 3;
}

.filter-row input[type='search'] {
  width: 100%;
  padding: 0.25rem 0.5rem;
  border: 1px solid var(--border-input);
  border-radius: 6px;
  background: var(--surface);
  color: inherit;
  font: inherit;
  font-size: 0.78rem;
}

.filter-row select {
  width: 100%;
  font-size: 0.78rem;
}

.filter-row .chip-cell {
  display: flex;
  flex-wrap: wrap;
  gap: 0.25rem;
}

/* RaidView.vue의 .chip/.chip.active(설계 유래)와 같은 모양 — 앱 전체에서 같은 다중 선택 칩이다. */
.filter-row .chip {
  position: relative;
  padding: 0.2rem 0.55rem;
  border: 1px solid var(--border-input);
  border-radius: 999px;
  background: var(--surface);
  color: var(--text-muted);
  font: inherit;
  font-size: 0.72rem;
  cursor: pointer;
  white-space: nowrap;
}

/*
 * 24×24 최소 타깃(WCAG 2.5.5) — 칩은 세로 폭이 20px 남짓이라 부족하다. 시각적 크기는 그대로
 * 두고 보이지 않는 `::after`로 클릭 가능 영역만 넓힌다(위·아래로 4px씩, 폭은 이미 대부분
 * 24px를 넘어 좌우로도 여유를 조금 더 둔다).
 */
.filter-row .chip::after {
  content: '';
  position: absolute;
  inset: -4px -2px;
}

.filter-row .chip.active {
  border-color: var(--accent);
  background: var(--accent);
  color: var(--accent-fg);
}

tbody tr {
  cursor: grab;
}

/* 필터가 걸린 동안은 끌 수 없다(지시서 2-2) — 끌리는 것처럼 보이면 안 된다. */
.wbs-tree.filtering tbody tr {
  cursor: default;
}

/*
 * 필터의 조건에는 맞지 않지만 조상으로 남은 행(2-1) — 비활성으로 만들지 않는다, 고르고
 * 편집할 수 있어야 한다. 흐리게만 표시한다.
 */
tbody tr.context > td {
  color: var(--text-faint);
}

tbody tr.dragging {
  opacity: 0.4;
}

tbody tr.drop-before > td {
  border-top-color: var(--accent);
}

tbody tr.drop-after > td {
  border-bottom-color: var(--accent);
}

tbody tr.drop-inside > td {
  background: var(--accent-weak);
}

/* Backlog에서 건너온 행. 잠깐만 표시하고 사라진다. */
tbody tr.focused > td {
  background: var(--accent-weak);
  transition: background 0.6s ease-out;
}

@media (prefers-reduced-motion: reduce) {
  tbody tr.focused > td {
    transition: none;
  }
}

/*
 * 클릭으로 고른 행 — focused와 달리 사라지지 않고 다른 행을 고르거나 트리를 벗어날 때까지
 * 유지된다. focused와 같은 배경(--accent-weak)을 쓰면 두 상태가 구분되지 않으므로
 * --accent-container를 쓰고, 왼쪽에 굵은 강조선을 더해 옅은 배경만으로는 놓치기 쉬운
 * "선택됨"을 분명히 한다.
 */
tbody tr.selected > td {
  background: var(--accent-container);
  color: var(--accent-container-fg);
}

tbody tr.selected > td:first-child {
  box-shadow: inset 3px 0 0 var(--accent);
}

/* Keyboard focus ring on the row container, not per-row — the selected row's own highlight
   already marks position, so this only needs to confirm "this table has keyboard focus". */
tbody:focus {
  outline: none;
}

tbody:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: -2px;
}

.code {
  width: 5.5rem;
  color: var(--text-dim);
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

/*
 * WBS 코드·업무명 고정(지시서 `wbs-tree-columns` 2-3) — RaciMatrix.vue:283-291의 `.task-col`과
 * 같은 이유다. 배경이 불투명하지 않으면 스크롤된 셀이 통과해 보인다.
 *
 * 배경은 --surface가 아니라 --bg다(지시서 3-4-e 예시 코드와 다르게 한 곳) — 이 표의 본문 행
 * (tbody tr)에는 배경 규칙이 없고 WbsView.vue의 감싸는 <section>에도 없어, 행의 실제 배경은 페이지
 * 배경(--bg)이다. 라이트 모드는 --bg와 --surface가 같은 색(#fff)이라 차이가 안 보이지만, 다크
 * 모드는 --surface(#1a1d23)가 --bg(#14161a)보다 밝아 --surface를 쓰면 고정된 두 열만 밝은 세로
 * 띠로 보인다. 고정 열의 목적은 "스크롤된 셀이 통과해 보이지 않게" 행과 같은 색으로 숨는 것이지
 * 열을 강조하는 것이 아니다.
 *
 * z-index 층이 셋이다: 고정된 본문 셀(여기서 1) < 고정 머리글(위 `thead th`의 2) < 고정 열과 고정
 * 머리글이 겹치는 칸(아래 `thead th.pinned`의 3, 왼쪽 위 칸이 항상 위에 있어야 한다). `.pinned`에
 * `!important`를 붙이지 않는다 — 붙이면 선택된 행(`tbody tr.selected > td`)·드롭 강조(`.drop-*`)·
 * `.focused`가 고정 셀에서만 배경이 안 먹는다. 저 규칙들은 특이도가 이미 `.pinned`보다 높아
 * 자연스럽게 이긴다.
 */
.pinned {
  z-index: 1;
  background: var(--bg);
}

thead th.pinned {
  z-index: 3;
  background: var(--surface-alt);
}

/* 고정된 마지막 열에만 경계선을 그어 고정 영역의 끝을 보인다(RaciMatrix.vue:290과 같은 이유). */
.pinned-edge {
  border-right: 1px solid var(--border);
}

/*
 * `.name`(아래, 셀 안쪽 flex 컨테이너)과는 다른 클래스다 — 겹치면 들여쓰기·토글 정렬이 깨진다.
 * 비고정일 때는 폭이 없어 남는 폭을 먹는다(예전과 같다); 고정일 때만 아래에서 폭을 준다.
 */
.col-name {
  white-space: nowrap;
}

/*
 * pin: 'name'일 때만 폭을 고정한다 — `wbsColumns.ts`의 `name.pinWidthRem`(22)과 반드시 같은 값이어야
 * 한다. 어긋나면 오른쪽 열들이 고정 영역 아래로 밀려 들어간다.
 */
.pin-name .col-name {
  width: 22rem;
  overflow: hidden;
}

.date {
  width: 7rem;
  white-space: nowrap;
  color: var(--text-muted);
}

.mode {
  width: 11rem;
  white-space: nowrap;
  font-size: 0.82rem;
}

.mode .mode-summary {
  color: var(--text-muted);
}

/* 미지정은 흐리게 — 아직 정하지 않은 값이고, 정해진 값과 같은 무게로 읽히면 안 된다. */
.mode .unspecified {
  color: var(--text-faint);
}

.backlog {
  width: 10rem;
  white-space: nowrap;
  font-size: 0.8rem;
}

.backlog a {
  color: var(--accent);
  text-decoration: none;
}

.backlog a:hover {
  text-decoration: underline;
}

.backlog .rolled-up {
  color: var(--text-faint);
}

.backlog .none {
  color: var(--text-faint);
}

.mode .retained {
  margin-left: 0.35rem;
  font-size: 0.68rem;
  padding: 0.05rem 0.35rem;
  border-radius: 999px;
  border: 1px dashed var(--border-dashed);
  color: var(--text-faint);
}

.name {
  display: flex;
  align-items: baseline;
  gap: 0.4rem;
}

.name .summary {
  font-weight: 600;
}

.desc {
  font-size: 0.78rem;
  color: var(--text-faint);
}

/* 색은 간트 차트의 지연 팔레트와 동일하게 유지한다. */
.delay-badge {
  flex: none;
  font-size: 0.68rem;
  padding: 0.05rem 0.4rem;
  border-radius: 999px;
  white-space: nowrap;
  color: var(--status-fg);
  background: var(--warn);
}

.delay-badge[data-status='DELAYED'] {
  background: var(--status-delayed);
}

.toggle,
.toggle-spacer {
  position: relative;
  width: 1.1rem;
  flex: none;
  font-size: 0.65rem;
  color: var(--text-faint);
  padding: 0;
  border: none;
  background: none;
  cursor: pointer;
  text-align: left;
}

/*
 * 24×24 최소 타깃(WCAG 2.5.5) — 삼각형 자체는 17.6×14px 남짓이라 부족하다. 시각적 크기는
 * 그대로 두고 보이지 않는 `::after`로 클릭 가능 영역만 넓힌다. 이름 칸의 간격(0.4rem)이
 * 옆 텍스트까지는 닿지 않을 만큼만 넓혀 둔다.
 */
.toggle::after {
  content: '';
  position: absolute;
  inset: -5px -3px;
}

.progress {
  width: 13rem;
  white-space: nowrap;
}

.bar {
  display: inline-block;
  vertical-align: middle;
  width: 4.5rem;
  height: 0.4rem;
  border-radius: 999px;
  background: var(--border-soft);
  overflow: hidden;
}

.fill {
  height: 100%;
  background: var(--accent);
}

.pct {
  margin-left: 0.4rem;
  font-size: 0.78rem;
  color: var(--text-muted);
  font-variant-numeric: tabular-nums;
}

.pct.none {
  color: var(--text-faint);
}

.basis,
.incomplete,
.pending {
  margin-left: 0.3rem;
  font-size: 0.66rem;
  color: var(--text-faint);
  white-space: nowrap;
}

.incomplete,
.pending {
  padding: 0.02rem 0.3rem;
  border-radius: 999px;
  background: var(--warn-weak);
  color: var(--warn-badge-fg);
}

.checkpoint {
  width: 8rem;
  white-space: nowrap;
}

.checkpoint .none {
  color: var(--text-faint);
}

.owner {
  width: 11rem;
  font-size: 0.82rem;
}

/*
 * 전역 .cell-clip 은 22rem 까지 허용한다 — 설명 칸 기준이라 이 열에는 너무 넓다. 길이만 좁히고
 * 줄임표 처리는 그대로 쓴다(색·글꼴을 덮는 것이 아니므로 토큰 규칙과 무관하다).
 */
.owner .cell-clip,
.tags .cell-clip {
  max-width: 11rem;
}

.owner .who-link {
  color: inherit;
  text-decoration: none;
}

.owner .who-link:hover .who {
  text-decoration: underline;
}

/*
 * 물려받은 담당자. 이 행에서 지울 수 없고 상위 행을 고쳐야 하는 값이라, 자기 담당자와 같은 무게로
 * 읽히면 안 된다 — RACI 셀이 상속 글자를 따로 싣는 것과 같은 이유다.
 */
.owner .inherited {
  color: var(--text-faint);
  font-style: italic;
}

.owner .none {
  color: var(--text-faint);
}

.tags {
  width: 12rem;
}

.tags .chips {
  display: block;
}

/* 칩 사이 간격. .cell-clip 이 block + nowrap 이라 여백은 칩 쪽에서 준다. */
.tags .chips > * + * {
  margin-left: 0.25rem;
}

.tags .none {
  color: var(--text-faint);
}

.tags .retained {
  margin-left: 0.35rem;
  font-size: 0.68rem;
  padding: 0.05rem 0.35rem;
  border-radius: 999px;
  border: 1px dashed var(--border-dashed);
  color: var(--text-faint);
}

/*
 * Deliberately not shaped like `.toggle` (▼/▶) — a pill button, not a triangle, so it does not
 * read as "this row has children" (지시서 4.2). Clicking the badge itself is the expand/collapse
 * affordance.
 */
.cp-badge {
  position: relative;
  font-size: 0.7rem;
  padding: 0.1rem 0.5rem;
  border-radius: 999px;
  border: 1px solid var(--border-dashed);
  background: var(--surface);
  color: var(--text-muted);
  cursor: pointer;
  white-space: nowrap;
}

.cp-badge.open {
  border-color: var(--accent);
  color: var(--accent);
}

/*
 * 이건 버튼이다 — pill 모양이라 상태 표시로 오인되기 쉬워, hover로 "누를 수 있다"를 드러낸다.
 * 배경색을 바꾸지 않고 겹치는 반투명 층을 쓴다(CLAUDE.md "상태 레이어"): 어떤 배경(기본·`.open`)
 * 위에서도 같은 규칙으로 동작한다. `border-radius: inherit`으로 알약 모양에 맞춰 직접 클리핑한다
 * — `.cp-badge` 자체의 `overflow: hidden`에 기대지 않는다. 그러면 아래 `::after`(24px 타깃)가
 * 알약 바깥으로 번지는 hover 층 없이도 잘리지 않고 그대로 클릭 영역이 된다.
 */
.cp-badge::before {
  content: '';
  position: absolute;
  inset: 0;
  border-radius: inherit;
  background: transparent;
  transition: background 120ms ease;
}

.cp-badge:hover::before {
  background: var(--state-hover);
}

.cp-badge:active::before {
  background: var(--state-press);
}

/*
 * 24×24 최소 타깃(WCAG 2.5.5) — 알약 높이가 16px 남짓이라 부족하다. 폭은 이미 텍스트("승인
 * N/M")로 충분히 넓으므로 위아래만 넓힌다.
 */
.cp-badge::after {
  content: '';
  position: absolute;
  inset: -4px 0;
}

/* 펼친 체크포인트 목록. WBS 행과 같은 배경을 쓰지 않아 하위 항목이 아니라 부가 패널임을 보인다. */
tbody tr.checkpoint-row > td {
  background: var(--surface-sunken);
  padding: 0.6rem 0.75rem 0.75rem 2.5rem;
  cursor: default;
  white-space: normal;
}

tbody tr.checkpoint-row {
  cursor: default;
}

/*
 * 행마다 있던 [하위][수정][삭제]를 여기로 모은다(지시서 1-1). 모양은 옛 `.actions button`을
 * 그대로 옮기되, 툴바는 행 안이 아니므로 한 단계 키워 WbsView.vue의 `.add` 버튼과 같은 계열
 * (padding·pill 모서리·글자 크기)로 맞춘다. 새 색 토큰은 만들지 않는다 — `--danger`·
 * `--border-dashed`·`--disabled-bg`/`--disabled-fg`가 이미 있다.
 */
.tree-toolbar {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin-bottom: 0.75rem;
}

.tree-toolbar button {
  padding: 0.4rem 0.85rem;
  border-radius: 999px;
  border: 1px solid var(--border-dashed);
  background: var(--surface);
  cursor: pointer;
  font: inherit;
  font-size: 0.8rem;
}

.tree-toolbar button:hover:not(:disabled) {
  box-shadow: var(--elevation-1);
}

.tree-toolbar button.danger {
  color: var(--danger);
  border-color: var(--danger-border);
}

/* 배경과 글자색을 함께 지정한다 — 배경만 옅게 하면 다크 모드에서 글자가 배경에 묻힌다. */
.tree-toolbar button:disabled {
  background: var(--disabled-bg);
  color: var(--disabled-fg);
  cursor: not-allowed;
}

.tree-toolbar .selected-label {
  margin-left: 0.25rem;
  font-size: 0.82rem;
  color: var(--text-muted);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* [필터] 버튼 — 조건이 있으면 점(●)을 얹는다(지시서 2-6 예시 "[필터 ●]"). */
.tree-toolbar button[data-action='filter'] {
  position: relative;
}

.tree-toolbar button[data-action='filter'].active {
  border-color: var(--accent);
  color: var(--accent);
}

.tree-toolbar button[data-action='filter'].active::after {
  content: '';
  position: absolute;
  top: 0.3rem;
  right: 0.45rem;
  width: 0.35rem;
  height: 0.35rem;
  border-radius: 50%;
  background: var(--accent);
}

/*
 * 필터 상태 배너(계약 D) — 경고가 아니라 "지금 화면이 좁혀져 있다"는 정보라 --warn이 아니라
 * --accent 계열을 쓴다. 숨긴 열의 조건만은 경고 톤(--warn-strong)이다 — 조용히 해제하면
 * 사용자 모르게 결과가 넓어지는 쪽이라 눈에 걸려야 한다(팀 리드 결정 2).
 */
.filter-banner {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 0.6rem;
  font-size: 0.82rem;
  color: var(--text-muted);
  background: var(--accent-weak);
  border-left: 3px solid var(--accent);
  border-radius: 6px;
  padding: 0.45rem 0.65rem;
  margin-bottom: 0.75rem;
}

.filter-banner .hidden-hint {
  color: var(--warn-strong);
}

.filter-banner button {
  margin-left: auto;
  flex: none;
  padding: 0.3rem 0.75rem;
  border-radius: 999px;
  border: 1px solid var(--border-input);
  background: var(--surface);
  color: var(--text-muted);
  cursor: pointer;
  font: inherit;
  font-size: 0.78rem;
}

/*
 * 표를 뷰포트에 맞춘 스크롤 패널로 만든다(지시서 2-2) — 세 칸으로 나뉜 flex column이라
 * `.tree-scroll`만 늘어나 스크롤되고, 드롭존·설명문은 항상 패널 아래에 붙어 있다.
 */
.tree-pane {
  display: flex;
  flex-direction: column;
}

/*
 * min-height: 0을 빼먹으면 flex 아이템이 내용보다 줄어들지 않아 스크롤 영역이 통째로
 * 늘어나 버린다(= 패널로 감싸기 전과 같아진다).
 */
.tree-scroll {
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
  overscroll-behavior: contain;
}

.root-dropzone {
  flex: none;
  margin-top: 0.75rem;
  padding: 0.7rem;
  border: 1px dashed var(--border-input);
  border-radius: 8px;
  text-align: center;
  font-size: 0.8rem;
  color: var(--text-faint);
}

.root-dropzone.active {
  border-color: var(--accent);
  background: var(--accent-weak);
  color: var(--accent);
}

.empty {
  padding: 2.5rem 0;
  text-align: center;
  color: var(--text-faint);
}

.legend {
  flex: none;
  margin-top: 0.75rem;
  font-size: 0.78rem;
  color: var(--text-faint);
}
</style>
