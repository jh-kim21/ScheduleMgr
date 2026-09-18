<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import ModalDialog from '../../components/ModalDialog.vue'
import type { Project, ProjectInput, ProjectStatus } from '../../api/projectApi'
import { STATUS_OPTIONS } from './statusLabels'

const props = defineProps<{
  editing: Project | null
  /** 저장이 거부된 이유. 대화상자 안에 보여야 사용자가 볼 수 있다. */
  error?: string | null
  /** 부모(ProjectsView)가 create/update 요청 중일 때 true — 응답이 올 때까지 다시 제출을 막는다. */
  submitting?: boolean
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

const title = computed(() => (props.editing ? '프로젝트 수정' : '새 프로젝트'))

/**
 * 열렸을 때의 값을 찍어 두고 지금 값과 비교한다 — Escape·배경 클릭으로 닫을 때 입력을 잃을
 * 수 있는 경우에만 `ModalDialog`가 한 번 확인하게 한다(`dirty` prop, opt-in). 필드마다 비교
 * 코드를 두는 대신 직렬화해서 통째로 비교한다 — 새 필드가 생겨도 여기를 따로 고칠 필요가 없다.
 */
const initialSnapshot = ref('')

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
    initialSnapshot.value = JSON.stringify(form)
  },
  { immediate: true },
)

const dirty = computed(() => JSON.stringify(form) !== initialSnapshot.value)

function onSubmit() {
  if (!form.name.trim() || props.submitting) return
  emit('submit', { ...form })
  if (!props.editing) {
    Object.assign(form, empty)
  }
}
</script>

<template>
  <ModalDialog :title="title" :error="props.error" :dirty="dirty" @close="emit('cancel')">
    <form class="project-form" @submit.prevent="onSubmit">

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
        <button type="submit" :disabled="submitting">
          {{ submitting ? (editing ? '저장 중…' : '추가 중…') : editing ? '저장' : '추가' }}
        </button>
        <button type="button" class="ghost" :disabled="submitting" @click="emit('cancel')">취소</button>
      </div>
    </form>
  </ModalDialog>
</template>

<style scoped>
.project-form {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

label {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  font-size: 0.85rem;
  color: var(--text-muted);
  flex: 1;
}

.row {
  display: flex;
  gap: 0.75rem;
}

.actions {
  display: flex;
  gap: 0.5rem;
}

/* 강조색 채움 — 취소(.ghost)는 전역 계약과 완전히 같은 값이라 지웠고, 이 제출 버튼만 남긴다.
   `.primary`와 같은 배색이지만 템플릿에 클래스를 붙이는 건 이번 작업 범위가 아니라 그대로 둔다. */
button {
  border: 1px solid var(--accent);
  background: var(--accent);
  color: var(--accent-fg);
}
</style>
