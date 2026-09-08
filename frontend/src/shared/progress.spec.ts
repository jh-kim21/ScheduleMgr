import { describe, expect, it } from 'vitest'
import { progressBarWidth, progressText, varianceText } from './progress'

describe('progressText', () => {
  it('산정 전은 0%가 아니다', () => {
    expect(progressText(null)).toBe('산정 전')
    expect(progressText(0)).toBe('0%')
  })

  it('표시할 때만 반올림한다', () => {
    expect(progressText(33.333333)).toBe('33%')
    expect(progressText(53.999)).toBe('54%')
  })
})

describe('varianceText', () => {
  it('기준선이 없으면 미산정', () => {
    expect(varianceText(null)).toBe('미산정')
  })

  it('부호를 붙이고 소수 한 자리까지 보여준다', () => {
    expect(varianceText(-3)).toBe('-3%p')
    expect(varianceText(2.34)).toBe('+2.3%p')
    expect(varianceText(0)).toBe('±0%p')
  })
})

describe('progressBarWidth', () => {
  it('산정 전은 막대를 그리지 않는다', () => {
    expect(progressBarWidth(null)).toBe('0%')
  })

  it('0~100 밖으로 나가지 않는다', () => {
    expect(progressBarWidth(120)).toBe('100%')
    expect(progressBarWidth(-5)).toBe('0%')
    expect(progressBarWidth(54)).toBe('54%')
  })
})
