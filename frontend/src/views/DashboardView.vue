<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import type { DataGap, RaidRef, TaskRef } from '../api/dashboardApi'
import { useDashboard } from '../features/dashboard/useDashboard'
import ProgressPanel from '../features/progress/ProgressPanel.vue'
import { useProjects } from '../features/projects/useProjects'
import { ensureSelection, selectedProjectId } from '../stores/projectSelection'
import { EXECUTION_MODE_LABELS } from '../shared/executionMode'
import { PROGRESS_BASIS_LABELS, progressText, roundPoints, varianceTone } from '../shared/progress'
import { RAID_TYPE_LABELS } from '../shared/raid'

const { projects, error: projectsError, ensureLoaded: ensureProjects } = useProjects()
const { data, loading, error, ensureLoaded, invalidate, load } = useDashboard()

const route = useRoute()
const router = useRouter()

/**
 * 요약과 진척은 한 화면의 두 면이다 (설계서 §2.2의 메뉴 일곱 개를 그대로 두려고 합쳤다).
 *
 * <p>요약은 읽기 전용 집계이고, 진척은 그 숫자의 근거를 입력하는 곳이다 — 가중치, 승인
 * 체크포인트, 기준선, 스냅샷. 두 탭이 같은 진척 API 를 쓰므로 변경 경로가 늘지 않는다.
 *
 * <p>탭을 쿼리에 두는 이유: 다른 화면에서 "진척으로 가라"고 링크할 수 있어야 한다
 * (예전 /progress 라우트가 이리로 리다이렉트된다).
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
 * 진척 탭에서 기준선을 승인하거나 체크포인트를 바꾸면 요약의 숫자가 낡는다. 화면이 바뀌지 않아
 * (탭은 같은 컴포넌트 안이다) 마운트 시 로드만으로는 갱신되지 않으므로, 요약으로 돌아올 때 다시
 * 확인한다. 캐시 키가 그대로면 ensureLoaded 는 아무것도 하지 않는다.
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

/**
 * 편차는 같은 범위에서만 뺀다 — 서버가 기준선에 든 Work Package만으로 두 숫자를 냈다. 기준선이
 * 없으면 계획이 미산정이므로 편차도 말하지 않는다 (지시서 6항).
 */
const variance = computed(() => {
  const progress = data.value?.progress
  if (!progress || progress.variancePoints === null) return null
  return progress.variancePoints
})

const varianceLabel = computed(() => {
  const points = variance.value
  if (points === null) return null
  const rounded = roundPoints(points)
  if (rounded === 0) return '계획과 같음'
  return rounded > 0 ? `계획보다 ${rounded}%p 앞섬` : `계획보다 ${-rounded}%p 뒤짐`
})

/**
 * 색을 고르는 것은 화면의 몫이지만 방향 판정은 shared/progress.ts 한 곳에서만 한다
 * (`varianceTone`). +는 초록, -는 빨강, 계획과 같음·미산정은 지금 색 그대로 둔다.
 */
const varianceToneClass = computed(() => {
  const tone = varianceTone(variance.value)
  return tone === 'ahead' ? 'variance-ahead' : tone === 'behind' ? 'variance-behind' : null
})

/** 같은 화면의 진척 탭. 카드에서 근거를 고치러 갈 때 쓴다. */
const PROGRESS_TAB = { path: '/dashboard', query: { tab: 'progress' } }

/** 데이터 누락이 향하는 화면. kind마다 원인이 있는 곳이 다르다. */
function gapRoute(gap: DataGap) {
  if (gap.kind.startsWith('BACKLOG')) return { path: '/backlog' }
  if (gap.kind === 'NOT_ESTIMABLE' || gap.kind === 'WEIGHT_MISSING') return PROGRESS_TAB
  return { path: '/wbs', query: { focus: gap.wbsItemIds[0] } }
}

function taskRoute(task: TaskRef) {
  return { path: '/wbs', query: { focus: task.wbsItemId } }
}

function raidLabel(entry: RaidRef) {
  const owner = entry.ownerName ?? '소유자 미지정'
  return entry.detail ? `${entry.title} · ${entry.detail} · ${owner}` : `${entry.title} · ${owner}`
}

/**
 * 카드당 5건만 싣는 목록에 총건수가 더 있으면 그 사실을 적는다 — 그러지 않으면 6건째부터는
 * 조용히 사라진 것처럼 보인다. 전체는 상세 링크(RAID 화면)에서 본다.
 */
function overflowSuffix(total: number, shown: number): string {
  return total > shown ? ` (${shown}건 표시 · 총 ${total}건)` : ''
}

/** 종료된 Sprint만 추세다. 진행 중인 것은 아직 움직이므로 옆에 따로 둔다. */
const closedVelocity = computed(() =>
  (data.value?.velocity ?? []).filter((sprint) => !sprint.inProgress),
)
const runningVelocity = computed(() =>
  (data.value?.velocity ?? []).find((sprint) => sprint.inProgress) ?? null,
)
const peakPoints = computed(() =>
  Math.max(1, ...closedVelocity.value.map((sprint) => sprint.donePoints)),
)

const scopeChanged = computed(() => {
  const scope = data.value?.scope
  if (!scope?.hasBaseline) return false
  return scope.added.length > 0 || scope.removed.length > 0 || scope.weightChanged.length > 0
})
</script>

<template>
  <section>
    <h1>Dashboard</h1>

    <p v-if="projectsError" class="error">{{ projectsError }}</p>

    <p v-else-if="projects.length === 0" class="notice">
      먼저 프로젝트를 등록해야 Dashboard 를 볼 수 있습니다.
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

        <span v-if="data" class="reference">기준일 {{ data.referenceDate }}</span>
        <button
          v-if="tab === 'summary'"
          type="button"
          class="refresh"
          :disabled="loading"
          @click="refresh"
        >새로고침</button>
      </div>

      <div class="tabs" role="tablist">
        <button
          type="button"
          role="tab"
          :aria-selected="tab === 'summary'"
          :class="{ active: tab === 'summary' }"
          @click="selectTab('summary')"
        >요약</button>
        <button
          type="button"
          role="tab"
          :aria-selected="tab === 'progress'"
          :class="{ active: tab === 'progress' }"
          @click="selectTab('progress')"
        >진척</button>
      </div>

      <ProgressPanel v-if="tab === 'progress'" />

      <template v-else>
      <p v-if="error" class="error">{{ error }}</p>
      <p v-if="loading" class="loading">불러오는 중...</p>

      <template v-else-if="data">
        <p v-if="data.workPackages.total === 0" class="notice">
          WBS에 Work Package가 없습니다. 업무를 등록하면 진척과 일정이 여기에 모입니다.
          <RouterLink to="/wbs">WBS 화면으로 이동</RouterLink>
        </p>

        <!--
          KPI 타일 줄 — 화면을 열자마자 봐야 할 숫자 네 개만 큼직하게. "이 프로젝트 지금
          괜찮은가"에 바로 답하는 값(진척·지연·실행·위험)만 여기 온다. 그 값의 근거·목록·부가
          정보는 전부 아래 .cards 의 상세 카드에 그대로 남아 있다 — 숫자를 크게 보여주는 것과
          숫자의 출처를 보여주는 것은 다른 카드의 몫이라 나눴다. 새 카드를 추가할 때 "한눈에
          볼 지표"면 여기, "근거·목록"이면 .cards 로 보낸다.
        -->
        <div class="kpis">
          <!-- 진척: 큰 숫자는 실제 percent, 작은 줄은 근거(basis)와 계획 대비 편차. -->
          <article class="card tile" :class="{ 'tile-warn': data.progress.incomplete }">
            <header>
              <h2>진척</h2>
              <span v-if="data.progress.incomplete" class="status-badge" aria-label="확인 필요">
                ! 확인 필요
              </span>
            </header>
            <p class="tile-value" :class="{ unset: data.progress.actualPercent === null }">
              {{ progressText(data.progress.actualPercent) }}
            </p>
            <p class="tile-sub">
              <span v-if="data.progress.basis">{{ PROGRESS_BASIS_LABELS[data.progress.basis] }}</span>
              <span v-if="varianceLabel" :class="varianceToneClass">{{ varianceLabel }}</span>
            </p>
          </article>

          <!-- 일정: 지연 건수가 핵심. 지연 위험 건수와 계획 기간을 작게 덧붙인다. -->
          <article class="card tile" :class="{ 'tile-warn': data.schedule.delayedCount > 0 }">
            <header>
              <h2>일정</h2>
              <span
                v-if="data.schedule.delayedCount > 0"
                class="status-badge"
                aria-label="확인 필요"
              >! 확인 필요</span>
            </header>
            <p class="tile-value">{{ data.schedule.delayedCount }}</p>
            <p class="tile-sub">
              <span>지연 위험 {{ data.schedule.atRiskCount }}건</span>
              <span v-if="data.project.planStart">
                {{ data.project.planStart }} ~ {{ data.project.planEnd }}
              </span>
              <span v-else>계획 기간 미입력</span>
            </p>
          </article>

          <!-- 실행: 진행 중 Sprint 의 완료/계획 건수. 없으면 숫자가 아니라 상태 문구다. -->
          <article class="card tile">
            <header><h2>실행</h2></header>
            <template v-if="data.execution.activeSprint">
              <p class="tile-value">
                {{ data.execution.activeSprint.doneItems }}/{{
                  data.execution.activeSprint.plannedItems
                }}
              </p>
              <p class="tile-sub">
                <span :title="data.execution.activeSprint.name">
                  {{ data.execution.activeSprint.name }}
                </span>
              </p>
            </template>
            <p v-else class="tile-value unset">실행 중 없음</p>
          </article>

          <!--
            위험·이슈: 큰 숫자는 highExposureCount·overdueCount — 목록(highExposure/overdue)이
            5건에서 잘려도 총건수는 잘리지 않는다(서버가 자르기 전 스트림에서 센다). 목록 길이를
            대신 쓰면 6건째부터 헤드라인이 거짓말을 한다.
          -->
          <article
            class="card tile"
            :class="{
              'tile-warn': data.control.highExposureCount > 0 || data.control.overdueCount > 0,
            }"
          >
            <header>
              <h2>위험·이슈</h2>
              <span
                v-if="data.control.highExposureCount > 0 || data.control.overdueCount > 0"
                class="status-badge"
                aria-label="확인 필요"
              >! 확인 필요</span>
            </header>
            <p class="tile-value">{{ data.control.highExposureCount }}</p>
            <p class="tile-sub">
              <span>기한 초과 {{ data.control.overdueCount }}건</span>
            </p>
          </article>
        </div>

        <div class="cards">
          <!-- 속도 -->
          <article class="card span-3">
            <header>
              <h2>완료 Story Point 추세</h2>
              <RouterLink to="/sprint" class="more">Sprint</RouterLink>
            </header>

            <template v-if="closedVelocity.length > 0">
              <ul class="trend">
                <li v-for="sprint in closedVelocity" :key="sprint.sprintId">
                  <span class="trend-bar-slot">
                    <span
                      class="trend-bar"
                      :style="{ height: `${(sprint.donePoints / peakPoints) * 100}%` }"
                    ></span>
                  </span>
                  <span class="trend-points">{{ sprint.donePoints }}</span>
                  <span class="trend-name" :title="sprint.name">{{ sprint.name }}</span>
                </li>
              </ul>
              <p class="muted">
                종료된 Sprint의 실적입니다. 이월된 항목은 완료한 Sprint 쪽에만 셉니다.
              </p>
            </template>
            <p v-else class="muted">종료된 Sprint가 아직 없습니다.</p>

            <p v-if="runningVelocity" class="running">
              진행 중 {{ runningVelocity.name }} — 현재 {{ runningVelocity.donePoints }}포인트.
              아직 움직이는 숫자라 추세에 넣지 않았습니다.
            </p>
          </article>

          <!-- 기준 일정 -->
          <article class="card">
            <header>
              <h2>기준 일정</h2>
              <RouterLink :to="PROGRESS_TAB" class="more">기준선</RouterLink>
            </header>

            <template v-if="data.baseline">
              <p class="headline small">
                v{{ data.baseline.version }}
                <span class="basis">{{ data.baseline.itemCount }}개 항목</span>
              </p>
              <p class="muted">
                {{ data.baseline.approvedAt.slice(0, 10) }}
                {{ data.baseline.approvedBy ? `· ${data.baseline.approvedBy}` : '' }}
              </p>

              <p v-if="data.schedule.baselineExceeded.length > 0" class="warn-note">
                기준 종료일을 넘길 것으로 보이는 업무가
                <strong>{{ data.schedule.baselineExceeded.length }}건</strong> 있습니다.
              </p>
              <ul class="refs">
                <li v-for="task in data.schedule.baselineExceeded" :key="task.wbsItemId">
                  <RouterLink :to="taskRoute(task)">{{ task.code }} {{ task.name }}</RouterLink>
                  <span class="muted">{{ task.detail }}</span>
                </li>
              </ul>

              <p v-if="scopeChanged" class="warn-note">
                기준선 이후 범위가 바뀌었습니다 — 추가 {{ data.scope.added.length }},
                제외 {{ data.scope.removed.length }},
                가중치 변경 {{ data.scope.weightChanged.length }}. 편차는 기준선에 든 항목만으로
                계산했습니다.
              </p>
            </template>

            <p v-else class="muted">
              승인된 기준선이 없습니다. 현재 계획을 기준선으로 보지 않으므로 계획 대비 편차와 기준
              초과는 표시하지 않습니다.
            </p>
          </article>

          <!--
            진척 상세 — 위 타일이 headline(percent·basis·편차)만 보여주고 남긴 나머지: 계획
            진척과 같은 범위 실제, Work Package 수와 산정 전 개수, 실행 방식 분포, incomplete
            경고문.
          -->
          <article class="card span-2">
            <header>
              <h2>진척 상세</h2>
              <RouterLink :to="PROGRESS_TAB" class="more">상세</RouterLink>
            </header>

            <dl class="rows">
              <dt>계획 진척</dt>
              <dd v-if="data.progress.plannedPercent !== null">
                {{ progressText(data.progress.plannedPercent) }}
                <span class="muted">(기준선 v{{ data.baseline?.version }} 범위)</span>
              </dd>
              <dd v-else class="muted">미산정 — 승인된 기준선이 없습니다.</dd>

              <template v-if="varianceLabel">
                <dt>편차</dt>
                <dd :class="varianceToneClass">
                  {{ varianceLabel }}
                  <span class="muted">
                    (같은 범위 실제 {{ progressText(data.progress.comparablePercent) }})
                  </span>
                </dd>
              </template>

              <dt>Work Package</dt>
              <dd>
                {{ data.workPackages.total }}개
                <span v-if="data.workPackages.notEstimableCount > 0" class="muted">
                  · 산정 전 {{ data.workPackages.notEstimableCount }}개
                </span>
              </dd>
            </dl>

            <p v-if="data.progress.incomplete" class="warn-note">
              일부 하위가 산정 전이거나 가중치가 없어 이 숫자에 빠져 있습니다. 0%로 대신하지
              않았습니다.
            </p>

            <ul class="modes">
              <li v-for="row in data.workPackages.byExecutionMode" :key="row.mode ?? 'NONE'">
                {{ row.mode ? EXECUTION_MODE_LABELS[row.mode] : '미지정' }}
                <strong>{{ row.count }}</strong>
              </li>
            </ul>
          </article>

          <!-- 책임과 통제 -->
          <article class="card">
            <header>
              <h2>책임</h2>
              <RouterLink to="/raci" class="more">RACI</RouterLink>
            </header>

            <p v-if="data.control.raciIssueCount === 0" class="ok-note">
              책임 공백이 없습니다.
            </p>
            <dl v-else class="rows">
              <dt>책임자 없음</dt>
              <dd>{{ data.control.missingAccountableCount }}건</dd>
              <dt>담당자 없음</dt>
              <dd>{{ data.control.missingResponsibleCount }}건</dd>
              <dt>책임자 중복</dt>
              <dd>{{ data.control.multipleAccountableCount }}건</dd>
            </dl>
            <p class="muted">상속을 반영한 판정입니다.</p>
          </article>

          <!-- 인수 -->
          <article v-if="data.schedule.acceptancePending.length > 0" class="card">
            <header><h2>인수 대기</h2></header>
            <p class="muted">실행은 끝났지만 승인이 남았습니다. 완료로 세지 않습니다.</p>
            <ul class="refs">
              <li v-for="task in data.schedule.acceptancePending" :key="task.wbsItemId">
                <RouterLink :to="taskRoute(task)">{{ task.code }} {{ task.name }}</RouterLink>
              </li>
            </ul>
          </article>

          <!-- 데이터 누락 -->
          <article v-if="data.gaps.length > 0" class="card span-2">
            <header>
              <h2>데이터 누락</h2>
              <span class="status-badge" aria-label="확인 필요">! 확인 필요</span>
            </header>
            <p class="muted">
              아래 항목 때문에 위 숫자가 답하지 못하는 부분이 있습니다. 임의로 채우지 않았습니다.
            </p>
            <ul class="gaps">
              <li v-for="gap in data.gaps" :key="gap.kind">
                <RouterLink :to="gapRoute(gap)">{{ gap.label }}</RouterLink>
                <strong>{{ gap.count }}건</strong>
              </li>
            </ul>
          </article>

          <!-- 일정 상세 — 타일의 지연 건수가 남긴 나머지: 예상 종료, 임계 경로 건수, 지연 목록. -->
          <article class="card span-2">
            <header>
              <h2>일정 상세</h2>
              <RouterLink to="/gantt" class="more">간트</RouterLink>
            </header>

            <dl class="rows">
              <dt>예상 종료</dt>
              <dd v-if="data.project.forecastEnd">{{ data.project.forecastEnd }}</dd>
              <dd v-else class="muted">-</dd>
            </dl>

            <p class="counts">
              <span :class="{ bad: data.schedule.delayedCount > 0 }">
                지연 <strong>{{ data.schedule.delayedCount }}</strong>
              </span>
              <span :class="{ warn: data.schedule.atRiskCount > 0 }">
                지연 위험 <strong>{{ data.schedule.atRiskCount }}</strong>
              </span>
              <span>임계 <strong>{{ data.schedule.criticalPathCount }}</strong></span>
            </p>

            <ul v-if="data.schedule.delayed.length > 0" class="refs">
              <li v-for="task in data.schedule.delayed" :key="task.wbsItemId">
                <RouterLink :to="taskRoute(task)">{{ task.code }} {{ task.name }}</RouterLink>
                <span class="muted">{{ task.detail }}</span>
              </li>
            </ul>
            <p v-else-if="data.workPackages.total > 0" class="ok-note">
              기준일 현재 지연된 업무가 없습니다.
            </p>
          </article>

          <!--
            실행 상세 — 타일의 완료/계획 건수가 남긴 나머지: 기간·목표, 포인트, 차단 건수와
            목록, 미연결 Backlog 경고.
          -->
          <article class="card span-2">
            <header>
              <h2>실행 상세</h2>
              <RouterLink to="/sprint" class="more">Sprint</RouterLink>
            </header>

            <template v-if="data.execution.activeSprint">
              <p class="headline small">
                {{ data.execution.activeSprint.name }}
                <span class="basis">
                  {{ data.execution.activeSprint.startDate }} ~
                  {{ data.execution.activeSprint.endDate }}
                </span>
              </p>
              <p v-if="data.execution.activeSprint.goal" class="goal">
                {{ data.execution.activeSprint.goal }}
              </p>

              <p class="counts">
                <span>
                  완료
                  <strong>
                    {{ data.execution.activeSprint.doneItems }}/{{
                      data.execution.activeSprint.plannedItems
                    }}
                  </strong>
                </span>
                <span>
                  포인트
                  <strong>
                    {{ data.execution.activeSprint.donePoints }}/{{
                      data.execution.activeSprint.plannedPoints
                    }}
                  </strong>
                </span>
                <span :class="{ bad: data.execution.activeSprint.blocked.length > 0 }">
                  차단 <strong>{{ data.execution.activeSprint.blocked.length }}</strong>
                </span>
              </p>

              <ul v-if="data.execution.activeSprint.blocked.length > 0" class="blocked">
                <li v-for="item in data.execution.activeSprint.blocked" :key="item.backlogItemId">
                  <span class="blocked-title">{{ item.title }}</span>
                  <span class="muted">
                    {{ item.reason ?? '사유 없음' }} · {{ item.assigneeName ?? '담당 미지정' }}
                  </span>
                  <ul v-if="item.raid.length > 0" class="refs">
                    <li v-for="entry in item.raid" :key="entry.raidItemId">
                      <RouterLink to="/raid">
                        {{ RAID_TYPE_LABELS[entry.type] }} {{ entry.title }}
                      </RouterLink>
                      <span class="muted">{{ entry.ownerName ?? '소유자 미지정' }}</span>
                    </li>
                  </ul>
                </li>
              </ul>
            </template>

            <p v-else class="muted">진행 중인 Sprint가 없습니다.</p>

            <p v-if="data.execution.backlogUnlinkedCount > 0" class="warn-note">
              Work Package에 연결되지 않은 Backlog 항목이
              {{ data.execution.backlogUnlinkedCount }}건 있습니다.
              <RouterLink to="/backlog">Backlog에서 확인</RouterLink>
            </p>
          </article>

          <!-- 위험·이슈 상세 — 타일의 노출도 높음 건수가 남긴 나머지: 기한 초과·노출도·열린 이슈 목록. -->
          <article class="card span-2">
            <header>
              <h2>위험·이슈 상세</h2>
              <RouterLink to="/raid" class="more">RAID</RouterLink>
            </header>

            <template
              v-if="
                data.control.openIssueCount > 0 ||
                data.control.highExposureCount > 0 ||
                data.control.overdueCount > 0
              "
            >
              <template v-if="data.control.overdueCount > 0">
                <h3>
                  기한 초과{{ overflowSuffix(data.control.overdueCount, data.control.overdue.length) }}
                </h3>
                <ul class="refs">
                  <li v-for="entry in data.control.overdue" :key="entry.raidItemId">
                    <RouterLink to="/raid">{{ raidLabel(entry) }}</RouterLink>
                  </li>
                </ul>
              </template>

              <template v-if="data.control.highExposureCount > 0">
                <h3>
                  노출도 높음{{
                    overflowSuffix(data.control.highExposureCount, data.control.highExposure.length)
                  }}
                </h3>
                <ul class="refs">
                  <li v-for="entry in data.control.highExposure" :key="entry.raidItemId">
                    <RouterLink to="/raid">{{ raidLabel(entry) }}</RouterLink>
                  </li>
                </ul>
              </template>

              <template v-if="data.control.openIssueCount > 0">
                <h3>
                  열린 이슈{{ overflowSuffix(data.control.openIssueCount, data.control.openIssues.length) }}
                </h3>
                <ul class="refs">
                  <li v-for="entry in data.control.openIssues" :key="entry.raidItemId">
                    <RouterLink to="/raid">{{ raidLabel(entry) }}</RouterLink>
                  </li>
                </ul>
              </template>
            </template>
            <p v-else class="ok-note">열린 위험·이슈가 없습니다.</p>
          </article>
        </div>
      </template>
      </template>
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

.reference {
  font-size: 0.78rem;
  color: var(--text-faint);
}

.refresh {
  margin-left: auto;
  padding: 0.35rem 0.8rem;
  border: 1px solid var(--border-input);
  border-radius: 6px;
  background: var(--surface);
  color: var(--text-muted);
  font: inherit;
  font-size: 0.8rem;
  cursor: pointer;
}

.error {
  color: var(--danger);
  margin-bottom: 0.75rem;
}

.notice {
  color: var(--text-dim);
  margin-bottom: 0.75rem;
}

/* 탭은 링크가 아니라 같은 화면의 두 면이라 버튼으로 둔다. */
.tabs {
  display: flex;
  gap: 0.25rem;
  margin-bottom: 1rem;
  border-bottom: 1px solid var(--border-soft);
}

.tabs button {
  padding: 0.4rem 0.9rem;
  border: none;
  border-bottom: 2px solid transparent;
  background: none;
  color: var(--text-muted);
  font: inherit;
  font-size: 0.85rem;
  cursor: pointer;
}

.tabs button.active {
  color: var(--accent);
  border-bottom-color: var(--accent);
  font-weight: 600;
}

/*
 * KPI 타일 줄. 넓은 화면에서 네 칸 고정 — auto-fit 을 쓰면 타일 개수가 늘 때마다 폭이
 * 들쭉날쭉해진다. align-items: stretch 로 타일 높이를 맞춘다(지시서 요구사항) — 아래 .cards
 * 는 카드마다 내용 길이가 크게 달라 stretch 를 쓰면 짧은 카드에 빈 공간만 늘어나므로 거기는
 * 그대로 start 를 쓴다.
 */
.kpis {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 0.9rem;
  align-items: stretch;
  margin-bottom: 0.9rem;
}

/*
 * 상세 카드 그리드. 넓은 화면은 네 칸이고, 카드마다 span-2/span-3 로 폭을 정한다(값을 지정하지
 * 않으면 한 칸). grid-auto-flow: dense 는 "인수 대기"·"데이터 누락"처럼 조건부로 사라지는
 * 카드가 있을 때 뒤 카드가 빈 칸을 채우고 올라오게 한다 — 없으면 그 자리가 빈 채로 다음 줄로
 * 밀린다.
 */
.cards {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  grid-auto-flow: dense;
  gap: 0.9rem;
  align-items: start;
}

.card {
  border: 1px solid var(--border);
  border-radius: 8px;
  background: var(--surface);
  padding: 0.85rem 1rem 1rem;
}

.card.span-2 {
  grid-column: span 2;
}

.card.span-3 {
  grid-column: span 3;
}

@media (max-width: 72rem) {
  .kpis {
    grid-template-columns: repeat(2, 1fr);
  }

  .cards {
    grid-template-columns: repeat(2, 1fr);
  }

  .card.span-3 {
    grid-column: span 2;
  }
}

@media (max-width: 48rem) {
  .kpis {
    grid-template-columns: 1fr;
  }

  .cards {
    grid-template-columns: 1fr;
  }

  .card.span-2,
  .card.span-3 {
    grid-column: span 1;
  }
}

/* KPI 타일. .card 의 테두리·배경은 그대로 물려받고, 안쪽 여백과 큰 숫자만 얹는다. */
.card.tile {
  display: flex;
  flex-direction: column;
  padding: 1rem 1.1rem 1.1rem;
}

.card.tile header {
  margin-bottom: 0.4rem;
}

/* 색은 배지 글자를 보조할 뿐이다 — 테두리만으로 확인 필요를 구분하게 하지 않는다. */
.card.tile.tile-warn {
  border-left: 3px solid var(--warn);
}

/* 큰 숫자. tabular-nums 로 자릿수가 바뀌어도 폭이 흔들리지 않는다. */
.tile-value {
  font-size: 2.2rem;
  font-weight: 700;
  line-height: 1.15;
  color: var(--text-h);
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

/* "산정 전"·"실행 중 없음"처럼 숫자가 아닌 상태는 크게 쓰지 않는다 — 0 이 아니라는 뜻이다. */
.tile-value.unset {
  font-size: 1rem;
  font-weight: 500;
  color: var(--text-faint);
  white-space: normal;
}

.tile-sub {
  margin-top: auto;
  padding-top: 0.4rem;
  display: flex;
  flex-direction: column;
  gap: 0.1rem;
  font-size: 0.78rem;
  color: var(--text-faint);
}

/*
 * 확인이 필요한 타일의 머리에 붙는 배지. 색만으로 구분하지 않도록 "! 확인 필요" 글자를 그대로
 * 쓰고, 배경도 얹어 흑백에서도 테두리로 구분되게 한다. 카드 배경색 자체는 바꾸지 않는다 — 표
 * 전체가 경고색으로 물들면 정작 배지가 묻힌다.
 */
.status-badge {
  margin-left: auto;
  padding: 0.1rem 0.5rem;
  border-radius: 999px;
  background: var(--warn-badge-bg);
  color: var(--warn-badge-fg);
  font-size: 0.72rem;
  font-weight: 600;
  white-space: nowrap;
}

.card header {
  display: flex;
  align-items: baseline;
  gap: 0.5rem;
  margin-bottom: 0.6rem;
}

.card h2 {
  font-size: 0.92rem;
  color: var(--text-h);
}

.card h3 {
  font-size: 0.78rem;
  color: var(--text-muted);
  margin: 0.5rem 0 0.2rem;
}

.more {
  margin-left: auto;
  font-size: 0.76rem;
}

.headline {
  font-size: 1.7rem;
  font-weight: 600;
  color: var(--text-h);
  line-height: 1.2;
}

.headline.small {
  font-size: 1.1rem;
}

.basis {
  margin-left: 0.4rem;
  font-size: 0.76rem;
  font-weight: 400;
  color: var(--text-faint);
}

.rows {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 0.2rem 0.7rem;
  margin-top: 0.5rem;
  font-size: 0.82rem;
}

.rows dt {
  color: var(--text-faint);
}

.rows dd {
  color: var(--text-muted);
}

/*
 * `.rows dd` above sets a neutral color, so the tone classes need matching specificity to win
 * rather than losing to source order. Same tone classes as the KPI tile sub-line and ProgressPanel
 * — one rule for what "+" and "-" mean, reused everywhere a variance figure is printed.
 */
.rows dd.variance-ahead {
  color: var(--success-text);
}

.rows dd.variance-behind {
  color: var(--danger);
}

.variance-ahead {
  color: var(--success-text);
}

.variance-behind {
  color: var(--danger);
}

.muted {
  color: var(--text-faint);
  font-size: 0.78rem;
}

.counts {
  display: flex;
  gap: 1rem;
  margin-top: 0.6rem;
  font-size: 0.82rem;
  color: var(--text-muted);
}

.counts strong {
  color: var(--text-h);
}

.counts .bad strong {
  color: var(--danger);
}

.counts .warn strong {
  color: var(--warn-strong);
}

.refs {
  list-style: none;
  margin: 0.4rem 0 0;
  padding: 0;
  font-size: 0.8rem;
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
}

.refs li {
  display: flex;
  gap: 0.4rem;
  align-items: baseline;
}

.refs .muted {
  margin-left: auto;
  white-space: nowrap;
}

.warn-note {
  margin-top: 0.5rem;
  font-size: 0.8rem;
  color: var(--warn-strong);
  background: var(--warn-weak);
  border-left: 3px solid var(--warn);
  border-radius: 6px;
  padding: 0.4rem 0.6rem;
  line-height: 1.5;
}

.ok-note {
  margin-top: 0.4rem;
  font-size: 0.8rem;
  color: var(--text-dim);
}

.modes {
  list-style: none;
  display: flex;
  flex-wrap: wrap;
  gap: 0.6rem;
  margin: 0.6rem 0 0;
  padding: 0;
  font-size: 0.78rem;
  color: var(--text-faint);
}

.modes strong {
  color: var(--text-muted);
}

.goal {
  font-size: 0.82rem;
  color: var(--text-muted);
  margin-top: 0.15rem;
}

.blocked {
  list-style: none;
  margin: 0.5rem 0 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
  font-size: 0.8rem;
}

.blocked-title {
  color: var(--text-h);
  margin-right: 0.4rem;
}

/* 막대 하나가 종료된 Sprint 하나. 축 없이 값을 옆에 적는다 — 칸이 적어 눈금이 소음이 된다. */
.trend {
  list-style: none;
  display: flex;
  align-items: flex-end;
  gap: 0.5rem;
  margin: 0 0 0.4rem;
  padding: 0;
  overflow-x: auto;
}

.trend li {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.15rem;
  min-width: 3.5rem;
}

.trend-bar-slot {
  display: flex;
  align-items: flex-end;
  height: 4rem;
  width: 1.4rem;
}

.trend-bar {
  width: 100%;
  min-height: 2px;
  background: var(--accent);
  border-radius: 3px 3px 0 0;
}

.trend-points {
  font-size: 0.8rem;
  color: var(--text-h);
}

.trend-name {
  font-size: 0.7rem;
  color: var(--text-faint);
  max-width: 5rem;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.running {
  font-size: 0.78rem;
  color: var(--text-faint);
}

.gaps {
  list-style: none;
  margin: 0.4rem 0 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  font-size: 0.82rem;
}

.gaps li {
  display: flex;
  gap: 0.5rem;
}

.gaps strong {
  margin-left: auto;
  color: var(--text-muted);
}

.loading {
  margin-top: 1rem;
}
</style>
