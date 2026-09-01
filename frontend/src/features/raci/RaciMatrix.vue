<script setup lang="ts">
import { computed } from 'vue'
import type { RaciIssue, RaciMatrix, RaciTask } from '../../api/raciApi'
import {
  issueSummary,
  RACI_DESCRIPTIONS,
  RACI_LABELS,
  RACI_LETTERS,
  RACI_ORDER,
  type RaciRole,
} from '../../shared/raci'

const props = defineProps<{
  data: RaciMatrix
  /** `wbsItemId:memberId` → the letters that cell holds, from `useRaci`. */
  cellIndex: Map<string, { roles: RaciRole[]; assignmentIds: number[] }>
  issuesByTask: Map<number, RaciIssue[]>
}>()

const emit = defineEmits<{
  assign: [wbsItemId: number, memberId: number, role: RaciRole]
  unassign: [assignmentId: number]
}>()

const hasColumns = computed(() => props.data.members.length > 0)

function cell(task: RaciTask, memberId: number) {
  return props.cellIndex.get(`${task.id}:${memberId}`)
}

function assignmentIdFor(task: RaciTask, memberId: number, role: RaciRole): number | null {
  const held = cell(task, memberId)
  if (!held) return null
  const at = held.roles.indexOf(role)
  return at === -1 ? null : held.assignmentIds[at]
}

/** One click either adds the letter or removes the one already there. */
function toggle(task: RaciTask, memberId: number, role: RaciRole) {
  const assignmentId = assignmentIdFor(task, memberId, role)
  if (assignmentId === null) emit('assign', task.id, memberId, role)
  else emit('unassign', assignmentId)
}

function rowIssues(task: RaciTask): RaciIssue[] {
  return props.issuesByTask.get(task.id) ?? []
}

function rowIssueTitle(task: RaciTask): string {
  return rowIssues(task)
    .map((issue) =>
      issue.memberNames.length > 0
        ? `${issueSummary(issue.type)}: ${issue.memberNames.join(', ')}`
        : issueSummary(issue.type),
    )
    .join(' · ')
}

function cellTitle(task: RaciTask, memberName: string, role: RaciRole): string {
  return `${task.code} ${task.name} · ${memberName} · ${RACI_LABELS[role]}(${RACI_LETTERS[role]})`
}
</script>

<template>
  <p v-if="data.tasks.length === 0" class="empty">
    WBS 항목이 없습니다. WBS 화면에서 업무를 먼저 등록해 주세요.
  </p>

  <p v-else-if="!hasColumns" class="empty">
    구성원이 없습니다. 아래에서 구성원을 등록하면 매트릭스 열이 만들어집니다.
  </p>

  <div v-else class="matrix-wrap">
    <table class="matrix">
      <thead>
        <tr>
          <th class="task-col">업무</th>
          <th v-for="member in data.members" :key="member.id" class="member-col">
            <span class="member-name">{{ member.name }}</span>
            <span v-if="member.position" class="member-position">{{ member.position }}</span>
          </th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="task in data.tasks" :key="task.id" :class="{ summary: task.summary }">
          <th class="task-col" scope="row">
            <span class="code">{{ task.code }}</span>
            <span
              class="name"
              :style="{ paddingLeft: `${(task.level - 1) * 0.75}rem` }"
              :title="task.name"
            >{{ task.name }}</span>
            <span
              v-if="rowIssues(task).length > 0"
              class="row-issue"
              :title="rowIssueTitle(task)"
              aria-hidden="true"
            >!</span>
          </th>

          <td v-for="member in data.members" :key="member.id">
            <div class="letters">
              <button
                v-for="role in RACI_ORDER"
                :key="role"
                type="button"
                class="letter"
                :data-role="role"
                :class="{ held: assignmentIdFor(task, member.id, role) !== null }"
                :title="cellTitle(task, member.name, role)"
                :aria-pressed="assignmentIdFor(task, member.id, role) !== null"
                @click="toggle(task, member.id, role)"
              >{{ RACI_LETTERS[role] }}</button>
            </div>
          </td>
        </tr>
      </tbody>
    </table>
  </div>

  <div v-if="hasColumns && data.tasks.length > 0" class="legend">
    <span v-for="role in RACI_ORDER" :key="role" class="legend-item" :title="RACI_DESCRIPTIONS[role]">
      <span class="letter held" :data-role="role">{{ RACI_LETTERS[role] }}</span>
      {{ RACI_LABELS[role] }}
    </span>
    <span class="legend-item legend-note">
      글자를 눌러 배정하고 다시 눌러 해제합니다. Summary 행은 검증하지 않습니다.
    </span>
  </div>
</template>

<style scoped>
.matrix-wrap {
  overflow-x: auto;
  border: 1px solid var(--border);
  border-radius: 8px;
}

.matrix {
  border-collapse: collapse;
  width: 100%;
  font-size: 0.82rem;
}

.matrix th,
.matrix td {
  border-bottom: 1px solid var(--border-softer);
  padding: 0.3rem 0.5rem;
  text-align: left;
  font-weight: 400;
}

thead th {
  background: var(--surface-alt);
  border-bottom: 1px solid var(--border);
  font-weight: 600;
  color: var(--text-dim);
  font-size: 0.75rem;
  white-space: nowrap;
}

/*
 * 업무 열은 고정한다. 구성원이 늘어나면 가로로 스크롤되는데, 어느 업무의 행인지 놓치면
 * 매트릭스를 읽을 수 없다.
 */
.task-col {
  position: sticky;
  left: 0;
  z-index: 1;
  background: var(--surface);
  min-width: 14rem;
  max-width: 20rem;
  border-right: 1px solid var(--border);
}

thead .task-col {
  background: var(--surface-alt);
}

tbody .task-col {
  display: table-cell;
}

.code {
  display: inline-block;
  min-width: 2.6rem;
  color: var(--text-faint);
  font-size: 0.72rem;
  font-variant-numeric: tabular-nums;
}

.name {
  display: inline-block;
  max-width: 12rem;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: bottom;
}

tr.summary .name {
  font-weight: 600;
}

/* 행의 규칙 위반 표시. 자세한 내용은 위쪽 배너가 문장으로 알려준다. */
.row-issue {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 1.05rem;
  height: 1.05rem;
  margin-left: 0.35rem;
  border-radius: 999px;
  background: var(--warn);
  color: var(--status-fg);
  font-size: 0.68rem;
  font-weight: 700;
  vertical-align: middle;
}

.member-col {
  min-width: 6.5rem;
}

.member-name {
  display: block;
}

.member-position {
  display: block;
  font-weight: 400;
  font-size: 0.68rem;
  color: var(--text-faint);
}

.letters {
  display: flex;
  gap: 0.15rem;
}

.letter {
  width: 1.35rem;
  height: 1.35rem;
  padding: 0;
  border: 1px solid var(--border-input);
  border-radius: 4px;
  background: var(--surface);
  color: var(--text-faint);
  font: inherit;
  font-size: 0.7rem;
  font-weight: 600;
  line-height: 1;
  cursor: pointer;
}

.letter:hover {
  border-color: var(--accent-border);
  color: var(--text-h);
}

.letter.held {
  color: var(--status-fg);
  border-color: transparent;
}

.letter.held[data-role='RESPONSIBLE'] {
  background: var(--raci-responsible);
}

.letter.held[data-role='ACCOUNTABLE'] {
  background: var(--raci-accountable);
}

.letter.held[data-role='CONSULTED'] {
  background: var(--raci-consulted);
}

.letter.held[data-role='INFORMED'] {
  background: var(--raci-informed);
}

.legend {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 0.85rem;
  margin-top: 0.6rem;
  font-size: 0.75rem;
  color: var(--text-faint);
}

.legend-item {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
}

.legend .letter {
  cursor: default;
}

.legend-note {
  margin-left: auto;
}

.empty {
  padding: 2.5rem 1rem;
  text-align: center;
  color: var(--text-faint);
  border: 1px dashed var(--border-dashed);
  border-radius: 8px;
}
</style>
