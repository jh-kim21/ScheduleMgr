import { describe, expect, it } from 'vitest'
import {
  countByStatus,
  DELAY_LABELS,
  delayBadge,
  delayDescription,
  localToday,
  needsAttention,
  type DelayInfo,
  type DelayStatus,
} from './delay'

const ALL_STATUSES: DelayStatus[] = [
  'UNSCHEDULED',
  'NOT_STARTED',
  'ON_TRACK',
  'AT_RISK',
  'DELAYED',
  'COMPLETED',
]

function row(overrides: Partial<DelayInfo> = {}): DelayInfo {
  return {
    delayStatus: 'ON_TRACK',
    expectedProgress: 0,
    progressGap: 0,
    delayDays: 0,
    progress: 0,
    startDate: '2026-09-01',
    ...overrides,
  }
}

describe('DELAY_LABELS', () => {
  it('모든 상태에 한글 라벨이 있다', () => {
    ALL_STATUSES.forEach((status) => {
      expect(DELAY_LABELS[status]).toBeTruthy()
    })
  })
})

describe('needsAttention', () => {
  it('지연과 지연 위험만 주의 대상이다', () => {
    expect(needsAttention(row({ delayStatus: 'DELAYED' }))).toBe(true)
    expect(needsAttention(row({ delayStatus: 'AT_RISK' }))).toBe(true)
    expect(needsAttention(row({ delayStatus: 'ON_TRACK' }))).toBe(false)
    expect(needsAttention(row({ delayStatus: 'COMPLETED' }))).toBe(false)
    expect(needsAttention(row({ delayStatus: 'NOT_STARTED' }))).toBe(false)
    expect(needsAttention(row({ delayStatus: 'UNSCHEDULED' }))).toBe(false)
  })
})

describe('delayBadge', () => {
  it('지연은 경과 일수를 라벨에 붙인다', () => {
    expect(delayBadge(row({ delayStatus: 'DELAYED', delayDays: 6 }))).toBe('지연 6일')
  })

  it('그 밖의 상태는 라벨만 쓴다', () => {
    expect(delayBadge(row({ delayStatus: 'AT_RISK' }))).toBe('지연 위험')
    expect(delayBadge(row({ delayStatus: 'COMPLETED' }))).toBe('완료')
  })
})

describe('delayDescription', () => {
  it('지연은 경과 일수와 부족한 진행률을 함께 알려준다', () => {
    const text = delayDescription(
      row({ delayStatus: 'DELAYED', delayDays: 5, progress: 80, progressGap: 20 }),
    )

    expect(text).toContain('5일')
    expect(text).toContain('80%')
    expect(text).toContain('20%p')
  })

  it('지연 위험은 기대 진행률과 실제를 대비해 보여준다', () => {
    const text = delayDescription(
      row({ delayStatus: 'AT_RISK', expectedProgress: 50, progress: 20, progressGap: 30 }),
    )

    expect(text).toContain('50%')
    expect(text).toContain('20%')
    expect(text).toContain('30%p')
  })

  it('시작 전 항목은 시작일을 알려준다', () => {
    expect(delayDescription(row({ delayStatus: 'NOT_STARTED' }))).toContain('2026-09-01')
  })

  it('모든 상태에 대해 빈 문자열을 반환하지 않는다', () => {
    ALL_STATUSES.forEach((status) => {
      expect(delayDescription(row({ delayStatus: status }))).toBeTruthy()
    })
  })
})

describe('countByStatus', () => {
  it('상태별 개수를 센다', () => {
    const rows = [
      row({ delayStatus: 'DELAYED' }),
      row({ delayStatus: 'DELAYED' }),
      row({ delayStatus: 'ON_TRACK' }),
    ]

    expect(countByStatus(rows, 'DELAYED')).toBe(2)
    expect(countByStatus(rows, 'ON_TRACK')).toBe(1)
    expect(countByStatus(rows, 'AT_RISK')).toBe(0)
  })
})

describe('localToday', () => {
  it('YYYY-MM-DD 형식으로 자리수를 맞춘다', () => {
    expect(localToday()).toMatch(/^\d{4}-\d{2}-\d{2}$/)
  })
})
