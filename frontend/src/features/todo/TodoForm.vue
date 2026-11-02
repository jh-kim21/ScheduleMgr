<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import ModalDialog from '../../components/ModalDialog.vue'
import type { ProjectMember } from '../../api/memberApi'
import type { TodoItem, TodoItemInput } from '../../api/todoApi'
import { DEFAULT_TODO_PRIORITY, TODO_PRIORITY_LABELS, TODO_PRIORITY_ORDER } from '../../shared/todo'

const props = defineProps<{
  editing: TodoItem | null
  members: ProjectMember[]
  /** 저장이 거부된 이유. 대화상자 안에 보여야 사용자가 볼 수 있다. */
  error?: string | null
  /**
   * 제출은 emit이 아니라 함수 prop이다 — emit은 결과를 돌려받을 수 없는데, "성공했을 때만
   * 닫는다"는 서버가 받아들였는지를 알아야 한다(CLAUDE.md "폼이 emit으로 제출하면…").
   * 실제 API 호출은 여전히 뷰가 한다(`readOnly` 게이팅 자리가 흩어지지 않게).
   */
  onSubmit: (input: TodoItemInput) => Promise<boolean>
}>()

const emit = defineEmits<{
  /** 저장 성공 또는 취소 — 대화상자를 닫아도 되는 때. */
  close: []
}>()

interface Draft {
  title: string
  priority: TodoItemInput['priority']
  assigneeMemberId: number | null
  /** date input은 비우면 빈 문자열이다 — 내보낼 때 null로 바꾼다. */
  dueDate: string
  memo: string
}

const empty = (): Draft => ({
  title: '',
  priority: DEFAULT_TODO_PRIORITY,
  assigneeMemberId: null,
  dueDate: '',
  memo: '',
})

const form = reactive<Draft>(empty())
const initialSnapshot = ref('')
const submitting = ref(false)

watch(
  () => props.editing,
  (item) => {
    Object.assign(
      form,
      item
        ? {
            title: item.title,
            priority: item.priority,
            assigneeMemberId: item.assigneeMemberId,
            dueDate: item.dueDate ?? '',
            memo: item.memo ?? '',
          }
        : empty(),
    )
    initialSnapshot.value = JSON.stringify(form)
  },
  { immediate: true },
)

const dirty = computed(() => JSON.stringify(form) !== initialSnapshot.value)
const title = computed(() => (props.editing ? `TODO 수정 — ${props.editing.title}` : 'TODO 추가'))
const submittable = computed(() => form.title.trim().length > 0)

/**
 * `v-model.number`를 쓰지 않는다 — `.number`는 null을 0으로 바꾸는데, 서버는 0번 구성원을
 * 찾다가 400을 낸다. 선택지의 값은 `:value`로 바인딩한 실제 값(null 또는 숫자)이라 변환이 필요 없다.
 */
function toInput(): TodoItemInput {
  return {
    title: form.title.trim(),
    priority: form.priority,
    assigneeMemberId: form.assigneeMemberId,
    dueDate: form.dueDate === '' ? null : form.dueDate,
    memo: form.memo.trim() === '' ? null : form.memo,
  }
}

async function submit() {
  if (!submittable.value || submitting.value) return
  submitting.value = true
  try {
    const ok = await props.onSubmit(toInput())
    // 거부되면 입력값을 그대로 둔 채 열어 둔다 — 오류는 대화상자 안에 보인다.
    if (ok) emit('close')
  } finally {
    submitting.value = false
  }
}

/** Ctrl/Cmd+Enter 저장. IME 조합 중에는 마지막 음절이 모델에 없으므로 막는다(`RaidForm`과 같음). */
function onShortcutSave(event: KeyboardEvent) {
  if (event.isComposing) return
  event.preventDefault()
  void submit()
}
</script>

<template>
  <ModalDialog :title="title" size="lg" :error="props.error" :dirty="dirty" @close="emit('close')">
    <form class="todo-form" @submit.prevent="submit">
      <div class="row">
        <label class="grow">
          제목
          <input v-model="form.title" type="text" maxlength="255" placeholder="할 일을 한 줄로" />
        </label>
      </div>

      <div class="row">
        <label>
          우선순위
          <select v-model="form.priority">
            <option v-for="priority in TODO_PRIORITY_ORDER" :key="priority" :value="priority">
              {{ TODO_PRIORITY_LABELS[priority] }}
            </option>
          </select>
        </label>

        <label>
          담당자
          <select v-model="form.assigneeMemberId">
            <option :value="null">미지정</option>
            <option v-for="member in members" :key="member.id" :value="member.id">
              {{ member.name }}
            </option>
          </select>
        </label>

        <label>
          기한
          <input v-model="form.dueDate" type="date" />
        </label>
      </div>

      <div class="row">
        <label class="grow">
          메모
          <textarea
            v-model="form.memo"
            rows="4"
            maxlength="2000"
            placeholder="선택 — Ctrl+Enter로 저장"
            @keydown.ctrl.enter="onShortcutSave"
            @keydown.meta.enter="onShortcutSave"
          ></textarea>
        </label>
      </div>

      <p v-if="members.length === 0" class="field-hint">
        담당자로 지정할 구성원이 없습니다. 프로젝트 화면에서 구성원을 먼저 등록하면 선택할 수 있습니다.
      </p>
      <p v-if="!submittable" class="field-hint">제목을 입력해야 저장할 수 있습니다.</p>

      <div class="dialog-actions">
        <button type="submit" class="primary" :disabled="!submittable || submitting">
          {{ submitting ? '저장 중…' : editing ? '저장' : '추가' }}
        </button>
        <button type="button" :disabled="submitting" @click="emit('close')">취소</button>
      </div>
    </form>
  </ModalDialog>
</template>

<style scoped>
.row {
  display: flex;
  align-items: flex-end;
  gap: 0.6rem;
  flex-wrap: wrap;
  margin-bottom: 0.6rem;
}

label {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  font-size: 0.8rem;
  color: var(--text-muted);
}

label.grow {
  flex: 1;
  min-width: 14rem;
}

label.grow input {
  width: 100%;
  box-sizing: border-box;
}

input,
select,
textarea {
  font-size: 0.85rem;
}

/* 가로로 늘리면 대화상자 폭을 넘어간다. 세로만 허용한다. */
textarea {
  width: 100%;
  box-sizing: border-box;
  resize: vertical;
  min-height: 3.2rem;
  line-height: 1.45;
}

.field-hint {
  margin: 0 0 0.6rem;
  font-size: 0.76rem;
  color: var(--warn-badge-fg);
}
</style>
