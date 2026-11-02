<script setup lang="ts">
import type { TodoItem } from '../../api/todoApi'
import { dueLabel } from '../../shared/raid'
import { TODO_PRIORITY_LABELS } from '../../shared/todo'

const props = defineProps<{
  items: TodoItem[]
  editingId: number | null
  /** 목록은 비어 있지 않은데 필터가 전부 가린 경우. */
  filtered?: boolean
  /** 완료 항목이 숨겨져 있어 비어 보이는 경우를 안내하려고 쓴다. */
  hiddenCompleted?: number
  /** 커밋 시점 조회 중이면 체크박스·행 액션을 막는다. */
  readOnly?: boolean
}>()

/** 화면 전체 공통 문구 — 화면마다 문구를 지어내지 않는다. */
const READONLY_HINT = '커밋 시점 조회 중에는 변경할 수 없습니다'

const emit = defineEmits<{
  edit: [item: TodoItem]
  remove: [item: TodoItem]
  toggle: [item: TodoItem, completed: boolean]
}>()

function onToggle(item: TodoItem, event: Event) {
  if (props.readOnly) return
  emit('toggle', item, (event.target as HTMLInputElement).checked)
}

function onRemove(item: TodoItem) {
  if (props.readOnly) return
  if (!confirm(`"${item.title}" TODO를 삭제할까요?`)) return
  emit('remove', item)
}
</script>

<template>
  <p v-if="items.length === 0" class="empty">
    <template v-if="filtered">조건에 맞는 TODO가 없습니다. 필터를 넓혀 보세요.</template>
    <template v-else-if="hiddenCompleted">
      해야 할 TODO가 없습니다. 완료된 {{ hiddenCompleted }}건은 숨겨져 있습니다 — 위의 완료 포함으로 볼 수 있습니다.
    </template>
    <template v-else>등록된 TODO가 없습니다. 위의 ＋ TODO 추가 버튼으로 할 일을 적어 보세요.</template>
  </p>

  <div v-else class="table-scroll">
    <table>
      <thead>
        <tr>
          <th class="check-col" scope="col"><span class="visually-hidden">완료</span></th>
          <th class="title-col" scope="col">제목</th>
          <th scope="col">우선순위</th>
          <th scope="col">담당자</th>
          <th scope="col">기한</th>
          <th scope="col">메모</th>
          <th class="actions-col" scope="col"></th>
        </tr>
      </thead>
      <tbody>
        <tr
          v-for="item in items"
          :key="item.id"
          :class="{ editing: editingId === item.id, done: item.completed }"
        >
          <td class="check-col">
            <input
              type="checkbox"
              :checked="item.completed"
              :disabled="readOnly"
              :title="readOnly ? READONLY_HINT : undefined"
              :aria-label="`${item.title} 완료`"
              @change="onToggle(item, $event)"
            />
          </td>
          <td class="title-col">
            <span class="title">{{ item.title }}</span>
          </td>
          <td>
            <span class="priority" :data-priority="item.priority">
              {{ TODO_PRIORITY_LABELS[item.priority] }}
            </span>
          </td>
          <td>
            <span v-if="item.assigneeName">{{ item.assigneeName }}</span>
            <span v-else class="none">미지정</span>
          </td>
          <td>
            <span v-if="item.overdue" class="overdue-badge">
              {{ dueLabel(item.dueDate, item.overdue, item.overdueDays) }}
            </span>
            <template v-else>{{ dueLabel(item.dueDate, false, 0) }}</template>
          </td>
          <td>
            <span v-if="item.memo" class="memo cell-clip" :title="item.memo">{{ item.memo }}</span>
            <span v-else class="none">-</span>
          </td>
          <td class="actions-col">
            <span class="actions">
              <button
                type="button"
                :disabled="readOnly"
                :title="readOnly ? READONLY_HINT : undefined"
                @click="emit('edit', item)"
              >수정</button>
              <button
                type="button"
                class="danger"
                :disabled="readOnly"
                :title="readOnly ? READONLY_HINT : undefined"
                @click="onRemove(item)"
              >삭제</button>
            </span>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<style scoped>
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
  vertical-align: middle;
  white-space: nowrap;
}

thead th {
  background: var(--surface-alt);
  border-bottom: 1px solid var(--border);
  font-weight: 600;
  font-size: 0.75rem;
  color: var(--text-dim);
}

tbody tr:last-child td {
  border-bottom: none;
}

tr.editing {
  background: var(--accent-weak);
}

/* 완료된 항목은 남겨 두되 시선을 덜 끌게 한다. */
tr.done .title,
tr.done .memo {
  color: var(--text-faint);
  text-decoration: line-through;
}

tr.done .priority,
tr.done .overdue-badge {
  opacity: 0.55;
}

.check-col {
  width: 1%;
}

.title-col {
  min-width: 16rem;
}

.title {
  font-weight: 600;
}

.priority {
  display: inline-block;
  font-size: 0.72rem;
  border-radius: 999px;
  padding: 0.1rem 0.5rem;
  background: var(--badge-neutral-bg);
  color: var(--badge-neutral-fg);
}

.priority[data-priority='HIGH'] {
  background: var(--status-delayed);
  color: var(--status-fg);
}

.priority[data-priority='MEDIUM'] {
  background: var(--warn-badge-bg);
  color: var(--warn-badge-fg);
}

/* 기한 초과는 기존 지연 배지 토큰을 그대로 쓴다 — 새 색을 만들지 않는다. */
.overdue-badge {
  display: inline-block;
  font-size: 0.75rem;
  font-weight: 600;
  border-radius: 4px;
  padding: 0.1rem 0.4rem;
  background: var(--status-delayed);
  color: var(--status-fg);
}

.none {
  color: var(--text-faint);
}

.actions-col {
  width: 1%;
}

.actions {
  display: flex;
  gap: 0.35rem;
}

.actions button {
  padding: 0.25rem 0.55rem;
  color: var(--text-muted);
  font-size: 0.75rem;
}

.empty {
  padding: 2.5rem 1rem;
  text-align: center;
  color: var(--text-faint);
  border: 1px dashed var(--border-dashed);
  border-radius: 8px;
}
</style>
