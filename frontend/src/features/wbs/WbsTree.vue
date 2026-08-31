<script setup lang="ts">
import { computed, ref } from 'vue'
import type { WbsMoveInput, WbsNode } from '../../api/wbsApi'
import { delayBadge, delayDescription, needsAttention } from '../../shared/delay'
import {
  containsDescendant,
  flattenTree,
  resolveDropPosition,
  type DropPlacement,
  type WbsRow,
} from './wbsTree'

const props = defineProps<{
  tree: WbsNode[]
}>()

const emit = defineEmits<{
  addChild: [parent: WbsNode]
  edit: [node: WbsNode]
  remove: [node: WbsNode]
  move: [itemId: number, input: WbsMoveInput]
}>()

const collapsed = ref(new Set<number>())
const rows = computed(() => flattenTree(props.tree, collapsed.value))

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

/** Top and bottom edges reorder among siblings; the middle re-parents into the row. */
function placementFor(event: DragEvent, element: HTMLElement): DropPlacement {
  const rect = element.getBoundingClientRect()
  const ratio = (event.clientY - rect.top) / rect.height
  if (ratio < 0.25) return 'before'
  if (ratio > 0.75) return 'after'
  return 'inside'
}

function onDragOver(event: DragEvent, row: WbsRow) {
  if (!canDrop(row.node)) {
    dropTarget.value = null
    return
  }
  event.preventDefault()
  if (event.dataTransfer) event.dataTransfer.dropEffect = 'move'
  dropTarget.value = {
    id: row.node.id,
    placement: placementFor(event, event.currentTarget as HTMLElement),
  }
}

function onDrop(event: DragEvent, row: WbsRow) {
  const dragged = dragging.value
  if (!dragged || !canDrop(row.node)) return
  event.preventDefault()

  const placement = placementFor(event, event.currentTarget as HTMLElement)
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
  }
}
</script>

<template>
  <div v-if="tree.length === 0" class="empty">
    등록된 WBS 항목이 없습니다. 위 폼에서 최상위 항목을 추가해 보세요.
  </div>

  <template v-else>
    <table class="wbs-tree">
      <thead>
        <tr>
          <th class="code">WBS</th>
          <th>업무명</th>
          <th class="date">시작일</th>
          <th class="date">종료일</th>
          <th class="progress">진행률</th>
          <th></th>
        </tr>
      </thead>
      <tbody>
        <tr
          v-for="row in rows"
          :key="row.node.id"
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
              <span v-if="row.node.description" class="desc">{{ row.node.description }}</span>
            </div>
          </td>
          <td class="date">{{ row.node.startDate ?? '-' }}</td>
          <td class="date">{{ row.node.endDate ?? '-' }}</td>
          <td class="progress">
            <div class="bar" :title="`${row.node.progress}%`">
              <div class="fill" :style="{ width: `${row.node.progress}%` }"></div>
            </div>
            <span class="pct">{{ row.node.progress }}%</span>
          </td>
          <td class="actions">
            <button type="button" @click="emit('addChild', row.node)">하위</button>
            <button type="button" @click="emit('edit', row.node)">수정</button>
            <button type="button" class="danger" @click="emit('remove', row.node)">삭제</button>
          </td>
        </tr>
      </tbody>
    </table>

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
  width: 8rem;
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
