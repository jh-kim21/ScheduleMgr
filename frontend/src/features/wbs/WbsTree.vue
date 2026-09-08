<script setup lang="ts">
import { computed, nextTick, onUnmounted, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import type { WbsMoveInput, WbsNode } from '../../api/wbsApi'
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
  flattenTree,
  resolveDropPosition,
  type DropPlacement,
  type WbsRow,
} from './wbsTree'

const props = defineProps<{
  tree: WbsNode[]
  /** Row to reveal and highlight — set when arriving from a Backlog entry's WBS link. */
  focusId?: number | null
}>()

const emit = defineEmits<{
  addChild: [parent: WbsNode]
  edit: [node: WbsNode]
  remove: [node: WbsNode]
  move: [itemId: number, input: WbsMoveInput]
}>()

const collapsed = ref(new Set<number>())
const rows = computed(() => flattenTree(props.tree, collapsed.value))

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
})

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

function toggle(node: WbsNode) {
  if (collapsed.value.has(node.id)) {
    collapsed.value.delete(node.id)
  } else {
    collapsed.value.add(node.id)
  }
}

function onDragStart(event: DragEvent, node: WbsNode) {
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

function rowClass(row: WbsRow) {
  const target = dropTarget.value
  return {
    dragging: dragging.value?.id === row.node.id,
    'drop-before': target?.id === row.node.id && target.placement === 'before',
    'drop-after': target?.id === row.node.id && target.placement === 'after',
    'drop-inside': target?.id === row.node.id && target.placement === 'inside',
    focused: highlighted.value === row.node.id,
  }
}
</script>

<template>
  <div v-if="tree.length === 0" class="empty">
    등록된 WBS 항목이 없습니다. 위 ＋ 최상위 항목 추가로 첫 항목을 만들어 보세요.
  </div>

  <template v-else>
    <div class="table-scroll">
      <table class="wbs-tree">
        <thead>
          <tr>
            <th class="code">WBS</th>
            <th>업무명</th>
            <th class="date">시작일</th>
            <th class="date">종료일</th>
            <th class="mode">실행 방식</th>
            <th class="backlog">연결 Backlog</th>
            <th class="progress">진척</th>
            <th></th>
          </tr>
        </thead>
        <tbody ref="body">
          <tr
            v-for="row in rows"
            :key="row.node.id"
            :data-row-id="row.node.id"
            :class="rowClass(row)"
            draggable="true"
            @dragstart="onDragStart($event, row.node)"
            @dragend="onDragEnd"
            @dragover="onDragOver($event, row)"
            @drop="onDrop($event, row)"
          >
            <td class="code">{{ row.node.code }}</td>
            <td>
              <div class="name" :style="{ paddingLeft: `${(row.node.level - 1) * 1.25}rem` }">
                <button
                  v-if="row.node.children.length > 0"
                  class="toggle"
                  type="button"
                  :aria-label="collapsed.has(row.node.id) ? '펼치기' : '접기'"
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
            <td class="date">{{ row.node.startDate ?? '-' }}</td>
            <td class="date">{{ row.node.endDate ?? '-' }}</td>
            <!--
              실행 방식은 최하위 Work Package의 것이다. 하위가 있는 항목은 자기 값을 쓰지 않고
              하위의 요약을 보여준다 (설계 §5).
            -->
            <td class="mode">
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
            <td class="backlog">
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
            <td class="progress">
              <div
                class="bar"
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
              <span v-if="row.node.progressIncomplete" class="incomplete" title="일부 하위가 산정 전이거나 가중치가 없습니다.">불완전</span>
              <span v-if="row.node.acceptancePending" class="pending">
                {{ ACCEPTANCE_STATUS_LABELS.PENDING }}
              </span>
            </td>
            <td class="actions">
              <!--
                Work Package에는 하위를 둘 수 없다. 눌러 봐야 서버가 거부하므로 미리 막고
                무엇을 해야 하는지 title로 알린다.
              -->
              <button
                type="button"
                :disabled="row.node.nodeType === 'WORK_PACKAGE'"
                :title="
                  row.node.nodeType === 'WORK_PACKAGE'
                    ? 'Work Package에는 하위 항목을 둘 수 없습니다. 수정에서 구분을 Summary로 바꾸세요.'
                    : '하위 항목 추가'
                "
                @click="emit('addChild', row.node)"
              >하위</button>
              <button type="button" @click="emit('edit', row.node)">수정</button>
              <button type="button" class="danger" @click="emit('remove', row.node)">삭제</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <div
      class="root-dropzone"
      :class="{ active: dropTarget?.id === 'root' }"
      @dragover="onDragOverRoot"
      @drop="onDropRoot"
    >
      여기로 끌어다 놓으면 최상위 항목이 됩니다
    </div>

    <p class="legend">
      행을 드래그해 순서를 바꾸거나 다른 항목의 <strong>가운데</strong>에 놓아 하위 항목으로 만들 수 있습니다.
      위/아래 가장자리에 놓으면 같은 계층에서 순서만 바뀝니다.
    </p>
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

tbody tr {
  cursor: grab;
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

.code {
  width: 5.5rem;
  color: var(--text-dim);
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
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

.actions {
  white-space: nowrap;
  text-align: right;
}

.actions button {
  padding: 0.25rem 0.5rem;
  margin-left: 0.25rem;
  border-radius: 5px;
  border: 1px solid var(--border-dashed);
  background: var(--surface);
  cursor: pointer;
  font-size: 0.75rem;
}

.actions button.danger {
  color: var(--danger);
  border-color: var(--danger-border);
}

.actions button:disabled {
  background: var(--disabled-bg);
  color: var(--disabled-fg);
  cursor: not-allowed;
}

.root-dropzone {
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
  margin-top: 0.75rem;
  font-size: 0.78rem;
  color: var(--text-faint);
}
</style>
