<script setup lang="ts">
import { computed, onMounted, watch } from 'vue'
import { RouterLink } from 'vue-router'
import type { MemberInput } from '../api/memberApi'
import MemberEditor from '../features/raci/MemberEditor.vue'
import ExportMenu from '../features/export/ExportMenu.vue'
import RaciMatrix from '../features/raci/RaciMatrix.vue'
import { useRaci } from '../features/raci/useRaci'
import { useProjects } from '../features/projects/useProjects'
import { ensureSelection, selectedProjectId } from '../stores/projectSelection'
import { issueSummary, type RaciIssueType, type RaciRole } from '../shared/raci'
import { raciCsv, raciLegend } from '../shared/exportRows'

const { projects, error: projectsError, ensureLoaded: ensureProjects } = useProjects()
const {
  data,
  loading,
  error,
  cellIndex,
  issuesByTask,
  ensureLoaded,
  assign,
  unassign,
  addMember,
  updateMember,
  removeMember,
} = useRaci()

/** 내보내기 파일명에 쓸 프로젝트 이름. */
const selectedProjectName = computed(
  () => projects.value.find((project) => project.id === selectedProjectId.value)?.name ?? 'project',
)


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

/**
 * Issues grouped by rule, so the banner says "실무 담당자 없음 3건" instead of listing the same
 * sentence three times. Ordered worst first: a clash is a decision someone has to make, while a
 * gap is usually just an unfinished matrix.
 */
const ISSUE_ORDER: RaciIssueType[] = [
  'MULTIPLE_ACCOUNTABLE',
  'MISSING_ACCOUNTABLE',
  'MISSING_RESPONSIBLE',
]

const issueGroups = computed(() =>
  ISSUE_ORDER.flatMap((type) => {
    const matching = data.value.issues.filter((issue) => issue.type === type)
    return matching.length === 0 ? [] : [{ type, issues: matching }]
  }),
)

/** Leaves only — the server validates leaves, so this is what "완료" is measured against. */
const leafCount = computed(() => data.value.tasks.filter((task) => !task.summary).length)

function handleAssign(wbsItemId: number, memberId: number, role: RaciRole) {
  if (selectedProjectId.value !== null) {
    assign(selectedProjectId.value, { wbsItemId, memberId, role })
  }
}

function handleUnassign(assignmentId: number) {
  if (selectedProjectId.value !== null) unassign(selectedProjectId.value, assignmentId)
}

function handleAddMember(input: MemberInput) {
  if (selectedProjectId.value !== null) addMember(selectedProjectId.value, input)
}

function handleUpdateMember(memberId: number, input: MemberInput) {
  if (selectedProjectId.value !== null) updateMember(selectedProjectId.value, memberId, input)
}

function handleRemoveMember(memberId: number) {
  if (selectedProjectId.value !== null) removeMember(selectedProjectId.value, memberId)
}
</script>

<template>
  <section>
    <h1>RACI</h1>

    <p v-if="projectsError" class="error">{{ projectsError }}</p>

    <p v-else-if="projects.length === 0" class="notice">
      먼저 프로젝트를 등록해야 RACI를 작성할 수 있습니다.
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
          screen="raci"
          :csv="() => raciCsv(data)"
          :note="`RACI: ${raciLegend()}`"
        />
      </div>

      <p v-if="error" class="error">{{ error }}</p>

      <p v-if="issueGroups.length > 0" class="issues">
        <span v-for="group in issueGroups" :key="group.type" class="issue-group">
          <strong>{{ issueSummary(group.type) }} {{ group.issues.length }}건</strong>
          —
          {{
            group.issues
              .map((issue) =>
                issue.memberNames.length > 0
                  ? `${issue.code} ${issue.name} (${issue.memberNames.join(', ')})`
                  : `${issue.code} ${issue.name}`,
              )
              .join(', ')
          }}.
        </span>
        <span class="hint">
          검증 대상은 하위가 없는 업무 {{ leafCount }}건입니다. 규칙을 어겨도 저장은 막지 않으니,
          정리하는 중이라면 그대로 두어도 됩니다.
        </span>
      </p>

      <p v-else-if="leafCount > 0 && data.members.length > 0" class="ok">
        업무 {{ leafCount }}건 모두 최종 책임자 한 명과 실무 담당자가 지정되어 있습니다.
      </p>

      <p v-if="loading">불러오는 중...</p>
      <template v-else>
        <RaciMatrix
          :data="data"
          :cell-index="cellIndex"
          :issues-by-task="issuesByTask"
          @assign="handleAssign"
          @unassign="handleUnassign"
        />
        <MemberEditor
          :members="data.members"
          @add="handleAddMember"
          @update="handleUpdateMember"
          @remove="handleRemoveMember"
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
  gap: 1rem;
  margin-bottom: 1rem;
}

.toolbar .export {
  margin-left: auto;
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

.error {
  color: var(--danger);
  margin-bottom: 0.75rem;
}

/* 규칙 위반은 지연과 성격이 달라 간트의 지연 배너와 같은 경고 계열을 쓰되 별도로 둔다. */
.issues {
  font-size: 0.85rem;
  color: var(--warn-strong);
  background: var(--warn-weak);
  border-left: 3px solid var(--warn);
  border-radius: 6px;
  padding: 0.5rem 0.7rem;
  margin-bottom: 0.75rem;
  line-height: 1.5;
}

.issue-group {
  display: block;
}

.issues .hint {
  display: block;
  margin-top: 0.2rem;
  color: var(--warn-badge-fg);
  font-size: 0.8rem;
}

.ok {
  font-size: 0.85rem;
  color: var(--success-text);
  margin-bottom: 0.75rem;
}

.notice {
  color: var(--text-dim);
}
</style>
