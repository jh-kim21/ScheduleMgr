<script setup lang="ts">
import { computed, reactive, watch } from 'vue'
import ModalDialog from '../../components/ModalDialog.vue'
import TagChip from './TagChip.vue'
import type { WbsItemInput, WbsNode } from '../../api/wbsApi'
import type { WbsTag } from '../../api/wbsTagApi'
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
import { WEIGHT_GRADES, isOnScale, wbsWeightLabel } from '../../shared/weight'
import { nodeToFormInput } from './wbsFormMapping'

const props = defineProps<{
  editing: WbsNode | null
  parent: WbsNode | null
  /**
   * 이 프로젝트에 등록된 업무 분야 전체 — 고를 수 있는 값의 목록이다. 마스터는 프로젝트 화면에서
   * 관리하므로 여기서는 새로 만들 수 없고, 비어 있으면 그 사실을 안내한다.
   */
  availableTags: WbsTag[]
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
  actionItemUrl: null,
  startDate: null,
  endDate: null,
  progress: 0,
  weight: null,
  agileRatio: null,
  acceptanceStatus: null,
  // 새 항목은 자식이 없으니 최하위 관리 단위로 시작한다. 서버 기본값과 같다.
  nodeType: 'WORK_PACKAGE',
  executionMode: null,
  actualStartDate: null,
  actualEndDate: null,
  forecastEndDate: null,
  // 폼은 언제나 배열을 명시적으로 보낸다 — `null`(= 변경 없음)은 이 필드를 모르는 호출자를 위한
  // 값이고, 폼이 보여 준 집합이 곧 저장될 집합이다.
  tagIds: [],
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

/**
 * 분야는 실제 작업의 속성이라 Summary에서는 바꿀 수 없다 — 실행 방식과 같은 규칙이고, 서버도
 * 거부한다. 지우지 않고 보관하므로 폼은 보관값을 그대로 되돌려 보낸다(값이 같으면 통과한다).
 */
const tagsDisabled = computed(() => form.nodeType === 'SUMMARY')

const selectedTagIds = computed(() => new Set(form.tagIds ?? []))

/**
 * 태그를 붙이거나 뗀다. **배열을 제자리에서 고치지 않고 새로 만든다** — `empty`는 모듈 스코프
 * 상수이고 `Object.assign(form, empty)`가 그 배열을 참조로 복사하므로, 제자리에서 `push`하면
 * 폼을 한 번 초기화한 뒤부터 `empty` 자신이 오염돼 다음 새 항목이 남의 태그를 달고 열린다.
 */
function toggleTag(tagId: number) {
  if (tagsDisabled.value) return
  const current = form.tagIds ?? []
  form.tagIds = current.includes(tagId)
    ? current.filter((id) => id !== tagId)
    : [...current, tagId]
}

const title = computed(() => {
  if (props.editing) return `항목 수정 — ${props.editing.code} ${props.editing.name}`
  if (props.parent) return `하위 항목 추가 — ${props.parent.code} ${props.parent.name}`
  return '최상위 항목 추가'
})

watch(
  () => props.editing,
  (item) => {
    if (item) {
      // 매핑은 wbsFormMapping.ts로 뽑아 뒀다 — "편집 중 아무것도 안 바꾸고 저장해도 모든 필드가
      // 그대로 다시 실려야 한다"(결함 3)는 이 대입 자체가 지켜야 할 계약이라, 컴포넌트를 마운트하지
      // 않고도 단위 테스트로 고정하려는 것이다.
      Object.assign(form, nodeToFormInput(item))
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
      </div>

      <!-- 설명은 자기 행이다 — 3줄짜리 textarea를 업무명 옆에 두면 두 칸의 높이가 크게 어긋난다. -->
      <div class="row">
        <label class="grow">
          설명
          <!-- self-closing 하지 말고 `></textarea>`로 닫는다 — 태그 사이의 공백이 그대로
               초기값이 되어, 비어 있어야 할 칸이 공백 한 칸을 들고 열린다. -->
          <textarea v-model="form.description" rows="3" placeholder="설명 (선택)"></textarea>
        </label>
      </div>

      <!-- 회의록·티켓·산출물 주소. 항목당 하나이고 라벨은 받지 않는다(지시서 §2-D).
           `type="url"`은 편의일 뿐이라 서버 검증(http/https만)이 따로 있다. -->
      <div class="row">
        <label class="grow">
          Action Item 주소
          <input v-model="form.actionItemUrl" type="url" placeholder="https://… (선택)" />
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

      <!--
        같은 진척 근거 묶음이지만 줄을 따로 쓴다 — 등급마다 판단 기준 문장이 붙어 가장 긴 옵션이
        대화상자 폭(34rem)의 1/3을 넘는다. 위 줄에 세 번째로 끼우면 닫힌 셀렉트에서 기준 문장이
        잘려 나가는데, 그 문장이 이 입력의 본체다(지시서 §2-b).

        `form.weight`를 payload에서 빼면 안 된다 — 기존에 값이 있던 항목이 저장하는 순간 `null`로
        덮인다(커밋 `da96ebe`와 같은 종류의 사고).

        `v-model.number`가 아니라 `v-model`이다 — `.number` 수식자는 `미지정` 옵션을 `0`으로 바꿀
        수 있고, `0`은 "집계 제외"라는 다른 뜻이다. `:value`에 숫자 리터럴을 바인딩하므로 문자열이
        될 일이 없다.
      -->
      <div class="row">
        <label class="grow">
          규모
          <select v-model="form.weight">
            <!-- 미입력은 실체화하지 않는다 — 그 몫은 형제의 선언값에서 나온다(지시서 §2-c). -->
            <option :value="null">미지정</option>
            <!-- 고를 수는 없지만 저장된 값은 그대로 보이고 그대로 저장돼야 한다(레거시 0·척도 밖). -->
            <option v-if="form.weight !== null && !isOnScale(form.weight)" :value="form.weight" disabled>
              {{ wbsWeightLabel(form.weight) }}
            </option>
            <option v-for="grade in WEIGHT_GRADES" :key="grade.value" :value="grade.value">
              {{ grade.label }} ({{ grade.value }}) — {{ grade.wbsHint }}
            </option>
          </select>
        </label>
      </div>

      <p class="hint muted">
        규모는 형제 항목 사이의 상대적 크기입니다. 미지정으로 두면 등급을 적은 형제에서 leaf 하나당
        비중을 뽑아 이 항목의 가지 크기만큼 칩니다 — 형제 중 누구도 적지 않았다면 예전처럼 하위 leaf
        개수로 집계합니다.
      </p>

      <div class="row">
        <label>
          실적 시작일
          <input v-model="form.actualStartDate" type="date" :disabled="modeDisabled" />
        </label>
        <label>
          실적 종료일
          <input v-model="form.actualEndDate" type="date" :disabled="modeDisabled" />
        </label>
        <label>
          예상 종료일
          <input v-model="form.forecastEndDate" type="date" :disabled="modeDisabled" />
        </label>
      </div>

      <!--
        업무 분야. 마스터는 프로젝트 화면(행의 `분야` 버튼)에서 관리하고 여기서는 고르기만 한다 —
        구성원을 RACI가 아니라 프로젝트 화면에서 관리하는 것과 같은 이유다(프로젝트 스코프 개념이
        WBS에 종속될 이유가 없다).
      -->
      <fieldset class="tag-picker">
        <legend>분야</legend>
        <p v-if="availableTags.length === 0" class="tag-empty">
          등록된 분야가 없습니다. 프로젝트 화면의 <strong>분야</strong> 버튼에서 먼저 만드세요.
        </p>
        <template v-else>
          <button
            v-for="tag in availableTags"
            :key="tag.id"
            type="button"
            class="tag-toggle"
            :disabled="tagsDisabled"
            :aria-pressed="selectedTagIds.has(tag.id)"
            @click="toggleTag(tag.id)"
          >
            <TagChip :tag="tag" :muted="!selectedTagIds.has(tag.id)" :selected="selectedTagIds.has(tag.id)" />
          </button>
        </template>
      </fieldset>

      <p v-if="tagsDisabled" class="hint">
        Summary 항목은 분야를 갖지 않습니다 — 분야는 실제 작업의 속성이고, 상위 행은 하위의 분야를
        모아서 보여 줍니다.
        <template v-if="(form.tagIds?.length ?? 0) > 0">
          전환 전에 붙어 있던 분야({{ form.tagIds?.length }}개)는 지우지 않고 보관 중이며, 구분을 Work
          Package로 되돌리면 다시 적용됩니다.
        </template>
      </p>

      <p class="hint muted">
        실적 시작일·종료일은 실제로 일한 기간을, 예상 종료일은 계획을 고치지 않고 지금 예상되는
        완료 시점을 적습니다. 간트 차트의 실적 막대와 예상 종료 표식에 쓰입니다. Summary 항목은
        실행 방식과 마찬가지로 최하위 Work Package에서만 의미가 있습니다.
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

/* `min-width: 0` 이 없으면 flex 항목이 min-content 밑으로 줄지 못한다 — 긴 옵션을 가진 `규모`
   셀렉트가 대화상자 밖으로 밀고 나간다. */
label.grow {
  flex: 1;
  min-width: 0;
}

/* `textarea`를 빠뜨리지 마라 — `font: inherit`이 없으면 설명 칸만 브라우저 기본 고정폭
   글꼴로 나와 옆의 업무명 칸과 다르게 보인다. 이 작업에서 가장 흔한 실수 자리다. */
input,
select,
textarea {
  padding: 0.45rem 0.6rem;
  border: 1px solid var(--border-input);
  border-radius: 6px;
  font: inherit;
}

/* 세로로만 늘린다 — 가로로 늘리면 대화상자 폭을 넘어간다. */
textarea {
  resize: vertical;
}

input:disabled,
select:disabled,
textarea:disabled {
  background: var(--surface-sunken);
  color: var(--text-faint);
}

.row {
  display: flex;
  gap: 0.75rem;
}

.tag-picker {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.35rem;
  border: 1px solid var(--border-soft);
  border-radius: 6px;
  padding: 0.5rem;
  margin: 0;
}

.tag-picker legend {
  font-size: 0.85rem;
  color: var(--text-muted);
  padding: 0 0.25rem;
}

.tag-empty {
  font-size: 0.78rem;
  color: var(--text-faint);
}

/* 버튼 크롬을 지우고 칩 자체를 누르게 한다 — 칩 옆에 체크박스를 두면 줄이 두 배가 된다. */
.tag-toggle {
  padding: 0;
  border: none;
  background: none;
  cursor: pointer;
}

.tag-toggle:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

.tag-toggle:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: 2px;
  border-radius: 999px;
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
