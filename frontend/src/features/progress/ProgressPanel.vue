<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { WorkPackageProgress } from '../../api/progressApi'
import { useProgress } from './useProgress'
import { executionModeLabel } from '../../shared/executionMode'
import {
  ACCEPTANCE_STATUS_LABELS,
  PROGRESS_BASIS_HINTS,
  PROGRESS_BASIS_LABELS,
  progressBarWidth,
  progressText,
  varianceText,
} from '../../shared/progress'
import { useRowSelection } from '../../shared/useRowSelection'
import { selectedProjectId } from '../../stores/projectSelection'

const {
  data,
  snapshots,
  loading,
  error,
  ensureLoaded,
  addCheckpoint,
  setApproval,
  deleteCheckpoint,
  approveBaseline,
  updateBasis,
  saveSnapshot,
} = useProgress()
// 같은 화면의 요약 탭이 이 숫자들을 다시 보여 준다. 여기서 무효화를 따로 부르지 않는 이유는
// useProgress 의 모든 변경이 markWbsChanged() 를 부르고, 대시보드 캐시 키에 WBS 리비전이
// 들어 있어 키가 이미 바뀌기 때문이다. 탭을 되돌아올 때 다시 읽는 것은 DashboardView 가 한다.

/** Which Work Package's checkpoints are open for editing. */
const expanded = ref<number | null>(null)

/**
 * Which Work Package's weight/α are being edited inline, and the values in progress — only one row
 * at a time. Not a computed off `data.value`: the input needs somewhere to hold a value the server
 * hasn't seen yet (including an in-progress `''` while the user is between digits).
 */
const editingBasis = ref<{ wbsItemId: number; weight: number | null; agileRatio: number | null } | null>(
  null,
)

/**
 * 클릭 선택·방향키 이동은 다른 표와 같은 컴포저블을 쓴다. 이 표에는 행 편집 대화상자가 없으므로
 * 더블클릭은 (수정이 아니라) 체크포인트 펼치기 토글에 연결한다 — `toggle`은 아래에서 정의된다.
 */
const body = ref<HTMLElement | null>(null)
const selection = useRowSelection(
  () => (data.value?.workPackages ?? []).map((wp) => wp.wbsItemId),
  body,
)
const newTitle = ref('')
const newWeight = ref<number | null>(null)
const newCriteria = ref('')

const approving = ref<{ checkpointId: number } | null>(null)
const approver = ref('')

const baselineOpen = ref(false)
const baselineBy = ref('')
const baselineNote = ref('')

const snapshotNote = ref('')

watch(
  selectedProjectId,
  (id) => {
    expanded.value = null
    editingBasis.value = null
    if (id !== null) ensureLoaded(id)
  },
  { immediate: true },
)


const project = computed(() => data.value?.project ?? null)
const scope = computed(() => data.value?.scope ?? null)

/** Work Packages whose figure cannot be produced yet — what the headline number is silent about. */
const notEstimable = computed(
  () => data.value?.workPackages.filter((wp) => wp.percent === null) ?? [],
)

function toggle(wp: WorkPackageProgress) {
  expanded.value = expanded.value === wp.wbsItemId ? null : wp.wbsItemId
  newTitle.value = ''
  newWeight.value = null
  newCriteria.value = ''
}

/** Opens inline editing for `wp`, discarding any other row's unsaved edit. */
function startEditBasis(wp: WorkPackageProgress) {
  editingBasis.value = { wbsItemId: wp.wbsItemId, weight: wp.weight, agileRatio: wp.agileRatio }
}

function cancelEditBasis() {
  editingBasis.value = null
}

/**
 * `v-model.number` leaves an empty box as `''` rather than `null` (and a box mid-edit of "-" or "."
 * as `NaN`) — neither is a value the backend accepts, and both mean the same thing here: "not
 * entered". Only this boundary needs to know that; the rest of the form treats `editingBasis` as
 * `number | null` throughout.
 */
function normalizeBasisNumber(value: number | string | null): number | null {
  if (value === '' || value === null || Number.isNaN(value as number)) return null
  return value as number
}

async function saveBasis() {
  const projectId = selectedProjectId.value
  const editing = editingBasis.value
  if (projectId === null || editing === null) return
  const ok = await updateBasis(projectId, editing.wbsItemId, {
    weight: normalizeBasisNumber(editing.weight),
    agileRatio: normalizeBasisNumber(editing.agileRatio),
  })
  // Rejected: keep the row open with what the user typed rather than losing it — `error` above the
  // table already explains why.
  if (ok) editingBasis.value = null
}

async function submitCheckpoint(wp: WorkPackageProgress) {
  const projectId = selectedProjectId.value
  if (projectId === null || !newTitle.value.trim()) return
  const ok = await addCheckpoint(projectId, {
    wbsItemId: wp.wbsItemId,
    title: newTitle.value.trim(),
    weight: newWeight.value,
    completionCriteria: newCriteria.value.trim() || null,
  })
  if (ok) {
    newTitle.value = ''
    newWeight.value = null
    newCriteria.value = ''
  }
}

async function confirmApproval() {
  const projectId = selectedProjectId.value
  const target = approving.value
  approving.value = null
  if (projectId === null || !target) return
  await setApproval(projectId, target.checkpointId, true, approver.value.trim() || null)
}

async function confirmBaseline() {
  const projectId = selectedProjectId.value
  if (projectId === null || !baselineBy.value.trim()) return
  const ok = await approveBaseline(projectId, baselineBy.value.trim(), baselineNote.value.trim() || null)
  if (ok) {
    baselineOpen.value = false
    baselineBy.value = ''
    baselineNote.value = ''
  }
}

async function takeSnapshot() {
  const projectId = selectedProjectId.value
  if (projectId === null) return
  const ok = await saveSnapshot(projectId, snapshotNote.value.trim() || null)
  if (ok) snapshotNote.value = ''
}

/** The stored JSON, unpacked just enough to read in a list. */
function snapshotSummary(metrics: string): string {
  try {
    const parsed = JSON.parse(metrics) as Record<string, unknown>
    const actual = parsed.actualPercent
    const planned = parsed.plannedPercent
    return `실제 ${actual === null ? '산정 전' : `${actual}%`} · 계획 ${planned === null ? '미산정' : `${planned}%`}`
  } catch {
    return metrics
  }
}
</script>

<template>
  <div class="progress-panel">
  <p v-if="loading" class="loading">불러오는 중…</p>
  <p v-if="error" class="error">{{ error }}</p>

  <template v-if="project">
    <div class="summary">
      <div class="metric">
        <span class="label">실제 진척</span>
        <strong :class="{ none: project.actualPercent === null }">
          {{ progressText(project.actualPercent) }}
        </strong>
        <span class="sub">{{ PROGRESS_BASIS_LABELS[project.basis] }}</span>
      </div>
      <div class="metric">
        <span class="label">계획 진척</span>
        <strong :class="{ none: project.plannedPercent === null }">
          {{ project.plannedPercent === null ? '미산정' : progressText(project.plannedPercent) }}
        </strong>
        <span class="sub">승인된 기준선 기준</span>
      </div>
      <div class="metric">
        <span class="label">편차</span>
        <strong :class="{ none: project.variancePoints === null }">
          {{ varianceText(project.variancePoints) }}
        </strong>
        <span class="sub">같은 범위·가중치 비교</span>
      </div>
      <div class="metric">
        <span class="label">Work Package</span>
        <strong>{{ project.workPackageCount }}</strong>
        <span class="sub">산정 전 {{ project.notEstimableCount }}건</span>
      </div>
    </div>

    <p v-if="project.plannedPercent === null" class="notice subtle">
      승인된 기준선이 없어 계획 진척은 미산정입니다. 시간이 지났다는 것만으로 진척을 채우지
      않습니다.
    </p>

    <p v-if="project.incomplete" class="attention">
      일부 하위가 산정 전이거나 가중치가 없어 <strong>집계가 불완전</strong>합니다. 위 숫자는
      셀 수 있는 부분만을 말합니다.
    </p>

    <p v-if="project.acceptancePending > 0" class="attention">
      <strong>인수 대기 {{ project.acceptancePending }}건</strong> — 진척 100%이지만 최종 인수가
      남았습니다.
    </p>

    <p v-if="notEstimable.length > 0" class="notice subtle">
      산정 전: {{ notEstimable.map((wp) => `${wp.code ?? ''} ${wp.name}`.trim()).join(', ') }}
    </p>

    <!-- 기준 범위와 현재 범위 (지시서 5-C) -->
    <div v-if="scope" class="scope">
      <h2>범위 비교</h2>
      <p v-if="!scope.hasBaseline" class="notice subtle">
        승인된 기준선이 없습니다. 기준선을 승인하면 이후의 범위·가중치 변화를 여기서 설명할 수
        있습니다.
      </p>
      <template v-else>
        <p class="scope-line">
          기준 {{ scope.baselineItemCount }}개 항목 → 현재 {{ scope.currentItemCount }}개
        </p>
        <p v-if="scope.added.length > 0" class="scope-line">
          <strong>추가 {{ scope.added.length }}</strong> — {{ scope.added.join(', ') }}
        </p>
        <p v-if="scope.removed.length > 0" class="scope-line">
          <strong>제외 {{ scope.removed.length }}</strong> — {{ scope.removed.join(', ') }}
        </p>
        <p v-if="scope.weightChanged.length > 0" class="scope-line">
          <strong>가중치 변경 {{ scope.weightChanged.length }}</strong> —
          {{ scope.weightChanged.join(', ') }}
        </p>
        <p
          v-if="scope.added.length === 0 && scope.removed.length === 0 && scope.weightChanged.length === 0"
          class="scope-line muted"
        >기준선 승인 이후 범위와 가중치가 그대로입니다.</p>
      </template>

      <div class="scope-actions">
        <span v-if="data?.baseline" class="baseline-badge">
          기준선 v{{ data.baseline.version }} · {{ data.baseline.approvedBy }} ·
          {{ data.baseline.approvedAt.slice(0, 10) }}
        </span>
        <button type="button" @click="baselineOpen = true">기준선 승인</button>
      </div>
    </div>

    <h2>Work Package별 진척</h2>
    <p class="notice subtle">
      가중치는 같은 상위 아래 형제 Work Package 사이의 비중입니다. 여기서 바꾸면 그 항목이 속한
      가지의 집계가 즉시 달라집니다. 비워 두면(미입력) 그 가지는 예전처럼 하위 leaf 개수 가중
      평균으로 집계됩니다 — <strong>0과 미입력은 다른 값</strong>입니다.
    </p>
    <div class="table-scroll">
      <table class="wp">
        <thead>
          <tr>
            <th class="code">WBS</th>
            <th>이름</th>
            <th class="mode">실행 방식</th>
            <th class="num">가중치</th>
            <th class="num">α</th>
            <th class="basis">기준</th>
            <th class="pct">진척</th>
            <th></th>
          </tr>
        </thead>
        <tbody ref="body" class="row-selectable" tabindex="0" @keydown="selection.onKeydown">
          <template v-for="wp in data?.workPackages ?? []" :key="wp.wbsItemId">
            <tr
              :data-row-id="wp.wbsItemId"
              :class="{ open: expanded === wp.wbsItemId, selected: selection.isSelected(wp.wbsItemId) }"
              :aria-selected="selection.isSelected(wp.wbsItemId)"
              @click="selection.select(wp.wbsItemId)"
              @dblclick="
                selection.onRowDblClick($event, () => {
                  // 이 행이 가중치 편집 중이면 더블클릭이 체크포인트 펼치기를 건드리지 않게 한다 —
                  // 입력칸 위 더블클릭은 isDoubleClickGuarded 를 통과하므로 여기서 따로 막는다.
                  if (editingBasis?.wbsItemId !== wp.wbsItemId) toggle(wp)
                })
              "
            >
              <td class="code">{{ wp.code ?? '-' }}</td>
              <td>
                {{ wp.name }}
                <span v-if="wp.acceptancePending" class="chip warn">
                  {{ ACCEPTANCE_STATUS_LABELS.PENDING }}
                </span>
              </td>
              <td class="mode">{{ executionModeLabel(wp.executionMode) }}</td>
              <td class="num">
                <input
                  v-if="editingBasis?.wbsItemId === wp.wbsItemId"
                  v-model.number="editingBasis!.weight"
                  type="number"
                  min="0"
                  class="basis-input"
                  @keydown.enter="saveBasis"
                  @keydown.esc="cancelEditBasis"
                />
                <template v-else>{{ wp.weight ?? '-' }}</template>
              </td>
              <td class="num">
                <input
                  v-if="editingBasis?.wbsItemId === wp.wbsItemId"
                  v-model.number="editingBasis!.agileRatio"
                  type="number"
                  min="0"
                  max="100"
                  :disabled="wp.executionMode !== 'HYBRID'"
                  class="basis-input"
                  @keydown.enter="saveBasis"
                  @keydown.esc="cancelEditBasis"
                />
                <template v-else>
                  {{
                    wp.executionMode !== 'HYBRID'
                      ? '-'
                      : wp.agileRatio === null
                        ? '미정'
                        : `${wp.agileRatio}%`
                  }}
                </template>
              </td>
              <td class="basis">
                <span :title="PROGRESS_BASIS_HINTS[wp.basis]">
                  {{ PROGRESS_BASIS_LABELS[wp.basis] }}
                </span>
              </td>
              <td class="pct">
                <div class="bar" :title="wp.note ?? ''">
                  <div class="fill" :style="{ width: progressBarWidth(wp.percent) }"></div>
                </div>
                <span :class="{ none: wp.percent === null }">{{ progressText(wp.percent) }}</span>
              </td>
              <td class="actions">
                <template v-if="editingBasis?.wbsItemId === wp.wbsItemId">
                  <button type="button" @click="saveBasis">저장</button>
                  <button type="button" class="ghost" @click="cancelEditBasis">취소</button>
                </template>
                <template v-else>
                  <button type="button" @click="toggle(wp)">
                    체크포인트 {{ wp.checkpointApproved }}/{{ wp.checkpointTotal }}
                  </button>
                  <button type="button" class="ghost" @click="startEditBasis(wp)">가중치</button>
                </template>
              </td>
            </tr>
            <tr v-if="expanded === wp.wbsItemId" class="detail">
              <td colspan="8">
                <p v-if="wp.note" class="note cell-clip" :title="wp.note">{{ wp.note }}</p>
                <p v-if="wp.backlogTotal > 0" class="note muted">
                  집계 대상 Story·Bug {{ wp.backlogDone }}/{{ wp.backlogTotal }} 완료
                </p>

                <ul v-if="wp.checkpoints.length > 0" class="checkpoints">
                  <li v-for="cp in wp.checkpoints" :key="cp.id">
                    <span class="cp-title">{{ cp.title }}</span>
                    <span class="cp-weight">가중치 {{ cp.weight ?? '균등' }}</span>
                    <span v-if="cp.completionCriteria" class="cp-criteria">
                      {{ cp.completionCriteria }}
                    </span>
                    <span v-if="cp.approved" class="cp-approved">
                      승인 · {{ cp.approvedBy }} · {{ cp.approvedAt?.slice(0, 10) }}
                    </span>
                    <button
                      v-if="cp.approved"
                      type="button"
                      class="link"
                      @click="setApproval(selectedProjectId!, cp.id, false, null)"
                    >승인 취소</button>
                    <button
                      v-else
                      type="button"
                      class="link"
                      @click="approving = { checkpointId: cp.id }; approver = ''"
                    >승인</button>
                    <button
                      type="button"
                      class="link danger"
                      @click="deleteCheckpoint(selectedProjectId!, cp.id)"
                    >삭제</button>
                  </li>
                </ul>
                <p v-else class="note muted">
                  체크포인트가 없습니다. Waterfall·Hybrid 진척은 이 목록이 분모이므로, 없으면
                  산정 전입니다.
                </p>

                <div class="cp-form">
                  <input v-model="newTitle" type="text" placeholder="체크포인트 제목" />
                  <input v-model.number="newWeight" type="number" min="0" placeholder="가중치" />
                  <input v-model="newCriteria" type="text" placeholder="완료 조건 (선택)" />
                  <button type="button" :disabled="!newTitle.trim()" @click="submitCheckpoint(wp)">
                    추가
                  </button>
                </div>
              </td>
            </tr>
          </template>
        </tbody>
      </table>
    </div>

    <h2>보고 스냅샷</h2>
    <p class="notice subtle">
      진척은 늘 다시 계산되므로 지난 보고서는 재현할 수 없습니다. 보고한 시점의 숫자를 여기에
      적어 둡니다.
    </p>
    <div class="snapshot-form">
      <input v-model="snapshotNote" type="text" placeholder="메모 (선택)" />
      <button type="button" @click="takeSnapshot">현재 진척 저장</button>
    </div>
    <ul v-if="snapshots.length > 0" class="snapshots">
      <li v-for="snapshot in snapshots" :key="snapshot.id">
        <span class="as-of">{{ snapshot.asOf }}</span>
        <span class="figures">{{ snapshotSummary(snapshot.metrics) }}</span>
        <span v-if="snapshot.baselineVersion" class="baseline-ref">
          기준선 v{{ snapshot.baselineVersion }}
        </span>
        <span class="scope-ref">{{ snapshot.scopeItemCount }}개 항목</span>
        <span v-if="snapshot.note" class="snapshot-note">{{ snapshot.note }}</span>
      </li>
    </ul>
    <p v-else class="notice subtle">저장된 스냅샷이 없습니다.</p>
  </template>

<div v-if="approving" class="dialog" role="dialog" aria-modal="true">
  <div class="dialog-body">
    <h4>체크포인트 승인</h4>
    <label>
      승인자
      <input v-model="approver" type="text" placeholder="예: 김재학" />
    </label>
    <p class="explain">로그인이 없어 이름을 직접 적습니다. 승인은 누가 했는지가 핵심입니다.</p>
    <div class="dialog-actions">
      <button type="button" :disabled="!approver.trim()" @click="confirmApproval">승인</button>
      <button type="button" class="ghost" @click="approving = null">취소</button>
    </div>
  </div>
</div>

<div v-if="baselineOpen" class="dialog" role="dialog" aria-modal="true">
  <div class="dialog-body">
    <h4>기준선 승인</h4>
    <p class="explain">
      지금의 범위·일정·가중치·완료 기준을 그대로 복사해 보존합니다. 이후 계획이 바뀌어도 이
      기준선은 변하지 않으며, 계획 진척과 범위 비교의 근거가 됩니다.
    </p>
    <label>
      승인자
      <input v-model="baselineBy" type="text" placeholder="예: 김재학" />
    </label>
    <label>
      메모
      <input v-model="baselineNote" type="text" placeholder="승인 사유 (선택)" />
    </label>
    <div class="dialog-actions">
      <button type="button" :disabled="!baselineBy.trim()" @click="confirmBaseline">승인</button>
      <button type="button" class="ghost" @click="baselineOpen = false">취소</button>
    </div>
  </div>
</div>
  </div>
</template>

<style scoped>

h2 {
  font-size: 1rem;
  margin: 1.5rem 0 0.5rem;
}



select,
input {
  padding: 0.4rem 0.6rem;
  border: 1px solid var(--border-input);
  border-radius: 6px;
  font: inherit;
  font-size: 0.85rem;
}

.reference,
.loading {
  font-size: 0.8rem;
  color: var(--text-faint);
}

.error {
  color: var(--danger);
  font-size: 0.9rem;
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

.summary {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 0.75rem;
  margin-bottom: 0.75rem;
}

.metric {
  border: 1px solid var(--border-soft);
  border-radius: 8px;
  padding: 0.7rem 0.8rem;
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
}

.metric .label {
  font-size: 0.75rem;
  color: var(--text-dim);
}

.metric strong {
  font-size: 1.35rem;
  font-variant-numeric: tabular-nums;
}

/* 산정 전은 숫자가 아니므로 숫자처럼 보이지 않게 한다. */
.metric strong.none {
  font-size: 1rem;
  color: var(--text-faint);
}

.metric .sub {
  font-size: 0.72rem;
  color: var(--text-faint);
}

.scope {
  border: 1px solid var(--border-soft);
  border-radius: 8px;
  padding: 0.75rem 0.9rem;
  margin-top: 1rem;
}

.scope h2 {
  margin: 0 0 0.4rem;
}

.scope-line {
  margin: 0.2rem 0;
  font-size: 0.83rem;
  color: var(--text-muted);
}

.scope-line.muted {
  color: var(--text-faint);
}

.scope-actions {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  margin-top: 0.6rem;
}

.baseline-badge {
  font-size: 0.75rem;
  color: var(--text-faint);
}

.wp {
  width: 100%;
  border-collapse: collapse;
}

.wp th,
.wp td {
  text-align: left;
  padding: 0.45rem 0.6rem;
  border-bottom: 1px solid var(--border-soft);
  font-size: 0.87rem;
  vertical-align: top;
  /* 값이 세로로 접히지 않게 한다 — 넘치면 .table-scroll 이 가로로 넘긴다. */
  white-space: nowrap;
}

.wp th {
  font-size: 0.78rem;
  color: var(--text-dim);
}

.wp .code {
  width: 5rem;
  color: var(--text-dim);
  font-variant-numeric: tabular-nums;
}

.wp .mode {
  width: 8rem;
  font-size: 0.8rem;
  color: var(--text-muted);
}

.wp .num {
  width: 4.5rem;
  text-align: right;
  font-variant-numeric: tabular-nums;
  color: var(--text-muted);
}

/* 가중치·α 인라인 편집 입력칸 — 열 폭에 맞춰 좁게, 숫자 칸이므로 오른쪽 정렬. */
.basis-input {
  width: 100%;
  padding: 0.25rem 0.35rem;
  text-align: right;
}

.basis-input:disabled {
  background: var(--disabled-bg);
  color: var(--disabled-fg);
}

.wp .basis {
  width: 6.5rem;
  font-size: 0.78rem;
  color: var(--text-muted);
}

.wp .pct {
  width: 8rem;
  white-space: nowrap;
}

.wp .pct .none {
  color: var(--text-faint);
  font-size: 0.8rem;
}

.bar {
  display: inline-block;
  vertical-align: middle;
  width: 4rem;
  height: 0.4rem;
  border-radius: 999px;
  background: var(--border-soft);
  overflow: hidden;
  margin-right: 0.4rem;
}

.fill {
  height: 100%;
  background: var(--accent);
}

.wp .actions {
  text-align: right;
  white-space: nowrap;
}

.wp .actions button + button {
  margin-left: 0.35rem;
}

tr.detail > td {
  background: var(--surface-sunken);
}

.note {
  margin: 0.2rem 0;
  font-size: 0.8rem;
  color: var(--warn-badge-fg);
}

.note.muted {
  color: var(--text-faint);
}

.checkpoints {
  list-style: none;
  margin: 0.4rem 0;
  padding: 0;
}

.checkpoints li {
  display: flex;
  align-items: baseline;
  gap: 0.5rem;
  flex-wrap: wrap;
  padding: 0.2rem 0;
  font-size: 0.82rem;
}

.cp-title {
  font-weight: 600;
}

.cp-weight,
.cp-criteria,
.cp-approved {
  font-size: 0.75rem;
  color: var(--text-faint);
}

.cp-approved {
  color: var(--accent);
}

.cp-form,
.snapshot-form {
  display: flex;
  gap: 0.4rem;
  margin-top: 0.5rem;
  flex-wrap: wrap;
}

.cp-form input:first-child,
.snapshot-form input {
  flex: 1;
  min-width: 8rem;
}

.snapshots {
  list-style: none;
  margin: 0.5rem 0 0;
  padding: 0;
}

.snapshots li {
  display: flex;
  gap: 0.6rem;
  align-items: baseline;
  flex-wrap: wrap;
  padding: 0.3rem 0;
  border-bottom: 1px solid var(--border-soft);
  font-size: 0.83rem;
}

.snapshots .as-of {
  font-variant-numeric: tabular-nums;
  font-weight: 600;
}

.snapshots .baseline-ref,
.snapshots .scope-ref,
.snapshots .snapshot-note {
  font-size: 0.76rem;
  color: var(--text-faint);
}

.chip {
  font-size: 0.68rem;
  padding: 0.05rem 0.4rem;
  border-radius: 999px;
  white-space: nowrap;
}

.chip.warn {
  background: var(--warn);
  color: var(--status-fg);
}

button {
  padding: 0.35rem 0.7rem;
  border-radius: 6px;
  border: 1px solid var(--accent);
  background: var(--accent);
  color: var(--accent-fg);
  cursor: pointer;
  font: inherit;
  font-size: 0.8rem;
  white-space: nowrap;
}

button.ghost {
  background: transparent;
  color: var(--text-muted);
  border-color: var(--border-input);
}

button:disabled {
  background: var(--disabled-bg);
  color: var(--disabled-fg);
  border-color: var(--border-soft);
  cursor: not-allowed;
}

button.link {
  border: none;
  background: none;
  padding: 0;
  color: var(--accent);
  font-size: 0.76rem;
}

button.link.danger {
  color: var(--danger);
}

.dialog {
  position: fixed;
  inset: 0;
  background: rgb(0 0 0 / 45%);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 50;
}

.dialog-body {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 10px;
  padding: 1.1rem 1.25rem;
  max-width: 28rem;
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.dialog-body h4 {
  margin: 0;
  font-size: 0.95rem;
}

.dialog-body label {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  font-size: 0.82rem;
  color: var(--text-muted);
}

.dialog-body .explain {
  margin: 0;
  font-size: 0.8rem;
  color: var(--text-faint);
}

.dialog-actions {
  display: flex;
  gap: 0.5rem;
  margin-top: 0.4rem;
}

@media (max-width: 860px) {
  .summary {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
