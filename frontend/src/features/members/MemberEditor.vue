<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { MemberInput, ProjectMember } from '../../api/memberApi'
import ModalDialog from '../../components/ModalDialog.vue'

const props = defineProps<{
  /** 대화상자 제목에 넣을 프로젝트 이름 — "구성원 관리 — 웹사이트 개편"처럼 문맥을 붙인다. */
  projectName: string
  members: ProjectMember[]
  loading: boolean
  /** 저장이 거부된 이유(이름 중복 등). 대화상자 안에 보여야 사용자가 볼 수 있다. */
  error?: string | null
}>()

const emit = defineEmits<{
  add: [input: MemberInput]
  update: [memberId: number, input: MemberInput]
  remove: [memberId: number]
  close: []
}>()

/** CLAUDE.md 화면 레이아웃 규칙: 폼이 자기 제목을 계산해 ModalDialog에 넘긴다. */
const title = computed(() => `구성원 관리 — ${props.projectName}`)

const blank = (): MemberInput => ({ name: '', email: null, position: null })

const draft = ref<MemberInput>(blank())
/** 추가 폼은 대화상자로 띄운다 — 이 화면의 주된 행위는 목록을 읽는 것이다. */
const addOpen = ref(false)

/** The row being edited in place, and the values it is being edited to. */
const editingId = ref<number | null>(null)
const editDraft = ref<MemberInput>(blank())

const submittable = computed(() => draft.value.name.trim().length > 0)
const editSubmittable = computed(() => editDraft.value.name.trim().length > 0)

// A rejected change leaves the member list untouched, so the draft stays put next to the error
// message; only an accepted one brings back a row holding the values we sent. The add form clears
// the same way — losing a typed name to a duplicate-name error is worse than a form that lingers.
watch(
  () => props.members,
  (members) => {
    const added = draft.value.name.trim()
    if (added.length > 0 && members.some((member) => member.name === added)) {
      // 저장이 받아들여졌다는 신호(보낸 이름이 목록에 나타남)일 때만 닫는다. 거부되면 대화상자가
      // 입력값과 오류 메시지를 그대로 들고 남아 있어야 한다.
      draft.value = blank()
      addOpen.value = false
    }

    if (editingId.value === null) return
    const saved = members.find((member) => member.id === editingId.value)
    if (!saved) {
      cancelEdit()
      return
    }
    if (
      saved.name === editDraft.value.name.trim() &&
      (saved.email ?? null) === normalise(editDraft.value.email) &&
      (saved.position ?? null) === normalise(editDraft.value.position)
    ) {
      cancelEdit()
    }
  },
)

function normalise(value: string | null): string | null {
  const trimmed = value?.trim() ?? ''
  return trimmed.length > 0 ? trimmed : null
}

function payload(input: MemberInput): MemberInput {
  return {
    name: input.name.trim(),
    email: normalise(input.email),
    position: normalise(input.position),
  }
}

function onSubmit() {
  if (!submittable.value) return
  emit('add', payload(draft.value))
}

function openAdd() {
  draft.value = blank()
  addOpen.value = true
}

function startEdit(member: ProjectMember) {
  editingId.value = member.id
  editDraft.value = { name: member.name, email: member.email, position: member.position }
}

function cancelEdit() {
  editingId.value = null
}

function onSave() {
  if (editingId.value === null || !editSubmittable.value) return
  emit('update', editingId.value, payload(editDraft.value))
}

function onRemove(member: ProjectMember) {
  if (!confirm(`${member.name} 구성원을 삭제하면 RACI 배정도 함께 지워집니다. 진행할까요?`)) return
  emit('remove', member.id)
}
</script>

<template>
  <ModalDialog :title="title" :error="props.error" @close="emit('close')">
    <section class="members">
      <header class="section-head">
        <p class="rule">
          구성원은 RACI 매트릭스의 <strong>열</strong>이 됩니다. 같은 프로젝트 안에서 이름은 겹칠 수
          없습니다 — 겹치면 매트릭스에서 누가 누구인지 구분할 수 없기 때문입니다.
        </p>
        <button type="button" class="add" @click="openAdd">＋ 구성원 추가</button>
      </header>

      <ModalDialog
        v-if="addOpen"
        title="구성원 추가"
        :error="props.error"
        @close="addOpen = false"
      >
        <form class="add-form" @submit.prevent="onSubmit">
          <label>
            이름
            <input v-model="draft.name" type="text" placeholder="이름" />
          </label>

          <label>
            직책
            <input v-model="draft.position" type="text" placeholder="예: PM, 백엔드 (선택)" />
          </label>

          <label class="email">
            이메일
            <input v-model="draft.email" type="email" placeholder="선택" />
          </label>

          <div class="dialog-actions">
            <button type="submit" class="primary" :disabled="!submittable">추가</button>
            <button type="button" @click="addOpen = false">취소</button>
          </div>
        </form>
      </ModalDialog>

      <p v-if="loading">불러오는 중...</p>
      <template v-else>
        <ul v-if="members.length > 0" class="list">
          <li
            v-for="member in members"
            :key="member.id"
            :class="{ editing: editingId === member.id }"
          >
            <template v-if="editingId === member.id">
              <input v-model="editDraft.name" type="text" aria-label="이름" />
              <input v-model="editDraft.position" type="text" aria-label="직책" placeholder="직책" />
              <input v-model="editDraft.email" type="email" aria-label="이메일" placeholder="이메일" />

              <span class="actions">
                <button type="button" class="primary" :disabled="!editSubmittable" @click="onSave">
                  저장
                </button>
                <button type="button" @click="cancelEdit">취소</button>
              </span>
            </template>

            <template v-else>
              <span class="name">{{ member.name }}</span>
              <span v-if="member.position" class="position">{{ member.position }}</span>
              <span v-if="member.email" class="email-text">{{ member.email }}</span>

              <span class="actions">
                <button type="button" @click="startEdit(member)">수정</button>
                <button type="button" class="danger" @click="onRemove(member)">삭제</button>
              </span>
            </template>
          </li>
        </ul>
        <p v-else class="none">등록된 구성원이 없습니다.</p>
      </template>
    </section>
  </ModalDialog>
</template>

<style scoped>
.section-head {
  display: flex;
  align-items: flex-start;
  gap: 0.75rem;
  margin-bottom: 0.75rem;
}

.rule {
  flex: 1;
  font-size: 0.78rem;
  color: var(--text-faint);
  line-height: 1.5;
  margin: 0;
}

.add {
  flex: none;
  padding: 0.35rem 0.7rem;
  border: 1px solid var(--border-input);
  border-radius: 999px;
  background: var(--surface);
  color: var(--text-muted);
  font: inherit;
  font-size: 0.78rem;
  cursor: pointer;
}

.add:hover {
  border-color: var(--accent-border);
  color: var(--text-h);
}

.add-form {
  display: flex;
  flex-direction: column;
  gap: 0.7rem;
}

.dialog-actions {
  display: flex;
  gap: 0.5rem;
}

label {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  font-size: 0.8rem;
  color: var(--text-muted);
}

input {
  padding: 0.4rem 0.55rem;
  border: 1px solid var(--border-input);
  border-radius: 6px;
  font: inherit;
  font-size: 0.85rem;
}

.email input {
  min-width: 14rem;
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

.list {
  list-style: none;
  padding: 0;
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
}

.list li {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.85rem;
  padding: 0.4rem 0.6rem;
  border: 1px solid var(--border-soft);
  border-radius: 6px;
}

.list li.editing {
  border-color: var(--accent-border);
  background: var(--accent-weak);
  flex-wrap: wrap;
}

.list input {
  padding: 0.25rem 0.4rem;
  font-size: 0.8rem;
}

.name {
  font-weight: 600;
}

.position {
  font-size: 0.72rem;
  color: var(--badge-planned-fg);
  background: var(--badge-planned-bg);
  border-radius: 999px;
  padding: 0.1rem 0.5rem;
}

.email-text {
  color: var(--text-faint);
  font-size: 0.78rem;
}

.actions {
  margin-left: auto;
  display: flex;
  gap: 0.35rem;
}

.actions button {
  padding: 0.25rem 0.55rem;
  font-size: 0.75rem;
}

.actions button.danger {
  color: var(--danger);
  border-color: var(--danger-border);
}

.none {
  font-size: 0.85rem;
  color: var(--text-faint);
}
</style>
