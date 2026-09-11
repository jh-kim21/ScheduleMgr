<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import type { Sprint } from '../api/sprintApi'
import ModalDialog from '../components/ModalDialog.vue'
import SprintForm from '../features/sprint/SprintForm.vue'
import SprintAssignTable from '../features/sprint/SprintAssignTable.vue'
import type { AssignCandidate } from '../features/sprint/assignFilter'
import { useSprints } from '../features/sprint/useSprints'
import { useProjects } from '../features/projects/useProjects'
import { BACKLOG_TYPE_LABELS } from '../shared/backlog'
import { localToday } from '../shared/delay'
import { cancelStartWarning, remainingLabel, SPRINT_STATUS_LABELS, sprintPeriod } from '../shared/sprint'
import { ensureSelection, selectedProjectId } from '../stores/projectSelection'

const { projects, error: projectsError, ensureLoaded: ensureProjects } = useProjects()
const {
  data,
  loading,
  error,
  selectedSprintId,
  selected,
  assignable,
  carryOverTargets,
  ensureLoaded,
  create,
  update,
  remove,
  start,
  cancelStart,
  close,
  assign,
} = useSprints()

const editing = ref<Sprint | null>(null)
const formOpen = ref(false)

/** 종료 대화상자: 이월 대상을 고르는 자리. 종료는 되돌릴 수 없으니 한 번 묻는다. */
const closing = ref<Sprint | null>(null)
const carryOverTo = ref<number | null>(null)

/**
 * 시작 취소 확인 대화상자. 되돌릴 수는 있는 조작(다시 시작할 수 있다)이지만, 각인된 Story Point가
 * 지워지고 완료 표시가 Sprint 기록에서 사라지는 부수효과는 눈에 보이지 않으므로 한 번 확인한다.
 */
const cancellingStart = ref<Sprint | null>(null)

/** 여러 건 배정 중에는 표를 잠그고, 부분 실패했을 때만 사람이 읽을 문장을 남긴다. */
const assigning = ref(false)
const assignMessage = ref<string | null>(null)

function openForm(sprint: Sprint | null) {
  editing.value = sprint
  formOpen.value = true
}

function closeForm() {
  formOpen.value = false
  editing.value = null
}

watch(
  selectedProjectId,
  (id) => {
    closeForm()
    assignMessage.value = null
    if (id !== null) ensureLoaded(id)
  },
  { immediate: true },
)

// 다른 Sprint를 보러 가면 이전 Sprint의 배정 결과 문장이 남아 있을 이유가 없다.
watch(selectedSprintId, () => {
  assignMessage.value = null
})

onMounted(async () => {
  await ensureProjects()
  ensureSelection(projects.value.map((project) => project.id))
})

const today = computed(() => localToday())

/** 미완료 항목 수 — 종료 대화상자에서 "무엇이 이월되는가"를 미리 알려 준다. */
const openItemCount = computed(() =>
  selected.value ? selected.value.items.filter((item) => item.status !== 'DONE').length : 0,
)

async function handleSubmit(input: { name: string; goal: string | null; startDate: string; endDate: string }) {
  const projectId = selectedProjectId.value
  if (projectId === null) return
  const ok = editing.value
    ? await update(projectId, editing.value.id, input)
    : await create(projectId, input)
  if (ok) closeForm()
}

/** SprintAssignTable이 그리는 후보 목록. 배정 가능 여부·순서는 useSprints의 assignable이 정한다. */
const candidates = computed<AssignCandidate[]>(() =>
  assignable.value.map((item) => ({
    id: item.id,
    title: item.title,
    typeLabel: BACKLOG_TYPE_LABELS[item.itemType],
    storyPoint: item.storyPoint,
  })),
)

/**
 * 서버는 한 건씩만 배정하므로 순차로 호출한다. 배정은 서로 독립이라 중간 실패가 이전 성공을
 * 무효로 만들지 않으므로, 실패하면 그 자리에서 멈추고 이미 들어간 것은 그대로 둔다.
 *
 * <p>표의 체크박스 선택은 비우지 않는다 — 성공한 항목은 `assignable`이 줄어들며
 * `SprintAssignTable`이 스스로 정리하고, 실패한(및 뒤에서 시도하지 못한) 항목은 후보에 그대로
 * 남아 선택도 남아 있어야 "거부되면 입력값이 남는다"는 화면 규칙과 일치한다.
 */
async function handleAssignMany(ids: number[]) {
  const projectId = selectedProjectId.value
  const sprintId = selectedSprintId.value
  if (projectId === null || sprintId === null || ids.length === 0) return
  assignMessage.value = null
  assigning.value = true
  let done = 0
  for (const id of ids) {
    const ok = await assign(projectId, sprintId, id)
    if (!ok) break
    done += 1
  }
  assigning.value = false
  // 전부 성공하면 조용히 끝난다 — 표에서 후보가 줄어드는 것이 이미 확인 신호다.
  if (done < ids.length) {
    const stoppedAt = candidates.value.find((c) => c.id === ids[done])?.title ?? '항목'
    assignMessage.value =
      `${ids.length}건 중 ${done}건을 배정했습니다. '${stoppedAt}'에서 멈췄습니다` +
      (error.value ? ` — ${error.value}` : '')
  }
}



function openClose(sprint: Sprint) {
  closing.value = sprint
  carryOverTo.value = carryOverTargets.value[0]?.id ?? null
}

async function confirmClose() {
  const projectId = selectedProjectId.value
  const sprint = closing.value
  closing.value = null
  if (projectId === null || !sprint) return
  await close(projectId, sprint.id, carryOverTo.value)
}

async function confirmCancelStart() {
  const projectId = selectedProjectId.value
  const sprint = cancellingStart.value
  if (projectId === null || !sprint) return
  const ok = await cancelStart(projectId, sprint.id)
  if (ok) cancellingStart.value = null
}
</script>

<template>
  <section>
    <h1>Sprint</h1>

    <p v-if="projectsError" class="error">{{ projectsError }}</p>

    <p v-else-if="projects.length === 0" class="notice">
      먼저 프로젝트를 등록해야 Sprint를 운영할 수 있습니다.
      <RouterLink to="/projects">프로젝트 화면으로 이동</RouterLink>
    </p>

    <template v-else>
      <div class="toolbar">
        <label class="project-picker">
          프로젝트
          <select v-model="selectedProjectId">
            <option v-for="project in projects" :key="project.id" :value="project.id">
              {{ project.name }}
            </option>
          </select>
        </label>
        <span class="team-note" title="한 프로젝트에 한 팀을 전제합니다. 실행 중인 Sprint는 하나뿐입니다.">
          단일 팀
        </span>
        <span v-if="loading" class="loading">불러오는 중…</span>
        <button type="button" class="add" @click="openForm(null)">＋ Sprint 추가</button>
      </div>

      <p v-if="error" class="error">{{ error }}</p>

      <SprintForm
        v-if="formOpen"
        :editing="editing"
        :error="error"
        @submit="handleSubmit"
        @cancel="closeForm"
      />

      <p v-if="data.sprints.length === 0 && !loading" class="notice">
        등록된 Sprint가 없습니다. ＋ Sprint 추가로 첫 Sprint를 계획해 보세요. 배정할 항목은
        <RouterLink to="/backlog">Backlog</RouterLink>에서 만듭니다.
      </p>

      <template v-else>
        <ul class="sprint-list">
          <li
            v-for="sprint in data.sprints"
            :key="sprint.id"
            :class="{ selected: sprint.id === selectedSprintId, active: sprint.status === 'ACTIVE' }"
            @dblclick="openForm(sprint)"
          >
            <!--
              이 목록은 tbody 표가 아니라 div 목록이라 useRowSelection의 tr 지향 가드가 맞지
              않는다 — 행 전체가 이미 이 button 하나뿐이라(다른 중첩 버튼이 없다) 더블클릭을 막을
              대상도 없다. 선택은 이미 selectedSprintId로 있었으므로(Board와 공유) 여기서는
              더블클릭 → 수정 대화상자만 얹는다.
            -->
            <button type="button" class="pick" @click="selectedSprintId = sprint.id">
              <span class="name">{{ sprint.name }}</span>
              <span class="status" :data-status="sprint.status">
                {{ SPRINT_STATUS_LABELS[sprint.status] }}
              </span>
              <span class="period">{{ sprintPeriod(sprint.startDate, sprint.endDate) }}</span>
              <span class="numbers">
                {{ sprint.doneItems }}/{{ sprint.plannedItems }}건 ·
                {{ sprint.donePoints }}/{{ sprint.plannedPoints }} SP
                <template v-if="sprint.blockedItems > 0"> · 차단 {{ sprint.blockedItems }}</template>
                <template v-if="sprint.carriedOverItems > 0"> · 이월 {{ sprint.carriedOverItems }}</template>
              </span>
            </button>
          </li>
        </ul>

        <template v-if="selected">
          <div class="sprint-head">
            <div>
              <h2>
                {{ selected.name }}
                <span class="status" :data-status="selected.status">
                  {{ SPRINT_STATUS_LABELS[selected.status] }}
                </span>
              </h2>
              <p class="meta">
                {{ sprintPeriod(selected.startDate, selected.endDate) }}
                <template v-if="selected.status === 'ACTIVE'">
                  · {{ remainingLabel(selected.endDate, today) }}
                </template>
                <template v-if="selected.closedAt"> · 종료 {{ selected.closedAt.slice(0, 10) }}</template>
              </p>
              <p v-if="selected.goal" class="goal">목표: {{ selected.goal }}</p>
              <p v-else class="goal muted">Sprint Goal이 비어 있습니다.</p>
            </div>

            <div class="sprint-actions">
              <button
                v-if="selected.status === 'PLANNED'"
                type="button"
                :disabled="!selected.canStart"
                :title="selected.canStart ? '이 Sprint를 시작합니다' : '이미 실행 중인 Sprint가 있습니다 (단일 팀)'"
                @click="start(selectedProjectId!, selected.id)"
              >시작</button>
              <button
                v-if="selected.status === 'ACTIVE'"
                type="button"
                @click="openClose(selected)"
              >종료</button>
              <button
                v-if="selected.status === 'ACTIVE'"
                type="button"
                class="ghost"
                @click="cancellingStart = selected"
              >시작 취소</button>
              <button
                v-if="selected.status !== 'CLOSED'"
                type="button"
                class="ghost"
                @click="openForm(selected)"
              >수정</button>
              <button
                v-if="selected.status === 'PLANNED'"
                type="button"
                class="ghost danger"
                :disabled="!selected.canDelete"
                :title="selected.canDelete ? '삭제' : '배정된 항목이 있어 삭제할 수 없습니다'"
                @click="remove(selectedProjectId!, selected.id)"
              >삭제</button>
            </div>
          </div>

          <div v-if="selected.status !== 'CLOSED'" class="assign">
            <span class="filter-label">항목 배정</span>
            <SprintAssignTable :candidates="candidates" :busy="assigning" @assign="handleAssignMany" />
            <p v-if="assignMessage" class="assign-result">{{ assignMessage }}</p>
            <span class="assign-note">
              완료 가능한 Story·Bug만, 그리고 다른 Sprint에 들어 있지 않은 것만 고를 수 있습니다.
            </span>
          </div>

          <p class="to-board">
            카드를 옮기고 차단을 표시하는 곳은 Board 입니다.
            <RouterLink to="/board">이 Sprint 의 Board 열기</RouterLink>
          </p>
        </template>
      </template>
    </template>

    <div v-if="closing" class="dialog" role="dialog" aria-modal="true">
      <div class="dialog-body">
        <h4>Sprint 종료</h4>
        <p class="subject">{{ closing.name }}</p>
        <p class="explain">
          완료된 항목은 이 Sprint의 실적으로 기록되고, 미완료 항목({{ openItemCount }}건)은
          <strong>이월</strong>로 남습니다. 항목의 상태는 바뀌지 않습니다.
        </p>
        <label v-if="carryOverTargets.length > 0">
          미완료 항목을 옮길 Sprint
          <select v-model="carryOverTo">
            <option :value="null">지금은 옮기지 않음</option>
            <option v-for="target in carryOverTargets" :key="target.id" :value="target.id">
              {{ target.name }} ({{ SPRINT_STATUS_LABELS[target.status] }})
            </option>
          </select>
        </label>
        <p v-else class="explain muted">
          옮길 Sprint가 없습니다. 나중에 새 Sprint를 만들어 배정할 수 있습니다.
        </p>
        <div class="dialog-actions">
          <button type="button" @click="confirmClose">종료</button>
          <button type="button" class="ghost" @click="closing = null">취소</button>
        </div>
      </div>
    </div>

    <ModalDialog
      v-if="cancellingStart"
      title="Sprint 시작 취소"
      :error="error"
      @close="cancellingStart = null"
    >
      <p class="subject">{{ cancellingStart.name }}</p>
      <p class="explain">{{ cancelStartWarning(cancellingStart.doneItems) }}</p>
      <p class="explain muted">배정된 항목은 그대로 남고, 상태만 계획으로 돌아갑니다.</p>
      <div class="dialog-actions">
        <button type="button" @click="confirmCancelStart">시작 취소</button>
        <button type="button" class="ghost" @click="cancellingStart = null">닫기</button>
      </div>
    </ModalDialog>
  </section>
</template>

<style scoped>
.to-board {
  font-size: 0.82rem;
  color: var(--text-muted);
  margin: 0.75rem 0 0;
}

h1 {
  font-size: 1.3rem;
  margin: 0 0 1rem;
}

.toolbar {
  display: flex;
  align-items: center;
  gap: 1rem;
  margin-bottom: 1rem;
}

.project-picker {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.85rem;
  color: var(--text-muted);
}

.team-note {
  font-size: 0.7rem;
  padding: 0.1rem 0.45rem;
  border-radius: 999px;
  border: 1px dashed var(--border-dashed);
  color: var(--text-faint);
}

select {
  padding: 0.4rem 0.6rem;
  border: 1px solid var(--border-input);
  border-radius: 6px;
  font: inherit;
  font-size: 0.85rem;
}

.loading {
  font-size: 0.8rem;
  color: var(--text-faint);
}

.toolbar .add {
  margin-left: auto;
}

.error {
  color: var(--danger);
  font-size: 0.9rem;
}

.notice {
  font-size: 0.9rem;
  color: var(--text-muted);
}

.sprint-list {
  list-style: none;
  margin: 0 0 1.25rem;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
}

.sprint-list .pick {
  width: 100%;
  display: flex;
  align-items: baseline;
  gap: 0.6rem;
  padding: 0.5rem 0.6rem;
  border: 1px solid var(--border-soft);
  border-radius: 6px;
  background: var(--surface);
  font: inherit;
  font-size: 0.85rem;
  text-align: left;
  cursor: pointer;
}

/*
 * 다른 표의 `.row-selectable tr.selected`와 같은 모양(강조 배경 + 왼쪽 3px inset 강조선)으로
 * 맞춘다 — 이 목록은 tbody 표가 아니라 button 목록이라 그 전역 유틸리티 선택자(tr/td 지향)를
 * 그대로 쓸 수 없어 같은 토큰으로 손으로 맞췄다.
 */
.sprint-list li.selected .pick {
  background: var(--accent-container);
  color: var(--accent-container-fg);
  box-shadow: inset 3px 0 0 var(--accent);
}

.sprint-list .name {
  font-weight: 600;
}

.sprint-list .period,
.sprint-list .numbers {
  font-size: 0.76rem;
  color: var(--text-faint);
}

.sprint-list .numbers {
  margin-left: auto;
  font-variant-numeric: tabular-nums;
}

.status {
  font-size: 0.68rem;
  padding: 0.05rem 0.4rem;
  border-radius: 999px;
  border: 1px solid var(--border-dashed);
  color: var(--text-muted);
  white-space: nowrap;
}

.status[data-status='ACTIVE'] {
  border-color: var(--accent-border);
  color: var(--accent);
}

.status[data-status='CLOSED'] {
  color: var(--text-faint);
}

.sprint-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 1rem;
  flex-wrap: wrap;
  margin-bottom: 0.75rem;
}

.sprint-head h2 {
  margin: 0;
  font-size: 1.05rem;
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.sprint-head .meta,
.sprint-head .goal {
  margin: 0.2rem 0 0;
  font-size: 0.82rem;
  color: var(--text-muted);
}

.sprint-head .goal.muted {
  color: var(--text-faint);
}

.sprint-actions {
  display: flex;
  gap: 0.4rem;
  flex-wrap: wrap;
}

.assign {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
  margin-bottom: 0.9rem;
}

.filter-label {
  font-size: 0.72rem;
  color: var(--text-dim);
}

.assign-result {
  margin: 0;
  font-size: 0.8rem;
  color: var(--text-muted);
}

.assign-note {
  font-size: 0.75rem;
  color: var(--text-faint);
}

button {
  padding: 0.4rem 0.8rem;
  border-radius: 6px;
  border: 1px solid var(--accent);
  background: var(--accent);
  color: var(--accent-fg);
  cursor: pointer;
  font: inherit;
  font-size: 0.82rem;
  white-space: nowrap;
}

button.ghost {
  background: transparent;
  color: var(--text-muted);
  border-color: var(--border-input);
}

button.ghost.danger {
  color: var(--danger);
  border-color: var(--danger-border);
}

button:disabled {
  background: var(--disabled-bg);
  color: var(--disabled-fg);
  border-color: var(--border-soft);
  cursor: not-allowed;
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
  max-width: 28rem;
}

.dialog-body h4 {
  margin: 0 0 0.5rem;
  font-size: 0.95rem;
}

.dialog-body .subject,
.subject {
  margin: 0 0 0.4rem;
  font-weight: 600;
  font-size: 0.9rem;
}

.dialog-body .explain,
.explain {
  margin: 0 0 0.6rem;
  font-size: 0.84rem;
  color: var(--text-muted);
}

.dialog-body .explain.muted,
.explain.muted {
  color: var(--text-faint);
}

.dialog-body label {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  font-size: 0.82rem;
  color: var(--text-muted);
}

.dialog-actions {
  display: flex;
  gap: 0.5rem;
  margin-top: 0.9rem;
}
</style>
