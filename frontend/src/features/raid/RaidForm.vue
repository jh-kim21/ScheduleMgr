<script setup lang="ts">
import { computed, reactive, watch } from 'vue'
import type { ProjectMember } from '../../api/memberApi'
import type { RaidItem, RaidItemInput, RaidLinkInput } from '../../api/raidApi'
import {
  RAID_LEVEL_LABELS,
  RAID_LEVEL_ORDER,
  RAID_LINK_TARGET_LABELS,
  RAID_LINK_TARGET_ORDER,
  RAID_STATUS_LABELS,
  RAID_STATUS_ORDER,
  RAID_TYPE_DESCRIPTIONS,
  RAID_TYPE_LABELS,
  RAID_TYPE_ORDER,
  type RaidLinkTarget,
} from '../../shared/raid'
import type { RaidLinkOption } from './useRaid'

const props = defineProps<{
  editing: RaidItem | null
  members: ProjectMember[]
  /** WBS tasks in tree order, for the optional link. */
  wbsTasks: { id: number; code: string; name: string; level: number }[]
  /** The other two kinds of link target. Empty until Sprints or Backlog entries exist. */
  sprints: RaidLinkOption[]
  backlogItems: RaidLinkOption[]
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
  links: [],
  dueDate: null,
  response: null,
}

const form = reactive<RaidItemInput>({ ...empty, links: [] })

/**
 * The link being composed. Two selects rather than one flat list of everything in the project:
 * the three kinds are different questions ("어느 업무인가" vs "어느 Story인가"), and a merged
 * list would run to hundreds of rows on a real project.
 */
const draftLink = reactive<{ targetType: RaidLinkTarget; targetId: number | null }>({
  targetType: 'WBS_ITEM',
  targetId: null,
})

const linkOptions = computed<RaidLinkOption[]>(() => {
  switch (draftLink.targetType) {
    case 'WBS_ITEM':
      return props.wbsTasks.map((task) => ({
        id: task.id,
        code: task.code,
        name: task.name,
        level: task.level,
      }))
    case 'SPRINT':
      return props.sprints
    case 'BACKLOG_ITEM':
      return props.backlogItems
  }
})

/** Already-linked targets drop out of the picker: linking twice is refused by the server. */
const availableOptions = computed(() =>
  linkOptions.value.filter(
    (option) =>
      !form.links.some(
        (link) => link.targetType === draftLink.targetType && link.targetId === option.id,
      ),
  ),
)

function optionLabel(option: RaidLinkOption): string {
  const indent = '\u00a0'.repeat(Math.max(0, (option.level - 1) * 2))
  return option.code ? `${indent}${option.code} ${option.name}` : `${indent}${option.name}`
}

/** The label for a link already on the form, resolved from the same option lists. */
function linkLabel(link: RaidLinkInput): string {
  const options =
    link.targetType === 'WBS_ITEM'
      ? props.wbsTasks.map((task) => ({ id: task.id, code: task.code, name: task.name, level: 1 }))
      : link.targetType === 'SPRINT'
        ? props.sprints
        : props.backlogItems
  const option = options.find((candidate) => candidate.id === link.targetId)
  if (!option) return `${RAID_LINK_TARGET_LABELS[link.targetType]} #${link.targetId}`
  return option.code ? `${option.code} ${option.name}` : option.name
}

function addLink() {
  if (draftLink.targetId === null) return
  form.links = [...form.links, { targetType: draftLink.targetType, targetId: draftLink.targetId }]
  draftLink.targetId = null
}

function removeLink(index: number) {
  form.links = form.links.filter((_, at) => at !== index)
}

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
      form.links = item.links.map((link) => ({
        targetType: link.targetType,
        targetId: link.targetId,
      }))
      form.dueDate = item.dueDate
      form.response = item.response
    } else {
      Object.assign(form, empty)
      form.links = []
    }
    draftLink.targetId = null
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
  emit('submit', { ...form, links: [...form.links] })
  if (!props.editing) {
    Object.assign(form, empty)
    form.links = []
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

    <fieldset class="links">
      <legend>연결 대상</legend>

      <ul v-if="form.links.length > 0" class="link-chips">
        <li v-for="(link, index) in form.links" :key="`${link.targetType}:${link.targetId}`">
          <span class="kind">{{ RAID_LINK_TARGET_LABELS[link.targetType] }}</span>
          {{ linkLabel(link) }}
          <button type="button" aria-label="연결 해제" @click="removeLink(index)">×</button>
        </li>
      </ul>
      <p v-else class="link-empty">연결 없음 — 프로젝트 전체에 대한 항목입니다.</p>

      <div class="link-add">
        <select v-model="draftLink.targetType" aria-label="연결 종류" @change="draftLink.targetId = null">
          <option v-for="target in RAID_LINK_TARGET_ORDER" :key="target" :value="target">
            {{ RAID_LINK_TARGET_LABELS[target] }}
          </option>
        </select>

        <select v-model="draftLink.targetId" aria-label="연결 대상" class="grow-select">
          <option :value="null">선택</option>
          <option v-for="option in availableOptions" :key="option.id" :value="option.id">
            {{ optionLabel(option) }}
          </option>
        </select>

        <button type="button" :disabled="draftLink.targetId === null" @click="addLink">추가</button>
      </div>

      <p v-if="linkOptions.length === 0" class="link-hint">
        연결할 {{ RAID_LINK_TARGET_LABELS[draftLink.targetType] }}이(가) 아직 없습니다.
      </p>
    </fieldset>

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
/* 연결은 여러 개라 한 줄 셀렉트로는 안 되고, 목록 + 추가 줄로 둔다. */
.links {
  border: 1px solid var(--border-input);
  border-radius: 6px;
  padding: 0.6rem 0.75rem 0.75rem;
  margin: 0 0 0.75rem;
}

.links legend {
  font-size: 0.78rem;
  color: var(--text-muted);
  padding: 0 0.3rem;
}

.link-chips {
  list-style: none;
  display: flex;
  flex-wrap: wrap;
  gap: 0.35rem;
  margin: 0 0 0.5rem;
  padding: 0;
}

.link-chips li {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  padding: 0.2rem 0.4rem 0.2rem 0.55rem;
  border: 1px solid var(--border-input);
  border-radius: 999px;
  background: var(--surface);
  font-size: 0.78rem;
}

.link-chips .kind {
  color: var(--text-faint);
  font-size: 0.72rem;
}

.link-chips button {
  border: none;
  background: none;
  color: var(--text-muted);
  font: inherit;
  line-height: 1;
  cursor: pointer;
  padding: 0 0.15rem;
}

.link-empty,
.link-hint {
  font-size: 0.78rem;
  color: var(--text-faint);
  margin: 0 0 0.5rem;
}

.link-add {
  display: flex;
  gap: 0.4rem;
  align-items: center;
}

.link-add select {
  padding: 0.3rem 0.5rem;
  border: 1px solid var(--border-input);
  border-radius: 6px;
  font: inherit;
  font-size: 0.8rem;
}

.link-add .grow-select {
  flex: 1;
  min-width: 0;
}

.link-add button {
  padding: 0.3rem 0.7rem;
  border: 1px solid var(--border-input);
  border-radius: 6px;
  background: var(--surface);
  color: var(--text-muted);
  font: inherit;
  font-size: 0.8rem;
  cursor: pointer;
}

.link-add button:disabled {
  background: var(--disabled-bg);
  border-color: var(--disabled-border);
  color: var(--disabled-fg);
  cursor: not-allowed;
}

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
