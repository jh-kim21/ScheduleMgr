<script setup lang="ts">
import { computed, reactive, watch } from 'vue'
import type { ProjectMember } from '../../api/memberApi'
import type { RaidItem, RaidItemInput } from '../../api/raidApi'
import {
  RAID_LEVEL_LABELS,
  RAID_LEVEL_ORDER,
  RAID_STATUS_LABELS,
  RAID_STATUS_ORDER,
  RAID_TYPE_DESCRIPTIONS,
  RAID_TYPE_LABELS,
  RAID_TYPE_ORDER,
} from '../../shared/raid'

const props = defineProps<{
  editing: RaidItem | null
  members: ProjectMember[]
  /** WBS tasks in tree order, for the optional link. */
  wbsTasks: { id: number; code: string; name: string; level: number }[]
}>()

const emit = defineEmits<{
  submit: [input: RaidItemInput]
  cancel: []
}>()

const empty: RaidItemInput = {
  type: 'RISK',
  title: '',
  description: null,
  status: 'OPEN',
  probability: null,
  impact: null,
  ownerMemberId: null,
  wbsItemId: null,
  dueDate: null,
  response: null,
}

const form = reactive<RaidItemInput>({ ...empty })

// Same pattern as the WBS form: one form serves create and edit, driven by the `editing` prop.
watch(
  () => props.editing,
  (item) => {
    if (item) {
      form.type = item.type
      form.title = item.title
      form.description = item.description
      form.status = item.status
      form.probability = item.probability
      form.impact = item.impact
      form.ownerMemberId = item.ownerMemberId
      form.wbsItemId = item.wbsItemId
      form.dueDate = item.dueDate
      form.response = item.response
    } else {
      Object.assign(form, empty)
    }
  },
  { immediate: true },
)

const title = computed(() =>
  props.editing ? `항목 수정 — ${props.editing.title}` : 'RAID 항목 추가',
)

const submittable = computed(() => form.title.trim().length > 0)

/**
 * 확률은 위험에서만 묻는다. 이슈는 이미 일어난 일이라 확률이 성립하지 않고, 가정·의존성은 등급을
 * 매기는 대상이 아니다. 서버는 종류로 제한하지 않으므로(둘 다 있으면 환산) 화면이 물어보지 않을
 * 뿐이며, 이미 값이 들어 있는 항목은 그대로 유지된다.
 */
const asksProbability = computed(() => form.type === 'RISK')
const asksImpact = computed(() => form.type === 'RISK' || form.type === 'ISSUE')

const responseLabel = computed(() => {
  switch (form.type) {
    case 'RISK':
      return '대응 방안'
    case 'ASSUMPTION':
      return '확인 방법'
    case 'ISSUE':
      return '해결 방안'
    case 'DEPENDENCY':
      return '확보 방안'
  }
})

const dueLabel = computed(() => (form.type === 'ASSUMPTION' ? '확인 기한' : '대응 기한'))

function onSubmit() {
  if (!submittable.value) return
  emit('submit', { ...form })
  if (!props.editing) {
    Object.assign(form, empty)
  }
}
</script>

<template>
  <form class="raid-form" @submit.prevent="onSubmit">
    <h2>{{ title }}</h2>

    <div class="row">
      <label class="type">
        종류
        <select v-model="form.type">
          <option v-for="type in RAID_TYPE_ORDER" :key="type" :value="type">
            {{ RAID_TYPE_LABELS[type] }}
          </option>
        </select>
      </label>

      <label class="grow">
        제목
        <input v-model="form.title" type="text" placeholder="한 줄로 요약" />
      </label>

      <label class="status">
        상태
        <select v-model="form.status">
          <option v-for="status in RAID_STATUS_ORDER" :key="status" :value="status">
            {{ RAID_STATUS_LABELS[status] }}
          </option>
        </select>
      </label>
    </div>

    <p class="type-hint">{{ RAID_TYPE_DESCRIPTIONS[form.type] }}</p>

    <div class="row">
      <label class="grow">
        설명
        <input v-model="form.description" type="text" placeholder="선택" />
      </label>
    </div>

    <div class="row">
      <label v-if="asksProbability" class="level">
        확률
        <select v-model="form.probability">
          <option :value="null">미지정</option>
          <option v-for="level in RAID_LEVEL_ORDER" :key="level" :value="level">
            {{ RAID_LEVEL_LABELS[level] }}
          </option>
        </select>
      </label>

      <label v-if="asksImpact" class="level">
        영향
        <select v-model="form.impact">
          <option :value="null">미지정</option>
          <option v-for="level in RAID_LEVEL_ORDER" :key="level" :value="level">
            {{ RAID_LEVEL_LABELS[level] }}
          </option>
        </select>
      </label>

      <label class="owner">
        소유자
        <select v-model="form.ownerMemberId">
          <option :value="null">미지정</option>
          <option v-for="member in members" :key="member.id" :value="member.id">
            {{ member.name }}
          </option>
        </select>
      </label>

      <label class="due">
        {{ dueLabel }}
        <input v-model="form.dueDate" type="date" />
      </label>
    </div>

    <div class="row">
      <label class="wbs">
        관련 업무
        <select v-model="form.wbsItemId">
          <option :value="null">미지정 (프로젝트 전체)</option>
          <option v-for="task in wbsTasks" :key="task.id" :value="task.id">
            {{ '\u00a0'.repeat((task.level - 1) * 2) }}{{ task.code }} {{ task.name }}
          </option>
        </select>
      </label>
    </div>

    <div class="row">
      <label class="grow">
        {{ responseLabel }}
        <input v-model="form.response" type="text" placeholder="선택" />
      </label>
    </div>

    <p v-if="members.length === 0" class="owner-hint">
      소유자로 지정할 구성원이 없습니다. RACI 화면에서 구성원을 먼저 등록하면 선택할 수 있습니다.
    </p>

    <div class="actions">
      <button type="submit" class="primary" :disabled="!submittable">
        {{ editing ? '저장' : '추가' }}
      </button>
      <button v-if="editing" type="button" @click="emit('cancel')">취소</button>
    </div>
  </form>
</template>

<style scoped>
.raid-form {
  padding: 0.85rem;
  border: 1px solid var(--border);
  border-radius: 8px;
}

h2 {
  font-size: 0.95rem;
  margin: 0 0 0.75rem;
}

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

.type select,
.status select,
.level select {
  min-width: 6rem;
}

.owner select {
  min-width: 8rem;
}

.wbs select {
  min-width: 20rem;
  max-width: 100%;
}

input,
select {
  padding: 0.4rem 0.55rem;
  border: 1px solid var(--border-input);
  border-radius: 6px;
  font: inherit;
  font-size: 0.85rem;
}

.type-hint {
  margin: -0.2rem 0 0.6rem;
  font-size: 0.76rem;
  color: var(--text-faint);
}

.owner-hint {
  margin: 0 0 0.6rem;
  font-size: 0.76rem;
  color: var(--warn-badge-fg);
}

.actions {
  display: flex;
  gap: 0.4rem;
}

button {
  padding: 0.45rem 0.9rem;
  border-radius: 6px;
  border: 1px solid var(--border-input);
  background: var(--surface);
  color: var(--text-muted);
  cursor: pointer;
  font-size: 0.85rem;
}

button.primary {
  border-color: var(--accent);
  background: var(--accent);
  color: var(--accent-fg);
}

button.primary:disabled {
  background: var(--disabled-bg);
  border-color: var(--disabled-border);
  color: var(--disabled-fg);
  cursor: not-allowed;
}
</style>
