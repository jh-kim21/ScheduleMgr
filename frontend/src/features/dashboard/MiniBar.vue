<script setup lang="ts">
import { computed } from 'vue'
import { progressBarWidth } from '../../shared/progress'
import type { Tone } from './icons'

/**
 * 가로 막대 하나. **산정 전(`null`)을 여기 한 곳에서 처리한다** — 카드마다 `?? 0` 을 쓰면 "아직
 * 셀 근거가 없다"가 "0% 했다"로 바뀌고, 그 실수는 카드 수만큼 반복된다.
 *
 * <p>채움을 그리지 않고 트랙만 남기는 것이 그 구분이다. 빈 막대는 0% 로 읽히므로 문구도 함께 적는다.
 */
const props = withDefaults(
  defineProps<{
    percent: number | null
    tone?: Tone
    /** 막대 옆에 "산정 전"을 적을 자리가 없는 좁은 행(BarList)에서 끈다. */
    showUnsetText?: boolean
  }>(),
  { tone: 'blue', showUnsetText: true },
)

const width = computed(() => progressBarWidth(props.percent))
const label = computed(() => (props.percent === null ? '산정 전' : `${Math.round(props.percent)}%`))
</script>

<template>
  <div class="minibar">
    <div class="track" role="img" :aria-label="label">
      <div v-if="percent !== null" class="fill" :class="`tone-${tone}`" :style="{ width }"></div>
    </div>
    <span v-if="percent === null && showUnsetText" class="unset">산정 전</span>
  </div>
</template>

<style scoped>
.minibar {
  display: flex;
  align-items: center;
  gap: 0.45rem;
  min-width: 0;
}

.track {
  flex: 1;
  min-width: 0;
  height: 6px;
  border-radius: 999px;
  background: var(--surface-sunken);
  overflow: hidden;
}

.fill {
  height: 100%;
  border-radius: 999px;
  background: var(--accent);
}

/* 톤은 클래스로만 고른다 — 컴포넌트에서 토큰을 재정의하면 자기참조로 값이 죽는다(CLAUDE.md). */
.fill.tone-green {
  background: var(--status-completed);
}

.fill.tone-blue {
  background: var(--status-on-track);
}

.fill.tone-violet {
  background: var(--accent);
}

.fill.tone-amber {
  background: var(--status-at-risk);
}

.fill.tone-red {
  background: var(--status-delayed);
}

.fill.tone-gray {
  background: var(--status-not-started);
}

.unset {
  font-size: 0.72rem;
  color: var(--text-faint);
  white-space: nowrap;
}
</style>
