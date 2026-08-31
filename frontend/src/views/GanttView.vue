<script setup lang="ts">
import { computed, onMounted, watch } from 'vue'
import { RouterLink } from 'vue-router'
import type { DependencyInput } from '../api/ganttApi'
import DependencyEditor from '../features/gantt/DependencyEditor.vue'
import GanttChart from '../features/gantt/GanttChart.vue'
import { useGantt } from '../features/gantt/useGantt'
import { useProjects } from '../features/projects/useProjects'
import { ensureSelection, selectedProjectId } from '../stores/projectSelection'

const { projects, error: projectsError, ensureLoaded: ensureProjects } = useProjects()
const {
  data,
  loading,
  error,
  lastRecalculation,
  showCriticalPath,
  ensureLoaded,
  addDependency,
  updateDependency,
  removeDependency,
  recalculate,
} = useGantt()

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

const violations = computed(() => data.value.tasks.filter((task) => task.scheduleViolation))

/** Leaf rows only: a summary would double-count the work its children already report. */
const leafTasks = computed(() => data.value.tasks.filter((task) => !task.summary))
const delayed = computed(() => leafTasks.value.filter((task) => task.delayStatus === 'DELAYED'))
const atRisk = computed(() => leafTasks.value.filter((task) => task.delayStatus === 'AT_RISK'))
const worstDelayDays = computed(() =>
  delayed.value.reduce((worst, task) => Math.max(worst, task.delayDays), 0),
)

/**
 * 임계 경로는 선후행 관계로 이어진 사슬에 대한 이야기이므로, 관계가 없으면 표시할 것도 없다.
 * 서버가 판정한 결과를 그대로 쓰고 여기서 다시 계산하지 않는다.
 */
const criticalTasks = computed(() =>
  data.value.tasks
    .filter((task) => task.criticalPath)
    .slice()
    .sort((a, b) => (a.startDate ?? '').localeCompare(b.startDate ?? '')),
)

/** 사슬의 시작과 끝. 종료일 포함이므로 일수는 +1 한다. */
const criticalSpan = computed(() => {
  const starts = criticalTasks.value.map((task) => task.startDate).filter((d): d is string => !!d)
  const ends = criticalTasks.value.map((task) => task.endDate).filter((d): d is string => !!d)
  if (starts.length === 0 || ends.length === 0) return null
  const start = starts.reduce((min, d) => (d < min ? d : min))
  const end = ends.reduce((max, d) => (d > max ? d : max))
  const days = Math.round((Date.parse(end) - Date.parse(start)) / 86_400_000) + 1
  return { start, end, days }
})

/** 여유가 가장 적은 비임계 업무 — 다음으로 임계 경로가 될 후보. */
const nextTightest = computed(() => {
  const candidates = data.value.tasks.filter(
    (task) => !task.summary && !task.criticalPath && task.floatDays !== null,
  )
  if (candidates.length === 0) return null
  return candidates.reduce((tightest, task) =>
    (task.floatDays ?? 0) < (tightest.floatDays ?? 0) ? task : tightest,
  )
})

async function handleAdd(input: DependencyInput) {
  if (selectedProjectId.value !== null) await addDependency(selectedProjectId.value, input)
}

async function handleUpdate(dependencyId: number, input: DependencyInput) {
  if (selectedProjectId.value !== null) {
    await updateDependency(selectedProjectId.value, dependencyId, input)
  }
}

async function handleRemove(dependencyId: number) {
  if (selectedProjectId.value !== null) await removeDependency(selectedProjectId.value, dependencyId)
}

async function handleRecalculate() {
  if (selectedProjectId.value === null) return
  if (!confirm('선후행 관계를 만족하도록 일정을 뒤로 밀어냅니다. 진행할까요?')) return
  await recalculate(selectedProjectId.value)
}
</script>

<template>
  <section>
    <h1>간트 차트</h1>

    <p v-if="projectsError" class="error">{{ projectsError }}</p>

    <p v-else-if="projects.length === 0" class="notice">
      먼저 프로젝트를 등록해야 간트 차트를 볼 수 있습니다.
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

        <div class="toolbar-actions">
          <label v-if="criticalTasks.length > 0" class="critical-toggle">
            <input v-model="showCriticalPath" type="checkbox" />
            임계 경로 강조
          </label>

          <button type="button" class="recalc" :disabled="loading" @click="handleRecalculate">
            일정 재계산
          </button>
        </div>
      </div>

      <p v-if="error" class="error">{{ error }}</p>

      <p v-if="lastRecalculation !== null" class="result">
        {{
          lastRecalculation === 0
            ? '모든 선후행 관계가 이미 충족되어 변경된 일정이 없습니다.'
            : `${lastRecalculation}개 항목의 일정을 뒤로 이동했습니다.`
        }}
      </p>

      <p v-if="delayed.length > 0 || atRisk.length > 0" class="delay">
        <template v-if="delayed.length > 0">
          <strong>지연 {{ delayed.length }}건</strong>
          (최대 {{ worstDelayDays }}일) —
          {{ delayed.map((task) => `${task.code} ${task.name}`).join(', ') }}.
        </template>
        <template v-if="atRisk.length > 0">
          <strong>지연 위험 {{ atRisk.length }}건</strong> —
          {{ atRisk.map((task) => `${task.code} ${task.name} (${task.progressGap}%p 부족)`).join(', ') }}.
        </template>
      </p>

      <p v-else-if="leafTasks.length > 0" class="ok">기준일 현재 지연된 업무가 없습니다.</p>

      <p v-if="violations.length > 0" class="violation">
        선행 업무보다 먼저 시작하는 업무가 {{ violations.length }}개 있습니다 —
        {{ violations.map((task) => `${task.code} ${task.name}`).join(', ') }}.
        <strong>일정 재계산</strong>으로 자동 조정할 수 있습니다.
      </p>

      <p v-if="criticalSpan && criticalTasks.length > 0" class="critical">
        <strong>임계 경로 {{ criticalTasks.length }}개 업무</strong>
        · {{ criticalSpan.start }} ~ {{ criticalSpan.end }} ({{ criticalSpan.days }}일) —
        {{ criticalTasks.map((task) => `${task.code} ${task.name}`).join(' → ') }}.
        <span class="hint">
          이 업무들은 여유가 없어, 하루 밀리면 프로젝트 종료일도 하루 밀립니다.
          <template v-if="nextTightest">
            다음으로 촉박한 업무는 {{ nextTightest.code }} {{ nextTightest.name }} (여유
            {{ nextTightest.floatDays }}일)입니다.
          </template>
        </span>
      </p>

      <p v-if="loading">불러오는 중...</p>
      <template v-else>
        <GanttChart :data="data" :highlight-critical-path="showCriticalPath" />
        <DependencyEditor
          :data="data"
          @add="handleAdd"
          @update="handleUpdate"
          @remove="handleRemove"
        />
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
  justify-content: space-between;
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

.toolbar-actions {
  display: flex;
  align-items: center;
  gap: 0.85rem;
}

.critical-toggle {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  font-size: 0.8rem;
  color: var(--text-muted);
  cursor: pointer;
  white-space: nowrap;
}

.recalc {
  padding: 0.45rem 0.9rem;
  border-radius: 6px;
  border: 1px solid var(--accent);
  background: var(--surface);
  color: var(--accent);
  cursor: pointer;
  font-size: 0.85rem;
}

.recalc:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.error {
  color: var(--danger);
  margin-bottom: 0.75rem;
}

.result {
  font-size: 0.85rem;
  color: var(--success);
  background: var(--success-weak);
  border-radius: 6px;
  padding: 0.5rem 0.7rem;
  margin-bottom: 0.75rem;
}

.delay {
  font-size: 0.85rem;
  color: var(--warn-strong);
  background: var(--warn-weak);
  border-left: 3px solid var(--warn);
  border-radius: 6px;
  padding: 0.5rem 0.7rem;
  margin-bottom: 0.75rem;
  line-height: 1.5;
}

.ok {
  font-size: 0.85rem;
  color: var(--success-text);
  margin-bottom: 0.75rem;
}

/* 선후행 위반은 지연과 다른 문제이므로 색도 다르게 쓴다 (차트의 점선 외곽선과 동일 계열). */
.violation {
  font-size: 0.85rem;
  color: var(--violation-text);
  background: var(--violation-weak);
  border-left: 3px solid var(--violation);
  border-radius: 6px;
  padding: 0.5rem 0.7rem;
  margin-bottom: 0.75rem;
}

/* 임계 경로는 지연·위반과 또 다른 종류의 정보라 배너 색도 중립 계열로 따로 둔다. */
.critical {
  font-size: 0.85rem;
  color: var(--text);
  background: var(--surface-alt);
  border-left: 3px solid var(--critical);
  border-radius: 6px;
  padding: 0.5rem 0.7rem;
  margin-bottom: 0.75rem;
  line-height: 1.5;
}

.critical .hint {
  display: block;
  margin-top: 0.2rem;
  color: var(--text-dim);
  font-size: 0.8rem;
}

.notice {
  color: var(--text-dim);
}
</style>
