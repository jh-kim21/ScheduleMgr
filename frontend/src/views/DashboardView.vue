<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import BaselineCard from '../features/dashboard/BaselineCard.vue'
import DashboardHeader from '../features/dashboard/DashboardHeader.vue'
import GapsCard from '../features/dashboard/GapsCard.vue'
import KpiStrip from '../features/dashboard/KpiStrip.vue'
import ProgressCard from '../features/dashboard/ProgressCard.vue'
import RaciCard from '../features/dashboard/RaciCard.vue'
import RiskCard from '../features/dashboard/RiskCard.vue'
import SprintCard from '../features/dashboard/SprintCard.vue'
import TimelineCard from '../features/dashboard/TimelineCard.vue'
import VelocityCard from '../features/dashboard/VelocityCard.vue'
import { useDashboard } from '../features/dashboard/useDashboard'
import ProgressPanel from '../features/progress/ProgressPanel.vue'
import { useProjects } from '../features/projects/useProjects'
import { ensureSelection, selectedProjectId } from '../stores/projectSelection'

/**
 * 배치와 탭만 남긴다 — 카드의 마크업과 서식은 features/dashboard 의 컴포넌트가 갖는다. 아홉 장의
 * 마크업이 한 파일에 모여 있으면 그리드를 바꿀 수가 없다.
 */
const { projects, error: projectsError, ensureLoaded: ensureProjects } = useProjects()
const { data, loading, error, ensureLoaded, invalidate, load } = useDashboard()

const route = useRoute()
const router = useRouter()

/**
 * 요약과 진척은 한 화면의 두 면이다 (설계서 §2.2의 메뉴 일곱 개를 그대로 두려고 합쳤다). 요약은
 * 읽기 전용 집계이고, 진척은 그 숫자의 근거를 입력하는 곳이다 — 가중치·체크포인트·기준선·스냅샷.
 * 탭이 쿼리에 있어야 다른 화면에서 "진척으로 가라"고 링크할 수 있다(/progress 가 이리로 온다).
 */
type Tab = 'summary' | 'progress'
const tab = ref<Tab>(route.query.tab === 'progress' ? 'progress' : 'summary')

function selectTab(next: Tab) {
  if (tab.value === next) return
  tab.value = next
  router.replace({ path: '/dashboard', query: next === 'progress' ? { tab: 'progress' } : {} })
}

// 주소로 직접 들어오거나 뒤로 가기를 했을 때 탭을 맞춘다.
watch(
  () => route.query.tab,
  (value) => {
    tab.value = value === 'progress' ? 'progress' : 'summary'
  },
)

/**
 * 진척 탭에서 기준선을 승인하거나 체크포인트를 바꾸면 요약의 숫자가 낡는다. 탭은 같은 컴포넌트
 * 안이라 마운트가 다시 일어나지 않으므로, 요약으로 돌아올 때 다시 확인한다. 캐시 키가 그대로면
 * ensureLoaded 는 아무것도 하지 않는다.
 */
watch(tab, (value) => {
  const id = selectedProjectId.value
  if (value === 'summary' && id !== null) ensureLoaded(id)
})

// The selection watcher is the single load path: `immediate` covers arriving with a project
// already chosen, and `ensureSelection` below covers the first ever visit by setting one.
watch(
  selectedProjectId,
  (id) => {
    if (id !== null) ensureLoaded(id)
  },
  { immediate: true },
)

onMounted(async () => {
  await ensureProjects()
  ensureSelection(projects.value.map((project) => project.id))
})

function refresh() {
  const id = selectedProjectId.value
  if (id === null) return
  invalidate()
  load(id)
}
</script>

<template>
  <section class="dashboard">
    <p v-if="projectsError" class="error">{{ projectsError }}</p>

    <p v-else-if="projects.length === 0" class="notice">
      먼저 프로젝트를 등록해야 Dashboard 를 볼 수 있습니다.
      <RouterLink to="/projects">프로젝트 화면으로 이동</RouterLink>
    </p>

    <template v-else>
      <DashboardHeader
        :projects="projects"
        :tab="tab"
        :reference-date="data?.referenceDate ?? null"
        :loading="loading"
        @update:tab="selectTab"
        @refresh="refresh"
      />

      <ProgressPanel v-if="tab === 'progress'" />

      <template v-else>
        <p v-if="error" class="error">{{ error }}</p>
        <p v-if="loading" class="loading">불러오는 중...</p>

        <template v-else-if="data">
          <p v-if="data.workPackages.total === 0" class="notice">
            WBS에 Work Package가 없습니다. 업무를 등록하면 진척과 일정이 여기에 모입니다.
            <RouterLink to="/wbs">WBS 화면으로 이동</RouterLink>
          </p>

          <KpiStrip :data="data" />

          <!-- 넓은 쪽(7)에 시간축·목록, 좁은 쪽(5)에 요약·분포. 카드가 아니라 여기가 폭을 준다. -->
          <div class="dash-grid">
            <ProgressCard class="span-7" :data="data" />
            <SprintCard class="span-5" :data="data" />
            <TimelineCard class="span-7" :data="data" />
            <RiskCard class="span-5" :data="data" />
            <BaselineCard class="span-7" :data="data" />
            <RaciCard class="span-5" :data="data" />
            <VelocityCard class="span-12" :data="data" />
            <GapsCard v-if="data.gaps.length > 0" class="span-12" :data="data" />
          </div>
        </template>
      </template>
    </template>
  </section>
</template>

<style scoped>
/*
 * 면 분리 — 페이지 바탕은 옅은 회색, 카드는 흰 표면. --bg 와 --surface 가 둘 다 흰색이라 카드가
 * 배경에서 떠오르지 않는다. 전역 --bg 를 내리는 것은 전 화면에 영향이 가는 별건이라, 우선
 * 대시보드 안에만 깐다(지시서 3-b). 음수 여백은 .app 의 좌우 패딩(1rem)과 .app-main 의 위아래
 * 패딩(1.5rem/3rem)을 정확히 상쇄해, 회색이 화면 끝까지 닿으면서도 본문 폭은 늘지 않는다.
 */
.dashboard {
  margin: -1.5rem -1rem -3rem;
  padding: 1.5rem 1rem 3rem;
  background: var(--dash-bg);
}

.error {
  color: var(--danger);
  margin-bottom: 0.75rem;
}

.notice {
  color: var(--text-dim);
  margin-bottom: 0.75rem;
}

.loading {
  margin-top: 1rem;
}

/* minmax(0, 1fr) 이 아니면 긴 업무명 하나가 열을 밀어 페이지에 가로 스크롤이 생긴다. */
.dash-grid {
  display: grid;
  grid-template-columns: repeat(12, minmax(0, 1fr));
  gap: var(--card-gap);
  align-items: start;
  margin-top: var(--card-gap);
}

.span-7 {
  grid-column: span 7;
}

.span-5 {
  grid-column: span 5;
}

.span-12 {
  grid-column: 1 / -1;
}

/* 1100~1400px: 비대칭을 접어 6 : 6. */
@media (max-width: 87.5rem) {
  .span-7,
  .span-5 {
    grid-column: span 6;
  }
}

/* 1100px 미만: 한 열. 나란히 두기엔 각 카드의 목록이 너무 좁아진다. */
@media (max-width: 68.75rem) {
  .span-7,
  .span-5 {
    grid-column: 1 / -1;
  }
}
</style>
