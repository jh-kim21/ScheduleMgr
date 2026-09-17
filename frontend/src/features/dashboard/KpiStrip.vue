<script setup lang="ts">
import { computed } from 'vue'
import type { Dashboard } from '../../api/dashboardApi'
import { PROGRESS_BASIS_LABELS } from '../../shared/progress'
import KpiCard from './KpiCard.vue'
import { latestClosedSprint } from './dashboardFormat'

/**
 * 화면 맨 위 한 줄. "이 프로젝트 지금 괜찮은가"에 바로 답하는 다섯 숫자만 온다 — 그 숫자의
 * 근거 · 목록 · 부가 정보는 전부 아래 상세 카드에 그대로 남아 있다. 새 지표를 얹을 때 "한눈에
 * 볼 값"이면 여기, "근거 · 목록"이면 아래 카드로 보낸다.
 *
 * <p>"지난주 대비" 증감은 넣지 않는다 — 과거 값이 남아 있는 지표가 사실상 없어서, 넣으면 다섯 칸
 * 중 서넛이 거짓이 된다(지시서 2절 · 부록 A-4).
 */
const props = defineProps<{ data: Dashboard }>()

const basisHint = computed(() => {
  const basis = props.data.progress.basis
  return basis ? PROGRESS_BASIS_LABELS[basis] : '근거 미정'
})

/** 계획 진척의 유일한 근거는 승인된 기준선이다. 없으면 왜 미산정인지 여기에 적는다. */
const baselineHint = computed(() =>
  props.data.baseline ? `기준선 v${props.data.baseline.version} 범위` : '승인된 기준선 없음',
)

/** 속도는 가장 최근에 *끝난* Sprint 의 실적이다. 진행 중인 것은 아직 움직이는 숫자다. */
const lastClosed = computed(() => latestClosedSprint(props.data.velocity))
</script>

<template>
  <div class="kpis">
    <KpiCard
      label="전체 진척"
      icon="progress"
      tone="green"
      unit="%"
      :value="data.progress.actualPercent"
      :percent="data.progress.actualPercent"
      :hint="basisHint"
    />
    <KpiCard
      label="계획 대비"
      icon="variance"
      unit="%p"
      signed
      unset-text="미산정"
      :value="data.progress.variancePoints"
      :hint="baselineHint"
    />
    <KpiCard
      label="지연 업무"
      icon="delay"
      tone="red"
      :value="data.schedule.delayedCount"
      :hint="`지연 위험 ${data.schedule.atRiskCount}건`"
    />
    <KpiCard
      label="인수 대기"
      icon="acceptance"
      tone="amber"
      :value="data.workPackages.acceptancePendingCount"
      hint="실행은 끝났고 승인이 남음"
    />
    <KpiCard
      label="스프린트 속도"
      icon="velocity"
      tone="violet"
      unit="pt"
      :value="lastClosed?.donePoints ?? null"
      :hint="lastClosed?.name ?? '종료된 Sprint 없음'"
    />
  </div>
</template>

<style scoped>
/*
 * 1400px 이상에서 다섯 장 한 줄. 좁아지면 3 + 2 (여섯 칸 그리드에 각 두 칸), 2장씩, 1장씩으로
 * 접힌다(지시서 3-d). 어느 구간에서도 minmax(0, 1fr) 이라 긴 Sprint 이름이 칸을 밀지 않는다.
 */
.kpis {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: var(--card-gap);
  align-items: stretch;
}

@media (max-width: 87.5rem) {
  .kpis {
    grid-template-columns: repeat(6, minmax(0, 1fr));
  }

  .kpis > * {
    grid-column: span 2;
  }
}

@media (max-width: 68.75rem) {
  .kpis {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .kpis > * {
    grid-column: auto;
  }
}

@media (max-width: 47.5rem) {
  .kpis {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
