<script setup lang="ts">
import { reactive, watch } from 'vue'
import type { Project, ProjectInput, ProjectStatus } from '../../api/projectApi'
import { STATUS_OPTIONS } from './statusLabels'

const props = defineProps<{
  editing: Project | null
}>()

const emit = defineEmits<{
  submit: [input: ProjectInput]
  cancel: []
}>()

const empty: ProjectInput = {
  name: '',
  description: '',
  status: 'PLANNED' as ProjectStatus,
  startDate: null,
  endDate: null,
}

const form = reactive<ProjectInput>({ ...empty })

watch(
  () => props.editing,
  (project) => {
    if (project) {
      form.name = project.name
      form.description = project.description ?? ''
      form.status = project.status
      form.startDate = project.startDate
      form.endDate = project.endDate
    } else {
      Object.assign(form, empty)
    }
  },
  { immediate: true },
)

function onSubmit() {
  if (!form.name.trim()) return
  emit('submit', { ...form })
  if (!props.editing) {
    Object.assign(form, empty)
  }
}
</script>

<template>
  <form class="project-form" @submit.prevent="onSubmit">
    <h2>{{ editing ? '프로젝트 수정' : '새 프로젝트' }}</h2>

    <label>
      이름
      <input v-model="form.name" type="text" required placeholder="프로젝트 이름" />
    </label>

    <label>
      설명
      <textarea v-model="form.description" rows="2" placeholder="프로젝트 설명"></textarea>
    </label>

    <div class="row">
      <label>
        상태
        <select v-model="form.status">
          <option v-for="[value, label] in STATUS_OPTIONS" :key="value" :value="value">
            {{ label }}
          </option>
        </select>
      </label>

      <label>
        시작일
        <input v-model="form.startDate" type="date" />
      </label>

      <label>
        종료일
        <input v-model="form.endDate" type="date" />
      </label>
    </div>

    <div class="actions">
      <button type="submit">{{ editing ? '저장' : '추가' }}</button>
      <button v-if="editing" type="button" class="ghost" @click="emit('cancel')">취소</button>
    </div>
  </form>
</template>

<style scoped>
.project-form {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  padding: 1rem;
  border: 1px solid var(--border);
  border-radius: 8px;
  margin-bottom: 1.5rem;
}

.project-form h2 {
  margin: 0 0 0.25rem;
  font-size: 1.1rem;
}

label {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  font-size: 0.85rem;
  color: var(--text-muted);
  flex: 1;
}

input,
textarea,
select {
  padding: 0.45rem 0.6rem;
  border: 1px solid var(--border-input);
  border-radius: 6px;
  font: inherit;
}

.row {
  display: flex;
  gap: 0.75rem;
}

.actions {
  display: flex;
  gap: 0.5rem;
}

button {
  padding: 0.5rem 1rem;
  border-radius: 6px;
  border: 1px solid var(--accent);
  background: var(--accent);
  color: var(--accent-fg);
  cursor: pointer;
}

button.ghost {
  background: transparent;
  color: var(--text-muted);
  border-color: var(--border-input);
}
</style>
