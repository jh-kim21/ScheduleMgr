<script lang="ts">
/**
 * 모듈 스코프 대화상자 스택 — 이 파일이 몇 번 컴포넌트로 인스턴스화되든 단 하나만 존재한다
 * (일반 `<script setup>`의 최상위 코드는 인스턴스마다 다시 실행되지만, 이 `<script>` 블록은
 * 모듈이 처음 로드될 때 한 번만 실행된다 — useProjects 등 컴포저블이 파일을 따로 두는 것과
 * 같은 이유를, 같은 파일 안에서 얻는 방법이다).
 *
 * 앱이 실제로 대화상자를 중첩해 쓴다(MemberEditor의 "구성원 관리" 안 "구성원 추가"가 일상
 * 경로고, CommitPanel은 용량 초과 시 3중첩까지 간다). 단일 인스턴스를 전제로 짜여 있던
 * 이전 구현은 세 가지가 깨졌다 — Escape가 전부 닫히고, 바깥 인스턴스가 안쪽 패널의 포커스를
 * 보고 자기 Tab 트랩을 오작동시키고, 안쪽이 닫히면 바깥이 열려 있어도 배경 스크롤이 풀렸다.
 * 셋 다 "최상단 하나만 반응한다"는 규칙이 없었던 게 원인이라, 스택으로 그 규칙 자체를 만든다.
 */
type StackEntry = { token: symbol; setInert(inert: boolean): void }
const stack: StackEntry[] = []

function pushDialog(entry: StackEntry) {
  // 새로 여는 대화상자가 최상단이 되므로, 지금까지 최상단이었던 것은 배경으로 물러난다 —
  // 스크린리더 가상 커서와 Tab이 더 이상 거기로 가면 안 된다.
  stack[stack.length - 1]?.setInert(true)
  stack.push(entry)
}

function popDialog(token: symbol) {
  const index = stack.findIndex((entry) => entry.token === token)
  if (index !== -1) stack.splice(index, 1)
  // 새 최상단(원래 그 아래 깔려 있던 것)을 다시 상호작용 가능하게 되돌린다.
  stack[stack.length - 1]?.setInert(false)
}

function isTopDialog(token: symbol): boolean {
  return stack.length > 0 && stack[stack.length - 1].token === token
}

// 배경 스크롤 잠금도 참조 카운트로 다룬다 — 마지막 하나가 닫힐 때만 원래 값으로 되돌린다.
// `''`로 하드코딩해 되돌리면 이 코드가 열기 전에 다른 코드가 설정해 둔 값을 지워 버린다.
let scrollLockCount = 0
let savedOverflow = ''

function lockScroll() {
  if (scrollLockCount === 0) savedOverflow = document.body.style.overflow
  scrollLockCount += 1
  document.body.style.overflow = 'hidden'
}

function unlockScroll() {
  scrollLockCount = Math.max(0, scrollLockCount - 1)
  if (scrollLockCount === 0) document.body.style.overflow = savedOverflow
}
</script>

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
 *
 * 중첩 지원: 위 모듈 스코프 스택을 참고. 인스턴스마다 keydown 리스너를 걸지만(기존과 같은
 * 방식), 실제 처리는 자기가 스택 최상단일 때만 한다 — 그래서 안쪽 대화상자가 열려 있는 동안
 * 바깥 인스턴스는 Escape에도 Tab에도 반응하지 않는다.
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

const backdrop = ref<HTMLElement | null>(null)
const panel = ref<HTMLElement | null>(null)
let previouslyFocused: HTMLElement | null = null

/** 이 인스턴스를 스택에서 식별하는 토큰. 값 자체는 의미가 없고 유일하기만 하면 된다. */
const token = Symbol('modal-dialog')

const FOCUSABLE =
  'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])'

function focusable(): HTMLElement[] {
  return [...(panel.value?.querySelectorAll<HTMLElement>(FOCUSABLE) ?? [])]
}

/**
 * 배경(비최상단)으로 물러난 대화상자를 스크린리더 가상 커서와 Tab에서 완전히 뺀다.
 * `inert`가 이미 상호작용·접근성 트리 노출을 함께 막지만, 지원이 애매한 환경을 위해
 * `aria-hidden`도 같이 건다. 자기 자신에게는 절대 걸지 않는다(포커스를 잃는다) — 항상
 * "내가 최상단이 아닐 때"만 호출된다.
 */
function setInert(inert: boolean) {
  const el = backdrop.value
  if (!el) return
  if (inert) {
    el.setAttribute('inert', '')
    el.setAttribute('aria-hidden', 'true')
  } else {
    el.removeAttribute('inert')
    el.removeAttribute('aria-hidden')
  }
}

function onKeydown(event: KeyboardEvent) {
  // 스택 최상단이 아니면 이 인스턴스는 조용히 있는다 — 안쪽 대화상자가 열려 있는데 바깥이
  // Escape로 같이 닫히거나, 안쪽 패널에 있는 포커스를 보고 자기 Tab 트랩을 오작동시키던
  // 문제가 이 한 줄로 없어진다.
  if (!isTopDialog(token)) return

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
  const outside = !panel.value?.contains(active)

  // 정방향·역방향을 대칭으로 둔다 — 포커스가 패널 밖에 있으면(정상적으로는 일어나지 않지만,
  // 방어적으로) 어느 방향으로 Tab 을 눌러도 패널 안으로 돌아와야 한다.
  if (event.shiftKey && (active === first || outside)) {
    event.preventDefault()
    last.focus()
  } else if (!event.shiftKey && (active === last || outside)) {
    event.preventDefault()
    first.focus()
  }
}

onMounted(() => {
  // 스택에 올리기 전에 먼저 캡처해야 한다 — pushDialog 가 이전 최상단을 inert 로 돌리면
  // (스펙상 포커스가 옮겨갈 수 있다) 그 전에 "지금 뭐에 포커스가 있었는지"를 알아 둬야 한다.
  previouslyFocused = document.activeElement as HTMLElement | null
  pushDialog({ token, setInert })
  document.addEventListener('keydown', onKeydown)
  // 대화상자가 떠 있는 동안 배경이 스크롤되면 뒤에 있는 표가 따라 움직여 혼란스럽다.
  lockScroll()
  requestAnimationFrame(() => {
    // 명시적으로 지정된 초기 포커스가 있으면 그것을 우선한다 — 예를 들어 파괴적 확인
    // 대화상자는 확인 버튼이 아니라 취소 버튼에 `autofocus`를 붙여 실수로 그대로
    // Enter 를 눌러 넘어가는 사고를 막는다.
    const explicit = panel.value?.querySelector<HTMLElement>('[autofocus]')
    if (explicit) {
      explicit.focus()
      return
    }
    const fields = panel.value?.querySelectorAll<HTMLElement>(
      'input:not([type="hidden"]):not([disabled]), select:not([disabled]), textarea:not([disabled])',
    )
    // 첫 입력 칸에 포커스를 두어 열자마자 타이핑할 수 있게 한다. 없으면 닫기 버튼으로 둔다.
    ;(fields?.[0] ?? focusable()[0])?.focus()
  })
})

onBeforeUnmount(() => {
  document.removeEventListener('keydown', onKeydown)
  unlockScroll()
  // 포커스를 되돌리기 전에 스택에서 먼저 빠져야 한다 — popDialog 가 아래 대화상자를 다시
  // 상호작용 가능하게 되돌려야, previouslyFocused 가 가리키는(그 대화상자 안의) 요소가
  // 실제로 focus() 를 받을 수 있다.
  popDialog(token)
  previouslyFocused?.focus?.()
})

/** 배경을 눌렀을 때만 닫는다 — 패널 안에서 올라온 클릭은 무시한다. */
function onBackdropPointerDown(event: MouseEvent) {
  if (event.target === event.currentTarget) emit('close')
}
</script>

<template>
  <Teleport to="body">
    <div ref="backdrop" class="backdrop" @mousedown="onBackdropPointerDown">
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
  /* Step 1에서 만든 공용 scrim 토큰 — 손으로 만든 대화상자 3곳이 42%/45%를 따로 쓰던 것과
     하나로 통일됐다(다크는 배경이 이미 어두워 알파를 더 올린 값). */
  background: var(--scrim);
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
  border-radius: var(--radius-lg);
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

/*
 * 컨트롤 층(Step 1)의 hover·press 상태 레이어(::before, --state-hover/--state-press)는 요소
 * 선택자라 이 버튼에도 이미 걸린다 — 여기서 다시 background 를 지정하면 두 층이 겹쳐 두 배로
 * 진해진다. 이 규칙에는 그 두 상태에서 배경을 손대지 않고, 전역이 주지 않는 것(원형 모양·
 * 고정 크기·hover 시 글자색)만 남긴다. `padding: 0`은 전역 기본값(0.45em 0.9em)이 고정
 * 24×24px 원 안에서 글자를 밀어내지 않도록 상쇄한다.
 */
.close {
  flex: none;
  width: 1.75rem;
  height: 1.75rem;
  padding: 0;
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
  color: var(--text-h);
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
