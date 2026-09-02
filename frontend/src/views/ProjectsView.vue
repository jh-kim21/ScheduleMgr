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
        <button type="button" :disabled="importing" @click="fileInput?.click()">
          {{ importing ? '가져오는 중...' : '가져오기' }}
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

    <p v-if="importError" class="error">{{ importError }}</p>
    <p v-else-if="importedName" class="imported">
      <strong>{{ importedName }}</strong> 프로젝트를 가져왔습니다. 이름이 겹치면 뒤에 "(가져옴)"이
      붙습니다.
    </p>

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

.import button {
  padding: 0.35rem 0.8rem;
  border: 1px solid var(--border-input);
  border-radius: 6px;
  background: var(--surface);
  color: var(--text-muted);
  font: inherit;
  font-size: 0.8rem;
  cursor: pointer;
}

.import button:disabled {
  background: var(--disabled-bg);
  border-color: var(--disabled-border);
  color: var(--disabled-fg);
  cursor: not-allowed;
}

.imported {
  font-size: 0.85rem;
  color: var(--success-text);
  background: var(--success-weak);
  border-radius: 6px;
  padding: 0.5rem 0.7rem;
  margin-bottom: 0.75rem;
}

h1 {
  font-size: 1.4rem;
  margin-bottom: 1rem;
}

.error {
  color: var(--danger);
}
</style>
