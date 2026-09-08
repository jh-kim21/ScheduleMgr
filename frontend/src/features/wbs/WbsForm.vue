<script setup lang="ts">
import { computed, reactive, watch } from 'vue'
import ModalDialog from '../../components/ModalDialog.vue'
import type { WbsItemInput, WbsNode } from '../../api/wbsApi'
import {
  ACCEPTANCE_STATUS_LABELS,
  ACCEPTANCE_STATUS_ORDER,
} from '../../shared/progress'
import {
  EXECUTION_MODE_LABELS,
  EXECUTION_MODE_ORDER,
  NODE_TYPE_LABELS,
  executionModeLabel,
} from '../../shared/executionMode'

const props = defineProps<{
  editing: WbsNode | null
  parent: WbsNode | null
  /** 저장이 거부된 이유. 대화상자 안에 보여야 사용자가 볼 수 있다. */
  error?: string | null
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
  weight: null,
  agileRatio: null,
  acceptanceStatus: null,
  // 새 항목은 자식이 없으니 최하위 관리 단위로 시작한다. 서버 기본값과 같다.
  nodeType: 'WORK_PACKAGE',
  executionMode: null,
}

const form = reactive<WbsItemInput>({ ...empty })

/**
 * A summary node's schedule and progress are rolled up from its children, so editing them
 * here would be silently discarded — the inputs are disabled instead.
 */
const rolledUp = computed(() => props.editing?.summary ?? false)

/** 실행 방식은 최하위 Work Package의 것이다 (설계 §5). Summary는 하위 요약만 보여준다. */
const modeDisabled = computed(() => form.nodeType === 'SUMMARY')

/**
 * A summary still holding a mode from before it was converted. The value is kept on purpose so
 * converting back restores it, but it is not in effect — saying so is better than showing a
 * disabled dropdown with a value in it and no explanation.
 */
const retainedMode = computed(() =>
  form.nodeType === 'SUMMARY' && form.executionMode ? executionModeLabel(form.executionMode) : null,
)

/** 하위가 있는 항목을 Work Package로 되돌릴 수는 없다 — 서버도 거부한다. */
const canBeWorkPackage = computed(() => (props.editing?.children.length ?? 0) === 0)

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
      form.weight = item.weight
      form.agileRatio = item.agileRatio
      form.acceptanceStatus = item.acceptanceStatus
      form.nodeType = item.nodeType
      // 보관된 값도 그대로 담아 되돌려 보낸다. Summary에서 값을 비워 보내면 서버가
      // "실행 방식을 바꾸려 한다"고 보고 거부한다.
      form.executionMode = item.executionMode
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
  <ModalDialog :title="title" :error="props.error" @close="emit('cancel')">
    <form class="wbs-form" @submit.prevent="onSubmit">

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

      <div class="row">
        <label>
          구분
          <select v-model="form.nodeType">
            <option value="WORK_PACKAGE" :disabled="!canBeWorkPackage">
              {{ NODE_TYPE_LABELS.WORK_PACKAGE }}
            </option>
            <option value="SUMMARY">{{ NODE_TYPE_LABELS.SUMMARY }}</option>
          </select>
        </label>
        <label>
          실행 방식
          <select v-model="form.executionMode" :disabled="modeDisabled">
            <option :value="null">미지정</option>
            <option v-for="mode in EXECUTION_MODE_ORDER" :key="mode" :value="mode">
              {{ EXECUTION_MODE_LABELS[mode] }}
            </option>
          </select>
        </label>
      </div>

      <div class="row">
        <label>
          가중치
          <input v-model.number="form.weight" type="number" min="0" placeholder="형제 간 비중" />
        </label>
        <label>
          Hybrid 비중 α (%)
          <input
            v-model.number="form.agileRatio"
            type="number"
            min="0"
            max="100"
            :disabled="form.executionMode !== 'HYBRID'"
            placeholder="Agile 요소 비중"
          />
        </label>
        <label>
          인수 상태
          <select v-model="form.acceptanceStatus">
            <option :value="null">해당 없음</option>
            <option v-for="value in ACCEPTANCE_STATUS_ORDER" :key="value" :value="value">
              {{ ACCEPTANCE_STATUS_LABELS[value] }}
            </option>
          </select>
        </label>
      </div>

      <p class="hint muted">
        가중치를 비워 두면 이 가지는 예전처럼 하위 평균으로 집계됩니다. 0은 "진척에 기여하지 않음"이라
        미입력과 다릅니다.
      </p>

      <p v-if="form.executionMode === 'HYBRID' && form.agileRatio === null" class="hint">
        Hybrid는 비중(α)이 있어야 진척을 셀 수 있습니다. 비워 두면 산정 전으로 표시됩니다.
      </p>

      <p v-if="rolledUp" class="hint">
        하위 항목이 있는 Summary 항목입니다. 일정과 진행률은 하위 항목에서 자동 집계되므로 직접 입력할 수 없습니다.
      </p>

      <p v-if="modeDisabled" class="hint">
        Summary 항목은 실행 방식을 갖지 않고 하위 Work Package의 실행 방식을 요약해서 보여줍니다.
        <template v-if="retainedMode">
          전환 전의 실행 방식({{ retainedMode }})은 지우지 않고 보관 중이며, 구분을 Work Package로 되돌리면 다시 적용됩니다.
        </template>
      </p>

      <!-- 위 힌트와 배타적이지 않다: Summary 안내와 "왜 Work Package를 고를 수 없는지"는 다른 이야기다. -->
      <p v-if="editing && !canBeWorkPackage" class="hint">
        하위 항목이 있어 Work Package로 되돌릴 수 없습니다. 하위 항목을 먼저 옮기거나 삭제하세요.
      </p>

      <div class="actions">
        <button type="submit">{{ editing ? '저장' : '추가' }}</button>
        <button type="button" class="ghost" @click="emit('cancel')">취소</button>
      </div>
    </form>
  </ModalDialog>
</template>

<style scoped>
.wbs-form {
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

label.grow {
  flex: 1;
}

input,
select {
  padding: 0.45rem 0.6rem;
  border: 1px solid var(--border-input);
  border-radius: 6px;
  font: inherit;
}

input:disabled,
select:disabled {
  background: var(--surface-sunken);
  color: var(--text-faint);
}

.row {
  display: flex;
  gap: 0.75rem;
}

.hint.muted {
  color: var(--text-muted);
  background: var(--surface-sunken);
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
