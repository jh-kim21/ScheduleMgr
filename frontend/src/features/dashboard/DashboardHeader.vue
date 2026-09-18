<script setup lang="ts">
import { nextTick } from 'vue'
import type { Project } from '../../api/projectApi'
import { selectedProjectId } from '../../stores/projectSelection'

/**
 * §2 배치안의 머리말 한 덩이 — 제목 · 프로젝트 선택 · 기준일 · 새로고침 · 탭.
 *
 * <p>탭의 *상태*는 뷰가 갖는다(주소의 ?tab= 과 맞물려 있다). 여기는 그리기와 클릭만 맡아,
 * 라우팅 규칙이 한 곳에 남는다.
 *
 * <p>기준일은 언제나 서버가 정한 값(`referenceDate`)이다 — 클라이언트 시계를 쓰면 오래 열어둔
 * 탭에서 카드의 판정과 화면에 적힌 날짜가 어긋난다.
 */
const props = defineProps<{
  projects: Project[]
  tab: 'summary' | 'progress'
  referenceDate: string | null
  loading: boolean
}>()

const emit = defineEmits<{
  'update:tab': ['summary' | 'progress']
  refresh: []
}>()

/**
 * ARIA Tabs의 방향키 이동(WAI-ARIA APG) — 탭이 둘뿐이라 어느 방향이든 서로를 오간다. 선택과
 * 포커스를 함께 옮기는 "automatic activation"을 쓴다: 탭이 둘뿐이라 골라만 두고 다른 키(Enter
 * 등)로 다시 확정하게 하면 손이 하나 더 간다. 패널은 `DashboardView`가 그리므로 `id`만 여기서
 * 정해 그 쪽의 `aria-labelledby`/`id`와 맞춘다.
 */
function onTabsKeydown(event: KeyboardEvent) {
  if (event.key !== 'ArrowLeft' && event.key !== 'ArrowRight') return
  event.preventDefault()
  const next = props.tab === 'summary' ? 'progress' : 'summary'
  emit('update:tab', next)
  nextTick(() => {
    document.getElementById(`dashboard-tab-${next}`)?.focus()
  })
}
</script>

<template>
  <header class="page-head">
    <div class="titles">
      <h1>프로젝트 대시보드</h1>
      <p class="lede">WBS + Agile 통합 현황</p>
    </div>

    <div class="tools">
      <label class="project-picker">
        프로젝트
        <select v-model="selectedProjectId">
          <option v-for="project in projects" :key="project.id" :value="project.id">
            {{ project.name }}
          </option>
        </select>
      </label>
      <span v-if="referenceDate" class="reference num">기준일 {{ referenceDate }}</span>
      <button
        v-if="tab === 'summary'"
        type="button"
        class="refresh"
        :disabled="loading"
        @click="emit('refresh')"
      >새로고침</button>
    </div>

    <!-- 탭은 링크가 아니라 같은 화면의 두 면이라 버튼으로 둔다. -->
    <div class="tabs" role="tablist" @keydown="onTabsKeydown">
      <button
        id="dashboard-tab-summary"
        type="button"
        role="tab"
        :aria-selected="tab === 'summary'"
        :tabindex="tab === 'summary' ? 0 : -1"
        aria-controls="dashboard-panel-summary"
        :class="{ active: tab === 'summary' }"
        @click="emit('update:tab', 'summary')"
      >요약</button>
      <button
        id="dashboard-tab-progress"
        type="button"
        role="tab"
        :aria-selected="tab === 'progress'"
        :tabindex="tab === 'progress' ? 0 : -1"
        aria-controls="dashboard-panel-progress"
        :class="{ active: tab === 'progress' }"
        @click="emit('update:tab', 'progress')"
      >진척</button>
    </div>
  </header>
</template>

<style scoped>
.page-head {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  gap: 0.75rem 1.5rem;
  margin-bottom: 1.25rem;
}

h1 {
  margin: 0;
  font-size: 1.35rem;
}

.lede {
  margin-top: 0.15rem;
  font-size: 0.8rem;
  color: var(--text-faint);
}

.tools {
  display: flex;
  align-items: center;
  gap: 1rem;
  margin-left: auto;
}

.project-picker {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.85rem;
  color: var(--text-muted);
}

/* 패딩·border·배경·글자색·font는 전역 기본과 같거나 아주 가까워 지웠다 — 이 셀렉트만 8px
   radius(전역 기본은 6px)를 쓴다. */
.project-picker select {
  border-radius: 8px;
}

.reference {
  font-size: 0.78rem;
  color: var(--text-faint);
}

/* border·배경·font·cursor는 전역 기본과 같아 지웠다 — 알약 모양(radius)과 색·크기만 남는다.
   :disabled는 전역이 특이도로 이미 이기고 있던 죽은 선언이라(값도 거의 같음) 지웠다. */
.refresh {
  padding: 0.35rem 0.9rem;
  border-radius: 999px;
  color: var(--text-muted);
  font-size: 0.8rem;
}

.tabs {
  display: flex;
  gap: 0.25rem;
  flex-basis: 100%;
  border-bottom: 1px solid var(--border);
}

/* 패딩은 전역 기본과 완전히 같은 값이라 지웠다 — font·cursor도 마찬가지. border를 없애고
   밑줄만 남기는 것이 이 탭 모양의 핵심이라 그대로 둔다. */
.tabs button {
  border: none;
  border-bottom: 2px solid transparent;
  background: none;
  color: var(--text-muted);
  font-size: 0.85rem;
}

.tabs button.active {
  color: var(--accent);
  border-bottom-color: var(--accent);
  font-weight: 600;
}

@media (max-width: 47.5rem) {
  .tools {
    margin-left: 0;
    flex-wrap: wrap;
  }
}
</style>
