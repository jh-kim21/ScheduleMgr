import { describe, expect, it } from 'vitest'
import { aggregatedType, backlogSummaryText, type BacklogSummary } from './backlog'

const summary = (over: Partial<BacklogSummary> = {}): BacklogSummary => ({
  items: 0,
  done: 0,
  archived: 0,
  ...over,
})

describe('backlogSummaryText', () => {
  it('연결이 없으면 빈 문자열', () => {
    expect(backlogSummaryText(null)).toBe('')
    expect(backlogSummaryText(summary())).toBe('')
  })

  it('설계 §4.3의 표기를 따른다', () => {
    expect(backlogSummaryText(summary({ items: 8, done: 4 }))).toBe('8개 항목 / 4개 완료')
  })

  it('보관은 합계에 섞지 않고 따로 적는다', () => {
    expect(backlogSummaryText(summary({ items: 3, done: 1, archived: 2 })))
      .toBe('3개 항목 / 1개 완료 · 보관 2')
  })

  it('보관만 남아 있어도 비어 보이지 않게 표시한다', () => {
    expect(backlogSummaryText(summary({ archived: 2 }))).toBe('보관 2')
  })
})

describe('aggregatedType', () => {
  it('Story와 Bug만 집계 대상이다 — Epic은 묶음, Task는 실행 상세다', () => {
    expect(aggregatedType('STORY')).toBe(true)
    expect(aggregatedType('BUG')).toBe(true)
    expect(aggregatedType('EPIC')).toBe(false)
    expect(aggregatedType('TASK')).toBe(false)
  })
})
