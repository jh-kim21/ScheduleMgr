<script setup lang="ts">
import { onMounted, ref } from 'vue'
import type { MemberInput } from '../api/memberApi'
import type { Project, ProjectInput } from '../api/projectApi'
import CommitPanel from '../features/commit/CommitPanel.vue'
import MemberEditor from '../features/members/MemberEditor.vue'
import { useMembers } from '../features/members/useMembers'
import WbsTagEditor from '../features/wbs/WbsTagEditor.vue'
import ProjectForm from '../features/projects/ProjectForm.vue'
import ProjectList from '../features/projects/ProjectList.vue'
import { useProjects } from '../features/projects/useProjects'
import { projectApi } from '../api/projectApi'
import { ApiError } from '../api/http'
import { readOnly } from '../stores/commitView'

const { projects, loading, error, ensureLoaded, load, create, update, remove } = useProjects()
const editing = ref<Project | null>(null)
/** 폼은 대화상자로 띄운다 — 이 화면의 주된 행위는 목록을 읽는 것이다. */
const formOpen = ref(false)
/** 저장 거부 사유. 목록 로딩 오류(`error`)와 섞이면 안 되므로 따로 둔다. */
const saveError = ref<string | null>(null)

/**
 * 구성원 관리 대화상자. 전역 `selectedProjectId`에 기대지 않는다 — 이 화면은 목록 화면이라
 * "지금 선택된 프로젝트"라는 개념이 없고, 행에서 누른 프로젝트가 곧 대상이다.
 */
const {
  members,
  loading: membersLoading,
  error: membersError,
  ensureLoaded: ensureMembers,
  create: createMember,
  update: updateMember,
  remove: removeMember,
} = useMembers()
const membersProject = ref<Project | null>(null)

function openMembers(project: Project) {
  // ProjectList가 이미 "구성원" 버튼을 막지만, 여기서도 한 번 더 막는다 — 구성원 추가·수정·삭제는
  // RACI 행의 쓰기 진입점이고, 이 대화상자가 그 유일한 경로다(지시서 5.2).
  if (readOnly.value) return
  membersProject.value = project
  ensureMembers(project.id)
}

function closeMembers() {
  membersProject.value = null
}

/**
 * emit이 아니라 `MemberEditor`의 함수 prop으로 넘긴다 — `useMembers.create`/`update`가 이미
 * 서버가 받아들였는지를 `Promise<boolean>`으로 돌려주므로(CLAUDE.md "변경 함수가 boolean을
 * 돌려주는 이유"), 그 값을 그대로 돌려줘야 대화상자가 문자열 비교 같은 추측 없이 정확히
 * "성공했을 때만 닫는다"를 할 수 있다. `readOnly`·`membersProject` 게이팅은 여기 그대로
 * 둔다 — `MemberEditor`가 API를 직접 부르게 하지 않는 구조(Step 1)는 바뀌지 않았다.
 */
async function handleAddMember(input: MemberInput): Promise<boolean> {
  // openMembers가 이미 대화상자 자체를 막지만, 열려 있는 대화상자 안에서 굳이 API를 부르지
  // 않도록 여기서도 한 번 더 막는다 — `ModalDialog` 중첩 결함이 그 방패를 무너뜨릴 수 있다는
  // 것이 별도로 확인됐다(지시서 2-a).
  if (readOnly.value || !membersProject.value) return false
  return createMember(membersProject.value.id, input)
}

async function handleUpdateMember(memberId: number, input: MemberInput): Promise<boolean> {
  if (readOnly.value || !membersProject.value) return false
  return updateMember(membersProject.value.id, memberId, input)
}

function handleRemoveMember(memberId: number) {
  if (readOnly.value) return
  if (membersProject.value) removeMember(membersProject.value.id, memberId)
}

/**
 * 업무 분야(태그) 관리 대화상자. `membersProject`와 같은 이유로 전역 선택이 아니라 방금 누른 행을
 * 기억한다. 목록·추가·수정·삭제는 `WbsTagEditor`가 `useWbsTags`로 직접 한다 — 거부됐는지 알아야
 * "성공했을 때만 닫는다"를 지킬 수 있는데, emit만으로는 결과가 돌아오지 않는다.
 */
const tagsProject = ref<Project | null>(null)

function openTags(project: Project) {
  // ProjectList가 이미 "분야" 버튼을 막지만, 여기서도 한 번 더 막는다 — 태그 추가·수정·삭제는
  // 커밋 조회 중에 잠겨야 할 쓰기 진입점이다(지시서 5.2).
  if (readOnly.value) return
  tagsProject.value = project
}

function closeTags() {
  tagsProject.value = null
}

/**
 * 커밋 히스토리 대화상자. `membersProject`와 같은 이유로 전역 선택이 아니라 방금 누른 행을
 * 기억한다.
 */
const commitsProject = ref<Project | null>(null)

function openCommits(project: Project) {
  commitsProject.value = project
}

function closeCommits() {
  commitsProject.value = null
}

/** 복원은 새 프로젝트를 만드므로, 목록 맨 아래에 나타나도록 다시 읽는다. */
function handleRestored() {
  load()
}

/** 가져오기 결과는 목록 로딩 오류와 섞이면 안 되므로 따로 둔다. */
const importError = ref<string | null>(null)
const importedName = ref<string | null>(null)
const importing = ref(false)
const fileInput = ref<HTMLInputElement | null>(null)

/**
 * 파일 내용을 손대지 않고 서버로 넘긴다. 클라이언트에서 parse·검증하면 서버 검증과 두 벌이
 * 되고, 무엇이 잘못됐는지 판단하는 곳도 두 군데가 된다.
 */
async function handleFile(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  // ProjectList/버튼이 이미 가져오기 입력을 막지만, 커밋 시점을 보는 동안에는 여기서도 한 번 더
  // 막는다 — 프로젝트 화면의 쓰기 진입점 중 하나다(지시서 5.2).
  if (readOnly.value) {
    input.value = ''
    return
  }

  importing.value = true
  importError.value = null
  importedName.value = null
  try {
    const project = await projectApi.importProject(await file.text())
    // 새 프로젝트가 목록 맨 아래에 생기므로 목록을 다시 읽는다.
    await load()
    importedName.value = project.name
  } catch (e) {
    importError.value =
      e instanceof ApiError ? e.message : '파일을 읽을 수 없습니다. 내보낸 JSON 파일인지 확인하세요.'
  } finally {
    importing.value = false
    // 같은 파일을 다시 고를 수 있게 비운다 — 값이 같으면 change 이벤트가 안 난다.
    input.value = ''
  }
}

onMounted(ensureLoaded)

function openForm(project: Project | null) {
  // ProjectList가 이미 "수정" 버튼을 막지만, 여기서도 한 번 더 막는다 — 프로젝트 수정은 커밋
  // 시점을 보는 동안 잠겨야 할 쓰기 진입점이다(지시서 5.2). 새 프로젝트 추가는 표에 없으므로
  // 막지 않는다 — 보고 있는 커밋과 무관한, 별개의 프로젝트를 만드는 일이다.
  if (readOnly.value && project) return
  editing.value = project
  saveError.value = null
  formOpen.value = true
}

function closeForm() {
  formOpen.value = false
  editing.value = null
  saveError.value = null
}

/** ProjectForm의 제출 버튼을 잠그는 데 쓴다 — 느린 네트워크에서 두 번 눌러 두 프로젝트가 생기는 것을 막는다. */
const submitting = ref(false)

/** 거부되면 대화상자를 열어 둔 채 사유를 보여 준다 — 입력을 다시 치게 만들면 안 된다. */
async function handleSubmit(input: ProjectInput) {
  saveError.value = null
  submitting.value = true
  try {
    if (editing.value) {
      await update(editing.value.id, input)
    } else {
      await create(input)
    }
    closeForm()
  } catch (e) {
    saveError.value = e instanceof ApiError ? e.message : '저장하지 못했습니다.'
  } finally {
    submitting.value = false
  }
}

async function handleRemove(project: Project) {
  // ProjectList가 이미 삭제 버튼을 막지만, 여기서도 한 번 더 막는다(지시서 5.2).
  if (readOnly.value) return
  if (!confirm(`"${project.name}" 프로젝트를 삭제할까요?`)) return
  await remove(project.id)
  if (editing.value?.id === project.id) closeForm()
}
</script>

<template>
  <section>
    <div class="head">
      <h1>프로젝트</h1>

      <button type="button" class="add" @click="openForm(null)">＋ 프로젝트 추가</button>

      <span class="import">
        <!--
          Material 의 filled tonal button: 주 동작이지만 페이지를 지배하지 않을 때 쓴다.
          가져오기는 자주 누르는 버튼이 아니라서 채움(filled)보다 이쪽이 맞다.
        -->
        <button
          type="button"
          class="md-tonal"
          :disabled="importing || readOnly"
          :title="readOnly ? '커밋 시점을 보는 동안에는 가져올 수 없습니다.' : undefined"
          @click="fileInput?.click()"
        >
          <svg class="icon" viewBox="0 0 24 24" aria-hidden="true">
            <path
              d="M11 15V7.22L8.8 9.4 7.4 8l4.6-4.6L16.6 8l-1.4 1.4L13 7.22V15h-2ZM6 20a2 2 0 0 1-2-2v-3h2v3h12v-3h2v3a2 2 0 0 1-2 2H6Z"
            />
          </svg>
          {{ importing ? '가져오는 중' : '가져오기' }}
        </button>
        <input
          ref="fileInput"
          type="file"
          accept="application/json,.json"
          hidden
          @change="handleFile"
        />
      </span>
    </div>

    <!--
      Material 의 banner 에 가깝게: 아이콘 + 문장 + 닫기. 자동으로 사라지는 스낵바가 아니라
      배너인 이유는, 어떤 이름으로 들어왔는지가 사용자가 목록에서 찾을 때 필요한 정보라서다.
    -->
    <div v-if="importError" class="md-banner is-error" role="alert">
      <svg class="icon" viewBox="0 0 24 24" aria-hidden="true">
        <path d="M12 17a1.2 1.2 0 1 1 0-2.4A1.2 1.2 0 0 1 12 17Zm-1-4V7h2v6h-2Zm1 9a10 10 0 1 1 0-20 10 10 0 0 1 0 20Z" />
      </svg>
      <span>{{ importError }}</span>
      <button type="button" class="dismiss" title="닫기" @click="importError = null">✕</button>
    </div>
    <div v-else-if="importedName" class="md-banner is-ok" role="status">
      <svg class="icon" viewBox="0 0 24 24" aria-hidden="true">
        <path d="m10 16.4-4-4L7.4 11l2.6 2.6L16.6 7 18 8.4l-8 8ZM12 22a10 10 0 1 1 0-20 10 10 0 0 1 0 20Z" />
      </svg>
      <span>
        <strong>{{ importedName }}</strong> 프로젝트를 가져왔습니다. 이름이 겹치면 뒤에
        "(가져옴)"이 붙습니다.
      </span>
      <button type="button" class="dismiss" title="닫기" @click="importedName = null">✕</button>
    </div>

    <ProjectForm
      v-if="formOpen"
      :editing="editing"
      :error="saveError"
      :submitting="submitting"
      @submit="handleSubmit"
      @cancel="closeForm"
    />

    <p v-if="error" class="error" role="alert">{{ error }}</p>
    <p v-else-if="loading">불러오는 중…</p>
    <ProjectList
      v-else
      :projects="projects"
      @edit="openForm"
      @remove="handleRemove"
      @members="openMembers"
      @tags="openTags"
      @commits="openCommits"
    />

    <MemberEditor
      v-if="membersProject"
      :project-name="membersProject.name"
      :members="members"
      :loading="membersLoading"
      :error="membersError"
      :on-submit-add="handleAddMember"
      :on-submit-update="handleUpdateMember"
      @remove="handleRemoveMember"
      @close="closeMembers"
    />

    <WbsTagEditor
      v-if="tagsProject"
      :project-id="tagsProject.id"
      :project-name="tagsProject.name"
      @close="closeTags"
    />

    <CommitPanel
      v-if="commitsProject"
      :project="commitsProject"
      @close="closeCommits"
      @restored="handleRestored"
    />
  </section>
</template>

<style scoped>
/* flex-wrap — 제목·추가·가져오기 세 항목이 좁은 화면에서 겹치지 않고 다음 줄로 접힌다. */
.head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.5rem 1rem;
  margin-bottom: 1rem;
}

.head h1 {
  margin-bottom: 0;
}

/* 전역 버튼 기본과 겹치는 선언(패딩·`font: inherit`·`cursor`·border의 두께·모양)은 지웠다 —
   `border-color`만 남겨 강조색만 얹는다. 패딩은 전역 기본(0.45em 0.9em)으로 아주 살짝 커진다. */
.add {
  margin-left: auto;
  border-color: var(--accent);
  border-radius: 999px;
  background: var(--accent);
  color: var(--accent-fg);
  font-size: 0.8rem;
  font-weight: 500;
  white-space: nowrap;
}

.add:hover {
  box-shadow: var(--elevation-1);
}

/*
 * 전역 버튼이 이미 `position: relative`·`display: inline-flex`·`font: inherit`·`cursor: pointer`·
 * 상태 레이어(::before)·`:focus-visible`·`:disabled` 배경/글자색을 준다 — 여기 남기는 것은
 * 전역이 모르는 것뿐이다: 알약 모양, 톤 색, 그리고 이 버튼만의 고도(elevation) 변화.
 * `:hover`/`:active`/`:focus-visible`의 상태 레이어·배경·글자색 재선언은 전역과 값이 완전히
 * 같아 지웠다(전역 선택자의 특이도가 더 높아 사실 이미 죽어 있던 코드였다).
 */
.md-tonal {
  gap: 0.35rem;
  padding: 0.4rem 0.9rem 0.4rem 0.75rem;
  border: none;
  border-radius: 999px;
  background: var(--accent-container);
  color: var(--accent-container-fg);
  font-size: 0.8rem;
  font-weight: 500;
  letter-spacing: 0.01em;
  overflow: hidden;
  box-shadow: var(--elevation-1);
  transition: box-shadow 140ms ease;
}

/* 전역 ::before가 content·position·배경을 이미 주므로, 이 버튼만의 트랜지션만 얹는다. */
.md-tonal::before {
  transition: background 120ms ease;
}

.md-tonal:hover {
  box-shadow: var(--elevation-2);
}

.md-tonal:active {
  box-shadow: var(--elevation-1);
}

/* box-shadow만 이 버튼 고유 — 배경·글자색·cursor는 전역 :disabled가 더 높은 특이도로 이미 맡는다. */
.md-tonal:disabled {
  box-shadow: none;
}

.md-tonal .icon {
  width: 1.05rem;
  height: 1.05rem;
  fill: currentColor;
}

.md-banner {
  display: flex;
  align-items: flex-start;
  gap: 0.5rem;
  padding: 0.6rem 0.7rem;
  border-radius: 10px;
  margin-bottom: 0.85rem;
  font-size: 0.85rem;
  line-height: 1.5;
  box-shadow: var(--elevation-1);
}

.md-banner .icon {
  width: 1.1rem;
  height: 1.1rem;
  flex: none;
  margin-top: 0.1rem;
  fill: currentColor;
}

.md-banner span {
  flex: 1;
}

.md-banner.is-ok {
  background: var(--success-weak);
  color: var(--success-text);
}

.md-banner.is-error {
  background: var(--danger-weak);
  color: var(--danger);
}

.md-banner .dismiss {
  flex: none;
  padding: 0.05rem 0.3rem;
  border: none;
  border-radius: 999px;
  background: transparent;
  color: inherit;
  opacity: 0.65;
  font-size: 0.75rem;
}

/* 배경 틴트는 전역 hover 상태 레이어(::before)가 이미 준다 — 여기서는 아이콘 자체의 흐림만 푼다. */
.md-banner .dismiss:hover {
  opacity: 1;
}

h1 {
  font-size: 1.4rem;
  margin-bottom: 1rem;
}

.error {
  color: var(--danger);
}

/* 이 파일이 직접 들여오는 transition(box-shadow·상태 레이어 배경)만 끈다 — 전역 컨트롤 층의
   transition은 style.css가 이미 막는다. */
@media (prefers-reduced-motion: reduce) {
  .md-tonal,
  .md-tonal::before {
    transition: none;
  }
}
</style>
