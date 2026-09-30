import { describe, expect, it } from 'vitest'
import { isTextEntry } from './treeKeyTarget'

/**
 * 순수 함수라 DOM이 필요 없다 — 이 파일에 `// @vitest-environment happy-dom`을 붙이지 말 것
 * (CLAUDE.md "컴포넌트 테스트": 기본 환경은 `node`이고, 컴포넌트 테스트 파일만 명시적으로 DOM
 * 환경을 켠다). 판정이 `tagName` 문자열이라 평범한 객체로 그대로 부를 수 있다는 것이 이 함수를
 * `instanceof HTMLInputElement`로 쓰지 않은 이유다.
 */
describe('isTextEntry', () => {
  it('입력 컨트롤이면 true — 여기서 누른 방향키는 트리가 가져가면 안 된다', () => {
    expect(isTextEntry({ tagName: 'INPUT' } as unknown as EventTarget)).toBe(true)
    expect(isTextEntry({ tagName: 'SELECT' } as unknown as EventTarget)).toBe(true)
    expect(isTextEntry({ tagName: 'TEXTAREA' } as unknown as EventTarget)).toBe(true)
  })

  it('트리 자신의 요소는 false — 탐색·이동 단축키가 그대로 살아 있어야 한다', () => {
    expect(isTextEntry({ tagName: 'TBODY' } as unknown as EventTarget)).toBe(false)
    expect(isTextEntry({ tagName: 'TR' } as unknown as EventTarget)).toBe(false)
    expect(isTextEntry({ tagName: 'TD' } as unknown as EventTarget)).toBe(false)
  })

  it('버튼은 false — "입력 중"을 막는 것이지 "tbody가 아님"을 막는 것이 아니다', () => {
    // 행 안의 토글 버튼·링크에 포커스가 있을 때도 방향키 탐색은 동작해야 한다(지시서 2-A).
    expect(isTextEntry({ tagName: 'BUTTON' } as unknown as EventTarget)).toBe(false)
    expect(isTextEntry({ tagName: 'A' } as unknown as EventTarget)).toBe(false)
  })

  it('target이 없으면 false', () => {
    expect(isTextEntry(null)).toBe(false)
  })

  it('contenteditable 도 입력 칸으로 본다', () => {
    expect(isTextEntry({ tagName: 'DIV', isContentEditable: true } as unknown as EventTarget)).toBe(
      true,
    )
    expect(
      isTextEntry({ tagName: 'DIV', isContentEditable: false } as unknown as EventTarget),
    ).toBe(false)
  })
})
