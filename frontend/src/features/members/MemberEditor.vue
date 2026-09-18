<script setup lang="ts">
import { computed, ref } from 'vue'
import type { MemberInput, ProjectMember } from '../../api/memberApi'
import ModalDialog from '../../components/ModalDialog.vue'

const props = defineProps<{
  /** 대화상자 제목에 넣을 프로젝트 이름 — "구성원 관리 — 웹사이트 개편"처럼 문맥을 붙인다. */
  projectName: string
  members: ProjectMember[]
  loading: boolean
  /** 저장이 거부된 이유(이름 중복 등). 대화상자 안에 보여야 사용자가 볼 수 있다. */
  error?: string | null
  /**
   * 추가·수정 제출은 emit이 아니라 함수 prop이다 — `emit`은 반환값을 받을 수 없는데,
   * `useMembers.mutate`가 이미 서버가 받아들였는지를 `Promise<boolean>`으로 돌려준다
   * (CLAUDE.md "변경 함수가 boolean을 돌려주는 이유"). 그 값을 그대로 전달받아야
   * "성공했을 때만 닫는다"를 목록 변화를 추측하지 않고 정확히 판단할 수 있다. 실제 API 호출은
   * 여전히 `ProjectsView`가 한다(Step 1의 `readOnly` 게이팅 구조를 그대로 유지) — 이 prop은
   * 그 결과만 돌려받는 통로다.
   */
  onSubmitAdd: (input: MemberInput) => Promise<boolean>
  onSubmitUpdate: (memberId: number, input: MemberInput) => Promise<boolean>
}>()

const emit = defineEmits<{
  remove: [memberId: number]
  close: []
}>()

/** CLAUDE.md 화면 레이아웃 규칙: 폼이 자기 제목을 계산해 ModalDialog에 넘긴다. */
const title = computed(() => `구성원 관리 — ${props.projectName}`)

const blank = (): MemberInput => ({ name: '', email: null, position: null })

/**
 * 추가·수정 공용 폼 — 하나의 대화상자, 하나의 draft다(CLAUDE.md "모든 추가·수정 폼은 대화상자").
 * 예전에는 추가만 대화상자였고 수정은 목록 행 안에서 바로 고치는 인라인 폼이었다 — `ModalDialog`가
 * 중첩(추가 대화상자가 이미 "구성원 관리" 대화상자 안에 있다)을 지원하지 못했기 때문이다. 중첩
 * 지원이 들어온 뒤로는 이 화면도 다른 화면과 같은 규칙을 따를 수 있어, 수정도 같은 대화상자를
 * 공유하도록 합쳤다 — `draft`/`editDraft`, `submittable`/`editSubmittable`처럼 사실상 같은 값을
 * 두 벌 관리할 이유가 없었다.
 */
const formOpen = ref(false)
const draft = ref<MemberInput>(blank())
/** `null`이면 추가 모드, 아니면 그 id의 구성원을 수정하는 중이다. */
const editingId = ref<number | null>(null)

const formTitle = computed(() => (editingId.value === null ? '구성원 추가' : '구성원 수정'))
const submittable = computed(() => draft.value.name.trim().length > 0)

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

function openAdd() {
  editingId.value = null
  draft.value = blank()
  formOpen.value = true
}

function startEdit(member: ProjectMember) {
  editingId.value = member.id
  draft.value = { name: member.name, email: member.email, position: member.position }
  formOpen.value = true
}

function closeForm() {
  formOpen.value = false
  editingId.value = null
  draft.value = blank()
}

/** 성공했을 때만(prop 함수가 `true`를 돌려줄 때만) 닫는다 — 거부되면 입력값과 오류가 그대로 남는다. */
async function onSubmit() {
  if (!submittable.value) return
  const ok =
    editingId.value === null
      ? await props.onSubmitAdd(payload(draft.value))
      : await props.onSubmitUpdate(editingId.value, payload(draft.value))
  if (ok) closeForm()
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

      <!-- 추가·수정 공용 대화상자. ModalDialog 중첩 지원(Step 2) 위에서 동작한다 — 이 대화상자가
           열려 있는 동안 바깥 "구성원 관리" 대화상자는 Escape·Tab에 반응하지 않고 배경으로
           물러난다. -->
      <ModalDialog
        v-if="formOpen"
        :title="formTitle"
        :error="props.error"
        @close="closeForm"
      >
        <form class="member-form" @submit.prevent="onSubmit">
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
            <button type="submit" class="primary" :disabled="!submittable">
              {{ editingId === null ? '추가' : '저장' }}
            </button>
            <button type="button" @click="closeForm">취소</button>
          </div>
        </form>
      </ModalDialog>

      <p v-if="loading">불러오는 중…</p>
      <template v-else>
        <ul v-if="members.length > 0" class="list">
          <li
            v-for="member in members"
            :key="member.id"
            :class="{ editing: editingId === member.id }"
          >
            <span class="name">{{ member.name }}</span>
            <span v-if="member.position" class="position">{{ member.position }}</span>
            <span v-if="member.email" class="email-text">{{ member.email }}</span>

            <span class="actions">
              <button type="button" @click="startEdit(member)">수정</button>
              <button type="button" class="danger" @click="onRemove(member)">삭제</button>
            </span>
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

/* border·배경·font·cursor는 전역 기본과 같아 지웠다. */
.add {
  flex: none;
  padding: 0.35rem 0.7rem;
  border-radius: 999px;
  color: var(--text-muted);
  font-size: 0.78rem;
}

.add:hover {
  border-color: var(--accent-border);
  color: var(--text-h);
}

.member-form {
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

/* 패딩·border·radius·font는 전역 기본과 같아(패딩은 아주 살짝만 다름) 지웠다 — font-size만 남긴다. */
input {
  font-size: 0.85rem;
}

.email input {
  min-width: 14rem;
}

/* 패딩·radius·border·배경·cursor는 전역 기본과 완전히 같은 값이라 지웠다. `.primary`와 그
   :disabled 변형도 전역 계약과 같아 통째로 지웠다. */
button {
  color: var(--text-muted);
  font-size: 0.85rem;
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

/* 이 행의 구성원을 지금 수정 대화상자에서 고치는 중임을 보여준다. */
.list li.editing {
  border-color: var(--accent-border);
  background: var(--accent-weak);
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

/* `.danger`가 전역 계약과 완전히 같은 값이라 지웠다 — 템플릿의 class="danger"가 이미 그 색을 준다. */

.none {
  font-size: 0.85rem;
  color: var(--text-faint);
}
</style>
