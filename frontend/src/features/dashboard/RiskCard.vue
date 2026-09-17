<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink } from 'vue-router'
import type { Dashboard } from '../../api/dashboardApi'
import DashCard from './DashCard.vue'
import StatChip from './StatChip.vue'
import { overflowSuffix, raidRefLabel } from './dashboardFormat'

/**
 * 노출도 · 기한 초과 · 열린 이슈. 칩의 숫자는 목록이 잘리기 전의 진짜 건수(서버가 자르기 전
 * 스트림에서 센다)이고, 아래 목록은 카드당 5건까지만 싣는다 — 두 값이 다를 수 있어 목록 제목에
 * "5건 표시 · 총 N건"을 적는다.
 *
 * <p>노출도 등급별 분포(높음 · 보통 · 낮음 · 미산정)는 대시보드 응답에 아직 없다. 임의로 만들지
 * 않고, 필요해지면 지시서 부록 A-3 으로 붙인다.
 */
const props = defineProps<{ data: Dashboard }>()

const hasAny = computed(
  () =>
    props.data.control.openIssueCount > 0 ||
    props.data.control.highExposureCount > 0 ||
    props.data.control.overdueCount > 0,
)
</script>

<template>
  <DashCard
    title="위험 · 이슈"
    :tone="data.control.highExposureCount > 0 || data.control.overdueCount > 0 ? 'warn' : 'default'"
  >
    <template #action>
      <RouterLink to="/raid">RAID ›</RouterLink>
    </template>

    <div class="chips">
      <StatChip
        :tone="data.control.highExposureCount > 0 ? 'danger' : 'neutral'"
        :label="`노출도 높음 ${data.control.highExposureCount}`"
      />
      <StatChip
        :tone="data.control.overdueCount > 0 ? 'warn' : 'neutral'"
        :label="`기한 초과 ${data.control.overdueCount}`"
      />
      <StatChip tone="neutral" :label="`열린 이슈 ${data.control.openIssueCount}`" />
    </div>

    <template v-if="hasAny">
      <template v-if="data.control.overdueCount > 0">
        <p class="subhead">
          기한 초과{{ overflowSuffix(data.control.overdueCount, data.control.overdue.length) }}
        </p>
        <ul class="refs">
          <li v-for="entry in data.control.overdue" :key="entry.raidItemId">
            <RouterLink to="/raid">{{ raidRefLabel(entry) }}</RouterLink>
          </li>
        </ul>
      </template>

      <template v-if="data.control.highExposureCount > 0">
        <p class="subhead">
          노출도 높음{{
            overflowSuffix(data.control.highExposureCount, data.control.highExposure.length)
          }}
        </p>
        <ul class="refs">
          <li v-for="entry in data.control.highExposure" :key="entry.raidItemId">
            <RouterLink to="/raid">{{ raidRefLabel(entry) }}</RouterLink>
          </li>
        </ul>
      </template>

      <template v-if="data.control.openIssueCount > 0">
        <p class="subhead">
          열린 이슈{{ overflowSuffix(data.control.openIssueCount, data.control.openIssues.length) }}
        </p>
        <ul class="refs">
          <li v-for="entry in data.control.openIssues" :key="entry.raidItemId">
            <RouterLink to="/raid">{{ raidRefLabel(entry) }}</RouterLink>
          </li>
        </ul>
      </template>
    </template>
    <p v-else class="ok-note">열린 위험·이슈가 없습니다.</p>
  </DashCard>
</template>
