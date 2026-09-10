<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'

/**
 * The dialog shell every 추가/수정 폼이 사용한다.
 *
 * 입력 폼을 화면에 고정 패널로 두면 표를 아래로 밀어내는데, 이 앱의 화면들은 대부분 *읽는* 것이
 * 주된 행위다. 그래서 폼은 필요할 때만 위에 띄운다.
 *
 * `body`로 teleport 한다 — 표 행이나 스크롤 컨테이너 안에서 렌더하면 `overflow`를 가진 조상에
 * 걸려 잘린다(내보내기 메뉴·간트 툴팁이 같은 이유로 같은 방식).
 */
const props = defineProps<{
  title: string
  /** 필드가 많은 폼(RAID·Backlog)은 넓게, 단순한 폼은 좁게. */
  size?: 'md' | 'lg'
  /**
   * 저장 거부 메시지. 화면 본문에도 같은 값이 렌더되지만 대화상자가 그것을 덮으므로, 사용자가
   * 보고 있는 곳에 다시 보여 준다 — 안 그러면 저장이 조용히 실패한 것처럼 보인다.
   */
  error?: string | null
}>()

const emit = defineEmits<{
  close: []
}>()

const panel = ref<HTMLElement | null>(null)
let previouslyFocused: HTMLElement | null = null

const FOCUSABLE =
  'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])'

function focusable(): HTMLElement[] {
  return [...(panel.value?.querySelectorAll<HTMLElement>(FOCUSABLE) ?? [])]
}

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') {
    event.preventDefault()
    emit('close')
    return
  }
  if (event.key !== 'Tab') return

  // Tab 이 대화상자 밖으로 나가면 배경 폼으로 포커스가 새어나가므로 안에서 순환시킨다.
  const items = focusable()
  if (items.length === 0) return
  const first = items[0]
  const last = items[items.length - 1]
  const active = document.activeElement

  if (event.shiftKey && (active === first || !panel.value?.contains(active))) {
    event.preventDefault()
    last.focus()
  } else if (!event.shiftKey && active === last) {
    event.preventDefault()
    first.focus()
  }
}

onMounted(() => {
  previouslyFocused = document.activeElement as HTMLElement | null
  document.addEventListener('keydown', onKeydown)
  // 대화상자가 떠 있는 동안 배경이 스크롤되면 뒤에 있는 표가 따라 움직여 혼란스럽다.
  document.body.style.overflow = 'hidden'
  requestAnimationFrame(() => {
    const fields = panel.value?.querySelectorAll<HTMLElement>(
      'input:not([type="hidden"]):not([disabled]), select:not([disabled]), textarea:not([disabled])',
    )
    // 첫 입력 칸에 포커스를 두어 열자마자 타이핑할 수 있게 한다. 없으면 닫기 버튼으로 둔다.
    ;(fields?.[0] ?? focusable()[0])?.focus()
  })
})

onBeforeUnmount(() => {
  document.removeEventListener('keydown', onKeydown)
  document.body.style.overflow = ''
  previouslyFocused?.focus?.()
})

/** 배경을 눌렀을 때만 닫는다 — 패널 안에서 올라온 클릭은 무시한다. */
function onBackdropPointerDown(event: MouseEvent) {
  if (event.target === event.currentTarget) emit('close')
}
</script>

<template>
  <Teleport to="body">
    <div class="backdrop" @mousedown="onBackdropPointerDown">
      <div
        ref="panel"
        class="panel"
        :class="`size-${props.size ?? 'md'}`"
        role="dialog"
        aria-modal="true"
        :aria-label="title"
      >
        <header class="head">
          <h2>{{ title }}</h2>
          <button type="button" class="close" aria-label="닫기" @click="emit('close')">✕</button>
        </header>
        <div class="body">
          <p v-if="props.error" class="error" role="alert">{{ props.error }}</p>
          <slot />
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.backdrop {
  position: fixed;
  inset: 0;
  z-index: 100;
  display: flex;
  align-items: flex-start;
  justify-content: center;
  padding: 3rem 1rem;
  overflow-y: auto;
  /* 다크 모드에서는 배경이 이미 어두워 42%로는 층이 갈라져 보이지 않는다. */
  background: rgb(0 0 0 / 55%);
  animation: fade 120ms ease-out;
}

.panel {
  width: 100%;
  max-width: 34rem;
  max-height: calc(100vh - 6rem);
  display: flex;
  flex-direction: column;
  background: var(--surface-raised);
  border: 1px solid var(--border);
  border-radius: 12px;
  box-shadow: var(--elevation-3);
  animation: rise 140ms ease-out;
}

.panel.size-lg {
  max-width: 52rem;
}

.head {
  display: flex;
  align-items: center;
  gap: 1rem;
  padding: 0.9rem 1.1rem;
  border-bottom: 1px solid var(--border-soft);
}

.head h2 {
  margin: 0;
  font-size: 1rem;
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.close {
  flex: none;
  width: 1.75rem;
  height: 1.75rem;
  display: grid;
  place-items: center;
  border: none;
  border-radius: 50%;
  background: transparent;
  color: var(--text-muted);
  font-size: 0.85rem;
  cursor: pointer;
}

.close:hover {
  background: var(--state-hover);
  color: var(--text-h);
}

.close:active {
  background: var(--state-press);
}

.body {
  padding: 1.1rem;
  overflow-y: auto;
}

.error {
  margin-bottom: 0.85rem;
  padding: 0.5rem 0.7rem;
  border-radius: 6px;
  border-left: 3px solid var(--danger);
  background: var(--danger-weak);
  color: var(--danger);
  font-size: 0.83rem;
  /* WBS 파일 가져오기처럼 행마다 사유를 나열하는 거부 메시지는 줄바꿈이 있어야 읽힌다. */
  white-space: pre-line;
}

@keyframes fade {
  from {
    opacity: 0;
  }
}

@keyframes rise {
  from {
    opacity: 0;
    transform: translateY(-0.5rem);
  }
}

@media (prefers-reduced-motion: reduce) {
  .backdrop,
  .panel {
    animation: none;
  }
}
</style>
