<script setup lang="ts">
import { computed } from 'vue'
import { activeCommit, exitCommitView } from '../stores/commitView'

/**
 * The one place every screen learns it is looking at a commit instead of live data. Mounted once
 * in `App.vue` (not per view) so switching screens while in commit view cannot lose it.
 *
 * <p>`position: sticky` rather than `fixed`: a fixed banner would need `App.vue`'s layout to make
 * room for it (padding-top on the header), which is more than "mount only" should touch here.
 * Sticky sits in normal flow — it pushes the header down on its own — and still stays on screen
 * while the page scrolls.
 */
const label = computed(() => {
  const commit = activeCommit.value
  if (!commit) return ''
  const parts = [`${commit.asOf} 시점 (v${commit.version})`]
  if (commit.message) parts.push(`"${commit.message}"`)
  return parts.join(' · ')
})
</script>

<template>
  <div v-if="activeCommit" class="commit-banner" role="status">
    <span class="text">{{ label }} — 읽기 전용</span>
    <button type="button" class="exit" @click="exitCommitView">나가기</button>
  </div>
</template>

<style scoped>
.commit-banner {
  position: sticky;
  top: 0;
  z-index: 90;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 1rem;
  padding: 0.5rem 1rem;
  background: var(--warn-weak);
  color: var(--warn-strong);
  border-bottom: 1px solid var(--warn);
  font-size: 0.85rem;
}

/* 배경(transparent)이 전역 버튼과 같으므로 hover 오버레이(::before)는 전역이 그대로 그려 준다 —
   여기서 다시 정의할 필요가 없다. 남은 것은 이 배너 안에서만 쓰는 색·크기다. */
.exit {
  flex: none;
  padding: 0.25rem 0.75rem;
  border-color: var(--warn);
  border-radius: 999px;
  background: transparent;
  color: inherit;
  font-size: 0.78rem;
}
</style>
