<script setup lang="ts">
import { computed, reactive, watch } from 'vue'
import type { WbsItemInput, WbsNode } from '../../api/wbsApi'

const props = defineProps<{
  editing: WbsNode | null
  parent: WbsNode | null
}>()

const emit = defineEmits<{
  submit: [input: WbsItemInput]
  cancel: []
}>()

const empty: WbsItemInput = {
  name: '',
  description: '',
  startDate: null,
  endDate: null,
  progress: 0,
}

const form = reactive<WbsItemInput>({ ...empty })

/**
 * A summary node's schedule and progress are rolled up from its children, so editing them
 * here would be silently discarded — the inputs are disabled instead.
 */
const rolledUp = computed(() => props.editing?.summary ?? false)

const title = computed(() => {
  if (props.editing) return `항목 수정 — ${props.editing.code} ${props.editing.name}`
  if (props.parent) return `하위 항목 추가 — ${props.parent.code} ${props.parent.name}`
  return '최상위 항목 추가'
})

watch(
  () => props.editing,
  (item) => {
    if (item) {
      form.name = item.name
      form.description = item.description ?? ''
      form.startDate = item.startDate
      form.endDate = item.endDate
      form.progress = item.progress
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
  <form class="wbs-form" @submit.prevent="onSubmit">
    <h2>{{ title }}</h2>

    <div class="row">
      <label class="grow">
        업무명
        <input v-model="form.name" type="text" required placeholder="업무명" />
      </label>
      <label class="grow">
        설명
        <input v-model="form.description" type="text" placeholder="설명 (선택)" />
      </label>
    </div>

    <div class="row">
      <label>
        시작일
        <input v-model="form.startDate" type="date" :disabled="rolledUp" />
      </label>
      <label>
        종료일
        <input v-model="form.endDate" type="date" :disabled="rolledUp" />
      </label>
      <label>
        진행률 (%)
        <input v-model.number="form.progress" type="number" min="0" max="100" :disabled="rolledUp" />
      </label>
    </div>

    <p v-if="rolledUp" class="hint">
      하위 항목이 있는 Summary 항목입니다. 일정과 진행률은 하위 항목에서 자동 집계되므로 직접 입력할 수 없습니다.
    </p>

    <div class="actions">
      <button type="submit">{{ editing ? '저장' : '추가' }}</button>
      <button v-if="editing || parent" type="button" class="ghost" @click="emit('cancel')">취소</button>
    </div>
  </form>
</template>

<style scoped>
.wbs-form {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  padding: 1rem;
  border: 1px solid var(--border);
  border-radius: 8px;
  margin-bottom: 1.5rem;
}

.wbs-form h2 {
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
}

input {
  padding: 0.45rem 0.6rem;
  border: 1px solid var(--border-input);
  border-radius: 6px;
  font: inherit;
}

input:disabled {
  background: var(--surface-sunken);
  color: var(--text-faint);
}

.row {
  display: flex;
  gap: 0.75rem;
}

.hint {
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
</style>
