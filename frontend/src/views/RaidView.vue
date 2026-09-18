<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import type { RaidItem, RaidItemInput } from '../api/raidApi'
import RaidForm from '../features/raid/RaidForm.vue'
import RaidList from '../features/raid/RaidList.vue'
import { useRaid } from '../features/raid/useRaid'
import { useProjects } from '../features/projects/useProjects'
import { ensureSelection, selectedProjectId } from '../stores/projectSelection'
import { readOnly } from '../stores/commitView'

/** 5.2 표의 화면 전체 공통 문구 — 화면마다 문구를 지어내지 않는다. */
const READONLY_HINT = '커밋 시점 조회 중에는 변경할 수 없습니다'
import {
  RAID_TYPE_DESCRIPTIONS,
  RAID_TYPE_ENGLISH,
  RAID_TYPE_LABELS,
  RAID_TYPE_ORDER,
} from '../shared/raid'
import {
  filtersFromQuery,
  filtersToQuery,
  isFiltered,
  SORT_LABELS,
  SORT_ORDER,
  STATUS_FILTER_LABELS,
  STATUS_FILTER_ORDER,
  typeCounts,
} from '../features/raid/raidFilter'

const route = useRoute()
const router = useRouter()

const { projects, error: projectsError, ensureLoaded: ensureProjects } = useProjects()
const {
  data,
  members,
  wbsTasks,
  sprints,
  backlogItems,
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


/** The row the single form is editing, mirroring how the WBS screen drives its form. */
const editing = ref<RaidItem | null>(null)

/**
 * The form is collapsed by default. The register is what this screen is for — reading it is the
 * common act, and entering an item is the occasional one — so the input panel does not get to
 * push the table below the fold all day.
 */
const formOpen = ref(false)
function openForm(item: RaidItem | null) {
  if (readOnly.value) return
  editing.value = item
  formOpen.value = true
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
const SORT_OPTIONS = SORT_ORDER

/**
 * 필터·정렬·검색을 주소에 반영한다 — `DashboardView`의 `?tab=`과 같은 패턴(CLAUDE.md "URL에
 * 화면 상태" 요구). `filters`는 `useRaid()`의 모듈 스코프 상태라 프로젝트를 오가도 남는데,
 * 그 지속성은 그대로 두고 주소를 그 값의 거울로만 쓴다 — 그래서 쿼리가 없는 채로 이 화면에
 * 들어오면(평범한 메뉴 클릭) 필터를 기본값으로 되돌리지 않고, 갖고 있던 값을 그대로 주소에
 * 반영한다. 반대로 공유된 링크·뒤로가기로 쿼리가 있는 채로 들어오면 그 값을 필터에 적용한다.
 */
const RAID_QUERY_KEYS = ['type', 'status', 'sort', 'q'] as const

function applyQueryToFilters() {
  const hasFilterQuery = RAID_QUERY_KEYS.some((key) => route.query[key] !== undefined)
  if (!hasFilterQuery) return
  // 개별 속성에 대입해야 값이 실제로 같으면 Vue가 변경으로 보지 않는다 — 그래야 아래
  // filters→주소 watch가 이 대입 때문에 다시 돌아 무한 루프가 되는 것을 막을 수 있다.
  Object.assign(filters.value, filtersFromQuery(route.query))
}

function sameQuery(a: Record<string, string>, b: typeof route.query): boolean {
  const bKeys = RAID_QUERY_KEYS.filter((key) => b[key] !== undefined)
  const aKeys = Object.keys(a)
  return aKeys.length === bKeys.length && aKeys.every((key) => a[key] === b[key])
}

applyQueryToFilters()

watch(
  filters,
  (value) => {
    const query = filtersToQuery(value)
    if (sameQuery(query, route.query)) return
    router.replace({ path: '/raid', query })
  },
  { deep: true, immediate: true },
)

// 뒤로가기 등으로 주소가 바뀌면 필터에 반영한다. 우리가 방금 위 watch에서 쓴 주소라면
// applyQueryToFilters가 같은 값을 대입할 뿐이라 아무 것도 바뀌지 않는다.
watch(() => route.query, applyQueryToFilters)

/** 노출도 높음이면서 아직 종결되지 않은 항목 — 계획이 필요한 지점. */
const highExposure = computed(() =>
  data.value.items.filter((item) => item.exposureLevel === 'HIGH' && item.status !== 'CLOSED'),
)

const unowned = computed(() =>
  data.value.items.filter((item) => item.ownerMemberId === null && item.status !== 'CLOSED'),
)

/** RaidForm의 제출 버튼을 잠그는 데 쓴다 — 느린 네트워크에서 두 번 눌러 두 건이 생기는 것을 막는다. */
const submitting = ref(false)

async function handleSubmit(input: RaidItemInput) {
  if (readOnly.value) return
  const projectId = selectedProjectId.value
  if (projectId === null) return

  submitting.value = true
  try {
    if (editing.value) {
      const ok = await update(projectId, editing.value.id, input)
      // A rejected save keeps the form open with the draft, next to the error message.
      if (ok) closeForm()
    } else {
      // Left open after a create so several entries can be logged in one sitting; the new row
      // appearing in the table below is the confirmation.
      await create(projectId, input)
    }
  } finally {
    submitting.value = false
  }
}

async function handleRemove(item: RaidItem) {
  if (readOnly.value) return
  const projectId = selectedProjectId.value
  if (projectId === null) return
  if (editing.value?.id === item.id) closeForm()
  await remove(projectId, item.id)
}
</script>

<template>
  <section>
    <h1>
      RAID
      <span class="acronym">{{ RAID_TYPE_ORDER.map((type) => RAID_TYPE_ENGLISH[type]).join(' · ') }}</span>
    </h1>
    <p class="lede">프로젝트를 흔들 수 있는 것들을 한 곳에 기록하고 추적합니다</p>

    <!-- 네 글자 범례. 문구는 shared/raid.ts의 상수를 그대로 렌더한다 — 두 벌로 적으면 한쪽만
         고쳐진다. -->
    <p class="legend">
      <span v-for="type in RAID_TYPE_ORDER" :key="type" class="legend-item">
        <strong>{{ RAID_TYPE_LABELS[type] }}</strong> {{ RAID_TYPE_DESCRIPTIONS[type] }}
      </span>
      <br />
      노출도는 확률 × 영향으로 자동 계산합니다(저장하지 않습니다). 기한이 지난 항목은 표시되지만,
      종결된 항목은 초과로 보지 않습니다.
    </p>

    <p v-if="projectsError" class="error" role="alert">{{ projectsError }}</p>

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

        <span v-if="data.referenceDate" class="reference">기준일 {{ data.referenceDate }}</span>
      </div>

      <p v-if="error" class="error" role="alert">{{ error }}</p>

      <p v-if="summary.length > 0" class="summary" aria-live="polite">
        <span v-for="row in summary" :key="row.type" class="summary-item">
          {{ RAID_TYPE_LABELS[row.type] }} <strong>{{ row.open }}</strong
          ><span class="of">/{{ row.total }}</span>
        </span>
        <span class="summary-note">미종결 / 전체</span>
      </p>

      <p v-if="overdue.length > 0 || highExposure.length > 0" class="attention" aria-live="polite">
        <template v-if="overdue.length > 0">
          <strong>기한 초과 {{ overdue.length }}건</strong> —
          {{ overdue.map((item) => `${item.title} (${item.overdueDays}일)`).join(', ') }}.
        </template>
        <template v-if="highExposure.length > 0">
          <strong>노출도 높음 {{ highExposure.length }}건</strong> —
          {{ highExposure.map((item) => item.title).join(', ') }}.
        </template>
      </p>

      <p v-if="unowned.length > 0" class="unowned" aria-live="polite">
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
            :aria-pressed="filters.type === 'ALL'"
              @click="filters.type = 'ALL'"
            >전체 {{ data.items.length }}</button>
            <button
              v-for="row in counts"
              :key="row.type"
              type="button"
              class="chip"
              :class="{ active: filters.type === row.type }"
              :aria-pressed="filters.type === row.type"
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

          <input
            v-model="filters.query"
            type="search"
            aria-label="검색"
            placeholder="제목·설명·소유자 검색"
          />

          <button v-if="filterActive" type="button" class="reset" @click="resetFilters">
            필터 해제
          </button>
        </div>

        <button
          type="button"
          class="add"
          :disabled="readOnly"
          :title="readOnly ? READONLY_HINT : undefined"
          @click="openForm(null)"
        >＋ 항목 추가</button>
      </div>

      <RaidForm
        v-if="formOpen"
        :editing="editing"
        :members="members"
        :wbs-tasks="wbsTasks"
        :sprints="sprints"
        :backlog-items="backlogItems"
        :error="error"
        :submitting="submitting"
        @submit="handleSubmit"
        @cancel="closeForm"
      />

      <p v-if="filterActive && data.items.length > 0" class="filter-note" aria-live="polite">
        {{ data.items.length }}건 중 {{ visibleItems.length }}건 표시 중.
      </p>

      <p v-if="loading" class="loading" aria-live="polite">불러오는 중…</p>
      <RaidList
        v-else
        :items="visibleItems"
        :editing-id="editing?.id ?? null"
        :filtered="filterActive && data.items.length > 0"
        :read-only="readOnly"
        @edit="openForm($event)"
        @remove="handleRemove"
      />
    </template>
  </section>
</template>

<style scoped>
h1 {
  font-size: 1.4rem;
  margin-bottom: 0.25rem;
}

/* 제목 옆에 약자가 무엇의 머리글자인지 풀어 적는다. 제목과 경쟁하지 않도록 작고 옅게 둔다. */
.acronym {
  margin-left: 0.5rem;
  font-size: 0.8rem;
  font-weight: normal;
  color: var(--text-faint);
}

.lede {
  font-size: 0.85rem;
  font-weight: normal;
  color: var(--text-muted);
  margin: 0 0 0.75rem;
}

/* 범례는 화면의 주된 내용(로그)이 아니므로 접지 않되 시각적으로 물러나 있는다. RaciView와 같은
   모양·같은 클래스 이름을 쓴다. */
.legend {
  font-size: 0.78rem;
  line-height: 1.6;
  color: var(--text-faint);
  background: var(--surface-sunken);
  border-radius: 6px;
  padding: 0.5rem 0.7rem;
  margin: 0 0 1rem;
}

.legend-item + .legend-item::before {
  content: ' · ';
}

/* flex-wrap — 프로젝트 선택 select는 이름 길이에 따라 폭이 정해지므로 상한이 없다. 좁은
   화면이나 긴 프로젝트 이름에서 기준일 배지가 밀려 화면 밖으로 넘치지 않게 wrap을 둔다. */
.toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.5rem 1rem;
  margin-bottom: 1rem;
}

.project-picker {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.85rem;
  color: var(--text-muted);
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
/* flex-wrap — BacklogView의 같은 자리(.list-header)와 같은 이유: 필터가 많아 좁은 화면에서
   ＋ 항목 추가 버튼과 한 줄에 다 들어가지 않으면 다음 줄로 접힌다. */
.list-header {
  display: flex;
  flex-wrap: wrap;
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

/* border 두께·모양·radius·font·cursor는 전역 기본과 같아 지웠다 — border-color만 남겨 강조색을 얹는다. */
.add {
  flex: none;
  margin-left: auto;
  padding: 0.35rem 0.8rem;
  border-color: var(--accent);
  background: var(--accent);
  color: var(--accent-fg);
  font-size: 0.8rem;
  white-space: nowrap;
}

.add.ghost {
  background: var(--surface);
  border-color: var(--border-input);
  color: var(--text-muted);
}

.type-chips {
  display: flex;
  gap: 0.25rem;
  flex-wrap: wrap;
}

/* border·배경·font·cursor는 전역 기본과 같아 지웠다 — 알약 모양(radius)과 색만 남는다. */
.chip {
  padding: 0.25rem 0.6rem;
  border-radius: 999px;
  color: var(--text-muted);
  font-size: 0.78rem;
}

.chip.active {
  border-color: var(--accent);
  background: var(--accent);
  color: var(--accent-fg);
}

/* :disabled는 전역이 특이도로 이미 이기고 있던 죽은 선언이라(값도 같음) 지웠다. */

.filters select,
.filters input {
  padding: 0.3rem 0.5rem;
  font-size: 0.8rem;
}

.filters input {
  min-width: 12rem;
}

.reset {
  padding: 0.3rem 0.6rem;
  color: var(--text-muted);
  font-size: 0.78rem;
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
