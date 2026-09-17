import { describe, expect, it } from 'vitest'
import { TAG_COLORS, isTagColor, tagColorFor } from './tagColor'

/**
 * 지시서 부록 함정 7: **태그 색을 랜덤으로 정하면 다시 그릴 때마다 바뀐다.** 스크롤 한 번에 같은
 * 태그가 다른 색이 되면 "분야별로 훑는다"는 이 열의 목적 자체가 사라진다. 그래서 이 모듈의 계약은
 * 하나뿐이다 — **같은 입력은 언제나 같은 슬롯.**
 */
describe('tagColorFor', () => {
  it('같은 이름은 언제나 같은 색을 받는다 — 몇 번을 물어도 바뀌지 않는다', () => {
    const first = tagColorFor({ name: 'Service', color: null })
    for (let i = 0; i < 50; i += 1) {
      expect(tagColorFor({ name: 'Service', color: null })).toBe(first)
    }
  })

  it('다른 인스턴스라도 이름이 같으면 같은 색이다 — 트리·폼·관리 화면이 같은 칩을 그린다', () => {
    expect(tagColorFor({ name: 'Web', color: null })).toBe(tagColorFor({ name: 'Web' }))
  })

  it('저장된 색이 아는 슬롯이면 그것을 쓴다 — 이름 해시보다 사용자가 고른 값이 우선이다', () => {
    // 'Service'가 해시로 'rose'를 받지는 않는다는 것까지 함께 확인해야 "지정이 이겼다"가 된다.
    expect(tagColorFor({ name: 'Service', color: null })).not.toBe('rose')
    expect(tagColorFor({ name: 'Service', color: 'rose' })).toBe('rose')
  })

  it('모르는 색값(가져오기·구형 데이터의 #3366ff 등)은 이름 해시로 떨어진다 — 그대로 칠하지 않는다', () => {
    expect(tagColorFor({ name: 'Service', color: '#3366ff' })).toBe(
      tagColorFor({ name: 'Service', color: null }),
    )
  })

  it('언제나 팔레트 안의 값을 돌려준다 — 토큰이 없는 이름이 나오면 칩이 색 없이 그려진다', () => {
    const names = ['Service', 'Web', 'DB', '인프라', 'QA', '문서', 'Batch', 'Mobile', '', 'a']
    for (const name of names) {
      expect(TAG_COLORS).toContain(tagColorFor({ name, color: null }))
    }
  })

  it('이름이 다르면 대체로 색도 갈린다 — 전부 한 색으로 몰리면 구분에 쓸모가 없다', () => {
    const names = ['Service', 'Web', 'DB', '인프라', 'QA', '문서', 'Batch', 'Mobile']
    const distinct = new Set(names.map((name) => tagColorFor({ name, color: null })))
    // 여덟 이름이 여덟 슬롯에 완벽히 흩어지기를 요구하지는 않는다(해시라 충돌은 정상) —
    // 절반 이상으로 갈리면 이 열이 제구실을 한다.
    expect(distinct.size).toBeGreaterThanOrEqual(4)
  })
})

describe('isTagColor', () => {
  it('팔레트에 있는 이름만 통과시킨다', () => {
    expect(isTagColor('blue')).toBe(true)
    expect(isTagColor('BLUE')).toBe(false)
    expect(isTagColor('#3366ff')).toBe(false)
    expect(isTagColor(null)).toBe(false)
    expect(isTagColor(undefined)).toBe(false)
  })
})
