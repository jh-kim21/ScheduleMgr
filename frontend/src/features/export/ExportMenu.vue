<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { ApiError } from '../../api/http'
import type { Project } from '../../api/projectApi'
import { raciApi } from '../../api/raciApi'
import { raidApi } from '../../api/raidApi'
import { wbsApi } from '../../api/wbsApi'
import { csvFileName, downloadCsv, toCsv } from '../../shared/csv'
import { raciCsv, raciLegend, raidCsv, wbsCsv, type CsvTable } from '../../shared/exportRows'

/**
 * Export controls for one project, as a Material-style menu button.
 *
 * <p>One control in one place: export is a per-project act, so a button on every screen meant
 * four copies of the same thing and blurred what unit was being exported.
 *
 * <p>A real menu rather than a native `<select>`. A select is a form control for a value that
 * gets submitted; these are four commands, and Material's menu is the pattern for that — it also
 * lets each item carry an icon and a description, which a select cannot.
 */
const props = defineProps<{
  project: Project
}>()

type Format = 'json' | 'wbs' | 'raci' | 'raid'

interface MenuItem {
  format: Format
  label: string
  hint: string
  /** Inline path data; the app has no icon library and four glyphs do not justify one. */
  icon: string
}

const DOWNLOAD_ICON =
  'M12 15.6 7.4 11l1.4-1.42L11 11.78V4h2v7.78l2.2-2.2L16.6 11 12 15.6ZM6 20a2 2 0 0 1-2-2v-3h2v3h12v-3h2v3a2 2 0 0 1-2 2H6Z'
const TABLE_ICON =
  'M4 20a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h16a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H4Zm0-10h4V6H4v4Zm6 0h10V6H10v4Zm-6 8h4v-6H4v6Zm6 0h10v-6H10v6Z'
const PACKAGE_ICON =
  'M11 21.4 3.5 17.1a1 1 0 0 1-.5-.87V7.77a1 1 0 0 1 .5-.87L11 2.6a2 2 0 0 1 2 0l7.5 4.3a1 1 0 0 1 .5.87v8.46a1 1 0 0 1-.5.87L13 21.4a2 2 0 0 1-2 0Zm1-10.55 6.9-3.96L12 2.92 5.1 6.89 12 10.85Z'
const PEOPLE_ICON =
  'M9 13a4 4 0 1 1 0-8 4 4 0 0 1 0 8Zm0 2c3.9 0 7 1.57 7 3.5V21H2v-2.5C2 16.57 5.1 15 9 15Zm8.5-2a3.5 3.5 0 1 1 0-7 3.5 3.5 0 0 1 0 7Zm0 2c2.5 0 4.5 1.2 4.5 2.7V21h-4v-2.5c0-1.16-.5-2.2-1.35-3.02.27-.03.55-.05.85-.05Z'

const ITEMS: MenuItem[] = [
  { format: 'json', label: '프로젝트 전체', hint: 'JSON · 다시 가져올 수 있는 형식', icon: PACKAGE_ICON },
  { format: 'wbs', label: 'WBS', hint: 'CSV · Excel에서 열기', icon: TABLE_ICON },
  { format: 'raci', label: 'RACI', hint: 'CSV · 업무 × 구성원 표', icon: PEOPLE_ICON },
  { format: 'raid', label: 'RAID', hint: 'CSV · 위험·가정·이슈·의존성', icon: TABLE_ICON },
]

const open = ref(false)
const busy = ref(false)
const error = ref<string | null>(null)
const trigger = ref<HTMLButtonElement | null>(null)
const menu = ref<HTMLElement | null>(null)
/** Index of the keyboard-focused item; -1 when the pointer is driving. */
const active = ref(-1)

/**
 * Positioned with `position: fixed` from the trigger's box rather than absolutely inside the
 * table. The same reason the Gantt tooltip does: an ancestor that scrolls or clips would
 * otherwise cut the menu off, and a table row is a bad place to fight that.
 */
const position = ref<{ left: number; top?: number; bottom?: number }>({ left: 0, top: 0 })
const MENU_WIDTH = 232
/** 항목 4개 + 패딩. 정확할 필요는 없고, 아래로 열 자리가 있는지 판단할 정도면 된다. */
const MENU_HEIGHT = 208

function place() {
  const box = trigger.value?.getBoundingClientRect()
  if (!box) return
  const left = Math.max(8, Math.min(box.left, window.innerWidth - MENU_WIDTH - 8))

  // 위로 뒤집을 때는 bottom 을 기준으로 잡아야 한다. top 을 트리거 위에 두면 메뉴가 거기서
  // 아래로 자라 트리거를 덮고 화면 밖으로 흘러내린다.
  position.value = box.bottom + MENU_HEIGHT + 8 > window.innerHeight
    ? { left, bottom: window.innerHeight - box.top + 4 }
    : { left, top: box.bottom + 4 }
}

const menuStyle = computed(() => ({
  left: `${position.value.left}px`,
  ...(position.value.top !== undefined ? { top: `${position.value.top}px` } : {}),
  ...(position.value.bottom !== undefined ? { bottom: `${position.value.bottom}px` } : {}),
  width: `${MENU_WIDTH}px`,
  transformOrigin: position.value.bottom !== undefined ? 'bottom left' : 'top left',
}))

function toggle() {
  if (open.value) {
    close()
    return
  }
  place()
  open.value = true
  active.value = -1
}

function close() {
  open.value = false
  active.value = -1
  trigger.value?.focus()
}

function onDocumentPointerDown(event: PointerEvent) {
  const target = event.target as Node
  if (trigger.value?.contains(target) || menu.value?.contains(target)) return
  open.value = false
  active.value = -1
}

function onKeydown(event: KeyboardEvent) {
  if (!open.value) return
  if (event.key === 'Escape') {
    event.preventDefault()
    close()
    return
  }
  if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
    event.preventDefault()
    const step = event.key === 'ArrowDown' ? 1 : -1
    active.value = (active.value + step + ITEMS.length) % ITEMS.length
    return
  }
  if ((event.key === 'Enter' || event.key === ' ') && active.value >= 0) {
    event.preventDefault()
    run(ITEMS[active.value].format)
  }
}

// 문서 리스너는 열려 있을 때만 붙인다 — 목록에 프로젝트가 많으면 메뉴도 그만큼 존재한다.
watch(open, (isOpen) => {
  if (isOpen) {
    document.addEventListener('pointerdown', onDocumentPointerDown, true)
    document.addEventListener('keydown', onKeydown, true)
    window.addEventListener('resize', place)
    window.addEventListener('scroll', place, true)
  } else {
    detach()
  }
})

function detach() {
  document.removeEventListener('pointerdown', onDocumentPointerDown, true)
  document.removeEventListener('keydown', onKeydown, true)
  window.removeEventListener('resize', place)
  window.removeEventListener('scroll', place, true)
}

onBeforeUnmount(detach)

/**
 * CSVs are built from a fresh fetch of the project's data. Moving this off the screens means the
 * file is the project's whole table, not the filtered view someone was looking at.
 */
async function tableFor(format: Exclude<Format, 'json'>): Promise<{ table: CsvTable; note?: string }> {
  const id = props.project.id
  if (format === 'wbs') {
    const tree = await wbsApi.tree(id)
    return { table: wbsCsv(tree.nodes, tree.referenceDate) }
  }
  if (format === 'raci') {
    const matrix = await raciApi.matrix(id)
    return { table: raciCsv(matrix), note: `RACI: ${raciLegend()}` }
  }
  const log = await raidApi.log(id)
  return { table: raidCsv(log.items, log.referenceDate) }
}

async function run(format: Format) {
  open.value = false
  active.value = -1
  error.value = null

  if (format === 'json') {
    // 서버가 Content-Disposition: attachment 로 내려주므로 링크를 따라가면 끝난다.
    window.location.href = `/api/projects/${props.project.id}/export`
    return
  }

  busy.value = true
  try {
    const { table, note } = await tableFor(format)
    let body = toCsv(table.header, table.rows)
    if (note) {
      // 빈 줄 하나를 두고 붙인다 — 표의 열 구조를 흐트러뜨리지 않는다.
      body += `\r\n\r\n${toCsv([note], [])}`
    }
    downloadCsv(csvFileName(props.project.name, format, new Date()), body)
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : '내보내지 못했습니다.'
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <span class="export">
    <button
      ref="trigger"
      type="button"
      class="md-button"
      :class="{ busy }"
      :disabled="busy"
      :aria-expanded="open"
      aria-haspopup="menu"
      :title="`${project.name} 내보내기`"
      @click="toggle"
    >
      <svg class="icon" viewBox="0 0 24 24" aria-hidden="true"><path :d="DOWNLOAD_ICON" /></svg>
      {{ busy ? '내보내는 중' : '내보내기' }}
      <svg class="caret" :class="{ up: open }" viewBox="0 0 24 24" aria-hidden="true">
        <path d="M7.4 8.6 12 13.2l4.6-4.6L18 10l-6 6-6-6 1.4-1.4Z" />
      </svg>
    </button>

    <Teleport to="body">
      <div
        v-if="open"
        ref="menu"
        class="md-menu"
        role="menu"
        :style="menuStyle"
        :aria-label="`${project.name} 내보내기`"
      >
        <button
          v-for="(item, index) in ITEMS"
          :key="item.format"
          type="button"
          class="md-menu-item"
          :class="{ active: active === index }"
          role="menuitem"
          @mouseenter="active = index"
          @click="run(item.format)"
        >
          <svg class="icon" viewBox="0 0 24 24" aria-hidden="true"><path :d="item.icon" /></svg>
          <span class="text">
            <span class="label">{{ item.label }}</span>
            <span class="hint">{{ item.hint }}</span>
          </span>
        </button>
      </div>
    </Teleport>

    <button
      v-if="error"
      type="button"
      class="error"
      :title="error"
      @click="error = null"
    >!</button>
  </span>
</template>

<style scoped>
.export {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
}

/*
 * Material 의 outlined button: 테두리 + 알약 모양 + 상태 레이어. 색은 앱 팔레트를 쓴다.
 * 상태 레이어를 배경색 교체가 아니라 겹치는 층으로 두는 이유는, 어떤 배경 위에서도 같은
 * 규칙으로 동작하고 다크 모드에서 색을 한 벌 더 정의하지 않아도 되기 때문이다.
 */
.md-button {
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  padding: 0.3rem 0.6rem 0.3rem 0.55rem;
  border: 1px solid var(--border-input);
  border-radius: 999px;
  background: transparent;
  color: var(--text-muted);
  font: inherit;
  font-size: 0.76rem;
  font-weight: 500;
  letter-spacing: 0.01em;
  line-height: 1.4;
  cursor: pointer;
  overflow: hidden;
  transition: border-color 120ms ease, color 120ms ease;
}

.md-button::before {
  content: '';
  position: absolute;
  inset: 0;
  background: transparent;
  transition: background 120ms ease;
}

.md-button:hover {
  border-color: var(--accent-border);
  color: var(--text-h);
}

.md-button:hover::before {
  background: var(--state-hover);
}

.md-button:active::before {
  background: var(--state-press);
}

.md-button:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: 2px;
}

.md-button[disabled] {
  color: var(--disabled-fg);
  border-color: var(--disabled-border);
  cursor: default;
}

.icon {
  width: 1rem;
  height: 1rem;
  flex: none;
  fill: currentColor;
}

.caret {
  width: 0.85rem;
  height: 0.85rem;
  flex: none;
  fill: currentColor;
  transition: transform 140ms ease;
}

.caret.up {
  transform: rotate(180deg);
}

/*
 * 메뉴는 body 로 teleport 하고 fixed 로 놓는다. 표 행 안에 두면 스크롤·클리핑되는 조상에
 * 걸려 잘리는데, 간트 툴팁이 같은 이유로 같은 방식을 쓴다.
 */
.md-menu {
  position: fixed;
  z-index: 60;
  padding: 0.25rem;
  border-radius: 10px;
  background: var(--surface-raised);
  border: 1px solid var(--border);
  box-shadow: var(--elevation-3);
  display: flex;
  flex-direction: column;
  animation: menu-in 120ms ease-out;
}

@keyframes menu-in {
  from {
    opacity: 0;
    transform: scale(0.97) translateY(-2px);
  }
}

@media (prefers-reduced-motion: reduce) {
  .md-menu {
    animation: none;
  }

  .caret,
  .md-button,
  .md-button::before {
    transition: none;
  }
}

.md-menu-item {
  position: relative;
  display: flex;
  align-items: flex-start;
  gap: 0.5rem;
  width: 100%;
  padding: 0.45rem 0.55rem;
  border: none;
  border-radius: 7px;
  background: transparent;
  color: var(--text);
  font: inherit;
  font-size: 0.8rem;
  text-align: left;
  cursor: pointer;
}

/* hover 와 키보드 이동이 같은 표시를 쓰도록 .active 하나로 모은다. */
.md-menu-item.active {
  background: var(--state-hover);
}

.md-menu-item:active {
  background: var(--state-press);
}

.md-menu-item .icon {
  margin-top: 0.1rem;
  color: var(--text-faint);
}

.md-menu-item .text {
  display: flex;
  flex-direction: column;
  gap: 0.05rem;
}

.md-menu-item .label {
  font-weight: 500;
  color: var(--text-h);
}

.md-menu-item .hint {
  font-size: 0.7rem;
  color: var(--text-faint);
}

.error {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 1.15rem;
  height: 1.15rem;
  padding: 0;
  border: none;
  border-radius: 999px;
  background: var(--danger);
  color: var(--status-fg);
  font: inherit;
  font-size: 0.72rem;
  font-weight: 700;
  cursor: pointer;
}
</style>
