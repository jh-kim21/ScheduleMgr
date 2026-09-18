import { describe, expect, it } from 'vitest'
import { adjacentStatus, moveTargets } from './boardMove'

describe('moveTargets', () => {
  it('지금 칸을 뺀 나머지 세 칸을 순서대로 돌려준다', () => {
    expect(moveTargets('IN_PROGRESS')).toEqual(['TODO', 'REVIEW', 'DONE'])
  })

  it('맨 앞/맨 뒤 칸도 나머지 세 칸을 그대로 돌려준다', () => {
    expect(moveTargets('TODO')).toEqual(['IN_PROGRESS', 'REVIEW', 'DONE'])
    expect(moveTargets('DONE')).toEqual(['TODO', 'IN_PROGRESS', 'REVIEW'])
  })
})

describe('adjacentStatus', () => {
  it('가운데 칸에서는 양쪽 다 이웃 칸이 있다', () => {
    expect(adjacentStatus('IN_PROGRESS', -1)).toBe('TODO')
    expect(adjacentStatus('IN_PROGRESS', 1)).toBe('REVIEW')
  })

  it('맨 앞 칸에서 왼쪽은 없다 — 순환하지 않는다', () => {
    expect(adjacentStatus('TODO', -1)).toBeNull()
    expect(adjacentStatus('TODO', 1)).toBe('IN_PROGRESS')
  })

  it('맨 뒤 칸에서 오른쪽은 없다', () => {
    expect(adjacentStatus('DONE', 1)).toBeNull()
    expect(adjacentStatus('DONE', -1)).toBe('REVIEW')
  })
})
