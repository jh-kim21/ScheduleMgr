<script setup lang="ts">
import { computed, ref } from 'vue'
import ModalDialog from '../../components/ModalDialog.vue'
import type { WbsNode } from '../../api/wbsApi'
import { downloadCsv, toCsv } from '../../shared/csv'

const props = defineProps<{
  tree: WbsNode[]
  /** 저장이 거부된 이유. 대화상자 안에 보여야 사용자가 볼 수 있다(줄마다 한 행의 사유). */
  error?: string | null
}>()

const emit = defineEmits<{
  submit: [input: { file: File; parentId: number | null }]
  cancel: []
}>()

const file = ref<File | null>(null)
const parentId = ref<number | null>(null)

/**
 * 상위로 고를 수 있는 것은 Summary뿐이다 — Work Package 아래에 하위를 두는 것은 이 화면의 단일 항목
 * 추가에서도 막혀 있다("하위" 버튼 비활성). 파일 가져오기라고 그 규칙을 우회할 이유가 없다.
 */
function flattenSummaries(nodes: WbsNode[]): WbsNode[] {
  return nodes.flatMap((node) =>
    node.nodeType === 'SUMMARY' ? [node, ...flattenSummaries(node.children)] : [],
  )
}
const summaryOptions = computed(() => flattenSummaries(props.tree))

function onFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  file.value = input.files?.[0] ?? null
}

function onSubmit() {
  if (!file.value) return
  emit('submit', { file: file.value, parentId: parentId.value })
}

/** 열 순서를 직접 설명하는 대신 그대로 채워서 내려주는 예시 파일. */
function downloadTemplate() {
  const header = ['레벨', '업무명', '시작일', '종료일', '진행률']
  const rows = [
    [1, '요구 분석', '2026-01-05', '2026-01-30', 0],
    [2, '현황 조사', '2026-01-05', '2026-01-12', 0],
    [2, '요구사항 정의', '2026-01-13', '2026-01-30', 0],
    [1, '설계', '', '', 0],
  ]
  downloadCsv('wbs-가져오기-양식.csv', toCsv(header, rows))
}
</script>

<template>
  <ModalDialog title="파일에서 가져오기 (Excel · CSV)" :error="props.error" @close="emit('cancel')">
    <form class="import-form" @submit.prevent="onSubmit">
      <p class="hint muted">
        열 순서는 <strong>레벨 · 업무명 · 시작일 · 종료일 · 진행률</strong> 로 고정입니다. 첫 행은
        머리글로 보고 건너뜁니다. 레벨은 1부터 시작하는 들여쓰기 단계이고, 한 단계씩만 깊어질 수
        있습니다. 시작일·종료일·진행률은 비워 둘 수 있습니다.
      </p>

      <label>
        파일
        <input type="file" accept=".csv,.xlsx,.xls" required @change="onFileChange" />
      </label>

      <label>
        상위 항목
        <select v-model="parentId">
          <option :value="null">(최상위)</option>
          <option v-for="node in summaryOptions" :key="node.id" :value="node.id">
            {{ node.code }} {{ node.name }}
          </option>
        </select>
      </label>
      <p class="hint muted">
        여기서 고른 항목의 맨 끝 하위로 붙습니다. 기존 항목은 건드리지 않습니다.
      </p>

      <div class="actions">
        <button type="submit" :disabled="!file">가져오기</button>
        <button type="button" class="ghost" @click="downloadTemplate">양식 CSV 내려받기</button>
        <button type="button" class="ghost" @click="emit('cancel')">취소</button>
      </div>
    </form>
  </ModalDialog>
</template>

<style scoped>
.import-form {
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
}

input,
select {
  padding: 0.45rem 0.6rem;
  border: 1px solid var(--border-input);
  border-radius: 6px;
  font: inherit;
}

.hint.muted {
  font-size: 0.8rem;
  color: var(--text-muted);
  background: var(--surface-sunken);
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

button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

button.ghost {
  background: transparent;
  color: var(--text-muted);
  border-color: var(--border-input);
}
</style>
