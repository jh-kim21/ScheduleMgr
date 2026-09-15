import { describe, expect, it } from 'vitest'
import type { CheckpointDetail } from '../../api/progressApi'
import {
  draftFrom,
  emptyDraft,
  isSubmittable,
  toCheckpointInput,
} from './checkpointForm'

/**
 * `checkpointForm.ts`는 `CheckpointList.vue`가 화면에 옮기기 전에 거쳐야 하는 판정(수정 시 어떤
 * 값을 채우는지, 제출 시 무엇을 보내는지)을 뺀 순수 함수다. `CheckpointList.vue`를 마운트해서도
 * 검증할 수 있지만, 이 계약은 화면 마크업이 아니라 값 자체에 대한 것이라 순수 함수 테스트로
 * 고정하는 편이 더 빠르고 규칙이 더 또렷하다(CLAUDE.md "컴포넌트 테스트": 컴포넌트 테스트는
 * 렌더링·상호작용을, 순수 함수 테스트는 판정을 덮는다). 여기서 고정하는 것이
 * docs/tasks/chepoint-input.md 7절의 완료 기준 중 "수정 버튼을 누르면 기존 값이 채워진다",
 * "저장 거부 시 값이 사라지지 않는다"를 실제로 지키는 자동 검증이다.
 */
function baseCheckpoint(overrides: Partial<CheckpointDetail> = {}): CheckpointDetail {
  return {
    id: 1,
    title: '설계 리뷰',
    weight: 2,
    completionCriteria: '리뷰어 승인',
    approved: false,
    approvedBy: null,
    approvedAt: null,
    ...overrides,
  }
}

describe('draftFrom', () => {
  it('제목·가중치·완료조건을 있는 그대로 채운다 — 수정 버튼을 누르면 기존 값이 폼에 채워져야 한다', () => {
    const draft = draftFrom(baseCheckpoint({ title: '설계 리뷰', weight: 2, completionCriteria: '리뷰어 승인' }))

    expect(draft.title).toBe('설계 리뷰')
    expect(draft.weight).toBe(2)
    expect(draft.criteria).toBe('리뷰어 승인')
  })

  it('weight: null 은 균등을 뜻하므로 null 그대로 옮긴다 — 0으로 바뀌면 화면이 "가중치 0"으로 잘못 읽는다', () => {
    const draft = draftFrom(baseCheckpoint({ weight: null }))

    expect(draft.weight).toBeNull()
  })

  it('completionCriteria: null 은 입력칸에 넣어야 하므로 빈 문자열로 바꾼다', () => {
    const draft = draftFrom(baseCheckpoint({ completionCriteria: null }))

    expect(draft.criteria).toBe('')
  })

  it('weight: 0 은 null 로 뭉개지 않고 0 그대로 옮긴다 — 0과 미입력(균등)은 다른 값이다', () => {
    const draft = draftFrom(baseCheckpoint({ weight: 0 }))

    expect(draft.weight).toBe(0)
  })
})

describe('emptyDraft', () => {
  it('빈 입력값을 준다 — 제목·완료조건은 빈 문자열, 가중치는 null(균등)이지 0이 아니다', () => {
    const draft = emptyDraft()

    expect(draft.title).toBe('')
    expect(draft.weight).toBeNull()
    expect(draft.criteria).toBe('')
  })
})

describe('toCheckpointInput', () => {
  it('제목 앞뒤 공백을 trim 한다', () => {
    const input = toCheckpointInput(10, { title: '  설계 리뷰  ', weight: 2, criteria: '리뷰어 승인' })

    expect(input.title).toBe('설계 리뷰')
  })

  it('완료조건이 비어 있으면(공백뿐이어도) null로 나간다 — 빈 문자열로 저장하면 나중에 "값이 있는데 빈 것"과 구분이 안 된다', () => {
    expect(toCheckpointInput(10, { title: '설계 리뷰', weight: 2, criteria: '' }).completionCriteria).toBeNull()
    expect(toCheckpointInput(10, { title: '설계 리뷰', weight: 2, criteria: '   ' }).completionCriteria).toBeNull()
  })

  it('완료조건에 값이 있으면 trim해서 담는다', () => {
    const input = toCheckpointInput(10, { title: '설계 리뷰', weight: 2, criteria: '  리뷰어 승인  ' })

    expect(input.completionCriteria).toBe('리뷰어 승인')
  })

  it('가중치 null은 0으로 바뀌지 않는다 — null(균등)이 0이 되면 모든 가중치가 0인 것과 같아져 분모가 0이 되고 진척이 산정 전이 된다', () => {
    const input = toCheckpointInput(10, { title: '설계 리뷰', weight: null, criteria: '' })

    expect(input.weight).toBeNull()
  })

  it('가중치 0은 null로 바뀌지 않는다 — 0과 미입력은 다른 값이다', () => {
    const input = toCheckpointInput(10, { title: '설계 리뷰', weight: 0, criteria: '' })

    expect(input.weight).toBe(0)
  })

  it('wbsItemId를 그대로 담아 어느 항목의 체크포인트인지 표시한다', () => {
    const input = toCheckpointInput(42, { title: '설계 리뷰', weight: 2, criteria: '' })

    expect(input.wbsItemId).toBe(42)
  })
})

describe('isSubmittable', () => {
  it('제목이 비어 있으면 false', () => {
    expect(isSubmittable({ title: '', weight: 2, criteria: '' })).toBe(false)
  })

  it('제목이 공백뿐이면 false', () => {
    expect(isSubmittable({ title: '   ', weight: null, criteria: '' })).toBe(false)
  })

  it('제목이 있으면 true — 가중치·완료조건은 선택값이라 필수 조건에 관여하지 않는다', () => {
    expect(isSubmittable({ title: '설계 리뷰', weight: null, criteria: '' })).toBe(true)
  })
})
