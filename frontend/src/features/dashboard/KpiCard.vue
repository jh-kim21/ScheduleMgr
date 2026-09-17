<script setup lang="ts">
import { computed } from 'vue'
import { roundPoints } from '../../shared/progress'
import MiniBar from './MiniBar.vue'
import { ICON_PATHS, type IconName, type Tone } from './icons'
import { varianceKpiTone } from './dashboardFormat'

/**
 * KPI 스트립 한 장: 파스텔 아이콘 원 + 라벨 + 큰 숫자 + 보조 한 줄 + (선택) 막대.
 *
 * <p><b>`null` 을 여기서만 처리한다.</b> 호출하는 카드가 `?? 0` 을 쓰지 못하게 값을 그대로
 * 받아서, 산정 전이면 큰 숫자 자리에 "산정 전"을 쓰고 막대를 아예 그리지 않는다. 0% 와는 다른
 * 사실이고, 이 구분을 카드마다 다시 쓰게 두면 한 곳에서 반드시 틀린다(지시서 부록 B-1).
 *
 * <p><b>편차는 부호를 유지한다</b>(`signed`). 절댓값 + 화살표로 바꾸면 정확한 양이 사라진다 —
 * 부호는 숫자에 남기고, 방향은 색이 거든다.
 */
const props = withDefaults(
  defineProps<{
    label: string
    /** 산정 전은 `null`. 0 과 구분해서 넘긴다. */
    value: number | null
    /** '%' · '%p' · 'pt' 처럼 숫자에 붙는 단위. 건수처럼 단위가 없으면 비운다. */
    unit?: string
    /** 이 숫자가 무엇 기준인지 한 줄. */
    hint?: string | null
    tone?: Tone
    icon: IconName
    /** 넘기면 숫자 아래 막대를 그린다. 값이 산정 전이면 막대는 그리지 않는다. */
    percent?: number | null
    /** 편차용 — 부호를 붙여 적고 톤을 값의 부호에서 정한다. */
    signed?: boolean
    /**
     * 값이 없을 때 큰 숫자 자리에 적을 말. 이 저장소는 지표마다 말이 다르다 — 진척은 "산정 전"
     * (progressText), 편차는 "미산정"(varianceText). 한 단어로 묶으면 같은 화면의 진척 탭과
     * 대시보드가 같은 값을 다르게 부르게 된다.
     */
    unsetText?: string
  }>(),
  {
    unit: '',
    hint: null,
    tone: 'gray',
    percent: undefined,
    signed: false,
    unsetText: '산정 전',
  },
)

/** 편차는 소수 한 자리(roundPoints), 나머지는 정수 — 리디자인 전 화면과 같은 반올림이다. */
const display = computed(() => {
  if (props.value === null) return null
  if (!props.signed) return `${Math.round(props.value)}`
  const rounded = roundPoints(props.value)
  if (rounded === 0) return '±0'
  return rounded > 0 ? `+${rounded}` : `${rounded}`
})

const tone = computed(() => (props.signed ? varianceKpiTone(props.value) : props.tone))

const showBar = computed(() => props.percent !== undefined && props.percent !== null)
</script>

<template>
  <article class="kpi">
    <header>
      <span class="icon" :class="`tone-${tone}`" aria-hidden="true">
        <svg viewBox="0 0 24 24" width="18" height="18" focusable="false">
          <path :d="ICON_PATHS[icon]" fill="currentColor" />
        </svg>
      </span>
      <span class="label">{{ label }}</span>
    </header>

    <p v-if="display !== null" class="value" :class="`fg-${tone}`">
      <span class="num">{{ display }}</span>
      <span v-if="unit" class="unit">{{ unit }}</span>
    </p>
    <p v-else class="value unset">{{ unsetText }}</p>

    <MiniBar v-if="showBar" class="bar" :percent="percent ?? null" :tone="tone" />

    <p v-if="hint" class="hint">{{ hint }}</p>
  </article>
</template>

<style scoped>
/*
 * 카드 크롬은 DashCard 와 같은 토큰을 쓴다 — 라이트는 그림자로, 다크는 --card-outline 으로
 * 선다(다크에서는 그림자가 배경에 묻힌다).
 */
.kpi {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  min-width: 0;
  padding: var(--card-pad);
  border: 1px solid var(--card-outline);
  border-radius: var(--card-radius);
  background: var(--surface);
  box-shadow: var(--elevation-1);
}

header {
  display: flex;
  align-items: center;
  gap: 0.55rem;
  min-width: 0;
}

.icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 2.1rem;
  height: 2.1rem;
  flex: none;
  border-radius: 999px;
}

/* 톤은 클래스로 고르고 값은 전역 토큰에서만 온다 — 여기서 토큰을 재정의하지 않는다. */
.icon.tone-green {
  background: var(--tint-green);
  color: var(--tint-green-fg);
}

.icon.tone-blue {
  background: var(--tint-blue);
  color: var(--tint-blue-fg);
}

.icon.tone-violet {
  background: var(--tint-violet);
  color: var(--tint-violet-fg);
}

.icon.tone-amber {
  background: var(--tint-amber);
  color: var(--tint-amber-fg);
}

.icon.tone-red {
  background: var(--tint-red);
  color: var(--tint-red-fg);
}

.icon.tone-gray {
  background: var(--tint-gray);
  color: var(--tint-gray-fg);
}

.label {
  min-width: 0;
  font-size: 0.78rem;
  color: var(--text-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 숫자 우선 위계: 숫자는 크고 굵게, 설명은 작고 흐리게(지시서 1-6). */
.value {
  display: flex;
  align-items: baseline;
  gap: 0.1rem;
  font-size: 1.9rem;
  font-weight: 700;
  line-height: 1.15;
  color: var(--text-h);
  white-space: nowrap;
}

/*
 * 편차만 값의 부호를 색으로 거든다. 나머지 KPI 는 아이콘 원에서 이미 톤을 쓰므로 숫자는 중립
 * 글자색으로 둔다 — 숫자까지 칠하면 카드 다섯 장이 전부 다른 색이 되어 위계가 사라진다.
 */
.value.fg-green {
  color: var(--success-text);
}

.value.fg-red {
  color: var(--danger);
}

.unit {
  font-size: 0.85rem;
  font-weight: 600;
  color: var(--text-dim);
}

/* "산정 전"은 숫자가 아니다 — 0 으로 읽히지 않도록 크게 쓰지 않는다. */
.value.unset {
  font-size: 1rem;
  font-weight: 500;
  color: var(--text-faint);
  white-space: normal;
}

.bar {
  margin-top: 0.15rem;
}

/* 보조줄은 카드 바닥에 붙여, 막대가 있는 카드와 없는 카드의 줄이 나란히 선다. */
.hint {
  margin-top: auto;
  padding-top: 0.3rem;
  font-size: 0.78rem;
  color: var(--text-faint);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
