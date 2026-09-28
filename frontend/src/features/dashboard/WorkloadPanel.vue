<script setup lang="ts">
import { computed, ref } from 'vue'
import { RouterLink } from 'vue-router'
import type { Dashboard } from '../../api/dashboardApi'
import { sortMembers, type SortDirection, type WorkloadSortKey } from './workloadFormat'

/**
 * 부하 탭 본문 — 구성원 × 출처 표. 요약 카드(`WorkloadCard`)와 같은 `data.workload`를 읽으므로
 * 상위 5명이 아니라 전원이 나온다(workload-balance 지시서 2-4). 이 화면은 읽기 전용이라 새
 * 무효화·새 요청을 만들지 않는다 — `DashboardView`가 이미 읽어 둔 `data`를 그대로 받는다.
 */
const props = defineProps<{ data: Dashboard }>()

const members = computed(() => props.data.workload.members)

interface Column {
  key: WorkloadSortKey
  label: string
}

const COLUMNS: Column[] = [
  { key: 'memberName', label: '구성원' },
  { key: 'activeCount', label: '동시 진행' },
  { key: 'delayedCount', label: '지연' },
  { key: 'atRiskCount', label: '위험' },
  { key: 'openStoryCount', label: '미완료 Story' },
  { key: 'storyPoints', label: '포인트' },
  { key: 'openRaidCount', label: '열린 RAID' },
]

/**
 * 처음엔 서버가 준 순서(activeCount 내림차순 · 동점은 이름)를 그대로 보여준다 — 요약 카드와
 * 같은 순서라야 "같은 숫자"로 읽힌다. 사용자가 열 머리글을 눌러야 비로소 클라이언트 정렬이
 * 끼어든다.
 */
const sortKey = ref<WorkloadSortKey | null>(null)
const sortDirection = ref<SortDirection>('desc')

const rows = computed(() =>
  sortKey.value === null ? members.value : sortMembers(members.value, sortKey.value, sortDirection.value),
)

function toggleSort(key: WorkloadSortKey) {
  if (sortKey.value === key) {
    sortDirection.value = sortDirection.value === 'desc' ? 'asc' : 'desc'
  } else {
    sortKey.value = key
    sortDirection.value = 'desc'
  }
}

function ariaSort(key: WorkloadSortKey): 'ascending' | 'descending' | 'none' {
  if (sortKey.value !== key) return 'none'
  return sortDirection.value === 'asc' ? 'ascending' : 'descending'
}
</script>

<template>
  <section class="workload-panel">
    <p v-if="members.length === 0" class="notice">구성원이 없습니다.</p>

    <template v-else>
      <p class="hint">
        이 프로젝트 안에서의 비교입니다. 다른 프로젝트와 합산할 수 없습니다 — 구성원은 프로젝트
        스코프라 이름이 같아도 같은 사람인지 알 수 없습니다.
      </p>
      <p v-if="data.workload.unassignedActiveCount > 0" class="warn-note">
        담당자 미지정 진행 업무 {{ data.workload.unassignedActiveCount }}건
      </p>

      <div class="table-scroll">
        <table>
          <thead>
            <tr>
              <th v-for="column in COLUMNS" :key="column.key" scope="col" :aria-sort="ariaSort(column.key)">
                <button type="button" @click="toggleSort(column.key)">
                  {{ column.label }}
                  <span v-if="sortKey === column.key" class="sort-indicator" aria-hidden="true">
                    {{ sortDirection === 'desc' ? '▼' : '▲' }}
                  </span>
                </button>
              </th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="member in rows" :key="member.memberId">
              <td>{{ member.memberName }}</td>
              <td class="num">
                <RouterLink to="/raci">{{ member.activeCount }}</RouterLink>
              </td>
              <td class="num">{{ member.delayedCount }}</td>
              <td class="num">{{ member.atRiskCount }}</td>
              <td class="num">
                <RouterLink to="/backlog">{{ member.openStoryCount }}</RouterLink>
              </td>
              <td class="num">{{ member.storyPoints ?? '-' }}</td>
              <td class="num">
                <RouterLink to="/raid">{{ member.openRaidCount }}</RouterLink>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <p class="hint link-hint">
        숫자를 누르면 해당 화면으로 이동합니다 — 담당자로 필터된 화면이 아직 없어 그 화면 전체가
        열립니다.
      </p>
    </template>
  </section>
</template>

<style scoped>
.workload-panel {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.notice {
  color: var(--text-dim);
  margin: 0;
}

.hint {
  margin: 0;
  font-size: 0.82rem;
  color: var(--text-faint);
}

/* DashCard의 .warn-note(:deep 전용, 카드 밖인 여기서는 닿지 않는다)와 값을 맞춘다. */
.warn-note {
  font-size: 0.8rem;
  line-height: 1.5;
  color: var(--warn-strong);
  background: var(--warn-weak);
  border-left: 3px solid var(--warn);
  border-radius: 6px;
  padding: 0.45rem 0.6rem;
  margin: 0;
}

table {
  width: 100%;
  border-collapse: collapse;
}

th,
td {
  text-align: left;
  padding: 0.5rem 0.7rem;
  border-bottom: 1px solid var(--border-soft);
  font-size: 0.85rem;
  white-space: nowrap;
}

th {
  font-size: 0.75rem;
  color: var(--text-dim);
  font-weight: 600;
}

th button {
  border: none;
  background: none;
  padding: 0;
  font: inherit;
  font-size: inherit;
  font-weight: inherit;
  color: inherit;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
}

.sort-indicator {
  font-size: 0.7rem;
  color: var(--accent);
}

td.num,
th:not(:first-child) {
  text-align: right;
}

td:first-child {
  color: var(--text-h);
}
</style>
