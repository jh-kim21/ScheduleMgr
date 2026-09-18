<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { DependencyInput, GanttData, GanttDependency } from '../../api/ganttApi'
import ModalDialog from '../../components/ModalDialog.vue'

const props = defineProps<{
  data: GanttData
  /** 저장이 거부된 이유(순환·중복 등). 대화상자 안에 보여야 사용자가 볼 수 있다. */
  error?: string | null
  /** 커밋 시점 조회 중이면 추가·수정·삭제를 모두 막는다. */
  readOnly?: boolean
}>()

/** 5.2 표의 화면 전체 공통 문구 — 화면마다 문구를 지어내지 않는다. */
const READONLY_HINT = '커밋 시점 조회 중에는 변경할 수 없습니다'

const emit = defineEmits<{
  add: [input: DependencyInput]
  update: [dependencyId: number, input: DependencyInput]
  remove: [dependencyId: number]
}>()

const predecessorId = ref<number | null>(null)
const successorId = ref<number | null>(null)
const lagDays = ref(0)
/** 추가 폼은 대화상자로 띄운다 — 이 화면의 주된 행위는 차트를 읽는 것이다. */
const addOpen = ref(false)
/** 방금 보낸 추가 요청. 목록에 나타나면 서버가 받아들인 것이므로 그때 닫는다. */
let pendingAdd: DependencyInput | null = null

/** The row being edited in place, and the values it is being edited to. */
const editingId = ref<number | null>(null)
const draft = ref<DependencyInput>({ predecessorId: 0, successorId: 0, lagDays: 0 })

const taskLabels = computed(
  () => new Map(props.data.tasks.map((task) => [task.id, `${task.code} ${task.name}`])),
)

// Keep the pickers pointing at tasks that still exist after a WBS change.
watch(
  () => props.data.tasks,
  (tasks) => {
    const ids = new Set(tasks.map((task) => task.id))
    if (predecessorId.value !== null && !ids.has(predecessorId.value)) predecessorId.value = null
    if (successorId.value !== null && !ids.has(successorId.value)) successorId.value = null
    // An open draft pointing at a deleted task can no longer be saved, so drop it rather than
    // leaving a select with no matching option.
    if (
      editingId.value !== null &&
      (!ids.has(draft.value.predecessorId) || !ids.has(draft.value.successorId))
    ) {
      cancelEdit()
    }
  },
)

// A rejected edit leaves the chart data untouched, so the draft stays open next to the error
// message; only an accepted one brings back a row holding the values we sent.
watch(
  () => props.data.dependencies,
  (dependencies) => {
    if (
      pendingAdd &&
      dependencies.some(
        (dependency) =>
          dependency.predecessorId === pendingAdd!.predecessorId &&
          dependency.successorId === pendingAdd!.successorId,
      )
    ) {
      pendingAdd = null
      addOpen.value = false
      successorId.value = null
      lagDays.value = 0
    }

    if (editingId.value === null) return
    const saved = dependencies.find((dependency) => dependency.id === editingId.value)
    if (!saved) {
      cancelEdit()
      return
    }
    if (
      saved.predecessorId === draft.value.predecessorId &&
      saved.successorId === draft.value.successorId &&
      saved.lagDays === draft.value.lagDays
    ) {
      cancelEdit()
    }
  },
)

const submittable = computed(
  () =>
    predecessorId.value !== null &&
    successorId.value !== null &&
    predecessorId.value !== successorId.value,
)

/** The server rejects self-links, duplicates and cycles too; this only blocks the obvious case. */
const draftSubmittable = computed(() => draft.value.predecessorId !== draft.value.successorId)

function onSubmit() {
  if (props.readOnly || !submittable.value) return
  pendingAdd = {
    predecessorId: predecessorId.value!,
    successorId: successorId.value!,
    lagDays: lagDays.value,
  }
  emit('add', pendingAdd)
}

function openAdd() {
  if (props.readOnly) return
  pendingAdd = null
  addOpen.value = true
}

function startEdit(dependency: GanttDependency) {
  if (props.readOnly) return
  editingId.value = dependency.id
  draft.value = {
    predecessorId: dependency.predecessorId,
    successorId: dependency.successorId,
    lagDays: dependency.lagDays,
  }
}

function cancelEdit() {
  editingId.value = null
}

function onSave() {
  if (props.readOnly || editingId.value === null || !draftSubmittable.value) return
  emit('update', editingId.value, { ...draft.value })
}
</script>

<template>
  <section class="dependencies">
    <header class="section-head">
      <h2>선후행 관계</h2>
      <button
        type="button"
        class="add"
        :disabled="readOnly || data.tasks.length < 2"
        :title="readOnly ? READONLY_HINT : undefined"
        @click="openAdd"
      >
        ＋ 관계 추가
      </button>
    </header>

    <ModalDialog
      v-if="addOpen"
      title="선후행 관계 추가"
      :error="props.error"
      @close="addOpen = false"
    >
      <form class="add-form" @submit.prevent="onSubmit">
        <label>
          선행 업무
          <select v-model="predecessorId">
            <option :value="null" disabled>선택</option>
            <option v-for="task in data.tasks" :key="`pred-${task.id}`" :value="task.id">
              {{ task.code }} {{ task.name }}
            </option>
          </select>
        </label>

        <label>
          후행 업무
          <select v-model="successorId">
            <option :value="null" disabled>선택</option>
            <option v-for="task in data.tasks" :key="`succ-${task.id}`" :value="task.id">
              {{ task.code }} {{ task.name }}
            </option>
          </select>
        </label>

        <label class="lag">
          대기(일)
          <input v-model.number="lagDays" type="number" min="0" />
        </label>

        <p class="rule">
          선행 업무가 끝난 뒤 대기 일수만큼 지나서 후행 업무를 시작할 수 있습니다 (대기 0 = 바로 다음 날).
          이 값은 <strong>계획상 간격</strong>이며, 실제 지연은 오늘 날짜와 진행률로 자동 판정됩니다.
        </p>

        <div class="dialog-actions">
          <button type="submit" class="primary" :disabled="!submittable">추가</button>
          <button type="button" @click="addOpen = false">취소</button>
        </div>
      </form>
    </ModalDialog>

    <ul v-if="data.dependencies.length > 0" class="list">
      <li
        v-for="dependency in data.dependencies"
        :key="dependency.id"
        :class="{ editing: editingId === dependency.id }"
      >
        <template v-if="editingId === dependency.id">
          <select v-model="draft.predecessorId" aria-label="선행 업무">
            <option v-for="task in data.tasks" :key="`edit-pred-${task.id}`" :value="task.id">
              {{ task.code }} {{ task.name }}
            </option>
          </select>

          <span class="arrow" aria-hidden="true">→</span>

          <select v-model="draft.successorId" aria-label="후행 업무">
            <option v-for="task in data.tasks" :key="`edit-succ-${task.id}`" :value="task.id">
              {{ task.code }} {{ task.name }}
            </option>
          </select>

          <label class="lag inline">
            대기(일)
            <input v-model.number="draft.lagDays" type="number" min="0" />
          </label>

          <span class="actions">
            <button type="button" class="primary" :disabled="!draftSubmittable" @click="onSave">
              저장
            </button>
            <button type="button" @click="cancelEdit">취소</button>
          </span>
        </template>

        <template v-else>
          <span>{{ taskLabels.get(dependency.predecessorId) ?? '?' }}</span>
          <span class="arrow" aria-hidden="true">→</span>
          <span>{{ taskLabels.get(dependency.successorId) ?? '?' }}</span>
          <span v-if="dependency.lagDays > 0" class="lag-badge">
            대기 {{ dependency.lagDays }}일
          </span>

          <span class="actions">
            <button
              type="button"
              :disabled="readOnly"
              :title="readOnly ? READONLY_HINT : undefined"
              @click="startEdit(dependency)"
            >수정</button>
            <button
              type="button"
              class="danger"
              :disabled="readOnly"
              :title="readOnly ? READONLY_HINT : undefined"
              @click="emit('remove', dependency.id)"
            >삭제</button>
          </span>
        </template>
      </li>
    </ul>
    <p v-else class="none">등록된 선후행 관계가 없습니다.</p>
  </section>
</template>

<style scoped>
.dependencies {
  margin-top: 1.75rem;
}

h2 {
  font-size: 1rem;
  margin: 0 0 0.75rem;
}

.section-head {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  margin-bottom: 0.75rem;
}

.section-head h2 {
  margin: 0;
}

/* border·배경·font·cursor는 전역 기본과 같아 지웠다 — 알약 모양(radius)과 색·크기만 남는다.
   :disabled는 전역이 특이도로 이미 이기고 있던 죽은 선언이라(값도 같음) 지웠다. */
.add {
  margin-left: auto;
  padding: 0.35rem 0.7rem;
  border-radius: 999px;
  color: var(--text-muted);
  font-size: 0.78rem;
  white-space: nowrap;
}

.add:hover:not(:disabled) {
  border-color: var(--accent-border);
  color: var(--text-h);
}

.add-form {
  display: flex;
  flex-direction: column;
  gap: 0.7rem;
}

.dialog-actions {
  display: flex;
  gap: 0.5rem;
}

label {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  font-size: 0.8rem;
  color: var(--text-muted);
}

/* 패딩·border·radius·font는 전역 기본과 같아(패딩은 아주 살짝만 다름) 지웠다 — font-size만 남긴다. */
select,
input {
  font-size: 0.85rem;
}

.lag input {
  width: 5rem;
}

.arrow {
  color: var(--text-faint);
  padding-bottom: 0.5rem;
}

/* 패딩·radius·border·배경·cursor는 전역 기본과 완전히 같은 값이라 지웠다. `.primary`와 그
   :disabled 변형도 전역 계약과 같아 통째로 지웠다. */
button {
  color: var(--text-muted);
  font-size: 0.85rem;
}

.rule {
  font-size: 0.78rem;
  color: var(--text-faint);
  line-height: 1.5;
}

.list {
  list-style: none;
  padding: 0;
  margin: 0.85rem 0 0;
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
}

.list li {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.85rem;
  padding: 0.4rem 0.6rem;
  border: 1px solid var(--border-soft);
  border-radius: 6px;
}

/* 편집 중인 행은 폼이므로 주변 행과 구분되게 강조한다. */
.list li.editing {
  border-color: var(--accent-border);
  background: var(--accent-weak);
  flex-wrap: wrap;
}

.list .arrow {
  padding: 0;
}

/* 행 안에서는 라벨을 좁게 눕혀 컨트롤 높이를 목록 리듬에 맞춘다. */
.list .lag.inline {
  flex-direction: row;
  align-items: center;
  gap: 0.35rem;
  font-size: 0.78rem;
  color: var(--text-dim);
}

.list .lag.inline input {
  width: 4rem;
  padding: 0.25rem 0.4rem;
  font-size: 0.8rem;
}

.list select {
  padding: 0.25rem 0.4rem;
  font-size: 0.8rem;
}

.lag-badge {
  font-size: 0.72rem;
  color: var(--warn-badge-fg);
  background: var(--warn-badge-bg);
  border-radius: 999px;
  padding: 0.1rem 0.5rem;
}

.actions {
  margin-left: auto;
  display: flex;
  gap: 0.35rem;
}

.actions button {
  padding: 0.25rem 0.55rem;
  font-size: 0.75rem;
}

/* `.danger`가 전역 계약과 완전히 같은 값이라 지웠다. */

.none {
  margin-top: 0.85rem;
  font-size: 0.85rem;
  color: var(--text-faint);
}
</style>
