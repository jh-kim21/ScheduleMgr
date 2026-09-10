import { describe, expect, it } from 'vitest'
import { displayProgress } from './ganttProgress'

describe('displayProgress', () => {
  it('computedProgress가 있으면 그것을 쓴다 — 툴팁과 막대가 같은 숫자를 봐야 한다', () => {
    expect(displayProgress({ computedProgress: 70, progress: 20 })).toBe(70)
  })

  it('산정 전(computedProgress === null)이면 저장된 progress로 되돌아간다', () => {
    expect(displayProgress({ computedProgress: null, progress: 20 })).toBe(20)
  })

  it('0은 산정 전이 아니다 — 그대로 0을 쓴다', () => {
    expect(displayProgress({ computedProgress: 0, progress: 55 })).toBe(0)
  })

  it('미지정(MANUAL)은 두 값이 같으므로 기존 화면과 달라지지 않는다', () => {
    expect(displayProgress({ computedProgress: 40, progress: 40 })).toBe(40)
  })
})
