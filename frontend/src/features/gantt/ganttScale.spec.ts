import { describe, expect, it } from 'vitest'
import {
  addDays,
  barGeometry,
  createScale,
  dateAt,
  dayTicks,
  daysBetween,
  dayWidthFor,
  monthBands,
  PADDING_DAYS,
  showsDayNumbers,
  weekdayLabel,
  xFor,
  type GanttScale,
} from './ganttScale'

describe('날짜 계산', () => {
  it('두 날짜 사이의 일수를 센다', () => {
    expect(daysBetween('2026-09-01', '2026-09-10')).toBe(9)
    expect(daysBetween('2026-09-10', '2026-09-01')).toBe(-9)
    expect(daysBetween('2026-09-01', '2026-09-01')).toBe(0)
  })

  it('월과 연도 경계를 넘어 계산한다', () => {
    expect(daysBetween('2026-01-31', '2026-02-01')).toBe(1)
    expect(daysBetween('2026-12-31', '2027-01-01')).toBe(1)
  })

  it('윤년 2월을 올바르게 처리한다', () => {
    expect(daysBetween('2028-02-28', '2028-03-01')).toBe(2)
    expect(addDays('2028-02-28', 1)).toBe('2028-02-29')
  })

  it('날짜를 더하고 빼면 자리수를 맞춘 문자열을 돌려준다', () => {
    expect(addDays('2026-09-30', 1)).toBe('2026-10-01')
    expect(addDays('2026-01-01', -1)).toBe('2025-12-31')
    expect(addDays('2026-09-05', 0)).toBe('2026-09-05')
  })
})

describe('createScale', () => {
  it('일정이 없으면 스케일을 만들지 않는다', () => {
    expect(createScale(null, null)).toBeNull()
    expect(createScale('2026-09-01', null)).toBeNull()
    expect(createScale(null, '2026-09-01')).toBeNull()
  })

  it('양쪽에 여유 일수를 붙인 범위를 만든다', () => {
    const scale = createScale('2026-09-10', '2026-09-20')!

    expect(scale.rangeStart).toBe(addDays('2026-09-10', -PADDING_DAYS))
    expect(scale.rangeEnd).toBe(addDays('2026-09-20', PADDING_DAYS))
    expect(scale.totalDays).toBe(11 + PADDING_DAYS * 2)
    expect(scale.width).toBe(scale.totalDays * scale.dayWidth)
  })

  it('기간이 길어지면 하루 폭을 좁힌다', () => {
    expect(dayWidthFor(30)).toBeGreaterThan(dayWidthFor(100))
    expect(dayWidthFor(100)).toBeGreaterThan(dayWidthFor(400))
  })

  it('하루 폭이 좁으면 날짜 숫자를 숨긴다', () => {
    const short = createScale('2026-09-01', '2026-09-20')!
    const long = createScale('2026-01-01', '2027-06-30')!

    expect(showsDayNumbers(short)).toBe(true)
    expect(showsDayNumbers(long)).toBe(false)
  })
})

describe('좌표 변환', () => {
  const scale: GanttScale = {
    rangeStart: '2026-09-01',
    rangeEnd: '2026-09-30',
    totalDays: 30,
    dayWidth: 10,
    width: 300,
  }

  it('범위 시작일은 x=0이다', () => {
    expect(xFor(scale, '2026-09-01')).toBe(0)
    expect(xFor(scale, '2026-09-11')).toBe(100)
  })

  it('막대는 종료일을 포함해 하루만큼 더 넓다', () => {
    // 09-01 ~ 09-01 은 하루짜리 업무이므로 한 칸을 차지한다.
    expect(barGeometry(scale, '2026-09-01', '2026-09-01')).toEqual({ x: 0, width: 10 })
    expect(barGeometry(scale, '2026-09-01', '2026-09-10')).toEqual({ x: 0, width: 100 })
    expect(barGeometry(scale, '2026-09-11', '2026-09-15')).toEqual({ x: 100, width: 50 })
  })

  it('일정이 비어 있으면 막대를 만들지 않는다', () => {
    expect(barGeometry(scale, null, '2026-09-10')).toBeNull()
    expect(barGeometry(scale, '2026-09-01', null)).toBeNull()
  })
})

describe('좌표에서 날짜 읽기', () => {
  const scale = createScale('2026-09-10', '2026-09-20')!

  it('하루 칸 안의 어느 지점이든 그 날짜를 돌려준다', () => {
    expect(dateAt(scale, 0)).toBe('2026-09-07')
    expect(dateAt(scale, scale.dayWidth - 1)).toBe('2026-09-07')
    expect(dateAt(scale, scale.dayWidth)).toBe('2026-09-08')
  })

  it('xFor와 왕복이 맞는다', () => {
    expect(dateAt(scale, xFor(scale, '2026-09-15'))).toBe('2026-09-15')
  })

  it('그려진 범위를 벗어나면 날짜를 만들지 않는다', () => {
    expect(dateAt(scale, -1)).toBeNull()
    expect(dateAt(scale, scale.width)).toBeNull()
    expect(dateAt(scale, scale.width - 1)).toBe(scale.rangeEnd)
  })

  it('요일 라벨을 돌려준다', () => {
    expect(weekdayLabel('2026-09-14')).toBe('월')
    expect(weekdayLabel('2026-09-19')).toBe('토')
    expect(weekdayLabel('2026-09-20')).toBe('일')
  })
})

describe('축 눈금', () => {
  it('범위의 모든 날짜에 대해 눈금을 만든다', () => {
    const scale = createScale('2026-09-10', '2026-09-12')!
    const ticks = dayTicks(scale)

    expect(ticks).toHaveLength(scale.totalDays)
    expect(ticks[0].date).toBe(scale.rangeStart)
    expect(ticks.at(-1)!.date).toBe(scale.rangeEnd)
    expect(ticks[1].x).toBe(scale.dayWidth)
  })

  it('토요일과 일요일을 주말로 표시한다', () => {
    // 2026-09-05는 토요일, 09-06은 일요일이다.
    const scale = createScale('2026-09-05', '2026-09-06')!
    const ticks = dayTicks(scale)

    const saturday = ticks.find((tick) => tick.date === '2026-09-05')!
    const sunday = ticks.find((tick) => tick.date === '2026-09-06')!
    const monday = ticks.find((tick) => tick.date === '2026-09-07')!

    expect(saturday.weekend).toBe(true)
    expect(sunday.weekend).toBe(true)
    expect(monday.weekend).toBe(false)
  })

  it('연속된 같은 달을 하나의 띠로 합친다', () => {
    const scale = createScale('2026-09-20', '2026-10-05')!
    const bands = monthBands(scale)

    expect(bands.map((band) => band.label)).toEqual(['9월', '10월'])
    expect(bands.reduce((sum, band) => sum + band.width, 0)).toBe(scale.width)
    expect(bands[0].x).toBe(0)
  })

  it('연도를 넘는 범위에서는 라벨에 연도를 포함한다', () => {
    const scale = createScale('2026-12-20', '2027-01-10')!
    const bands = monthBands(scale)

    expect(bands.map((band) => band.label)).toEqual(['2026.12', '2027.01'])
  })
})
