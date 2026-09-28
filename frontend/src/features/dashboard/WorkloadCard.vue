<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink } from 'vue-router'
import type { Dashboard } from '../../api/dashboardApi'
import BarList from './BarList.vue'
import DashCard from './DashCard.vue'
import { WORKLOAD_TAB, type BarListItem } from './dashboardFormat'
import { delayBadge, maxActive, topMembers } from './workloadFormat'

/**
 * 상위 5명 — 전원은 부하 탭(`WorkloadPanel`)에서 본다(workload-balance 지시서 2-4). 여기서
 * 새 카드 문법을 짓지 않고 기존 `DashCard`·`BarList`를 그대로 쓴다.
 *
 * <p>막대의 분모는 이 프로젝트 최댓값이다(절대 임계값 없음, 지시서 2-2) — "5건 이상은 위험"
 * 같은 선을 긋지 않고, 분포와 정렬만으로 누가 몰려 있는지 보여준다. 그래서 막대 색도 늘 같은
 * 톤이다 — 부하 숫자 자체에 경고색을 얹지 않는다.
 */
const props = defineProps<{ data: Dashboard }>()

const members = computed(() => props.data.workload.members)
const top = computed(() => topMembers(members.value))
const max = computed(() => maxActive(members.value))

/** `total`을 비우면(= undefined) BarList 가 분모 없는 값만 적고, MiniBar 도 채움 없이 트랙만
 * 그린다 — 최댓값이 0(=아무도 부하가 없음)일 때 "막대를 그리지 않는다"는 규칙이 여기서 나온다. */
const items = computed<BarListItem[]>(() =>
  top.value.map((member) => ({
    key: String(member.memberId),
    label: member.memberName,
    value: member.activeCount,
    total: max.value > 0 ? max.value : undefined,
    badge: delayBadge(member),
  })),
)
</script>

<template>
  <DashCard
    title="부하"
    subtitle="구성원별 동시 진행 업무"
    :tone="data.workload.unassignedActiveCount > 0 ? 'warn' : 'default'"
  >
    <template #action>
      <RouterLink :to="WORKLOAD_TAB">자세히 ›</RouterLink>
    </template>

    <p v-if="members.length === 0" class="muted">구성원이 없습니다.</p>
    <template v-else>
      <BarList :items="items" />

      <p class="muted">이 프로젝트 안에서의 비교입니다.</p>
      <p v-if="data.workload.unassignedActiveCount > 0" class="warn-note">
        담당자 미지정 진행 업무 {{ data.workload.unassignedActiveCount }}건
      </p>
    </template>
  </DashCard>
</template>
