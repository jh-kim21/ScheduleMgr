/**
 * 지금 키를 받는 것이 "글자를 입력하는 칸"인가. WBS 트리의 `<tbody>`는 키 핸들러를 하나만
 * 두는데(`WbsTree.onKeydown`), 그 안에 펼친 Work Package의 체크포인트 폼(`CheckpointList`)이
 * 들어온다. 그 입력칸에서 누른 방향키가 여기까지 버블링해 `preventDefault()`를 맞으면 캐럿이
 * 움직이지 않고, `<select>`의 가중치 등급도 고를 수 없다. `Alt`+방향키는 더 나쁘다 — 타이핑
 * 중에 WBS 항목이 실제로 이동한다.
 *
 * `SprintAssignTable.vue:94`가 같은 종류의 사고를 같은 방법으로 막고 있다.
 *
 * **`instanceof HTMLInputElement`를 쓰지 마라** — 이 저장소의 기본 vitest 환경은 `node`라
 * `HTMLInputElement`가 정의되어 있지 않아 `ReferenceError`가 난다. `tagName` 문자열로 보면
 * 평범한 객체로 테스트할 수 있어 이 파일만 `happy-dom`으로 올릴 필요가 없다
 * (CLAUDE.md "컴포넌트 테스트 (프론트엔드)").
 */
const TEXT_ENTRY_TAGS = new Set(['INPUT', 'SELECT', 'TEXTAREA'])

export function isTextEntry(target: EventTarget | null): boolean {
  const el = target as { tagName?: unknown; isContentEditable?: unknown } | null
  if (!el) return false
  if (typeof el.tagName === 'string' && TEXT_ENTRY_TAGS.has(el.tagName)) return true
  return el.isContentEditable === true
}
