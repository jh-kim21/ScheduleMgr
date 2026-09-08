<script setup lang="ts">
import { RouterLink } from 'vue-router'
import type { BacklogItem } from '../../api/backlogApi'
import {
  BACKLOG_PRIORITY_LABELS,
  BACKLOG_STATUS_LABELS,
  BACKLOG_TYPE_LABELS,
} from '../../shared/backlog'
import { executionModeLabel } from '../../shared/executionMode'
import type { BacklogRow } from './backlogFilter'

const props = defineProps<{
  rows: BacklogRow[]
  filtered: boolean
}>()

const emit = defineEmits<{
  edit: [item: BacklogItem]
  archive: [item: BacklogItem, archived: boolean]
  remove: [item: BacklogItem]
}>()

/**
 * Why a row is flagged. Ordered worst first: a link that cannot be used at all matters more than
 * one that merely needs the Work Package's execution mode changed.
 */
function warning(item: BacklogItem): string | null {
  if (item.danglingLink) return '귀속된 Work Package를 찾을 수 없습니다.'
  if (item.linkedToSummary) {
    return '귀속 대상이 Summary로 바뀌어 연결을 쓸 수 없습니다. 최하위 Work Package로 다시 연결하세요.'
  }
  if (item.unlinked) return '귀속 Work Package가 없습니다. Sprint에 넣기 전에 연결해야 합니다.'
  if (item.requiresExecutionModeChange) {
    return `귀속 Work Package의 실행 방식이 ${executionModeLabel(item.wbsExecutionMode)}입니다. Agile 실행으로 관리하려면 실행 방식을 바꿔야 합니다.`
  }
  return null
}

function warningLabel(item: BacklogItem): string {
  if (item.danglingLink) return '연결 끊김'
  if (item.linkedToSummary) return 'Summary 연결'
  if (item.unlinked) return '미연결'
  return '실행 방식 확인'
}

/** 하위가 있으면 삭제할 수 없다 — 서버도 거부하므로 미리 막고 이유를 알린다. */
function deleteTitle(item: BacklogItem): string {
  if (item.childCount > 0) {
    return `하위 항목이 ${item.childCount}건 있어 삭제할 수 없습니다. 하위 항목을 먼저 옮기거나 삭제하세요.`
  }
  if (item.openSprintName) {
    return `'${item.openSprintName}'에 배정되어 있어 삭제할 수 없습니다. Sprint에서 먼저 제거하세요.`
  }
  return '삭제'
}

/** 진행 중인 Sprint의 항목을 접어두면 보드에서 사라지므로 서버가 거부한다. */
function archiveTitle(item: BacklogItem): string {
  if (item.archived) return '보관 해제'
  if (item.openSprintName) {
    return `'${item.openSprintName}'에 배정되어 있어 보관할 수 없습니다. Sprint에서 먼저 제거하세요.`
  }
  return '보관 (상태는 그대로 유지됩니다)'
}
</script>

<template>
  <div v-if="rows.length === 0" class="empty">
    {{ filtered ? '조건에 맞는 항목이 없습니다.' : '등록된 Backlog 항목이 없습니다. ＋ 항목 추가로 시작해 보세요.' }}
  </div>

  <table v-else class="backlog">
    <thead>
      <tr>
        <th class="type">유형</th>
        <th>제목</th>
        <th class="wbs">귀속 Work Package</th>
        <th class="who">담당</th>
        <th class="num">SP</th>
        <th class="num">가중치</th>
        <th class="status">상태</th>
        <th></th>
      </tr>
    </thead>
    <tbody>
      <tr
        v-for="row in rows"
        :key="row.item.id"
        :class="{ context: row.context, archived: row.item.archived }"
      >
        <td class="type">
          <span class="type-badge" :data-type="row.item.itemType">
            {{ BACKLOG_TYPE_LABELS[row.item.itemType] }}
          </span>
        </td>
        <td>
          <div class="title" :style="{ paddingLeft: `${row.item.depth * 1.25}rem` }">
            <span>{{ row.item.title }}</span>
            <span v-if="row.item.archived" class="chip">보관</span>
            <span
              v-if="row.item.openSprintName"
              class="chip sprint"
              :title="'진행 중인 Sprint에 배정되어 있어 삭제·보관할 수 없습니다.'"
            >{{ row.item.openSprintName }}</span>
            <span
              v-if="row.item.blocked"
              class="chip warn"
              :title="row.item.blockedReason ?? '차단됨'"
            >차단</span>
            <!-- 집계 대상이 아닌 유형은 Step 5의 진척에 가산되지 않는다. 미리 밝혀 둔다. -->
            <span v-if="!row.item.aggregated" class="chip muted" title="진척 집계에 별도로 가산하지 않습니다 (Epic은 묶음, Task는 실행 상세).">
              집계 제외
            </span>
            <span
              v-if="warning(row.item)"
              class="chip warn"
              :title="warning(row.item) ?? ''"
            >{{ warningLabel(row.item) }}</span>
            <span class="priority" :data-priority="row.item.priority">
              {{ BACKLOG_PRIORITY_LABELS[row.item.priority] }}
            </span>
          </div>
          <div v-if="row.item.acceptanceCriteria" class="criteria">
            수용 조건: {{ row.item.acceptanceCriteria }}
          </div>
        </td>
        <td class="wbs">
          <!-- Backlog에서 원본 WBS로 이동한다 (Step 3 지시서 7항). -->
          <RouterLink
            v-if="row.item.wbsItemId"
            :to="{ path: '/wbs', query: { focus: String(row.item.wbsItemId) } }"
            :title="`WBS에서 '${row.item.wbsName}' 보기`"
          >{{ row.item.wbsCode }} {{ row.item.wbsName }}</RouterLink>
          <span v-else class="none">미연결</span>
        </td>
        <td class="who">{{ row.item.assigneeName ?? '-' }}</td>
        <td class="num">{{ row.item.storyPoint ?? '-' }}</td>
        <td class="num">{{ row.item.progressWeight ?? '-' }}</td>
        <td class="status">
          <span class="status-badge" :data-status="row.item.status">
            {{ BACKLOG_STATUS_LABELS[row.item.status] }}
          </span>
        </td>
        <td class="actions">
          <button type="button" @click="emit('edit', row.item)">수정</button>
          <button
            type="button"
            :disabled="!row.item.archived && row.item.openSprintName !== null"
            :title="archiveTitle(row.item)"
            @click="emit('archive', row.item, !row.item.archived)"
          >{{ row.item.archived ? '복구' : '보관' }}</button>
          <button
            type="button"
            class="danger"
            :disabled="row.item.childCount > 0 || row.item.openSprintName !== null"
            :title="deleteTitle(row.item)"
            @click="emit('remove', row.item)"
          >삭제</button>
        </td>
      </tr>
    </tbody>
  </table>
</template>

<style scoped>
.backlog {
  width: 100%;
  border-collapse: collapse;
}

th,
td {
  text-align: left;
  padding: 0.5rem 0.6rem;
  border-bottom: 1px solid var(--border-soft);
  font-size: 0.9rem;
  vertical-align: top;
}

th {
  font-size: 0.8rem;
  color: var(--text-dim);
  font-weight: 600;
}

/* 필터에 걸리지 않았지만 하위를 설명하려고 남긴 행. 결과와 구분되어야 한다. */
tbody tr.context {
  color: var(--text-faint);
}

tbody tr.archived .title > span:first-child {
  text-decoration: line-through;
  color: var(--text-faint);
}

.type {
  width: 4.5rem;
}

.type-badge {
  font-size: 0.68rem;
  padding: 0.05rem 0.4rem;
  border-radius: 999px;
  border: 1px solid var(--border-dashed);
  color: var(--text-muted);
  white-space: nowrap;
}

.type-badge[data-type='EPIC'] {
  border-color: var(--accent-border);
  color: var(--accent);
}

.type-badge[data-type='BUG'] {
  border-color: var(--danger-border);
  color: var(--danger);
}

.title {
  display: flex;
  align-items: baseline;
  gap: 0.4rem;
  flex-wrap: wrap;
}

.criteria {
  font-size: 0.78rem;
  color: var(--text-faint);
  margin-top: 0.15rem;
}

.chip {
  flex: none;
  font-size: 0.68rem;
  padding: 0.05rem 0.4rem;
  border-radius: 999px;
  background: var(--surface-sunken);
  color: var(--text-muted);
  white-space: nowrap;
}

.chip.muted {
  color: var(--text-faint);
}

.chip.warn {
  background: var(--warn);
  color: var(--status-fg);
}

.chip.sprint {
  border: 1px solid var(--accent-border);
  background: transparent;
  color: var(--accent);
}

.priority {
  font-size: 0.7rem;
  color: var(--text-faint);
}

.priority[data-priority='HIGH'] {
  color: var(--danger);
}

.wbs {
  width: 12rem;
  font-size: 0.82rem;
}

.wbs a {
  color: var(--accent);
  text-decoration: none;
}

.wbs a:hover {
  text-decoration: underline;
}

.wbs .none {
  color: var(--text-faint);
}

.who {
  width: 6rem;
  color: var(--text-muted);
  font-size: 0.85rem;
}

.num {
  width: 4rem;
  text-align: right;
  font-variant-numeric: tabular-nums;
  color: var(--text-muted);
}

.status {
  width: 5.5rem;
}

.status-badge {
  font-size: 0.7rem;
  padding: 0.05rem 0.4rem;
  border-radius: 999px;
  background: var(--surface-sunken);
  color: var(--text-muted);
  white-space: nowrap;
}

.status-badge[data-status='DONE'] {
  background: var(--status-completed);
  color: var(--status-fg);
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
  border-color: var(--border-soft);
  cursor: not-allowed;
}

.empty {
  padding: 2.5rem 0;
  text-align: center;
  color: var(--text-faint);
}
</style>
