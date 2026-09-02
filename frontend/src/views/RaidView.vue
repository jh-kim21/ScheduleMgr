<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import type { RaidItem, RaidItemInput } from '../api/raidApi'
import ExportMenu from '../features/export/ExportMenu.vue'
import RaidForm from '../features/raid/RaidForm.vue'
import RaidList from '../features/raid/RaidList.vue'
import { useRaid } from '../features/raid/useRaid'
import { useProjects } from '../features/projects/useProjects'
import { ensureSelection, selectedProjectId } from '../stores/projectSelection'
import { RAID_TYPE_LABELS, RAID_TYPE_ORDER } from '../shared/raid'
import { raidCsv } from '../shared/exportRows'
import {
  isFiltered,
  SORT_LABELS,
  STATUS_FILTER_LABELS,
  STATUS_FILTER_ORDER,
  typeCounts,
  type RaidSort,
} from '../features/raid/raidFilter'

const { projects, error: projectsError, ensureLoaded: ensureProjects } = useProjects()
const {
  data,
  members,
  wbsTasks,
  loading,
  error,
  filters,
  visibleItems,
  ensureLoaded,
  create,
  update,
  remove,
  resetFilters,
} = useRaid()

/** 내보내기 파일명에 쓸 프로젝트 이름. */
const selectedProjectName = computed(
  () => projects.value.find((project) => project.id === selectedProjectId.value)?.name ?? 'project',
)


/** The row the single form is editing, mirroring how the WBS screen drives its form. */
const editing = ref<RaidItem | null>(null)

/**
 * The form is collapsed by default. The register is what this screen is for — reading it is the
 * common act, and entering an item is the occasional one — so the input panel does not get to
 * push the table below the fold all day.
 */
const formOpen = ref(false)
const formSlot = ref<HTMLElement | null>(null)

/** The form sits above the table, so opening it from a row far down needs a scroll to be seen. */
async function openForm(item: RaidItem | null) {
  editing.value = item
  formOpen.value = true
  await nextTick()
  formSlot.value?.scrollIntoView({ behavior: 'smooth', block: 'center' })
}

function closeForm() {
  formOpen.value = false
  editing.value = null
}

// The selection watcher is the single load path: `immediate` covers arriving with a project
// already chosen, and `ensureSelection` below covers the first ever visit by setting one.
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

/** Counts per type, open ones separated — a closed entry needs no attention. */
const summary = computed(() =>
  RAID_TYPE_ORDER.map((type) => {
    const items = data.value.items.filter((item) => item.type === type)
    return {
      type,
      total: items.length,
      open: items.filter((item) => item.status !== 'CLOSED').length,
    }
  }).filter((row) => row.total > 0),
)

/**
 * The banners below deliberately measure the whole register, not the filtered view: "기한 초과
 * 2건" is a fact about the project, and having it shrink because a filter is on would be
 * misleading. Only the table respects the filter.
 */
const overdue = computed(() => data.value.items.filter((item) => item.overdue))

const counts = computed(() => typeCounts(data.value.items))
const filterActive = computed(() => isFiltered(filters.value))
const SORT_OPTIONS = Object.keys(SORT_LABELS) as RaidSort[]

/** 노출도 높음이면서 아직 종결되지 않은 항목 — 계획이 필요한 지점. */
const highExposure = computed(() =>
  data.value.items.filter((item) => item.exposureLevel === 'HIGH' && item.status !== 'CLOSED'),
)

const unowned = computed(() =>
  data.value.items.filter((item) => item.ownerMemberId === null && item.status !== 'CLOSED'),
)

async function handleSubmit(input: RaidItemInput) {
  const projectId = selectedProjectId.value
  if (projectId === null) return

  if (editing.value) {
    const ok = await update(projectId, editing.value.id, input)
    // A rejected save keeps the form open with the draft, next to the error message.
    if (ok) closeForm()
  } else {
    // Left open after a create so several entries can be logged in one sitting; the new row
    // appearing in the table below is the confirmation.
    await create(projectId, input)
  }
}

async function handleRemove(item: RaidItem) {
  const projectId = selectedProjectId.value
  if (projectId === null) return
  if (editing.value?.id === item.id) closeForm()
  await remove(projectId, item.id)
}
</script>

<template>
  <section>
    <h1>RAID</h1>

    <p v-if="projectsError" class="error">{{ projectsError }}</p>

    <p v-else-if="projects.length === 0" class="notice">
      먼저 프로젝트를 등록해야 RAID 로그를 작성할 수 있습니다.
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

        <ExportMenu
          v-if="selectedProjectId !== null"
          :project-id="selectedProjectId"
          :project-name="selectedProjectName"
          screen="raid"
          :csv="() => raidCsv(visibleItems, data.referenceDate)"
        />

        <span v-if="data.referenceDate" class="reference">기준일 {{ data.referenceDate }}</span>
      </div>

      <p v-if="error" class="error">{{ error }}</p>

      <p v-if="summary.length > 0" class="summary">
        <span v-for="row in summary" :key="row.type" class="summary-item">
          {{ RAID_TYPE_LABELS[row.type] }} <strong>{{ row.open }}</strong
          ><span class="of">/{{ row.total }}</span>
        </span>
        <span class="summary-note">미종결 / 전체</span>
      </p>

      <p v-if="overdue.length > 0 || highExposure.length > 0" class="attention">
        <template v-if="overdue.length > 0">
          <strong>기한 초과 {{ overdue.length }}건</strong> —
          {{ overdue.map((item) => `${item.title} (${item.overdueDays}일)`).join(', ') }}.
        </template>
        <template v-if="highExposure.length > 0">
          <strong>노출도 높음 {{ highExposure.length }}건</strong> —
          {{ highExposure.map((item) => item.title).join(', ') }}.
        </template>
      </p>

      <p v-if="unowned.length > 0" class="unowned">
        소유자가 없는 미종결 항목이 {{ unowned.length }}건 있습니다 —
        {{ unowned.map((item) => item.title).join(', ') }}.
      </p>

      <div class="list-header">
        <div v-if="data.items.length > 0" class="filters">
          <span class="type-chips">
          <button
            type="button"
            class="chip"
            :class="{ active: filters.type === 'ALL' }"
              @click="filters.type = 'ALL'"
            >전체 {{ data.items.length }}</button>
            <button
              v-for="row in counts"
              :key="row.type"
              type="button"
              class="chip"
              :class="{ active: filters.type === row.type }"
              :disabled="row.count === 0"
              @click="filters.type = row.type"
            >{{ RAID_TYPE_LABELS[row.type] }} {{ row.count }}</button>
          </span>

          <select v-model="filters.status" aria-label="상태 필터">
            <option v-for="status in STATUS_FILTER_ORDER" :key="status" :value="status">
              {{ STATUS_FILTER_LABELS[status] }}
            </option>
          </select>

          <select v-model="filters.sort" aria-label="정렬">
            <option v-for="sort in SORT_OPTIONS" :key="sort" :value="sort">
              {{ SORT_LABELS[sort] }}
            </option>
          </select>

          <input v-model="filters.query" type="search" placeholder="제목·설명·소유자 검색" />

          <button v-if="filterActive" type="button" class="reset" @click="resetFilters">
            필터 해제
          </button>
        </div>

        <button
          v-if="!formOpen"
          type="button"
          class="add"
          @click="openForm(null)"
        >＋ 항목 추가</button>
        <button v-else type="button" class="add ghost" @click="closeForm">입력 닫기</button>
      </div>

      <div v-if="formOpen" ref="formSlot" class="form-slot">
        <RaidForm
          :editing="editing"
          :members="members"
          :wbs-tasks="wbsTasks"
          @submit="handleSubmit"
          @cancel="closeForm"
        />
      </div>

      <p v-if="filterActive && data.items.length > 0" class="filter-note">
        {{ data.items.length }}건 중 {{ visibleItems.length }}건 표시 중.
      </p>

      <p v-if="loading" class="loading">불러오는 중...</p>
      <RaidList
        v-else
        :items="visibleItems"
        :editing-id="editing?.id ?? null"
        :filtered="filterActive && data.items.length > 0"
        @edit="openForm($event)"
        @remove="handleRemove"
      />
    </template>
  </section>
</template>

<style scoped>
h1 {
  font-size: 1.4rem;
  margin-bottom: 1rem;
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

.project-picker select {
  padding: 0.4rem 0.6rem;
  border: 1px solid var(--border-input);
  border-radius: 6px;
  font: inherit;
}

.toolbar .export {
  margin-left: auto;
}

.reference {
  font-size: 0.78rem;
  color: var(--text-faint);
}

.error {
  color: var(--danger);
  margin-bottom: 0.75rem;
}

.summary {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 0.85rem;
  margin-bottom: 0.75rem;
  font-size: 0.82rem;
  color: var(--text-muted);
}

.summary-item strong {
  color: var(--text-h);
}

.summary .of {
  color: var(--text-faint);
}

.summary-note {
  margin-left: auto;
  font-size: 0.75rem;
  color: var(--text-faint);
}

/* 기한 초과·노출도 높음은 간트의 지연 배너와 같은 경고 계열로 둔다. */
.attention {
  font-size: 0.85rem;
  color: var(--warn-strong);
  background: var(--warn-weak);
  border-left: 3px solid var(--warn);
  border-radius: 6px;
  padding: 0.5rem 0.7rem;
  margin-bottom: 0.75rem;
  line-height: 1.5;
}

.unowned {
  font-size: 0.82rem;
  color: var(--text-dim);
  margin-bottom: 0.75rem;
}

/* 목록의 머리말: 왼쪽은 필터, 오른쪽은 입력 패널 토글. */
.list-header {
  display: flex;
  align-items: flex-start;
  gap: 0.75rem;
  margin: 0.25rem 0 0.75rem;
}

.filters {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 0.5rem;
  flex: 1;
}

.add {
  flex: none;
  margin-left: auto;
  padding: 0.35rem 0.8rem;
  border: 1px solid var(--accent);
  border-radius: 6px;
  background: var(--accent);
  color: var(--accent-fg);
  font: inherit;
  font-size: 0.8rem;
  cursor: pointer;
  white-space: nowrap;
}

.add.ghost {
  background: var(--surface);
  border-color: var(--border-input);
  color: var(--text-muted);
}

.form-slot {
  margin-bottom: 1rem;
}

.type-chips {
  display: flex;
  gap: 0.25rem;
  flex-wrap: wrap;
}

.chip {
  padding: 0.25rem 0.6rem;
  border: 1px solid var(--border-input);
  border-radius: 999px;
  background: var(--surface);
  color: var(--text-muted);
  font: inherit;
  font-size: 0.78rem;
  cursor: pointer;
}

.chip.active {
  border-color: var(--accent);
  background: var(--accent);
  color: var(--accent-fg);
}

.chip:disabled {
  background: var(--disabled-bg);
  border-color: var(--disabled-border);
  color: var(--disabled-fg);
  cursor: not-allowed;
}

.filters select,
.filters input {
  padding: 0.3rem 0.5rem;
  border: 1px solid var(--border-input);
  border-radius: 6px;
  font: inherit;
  font-size: 0.8rem;
}

.filters input {
  min-width: 12rem;
}

.reset {
  padding: 0.3rem 0.6rem;
  border: 1px solid var(--border-input);
  border-radius: 6px;
  background: var(--surface);
  color: var(--text-muted);
  font: inherit;
  font-size: 0.78rem;
  cursor: pointer;
}

.filter-note {
  font-size: 0.78rem;
  color: var(--text-faint);
  margin-bottom: 0.5rem;
}

.loading {
  margin-top: 1rem;
}

.notice {
  color: var(--text-dim);
}


</style>
