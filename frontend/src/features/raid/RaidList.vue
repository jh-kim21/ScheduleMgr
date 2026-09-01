<script setup lang="ts">
import { computed } from 'vue'
import type { RaidItem } from '../../api/raidApi'
import {
  dueLabel,
  RAID_LEVEL_LABELS,
  RAID_STATUS_LABELS,
  RAID_TYPE_LABELS,
  RAID_TYPE_ORDER,
  type RaidType,
} from '../../shared/raid'

const props = defineProps<{
  items: RaidItem[]
  editingId: number | null
  /** True when the register is non-empty but the filter hides everything. */
  filtered?: boolean
}>()

const emit = defineEmits<{
  edit: [item: RaidItem]
  remove: [item: RaidItem]
}>()

/**
 * Sections in RAID order, empty ones dropped. The server already sorts by type then id; within a
 * section the rows keep that order so a row never jumps while it is being edited.
 */
const sections = computed(() =>
  RAID_TYPE_ORDER.flatMap((type) => {
    const items = props.items.filter((item) => item.type === type)
    return items.length === 0 ? [] : [{ type: type as RaidType, items }]
  }),
)

const emptyMessage = computed(() =>
  props.filtered
    ? '조건에 맞는 항목이 없습니다. 필터를 넓혀 보세요.'
    : '등록된 RAID 항목이 없습니다. 위의 ＋ 항목 추가 버튼으로 위험·가정·이슈·의존성을 기록해 보세요.',
)

function onRemove(item: RaidItem) {
  if (!confirm(`"${item.title}" 항목을 삭제할까요?`)) return
  emit('remove', item)
}
</script>

<template>
  <p v-if="items.length === 0" class="empty">
    {{ emptyMessage }}
  </p>

  <div v-else class="sections">
    <section v-for="section in sections" :key="section.type">
      <h3>
        <span class="type-badge" :data-type="section.type">
          {{ RAID_TYPE_LABELS[section.type] }}
        </span>
        {{ section.items.length }}건
      </h3>

      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th class="title-col">제목</th>
              <th>상태</th>
              <th>노출도</th>
              <th>소유자</th>
              <th>관련 업무</th>
              <th>기한</th>
              <th class="actions-col"></th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="item in section.items"
              :key="item.id"
              :class="{ editing: editingId === item.id, closed: item.status === 'CLOSED' }"
            >
              <td class="title-col">
                <span class="title">{{ item.title }}</span>
                <span v-if="item.description" class="description">{{ item.description }}</span>
                <span v-if="item.response" class="response">↳ {{ item.response }}</span>
              </td>

              <td>
                <span class="status-badge" :data-status="item.status">
                  {{ RAID_STATUS_LABELS[item.status] }}
                </span>
              </td>

              <td>
                <span
                  v-if="item.exposureLevel"
                  class="exposure"
                  :data-level="item.exposureLevel"
                  :title="`확률 ${RAID_LEVEL_LABELS[item.probability!]} × 영향 ${RAID_LEVEL_LABELS[item.impact!]}`"
                >
                  {{ RAID_LEVEL_LABELS[item.exposureLevel] }} ({{ item.exposure }})
                </span>
                <span v-else-if="item.impact" class="exposure" :data-level="item.impact">
                  영향 {{ RAID_LEVEL_LABELS[item.impact] }}
                </span>
                <span v-else class="none">-</span>
              </td>

              <td>
                <span v-if="item.ownerName">{{ item.ownerName }}</span>
                <span v-else class="none">미지정</span>
              </td>

              <td>
                <span v-if="item.wbsCode" class="wbs" :title="item.wbsName ?? ''">
                  <span class="wbs-code">{{ item.wbsCode }}</span> {{ item.wbsName }}
                </span>
                <span v-else class="none">-</span>
              </td>

              <td :class="{ overdue: item.overdue }">
                {{ dueLabel(item.dueDate, item.overdue, item.overdueDays) }}
              </td>

              <td class="actions-col">
                <span class="actions">
                  <button type="button" @click="emit('edit', item)">수정</button>
                  <button type="button" class="danger" @click="onRemove(item)">삭제</button>
                </span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
  </div>
</template>

<style scoped>
.sections {
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}

h3 {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  font-size: 0.85rem;
  font-weight: 600;
  color: var(--text-dim);
  margin: 0 0 0.4rem;
}

.type-badge {
  font-size: 0.72rem;
  font-weight: 600;
  color: var(--status-fg);
  border-radius: 999px;
  padding: 0.1rem 0.5rem;
}

.type-badge[data-type='RISK'] {
  background: var(--raid-risk);
}

.type-badge[data-type='ASSUMPTION'] {
  background: var(--raid-assumption);
}

.type-badge[data-type='ISSUE'] {
  background: var(--raid-issue);
}

.type-badge[data-type='DEPENDENCY'] {
  background: var(--raid-dependency);
}

.table-wrap {
  overflow-x: auto;
  border: 1px solid var(--border);
  border-radius: 8px;
}

table {
  border-collapse: collapse;
  width: 100%;
  font-size: 0.82rem;
}

th,
td {
  border-bottom: 1px solid var(--border-softer);
  padding: 0.4rem 0.6rem;
  text-align: left;
  vertical-align: top;
}

thead th {
  background: var(--surface-alt);
  border-bottom: 1px solid var(--border);
  font-weight: 600;
  font-size: 0.75rem;
  color: var(--text-dim);
  white-space: nowrap;
}

tbody tr:last-child td {
  border-bottom: none;
}

tr.editing {
  background: var(--accent-weak);
}

/* 종결된 항목은 남겨 두되 시선을 덜 끌게 한다. 노출도 배지가 특히 눈에 튄다. */
tr.closed .title {
  color: var(--text-faint);
  text-decoration: line-through;
}

tr.closed .exposure,
tr.closed .status-badge {
  opacity: 0.55;
}

.title-col {
  min-width: 18rem;
}

.title {
  display: block;
  font-weight: 600;
}

.description,
.response {
  display: block;
  font-size: 0.76rem;
  color: var(--text-faint);
  margin-top: 0.1rem;
}

.status-badge {
  display: inline-block;
  font-size: 0.72rem;
  border-radius: 999px;
  padding: 0.1rem 0.5rem;
  white-space: nowrap;
  background: var(--badge-neutral-bg);
  color: var(--badge-neutral-fg);
}

.status-badge[data-status='OPEN'] {
  background: var(--badge-planned-bg);
  color: var(--badge-planned-fg);
}

.status-badge[data-status='IN_PROGRESS'] {
  background: var(--warn-badge-bg);
  color: var(--warn-badge-fg);
}

.exposure {
  display: inline-block;
  font-size: 0.72rem;
  border-radius: 4px;
  padding: 0.1rem 0.4rem;
  white-space: nowrap;
  color: var(--status-fg);
  background: var(--status-not-started);
}

.exposure[data-level='LOW'] {
  background: var(--status-not-started);
}

.exposure[data-level='MEDIUM'] {
  background: var(--warn);
}

.exposure[data-level='HIGH'] {
  background: var(--status-delayed);
}

td.overdue {
  color: var(--status-delayed);
  font-weight: 600;
  white-space: nowrap;
}

.none {
  color: var(--text-faint);
}

.wbs {
  display: inline-block;
  max-width: 12rem;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: bottom;
}

.wbs-code {
  color: var(--text-faint);
  font-size: 0.74rem;
  font-variant-numeric: tabular-nums;
}

.actions-col {
  width: 1%;
  white-space: nowrap;
}

.actions {
  display: flex;
  gap: 0.35rem;
}

.actions button {
  padding: 0.25rem 0.55rem;
  border: 1px solid var(--border-input);
  border-radius: 6px;
  background: var(--surface);
  color: var(--text-muted);
  cursor: pointer;
  font: inherit;
  font-size: 0.75rem;
}

.actions button.danger {
  color: var(--danger);
  border-color: var(--danger-border);
}

.empty {
  padding: 2.5rem 1rem;
  text-align: center;
  color: var(--text-faint);
  border: 1px dashed var(--border-dashed);
  border-radius: 8px;
}
</style>
