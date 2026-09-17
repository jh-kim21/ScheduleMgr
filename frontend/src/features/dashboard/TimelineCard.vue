<script setup lang="ts">
import { RouterLink } from 'vue-router'
import type { Dashboard } from '../../api/dashboardApi'
import DashCard from './DashCard.vue'
import StatChip from './StatChip.vue'
import { taskRoute } from './dashboardFormat'

/**
 * 계획 · 예상 · 지연. 그리고 **인수 대기 목록**이 여기 있는 이유: "실행은 끝났지만 무엇이 안
 * 끝났나"에 대한 답이라 일정 옆이 제자리다(인수는 진척과 직교하므로 진척 카드에 두지 않는다).
 * 건수 자체는 KPI 스트립이 들고 있고, 이 카드는 그 건수의 대상이 무엇인지를 보여 준다.
 */
defineProps<{ data: Dashboard }>()
</script>

<template>
  <DashCard
    title="일정"
    :subtitle="
      data.project.planStart
        ? `계획 ${data.project.planStart} ~ ${data.project.planEnd}`
        : '계획 기간 미입력'
    "
    :tone="data.schedule.delayedCount > 0 ? 'warn' : 'default'"
  >
    <template #action>
      <RouterLink to="/gantt">간트 ›</RouterLink>
    </template>

    <dl class="rows">
      <dt>예상 종료</dt>
      <dd v-if="data.project.forecastEnd" class="num">{{ data.project.forecastEnd }}</dd>
      <dd v-else class="muted">-</dd>
    </dl>

    <div class="chips">
      <StatChip
        :tone="data.schedule.delayedCount > 0 ? 'danger' : 'neutral'"
        :label="`지연 ${data.schedule.delayedCount}`"
      />
      <StatChip
        :tone="data.schedule.atRiskCount > 0 ? 'warn' : 'neutral'"
        :label="`지연 위험 ${data.schedule.atRiskCount}`"
      />
      <StatChip tone="neutral" :label="`임계 ${data.schedule.criticalPathCount}`" />
    </div>

    <template v-if="data.schedule.delayed.length > 0">
      <hr class="divider" />
      <p class="subhead">지연 업무</p>
      <ul class="refs">
        <li v-for="task in data.schedule.delayed" :key="task.wbsItemId">
          <RouterLink :to="taskRoute(task)">{{ task.code }} {{ task.name }}</RouterLink>
          <span class="muted">{{ task.detail }}</span>
        </li>
      </ul>
    </template>
    <p v-else-if="data.workPackages.total > 0" class="ok-note">
      기준일 현재 지연된 업무가 없습니다.
    </p>

    <template v-if="data.schedule.acceptancePending.length > 0">
      <hr class="divider" />
      <p class="subhead">인수 대기</p>
      <p class="muted">실행은 끝났지만 승인이 남았습니다. 완료로 세지 않습니다.</p>
      <ul class="refs">
        <li v-for="task in data.schedule.acceptancePending" :key="task.wbsItemId">
          <RouterLink :to="taskRoute(task)">{{ task.code }} {{ task.name }}</RouterLink>
        </li>
      </ul>
    </template>
  </DashCard>
</template>
