<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink } from 'vue-router'
import type { Dashboard } from '../../api/dashboardApi'
import DashCard from './DashCard.vue'
import StatChip from './StatChip.vue'
import { PROGRESS_TAB, taskRoute } from './dashboardFormat'

/**
 * 계획 진척의 유일한 근거. 이 카드가 진척 묶음 쪽에 있는 이유는 "미산정"이라 적힌 까닭이 여기에
 * 있기 때문이다 — 승인된 기준선이 없으면 시간이 지났다는 것만으로 계획 진척을 채우지 않는다.
 */
const props = defineProps<{ data: Dashboard }>()

const scopeChanged = computed(() => {
  const scope = props.data.scope
  if (!scope.hasBaseline) return false
  return scope.added.length > 0 || scope.removed.length > 0 || scope.weightChanged.length > 0
})
</script>

<template>
  <DashCard
    title="기준 일정"
    :subtitle="
      data.baseline
        ? `v${data.baseline.version} · ${data.baseline.itemCount}개 항목 · ${data.baseline.approvedAt.slice(0, 10)} 승인`
        : null
    "
    :tone="data.baseline && data.schedule.baselineExceeded.length > 0 ? 'warn' : 'default'"
  >
    <template #action>
      <RouterLink :to="PROGRESS_TAB">기준선 ›</RouterLink>
    </template>

    <template v-if="data.baseline">
      <div class="chips">
        <StatChip
          :tone="data.schedule.baselineExceeded.length > 0 ? 'warn' : 'success'"
          :label="`기준 종료일 초과 ${data.schedule.baselineExceeded.length}`"
        />
        <StatChip
          :tone="scopeChanged ? 'warn' : 'neutral'"
          :label="`범위 변화 +${data.scope.added.length} / −${data.scope.removed.length} / 가중 ${data.scope.weightChanged.length}`"
        />
      </div>

      <p v-if="data.baseline.approvedBy" class="muted">승인 {{ data.baseline.approvedBy }}</p>

      <ul v-if="data.schedule.baselineExceeded.length > 0" class="refs">
        <li v-for="task in data.schedule.baselineExceeded" :key="task.wbsItemId">
          <RouterLink :to="taskRoute(task)">{{ task.code }} {{ task.name }}</RouterLink>
          <span class="muted">{{ task.detail }}</span>
        </li>
      </ul>

      <p v-if="scopeChanged" class="warn-note">
        기준선 이후 범위가 바뀌었습니다 — 추가 {{ data.scope.added.length }}, 제외
        {{ data.scope.removed.length }}, 가중치 변경 {{ data.scope.weightChanged.length }}. 편차는
        기준선에 든 항목만으로 계산했습니다.
      </p>
    </template>

    <p v-else class="muted">
      승인된 기준선이 없습니다. 현재 계획을 기준선으로 보지 않으므로 계획 대비 편차와 기준 초과는
      표시하지 않습니다.
    </p>
  </DashCard>
</template>
