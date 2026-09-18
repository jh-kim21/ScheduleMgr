<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  CommitCapacityExceededError,
  type CommitCapacity,
  type CommitInput,
  type CommitMeta,
} from '../../api/commitApi'
import { ApiError } from '../../api/http'
import type { Project } from '../../api/projectApi'
import ModalDialog from '../../components/ModalDialog.vue'
import { enterCommitView } from '../../stores/commitView'
import CommitCapacityDialog from './CommitCapacityDialog.vue'
import CommitForm from './CommitForm.vue'
import { formatBytes, formatCapacityUsage } from './commitFormat'
import { useCommits } from './useCommits'

/**
 * 커밋 히스토리 대화상자. `MemberEditor`와 같은 자리다 — 전역 `selectedProjectId`가 아니라
 * `ProjectList`에서 방금 누른 프로젝트를 그대로 받는다(이 화면은 목록 화면이라 "지금 선택된
 * 프로젝트"라는 개념이 없다).
 */
const props = defineProps<{
  project: Project
}>()

const emit = defineEmits<{
  close: []
  /** 복원으로 새 프로젝트가 생겼음을 알려, 부모가 프로젝트 목록을 다시 읽게 한다. */
  restored: [project: Project]
}>()

const { commits, capacity, loading, error, ensureLoaded, create, remove, view, restore } =
  useCommits()
ensureLoaded(props.project.id)

const title = computed(() => `커밋 히스토리 — ${props.project.name}`)
const gaugeLabel = computed(() => (capacity.value ? formatCapacityUsage(capacity.value) : null))

const formOpen = ref(false)
const saveError = ref<string | null>(null)

/** 409로 거부된 입력을 들고 있다가, 대화상자에서 지운 뒤 같은 내용으로 다시 시도한다. */
const pendingInput = ref<CommitInput | null>(null)
const capacityDialog = ref<{ capacity: CommitCapacity; commits: CommitMeta[] } | null>(null)
const capacityDialogError = ref<string | null>(null)
const capacityBusyVersion = ref<number | null>(null)
const retrying = ref(false)

/** 행별 동작(보기·내보내기·복원·삭제) 진행 상태와 오류. 목록 로딩 오류와 섞이지 않게 따로 둔다. */
const actionError = ref<string | null>(null)
const viewingVersion = ref<number | null>(null)
const restoringVersion = ref<number | null>(null)
const removingVersion = ref<number | null>(null)
const restoredName = ref<string | null>(null)

const router = useRouter()

function openForm() {
  saveError.value = null
  formOpen.value = true
}

function closeForm() {
  formOpen.value = false
}

/** 성공하면 true. 409는 예외로 던지지 않고 호출자가 대화상자를 띄우도록 안내만 한다. */
async function submitCommit(input: CommitInput): Promise<boolean> {
  try {
    await create(props.project.id, input)
    return true
  } catch (e) {
    if (e instanceof CommitCapacityExceededError) {
      pendingInput.value = input
      capacityDialog.value = { capacity: e.capacity, commits: e.commits }
      capacityDialogError.value = null
      return false
    }
    throw e
  }
}

async function handleSubmit(input: CommitInput) {
  saveError.value = null
  try {
    const ok = await submitCommit(input)
    if (ok) closeForm()
  } catch (e) {
    saveError.value = e instanceof ApiError ? e.message : '커밋을 저장하지 못했습니다.'
  }
}

function closeCapacityDialog() {
  capacityDialog.value = null
  capacityDialogError.value = null
  pendingInput.value = null
}

async function handleCapacityRemove(version: number) {
  capacityDialogError.value = null
  capacityBusyVersion.value = version
  try {
    await remove(props.project.id, version)
    // 지운 뒤의 최신 목록·용량을 대화상자에 그대로 반영한다.
    if (capacity.value) {
      capacityDialog.value = { capacity: capacity.value, commits: commits.value }
    }
  } catch (e) {
    capacityDialogError.value = e instanceof ApiError ? e.message : '삭제하지 못했습니다.'
  } finally {
    capacityBusyVersion.value = null
  }
}

async function handleCapacityRetry() {
  if (!pendingInput.value) return
  capacityDialogError.value = null
  retrying.value = true
  try {
    const ok = await submitCommit(pendingInput.value)
    if (ok) {
      closeCapacityDialog()
      closeForm()
    }
  } catch (e) {
    capacityDialogError.value = e instanceof ApiError ? e.message : '다시 시도하지 못했습니다.'
  } finally {
    retrying.value = false
  }
}

async function handleView(commit: CommitMeta) {
  actionError.value = null
  viewingVersion.value = commit.version
  try {
    const { commit: meta, payload } = await view(props.project.id, commit.version)
    enterCommitView(
      {
        projectId: props.project.id,
        id: meta.id,
        version: meta.version,
        asOf: meta.asOf,
        message: meta.message,
        committedBy: meta.committedBy,
        formatVersion: meta.formatVersion,
      },
      payload,
    )
    emit('close')
    // 대시보드가 전 화면을 모아 보여주는 자리라 시점 확인의 첫 화면으로 적당하다.
    router.push('/dashboard')
  } catch (e) {
    actionError.value = e instanceof ApiError ? e.message : '커밋을 열지 못했습니다.'
  } finally {
    viewingVersion.value = null
  }
}

function handleExport(commit: CommitMeta) {
  window.location.href = `/api/projects/${props.project.id}/commits/${commit.version}/export`
}

async function handleRestore(commit: CommitMeta) {
  if (!confirm(`v${commit.version} (${commit.asOf}) 시점으로 새 프로젝트를 만들까요?`)) return
  actionError.value = null
  restoredName.value = null
  restoringVersion.value = commit.version
  try {
    const created = await restore(commit.id)
    restoredName.value = created.name
    emit('restored', created)
  } catch (e) {
    actionError.value = e instanceof ApiError ? e.message : '복원하지 못했습니다.'
  } finally {
    restoringVersion.value = null
  }
}

async function handleRemove(commit: CommitMeta) {
  if (!confirm(`v${commit.version} 커밋을 삭제할까요? 되돌릴 수 없습니다.`)) return
  actionError.value = null
  removingVersion.value = commit.version
  try {
    await remove(props.project.id, commit.version)
  } catch (e) {
    actionError.value = e instanceof ApiError ? e.message : '삭제하지 못했습니다.'
  } finally {
    removingVersion.value = null
  }
}
</script>

<template>
  <ModalDialog :title="title" size="lg" :error="error" @close="emit('close')">
    <section class="commits">
      <header class="section-head">
        <p class="rule">
          그 시점의 WBS·간트·RACI·RAID·Backlog·Sprint·진척·대시보드를 판정값까지 그대로 저장합니다.
          계산 로직이 바뀌어도 이 숫자는 바뀌지 않습니다. 커밋은 수정할 수 없고 삭제만 가능합니다.
        </p>
        <button type="button" class="add" @click="openForm">＋ 현재 시점 커밋</button>
      </header>

      <p v-if="gaugeLabel" class="gauge" :class="{ warn: capacity?.warning }">
        {{ gaugeLabel }}
      </p>

      <CommitForm v-if="formOpen" :error="saveError" @submit="handleSubmit" @cancel="closeForm" />

      <CommitCapacityDialog
        v-if="capacityDialog"
        :capacity="capacityDialog.capacity"
        :commits="capacityDialog.commits"
        :busy-version="capacityBusyVersion"
        :retrying="retrying"
        :error="capacityDialogError"
        @remove="handleCapacityRemove"
        @retry="handleCapacityRetry"
        @close="closeCapacityDialog"
      />

      <p v-if="actionError" class="error">{{ actionError }}</p>
      <p v-if="restoredName" class="ok">
        <strong>{{ restoredName }}</strong> 프로젝트를 만들었습니다. 프로젝트 목록에서 확인하세요.
      </p>

      <p v-if="loading">불러오는 중…</p>
      <template v-else>
        <div class="table-scroll">
          <table>
            <thead>
              <tr>
                <th>버전</th>
                <th>시점</th>
                <th>메시지</th>
                <th>작성자</th>
                <th>용량</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="commits.length === 0">
                <td colspan="6" class="empty">저장된 커밋이 없습니다.</td>
              </tr>
              <tr v-for="commit in commits" :key="commit.version">
                <td>v{{ commit.version }}</td>
                <td>{{ commit.asOf }}</td>
                <td>
                  <span class="cell-clip" :title="commit.message ?? ''">{{
                    commit.message ?? '-'
                  }}</span>
                </td>
                <td>{{ commit.committedBy ?? '-' }}</td>
                <td>{{ formatBytes(commit.payloadBytes) }}</td>
                <td class="actions">
                  <button type="button" :disabled="viewingVersion === commit.version" @click="handleView(commit)">
                    {{ viewingVersion === commit.version ? '여는 중' : '보기' }}
                  </button>
                  <button type="button" @click="handleExport(commit)">JSON 내보내기</button>
                  <button
                    type="button"
                    :disabled="restoringVersion === commit.version"
                    @click="handleRestore(commit)"
                  >
                    {{ restoringVersion === commit.version ? '복원 중' : '새 프로젝트로 복원' }}
                  </button>
                  <button
                    type="button"
                    class="danger"
                    :disabled="removingVersion === commit.version"
                    @click="handleRemove(commit)"
                  >
                    {{ removingVersion === commit.version ? '삭제 중' : '삭제' }}
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </template>
    </section>
  </ModalDialog>
</template>

<style scoped>
.section-head {
  display: flex;
  align-items: flex-start;
  gap: 0.75rem;
  margin-bottom: 0.6rem;
}

.rule {
  flex: 1;
  font-size: 0.78rem;
  color: var(--text-faint);
  line-height: 1.5;
  margin: 0;
}

/* border·배경·font·cursor는 전역 기본과 같아 지웠다 — 알약 모양(radius)과 색·크기만 남는다. */
.add {
  flex: none;
  padding: 0.35rem 0.7rem;
  border-radius: 999px;
  color: var(--text-muted);
  font-size: 0.78rem;
  white-space: nowrap;
}

.add:hover {
  border-color: var(--accent-border);
  color: var(--text-h);
}

.gauge {
  margin: 0 0 0.85rem;
  font-size: 0.85rem;
  font-weight: 600;
  color: var(--text-muted);
}

.gauge.warn {
  color: var(--warn-strong);
}

table {
  width: 100%;
  border-collapse: collapse;
}

th,
td {
  text-align: left;
  padding: 0.5rem 0.6rem;
  border-bottom: 1px solid var(--border-soft);
  white-space: nowrap;
  vertical-align: top;
}

.empty {
  text-align: center;
  color: var(--text-faint);
  padding: 1.5rem 0;
}

.actions {
  display: flex;
  gap: 0.35rem;
}

/* border-radius·border·배경·cursor는 전역 기본과 같아 지웠다. `:disabled`·`.danger`는 전역
   계약과 완전히 같은 값이라 통째로 지웠다(특이도상 이미 전역이 이기고 있던 죽은 코드였다). */
button {
  padding: 0.35rem 0.7rem;
  color: var(--text-muted);
  font-size: 0.78rem;
}

button:hover {
  border-color: var(--accent-border);
  color: var(--text-h);
}

.error {
  color: var(--danger);
  font-size: 0.85rem;
}

.ok {
  color: var(--success-text);
  font-size: 0.85rem;
}
</style>
