<script setup lang="ts">
import { computed, onMounted, watch } from 'vue'
import { RouterLink } from 'vue-router'
import type { BoardMoveInput, SprintItem } from '../api/sprintApi'
import SprintBoard from '../features/sprint/SprintBoard.vue'
import { useRaid } from '../features/raid/useRaid'
import { useSprints } from '../features/sprint/useSprints'
import { useProjects } from '../features/projects/useProjects'
import { remainingLabel, SPRINT_STATUS_LABELS, sprintPeriod } from '../shared/sprint'
import { localToday } from '../shared/delay'
import { ensureSelection, selectedProjectId } from '../stores/projectSelection'

const { projects, error: projectsError, ensureLoaded: ensureProjects } = useProjects()
/**
 * 차단된 카드에 "왜 막혔나"를 적으려면 등록부가 필요하다. RAID 컴포저블을 그대로 쓴다 —
 * 모듈 스코프라 Sprint 화면과 한 번의 조회를 나눠 쓴다.
 */
const { data: raidLog, ensureLoaded: ensureRaid } = useRaid()
/**
 * Sprint 선택은 Sprint 화면과 공유한다(같은 모듈 스코프 컴포저블). 그래서 저기서 고른 Sprint 의
 * Board 가 여기서 열리고, 처음 들어오면 실행 중인 Sprint 가 기본값이다.
 */
const { data, loading, error, selectedSprintId, selected, ensureLoaded, move, unassign } =
  useSprints()

const today = localToday()

// 선택 watcher 하나가 유일한 로드 경로다.
watch(
  selectedProjectId,
  (id) => {
    if (id !== null) {
      ensureLoaded(id)
      ensureRaid(id)
    }
  },
  { immediate: true },
)

onMounted(async () => {
  await ensureProjects()
  ensureSelection(projects.value.map((project) => project.id))
})

/** 계획 중인 Sprint 는 아직 실행할 것이 없지만, 배정을 확인하러 열어 볼 수는 있다. */
const boards = computed(() => data.value.sprints)

async function handleMove(backlogItemId: number, input: BoardMoveInput) {
  const projectId = selectedProjectId.value
  const sprintId = selectedSprintId.value
  if (projectId === null || sprintId === null) return
  await move(projectId, sprintId, backlogItemId, input)
}

async function handleUnassign(item: SprintItem) {
  const projectId = selectedProjectId.value
  const sprintId = selectedSprintId.value
  if (projectId === null || sprintId === null) return
  await unassign(projectId, sprintId, item.backlogItemId)
}
</script>

<template>
  <section>
    <h1>Board</h1>

    <p v-if="projectsError" class="error">{{ projectsError }}</p>

    <p v-else-if="projects.length === 0" class="notice">
      먼저 프로젝트를 등록해야 Board 를 볼 수 있습니다.
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

        <label v-if="boards.length > 0" class="sprint-picker">
          Sprint
          <select v-model="selectedSprintId">
            <option v-for="sprint in boards" :key="sprint.id" :value="sprint.id">
              {{ sprint.name }} ({{ SPRINT_STATUS_LABELS[sprint.status] }})
            </option>
          </select>
        </label>
      </div>

      <p v-if="error" class="error">{{ error }}</p>
      <p v-if="loading" class="loading">불러오는 중...</p>

      <template v-else-if="selected">
        <div class="head">
          <div>
            <h2>
              {{ selected.name }}
              <span class="status" :data-status="selected.status">
                {{ SPRINT_STATUS_LABELS[selected.status] }}
              </span>
            </h2>
            <p class="period">
              {{ sprintPeriod(selected.startDate, selected.endDate) }}
              <template v-if="selected.status === 'ACTIVE'">
                · {{ remainingLabel(selected.endDate, today) }}
              </template>
            </p>
            <p v-if="selected.goal" class="goal">목표: {{ selected.goal }}</p>
          </div>
          <RouterLink to="/sprint" class="to-sprint">Sprint 계획으로</RouterLink>
        </div>

        <p v-if="selected.status === 'PLANNED'" class="planned-note">
          아직 시작하지 않은 Sprint 입니다. 카드를 옮길 수는 있지만, 실적은 시작한 뒤부터
          의미가 있습니다.
        </p>

        <SprintBoard
          :sprint="selected"
          :raid-items="raidLog.items"
          @move="handleMove"
          @unassign="handleUnassign"
        />
      </template>

      <p v-else-if="!loading" class="notice">
        Sprint 가 없습니다. Sprint 를 먼저 만들고 항목을 배정하면 여기에서 실행합니다.
        <RouterLink to="/sprint">Sprint 화면으로 이동</RouterLink>
      </p>
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

.project-picker,
.sprint-picker {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.85rem;
  color: var(--text-muted);
}

.project-picker select,
.sprint-picker select {
  padding: 0.4rem 0.6rem;
  border: 1px solid var(--border-input);
  border-radius: 6px;
  font: inherit;
}

.head {
  display: flex;
  align-items: flex-start;
  gap: 1rem;
  margin-bottom: 0.75rem;
}

.head h2 {
  font-size: 1rem;
  color: var(--text-h);
}

.status {
  margin-left: 0.4rem;
  padding: 0.1rem 0.4rem;
  border-radius: 999px;
  font-size: 0.7rem;
  font-weight: 500;
  background: var(--surface-alt);
  color: var(--text-muted);
}

.status[data-status='ACTIVE'] {
  background: var(--accent);
  color: var(--accent-fg);
}

.period,
.goal {
  font-size: 0.8rem;
  color: var(--text-muted);
  margin-top: 0.15rem;
}

.to-sprint {
  margin-left: auto;
  font-size: 0.8rem;
  white-space: nowrap;
}

.planned-note {
  font-size: 0.8rem;
  color: var(--text-dim);
  margin-bottom: 0.6rem;
}

.error {
  color: var(--danger);
  margin-bottom: 0.75rem;
}

.notice {
  color: var(--text-dim);
}

.loading {
  margin-top: 1rem;
}
</style>
