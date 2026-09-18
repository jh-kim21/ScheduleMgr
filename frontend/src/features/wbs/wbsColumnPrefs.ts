import { ref } from 'vue'
import { defaultPrefs, normalizePrefs, type WbsColumnPrefs } from './wbsColumns'

/**
 * 지금 이 키를 읽는 곳은 이 파일 하나뿐이다(`stores/theme.ts:7`의 `project-flow.theme`과 같은
 * 모양) — 바꾸려면 아래 STORAGE_KEY만 고치면 된다. 프로젝트별로 나누지 않는다(지시서 2-5) — 열
 * 구성은 "이 사람이 무엇을 보고 싶은가"이지 프로젝트의 속성이 아니다.
 */
const STORAGE_KEY = 'project-flow.wbs-columns'

function load(): WbsColumnPrefs {
  try {
    const stored = localStorage.getItem(STORAGE_KEY)
    if (stored === null) return defaultPrefs()
    return normalizePrefs(JSON.parse(stored))
  } catch {
    // 사생활 보호 모드 등에서 localStorage 접근이 던질 수 있다 — 못 읽는 것이 화면을 못 그릴
    // 이유는 아니므로 기본값으로 떨어진다.
    return defaultPrefs()
  }
}

/** 모듈 스코프 — 화면(WBS 트리)을 벗어나도, 프로젝트를 옮겨도 유지된다. */
export const columnPrefs = ref<WbsColumnPrefs>(load())

function persist(prefs: WbsColumnPrefs) {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(prefs))
  } catch {
    // 저장이 안 되어도 이번 세션에는 반영된다 — 실패를 화면에 알릴 이유는 없다.
  }
}

export function setColumnPrefs(next: WbsColumnPrefs): void {
  columnPrefs.value = next
  persist(next)
}

/** 기본값으로 되돌리고 저장소도 정리한다(빈 값을 다시 적어 두는 것이 아니라 키 자체를 지운다). */
export function resetColumnPrefs(): void {
  columnPrefs.value = defaultPrefs()
  try {
    localStorage.removeItem(STORAGE_KEY)
  } catch {
    // 정리가 안 되어도 이번 세션의 ref는 이미 기본값이다.
  }
}
