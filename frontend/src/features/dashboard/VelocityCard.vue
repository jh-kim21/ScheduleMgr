<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink } from 'vue-router'
import type { Dashboard } from '../../api/dashboardApi'
import DashCard from './DashCard.vue'
import { closedSprints, runningSprint } from './dashboardFormat'

/**
 * 막대 하나가 종료된 Sprint 하나. 진행 중인 것은 아직 움직이는 숫자라 추세에 넣지 않고 옆에
 * 따로 적는다 — 넣으면 Sprint 중간에 볼 때마다 매번 하락으로 읽힌다.
 *
 * <p>단일 팀 전제라 계열이 하나다. 팀이 생기면 계열이 나뉘어야 하고, 합산해서는 안 된다.
 *
 * <p>막대에 `<title>` 을 달지 않는다 — 네이티브 툴팁이 커스텀 툴팁과 이중으로 뜬다(간트에서
 * 같은 이유로 제거했다). 값은 막대 아래에 그대로 적혀 있다.
 */
const props = defineProps<{ data: Dashboard }>()

const closed = computed(() => closedSprints(props.data.velocity))
const running = computed(() => runningSprint(props.data.velocity))

/** 0 으로 나누지 않도록 바닥을 1 로 둔다 — 전부 0 포인트인 Sprint 만 있을 수 있다. */
const peak = computed(() => Math.max(1, ...closed.value.map((sprint) => sprint.donePoints)))
</script>

<template>
  <DashCard title="완료 Story Point 추세" subtitle="종료된 Sprint의 실적">
    <template #action>
      <RouterLink to="/sprint">Sprint ›</RouterLink>
    </template>

    <template v-if="closed.length > 0">
      <ul class="trend">
        <li v-for="sprint in closed" :key="sprint.sprintId">
          <span class="slot">
            <span class="bar" :style="{ height: `${(sprint.donePoints / peak) * 100}%` }"></span>
          </span>
          <span class="points num">{{ sprint.donePoints }}</span>
          <span class="name" :title="sprint.name">{{ sprint.name }}</span>
        </li>
      </ul>
      <p class="muted">이월된 항목은 완료한 Sprint 쪽에만 셉니다.</p>
    </template>
    <p v-else class="muted">종료된 Sprint가 아직 없습니다.</p>

    <p v-if="running" class="muted">
      진행 중 {{ running.name }} — 현재 {{ running.donePoints }}포인트. 아직 움직이는 숫자라 추세에
      넣지 않았습니다.
    </p>
  </DashCard>
</template>

<style scoped>
/*
 * 축 없이 값을 막대 아래 적는다 — 칸이 적어 눈금이 소음이 된다. Sprint 가 많아지면 이 목록만
 * 가로로 넘어가고 페이지에는 스크롤이 생기지 않는다(.table-scroll 과 같은 태도).
 */
.trend {
  list-style: none;
  display: flex;
  align-items: flex-end;
  gap: 0.6rem;
  margin: 0;
  padding: 0 0 0.2rem;
  overflow-x: auto;
  overscroll-behavior-x: contain;
}

.trend li {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.15rem;
  min-width: 3.5rem;
}

.slot {
  display: flex;
  align-items: flex-end;
  height: 4.5rem;
  width: 1.6rem;
}

.bar {
  width: 100%;
  min-height: 2px;
  background: var(--accent);
  border-radius: 4px 4px 0 0;
}

.points {
  font-size: 0.8rem;
  font-weight: 600;
  color: var(--text-h);
}

.name {
  font-size: 0.7rem;
  color: var(--text-faint);
  max-width: 6rem;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
