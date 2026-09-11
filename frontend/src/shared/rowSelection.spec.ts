import { describe, expect, it } from 'vitest'
// eslint-disable-next-line import/no-unresolved
import {
  isDoubleClickGuarded,
  nextSelectionId,
  reconcileSelection,
} from './rowSelection'

/**
 * WbsTree.vue(features/wbs/WbsTree.vue, 커밋 c5b4530)는 행 선택·방향키 이동·더블클릭 가드를
 * 컴포넌트 안의 클로저(moveSelection/resolveVisibleSelection/onRowDblClick, selectedId ref,
 * rows computed)로만 구현하고 있고, 이 시점에는 순수 함수로 분리된 모듈이 저장소 어디에도
 * 없다 (`frontend/src/shared/rowSelection.ts` 미존재, grep 결과 WbsTree.vue 한 곳뿐).
 *
 * @vue/test-utils 도 DOM 환경도 없어 컴포넌트를 직접 마운트해 검증할 수 없으므로, 리더가 고정한
 * 동작 계약 5가지를 기준으로 "Developer1이 뽑아낼 법한" 순수 함수 시그니처를 가정해 이 스펙을
 * 작성한다. 아래는 실제 구현이 아니라 테스트가 기대하는 계약이다 — 이름이 다르면 리더가
 * Developer1과 맞춰야 한다.
 *
 * 가정한 함수 시그니처 (모두 `./rowSelection`에서 export 된다고 가정):
 *
 *   nextSelectionId(ids: number[], selectedId: number | null, delta: 1 | -1): number | null
 *     - WbsTree의 moveSelection(delta)를, id 목록만 받는 순수 함수로 뽑은 것.
 *     - `ids`는 현재 화면에 보이는 행(rows)의 순서대로 나열한 id 배열.
 *     - 반환값은 "다음에 선택되어야 할 id" — 컴포넌트는 이 값을 selectedId ref에 대입하기만
 *       하면 된다.
 *
 *   reconcileSelection(ids: number[], selectedId: number | null): number | null
 *     - WbsTree 110~120행의 선택 무효화 watch를 뽑은 것. 트리가 바뀐 뒤 선택이 여전히
 *       유효한지 판정한다.
 *
 *   isDoubleClickGuarded(ancestorTagNames: string[]): boolean
 *     - WbsTree의 onRowDblClick 가드(`event.target.closest('button, a')`)를, 실제 DOM 대신
 *       "클릭된 지점에서 행까지의 조상 태그 이름 배열"을 받는 순수 함수로 뽑은 것이라 가정한다.
 *       배열은 target 자신부터 시작해 바깥쪽으로 나열한다(예: ['SPAN', 'BUTTON', 'TD', 'TR']).
 *       이 가정이 맞다면 DOM 없이도 가드를 검증할 수 있다 — 순수 분리가 안 되어 있다면 아래
 *       describe('isDoubleClickGuarded', ...) 블록은 모듈과 함께 통째로 실패할 것이다.
 */

describe('nextSelectionId — 계약 1·2·5: 방향키 이동, 최초 선택, 빈 목록', () => {
  it('선택이 없을 때 아래 화살표를 누르면 첫 행이 선택된다 (계약 2)', () => {
    expect(nextSelectionId([10, 20, 30], null, 1)).toBe(10)
  })

  it('선택이 없을 때 위 화살표를 눌러도 첫 행이 선택된다 (계약 2) — 방향과 무관하다', () => {
    expect(nextSelectionId([10, 20, 30], null, -1)).toBe(10)
  })

  it('ArrowDown은 다음 행을 선택한다 (계약 1)', () => {
    expect(nextSelectionId([10, 20, 30], 10, 1)).toBe(20)
  })

  it('ArrowUp은 이전 행을 선택한다 (계약 1)', () => {
    expect(nextSelectionId([10, 20, 30], 20, -1)).toBe(10)
  })

  it('마지막 행에서 ArrowDown을 눌러도 순환하지 않고 그대로 멈춘다 (계약 1)', () => {
    expect(nextSelectionId([10, 20, 30], 30, 1)).toBe(30)
  })

  it('첫 행에서 ArrowUp을 눌러도 순환하지 않고 그대로 멈춘다 (계약 1)', () => {
    expect(nextSelectionId([10, 20, 30], 10, -1)).toBe(10)
  })

  it('목록이 비어 있으면 이동해도 아무 일도 없다 (계약 5) — 선택 없음 상태 유지', () => {
    expect(nextSelectionId([], null, 1)).toBeNull()
  })

  it('목록이 비어 있으면 기존 선택도 그대로 유지한다 (계약 5) — 이미 있던 값을 지우지 않는다', () => {
    // WbsTree의 moveSelection은 list.length === 0일 때 즉시 return하므로 selectedId를
    // 건드리지 않는다. 이 케이스는 실제로는 selectedId가 사라진 행을 가리킬 수 없어(계약 3이
    // 먼저 null로 만든다) 일어나지 않지만, 방어적으로 "빈 목록에서는 손대지 않는다"를 고정한다.
    expect(nextSelectionId([], 99, 1)).toBe(99)
  })
})

describe('reconcileSelection — 계약 3: 선택된 행이 사라지면 선택 해제', () => {
  it('선택된 id가 여전히 목록에 있으면 그대로 유지한다', () => {
    expect(reconcileSelection([10, 20, 30], 20)).toBe(20)
  })

  it('선택된 id가 목록에서 사라지면 null로 해제된다 (계약 3)', () => {
    expect(reconcileSelection([10, 30], 20)).toBeNull()
  })

  it('선택이 이미 없으면(null) null 그대로다', () => {
    expect(reconcileSelection([10, 20, 30], null)).toBeNull()
  })

  it('목록 전체가 비어도 선택된 id는 사라진 것으로 본다', () => {
    expect(reconcileSelection([], 20)).toBeNull()
  })
})

describe('isDoubleClickGuarded — 계약 4: button/a 안에서는 더블클릭으로 편집을 열지 않는다', () => {
  it('버튼 위에서 더블클릭하면 막는다', () => {
    expect(isDoubleClickGuarded(['BUTTON', 'TD', 'TR'])).toBe(true)
  })

  it('버튼 안의 텍스트(span 등) 위에서 더블클릭해도 막는다 — closest는 조상까지 본다', () => {
    expect(isDoubleClickGuarded(['SPAN', 'BUTTON', 'TD', 'TR'])).toBe(true)
  })

  it('링크(a) 위에서 더블클릭해도 막는다', () => {
    expect(isDoubleClickGuarded(['A', 'TD', 'TR'])).toBe(true)
  })

  it('버튼·링크가 조상에 없으면 편집을 연다', () => {
    expect(isDoubleClickGuarded(['SPAN', 'TD', 'TR'])).toBe(false)
  })

  it('행(TR) 자체를 더블클릭해도(빈 셀) 편집을 연다', () => {
    expect(isDoubleClickGuarded(['TD', 'TR'])).toBe(false)
  })
})
