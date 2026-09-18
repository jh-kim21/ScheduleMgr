<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, RouterView, useRoute } from 'vue-router'
import CommitBanner from './components/CommitBanner.vue'
import {
  cycleThemePreference,
  THEME_ICONS,
  THEME_LABELS,
  themePreference,
} from './stores/theme'

const route = useRoute()

/**
 * Agile 묶음 (설계서 §2.2). 세 화면은 한 흐름의 세 단계라 최상위에 나란히 두면 관계가 보이지
 * 않는다 — Backlog 에서 계획하고, Sprint 로 담고, Board 에서 실행한다.
 *
 * <p>드롭다운 대신 하위 줄로 둔 이유: 팝업은 바깥 클릭·키보드·잘림 처리를 다 얹어야 하는데
 * (내보내기 메뉴가 그렇다), 여기서 얻는 것은 항목 세 개를 감추는 것뿐이다. 묶음 안에 있을 때만
 * 펼치면 평소 헤더도 그대로 짧다.
 */
const AGILE_PATHS = ['/backlog', '/sprint', '/board']
const inAgile = computed(() => AGILE_PATHS.includes(route.path))
</script>

<template>
  <!-- 본문 바로가기(WCAG 2.4.1) — 첫 Tab에서 나타나 주 메뉴 7개+Agile 하위 줄을 건너뛰고
       바로 <main>으로 간다. 평소에는 화면 밖으로 숨겨 두고 포커스를 받을 때만 보인다. -->
  <a href="#main-content" class="skip-link">본문 바로가기</a>
  <CommitBanner />
  <div class="app">
    <header class="app-header">
      <div class="brand">일정관리</div>
      <nav aria-label="주 메뉴">
        <RouterLink to="/projects">프로젝트</RouterLink>
        <RouterLink to="/wbs">WBS</RouterLink>
        <RouterLink to="/backlog" class="group" :class="{ 'group-active': inAgile }">Agile</RouterLink>
        <RouterLink to="/gantt">간트 차트</RouterLink>
        <RouterLink to="/raci">RACI</RouterLink>
        <RouterLink to="/raid">RAID</RouterLink>
        <RouterLink to="/dashboard">Dashboard</RouterLink>
      </nav>
      <button
        type="button"
        class="theme-toggle"
        :title="`화면 테마: ${THEME_LABELS[themePreference]} (클릭하면 전환)`"
        :aria-label="`화면 테마: ${THEME_LABELS[themePreference]}`"
        @click="cycleThemePreference"
      >
        <span class="icon" aria-hidden="true">{{ THEME_ICONS[themePreference] }}</span>
        {{ THEME_LABELS[themePreference] }}
      </button>
    </header>
    <nav v-if="inAgile" class="subnav" aria-label="Agile">
      <RouterLink to="/backlog">Backlog</RouterLink>
      <RouterLink to="/sprint">Sprint</RouterLink>
      <RouterLink to="/board">Board</RouterLink>
    </nav>

    <!-- tabindex="-1": 시퀀스 탭 순서에는 끼지 않지만, 바로가기 링크가 해시로 옮긴 포커스는
         받을 수 있다 — <main>은 원래 포커스 가능한 요소가 아니다. -->
    <main id="main-content" class="app-main" tabindex="-1">
      <RouterView />
    </main>
  </div>
</template>

<style scoped>
/*
 * 표준 skip-link 패턴 — 평소에는 화면 밖(위로 이동)에 둬 레이아웃에 자리를 차지하지 않다가,
 * 키보드로 포커스를 받는 순간(`:focus`) 화면 안으로 들어온다. `position: fixed`라 스크롤
 * 위치와 무관하게 항상 뷰포트 맨 위에 나타난다.
 */
.skip-link {
  position: fixed;
  top: -3rem;
  left: 1rem;
  z-index: 200;
  padding: 0.5rem 0.9rem;
  border-radius: var(--radius-md);
  background: var(--accent);
  color: var(--accent-fg);
  font-size: 0.85rem;
  text-decoration: none;
  transition: top 120ms ease;
}

.skip-link:focus {
  top: 1rem;
}

@media (prefers-reduced-motion: reduce) {
  .skip-link {
    transition: none;
  }
}

/*
 * 표와 간트 차트가 주된 내용이라 폭이 넓을수록 한 화면에 들어오는 열이 늘어난다. 960px 은 본문
 * 위주 페이지의 읽기 폭이라 이 앱에서는 좌우가 비어 보였다.
 *
 * 여전히 상한을 두는 이유: 아주 넓은 화면에서 헤더의 메뉴가 양 끝으로 흩어지고 표가 화면 폭만큼
 * 늘어나면 시선 이동이 오히려 커진다.
 */
.app {
  max-width: 1600px;
  margin: 0 auto;
  padding: 0 1rem;
}

/*
 * flex-wrap: wrap — 메뉴 7개(간트·RACI·RAID 등) + 테마 토글이 좁은 화면(태블릿 768px 안팎)에서
 * 한 줄에 다 안 들어가면 두 번째 줄로 접힌다. 항목이 한 줄에 다 들어가는 폭에서는 아무 효과가
 * 없으므로 넓은 화면의 모양은 그대로다 — flex 항목은 기본으로 자기 내용보다 줄어들지 않아서(
 * `min-width: auto`), wrap이 없으면 화면 전체가 가로로 밀려 스크롤이 생긴다.
 */
.app-header {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.75rem 2rem;
  padding: 1.25rem 0;
  border-bottom: 1px solid var(--border-soft);
}

.brand {
  font-weight: 700;
  font-size: 1.1rem;
  color: var(--text-h);
}

nav {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem 1.25rem;
}

nav a {
  color: var(--text-muted);
  text-decoration: none;
  font-size: 0.9rem;
}

nav a.router-link-active {
  color: var(--accent);
  font-weight: 600;
}

/*
 * Agile 은 자기 화면이 없는 묶음이라 /backlog 로 간다. 그래서 Backlog 에 있을 때 상위와 하위가
 * 둘 다 굵어지는데, 상위는 "지금 이 묶음 안"이라는 뜻이므로 맞다. 다만 router-link-active 만으로는
 * Sprint·Board 에서 상위가 꺼지므로 inAgile 로 직접 칠한다.
 */
nav a.group-active {
  color: var(--accent);
  font-weight: 600;
}

.subnav {
  display: flex;
  flex-wrap: wrap;
  gap: 0.4rem 1rem;
  padding: 0.55rem 0;
  border-bottom: 1px solid var(--border-soft);
}

.subnav a {
  color: var(--text-faint);
  text-decoration: none;
  font-size: 0.82rem;
}

.subnav a.router-link-active {
  color: var(--accent);
  font-weight: 600;
}

/* 전역 버튼 기본(패딩·테두리·배경·`font: inherit`·`cursor`)과 겹치는 선언은 지웠다 — 여기 남은
   것은 이 토글만의 것(알약 모양, 작은 크기, 오른쪽 밀기)이다. */
.theme-toggle {
  margin-left: auto;
  gap: 0.35rem;
  padding: 0.3rem 0.6rem;
  border-radius: 999px;
  color: var(--text-muted);
  font-size: 0.75rem;
  white-space: nowrap;
}

.theme-toggle:hover {
  border-color: var(--accent-border);
  color: var(--text-h);
}

.theme-toggle .icon {
  font-size: 0.85rem;
  line-height: 1;
}

.app-main {
  padding: 1.5rem 0 3rem;
}
</style>
