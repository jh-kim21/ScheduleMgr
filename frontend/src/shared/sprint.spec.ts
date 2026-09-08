import { describe, expect, it } from 'vitest'
import { daysRemaining, remainingLabel, sprintPeriod } from './sprint'

describe('sprintPeriod', () => {
  it('기간을 한 줄로 읽는다', () => {
    expect(sprintPeriod('2026-09-01', '2026-09-14')).toBe('2026-09-01 ~ 2026-09-14')
  })
})

describe('daysRemaining', () => {
  it('종료일 포함으로 센다 — 일정 쪽과 같은 규칙', () => {
    expect(daysRemaining('2026-09-14', '2026-09-01')).toBe(13)
    expect(daysRemaining('2026-09-14', '2026-09-14')).toBe(0)
  })

  it('지나간 종료일은 음수다', () => {
    expect(daysRemaining('2026-09-14', '2026-09-20')).toBe(-6)
  })
})

describe('remainingLabel', () => {
  it('남은 날, 오늘 종료, 경과를 구분한다', () => {
    expect(remainingLabel('2026-09-14', '2026-09-10')).toBe('4일 남음')
    expect(remainingLabel('2026-09-14', '2026-09-14')).toBe('오늘 종료')
    // 지난 Sprint에 "0일 남음"이라고 적으면 거짓이 된다.
    expect(remainingLabel('2026-09-14', '2026-09-20')).toBe('종료일 6일 경과')
  })
})
