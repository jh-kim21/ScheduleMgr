<script setup lang="ts">
import { computed, reactive, watch } from 'vue'
import ModalDialog from '../../components/ModalDialog.vue'
import type { BacklogItem, BacklogItemInput } from '../../api/backlogApi'
import type { ProjectMember } from '../../api/memberApi'
import {
  BACKLOG_PRIORITY_LABELS,
  BACKLOG_PRIORITY_ORDER,
  BACKLOG_STATUS_LABELS,
  BACKLOG_STATUS_ORDER,
  BACKLOG_TYPE_LABELS,
  BACKLOG_TYPE_ORDER,
  aggregatedType,
} from '../../shared/backlog'
import { executionModeLabel } from '../../shared/executionMode'
import type { WorkPackageOption } from './useBacklog'

const props = defineProps<{
  editing: BacklogItem | null
  members: ProjectMember[]
  workPackages: WorkPackageOption[]
  parentOptions: BacklogItem[]
  /**
   * 목록 화면에서 걸어 둔 Work Package 필터. 새 항목의 귀속 기본값으로만 쓰이며(아래
   * `inheritsLink`가 이기면 무시된다), 수정 중인 항목에는 적용하지 않는다.
   */
  defaultWbsItemId: number | null
  /** 저장이 거부된 이유. 대화상자 안에 보여야 사용자가 볼 수 있다. */
  error?: string | null
}>()

const emit = defineEmits<{
  submit: [input: BacklogItemInput]
  cancel: []
}>()

const empty: BacklogItemInput = {
  wbsItemId: null,
  parentId: null,
  itemType: 'STORY',
  title: '',
  description: '',
  priority: 'MEDIUM',
  status: 'TODO',
  assigneeMemberId: null,
  acceptanceCriteria: '',
  storyPoint: null,
  progressWeight: null,
  acceptanceConfirmed: false,
}

const form = reactive<BacklogItemInput>({ ...empty })

/**
 * A Task must have a parent; the others may sit directly under a Work Package. Parents are also
 * limited by type: an Epic takes Story/Bug, a Story or Bug takes Tasks.
 */
const eligibleParents = computed(() =>
  props.parentOptions.filter((candidate) => {
    if (candidate.id === props.editing?.id) return false
    return form.itemType === 'TASK'
      ? candidate.itemType === 'STORY' || candidate.itemType === 'BUG'
      : candidate.itemType === 'EPIC'
  }),
)

const parent = computed(() =>
  form.parentId === null
    ? null
    : (props.parentOptions.find((candidate) => candidate.id === form.parentId) ?? null),
)

/** 하위의 귀속은 상위를 따른다. 고를 수 있는 값이 아니므로 컨트롤을 잠근다. */
const inheritsLink = computed(() => parent.value !== null)

/**
 * 필터에서 물려받은 기본값이 아직 그대로 남아 있는 동안만 안내한다. 상위를 골라 상속이 이기면
 * (`inheritsLink`) 이 안내는 물러난다 — 두 안내가 같은 자리에서 동시에 뜨면 안 된다.
 */
const defaultLinkApplied = computed(
  () =>
    !props.editing &&
    !inheritsLink.value &&
    props.defaultWbsItemId !== null &&
    form.wbsItemId === props.defaultWbsItemId,
)

const selectedPackage = computed(() =>
  props.workPackages.find((candidate) => candidate.id === form.wbsItemId) ?? null,
)

/** 실행 방식이 Agile·Hybrid가 아니면 안내한다 (막지는 않는다). */
const modeNotice = computed(() => {
  const target = selectedPackage.value
  if (!target) return null
  if (target.executionMode === 'AGILE' || target.executionMode === 'HYBRID') return null
  return `'${target.code} ${target.name}'의 실행 방식은 ${executionModeLabel(target.executionMode)}입니다. Agile 실행으로 관리하려면 WBS 화면에서 실행 방식을 Agile 또는 Hybrid로 바꿔야 합니다.`
})

const title = computed(() =>
  props.editing ? `항목 수정 — ${props.editing.title}` : 'Backlog 항목 추가',
)

/**
 * 완료로 바꾸려면 확인이 필요하다. 이미 완료였던 항목은 다시 묻지 않는다 — 폼은 저장할 때마다
 * 전체를 보내므로, 재확인을 요구하면 제목만 고쳐도 체크를 다시 눌러야 한다.
 */
const needsAcceptance = computed(
  () => form.status === 'DONE' && props.editing?.status !== 'DONE',
)

watch(
  () => props.editing,
  (item) => {
    if (item) {
      form.wbsItemId = item.wbsItemId
      form.parentId = item.parentId
      form.itemType = item.itemType
      form.title = item.title
      form.description = item.description ?? ''
      form.priority = item.priority
      form.status = item.status
      form.assigneeMemberId = item.assigneeMemberId
      form.acceptanceCriteria = item.acceptanceCriteria ?? ''
      form.storyPoint = item.storyPoint
      form.progressWeight = item.progressWeight
      // 이미 완료인 항목을 다시 저장하는 것은 새 확인을 요구하지 않는다 (서버도 그렇게 본다).
      form.acceptanceConfirmed = item.status === 'DONE'
    } else {
      // 새 항목: 목록에서 걸어 둔 Work Package 필터를 출발점으로 삼는다. 필터가 없거나(전체
      // 보기) 가리키는 항목이 이미 사라졌으면 defaultWbsItemId는 null이라 미연결로 시작한다.
      Object.assign(form, empty, { wbsItemId: props.defaultWbsItemId })
    }
  },
  { immediate: true },
)

/** Switching to Task requires a parent; switching away from it may invalidate the current one. */
watch(
  () => form.itemType,
  () => {
    if (form.parentId !== null && !eligibleParents.value.some((c) => c.id === form.parentId)) {
      form.parentId = null
    }
  },
)

/** 상위를 고르면 귀속은 상위 값으로 맞춰 보낸다 — 서버가 다른 값을 거부한다. */
watch(parent, (value) => {
  if (value) form.wbsItemId = value.wbsItemId
})

function onSubmit() {
  if (!form.title.trim()) return
  if (needsAcceptance.value && !form.acceptanceConfirmed) return
  emit('submit', { ...form })
}
</script>

<template>
  <ModalDialog :title="title" size="lg" :error="props.error" @close="emit('cancel')">
    <form class="backlog-form" @submit.prevent="onSubmit">

      <div class="row">
        <label class="grow">
          제목
          <input v-model="form.title" type="text" required placeholder="예: WBS 계층 등록" />
        </label>
        <label>
          유형
          <select v-model="form.itemType">
            <option v-for="type in BACKLOG_TYPE_ORDER" :key="type" :value="type">
              {{ BACKLOG_TYPE_LABELS[type] }}
            </option>
          </select>
        </label>
        <label>
          우선순위
          <select v-model="form.priority">
            <option v-for="value in BACKLOG_PRIORITY_ORDER" :key="value" :value="value">
              {{ BACKLOG_PRIORITY_LABELS[value] }}
            </option>
          </select>
        </label>
        <label>
          상태
          <select v-model="form.status">
            <option v-for="value in BACKLOG_STATUS_ORDER" :key="value" :value="value">
              {{ BACKLOG_STATUS_LABELS[value] }}
            </option>
          </select>
        </label>
      </div>

      <div class="row">
        <label class="grow">
          상위 항목
          <select v-model="form.parentId">
            <option :value="null">
              {{ form.itemType === 'TASK' ? '상위 Story·Bug를 선택하세요' : '없음 (Work Package에 직접)' }}
            </option>
            <option v-for="candidate in eligibleParents" :key="candidate.id" :value="candidate.id">
              {{ BACKLOG_TYPE_LABELS[candidate.itemType] }} · {{ candidate.title }}
            </option>
          </select>
        </label>
        <label class="grow">
          귀속 Work Package
          <select v-model="form.wbsItemId" :disabled="inheritsLink">
            <option :value="null">미연결 (초안)</option>
            <option v-for="option in workPackages" :key="option.id" :value="option.id">
              {{ option.code }} {{ option.name }} · {{ executionModeLabel(option.executionMode) }}
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
      </div>

      <div class="row">
        <label class="grow">
          수용 조건
          <input
            v-model="form.acceptanceCriteria"
            type="text"
            placeholder="완료로 인정하는 기준 (선택)"
          />
        </label>
        <label>
          Story Point
          <input v-model.number="form.storyPoint" type="number" min="0" placeholder="추정" />
        </label>
        <label>
          진척 가중치
          <input v-model.number="form.progressWeight" type="number" min="0" placeholder="비중" />
        </label>
      </div>

      <label class="grow">
        설명
        <input v-model="form.description" type="text" placeholder="설명 (선택)" />
      </label>

      <p v-if="inheritsLink" class="hint">
        하위 항목의 귀속은 상위 항목을 따릅니다. 귀속을 바꾸려면 상위 항목을 옮기세요.
      </p>

      <p v-else-if="defaultLinkApplied" class="hint muted">
        목록에서 걸어 둔 필터를 따라 귀속 Work Package를 미리 골라 두었습니다. 다른 곳에 붙이려면 바꾸세요.
      </p>

      <p v-if="form.itemType === 'TASK' && form.parentId === null" class="hint">
        Task는 Story 또는 Bug의 하위여야 합니다. 상위 항목을 선택하세요.
      </p>

      <p v-if="!aggregatedType(form.itemType)" class="hint muted">
        {{ BACKLOG_TYPE_LABELS[form.itemType] }}은(는) 진척 집계에 별도로 가산되지 않습니다.
        Story Point와 진척 가중치는 서로 다른 값이며, 집계는 Story·Bug 기준입니다.
      </p>

      <p v-if="modeNotice" class="hint">{{ modeNotice }}</p>

      <!-- 최소 완료 절차 (Step 4 지시서 7항). Board의 완료 확인과 같은 규칙을 폼에도 적용한다. -->
      <label v-if="needsAcceptance" class="confirm">
        <input v-model="form.acceptanceConfirmed" type="checkbox" />
        <span>
          수용 조건과 완료 기준(Definition of Done)을 확인했습니다.
          <template v-if="form.acceptanceCriteria"> — {{ form.acceptanceCriteria }}</template>
        </span>
      </label>

      <div class="actions">
        <button
          type="submit"
          :disabled="needsAcceptance && !form.acceptanceConfirmed"
        >{{ editing ? '저장' : '추가' }}</button>
        <button type="button" class="ghost" @click="emit('cancel')">취소</button>
      </div>
    </form>
  </ModalDialog>
</template>

<style scoped>
.backlog-form {
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
  min-width: 0;
}

input,
select {
  padding: 0.45rem 0.6rem;
  border: 1px solid var(--border-input);
  border-radius: 6px;
  font: inherit;
  min-width: 0;
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

.hint {
  margin: 0;
  font-size: 0.8rem;
  color: var(--warn-badge-fg);
  background: var(--warn-weak);
  border-radius: 6px;
  padding: 0.5rem 0.65rem;
}

.hint.muted {
  color: var(--text-muted);
  background: var(--surface-sunken);
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

.confirm {
  flex-direction: row;
  align-items: flex-start;
  gap: 0.45rem;
  font-size: 0.82rem;
  color: var(--text-muted);
  background: var(--surface-sunken);
  border-radius: 6px;
  padding: 0.5rem 0.65rem;
}

.confirm input {
  margin-top: 0.15rem;
}
</style>
