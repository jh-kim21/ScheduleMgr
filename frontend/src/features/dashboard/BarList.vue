<script setup lang="ts">
import MiniBar from './MiniBar.vue'
import { barPercent, type BarListItem } from './dashboardFormat'

/**
 * 라벨 + 가로 막대 + 우측 값의 목록. 표보다 훑기 쉬워서 분포(실행 방식별 Work Package 수 등)에 쓴다.
 *
 * <p>항목이 없으면 길이 0 인 막대를 그리지 않고 문구를 쓴다. 빈 막대는 "0 이다"로 읽히는데,
 * 여기서 말하려는 것은 "셀 것이 없다"이다(지시서 부록 B-8).
 */
withDefaults(
  defineProps<{
    items: BarListItem[]
    emptyText?: string
  }>(),
  { emptyText: '표시할 항목이 없습니다.' },
)

function valueText(item: BarListItem): string {
  return item.total === undefined ? `${item.value}` : `${item.value} / ${item.total}`
}

function percentText(item: BarListItem): string {
  const percent = barPercent(item)
  return percent === null ? '-' : `${Math.round(percent)}%`
}
</script>

<template>
  <ul v-if="items.length > 0" class="barlist">
    <li v-for="item in items" :key="item.key">
      <span class="label" :title="item.label">{{ item.label }}</span>
      <MiniBar :percent="barPercent(item)" :tone="item.tone" :show-unset-text="false" />
      <span class="value num">{{ valueText(item) }}</span>
      <span class="percent num">{{ percentText(item) }}</span>
    </li>
  </ul>
  <p v-else class="empty">{{ emptyText }}</p>
</template>

<style scoped>
.barlist {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 0.55rem;
}

/*
 * 라벨 열을 minmax(0, …) 로 잡아야 긴 이름 하나가 열을 밀어 페이지에 가로 스크롤을 만들지
 * 않는다(지시서 부록 B-7). 막대는 남는 폭을 먹고, 값과 비율은 자리가 고정돼 세로로 정렬된다.
 */
.barlist li {
  display: grid;
  grid-template-columns: minmax(0, 7rem) minmax(0, 1fr) auto 2.6rem;
  align-items: center;
  gap: 0.6rem;
  font-size: 0.8rem;
}

.label {
  color: var(--text-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.value {
  color: var(--text-h);
  white-space: nowrap;
}

.percent {
  color: var(--text-faint);
  text-align: right;
}

.empty {
  font-size: 0.8rem;
  color: var(--text-faint);
}
</style>
