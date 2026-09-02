<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import type { WbsItemInput, WbsMoveInput, WbsNode } from '../api/wbsApi'
import WbsForm from '../features/wbs/WbsForm.vue'
import WbsTree from '../features/wbs/WbsTree.vue'
import { useWbs } from '../features/wbs/useWbs'
import { useProjects } from '../features/projects/useProjects'
import { ensureSelection, selectedProjectId } from '../stores/projectSelection'
import { needsAttention } from '../shared/delay'

const { projects, error: projectsError, ensureLoaded: ensureProjects } = useProjects()
const { tree, referenceDate, loading, error, ensureLoaded, create, update, move, remove } = useWbs()


const editing = ref<WbsNode | null>(null)
const parentForNew = ref<WbsNode | null>(null)

// The selection watcher is the single load path: `immediate` covers arriving with a project
// already chosen, and `ensureSelection` below covers the first ever visit by setting one.
watch(
  selectedProjectId,
  (id, previous) => {
    if (previous !== undefined) {
      editing.value = null
      parentForNew.value = null
    }
    if (id !== null) ensureLoaded(id)
  },
  { immediate: true },
)

onMounted(async () => {
  await ensureProjects()
  ensureSelection(projects.value.map((project) => project.id))
})

/** Leaf rows only: a summary already reflects its children's delay. */
function flatten(nodes: WbsNode[]): WbsNode[] {
  return nodes.flatMap((node) => [node, ...flatten(node.children)])
}
const attention = computed(() =>
  flatten(tree.value).filter((node) => !node.summary && needsAttention(node)),
)
const delayedCount = computed(
  () => attention.value.filter((node) => node.delayStatus === 'DELAYED').length,
)
const atRiskCount = computed(
  () => attention.value.filter((node) => node.delayStatus === 'AT_RISK').length,
)

async function handleSubmit(input: WbsItemInput) {
  const id = selectedProjectId.value
  if (id === null) return
  if (editing.value) {
    await update(id, editing.value.id, input)
    editing.value = null
  } else {
    await create(id, parentForNew.value?.id ?? null, input)
    parentForNew.value = null
  }
}

function startAddChild(parent: WbsNode) {
  editing.value = null
  parentForNew.value = parent
}

function startEdit(node: WbsNode) {
  parentForNew.value = null
  editing.value = node
}

function cancelForm() {
  editing.value = null
  parentForNew.value = null
}

async function handleRemove(node: WbsNode) {
  const id = selectedProjectId.value
  if (id === null) return
  const warning = node.children.length > 0 ? '\n하위 항목도 모두 함께 삭제됩니다.' : ''
  if (!confirm(`"${node.code} ${node.name}" 항목을 삭제할까요?${warning}`)) return
  await remove(id, node.id)
  if (editing.value?.id === node.id) editing.value = null
  if (parentForNew.value?.id === node.id) parentForNew.value = null
}

async function handleMove(itemId: number, input: WbsMoveInput) {
  const id = selectedProjectId.value
  if (id === null) return
  await move(id, itemId, input)
}
</script>

<template>
  <section>
    <h1>WBS</h1>

    <p v-if="projectsError" class="error">{{ projectsError }}</p>

    <p v-else-if="projects.length === 0" class="notice">
      먼저 프로젝트를 등록해야 WBS를 작성할 수 있습니다.
      <RouterLink to="/projects">프로젝트 화면으로 이동</RouterLink>
    </p>

    <template v-else>
      <label class="project-picker">
        프로젝트
        <select v-model="selectedProjectId">
          <option v-for="project in projects" :key="project.id" :value="project.id">
            {{ project.name }}
          </option>
        </select>
      </label>

      <WbsForm
        :editing="editing"
        :parent="parentForNew"
        @submit="handleSubmit"
        @cancel="cancelForm"
      />

      <p v-if="error" class="error">{{ error }}</p>

      <p v-if="attention.length > 0" class="attention">
        <!-- "업무"를 붙여, Summary 행에도 배지가 달리는 것과 달리 이 숫자는 leaf 기준임을 드러낸다. -->
        <template v-if="delayedCount > 0"><strong>지연 업무 {{ delayedCount }}건</strong></template>
        <template v-if="delayedCount > 0 && atRiskCount > 0"> · </template>
        <template v-if="atRiskCount > 0">지연 위험 {{ atRiskCount }}건</template>
        <span v-if="referenceDate" class="reference">기준일 {{ referenceDate }}</span>
        <RouterLink to="/gantt" class="detail">간트에서 보기</RouterLink>
      </p>

      <p v-if="loading">불러오는 중...</p>
      <WbsTree
        v-else
        :tree="tree"
        @add-child="startAddChild"
        @edit="startEdit"
        @remove="handleRemove"
        @move="handleMove"
      />
    </template>
  </section>
</template>

<style scoped>
h1 {
  font-size: 1.4rem;
  margin-bottom: 1rem;
}

.project-picker {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.85rem;
  color: var(--text-muted);
  margin-bottom: 1.25rem;
}

.project-picker select {
  padding: 0.4rem 0.6rem;
  border: 1px solid var(--border-input);
  border-radius: 6px;
  font: inherit;
}

.error {
  color: var(--danger);
  margin-bottom: 0.75rem;
}

.attention {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.82rem;
  color: var(--warn-strong);
  background: var(--warn-weak);
  border-left: 3px solid var(--warn);
  border-radius: 6px;
  padding: 0.45rem 0.65rem;
  margin-bottom: 0.75rem;
}

.attention .reference {
  color: var(--warn-badge-fg);
}

.attention .detail {
  margin-left: auto;
  color: var(--warn-strong);
}

.notice {
  color: var(--text-dim);
}
</style>
