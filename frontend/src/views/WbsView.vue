<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import type { WbsItemInput, WbsMoveInput, WbsNode } from '../api/wbsApi'
import WbsForm from '../features/wbs/WbsForm.vue'
import WbsImportForm from '../features/wbs/WbsImportForm.vue'
import WbsTree from '../features/wbs/WbsTree.vue'
import { useWbs } from '../features/wbs/useWbs'
import { useProjects } from '../features/projects/useProjects'
import { ensureSelection, selectedProjectId } from '../stores/projectSelection'
import { needsAttention } from '../shared/delay'

const route = useRoute()
const router = useRouter()

/**
 * Arriving from a Backlog entry's WBS link: reveal that row (Step 3 지시서 7항). The query is
 * cleared right away so a later reload does not scroll the user somewhere they did not ask for,
 * while the id kept here still drives the highlight.
 */
const focusId = ref<number | null>(null)
watch(
  () => route.query.focus,
  async (value) => {
    if (value === undefined) return
    const id = Number(Array.isArray(value) ? value[0] : value)
    focusId.value = Number.isFinite(id) ? id : null
    await router.replace({ path: '/wbs' })
  },
  { immediate: true },
)

const { projects, error: projectsError, ensureLoaded: ensureProjects } = useProjects()
const { tree, referenceDate, loading, error, ensureLoaded, create, update, move, remove, importFile } =
  useWbs()


const editing = ref<WbsNode | null>(null)
const parentForNew = ref<WbsNode | null>(null)
/** 폼은 대화상자로 띄운다 — 이 화면의 주된 행위는 트리를 읽는 것이다. */
const formOpen = ref(false)
const importFormOpen = ref(false)

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

/** The node whose `children` array contains `target`, or `null` if `target` is a root. */
function findParent(nodes: WbsNode[], target: WbsNode): WbsNode | null {
  for (const node of nodes) {
    if (node.children.some((child) => child.id === target.id)) return node
    const found = findParent(node.children, target)
    if (found) return found
  }
  return null
}

/**
 * The rows `WbsForm`'s weight suggestion is derived from — 편집 중이면 형제(자기 자신 제외), 하위
 * 추가면 그 부모의 자식들, 최상위 추가면 트리 루트 전체.
 */
const siblings = computed<WbsNode[]>(() => {
  if (editing.value) {
    const parent = findParent(tree.value, editing.value)
    const pool = parent ? parent.children : tree.value
    return pool.filter((node) => node.id !== editing.value?.id)
  }
  if (parentForNew.value) return parentForNew.value.children
  return tree.value
})
const attention = computed(() =>
  flatten(tree.value).filter((node) => !node.summary && needsAttention(node)),
)
const delayedCount = computed(
  () => attention.value.filter((node) => node.delayStatus === 'DELAYED').length,
)
const atRiskCount = computed(
  () => attention.value.filter((node) => node.delayStatus === 'AT_RISK').length,
)

/** 거부되면 대화상자를 열어 둔 채 사유를 보여 준다 — 입력을 다시 치게 만들면 안 된다. */
async function handleSubmit(input: WbsItemInput) {
  const id = selectedProjectId.value
  if (id === null) return
  const ok = editing.value
    ? await update(id, editing.value.id, input)
    : await create(id, parentForNew.value?.id ?? null, input)
  if (ok) closeForm()
}

function openAddRoot() {
  editing.value = null
  parentForNew.value = null
  formOpen.value = true
}

function startAddChild(parent: WbsNode) {
  editing.value = null
  parentForNew.value = parent
  formOpen.value = true
}

function startEdit(node: WbsNode) {
  parentForNew.value = null
  editing.value = node
  formOpen.value = true
}

function closeForm() {
  formOpen.value = false
  editing.value = null
  parentForNew.value = null
  // 폼 안의 체크포인트 추가·승인·삭제는 자기 API로 즉시 저장되고 markWbsChanged() 를 부른다
  // (useProgress.mutate). 하지만 이 화면은 watch(selectedProjectId, …) 하나로만 로드하므로,
  // 폼을 닫는 것만으로는 트리를 다시 읽지 않아 그 항목의 진척(%)이 낡은 채로 남는다.
  // ensureLoaded 는 캐시 키가 그대로면 아무 일도 하지 않으므로(무료), 체크포인트를 건드리지
  // 않고 닫았을 때는 비용이 없다 — Dashboard 탭 전환과 같은 패턴(CLAUDE.md).
  const id = selectedProjectId.value
  if (id !== null) ensureLoaded(id)
}

function openImportForm() {
  importFormOpen.value = true
}

function closeImportForm() {
  importFormOpen.value = false
}

/** 거부되면(행별 사유가 줄바꿈으로 이어진 메시지) 대화상자를 열어 둔 채 보여 준다. */
async function handleImportSubmit(input: { file: File; parentId: number | null }) {
  const id = selectedProjectId.value
  if (id === null) return
  const ok = await importFile(id, input)
  if (ok) closeImportForm()
}

async function handleRemove(node: WbsNode) {
  const id = selectedProjectId.value
  if (id === null) return
  const warning = node.children.length > 0 ? '\n하위 항목도 모두 함께 삭제됩니다.' : ''
  if (!confirm(`"${node.code} ${node.name}" 항목을 삭제할까요?${warning}`)) return
  await remove(id, node.id)
  if (editing.value?.id === node.id || parentForNew.value?.id === node.id) closeForm()
}

async function handleMove(itemId: number, input: WbsMoveInput) {
  const id = selectedProjectId.value
  if (id === null) return
  await move(id, itemId, input)
}
</script>

<template>
  <section>
    <div class="head">
      <h1>WBS</h1>
      <div v-if="projects.length > 0" class="head-actions">
        <button type="button" class="add ghost" @click="openImportForm">＋ 파일에서 가져오기</button>
        <button type="button" class="add" @click="openAddRoot">＋ 최상위 항목 추가</button>
      </div>
    </div>

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
        v-if="formOpen"
        :editing="editing"
        :parent="parentForNew"
        :siblings="siblings"
        :error="error"
        :project-id="selectedProjectId"
        @submit="handleSubmit"
        @cancel="closeForm"
      />

      <WbsImportForm
        v-if="importFormOpen"
        :tree="tree"
        :error="error"
        @submit="handleImportSubmit"
        @cancel="closeImportForm"
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
        :focus-id="focusId"
        @add-child="startAddChild"
        @edit="startEdit"
        @remove="handleRemove"
        @move="handleMove"
      />
    </template>
  </section>
</template>

<style scoped>
.head {
  display: flex;
  align-items: center;
  gap: 1rem;
  margin-bottom: 1rem;
}

h1 {
  font-size: 1.4rem;
  margin: 0;
}

.head-actions {
  margin-left: auto;
  display: flex;
  gap: 0.5rem;
}

.add {
  padding: 0.4rem 0.85rem;
  border: 1px solid var(--accent);
  border-radius: 999px;
  background: var(--accent);
  color: var(--accent-fg);
  font: inherit;
  font-size: 0.8rem;
  font-weight: 500;
  cursor: pointer;
  white-space: nowrap;
}

.add:hover {
  box-shadow: var(--elevation-1);
}

.add.ghost {
  background: transparent;
  color: var(--text-muted);
  border-color: var(--border-input);
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
