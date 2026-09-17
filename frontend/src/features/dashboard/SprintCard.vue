<script setup lang="ts">
import { RouterLink } from 'vue-router'
import type { Dashboard } from '../../api/dashboardApi'
import { RAID_TYPE_LABELS } from '../../shared/raid'
import DashCard from './DashCard.vue'
import StatChip from './StatChip.vue'

/**
 * 진행 중인 Sprint 하나. 단일 팀 전제라 최대 하나이고, 없으면 숫자가 아니라 상태 문구다.
 *
 * <p>차단은 상태와 직교한다 — 칸을 옮기는 것이 아니라 표시라서, 여기서도 완료 건수와 나란히
 * 세지 않고 목록으로 따로 적는다.
 */
defineProps<{ data: Dashboard }>()
</script>

<template>
  <DashCard
    title="실행"
    :subtitle="
      data.execution.activeSprint
        ? `${data.execution.activeSprint.startDate} ~ ${data.execution.activeSprint.endDate}`
        : null
    "
  >
    <template #action>
      <RouterLink to="/sprint">Sprint ›</RouterLink>
    </template>

    <template v-if="data.execution.activeSprint">
      <p class="sprint-name">{{ data.execution.activeSprint.name }}</p>
      <p v-if="data.execution.activeSprint.goal" class="muted">
        {{ data.execution.activeSprint.goal }}
      </p>

      <div class="chips">
        <StatChip
          tone="neutral"
          :label="`완료 ${data.execution.activeSprint.doneItems}/${data.execution.activeSprint.plannedItems}`"
        />
        <StatChip
          tone="planned"
          :label="`포인트 ${data.execution.activeSprint.donePoints}/${data.execution.activeSprint.plannedPoints}`"
        />
        <StatChip
          :tone="data.execution.activeSprint.blocked.length > 0 ? 'danger' : 'neutral'"
          :label="`차단 ${data.execution.activeSprint.blocked.length}`"
        />
      </div>

      <template v-if="data.execution.activeSprint.blocked.length > 0">
        <hr class="divider" />
        <p class="subhead">차단</p>
        <ul class="blocked">
          <li v-for="item in data.execution.activeSprint.blocked" :key="item.backlogItemId">
            <span class="blocked-title">{{ item.title }}</span>
            <span class="muted">
              {{ item.reason ?? '사유 없음' }} · {{ item.assigneeName ?? '담당 미지정' }}
            </span>
            <ul v-if="item.raid.length > 0" class="refs">
              <li v-for="entry in item.raid" :key="entry.raidItemId">
                <RouterLink to="/raid">
                  {{ RAID_TYPE_LABELS[entry.type] }} {{ entry.title }}
                </RouterLink>
                <span class="muted">{{ entry.ownerName ?? '소유자 미지정' }}</span>
              </li>
            </ul>
          </li>
        </ul>
      </template>
    </template>

    <p v-else class="muted">진행 중인 Sprint가 없습니다.</p>

    <p v-if="data.execution.backlogUnlinkedCount > 0" class="warn-note">
      Work Package에 연결되지 않은 Backlog 항목이 {{ data.execution.backlogUnlinkedCount }}건
      있습니다.
      <RouterLink to="/backlog">Backlog에서 확인</RouterLink>
    </p>
  </DashCard>
</template>

<style scoped>
.sprint-name {
  font-size: 0.95rem;
  font-weight: 600;
  color: var(--text-h);
}

.blocked {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 0.45rem;
  font-size: 0.8rem;
}

.blocked-title {
  color: var(--text-h);
  margin-right: 0.4rem;
}
</style>
