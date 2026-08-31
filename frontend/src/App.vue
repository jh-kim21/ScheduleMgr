<script setup lang="ts">
import { RouterLink, RouterView } from 'vue-router'
import {
  cycleThemePreference,
  THEME_ICONS,
  THEME_LABELS,
  themePreference,
} from './stores/theme'
</script>

<template>
  <div class="app">
    <header class="app-header">
      <div class="brand">일정관리</div>
      <nav>
        <RouterLink to="/projects">프로젝트</RouterLink>
        <RouterLink to="/wbs">WBS</RouterLink>
        <RouterLink to="/gantt">간트 차트</RouterLink>
        <RouterLink to="/raci">RACI</RouterLink>
        <RouterLink to="/raid">RAID</RouterLink>
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
    <main class="app-main">
      <RouterView />
    </main>
  </div>
</template>

<style scoped>
.app {
  max-width: 960px;
  margin: 0 auto;
  padding: 0 1.5rem;
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
