<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, RouterView, useRoute } from 'vue-router'
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
  <div class="app">
    <header class="app-header">
      <div class="brand">일정관리</div>
      <nav>
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

    <main class="app-main">
      <RouterView />
    </main>
  </div>
</template>

<style scoped>
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

.app-header {
  display: flex;
  align-items: center;
  gap: 2rem;
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
  gap: 1.25rem;
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
  gap: 1rem;
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

.theme-toggle {
  margin-left: auto;
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  padding: 0.3rem 0.6rem;
  border: 1px solid var(--border-input);
  border-radius: 999px;
  background: var(--surface);
  color: var(--text-muted);
  font: inherit;
  font-size: 0.75rem;
  cursor: pointer;
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
