import { describe, expect, it } from 'vitest'
import { isSafeHttpUrl } from './url'

/**
 * 순수 함수라 DOM이 필요 없다 — `// @vitest-environment happy-dom`을 붙이지 말 것(CLAUDE.md
 * "컴포넌트 테스트"). `URL`은 Node 에도 전역으로 있다.
 *
 * 이 함수는 렌더 직전의 **마지막 방어선**이다(지시서 2-D 보안). 서버·가져오기도 같은 규칙으로
 * 막지만, 규칙이 생기기 전에 저장된 행이 남아 있을 수 있고 실제로 스크립트를 실행하는 것은
 * 브라우저다.
 */
describe('isSafeHttpUrl', () => {
  it('http·https 만 통과한다', () => {
    expect(isSafeHttpUrl('https://example.com')).toBe(true)
    expect(isSafeHttpUrl('http://example.com/a/b?c=1#d')).toBe(true)
  })

  it('javascript: 는 막는다 — 누르는 것만으로 스크립트가 실행된다', () => {
    expect(isSafeHttpUrl('javascript:alert(1)')).toBe(false)
  })

  it('대소문자를 섞어도 막는다', () => {
    expect(isSafeHttpUrl('JavaScript:alert(1)')).toBe(false)
  })

  it('앞에 공백을 붙여도 막는다 — URL 파서가 공백을 떼고 읽는다', () => {
    expect(isSafeHttpUrl('  javascript:alert(1)')).toBe(false)
  })

  it('data:·vbscript: 도 막는다', () => {
    expect(isSafeHttpUrl('data:text/html,<script>alert(1)</script>')).toBe(false)
    expect(isSafeHttpUrl('vbscript:msgbox(1)')).toBe(false)
  })

  it('URL 로 읽히지 않는 값은 막는다 — 상대 경로도 여기서 걸린다', () => {
    expect(isSafeHttpUrl('not-a-url')).toBe(false)
    expect(isSafeHttpUrl('/wbs')).toBe(false)
  })

  it('비어 있으면 막는다 — 미입력이 링크가 되면 안 된다', () => {
    expect(isSafeHttpUrl(null)).toBe(false)
    expect(isSafeHttpUrl(undefined)).toBe(false)
    expect(isSafeHttpUrl('')).toBe(false)
  })
})
