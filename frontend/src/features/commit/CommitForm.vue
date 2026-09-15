<script setup lang="ts">
import { ref } from 'vue'
import type { CommitInput } from '../../api/commitApi'
import ModalDialog from '../../components/ModalDialog.vue'

const props = defineProps<{
  /** 저장 거부 사유. 용량 초과(409)는 별도의 대화상자로 처리되므로 여기 오지 않는다. */
  error?: string | null
}>()

const emit = defineEmits<{
  submit: [input: CommitInput]
  cancel: []
}>()

const message = ref('')
const committedBy = ref('')

function onSubmit() {
  emit('submit', {
    message: message.value.trim() || null,
    committedBy: committedBy.value.trim() || null,
  })
}
</script>

<template>
  <ModalDialog title="현재 시점 커밋" :error="props.error" @close="emit('cancel')">
    <form class="commit-form" @submit.prevent="onSubmit">
      <p class="hint">
        지금 이 순간의 WBS·간트·RACI·RAID·Backlog·Sprint·진척·대시보드를 판정값까지 그대로
        저장합니다. 계산 로직이 나중에 바뀌어도 이 시점의 숫자는 바뀌지 않습니다. 수정은 할 수
        없고, 삭제만 가능합니다.
      </p>

      <label>
        메시지
        <textarea v-model="message" rows="3" placeholder="예: 1차 검수 완료 (선택)"></textarea>
      </label>

      <label>
        작성자
        <input v-model="committedBy" type="text" placeholder="이름 (선택)" />
      </label>

      <div class="actions">
        <button type="submit">커밋</button>
        <button type="button" class="ghost" @click="emit('cancel')">취소</button>
      </div>
    </form>
  </ModalDialog>
</template>

<style scoped>
.commit-form {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.hint {
  margin: 0;
  font-size: 0.8rem;
  color: var(--text-faint);
  line-height: 1.5;
}

label {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  font-size: 0.85rem;
  color: var(--text-muted);
}

input,
textarea {
  padding: 0.45rem 0.6rem;
  border: 1px solid var(--border-input);
  border-radius: 6px;
  font: inherit;
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
