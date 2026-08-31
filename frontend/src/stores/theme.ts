import { computed, ref, watch } from 'vue'

export type ThemePreference = 'system' | 'light' | 'dark'
export type ResolvedTheme = 'light' | 'dark'

/** Shared with the bootstrap script in index.html — keep the two in step. */
const STORAGE_KEY = 'project-flow.theme'
const DARK_QUERY = '(prefers-color-scheme: dark)'

const PREFERENCES: ThemePreference[] = ['system', 'light', 'dark']

export const THEME_LABELS: Record<ThemePreference, string> = {
  system: '시스템 설정',
  light: '라이트 모드',
  dark: '다크 모드',
}

export const THEME_ICONS: Record<ThemePreference, string> = {
  system: '🖥',
  light: '☀',
  dark: '🌙',
}

function readStored(): ThemePreference {
  try {
    const stored = localStorage.getItem(STORAGE_KEY)
    return PREFERENCES.includes(stored as ThemePreference) ? (stored as ThemePreference) : 'system'
  } catch {
    // Private windows and blocked site data throw on access rather than returning null.
    return 'system'
  }
}

function systemPrefersDark(): boolean {
  return typeof matchMedia === 'function' && matchMedia(DARK_QUERY).matches
}

/** Module scope: every view shares one theme, the same reason the data composables do. */
export const themePreference = ref<ThemePreference>(readStored())
const systemDark = ref(systemPrefersDark())

/**
 * What is actually on screen. The stylesheet only knows `light` and `dark`, so "follow the system"
 * is resolved here instead of being a third CSS state — that keeps the dark palette in one block.
 */
export const resolvedTheme = computed<ResolvedTheme>(() =>
  themePreference.value === 'system'
    ? systemDark.value
      ? 'dark'
      : 'light'
    : themePreference.value,
)

if (typeof matchMedia === 'function') {
  matchMedia(DARK_QUERY).addEventListener('change', (event) => {
    systemDark.value = event.matches
  })
}

watch(
  resolvedTheme,
  (theme) => {
    document.documentElement.dataset.theme = theme
  },
  { immediate: true },
)

watch(themePreference, (preference) => {
  try {
    localStorage.setItem(STORAGE_KEY, preference)
  } catch {
    // Not being able to remember the choice is not worth failing the interaction over.
  }
})

export function setThemePreference(preference: ThemePreference) {
  themePreference.value = preference
}

/** Cycles 시스템 → 라이트 → 다크, which keeps the header control to a single button. */
export function cycleThemePreference() {
  const next = (PREFERENCES.indexOf(themePreference.value) + 1) % PREFERENCES.length
  themePreference.value = PREFERENCES[next]
}
