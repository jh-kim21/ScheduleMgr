<script lang="ts">
import { ref as moduleRef } from 'vue'

/**
 * 마지막으로 입력한 승인자. **모듈 스코프**라 이 컴포넌트의 모든 인스턴스(WBS 트리에서 펼친
 * Work Package마다 하나씩 생긴다)가 같은 값을 본다 — 한 사람이 한 자리에서 여러 체크포인트를
 * 연달아 승인하는 것이 이 화면의 주 사용 패턴인데, 인스턴스마다 따로 기억하면 행을 옮길 때마다
 * 이름을 다시 쳐야 해서 고친 것이 무의미해진다. `useProgress`가 모듈 스코프 상태를 쓰는 것과
 * 같은 방식이고, `<script setup>`의 최상위는 인스턴스마다 다시 실행되므로 여기 둬야 한다.
 *
 * 한 세션까지만 기억한다 — `localStorage`에 남기지 않는다(지시서 6-6). 새로 고치면 지워지는
 * 편이 "다른 사람이 같은 브라우저를 쓴다"는 경우에 안전하다.
 */
const lastApprover = moduleRef('')
</script>

<script setup lang="ts">
import { nextTick, ref, type ComponentPublicInstance } from 'vue'
import type { CheckpointDetail } from '../../api/progressApi'
import { useProgress } from './useProgress'
import {
  draftFrom,
  emptyDraft,
  isSubmittable,
  toCheckpointInput,
  type CheckpointDraft,
} from './checkpointForm'

/**
 * 승인 체크포인트 표시·편집. `WbsTree`(정의: 추가·수정·삭제·승인, `editable: true` — Work Package
 * 행을 펼쳐서 다룬다)와 `ProgressPanel`(조망: 읽기 전용, `editable: false`)이 함께 쓴다.
 *
 * 목록 자체는 부모가 넘긴다(`checkpoints`) — 부모마다 그 항목을 어디서 찾는지가 다르기 때문이다
 * (WbsTree는 펼친 Work Package 하나, ProgressPanel은 펼친 행 하나). 변경은 여전히 `useProgress()`
 * (모듈 스코프 공유 상태)로 하므로, 한쪽에서 바꾸면 다른 화면이 다음 방문에 같은 데이터를 본다.
 *
 * 패널 크롬(테두리·큰 여백)은 두지 않는다 — 자리는 부모가 정한다.
 */
const props = defineProps<{
  projectId: number
  wbsItemId: number
  checkpoints: CheckpointDetail[]
  /** false면 아무 버튼도 폼도 렌더링하지 않는다(숨김이 아니라 없음) — 제목·가중치·완료조건·승인
   * 상태(+승인자·승인일)만 표시하는 순수 조회 모드다. */
  editable: boolean
}>()

const { error, addCheckpoint, updateCheckpoint, setApproval, deleteCheckpoint } = useProgress()

/**
 * 읽기 전용 잠금은 호출자가 `editable`로 건다 — 여기서 `readOnly`를 다시 보지 않는다. `WbsTree`가
 * `:editable="!readOnly"`로 넘기므로 커밋 조회 중에는 이 컴포넌트의 버튼·폼 전체가 이미
 * 렌더링되지 않는다. 여기서 두 번째 게이트(`:disabled="readOnly"`)를 만들면 "빠짐없이 잠갔다"를
 * 확인할 자리가 둘이 된다(CLAUDE.md "커밋 조회 중에는 … 플래그를 하나로 묶어야 한다").
 */

/** `null`이면 추가 모드, 아니면 그 id의 체크포인트를 고치는 중. */
const editingId = ref<number | null>(null)
const draft = ref<CheckpointDraft>(emptyDraft())

/**
 * 추가/수정 폼(`.cp-form`)이 펼쳐져 있는지. 기본은 접힘 — 트리 안에 인라인으로 들어가면서 항상
 * 펼쳐 두면 거슬린다. `editingId`가 있어도(수정 모드) 이 값이 `true`여야 폼이 보인다 —
 * `startEdit`이 함께 열어 준다.
 */
const formOpen = ref(false)

/** 폼의 첫 입력칸 — 열릴 때 포커스를 옮겨 준다. */
const titleInput = ref<HTMLInputElement | null>(null)

async function focusTitleInput() {
  await nextTick()
  titleInput.value?.focus()
}

/** 지금 인라인 승인 입력이 펼쳐진 체크포인트. 대화상자를 쓰지 않는 이유는 WbsTree가 이미
 * 트리 안이라서다(오버레이 중첩 금지). */
const approvingId = ref<number | null>(null)
const approver = ref('')

/**
 * 지금 펼쳐진 승인 입력칸. `v-for` 안에 있으므로 문자열 ref를 쓰면 Vue가 배열로 모아 "지금 열린
 * 하나"를 집기 번거롭다 — 함수 ref로 받아 항상 하나만 들고 있는다(`v-if` 덕분에 한 번에 하나만
 * 렌더링된다).
 */
const approverInput = ref<HTMLInputElement | null>(null)

function setApproverInput(el: Element | ComponentPublicInstance | null) {
  approverInput.value = el instanceof HTMLInputElement ? el : null
}

/**
 * `v-model.number`는 빈 칸을 `null`이 아니라 빈 문자열로, 편집 중인 "-"·"."는 `NaN`으로 남긴다.
 * 서버는 둘 다 받지 못하므로 여기서 걸러 `null`로 바꾼다 — `0`은 그대로 통과시킨다(0과 미입력은
 * 다른 값이다).
 */
function normalizeWeight(value: number | string | null): number | null {
  if (value === '' || value === null || Number.isNaN(value as number)) return null
  return value as number
}

/** ＋ 체크포인트 추가 버튼. 목록이 비어 있어도 눌러 바로 첫 체크포인트를 만들 수 있다. */
function openAddForm() {
  editingId.value = null
  draft.value = emptyDraft()
  formOpen.value = true
  focusTitleInput()
}

function startEdit(cp: CheckpointDetail) {
  editingId.value = cp.id
  draft.value = draftFrom(cp)
  formOpen.value = true
  focusTitleInput()
}

/** 추가·수정 모두에서 쓰는 닫기 — 폼을 접고 입력값을 비운다. */
function closeForm() {
  editingId.value = null
  draft.value = emptyDraft()
  formOpen.value = false
}

async function submit() {
  if (!isSubmittable(draft.value)) return
  const wasAdding = editingId.value === null
  const input = toCheckpointInput(props.wbsItemId, {
    ...draft.value,
    weight: normalizeWeight(draft.value.weight),
  })
  const ok = wasAdding
    ? await addCheckpoint(props.projectId, input)
    : await updateCheckpoint(props.projectId, editingId.value!, input)
  // 거부되면 입력값을 그대로 남기고 폼도 열어 둔다 — 위의 `error`가 이유를 설명한다
  // (CLAUDE.md 화면 규칙).
  if (!ok) return
  if (wasAdding) {
    // 추가 후에는 열어 둔다 — RAID 입력 패널과 같은 규칙이다(CLAUDE.md RAID 설계:
    // "수정 저장 후에는 닫고, 추가 후에는 열어 둡니다 — 여러 건을 연달아 기록하는 것이 흔하고,
    // 아래 표에 새 행이 나타나는 것이 이미 확인 신호입니다"). Work Package를 Waterfall로 바꾼
    // 직후 체크포인트를 3~5개 연달아 넣는 것이 이 화면의 가장 흔한 사용이라, 한 건마다
    // ＋ 버튼을 다시 누르게 하면 안 된다.
    draft.value = emptyDraft()
    focusTitleInput()
  } else {
    // 수정 저장 후에는 닫는다 — 고칠 항목은 목록에서 다시 골라야 하므로 열어 둘 이유가 없다.
    closeForm()
  }
}

/**
 * 승인 입력을 편다. 마지막 승인자를 미리 채우되 **전체 선택 상태로 포커스**한다 — 대부분은 같은
 * 사람이 연달아 승인하므로 그대로 Enter를 치면 되고, 다른 사람이면 첫 글자를 치는 순간 통째로
 * 덮인다. 커서만 끝에 두면 매번 지우는 손이 한 번 더 든다.
 */
async function startApprove(cp: CheckpointDetail) {
  approvingId.value = cp.id
  approver.value = lastApprover.value
  await nextTick()
  approverInput.value?.focus()
  approverInput.value?.select()
}

function cancelApprove() {
  approvingId.value = null
  approver.value = ''
}

async function confirmApprove() {
  if (!approver.value.trim() || approvingId.value === null) return
  const name = approver.value.trim()
  const ok = await setApproval(props.projectId, approvingId.value, true, name)
  // 성공한 이름만 기억한다 — 거부된 입력을 다음 승인에 미리 채워 주면 같은 실패를 되풀이하게 된다.
  if (ok) {
    lastApprover.value = name
    cancelApprove()
  }
}

function revoke(cp: CheckpointDetail) {
  setApproval(props.projectId, cp.id, false, null)
}

/**
 * 승인된 체크포인트는 삭제로 진척 숫자(Waterfall·Hybrid의 분모)가 즉시 바뀐다 — 그 사실을 문구에
 * 담아 확인을 한 번 더 받는다. 다른 확인 문구(`WbsView.vue`의 삭제, `MemberEditor.vue`의 구성원
 * 삭제)와 같은 어투: `"제목" …을 삭제할까요?` 뒤에 부작용을 줄바꿈으로 덧붙인다.
 */
function remove(cp: CheckpointDetail) {
  const warning = cp.approved
    ? '\n이미 승인되어 있어 지우면 이 Work Package의 진척 숫자가 즉시 바뀝니다.'
    : ''
  if (!confirm(`"${cp.title}" 체크포인트를 삭제할까요?${warning}`)) return
  if (approvingId.value === cp.id) cancelApprove()
  if (editingId.value === cp.id) closeForm()
  deleteCheckpoint(props.projectId, cp.id)
}
</script>

<template>
  <div class="checkpoint-list">
    <p v-if="error" class="error">{{ error }}</p>

    <ul v-if="checkpoints.length > 0" class="checkpoints">
      <li v-for="cp in checkpoints" :key="cp.id">
        <div class="row">
          <span class="cp-title">{{ cp.title }}</span>
          <!--
            미입력(`null`)은 "균등"이 아니라 **1**이다 — `ProgressCalculator.weightOf`가 1로
            폴백하므로 `[30, 30, 40, null]`에서 마지막 하나는 1/101을 갖는다. 예전 "균등" 표시는
            형제 중 하나라도 값이 있으면 거짓이었다.
          -->
          <span class="cp-weight">가중치 {{ cp.weight ?? 1 }}</span>
          <span v-if="cp.completionCriteria" class="cp-criteria">{{ cp.completionCriteria }}</span>
          <span v-if="cp.approved" class="cp-approved">
            승인 완료 · {{ cp.approvedBy }} · {{ cp.approvedAt?.slice(0, 10) }}
          </span>
          <span v-else class="cp-pending">미승인</span>
          <span v-if="editable" class="cp-actions">
            <button
              v-if="cp.approved"
              type="button"
              @click="revoke(cp)"
            >승인 취소</button>
            <button
              v-else-if="approvingId !== cp.id"
              type="button"
              class="primary"
              @click="startApprove(cp)"
            >승인</button>
            <button type="button" @click="startEdit(cp)">수정</button>
            <button type="button" class="danger" @click="remove(cp)">삭제</button>
          </span>
        </div>
        <!-- 인라인 승인 입력 — 부모(WbsTree)가 이미 트리 안이라 여기서 또 오버레이를 띄우면
             레이어가 겹친다. -->
        <div v-if="editable && approvingId === cp.id" class="approve-row">
          <input
            :ref="setApproverInput"
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
    <p v-else-if="editable" class="notice muted">
      체크포인트가 없습니다. Waterfall·Hybrid 진척은 이 목록이 분모이므로, 없으면 산정 전입니다.
    </p>
    <p v-else class="notice muted">
      체크포인트가 없습니다. WBS 화면에서 해당 업무를 펼쳐 등록하세요. Waterfall·Hybrid 진척은
      이 목록이 분모이므로, 없으면 산정 전입니다.
    </p>

    <!-- 목록이 비어 있어도 이 버튼은 그대로 보인다 — 그 자리에서 바로 첫 체크포인트를 추가할 수
         있어야 한다. -->
    <button
      v-if="editable && !formOpen"
      type="button"
      class="add"
      @click="openAddForm"
    >＋ 체크포인트 추가</button>

    <!--
      두 글자 칸 어디서든 Enter로 저장되고 Esc로 닫힌다 — 승인 입력(위)에만 있던 핸들러라
      비대칭이었다. `<form>`이 아니라 `<div>`라서(트리 행 안에 들어가므로 중첩 폼을 만들 수 없다)
      브라우저의 기본 submit이 없고, 그래서 이 핸들러가 곧 Enter 저장의 전부다. 가중치 칸은
      `type="number"`라 Enter를 눌러도 브라우저 동작이 없어 같은 핸들러를 달아 둔다.
    -->
    <div v-if="editable && formOpen" class="cp-form">
      <input
        ref="titleInput"
        v-model="draft.title"
        type="text"
        class="cp-title-input"
        placeholder="체크포인트 제목"
        @keydown.enter="isSubmittable(draft) && submit()"
        @keydown.esc="closeForm"
      />
      <input
        v-model.number="draft.weight"
        type="number"
        min="0"
        class="cp-weight-input"
        placeholder="가중치"
        @keydown.enter="isSubmittable(draft) && submit()"
        @keydown.esc="closeForm"
      />
      <input
        v-model="draft.criteria"
        type="text"
        class="cp-criteria-input"
        placeholder="완료 조건 (선택)"
        @keydown.enter="isSubmittable(draft) && submit()"
        @keydown.esc="closeForm"
      />
      <button type="button" :disabled="!isSubmittable(draft)" @click="submit">
        {{ editingId === null ? '추가' : '저장' }}
      </button>
      <button type="button" class="ghost" @click="closeForm">취소</button>
    </div>
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

/*
 * 상태 칩 — 눌러서 바뀌는 것이 아니라 지금 상태를 보여주기만 한다(지시서 "승인 — 상태는 칩,
 * 동작은 버튼"). 크기·모서리는 이 저장소의 기존 칩(BacklogList `.chip`, ProgressPanel `.chip`)과
 * 같은 계열이다. `cursor: default`로 눌러도 아무 일도 일어나지 않음을 드러내고, `<span>`이라
 * hover/focus 스타일 자체가 없다 — 클릭 핸들러를 붙이면 안 된다(테스트가 이를 고정한다).
 */
.cp-approved,
.cp-pending {
  font-size: 0.68rem;
  padding: 0.05rem 0.4rem;
  border-radius: 999px;
  white-space: nowrap;
  cursor: default;
}

.cp-approved {
  background: var(--success-weak);
  color: var(--success-text);
}

.cp-pending {
  background: var(--badge-neutral-bg);
  color: var(--badge-neutral-fg);
}

/* 승인/승인 취소·수정·삭제 — 행 안에 들어가는 작은 버튼이라 화면을 압도하면 안 된다. 다른
 * 표의 행 액션(BacklogList·RaidList의 `.actions button`)과 같은 모양·크기 계열로 맞췄다. */
.cp-actions {
  display: flex;
  gap: 0.3rem;
  margin-left: auto;
}

/* border·배경·font·cursor는 전역 기본과 같아 지웠다 — 이 칸만의 작은 크기만 남는다. */
.cp-actions button {
  overflow: hidden;
  padding: 0.2rem 0.5rem;
  color: var(--text-muted);
  font-size: 0.72rem;
  white-space: nowrap;
}

/* 전역 ::before가 position·배경을 이미 주므로, 이 버튼만의 트랜지션만 얹는다. `:hover`/`:active`
 * 오버레이 재선언은 전역과 값이 완전히 같아 지웠다(특이도 동점이라 소스 순서로 이겨 왔을 뿐이다). */
.cp-actions button::before {
  transition: background 120ms ease;
}

/* 「승인」만 옅은 강조 채움(filled tonal) — 전역 `.tonal`과 같은 배색이지만 템플릿의 클래스
 * 이름은 `.primary`라 로컬에 남긴다. 「승인 취소」·「수정」·「삭제」는 기존 테두리 모양을 유지한다. */
.cp-actions button.primary {
  background: var(--accent-container);
  color: var(--accent-container-fg);
  border-color: transparent;
}

/* `.danger`는 전역 계약과 완전히 같아 지웠다. :disabled는 border-color만 이 칸 고유 값(옅은
 * --border-soft)이다 — 배경·글자색·cursor는 특이도 동점에서도 전역과 같은 값을 쓰고 있었다. */
.cp-actions button:disabled {
  border-color: var(--border-soft);
}

.approve-row {
  display: flex;
  gap: 0.4rem;
  margin: 0.25rem 0 0.4rem;
}

/* 패딩·border·radius·font는 전역 기본과 같아(패딩은 아주 살짝만 다름) 지웠다. */
.approve-row input {
  flex: 1;
  min-width: 8rem;
  font-size: 0.82rem;
}

.cp-form {
  display: flex;
  gap: 0.4rem;
  flex-wrap: wrap;
}

.cp-form input {
  font-size: 0.82rem;
}

/* 폼은 [제목][가중치][완료조건][추가][취소] 다섯 칸이다. 글자 칸 둘이 남는 폭을 나눠 갖고,
 * 가중치는 숫자 한두 자리라 좁게 고정한다. 위치(`:first-child`)가 아니라 이름으로 잡는다 —
 * 칸 순서가 바뀌어도 폭이 엉키지 않는다. */
.cp-form .cp-title-input,
.cp-form .cp-criteria-input {
  flex: 1;
  min-width: 8rem;
}

.cp-form .cp-weight-input {
  flex: 0 0 auto;
  width: 5.5rem;
}

/* 컨테이너(.checkpoint-list)가 flex-column이라 align-items 기본값(stretch)을 그대로 두면 이
 * 버튼이 전체 폭으로 늘어난다 — 다른 화면의 "＋ … 추가" 버튼처럼 내용 크기만큼만 차지해야 한다. */
.add {
  align-self: flex-start;
}

/* radius·font·cursor는 전역 기본과 같아 지웠다 — 취소(.ghost)는 전역 계약과 완전히 같아
   통째로 지웠고, :disabled도 전역이 특이도로 이미 이기고 있던 죽은 선언이라 지웠다. */
button {
  padding: 0.3rem 0.6rem;
  border-color: var(--accent);
  background: var(--accent);
  color: var(--accent-fg);
  font-size: 0.78rem;
  white-space: nowrap;
}

/* 이 파일이 직접 들여오는 transition(승인/수정/삭제 버튼의 상태 레이어 배경)만 끈다 — 전역
   컨트롤 층의 transition은 style.css가 이미 막는다. */
@media (prefers-reduced-motion: reduce) {
  .cp-actions button::before {
    transition: none;
  }
}
</style>
