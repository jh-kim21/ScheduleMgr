<script setup lang="ts">
import { onMounted, ref } from 'vue'
import type { Project, ProjectInput } from '../api/projectApi'
import ProjectForm from '../features/projects/ProjectForm.vue'
import ProjectList from '../features/projects/ProjectList.vue'
import { useProjects } from '../features/projects/useProjects'
import { projectApi } from '../api/projectApi'
import { ApiError } from '../api/http'

const { projects, loading, error, ensureLoaded, load, create, update, remove } = useProjects()
const editing = ref<Project | null>(null)

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

async function handleSubmit(input: ProjectInput) {
  if (editing.value) {
    await update(editing.value.id, input)
    editing.value = null
  } else {
    await create(input)
  }
}

async function handleRemove(project: Project) {
  if (!confirm(`"${project.name}" 프로젝트를 삭제할까요?`)) return
  await remove(project.id)
  if (editing.value?.id === project.id) editing.value = null
}
</script>

<template>
  <section>
    <div class="head">
      <h1>프로젝트</h1>

      <span class="import">
        <!--
          Material 의 filled tonal button: 주 동작이지만 페이지를 지배하지 않을 때 쓴다.
          가져오기는 자주 누르는 버튼이 아니라서 채움(filled)보다 이쪽이 맞다.
        -->
        <button type="button" class="md-tonal" :disabled="importing" @click="fileInput?.click()">
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
      :editing="editing"
      @submit="handleSubmit"
      @cancel="editing = null"
    />

    <p v-if="error" class="error">{{ error }}</p>
    <p v-else-if="loading">불러오는 중...</p>
    <ProjectList
      v-else
      :projects="projects"
      @edit="(p) => (editing = p)"
      @remove="handleRemove"
    />
  </section>
</template>

<style scoped>
.head {
  display: flex;
  align-items: center;
  gap: 1rem;
  margin-bottom: 1rem;
}

.head h1 {
  margin-bottom: 0;
}

.import {
  margin-left: auto;
}

.md-tonal {
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  padding: 0.4rem 0.9rem 0.4rem 0.75rem;
  border: none;
  border-radius: 999px;
  background: var(--accent-container);
  color: var(--accent-container-fg);
  font: inherit;
  font-size: 0.8rem;
  font-weight: 500;
  letter-spacing: 0.01em;
  cursor: pointer;
  overflow: hidden;
  box-shadow: var(--elevation-1);
  transition: box-shadow 140ms ease;
}

/* 상태 레이어를 겹치는 층으로 둔다 — 배경색을 갈아치우지 않으므로 다크 모드에 색을 더 정의할 필요가 없다. */
.md-tonal::before {
  content: '';
  position: absolute;
  inset: 0;
  background: transparent;
  transition: background 120ms ease;
}

.md-tonal:hover {
  box-shadow: var(--elevation-2);
}

.md-tonal:hover::before {
  background: var(--state-hover);
}

.md-tonal:active {
  box-shadow: var(--elevation-1);
}

.md-tonal:active::before {
  background: var(--state-press);
}

.md-tonal:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: 2px;
}

.md-tonal:disabled {
  background: var(--disabled-bg);
  color: var(--disabled-fg);
  box-shadow: none;
  cursor: default;
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
  font: inherit;
  font-size: 0.75rem;
  cursor: pointer;
}

.md-banner .dismiss:hover {
  opacity: 1;
  background: var(--state-hover);
}

h1 {
  font-size: 1.4rem;
  margin-bottom: 1rem;
}

.error {
  color: var(--danger);
}
</style>
