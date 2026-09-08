import { describe, expect, it } from 'vitest'
import {
  executionModeLabel,
  executionModeSummaryText,
  type ExecutionModeSummary,
} from './executionMode'

const summary = (over: Partial<ExecutionModeSummary> = {}): ExecutionModeSummary => ({
  waterfall: 0,
  agile: 0,
  hybrid: 0,
  unspecified: 0,
  ...over,
})

describe('executionModeLabel', () => {
  it('실행 방식이 없으면 미지정으로 읽는다', () => {
    expect(executionModeLabel(null)).toBe('미지정')
  })

  it('지정된 실행 방식은 그 이름으로 읽는다', () => {
    expect(executionModeLabel('AGILE')).toBe('Agile')
    expect(executionModeLabel('HYBRID')).toBe('Hybrid')
  })
})

describe('executionModeSummaryText', () => {
  it('하위가 없으면 빈 문자열', () => {
    expect(executionModeSummaryText(null)).toBe('')
    expect(executionModeSummaryText(summary())).toBe('')
  })

  it('0건인 실행 방식은 빼고, 고정된 순서로 이어 붙인다', () => {
    expect(executionModeSummaryText(summary({ agile: 3, waterfall: 1 }))).toBe('Waterfall 1 · Agile 3')
  })

  it('미지정은 숨기지 않고 마지막에 붙인다 — 배정되지 않은 Work Package가 확인 대상이다', () => {
    expect(executionModeSummaryText(summary({ agile: 2, unspecified: 1 }))).toBe('Agile 2 · 미지정 1')
    expect(executionModeSummaryText(summary({ unspecified: 4 }))).toBe('미지정 4')
  })

  it('세 가지가 섞여 있어도 순서를 유지한다', () => {
    expect(executionModeSummaryText(summary({ hybrid: 1, agile: 1, waterfall: 1, unspecified: 1 })))
      .toBe('Waterfall 1 · Agile 1 · Hybrid 1 · 미지정 1')
  })
})
