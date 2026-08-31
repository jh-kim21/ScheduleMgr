<script setup lang="ts">
defineProps<{
  title: string
}>()
</script>

<template>
  <section class="placeholder">
    <h1>{{ title }}</h1>
    <p>준비 중인 기능입니다.</p>
  </section>
</template>

<style scoped>
.placeholder {
  padding: 2rem 0;
  color: var(--text-dim);
}

h1 {
  font-size: 1.4rem;
  color: var(--text-h);
  margin-bottom: 0.5rem;
}
</style>
