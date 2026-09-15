<script setup lang="ts">
import type { CommitCapacity, CommitMeta } from '../../api/commitApi'
import ModalDialog from '../../components/ModalDialog.vue'
import { formatBytes, formatCapacityUsage } from './commitFormat'

/**
 * The 409 dialog: capacity is full, so the pending commit was rejected. Deleting is never
 * automatic (지시서 2.3) — the user picks what to give up, then retries the same commit by hand.
 */
const props = defineProps<{
  capacity: CommitCapacity
  commits: CommitMeta[]
  /** Which version a delete/retry is in flight for, so its own row can show that. */
  busyVersion?: number | null
  retrying?: boolean
  /** A rejection from a delete or a retry attempted from inside this dialog. */
  error?: string | null
}>()

const emit = defineEmits<{
  remove: [version: number]
  retry: []
  close: []
}>()
</script>

<template>
  <ModalDialog title="커밋 용량이 가득 찼습니다" :error="props.error" @close="emit('close')">
    <p class="lede">
      이 프로젝트의 커밋 용량이 한도를 넘어 새 커밋을 저장할 수 없습니다. 지울 커밋을 골라
      삭제한 뒤 <strong>다시 시도</strong>를 눌러 주세요. 자동으로 지우지 않습니다 — 무엇을 버릴지는
      직접 고릅니다.
    </p>
    <p class="gauge" :class="{ warn: capacity.usedPercent < 100, over: capacity.usedPercent >= 100 }">
      {{ formatCapacityUsage(capacity) }}
    </p>

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
            <td colspan="6" class="empty">삭제할 커밋이 없습니다.</td>
          </tr>
          <tr v-for="commit in commits" :key="commit.version">
            <td>v{{ commit.version }}</td>
            <td>{{ commit.asOf }}</td>
            <td><span class="cell-clip" :title="commit.message ?? ''">{{ commit.message ?? '-' }}</span></td>
            <td>{{ commit.committedBy ?? '-' }}</td>
            <td>{{ formatBytes(commit.payloadBytes) }}</td>
            <td>
              <button
                type="button"
                class="danger"
                :disabled="busyVersion === commit.version"
                @click="emit('remove', commit.version)"
              >
                {{ busyVersion === commit.version ? '삭제 중' : '삭제' }}
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <div class="dialog-actions">
      <button type="button" class="primary" :disabled="retrying" @click="emit('retry')">
        {{ retrying ? '다시 시도하는 중' : '다시 시도' }}
      </button>
      <button type="button" @click="emit('close')">닫기</button>
    </div>
  </ModalDialog>
</template>

<style scoped>
.lede {
  margin: 0 0 0.75rem;
  font-size: 0.85rem;
  color: var(--text-muted);
  line-height: 1.5;
}

.gauge {
  margin: 0 0 0.85rem;
  font-size: 0.85rem;
  font-weight: 600;
}

.gauge.warn {
  color: var(--warn-strong);
}

.gauge.over {
  color: var(--danger);
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
}

.empty {
  text-align: center;
  color: var(--text-faint);
  padding: 1.5rem 0;
}

button {
  padding: 0.4rem 0.8rem;
  border-radius: 6px;
  border: 1px solid var(--border-input);
  background: var(--surface);
  color: var(--text-muted);
  cursor: pointer;
  font-size: 0.8rem;
}

button.danger {
  color: var(--danger);
  border-color: var(--danger-border);
}

button.danger:disabled {
  color: var(--disabled-fg);
  border-color: var(--disabled-border);
  cursor: default;
}

button.primary {
  border-color: var(--accent);
  background: var(--accent);
  color: var(--accent-fg);
}

button.primary:disabled {
  background: var(--disabled-bg);
  border-color: var(--disabled-border);
  color: var(--disabled-fg);
  cursor: default;
}

.dialog-actions {
  display: flex;
  gap: 0.5rem;
  margin-top: 0.9rem;
}
</style>
