<script setup lang="ts">
import { computed, onMounted, watch } from 'vue'
import { RouterLink } from 'vue-router'
import type { MemberInput } from '../api/memberApi'
import MemberEditor from '../features/raci/MemberEditor.vue'
import RaciMatrix from '../features/raci/RaciMatrix.vue'
import { useRaci } from '../features/raci/useRaci'
import { useProjects } from '../features/projects/useProjects'
import { ensureSelection, selectedProjectId } from '../stores/projectSelection'
import {
  issueSummary,
  RACI_DESCRIPTIONS,
  RACI_ENGLISH,
  RACI_LABELS,
  RACI_LETTERS,
  RACI_ORDER,
  type RaciIssueType,
  type RaciRole,
} from '../shared/raci'

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
    <h1>
      RACI
      <span class="acronym">{{ RACI_ORDER.map((role) => RACI_ENGLISH[role]).join(' · ') }}</span>
    </h1>
    <p class="lede">업무마다 누가 실행하고 누가 책임지는지 한 표에 적습니다</p>

    <!-- 네 글자 범례. 문구는 shared/raci.ts의 상수를 그대로 렌더한다 — 두 벌로 적으면 한쪽만
         고쳐진다. -->
    <p class="legend">
      <span v-for="role in RACI_ORDER" :key="role" class="legend-item">
        <strong>{{ RACI_LETTERS[role] }}</strong> {{ RACI_LABELS[role] }} — {{ RACI_DESCRIPTIONS[role] }}
      </span>
      <br />
      한 사람이 여러 글자를 겸할 수 있습니다. 상위 업무의 글자는 하위로 상속되고, 하위가 같은 역할을
      지정하면 그것이 우선합니다(상위 글자는 취소선). 규칙 위반은 저장을 막지 않고 표시만 합니다.
    </p>

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
          :error="error"
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

/* 범례는 화면의 주된 내용(매트릭스)이 아니므로 접지 않되 시각적으로 물러나 있는다. RaidView와 같은
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
