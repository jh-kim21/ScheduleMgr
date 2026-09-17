<script setup lang="ts">
import { computed } from 'vue'
import { tagColorFor } from './tagColor'

/**
 * 업무 분야 태그 하나를 알약으로 그린다. 트리의 분야 열, WBS 폼의 다중 선택, 분야 관리 대화상자가
 * 같은 칩을 쓴다 — 세 곳이 각자 그리면 한 곳의 색 규칙만 바뀌어도 같은 태그가 화면마다 달라진다.
 *
 * 색은 `tagColorFor`가 고른 슬롯 이름을 `data-color`에 실어 `style.css`의 토큰으로 칠한다.
 * **이 파일에 색값은 없다** (CLAUDE.md "다크 모드").
 */
const props = defineProps<{
  tag: { id?: number; name: string; color?: string | null }
  /** 하위에서 모아 온 값이거나 지금 적용되지 않는 값일 때 — 자기 분야와 같은 무게로 읽히면 안 된다. */
  muted?: boolean
  /** 고를 수 있는 칩인지(폼·관리 화면). 기본은 읽기 전용 표시다. */
  selected?: boolean
}>()

const color = computed(() => tagColorFor(props.tag))
</script>

<template>
  <span
    class="tag-chip"
    :data-color="color"
    :class="{ muted: props.muted, selected: props.selected }"
    >{{ props.tag.name }}</span
  >
</template>

<style scoped>
.tag-chip {
  display: inline-block;
  padding: 0.08rem 0.5rem;
  border-radius: 999px;
  font-size: 0.72rem;
  line-height: 1.6;
  white-space: nowrap;
  background: var(--tag-bg);
  color: var(--tag-fg);
  border: 1px solid transparent;
}

/*
 * 슬롯 이름 → 토큰. 여기서 하는 일은 --tag-bg/--tag-fg 라는 지역 별칭에 팔레트 토큰을 꽂는 것뿐이고,
 * 색값 자체는 style.css 에만 있다. (팔레트를 컴포넌트에서 *재정의*하는 것과 다르다 — 과거 GanttChart
 * 가 자기 팔레트를 갖고 있다가 자기 참조로 SVG 가 전부 검게 나온 적이 있다.)
 */
.tag-chip[data-color='blue'] {
  --tag-bg: var(--tag-blue-bg);
  --tag-fg: var(--tag-blue-fg);
}
.tag-chip[data-color='teal'] {
  --tag-bg: var(--tag-teal-bg);
  --tag-fg: var(--tag-teal-fg);
}
.tag-chip[data-color='green'] {
  --tag-bg: var(--tag-green-bg);
  --tag-fg: var(--tag-green-fg);
}
.tag-chip[data-color='lime'] {
  --tag-bg: var(--tag-lime-bg);
  --tag-fg: var(--tag-lime-fg);
}
.tag-chip[data-color='amber'] {
  --tag-bg: var(--tag-amber-bg);
  --tag-fg: var(--tag-amber-fg);
}
.tag-chip[data-color='orange'] {
  --tag-bg: var(--tag-orange-bg);
  --tag-fg: var(--tag-orange-fg);
}
.tag-chip[data-color='rose'] {
  --tag-bg: var(--tag-rose-bg);
  --tag-fg: var(--tag-rose-fg);
}
.tag-chip[data-color='violet'] {
  --tag-bg: var(--tag-violet-bg);
  --tag-fg: var(--tag-violet-fg);
}

/* 하위 요약·보관값. 색 계열은 유지하고 무게만 낮춘다 — 무채색으로 바꾸면 어느 분야인지 못 읽는다. */
.tag-chip.muted {
  opacity: 0.72;
  background: transparent;
  border-color: var(--tag-bg);
}

.tag-chip.selected {
  border-color: var(--tag-fg);
  font-weight: 600;
}
</style>
