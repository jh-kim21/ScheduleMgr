<script setup lang="ts">
import { RouterLink } from 'vue-router'
import type { Dashboard } from '../../api/dashboardApi'
import DashCard from './DashCard.vue'
import { gapRoute } from './dashboardFormat'

/**
 * 숫자가 답하지 못하는 부분을 따로 카드로 보고한다. 각주로 두면 헤드라인만 읽고 지나간다.
 * **임의로 채우지 않는다** — "모른다"와 "0"은 다른 사실이다.
 */
defineProps<{ data: Dashboard }>()
</script>

<template>
  <DashCard title="데이터 누락" subtitle="임의로 채우지 않았습니다" tone="warn">
    <ul class="gaps">
      <li v-for="gap in data.gaps" :key="gap.kind">
        <RouterLink :to="gapRoute(gap)">{{ gap.label }}</RouterLink>
        <strong class="num">{{ gap.count }}건</strong>
      </li>
    </ul>
  </DashCard>
</template>

<style scoped>
/*
 * 누락이 여러 종류면 한 줄에 여러 개가 들어간다 — 이 카드는 폭이 12칸이라 세로로 쌓으면
 * 오른쪽이 비고, 헤드라인 카드보다 길어 보인다.
 */
.gaps {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(min(100%, 16rem), 1fr));
  gap: 0.3rem 1.2rem;
  font-size: 0.82rem;
}

.gaps li {
  display: flex;
  gap: 0.5rem;
  min-width: 0;
}

.gaps li > :first-child {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.gaps strong {
  margin-left: auto;
  color: var(--text-muted);
}
</style>
