<script setup lang="ts">
/**
 * 상태 · 등급 알약. 평문으로 흩어져 있던 "지연 14 · 위험 6 · 임계 9" 같은 값을 한 모양으로 묶는다.
 *
 * <p>색을 새로 만들지 않고 이미 배지가 쓰던 토큰(--badge-* / --warn-badge-* / --danger / --success)을
 * 그대로 쓴다. 칩이 자기 팔레트를 가지면 다크 모드를 한 벌 더 관리하게 된다.
 *
 * <p>색만으로 뜻을 전하지 않는다 — 라벨에 항상 무엇을 세는지 적는다("지연 14"). 흑백 화면과
 * 색각 이상에서도 같은 값을 읽을 수 있어야 한다.
 */
defineProps<{
  label: string
  tone: 'neutral' | 'planned' | 'warn' | 'danger' | 'success'
}>()
</script>

<template>
  <span class="chip num" :class="`tone-${tone}`">{{ label }}</span>
</template>

<style scoped>
.chip {
  display: inline-flex;
  align-items: center;
  padding: 0.15rem 0.6rem;
  border-radius: 999px;
  font-size: 0.74rem;
  font-weight: 600;
  white-space: nowrap;
}

.tone-neutral {
  background: var(--badge-neutral-bg);
  color: var(--badge-neutral-fg);
}

.tone-planned {
  background: var(--badge-planned-bg);
  color: var(--badge-planned-fg);
}

.tone-warn {
  background: var(--warn-badge-bg);
  color: var(--warn-badge-fg);
}

.tone-danger {
  background: var(--danger-weak);
  color: var(--danger);
}

.tone-success {
  background: var(--success-weak);
  color: var(--success-text);
}
</style>
