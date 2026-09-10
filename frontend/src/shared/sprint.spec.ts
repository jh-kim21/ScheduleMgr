import { describe, expect, it } from 'vitest'
import { cancelStartWarning, daysRemaining, remainingLabel, sprintPeriod } from './sprint'

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

describe('cancelStartWarning', () => {
  it('완료 항목이 없으면 Story Point 소실만 알린다', () => {
    const message = cancelStartWarning(0)
    expect(message).toContain('Story Point')
    expect(message).not.toContain('완료 표시한')
  })

  it('완료 항목이 있으면 건수를 밝히고, 항목 자체는 남는다고 알린다', () => {
    const message = cancelStartWarning(3)
    expect(message).toContain('완료 표시한 3건')
    expect(message).toContain('항목 자체의 완료 상태는 그대로 남습니다')
    // Story Point 소실 안내는 완료 항목 유무와 관계없이 항상 붙는다.
    expect(message).toContain('Story Point가 지워집니다')
  })

  it('음수 doneItems는 0과 같이 다룬다 — 방어적으로, 실제로는 발생하지 않는다', () => {
    expect(cancelStartWarning(-1)).toBe(cancelStartWarning(0))
  })
})
