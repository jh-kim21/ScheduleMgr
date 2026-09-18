<script setup lang="ts">
/**
 * 대시보드 카드의 크롬 전부 — 면 · 라운드 · 패딩 · 머리말 세 자리(제목 · 부제 · 우상단 액션).
 * **크롬은 여기만 갖는다**: 카드가 각자 테두리와 여백을 그리면 아홉 장이 조금씩 어긋나고,
 * 그리드를 바꿀 때 아홉 곳을 고쳐야 한다.
 *
 * <p>`tone="warn"` 은 카드 배경을 물들이지 않고 왼쪽 띠만 얹는다 — 카드 전체가 경고색이 되면
 * 정작 그 안의 배지와 문구가 묻힌다(기존 KPI 타일이 쓰던 규칙 그대로다).
 */
defineProps<{
  title: string
  /** 제목 아래 작고 흐린 한 줄. 이 카드의 숫자가 무엇 기준인지 적는다. */
  subtitle?: string | null
  tone?: 'default' | 'warn'
}>()
</script>

<template>
  <article class="dash-card" :class="{ warn: tone === 'warn' }">
    <header>
      <div class="heading">
        <h2>{{ title }}</h2>
        <p v-if="subtitle" class="subtitle">{{ subtitle }}</p>
      </div>
      <div v-if="$slots.action" class="action"><slot name="action" /></div>
    </header>
    <div class="body"><slot /></div>
  </article>
</template>

<style scoped>
.dash-card {
  min-width: 0;
  padding: var(--card-pad);
  border: 1px solid var(--card-outline);
  border-radius: var(--card-radius);
  background: var(--surface);
  box-shadow: var(--elevation-1);
}

/* inset 그림자로 두어야 라운드를 따라 띠가 잘린다. 바깥 고도는 그대로 얹는다. */
.dash-card.warn {
  box-shadow: inset 3px 0 0 var(--warn), var(--elevation-1);
}

/* 제목 · 부제 · 액션이 항상 같은 자리에 온다(지시서 1-3). */
header {
  display: flex;
  align-items: flex-start;
  gap: 0.75rem;
  margin-bottom: 0.9rem;
}

.heading {
  min-width: 0;
}

/* 페이지 h1(DashboardHeader) 바로 아래 카드 제목이라 h2다 — 예전에 h3이라 단계를 건너뛰었다. */
h2 {
  margin: 0;
  font-size: 0.95rem;
  font-weight: 600;
  color: var(--text-h);
}

.subtitle {
  margin-top: 0.15rem;
  font-size: 0.76rem;
  color: var(--text-faint);
}

.action {
  margin-left: auto;
  flex: none;
  font-size: 0.76rem;
  white-space: nowrap;
}

.body {
  display: flex;
  flex-direction: column;
  gap: 0.55rem;
  min-width: 0;
}

/*
 * 아래는 슬롯으로 들어온 카드 본문에 거는 공통 서식이다. 슬롯 내용은 *부모* 컴포넌트의 스코프
 * 아이디를 달고 오므로 평범한 선택자로는 닿지 않는다 — 그래서 :deep() 을 쓴다. 카드마다 같은
 * 목록 · 정의표를 다시 정의하면 아홉 장의 글자 크기가 조금씩 갈라진다.
 */
.body :deep(.rows) {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 0.25rem 0.8rem;
  margin: 0;
  font-size: 0.82rem;
}

.body :deep(.rows dt) {
  color: var(--text-faint);
}

.body :deep(.rows dd) {
  margin: 0;
  color: var(--text-muted);
}

.body :deep(.refs) {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
  font-size: 0.8rem;
}

.body :deep(.refs li) {
  display: flex;
  gap: 0.5rem;
  align-items: baseline;
  min-width: 0;
}

.body :deep(.refs li > :first-child) {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.body :deep(.refs .muted) {
  margin-left: auto;
  white-space: nowrap;
}

.body :deep(.muted) {
  font-size: 0.78rem;
  color: var(--text-faint);
}

.body :deep(.subhead) {
  margin: 0.25rem 0 0;
  font-size: 0.78rem;
  font-weight: 600;
  color: var(--text-muted);
}

.body :deep(.chips) {
  display: flex;
  flex-wrap: wrap;
  gap: 0.35rem;
}

.body :deep(.warn-note) {
  font-size: 0.8rem;
  line-height: 1.5;
  color: var(--warn-strong);
  background: var(--warn-weak);
  border-left: 3px solid var(--warn);
  border-radius: 6px;
  padding: 0.45rem 0.6rem;
}

.body :deep(.ok-note) {
  font-size: 0.8rem;
  color: var(--text-dim);
}

/* "+"와 "-"의 뜻은 ProgressPanel · KPI 와 같은 한 벌을 쓴다. */
.body :deep(.variance-ahead) {
  color: var(--success-text);
}

.body :deep(.variance-behind) {
  color: var(--danger);
}

.body :deep(.divider) {
  margin: 0.35rem 0 0;
  border: none;
  border-top: 1px solid var(--border-soft);
}
</style>
