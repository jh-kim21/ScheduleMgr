<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { MemberInput, ProjectMember } from '../../api/memberApi'

const props = defineProps<{
  members: ProjectMember[]
}>()

const emit = defineEmits<{
  add: [input: MemberInput]
  update: [memberId: number, input: MemberInput]
  remove: [memberId: number]
}>()

const blank = (): MemberInput => ({ name: '', email: null, position: null })

const draft = ref<MemberInput>(blank())

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
      draft.value = blank()
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
  <section class="members">
    <h2>프로젝트 구성원</h2>

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

      <button type="submit" class="primary" :disabled="!submittable">추가</button>
    </form>

    <p class="rule">
      구성원은 RACI 매트릭스의 <strong>열</strong>이 됩니다. 같은 프로젝트 안에서 이름은 겹칠 수
      없습니다 — 겹치면 매트릭스에서 누가 누구인지 구분할 수 없기 때문입니다.
    </p>

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
  </section>
</template>

<style scoped>
.members {
  margin-top: 1.75rem;
}

h2 {
  font-size: 1rem;
  margin: 0 0 0.75rem;
}

.add-form {
  display: flex;
  align-items: flex-end;
  gap: 0.6rem;
  flex-wrap: wrap;
  padding: 0.85rem;
  border: 1px solid var(--border);
  border-radius: 8px;
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

.rule {
  margin-top: 0.5rem;
  font-size: 0.78rem;
  color: var(--text-faint);
}

.list {
  list-style: none;
  padding: 0;
  margin: 0.85rem 0 0;
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
  margin-top: 0.85rem;
  font-size: 0.85rem;
  color: var(--text-faint);
}
</style>
