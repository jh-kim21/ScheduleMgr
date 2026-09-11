import { describe, expect, it } from 'vitest'
import {
  assignSelectionSummary,
  filterAssignable,
  type AssignCandidate,
} from './assignFilter'

function candidate(over: Partial<AssignCandidate> = {}): AssignCandidate {
  return {
    id: 1,
    title: '항목',
    typeLabel: 'Story',
    storyPoint: null,
    ...over,
  }
}

describe('filterAssignable', () => {
  it('빈 문자열 query면 전부 원본 순서 그대로 돌려준다', () => {
    const list = [candidate({ id: 1 }), candidate({ id: 2 }), candidate({ id: 3 })]

    expect(filterAssignable(list, '')).toEqual(list)
  })

  it('공백만 있는 query도 전부 원본 순서 그대로다', () => {
    const list = [candidate({ id: 1 }), candidate({ id: 2 }), candidate({ id: 3 })]

    expect(filterAssignable(list, '   ')).toEqual(list)
  })

  it('제목 부분 일치는 대소문자를 가리지 않는다 — "story"가 "Story"를 찾는다', () => {
    const target = candidate({ id: 1, title: '로그인 Story 작성', typeLabel: 'Task' })
    const other = candidate({ id: 2, title: '무관한 항목', typeLabel: 'Task' })

    expect(filterAssignable([target, other], 'story')).toEqual([target])
  })

  it('유형 라벨로도 걸린다 — 제목에 Bug가 없어도 Bug 유형이면 나온다', () => {
    const bug = candidate({ id: 1, title: '로그인 오류', typeLabel: 'Bug' })
    const story = candidate({ id: 2, title: '로그인 화면', typeLabel: 'Story' })

    expect(filterAssignable([bug, story], 'Bug')).toEqual([bug])
  })

  it('앞뒤 공백이 붙은 query도 trim 되어 동작한다', () => {
    const target = candidate({ id: 1, title: '결제 기능' })
    const other = candidate({ id: 2, title: '무관한 항목' })

    expect(filterAssignable([target, other], '  결제  ')).toEqual([target])
  })

  it('아무것도 안 맞으면 빈 배열', () => {
    const list = [candidate({ id: 1, title: '가나다', typeLabel: 'Story' })]

    expect(filterAssignable(list, '해당없음')).toEqual([])
  })

  it(
    '걸러낸 결과가 원본 순서를 유지한다 — 이 목록의 순서는 Backlog 우선순위라 재정렬하면 뜻이 바뀐다',
    () => {
      const c1 = candidate({ id: 1, title: 'story 하나', typeLabel: 'Task' })
      const c2 = candidate({ id: 2, title: '무관', typeLabel: 'Task' })
      const c3 = candidate({ id: 3, title: 'story 둘', typeLabel: 'Task' })
      const c4 = candidate({ id: 4, title: 'story 셋', typeLabel: 'Task' })

      expect(filterAssignable([c1, c2, c3, c4], 'story')).toEqual([c1, c3, c4])
    },
  )

  it('한글 제목으로도 동작한다', () => {
    const target = candidate({ id: 1, title: '회원가입 화면 개선' })
    const other = candidate({ id: 2, title: '결제 기능 추가' })

    expect(filterAssignable([target, other], '회원가입')).toEqual([target])
  })
})

describe('assignSelectionSummary', () => {
  it('아무것도 안 골랐으면 {count: 0, points: 0, unestimated: 0}', () => {
    const list = [candidate({ id: 1, storyPoint: 3 })]

    expect(assignSelectionSummary(list, [])).toEqual({ count: 0, points: 0, unestimated: 0 })
  })

  it('기본 합산', () => {
    const c1 = candidate({ id: 1, storyPoint: 3 })
    const c2 = candidate({ id: 2, storyPoint: 5 })

    expect(assignSelectionSummary([c1, c2], [1, 2])).toEqual({ count: 2, points: 8, unestimated: 0 })
  })

  it(
    'storyPoint: null인 항목을 골라도 points가 늘지 않고 unestimated가 는다 — ' +
      'null을 0으로 합치면 "추정 없음"과 "0포인트"가 구분되지 않는다(산정 전 ≠ 0 과 같은 태도)',
    () => {
      const c1 = candidate({ id: 1, storyPoint: null })
      const c2 = candidate({ id: 2, storyPoint: 5 })

      expect(assignSelectionSummary([c1, c2], [1, 2])).toEqual({ count: 2, points: 5, unestimated: 1 })
    },
  )

  it('storyPoint: 0은 points에 0을 더하고 unestimated를 늘리지 않는다 — null과 0이 갈리는 자리다', () => {
    const c1 = candidate({ id: 1, storyPoint: 0 })
    const c2 = candidate({ id: 2, storyPoint: 5 })

    expect(assignSelectionSummary([c1, c2], [1, 2])).toEqual({ count: 2, points: 5, unestimated: 0 })
  })

  it('후보에 없는 id는 무시된다', () => {
    const c1 = candidate({ id: 1, storyPoint: 3 })

    expect(assignSelectionSummary([c1], [1, 999])).toEqual({ count: 1, points: 3, unestimated: 0 })
  })

  it('같은 id가 두 번 들어와도 한 번만 센다', () => {
    const c1 = candidate({ id: 1, storyPoint: 3 })

    expect(assignSelectionSummary([c1], [1, 1])).toEqual({ count: 1, points: 3, unestimated: 0 })
  })
})
