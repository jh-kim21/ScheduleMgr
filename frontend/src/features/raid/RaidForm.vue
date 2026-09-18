<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import ModalDialog from '../../components/ModalDialog.vue'
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
  /** 저장이 거부된 이유. 대화상자 안에 보여야 사용자가 볼 수 있다. */
  error?: string | null
  /** 부모(RaidView)가 create/update 요청 중일 때 true — 응답이 올 때까지 다시 제출을 막는다. */
  submitting?: boolean
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
 * 열렸을 때의 값을 찍어 두고 지금 값과 비교한다 — Escape·배경 클릭으로 닫을 때 입력을 잃을
 * 수 있는 경우에만 `ModalDialog`가 한 번 확인하게 한다(`dirty` prop, opt-in). 연결 대상을
 * 고르는 중인 `draftLink`는 아직 폼에 커밋되지 않은 값이라 여기 포함하지 않는다.
 */
const initialSnapshot = ref('')

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
    initialSnapshot.value = JSON.stringify(form)
  },
  { immediate: true },
)

const dirty = computed(() => JSON.stringify(form) !== initialSnapshot.value)

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
  if (!submittable.value || props.submitting) return
  emit('submit', { ...form, links: [...form.links] })
  if (!props.editing) {
    Object.assign(form, empty)
    form.links = []
  }
}

/**
 * Ctrl/Cmd+Enter 저장. 한글 IME 조합 중에는 `v-model`이 마지막 음절을 아직 반영하지 않았다 —
 * Vue의 `vModelText`는 `composing` 동안 input 리스너를 건너뛰고 `compositionend`에서야 모델을
 * 갱신한다. 그대로 저장하면 화면에 보이는 마지막 글자가 빠진 채 저장된다. `withKeys`는
 * `event.key`만 보고 `isComposing`을 보지 않으므로 여기서 직접 막는다.
 *
 * 브라우저·IME 조합에 따라서는 이 자리에 아예 오지 않거나(키를 IME가 먹음) 조합이 먼저
 * 커밋되기도 한다. 그때 이 가드는 아무 일도 하지 않고, 유실이 일어나는 경우에만 작동한다 —
 * 틀렸을 때의 대가가 "사용자가 쓴 글자가 말없이 사라짐"이라 확정 전에 막아 둔다.
 *
 * `.prevent`를 템플릿에 두지 않은 이유도 같다. 수식자 가드는 핸들러보다 먼저 실행되므로,
 * 우리가 아무 일도 하지 않는 조합 중에까지 기본 동작을 막게 된다.
 */
function onShortcutSave(event: KeyboardEvent) {
  if (event.isComposing) return
  event.preventDefault()
  onSubmit()
}
</script>

<template>
  <ModalDialog :title="title" size="lg" :error="props.error" :dirty="dirty" @close="emit('cancel')">
    <form class="raid-form" @submit.prevent="onSubmit">

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
          <textarea
            v-model="form.description"
            rows="4"
            maxlength="2000"
            placeholder="선택 — Ctrl+Enter로 저장"
            @keydown.ctrl.enter="onShortcutSave"
            @keydown.meta.enter="onShortcutSave"
          ></textarea>
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
          <textarea
            v-model="form.response"
            rows="3"
            maxlength="2000"
            placeholder="선택"
            @keydown.ctrl.enter="onShortcutSave"
            @keydown.meta.enter="onShortcutSave"
          ></textarea>
        </label>
      </div>

      <p v-if="members.length === 0" class="field-hint">
        소유자로 지정할 구성원이 없습니다. RACI 화면에서 구성원을 먼저 등록하면 선택할 수 있습니다.
      </p>

      <!-- 저장 버튼이 비활성인 이유 — 제목 없이는 눌러도 왜 안 되는지 알 수 없었다. -->
      <p v-if="!submittable" class="field-hint">제목을 입력해야 저장할 수 있습니다.</p>

      <div class="actions">
        <button type="submit" class="primary" :disabled="!submittable || submitting">
          {{ submitting ? (editing ? '저장 중…' : '추가 중…') : editing ? '저장' : '추가' }}
        </button>
        <button type="button" :disabled="submitting" @click="emit('cancel')">취소</button>
      </div>
    </form>
  </ModalDialog>
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
  line-height: 1;
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
  font-size: 0.8rem;
}

.link-add .grow-select {
  flex: 1;
  min-width: 0;
}

/* border·배경·font·cursor는 전역 기본과 같아 지웠다. :disabled도 전역과 값이 같아(특이도
   동점이라 실제로 이 규칙이 이겨 왔지만) 지웠다. */
.link-add button {
  padding: 0.3rem 0.7rem;
  color: var(--text-muted);
  font-size: 0.8rem;
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

/* 패딩·border·radius·font는 전역 기본과 같아(패딩은 아주 살짝만 다름) 지웠다 — font-size만 남긴다. */
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

.type-hint {
  margin: -0.2rem 0 0.6rem;
  font-size: 0.76rem;
  color: var(--text-faint);
}

.field-hint {
  margin: 0 0 0.6rem;
  font-size: 0.76rem;
  color: var(--warn-badge-fg);
}

.actions {
  display: flex;
  gap: 0.4rem;
}

/* 패딩·radius·border·배경·cursor는 전역 기본과 완전히 같은 값이라 지웠다. `.primary`와 그
   :disabled 변형도 전역 계약과 같아 통째로 지웠다. */
button {
  color: var(--text-muted);
  font-size: 0.85rem;
}
</style>
