<script setup lang="ts">
import { computed, ref } from 'vue'
import type { RaidItem } from '../../api/raidApi'
import type { BoardMoveInput, Sprint, SprintItem } from '../../api/sprintApi'
import { RAID_TYPE_LABELS } from '../../shared/raid'
import {
  BACKLOG_STATUS_LABELS,
  BACKLOG_STATUS_ORDER,
  BACKLOG_TYPE_LABELS,
  type BacklogStatus,
} from '../../shared/backlog'
import { SPRINT_OUTCOME_LABELS } from '../../shared/sprint'

const props = defineProps<{
  sprint: Sprint
  /**
   * The project's RAID register, used only to show what is holding a blocked card up
   * (지시서 6-C). Read-only here: the Board never edits the register, and a card being blocked
   * does not create or close an entry.
   */
  raidItems: RaidItem[]
}>()

const emit = defineEmits<{
  move: [backlogItemId: number, input: BoardMoveInput]
  unassign: [item: SprintItem]
}>()

/** The card being dragged, so a column can tell whether a drop belongs to it. */
const dragging = ref<SprintItem | null>(null)
const hoverColumn = ref<BacklogStatus | null>(null)

/**
 * Completion asks for a confirmation, because there is no Definition of Done stored anywhere —
 * the person clicking is the only thing that can attest the criteria were met (지시서 7항).
 */
const confirming = ref<SprintItem | null>(null)
const blocking = ref<SprintItem | null>(null)
const blockReason = ref('')

const frozen = computed(() => props.sprint.status === 'CLOSED')

const columns = computed(() =>
  BACKLOG_STATUS_ORDER.map((status) => ({
    status,
    items: props.sprint.items.filter((item) => item.status === status && !item.removed),
  })),
)

const removedItems = computed(() => props.sprint.items.filter((item) => item.removed))

/**
 * Open RAID entries related to a card: linked to the entry itself, or to the Work Package it
 * belongs to. Both are what somebody staring at a blocked card wants — the Issue is as often
 * logged against the Work Package as against the Story.
 *
 * <p>Closed entries are left out: they are no longer holding anything up.
 */
function relatedRaid(item: SprintItem): RaidItem[] {
  return props.raidItems.filter(
    (entry) =>
      entry.status !== 'CLOSED' &&
      entry.links.some(
        (link) =>
          (link.targetType === 'BACKLOG_ITEM' && link.targetId === item.backlogItemId) ||
          (link.targetType === 'WBS_ITEM' &&
            item.wbsItemId !== null &&
            link.targetId === item.wbsItemId),
      ),
  )
}

function onDragStart(item: SprintItem) {
  if (frozen.value) return
  dragging.value = item
}

function onDragEnd() {
  dragging.value = null
  hoverColumn.value = null
}

function onDragOver(event: DragEvent, status: BacklogStatus) {
  if (frozen.value || !dragging.value || dragging.value.status === status) return
  event.preventDefault()
  hoverColumn.value = status
}

function onDrop(status: BacklogStatus) {
  const item = dragging.value
  onDragEnd()
  if (!item || frozen.value || item.status === status) return
  requestMove(item, status)
}

/** 완료로 가는 이동만 확인을 거친다. 나머지는 그대로 보낸다. */
function requestMove(item: SprintItem, status: BacklogStatus) {
  if (status === 'DONE') {
    confirming.value = item
    return
  }
  emit('move', item.backlogItemId, { status })
}

function confirmDone() {
  const item = confirming.value
  confirming.value = null
  if (!item) return
  emit('move', item.backlogItemId, { status: 'DONE', acceptanceConfirmed: true })
}

function openBlock(item: SprintItem) {
  blocking.value = item
  blockReason.value = item.blockedReason ?? ''
}

function submitBlock() {
  const item = blocking.value
  blocking.value = null
  if (!item) return
  emit('move', item.backlogItemId, {
    status: item.status,
    blocked: true,
    blockedReason: blockReason.value.trim() || null,
  })
}

function unblock(item: SprintItem) {
  emit('move', item.backlogItemId, { status: item.status, blocked: false, blockedReason: null })
}
</script>

<template>
  <div class="board">
    <div
      v-for="column in columns"
      :key="column.status"
      class="column"
      :class="{ hover: hoverColumn === column.status }"
      @dragover="onDragOver($event, column.status)"
      @dragleave="hoverColumn = null"
      @drop="onDrop(column.status)"
    >
      <h3>
        {{ BACKLOG_STATUS_LABELS[column.status] }}
        <span class="count">{{ column.items.length }}</span>
      </h3>

      <p v-if="column.items.length === 0" class="empty">없음</p>

      <article
        v-for="item in column.items"
        :key="item.assignmentId"
        class="card"
        :class="{ blocked: item.blocked, dragging: dragging?.assignmentId === item.assignmentId }"
        :draggable="!frozen"
        @dragstart="onDragStart(item)"
        @dragend="onDragEnd"
      >
        <header>
          <span class="type">{{ BACKLOG_TYPE_LABELS[item.itemType] }}</span>
          <span v-if="item.storyPoint !== null" class="points">{{ item.storyPoint }}</span>
        </header>
        <p class="title">{{ item.title }}</p>
        <p v-if="item.wbsCode" class="wbs">{{ item.wbsCode }} {{ item.wbsName }}</p>

        <p v-if="item.blocked" class="block-note">
          차단{{ item.blockedReason ? `: ${item.blockedReason}` : '' }}
        </p>
        <!-- 차단된 카드에서만 편다: 열려 있는 카드에 위험 목록까지 붙이면 보드가 읽히지 않는다. -->
        <ul v-if="item.blocked && relatedRaid(item).length > 0" class="raid-note">
          <li v-for="entry in relatedRaid(item)" :key="entry.id">
            <span class="raid-type">{{ RAID_TYPE_LABELS[entry.type] }}</span>
            {{ entry.title }}
            <span class="raid-owner">{{ entry.ownerName ?? '소유자 미지정' }}</span>
          </li>
        </ul>
        <p v-if="item.openChildCount > 0" class="child-note">
          완료되지 않은 하위 {{ item.openChildCount }}건
        </p>
        <p v-if="item.reopened" class="reopen-note">
          지난 Sprint에서 완료로 기록된 항목입니다 (지금은 미완료).
        </p>

        <footer>
          <span class="who">{{ item.assigneeName ?? '담당 미지정' }}</span>
          <span v-if="item.outcome" class="outcome">
            {{ SPRINT_OUTCOME_LABELS[item.outcome] }}
          </span>
          <template v-if="!frozen">
            <button
              v-if="item.blocked"
              type="button"
              class="link"
              @click="unblock(item)"
            >차단 해제</button>
            <button v-else type="button" class="link" @click="openBlock(item)">차단</button>
            <button type="button" class="link danger" @click="emit('unassign', item)">제거</button>
          </template>
        </footer>
      </article>
    </div>
  </div>

  <!-- 종료된 Sprint는 제거된 배정까지 보여준다 — 왜 빠졌는지가 이력이다. -->
  <p v-if="removedItems.length > 0" class="removed">
    이 Sprint에서 제외된 항목 {{ removedItems.length }}건 —
    {{ removedItems.map((item) => item.title).join(', ') }}.
  </p>

  <div v-if="confirming" class="dialog" role="dialog" aria-modal="true">
    <div class="dialog-body">
      <h4>완료 처리</h4>
      <p class="subject">{{ confirming.title }}</p>
      <p v-if="confirming.acceptanceCriteria" class="criteria">
        수용 조건: {{ confirming.acceptanceCriteria }}
      </p>
      <p v-else class="criteria muted">등록된 수용 조건이 없습니다.</p>
      <p v-if="confirming.openChildCount > 0" class="child-note">
        완료되지 않은 하위가 {{ confirming.openChildCount }}건 있습니다. 그래도 완료로 처리할 수 있습니다.
      </p>
      <p class="ask">수용 조건과 완료 기준(Definition of Done)을 확인했습니까?</p>
      <div class="dialog-actions">
        <button type="button" @click="confirmDone">확인하고 완료</button>
        <button type="button" class="ghost" @click="confirming = null">취소</button>
      </div>
    </div>
  </div>

  <div v-if="blocking" class="dialog" role="dialog" aria-modal="true">
    <div class="dialog-body">
      <h4>차단 표시</h4>
      <p class="subject">{{ blocking.title }}</p>
      <label>
        차단 사유
        <input v-model="blockReason" type="text" placeholder="예: 외부 API 응답 대기" />
      </label>
      <div class="dialog-actions">
        <button type="button" @click="submitBlock">차단</button>
        <button type="button" class="ghost" @click="blocking = null">취소</button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.board {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 0.75rem;
  align-items: start;
}

.column {
  border: 1px solid var(--border-soft);
  border-radius: 8px;
  padding: 0.6rem;
  min-height: 8rem;
  background: var(--surface);
}

.column.hover {
  border-color: var(--accent);
  background: var(--accent-weak);
}

.column h3 {
  margin: 0 0 0.5rem;
  font-size: 0.82rem;
  color: var(--text-muted);
  display: flex;
  justify-content: space-between;
}

.column h3 .count {
  color: var(--text-faint);
  font-variant-numeric: tabular-nums;
}

.column .empty {
  margin: 0;
  font-size: 0.78rem;
  color: var(--text-faint);
  text-align: center;
  padding: 0.75rem 0;
}

.card {
  border: 1px solid var(--border-soft);
  border-radius: 6px;
  padding: 0.5rem;
  margin-bottom: 0.5rem;
  background: var(--surface-raised, var(--surface));
  cursor: grab;
}

.card.dragging {
  opacity: 0.4;
}

/* 차단은 상태와 별개다 — 칸은 그대로 두고 테두리로만 알린다. */
/* 관련 RAID는 차단 사유 바로 아래에 둔다 — "왜 막혔나"의 답이 두 곳에 흩어지면 안 된다. */
.raid-note {
  list-style: none;
  margin: 0.25rem 0 0;
  padding: 0;
  font-size: 0.72rem;
  color: var(--text-muted);
}

.raid-note li {
  display: flex;
  gap: 0.3rem;
  align-items: baseline;
}

.raid-note .raid-type {
  color: var(--text-faint);
  flex: none;
}

.raid-note .raid-owner {
  margin-left: auto;
  color: var(--text-faint);
  flex: none;
}

.card.blocked {
  border-color: var(--danger-border);
}

.card header {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  font-size: 0.68rem;
  color: var(--text-faint);
}

.card .points {
  font-variant-numeric: tabular-nums;
}

.card .title {
  margin: 0.2rem 0;
  font-size: 0.85rem;
}

.card .wbs,
.card .block-note,
.card .child-note,
.card .reopen-note {
  margin: 0.15rem 0 0;
  font-size: 0.72rem;
}

.card .wbs {
  color: var(--text-faint);
}

.card .block-note {
  color: var(--danger);
}

.card .child-note {
  color: var(--warn-badge-fg);
}

.card .reopen-note {
  color: var(--warn-badge-fg);
}

.card footer {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  flex-wrap: wrap;
  margin-top: 0.35rem;
  font-size: 0.72rem;
  color: var(--text-faint);
}

.card footer .who {
  margin-right: auto;
}

.card footer .outcome {
  padding: 0.05rem 0.35rem;
  border-radius: 999px;
  background: var(--surface-sunken);
}

button.link {
  border: none;
  background: none;
  padding: 0;
  font: inherit;
  font-size: 0.72rem;
  color: var(--accent);
  cursor: pointer;
}

button.link.danger {
  color: var(--danger);
}

.removed {
  margin-top: 0.75rem;
  font-size: 0.8rem;
  color: var(--text-faint);
}

.dialog {
  position: fixed;
  inset: 0;
  background: rgb(0 0 0 / 45%);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 50;
}

.dialog-body {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 10px;
  padding: 1.1rem 1.25rem;
  max-width: 26rem;
  box-shadow: var(--elevation-3, 0 10px 30px rgb(0 0 0 / 25%));
}

.dialog-body h4 {
  margin: 0 0 0.5rem;
  font-size: 0.95rem;
}

.dialog-body .subject {
  margin: 0 0 0.4rem;
  font-weight: 600;
  font-size: 0.9rem;
}

.dialog-body .criteria {
  margin: 0 0 0.4rem;
  font-size: 0.82rem;
  color: var(--text-muted);
}

.dialog-body .criteria.muted {
  color: var(--text-faint);
}

.dialog-body .ask {
  margin: 0.6rem 0;
  font-size: 0.85rem;
}

.dialog-body label {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  font-size: 0.82rem;
  color: var(--text-muted);
}

.dialog-body input {
  padding: 0.45rem 0.6rem;
  border: 1px solid var(--border-input);
  border-radius: 6px;
  font: inherit;
}

.dialog-actions {
  display: flex;
  gap: 0.5rem;
  margin-top: 0.9rem;
}

.dialog-actions button {
  padding: 0.45rem 0.9rem;
  border-radius: 6px;
  border: 1px solid var(--accent);
  background: var(--accent);
  color: var(--accent-fg);
  cursor: pointer;
  font: inherit;
  font-size: 0.82rem;
}

.dialog-actions button.ghost {
  background: transparent;
  color: var(--text-muted);
  border-color: var(--border-input);
}

@media (max-width: 860px) {
  .board {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
