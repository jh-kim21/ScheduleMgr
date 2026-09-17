<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink } from 'vue-router'
import type { Dashboard } from '../../api/dashboardApi'
import BarList from './BarList.vue'
import DashCard from './DashCard.vue'
import type { BarListItem } from './dashboardFormat'

/** 상속을 반영한 판정이다 — 단계의 A 를 물려받은 Work Package 는 누락이 아니다. */
const props = defineProps<{ data: Dashboard }>()

/**
 * 셋 다 "공백의 건수"라 셋의 합을 분모로 두어 어느 쪽이 큰지 한눈에 보이게 한다. 건수 자체는
 * 바뀌지 않는다 — 분모는 세 값을 서로 견주기 위한 것이지 새 지표가 아니다.
 *
 * <p>위반이 0 건이면 BarList 대신 ok-note 를 쓴다 — 길이 0 인 막대 셋은 "0 이다"로 읽히기 전에
 * 그냥 소음이다.
 */
const issues = computed<BarListItem[]>(() => {
  const control = props.data.control
  const total =
    control.missingAccountableCount +
    control.missingResponsibleCount +
    control.multipleAccountableCount
  return [
    { key: 'accountable', label: '책임자 없음', value: control.missingAccountableCount, total, tone: 'red' },
    { key: 'responsible', label: '담당자 없음', value: control.missingResponsibleCount, total, tone: 'amber' },
    { key: 'multiple', label: '책임자 중복', value: control.multipleAccountableCount, total, tone: 'violet' },
  ]
})
</script>

<template>
  <DashCard
    title="책임 (RACI)"
    subtitle="상속을 반영한 판정"
    :tone="data.control.raciIssueCount > 0 ? 'warn' : 'default'"
  >
    <template #action>
      <RouterLink to="/raci">RACI ›</RouterLink>
    </template>

    <p v-if="data.control.raciIssueCount === 0" class="ok-note">책임 공백이 없습니다.</p>
    <BarList v-else :items="issues" />
  </DashCard>
</template>
