<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import type { BacklogItem, BacklogItemInput } from '../api/backlogApi'
import BacklogForm from '../features/backlog/BacklogForm.vue'
import BacklogList from '../features/backlog/BacklogList.vue'
import { useBacklog } from '../features/backlog/useBacklog'
import {
  ARCHIVE_FILTER_LABELS,
  ARCHIVE_FILTER_ORDER,
  isFiltered,
  LINK_FILTER_LABELS,
  LINK_FILTER_ORDER,
} from '../features/backlog/backlogFilter'
import { useProjects } from '../features/projects/useProjects'
import {
  BACKLOG_STATUS_LABELS,
  BACKLOG_STATUS_ORDER,
  BACKLOG_TYPE_LABELS,
  BACKLOG_TYPE_ORDER,
} from '../shared/backlog'
import { ensureSelection, selectedProjectId } from '../stores/projectSelection'

const route = useRoute()
const router = useRouter()

const { projects, error: projectsError, ensureLoaded: ensureProjects } = useProjects()
const {
  data,
  members,
  workPackages,
  loading,
  error,
  filters,
  rows,
  parentOptions,
  ensureLoaded,
  create,
  update,
  setArchived,
  remove,
  resetFilters,
} = useBacklog()

const editing = ref<BacklogItem | null>(null)

/**
 * Collapsed by default, like the RAID form: this screen is mostly for reading the backlog, and an
 * always-open form would push the table below the fold.
 */
const formOpen = ref(false)

function openForm(item: BacklogItem | null) {
  editing.value = item
  formOpen.value = true
}

function closeForm() {
  formOpen.value = false
  editing.value = null
}

// The selection watcher is the single load path (`immediate` covers arriving with a project
// already chosen), matching the other screens.
watch(
  selectedProjectId,
  (id) => {
    closeForm()
    if (id !== null) ensureLoaded(id)
  },
  { immediate: true },
)

onMounted(async () => {
  await ensureProjects()
  ensureSelection(projects.value.map((project) => project.id))
})

/**
 * Arriving from a WBS row's linked-count link: pre-filter to that Work Package (Step 3 지시서 7항).
 * The query is then cleared so a later manual filter change does not fight a stale URL.
 */
watch(
  () => route.query.wbs,
  async (value) => {
    if (value === undefined) return
    const wbsItemId = Number(Array.isArray(value) ? value[0] : value)
    if (!Number.isFinite(wbsItemId)) return
    filters.wbsItemId = wbsItemId
    // 보관까지 포함해 보여 준다 — WBS에서 "보관 2"를 눌러 온 경우 빈 목록이 나오면 안 된다.
    filters.archive = 'ALL'
    await router.replace({ path: '/backlog' })
  },
  { immediate: true },
)

const filterActive = computed(() => isFiltered(filters))

const focusedPackage = computed(() =>
  filters.wbsItemId === null
    ? null
    : (workPackages.value.find((option) => option.id === filters.wbsItemId) ?? null),
)

/** Banner counts measure the whole backlog, not the filtered view (the RAID banner rule). */
const needsAttention = computed(() =>
  data.value.items.filter(
    (item) => !item.archived && (item.linkedToSummary || item.danglingLink),
  ),
)

const notSprintReady = computed(() =>
  data.value.items.filter((item) => item.aggregated && !item.archived && !item.readyForSprint),
)

async function handleSubmit(input: BacklogItemInput) {
  const projectId = selectedProjectId.value
  if (projectId === null) return

  if (editing.value) {
    const ok = await update(projectId, editing.value.id, input)
    // A rejected save keeps the form open with the draft, next to the message.
    if (ok) closeForm()
  } else {
    // Left open after a create: entering several items in one sitting is the common case, and the
    // new row appearing below is already the confirmation.
    await create(projectId, input)
  }
}

async function handleArchive(item: BacklogItem, archived: boolean) {
  const projectId = selectedProjectId.value
  if (projectId === null) return
  await setArchived(projectId, item.id, archived)
}

async function handleRemove(item: BacklogItem) {
  const projectId = selectedProjectId.value
  if (projectId === null) return
  if (editing.value?.id === item.id) closeForm()
  await remove(projectId, item.id)
}
</script>

<template>
  <section>
    <h1>Backlog</h1>

    <p v-if="projectsError" class="error">{{ projectsError }}</p>

    <p v-else-if="projects.length === 0" class="notice">
      먼저 프로젝트를 등록해야 Backlog를 작성할 수 있습니다.
      <RouterLink to="/projects">프로젝트 화면으로 이동</RouterLink>
    </p>

    <template v-else>
      <div class="toolbar">
        <label class="project-picker">
          프로젝트
          <select v-model="selectedProjectId">
            <option v-for="project in projects" :key="project.id" :value="project.id">
              {{ project.name }}
            </option>
          </select>
        </label>
        <span v-if="loading" class="loading">불러오는 중…</span>
      </div>

      <p v-if="error" class="error">{{ error }}</p>

      <p v-if="workPackages.length === 0 && !loading" class="notice">
        실행 방식을 지정할 Work Package가 없습니다. Backlog는 최하위 Work Package에 귀속되므로,
        먼저 WBS에서 항목을 만들어야 연결할 수 있습니다.
        <RouterLink to="/wbs">WBS 화면으로 이동</RouterLink>
      </p>

      <p v-if="data.unlinkedCount > 0" class="attention">
        <strong>미연결 {{ data.unlinkedCount }}건</strong> — 초안으로 둘 수는 있지만 Sprint에 넣기
        전에 Work Package에 귀속시켜야 합니다.
      </p>

      <p v-if="needsAttention.length > 0" class="attention">
        <strong>연결을 고쳐야 하는 항목 {{ needsAttention.length }}건</strong> —
        {{ needsAttention.map((item) => item.title).join(', ') }}.
        귀속 대상이 Summary로 바뀌었거나 사라졌습니다.
      </p>

      <p v-if="notSprintReady.length > 0" class="notice subtle">
        Sprint에 넣을 수 없는 Story·Bug가 {{ notSprintReady.length }}건 있습니다 (미연결 또는 연결 오류).
      </p>

      <div class="list-header">
        <div class="filters">
          <label>
            <span class="filter-label">Work Package</span>
            <select v-model="filters.wbsItemId">
              <option :value="null">전체</option>
              <option v-for="option in workPackages" :key="option.id" :value="option.id">
                {{ option.code }} {{ option.name }}
              </option>
            </select>
          </label>
          <label>
            <span class="filter-label">유형</span>
            <select v-model="filters.type">
              <option value="ALL">전체</option>
              <option v-for="type in BACKLOG_TYPE_ORDER" :key="type" :value="type">
                {{ BACKLOG_TYPE_LABELS[type] }}
              </option>
            </select>
          </label>
          <label>
            <span class="filter-label">상태</span>
            <select v-model="filters.status">
              <option value="ALL">전체</option>
              <option v-for="value in BACKLOG_STATUS_ORDER" :key="value" :value="value">
                {{ BACKLOG_STATUS_LABELS[value] }}
              </option>
            </select>
          </label>
          <label>
            <span class="filter-label">연결</span>
            <select v-model="filters.link">
              <option v-for="value in LINK_FILTER_ORDER" :key="value" :value="value">
                {{ LINK_FILTER_LABELS[value] }}
              </option>
            </select>
          </label>
          <label>
            <span class="filter-label">보관</span>
            <select v-model="filters.archive">
              <option v-for="value in ARCHIVE_FILTER_ORDER" :key="value" :value="value">
                {{ ARCHIVE_FILTER_LABELS[value] }}
              </option>
            </select>
          </label>
          <button v-if="filterActive" type="button" class="ghost" @click="resetFilters">
            필터 해제
          </button>
        </div>

        <button type="button" class="add" @click="openForm(null)">＋ 항목 추가</button>
      </div>

      <p v-if="focusedPackage" class="focused">
        <strong>{{ focusedPackage.code }} {{ focusedPackage.name }}</strong>에 귀속된 항목만 보고 있습니다.
      </p>

      <BacklogForm
        v-if="formOpen"
        :editing="editing"
        :members="members"
        :work-packages="workPackages"
        :parent-options="parentOptions"
        :error="error"
        @submit="handleSubmit"
        @cancel="closeForm"
      />

      <BacklogList
        :rows="rows"
        :filtered="filterActive"
        @edit="openForm"
        @archive="handleArchive"
        @remove="handleRemove"
      />
    </template>
  </section>
</template>

<style scoped>
h1 {
  font-size: 1.3rem;
  margin: 0 0 1rem;
}

.toolbar {
  display: flex;
  align-items: center;
  gap: 1rem;
  margin-bottom: 1rem;
}

.project-picker {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.85rem;
  color: var(--text-muted);
}

select {
  padding: 0.4rem 0.6rem;
  border: 1px solid var(--border-input);
  border-radius: 6px;
  font: inherit;
  font-size: 0.85rem;
}

.loading {
  font-size: 0.8rem;
  color: var(--text-faint);
}

.error {
  color: var(--danger);
  font-size: 0.9rem;
}

.notice {
  font-size: 0.9rem;
  color: var(--text-muted);
}

.notice.subtle {
  font-size: 0.82rem;
  color: var(--text-faint);
}

.attention {
  font-size: 0.85rem;
  color: var(--warn-badge-fg);
  background: var(--warn-weak);
  border-radius: 6px;
  padding: 0.55rem 0.7rem;
}

.list-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 1rem;
  flex-wrap: wrap;
  margin: 1rem 0 0.5rem;
}

.filters {
  display: flex;
  align-items: flex-end;
  gap: 0.6rem;
  flex-wrap: wrap;
}

.filters label {
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
}

.filter-label {
  font-size: 0.72rem;
  color: var(--text-dim);
}

.focused {
  font-size: 0.82rem;
  color: var(--text-muted);
  margin: 0 0 0.5rem;
}

button {
  padding: 0.4rem 0.8rem;
  border-radius: 6px;
  border: 1px solid var(--accent);
  background: var(--accent);
  color: var(--accent-fg);
  cursor: pointer;
  font: inherit;
  font-size: 0.82rem;
  white-space: nowrap;
}

button.ghost {
  background: transparent;
  color: var(--text-muted);
  border-color: var(--border-input);
}
</style>
