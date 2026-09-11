<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { CheckpointDetail } from '../../api/progressApi'
import { useProgress } from './useProgress'

/**
 * 승인 체크포인트 편집. `WbsForm`(항목을 저장하는 곳)과 `ProgressPanel`(진척 탭)이 함께 쓴다 —
 * 부모가 데이터를 넘기지 않고 이 컴포넌트가 직접 `useProgress()`를 읽고 쓴다. `useProgress`는
 * 모듈 스코프 공유 상태라, 어느 화면에서 불러도 같은 인스턴스를 본다(CLAUDE.md "모든 화면이 이
 * 서비스 하나를 읽습니다").
 *
 * 패널 크롬(테두리·큰 여백)은 두지 않는다 — 자리는 부모가 정한다.
 */
const props = defineProps<{
  projectId: number
  wbsItemId: number
}>()

const { data, loading, error, ensureLoaded, addCheckpoint, setApproval, deleteCheckpoint } = useProgress()

watch(
  () => props.projectId,
  (id) => {
    ensureLoaded(id)
  },
  { immediate: true },
)

/**
 * `data.value.workPackages`에서 이 항목을 찾는다. 없을 수 있는 경우:
 * - 아직 불러오는 중(`loading`).
 * - 이 항목이 Summary라서 애초에 집계 대상이 아님(체크포인트 목록에는 Work Package만 실린다).
 * - 방금 Work Package로 전환했는데 아직 다시 읽지 않음.
 * 세 경우 모두 사용자에게 "왜 안 보이는지"를 말해야 하므로, 조용히 빈 화면을 보여주지 않는다.
 */
const workPackage = computed(
  () => data.value?.workPackages.find((wp) => wp.wbsItemId === props.wbsItemId) ?? null,
)

const newTitle = ref('')
const newWeight = ref<number | null>(null)
const newCriteria = ref('')

/** 지금 인라인 승인 입력이 펼쳐진 체크포인트. 대화상자를 쓰지 않는 이유는 위 설계 결정 1 참고. */
const approvingId = ref<number | null>(null)
const approver = ref('')

async function submit() {
  if (!newTitle.value.trim()) return
  const ok = await addCheckpoint(props.projectId, {
    wbsItemId: props.wbsItemId,
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

function startApprove(cp: CheckpointDetail) {
  approvingId.value = cp.id
  approver.value = ''
}

function cancelApprove() {
  approvingId.value = null
  approver.value = ''
}

async function confirmApprove() {
  if (!approver.value.trim() || approvingId.value === null) return
  const ok = await setApproval(props.projectId, approvingId.value, true, approver.value.trim())
  if (ok) cancelApprove()
}

function revoke(cp: CheckpointDetail) {
  setApproval(props.projectId, cp.id, false, null)
}

function remove(cp: CheckpointDetail) {
  if (approvingId.value === cp.id) cancelApprove()
  deleteCheckpoint(props.projectId, cp.id)
}
</script>

<template>
  <div class="checkpoint-list">
    <p v-if="loading && !workPackage" class="notice muted">불러오는 중…</p>
    <p v-else-if="!workPackage" class="notice">
      이 항목의 진척 정보를 찾을 수 없습니다. WBS 화면을 새로고침한 뒤 다시 시도하세요.
    </p>
    <template v-else>
      <p v-if="error" class="error">{{ error }}</p>

      <ul v-if="workPackage.checkpoints.length > 0" class="checkpoints">
        <li v-for="cp in workPackage.checkpoints" :key="cp.id">
          <div class="row">
            <span class="cp-title">{{ cp.title }}</span>
            <span class="cp-weight">가중치 {{ cp.weight ?? '균등' }}</span>
            <span v-if="cp.completionCriteria" class="cp-criteria">{{ cp.completionCriteria }}</span>
            <span v-if="cp.approved" class="cp-approved">
              승인 · {{ cp.approvedBy }} · {{ cp.approvedAt?.slice(0, 10) }}
            </span>
            <button v-if="cp.approved" type="button" class="link" @click="revoke(cp)">승인 취소</button>
            <button
              v-else-if="approvingId !== cp.id"
              type="button"
              class="link"
              @click="startApprove(cp)"
            >승인</button>
            <button type="button" class="link danger" @click="remove(cp)">삭제</button>
          </div>
          <!-- 인라인 승인 입력 — 부모(WbsForm)가 이미 ModalDialog 안이라 여기서 또 오버레이를 띄우면
               대화상자 위에 대화상자가 겹친다(설계 결정 1). -->
          <div v-if="approvingId === cp.id" class="approve-row">
            <input
              v-model="approver"
              type="text"
              placeholder="승인자 (예: 김재학)"
              @keydown.enter="confirmApprove"
              @keydown.esc="cancelApprove"
            />
            <button type="button" :disabled="!approver.trim()" @click="confirmApprove">확인</button>
            <button type="button" class="ghost" @click="cancelApprove">취소</button>
          </div>
        </li>
      </ul>
      <p v-else class="notice muted">
        체크포인트가 없습니다. Waterfall·Hybrid 진척은 이 목록이 분모이므로, 없으면 산정 전입니다.
      </p>

      <div class="cp-form">
        <input v-model="newTitle" type="text" placeholder="체크포인트 제목" />
        <input v-model.number="newWeight" type="number" min="0" placeholder="가중치" />
        <input v-model="newCriteria" type="text" placeholder="완료 조건 (선택)" />
        <button type="button" :disabled="!newTitle.trim()" @click="submit">추가</button>
      </div>
    </template>
  </div>
</template>

<style scoped>
.checkpoint-list {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.notice {
  font-size: 0.8rem;
  color: var(--warn-badge-fg);
}

.notice.muted {
  color: var(--text-faint);
}

.error {
  font-size: 0.82rem;
  color: var(--danger);
}

.checkpoints {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
}

.checkpoints .row {
  display: flex;
  align-items: baseline;
  gap: 0.5rem;
  flex-wrap: wrap;
  font-size: 0.82rem;
}

.cp-title {
  font-weight: 600;
}

.cp-weight,
.cp-criteria {
  font-size: 0.75rem;
  color: var(--text-faint);
}

.cp-approved {
  font-size: 0.75rem;
  color: var(--accent);
}

.approve-row {
  display: flex;
  gap: 0.4rem;
  margin: 0.25rem 0 0.4rem;
}

.approve-row input {
  flex: 1;
  min-width: 8rem;
  padding: 0.35rem 0.5rem;
  border: 1px solid var(--border-input);
  border-radius: 6px;
  font: inherit;
  font-size: 0.82rem;
}

.cp-form {
  display: flex;
  gap: 0.4rem;
  flex-wrap: wrap;
}

.cp-form input {
  padding: 0.35rem 0.5rem;
  border: 1px solid var(--border-input);
  border-radius: 6px;
  font: inherit;
  font-size: 0.82rem;
}

.cp-form input:first-child {
  flex: 1;
  min-width: 8rem;
}

button {
  padding: 0.3rem 0.6rem;
  border-radius: 6px;
  border: 1px solid var(--accent);
  background: var(--accent);
  color: var(--accent-fg);
  cursor: pointer;
  font: inherit;
  font-size: 0.78rem;
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
</style>
