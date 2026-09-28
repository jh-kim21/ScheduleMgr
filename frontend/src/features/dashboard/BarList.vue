<script setup lang="ts">
import { computed } from 'vue'
import MiniBar from './MiniBar.vue'
import StatChip from './StatChip.vue'
import { barPercent, type BarListItem } from './dashboardFormat'

/**
 * 라벨 + 가로 막대 + 우측 값의 목록. 표보다 훑기 쉬워서 분포(실행 방식별 Work Package 수 등)에 쓴다.
 *
 * <p>항목이 없으면 길이 0 인 막대를 그리지 않고 문구를 쓴다. 빈 막대는 "0 이다"로 읽히는데,
 * 여기서 말하려는 것은 "셀 것이 없다"이다(지시서 부록 B-8).
 */
const props = withDefaults(
  defineProps<{
    items: BarListItem[]
    emptyText?: string
  }>(),
  { emptyText: '표시할 항목이 없습니다.' },
)

/**
 * 배지 트랙은 배지를 쓰는 목록(WorkloadCard)에서만 그리드에 넣는다. `v-if`로 배지를 안 그리는
 * 것만으로는 부족하다 — CSS Grid의 `gap`은 빈 트랙 앞뒤에도 그대로 붙어서, 5열 템플릿을 전부에
 * 걸어 두면 배지가 없는 목록(RaciCard·ProgressCard)도 열 사이 간격이 하나 늘고 그 여분을 `1fr`인
 * 막대 열이 흡수해 막대가 미세하게 좁아진다. `has-badge` 클래스로 트랙 자체를 만들지 않아야
 * 배지 없는 기존 카드의 레이아웃이 픽셀 단위로 이전과 같다.
 */
const hasBadges = computed(() => props.items.some((item) => item.badge))

function valueText(item: BarListItem): string {
  return item.total === undefined ? `${item.value}` : `${item.value} / ${item.total}`
}

function percentText(item: BarListItem): string {
  const percent = barPercent(item)
  return percent === null ? '-' : `${Math.round(percent)}%`
}
</script>

<template>
  <ul v-if="items.length > 0" class="barlist" :class="{ 'has-badge': hasBadges }">
    <li v-for="item in items" :key="item.key">
      <span class="label" :title="item.label">{{ item.label }}</span>
      <MiniBar :percent="barPercent(item)" :tone="item.tone" :show-unset-text="false" />
      <span class="value num">{{ valueText(item) }}</span>
      <span class="percent num">{{ percentText(item) }}</span>
      <StatChip v-if="item.badge" class="badge" :tone="item.badge.tone" :label="item.badge.label" />
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

/*
 * 배지 트랙은 여기서만 더한다 — `grid-template-columns`를 4열 그대로 둔 목록(RaciCard 등)은
 * `gap`이 붙을 5번째 트랙 자체가 없어, 이전과 픽셀 단위로 같은 막대 폭을 유지한다. `v-if`로
 * 배지 엘리먼트만 안 그리는 것으로는 부족했다 — CSS Grid의 `gap`은 빈 트랙 앞뒤에도 그대로
 * 붙어서, 5열 템플릿을 전부에 걸면 배지 없는 카드도 그 여분 gap 만큼 `1fr`인 막대 열이 좁아졌다.
 */
.barlist.has-badge li {
  grid-template-columns: minmax(0, 7rem) minmax(0, 1fr) auto 2.6rem auto;
}

.badge {
  justify-self: start;
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
