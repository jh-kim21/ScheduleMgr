<script setup lang="ts">
import { computed, onMounted, watch } from 'vue'
import { RouterLink } from 'vue-router'
import type { DataGap, RaidRef, TaskRef } from '../api/dashboardApi'
import { useDashboard } from '../features/dashboard/useDashboard'
import { useProjects } from '../features/projects/useProjects'
import { ensureSelection, selectedProjectId } from '../stores/projectSelection'
import { EXECUTION_MODE_LABELS } from '../shared/executionMode'
import { PROGRESS_BASIS_LABELS, progressText } from '../shared/progress'
import { RAID_TYPE_LABELS } from '../shared/raid'

const { projects, error: projectsError, ensureLoaded: ensureProjects } = useProjects()
const { data, loading, error, ensureLoaded, invalidate, load } = useDashboard()

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
  const rounded = Math.round(points * 10) / 10
  if (rounded === 0) return '계획과 같음'
  return rounded > 0 ? `계획보다 ${rounded}%p 앞섬` : `계획보다 ${-rounded}%p 뒤짐`
})

/** 데이터 누락이 향하는 화면. kind마다 원인이 있는 곳이 다르다. */
function gapRoute(gap: DataGap) {
  if (gap.kind.startsWith('BACKLOG')) return { path: '/backlog' }
  if (gap.kind === 'NOT_ESTIMABLE' || gap.kind === 'WEIGHT_MISSING') return { path: '/progress' }
  return { path: '/wbs', query: { focus: gap.wbsItemIds[0] } }
}

function taskRoute(task: TaskRef) {
  return { path: '/wbs', query: { focus: task.wbsItemId } }
}

function raidLabel(entry: RaidRef) {
  const owner = entry.ownerName ?? '소유자 미지정'
  return entry.detail ? `${entry.title} · ${entry.detail} · ${owner}` : `${entry.title} · ${owner}`
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
    <h1>대시보드</h1>

    <p v-if="projectsError" class="error">{{ projectsError }}</p>

    <p v-else-if="projects.length === 0" class="notice">
      먼저 프로젝트를 등록해야 대시보드를 볼 수 있습니다.
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
        <button type="button" class="refresh" :disabled="loading" @click="refresh">새로고침</button>
      </div>

      <p v-if="error" class="error">{{ error }}</p>
      <p v-if="loading" class="loading">불러오는 중...</p>

      <template v-else-if="data">
        <p v-if="data.workPackages.total === 0" class="notice">
          WBS에 Work Package가 없습니다. 업무를 등록하면 진척과 일정이 여기에 모입니다.
          <RouterLink to="/wbs">WBS 화면으로 이동</RouterLink>
        </p>

        <div class="cards">
          <!-- 진척 -->
          <article class="card wide">
            <header>
              <h2>진척</h2>
              <RouterLink to="/progress" class="more">상세</RouterLink>
            </header>

            <p class="headline">
              {{ progressText(data.progress.actualPercent) }}
              <span v-if="data.progress.basis" class="basis">
                {{ PROGRESS_BASIS_LABELS[data.progress.basis] }}
              </span>
            </p>

            <dl class="rows">
              <dt>계획 진척</dt>
              <dd v-if="data.progress.plannedPercent !== null">
                {{ progressText(data.progress.plannedPercent) }}
                <span class="muted">(기준선 v{{ data.baseline?.version }} 범위)</span>
              </dd>
              <dd v-else class="muted">미산정 — 승인된 기준선이 없습니다.</dd>

              <template v-if="varianceLabel">
                <dt>편차</dt>
                <dd :class="{ behind: (variance ?? 0) < 0 }">
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

          <!-- 일정 -->
          <article class="card">
            <header>
              <h2>일정</h2>
              <RouterLink to="/gantt" class="more">간트</RouterLink>
            </header>

            <dl class="rows">
              <dt>계획 기간</dt>
              <dd v-if="data.project.planStart">
                {{ data.project.planStart }} ~ {{ data.project.planEnd }}
              </dd>
              <dd v-else class="muted">일정이 입력된 항목이 없습니다.</dd>

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

          <!-- 기준 일정 -->
          <article class="card">
            <header>
              <h2>기준 일정</h2>
              <RouterLink to="/progress" class="more">기준선</RouterLink>
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

          <!-- 실행 -->
          <article class="card wide">
            <header>
              <h2>실행</h2>
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

          <!-- 속도 -->
          <article class="card wide">
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

          <article class="card">
            <header>
              <h2>위험·이슈</h2>
              <RouterLink to="/raid" class="more">RAID</RouterLink>
            </header>

            <template
              v-if="
                data.control.openIssues.length > 0 ||
                data.control.highExposure.length > 0 ||
                data.control.overdue.length > 0
              "
            >
              <template v-if="data.control.overdue.length > 0">
                <h3>기한 초과</h3>
                <ul class="refs">
                  <li v-for="entry in data.control.overdue" :key="entry.raidItemId">
                    <RouterLink to="/raid">{{ raidLabel(entry) }}</RouterLink>
                  </li>
                </ul>
              </template>

              <template v-if="data.control.highExposure.length > 0">
                <h3>노출도 높음</h3>
                <ul class="refs">
                  <li v-for="entry in data.control.highExposure" :key="entry.raidItemId">
                    <RouterLink to="/raid">{{ raidLabel(entry) }}</RouterLink>
                  </li>
                </ul>
              </template>

              <template v-if="data.control.openIssues.length > 0">
                <h3>열린 이슈</h3>
                <ul class="refs">
                  <li v-for="entry in data.control.openIssues" :key="entry.raidItemId">
                    <RouterLink to="/raid">{{ raidLabel(entry) }}</RouterLink>
                  </li>
                </ul>
              </template>
            </template>
            <p v-else class="ok-note">열린 위험·이슈가 없습니다.</p>
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
          <article v-if="data.gaps.length > 0" class="card wide">
            <header><h2>데이터 누락</h2></header>
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
        </div>
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

/* 카드는 두 열이 기본이고, 넓은 카드는 두 칸을 차지한다. 좁아지면 한 열로 접힌다. */
.cards {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(19rem, 1fr));
  gap: 0.9rem;
  align-items: start;
}

.card {
  border: 1px solid var(--border);
  border-radius: 8px;
  background: var(--surface);
  padding: 0.85rem 1rem 1rem;
}

.card.wide {
  grid-column: span 2;
}

@media (max-width: 60rem) {
  .card.wide {
    grid-column: span 1;
  }
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

.rows dd.behind {
  color: var(--warn-strong);
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
