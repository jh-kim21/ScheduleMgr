import { describe, expect, it } from 'vitest'
import { formatBytes, formatCapacityUsage } from './commitFormat'

describe('formatBytes', () => {
  it('1024 미만은 바이트 그대로', () => {
    expect(formatBytes(512)).toBe('512B')
  })

  it('킬로바이트도 10 미만이면 소수점 한 자리를 남긴다', () => {
    expect(formatBytes(2_500)).toBe('2.4KB')
  })

  it('킬로바이트가 10 이상이면 정수로 반올림한다', () => {
    expect(formatBytes(15 * 1024)).toBe('15KB')
  })

  it('메가바이트 단위', () => {
    expect(formatBytes(5 * 1024 * 1024)).toBe('5MB')
  })

  it('10 미만은 소수점 한 자리를 남긴다', () => {
    expect(formatBytes(1.5 * 1024 * 1024 * 1024)).toBe('1.5GB')
  })

  it('10 이상은 정수로 반올림한다', () => {
    expect(formatBytes(12.34 * 1024 * 1024 * 1024)).toBe('12GB')
  })

  it('0 이하는 0B', () => {
    expect(formatBytes(0)).toBe('0B')
    expect(formatBytes(-10)).toBe('0B')
  })
})

describe('formatCapacityUsage', () => {
  it('퍼센트와 바이트 쌍을 하나의 문구로 합친다', () => {
    const capacity = {
      usedBytes: 900 * 1024 * 1024,
      maxBytes: 1024 * 1024 * 1024,
      usedPercent: 84,
      warning: true,
    }
    expect(formatCapacityUsage(capacity)).toBe('84% (900MB / 1GB)')
  })

  it('퍼센트는 반올림한다', () => {
    const capacity = {
      usedBytes: 100,
      maxBytes: 1024 * 1024 * 1024,
      usedPercent: 12.6,
      warning: false,
    }
    expect(formatCapacityUsage(capacity)).toBe('13% (100B / 1GB)')
  })
})
