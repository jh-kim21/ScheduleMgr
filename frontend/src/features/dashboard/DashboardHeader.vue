<script setup lang="ts">
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
defineProps<{
  projects: Project[]
  tab: 'summary' | 'progress'
  referenceDate: string | null
  loading: boolean
}>()

const emit = defineEmits<{
  'update:tab': ['summary' | 'progress']
  refresh: []
}>()
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
    <div class="tabs" role="tablist">
      <button
        type="button"
        role="tab"
        :aria-selected="tab === 'summary'"
        :class="{ active: tab === 'summary' }"
        @click="emit('update:tab', 'summary')"
      >요약</button>
      <button
        type="button"
        role="tab"
        :aria-selected="tab === 'progress'"
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

.project-picker select {
  padding: 0.4rem 0.6rem;
  border: 1px solid var(--border-input);
  border-radius: 8px;
  background: var(--surface);
  color: var(--text);
  font: inherit;
}

.reference {
  font-size: 0.78rem;
  color: var(--text-faint);
}

.refresh {
  padding: 0.35rem 0.9rem;
  border: 1px solid var(--border-input);
  border-radius: 999px;
  background: var(--surface);
  color: var(--text-muted);
  font: inherit;
  font-size: 0.8rem;
  cursor: pointer;
}

.refresh:disabled {
  background: var(--disabled-bg);
  border-color: var(--disabled-border);
  color: var(--disabled-fg);
  cursor: default;
}

.tabs {
  display: flex;
  gap: 0.25rem;
  flex-basis: 100%;
  border-bottom: 1px solid var(--border);
}

.tabs button {
  padding: 0.45rem 0.9rem;
  border: none;
  border-bottom: 2px solid transparent;
  background: none;
  color: var(--text-muted);
  font: inherit;
  font-size: 0.85rem;
  cursor: pointer;
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
