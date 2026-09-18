<script setup lang="ts">
import { computed, ref } from 'vue'
import type { CommitInput } from '../../api/commitApi'
import ModalDialog from '../../components/ModalDialog.vue'

const props = defineProps<{
  /** 저장 거부 사유. 용량 초과(409)는 별도의 대화상자로 처리되므로 여기 오지 않는다. */
  error?: string | null
  /** 부모(CommitPanel)가 커밋 요청 중일 때 true — 응답이 올 때까지 다시 제출을 막는다. */
  submitting?: boolean
}>()

const emit = defineEmits<{
  submit: [input: CommitInput]
  cancel: []
}>()

const message = ref('')
const committedBy = ref('')

/**
 * 이 폼은 `editing` 개념이 없다 — 열 때마다(`v-if="formOpen"`) 새로 마운트되어 두 칸 다 빈
 * 값으로 시작하므로, "바뀌었는가"는 그냥 "뭔가 적었는가"와 같다(`ModalDialog`의 `dirty` prop,
 * opt-in — Escape·배경 클릭으로 닫을 때 한 번 확인한다).
 */
const dirty = computed(() => message.value.trim().length > 0 || committedBy.value.trim().length > 0)

function onSubmit() {
  if (props.submitting) return
  emit('submit', {
    message: message.value.trim() || null,
    committedBy: committedBy.value.trim() || null,
  })
}
</script>

<template>
  <ModalDialog title="현재 시점 커밋" :error="props.error" :dirty="dirty" @close="emit('cancel')">
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
        <button type="submit" :disabled="submitting">{{ submitting ? '커밋하는 중…' : '커밋' }}</button>
        <button type="button" class="ghost" :disabled="submitting" @click="emit('cancel')">취소</button>
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

.actions {
  display: flex;
  gap: 0.5rem;
}

/* 강조색 채움 — 취소(.ghost)는 전역 계약과 완전히 같은 값이라 지웠고, 이 제출 버튼만 남긴다. */
button {
  border: 1px solid var(--accent);
  background: var(--accent);
  color: var(--accent-fg);
}
</style>
