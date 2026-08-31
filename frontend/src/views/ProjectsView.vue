<script setup lang="ts">
import { onMounted, ref } from 'vue'
import type { Project, ProjectInput } from '../api/projectApi'
import ProjectForm from '../features/projects/ProjectForm.vue'
import ProjectList from '../features/projects/ProjectList.vue'
import { useProjects } from '../features/projects/useProjects'

const { projects, loading, error, ensureLoaded, create, update, remove } = useProjects()
const editing = ref<Project | null>(null)

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
    <h1>프로젝트</h1>

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
h1 {
  font-size: 1.4rem;
  margin-bottom: 1rem;
}

.error {
  color: var(--danger);
}
</style>
