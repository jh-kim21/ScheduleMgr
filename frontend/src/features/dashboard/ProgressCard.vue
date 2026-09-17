<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink } from 'vue-router'
import type { Dashboard } from '../../api/dashboardApi'
import { EXECUTION_MODE_LABELS } from '../../shared/executionMode'
import { PROGRESS_BASIS_LABELS, progressText } from '../../shared/progress'
import BarList from './BarList.vue'
import DashCard from './DashCard.vue'
import {
  PROGRESS_TAB,
  varianceSentence,
  varianceToneClass,
  type BarListItem,
} from './dashboardFormat'

/**
 * KPI 가 headline(진척 % · 근거 · 편차)만 보여주고 남긴 나머지: 계획 진척과 같은 범위의 실제,
 * Work Package 수와 산정 전 개수, 실행 방식 분포.
 */
const props = defineProps<{ data: Dashboard }>()

const basis = computed(() => {
  const value = props.data.progress.basis
  return value ? PROGRESS_BASIS_LABELS[value] : null
})

const variance = computed(() => varianceSentence(props.data.progress.variancePoints))

/** 분포는 Work Package 전체를 분모로 본다. 0 개면 BarList 가 막대 대신 문구를 쓴다. */
const modes = computed<BarListItem[]>(() =>
  props.data.workPackages.byExecutionMode.map((row) => ({
    key: row.mode ?? 'NONE',
    label: row.mode ? EXECUTION_MODE_LABELS[row.mode] : '미지정',
    value: row.count,
    total: props.data.workPackages.total,
    tone: row.mode === 'AGILE' ? 'blue' : row.mode === 'WATERFALL' ? 'violet' : row.mode === 'HYBRID' ? 'green' : 'gray',
  })),
)
</script>

<template>
  <DashCard
    title="진척 상세"
    :subtitle="basis"
    :tone="data.progress.incomplete ? 'warn' : 'default'"
  >
    <template #action>
      <RouterLink :to="PROGRESS_TAB">상세 ›</RouterLink>
    </template>

    <dl class="rows">
      <dt>계획 진척</dt>
      <dd v-if="data.progress.plannedPercent !== null">
        {{ progressText(data.progress.plannedPercent) }}
        <span class="muted">(기준선 v{{ data.baseline?.version }} 범위)</span>
      </dd>
      <dd v-else class="muted">미산정 — 승인된 기준선이 없습니다.</dd>

      <template v-if="variance">
        <dt>편차</dt>
        <dd :class="varianceToneClass(data.progress.variancePoints)">
          {{ variance }}
          <span class="muted">
            (같은 범위 실제 {{ progressText(data.progress.comparablePercent) }})
          </span>
        </dd>
      </template>

      <dt>Work Package</dt>
      <dd>
        {{ data.workPackages.total }}개
        <span v-if="data.workPackages.notEstimableCount > 0" class="muted">
          · 산정 전 {{ data.workPackages.notEstimableCount }}개
        </span>
      </dd>
    </dl>

    <p v-if="data.progress.incomplete" class="warn-note">
      일부 하위가 아직 산정 전이라 이 숫자에 빠져 있습니다. 0%로 대신하지 않았습니다.
    </p>

    <p class="subhead">실행 방식별 Work Package</p>
    <BarList :items="modes" empty-text="Work Package가 아직 없습니다." />
  </DashCard>
</template>
