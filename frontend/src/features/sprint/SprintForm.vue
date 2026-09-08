<script setup lang="ts">
import { computed, reactive, watch } from 'vue'
import type { Sprint, SprintInput } from '../../api/sprintApi'

const props = defineProps<{
  editing: Sprint | null
}>()

const emit = defineEmits<{
  submit: [input: SprintInput]
  cancel: []
}>()

const empty: SprintInput = {
  name: '',
  goal: '',
  startDate: '',
  endDate: '',
}

const form = reactive<SprintInput>({ ...empty })

const title = computed(() =>
  props.editing ? `Sprint 수정 — ${props.editing.name}` : 'Sprint 추가',
)

/** 기간이 거꾸로인 것은 서버도 거부한다. 보내기 전에 알려 준다. */
const backwards = computed(
  () => !!form.startDate && !!form.endDate && form.endDate < form.startDate,
)

watch(
  () => props.editing,
  (sprint) => {
    if (sprint) {
      form.name = sprint.name
      form.goal = sprint.goal ?? ''
      form.startDate = sprint.startDate
      form.endDate = sprint.endDate
    } else {
      Object.assign(form, empty)
    }
  },
  { immediate: true },
)

function onSubmit() {
  if (!form.name.trim() || !form.startDate || !form.endDate || backwards.value) return
  emit('submit', { ...form, goal: form.goal?.trim() || null })
}
</script>

<template>
  <form class="sprint-form" @submit.prevent="onSubmit">
    <h2>{{ title }}</h2>

    <div class="row">
      <label class="grow">
        이름
        <input v-model="form.name" type="text" required placeholder="예: Sprint 1" />
      </label>
      <label>
        시작일
        <input v-model="form.startDate" type="date" required />
      </label>
      <label>
        종료일
        <input v-model="form.endDate" type="date" required />
      </label>
    </div>

    <label class="grow">
      Sprint Goal
      <input v-model="form.goal" type="text" placeholder="이번 Sprint에서 달성할 목표 (선택)" />
    </label>

    <p v-if="backwards" class="hint">종료일이 시작일보다 앞설 수 없습니다.</p>

    <div class="actions">
      <button type="submit" :disabled="backwards">{{ editing ? '저장' : '추가' }}</button>
      <button type="button" class="ghost" @click="emit('cancel')">취소</button>
    </div>
  </form>
</template>

<style scoped>
.sprint-form {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  padding: 1rem;
  border: 1px solid var(--border);
  border-radius: 8px;
  margin-bottom: 1.25rem;
}

.sprint-form h2 {
  margin: 0;
  font-size: 1rem;
}

label {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  font-size: 0.85rem;
  color: var(--text-muted);
}

label.grow {
  flex: 1;
  min-width: 0;
}

input {
  padding: 0.45rem 0.6rem;
  border: 1px solid var(--border-input);
  border-radius: 6px;
  font: inherit;
  min-width: 0;
}

.row {
  display: flex;
  gap: 0.75rem;
}

.hint {
  margin: 0;
  font-size: 0.8rem;
  color: var(--warn-badge-fg);
  background: var(--warn-weak);
  border-radius: 6px;
  padding: 0.5rem 0.65rem;
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

button:disabled {
  background: var(--disabled-bg);
  color: var(--disabled-fg);
  border-color: var(--border-soft);
  cursor: not-allowed;
}
</style>
