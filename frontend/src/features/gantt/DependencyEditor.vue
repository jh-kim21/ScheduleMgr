<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { DependencyInput, GanttData, GanttDependency } from '../../api/ganttApi'

const props = defineProps<{
  data: GanttData
}>()

const emit = defineEmits<{
  add: [input: DependencyInput]
  update: [dependencyId: number, input: DependencyInput]
  remove: [dependencyId: number]
}>()

const predecessorId = ref<number | null>(null)
const successorId = ref<number | null>(null)
const lagDays = ref(0)

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
  if (!submittable.value) return
  emit('add', {
    predecessorId: predecessorId.value!,
    successorId: successorId.value!,
    lagDays: lagDays.value,
  })
  successorId.value = null
  lagDays.value = 0
}

function startEdit(dependency: GanttDependency) {
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
  if (editingId.value === null || !draftSubmittable.value) return
  emit('update', editingId.value, { ...draft.value })
}
</script>

<template>
  <section class="dependencies">
    <h2>선후행 관계</h2>

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

      <span class="arrow" aria-hidden="true">→</span>

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

      <button type="submit" class="primary" :disabled="!submittable">추가</button>
    </form>

    <p class="rule">
      선행 업무가 끝난 뒤 대기 일수만큼 지나서 후행 업무를 시작할 수 있습니다 (대기 0 = 바로 다음 날).
      이 값은 <strong>계획상 간격</strong>이며, 실제 지연은 오늘 날짜와 진행률로 자동 판정됩니다.
    </p>

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
            <button type="button" @click="startEdit(dependency)">수정</button>
            <button type="button" class="danger" @click="emit('remove', dependency.id)">삭제</button>
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

.add-form {
  display: flex;
  align-items: flex-end;
  gap: 0.6rem;
  flex-wrap: wrap;
  padding: 0.85rem;
  border: 1px solid var(--border);
  border-radius: 8px;
}

label {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  font-size: 0.8rem;
  color: var(--text-muted);
}

select,
input {
  padding: 0.4rem 0.55rem;
  border: 1px solid var(--border-input);
  border-radius: 6px;
  font: inherit;
  font-size: 0.85rem;
}

.lag input {
  width: 5rem;
}

.arrow {
  color: var(--text-faint);
  padding-bottom: 0.5rem;
}

button {
  padding: 0.45rem 0.9rem;
  border-radius: 6px;
  border: 1px solid var(--border-input);
  background: var(--surface);
  color: var(--text-muted);
  cursor: pointer;
  font-size: 0.85rem;
}

button.primary {
  border-color: var(--accent);
  background: var(--accent);
  color: var(--accent-fg);
}

button.primary:disabled {
  background: var(--disabled-bg);
  border-color: var(--disabled-border);
  color: var(--disabled-fg);
  cursor: not-allowed;
}

.rule {
  margin-top: 0.5rem;
  font-size: 0.78rem;
  color: var(--text-faint);
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

.actions button.danger {
  color: var(--danger);
  border-color: var(--danger-border);
}

.none {
  margin-top: 0.85rem;
  font-size: 0.85rem;
  color: var(--text-faint);
}
</style>
