import { describe, expect, it } from 'vitest'
import {
  DEFAULT_WEIGHT,
  WEIGHT_GRADES,
  gradeLabel,
  isOnScale,
  wbsWeightLabel,
  weightForForm,
  weightLabel,
} from './weight'

/**
 * 등급 척도는 순수 함수라 DOM이 필요 없다 — 이 파일에 `// @vitest-environment happy-dom`을 붙이지
 * 말 것(CLAUDE.md "컴포넌트 테스트": 기본 환경은 `node`이고, 컴포넌트 테스트 파일만 명시적으로
 * DOM 환경을 켠다).
 *
 * 여기서 고정하는 것의 핵심은 **`null`의 뜻이 둘**이라는 것이다(지시서 §2-c). 체크포인트·Backlog의
 * 미입력은 상수 `DEFAULT_WEIGHT`로 계산되지만, WBS 형제의 미입력은 형제의 선언값에서 뽑은
 * 단위(`ProgressCalculator.unitPerLeaf`)라 어떤 등급 이름으로도 적을 수 없다.
 */
describe('WEIGHT_GRADES', () => {
  it('1·2·3·5·8 다섯 등급을 큰 것부터 담는다 — 드롭다운이 이 순서로 읽힌다', () => {
    expect(WEIGHT_GRADES.map((g) => g.value)).toEqual([8, 5, 3, 2, 1])
  })

  it('0은 등급 사다리에 없다 — "진척에 기여하지 않음"은 "작다"가 아니라 별도의 뜻이라 새로 고를 수 없다(지시서 §2-e)', () => {
    expect(WEIGHT_GRADES.some((g) => g.value === 0)).toBe(false)
  })

  it('모든 등급에 두 가지 기준 문장이 비어 있지 않다 — 기준 문장이 이 작업의 본체다. 드롭다운만 만들고 기준을 빼면 "무엇을 적어야 하나"가 그대로 남는다', () => {
    for (const grade of WEIGHT_GRADES) {
      expect(grade.hint.trim().length, `${grade.label}의 hint`).toBeGreaterThan(0)
      expect(grade.wbsHint.trim().length, `${grade.label}의 wbsHint`).toBeGreaterThan(0)
    }
  })

  it('한 Work Package 안(hint)과 형제 사이(wbsHint)의 기준이 서로 다른 문장이다 — 같은 3이라도 견주는 대상이 다르다', () => {
    for (const grade of WEIGHT_GRADES) {
      expect(grade.hint, `${grade.label}`).not.toBe(grade.wbsHint)
    }
  })
})

describe('DEFAULT_WEIGHT', () => {
  it('실제로 척도 위의 값이다 — 척도 밖이면 weightLabel(null)의 `!` 단언이 터진다', () => {
    expect(WEIGHT_GRADES.some((g) => g.value === DEFAULT_WEIGHT)).toBe(true)
  })

  it('척도의 한가운데인 보통(3)이다 — 백엔드 ProgressCalculator.weightOf의 폴백과 같아야 한다. 잇는 컴파일 타임 장치가 없어 한쪽만 고치면 조용히 어긋난다', () => {
    expect(DEFAULT_WEIGHT).toBe(3)
    expect(gradeLabel(DEFAULT_WEIGHT)).toBe('보통')
  })
})

describe('gradeLabel', () => {
  it('척도 위의 다섯 값은 각자의 등급 이름으로 읽힌다', () => {
    expect(gradeLabel(8)).toBe('아주 큼')
    expect(gradeLabel(5)).toBe('큼')
    expect(gradeLabel(3)).toBe('보통')
    expect(gradeLabel(2)).toBe('작음')
    expect(gradeLabel(1)).toBe('아주 작음')
  })

  it('0은 "집계 제외(0)" — 등급이 아니라 별도의 뜻이라 사다리 이름을 주지 않는다', () => {
    expect(gradeLabel(0)).toBe('집계 제외(0)')
  })

  it('척도 밖 값은 숫자째로 드러낸다 — 구형 파일 가져오기·커밋 복원이 이런 값을 싣고 오고, 그것은 정상 데이터다', () => {
    expect(gradeLabel(4)).toBe('사용자 지정 4')
    expect(gradeLabel(30)).toBe('사용자 지정 30')
  })
})

describe('weightLabel — 체크포인트·Backlog', () => {
  it('척도 위·밖·0은 gradeLabel과 같게 읽는다', () => {
    expect(weightLabel(8)).toBe('아주 큼')
    expect(weightLabel(3)).toBe('보통')
    expect(weightLabel(1)).toBe('아주 작음')
    expect(weightLabel(0)).toBe('집계 제외(0)')
    expect(weightLabel(30)).toBe('사용자 지정 30')
  })

  it('null은 "보통" — 계산이 실제로 DEFAULT_WEIGHT로 폴백하므로 그렇게 적는 것이 사실에 맞는다', () => {
    expect(weightLabel(null)).toBe('보통')
  })
})

describe('wbsWeightLabel — WBS 형제', () => {
  it('척도 위·밖·0은 gradeLabel과 같게 읽는다 — 어휘는 셋이 공유한다', () => {
    expect(wbsWeightLabel(8)).toBe('아주 큼')
    expect(wbsWeightLabel(3)).toBe('보통')
    expect(wbsWeightLabel(1)).toBe('아주 작음')
    expect(wbsWeightLabel(0)).toBe('집계 제외(0)')
    expect(wbsWeightLabel(30)).toBe('사용자 지정 30')
  })

  it('null은 "미지정" — 이 값의 몫은 상수가 아니라 형제의 선언값에서 나오므로(unitPerLeaf × leafCount) 어떤 등급 이름으로도 적을 수 없다', () => {
    expect(wbsWeightLabel(null)).toBe('미지정')
  })
})

describe('null의 뜻은 둘이다 (지시서 §2-c)', () => {
  it('weightLabel(null)과 wbsWeightLabel(null)은 달라야 한다 — 둘을 같게 "정리"하려는 다음 사람을 여기서 멈춘다. 미입력 WBS 항목의 몫은 3이 아니다', () => {
    expect(weightLabel(null)).not.toBe(wbsWeightLabel(null))
  })

  it('null이 아닌 값에서는 두 함수가 같은 말을 한다 — 갈라지는 것은 미입력의 뜻뿐이고 어휘 자체는 하나다', () => {
    for (const weight of [0, 1, 2, 3, 5, 8, 30]) {
      expect(wbsWeightLabel(weight), `weight=${weight}`).toBe(weightLabel(weight))
    }
  })
})

describe('isOnScale', () => {
  it('다섯 등급은 true — 드롭다운에서 고를 수 있다', () => {
    for (const grade of WEIGHT_GRADES) {
      expect(isOnScale(grade.value), `${grade.label}`).toBe(true)
    }
  })

  it('null·0·척도 밖 값은 false — 이 셋은 레거시 전용이라 비활성 옵션으로만 보인다', () => {
    expect(isOnScale(null)).toBe(false)
    expect(isOnScale(0)).toBe(false)
    expect(isOnScale(4)).toBe(false)
    expect(isOnScale(30)).toBe(false)
  })
})

describe('weightForForm — 체크포인트·Backlog 전용', () => {
  it('null은 기본 등급으로 미리 골라 둔다 — 계산이 이미 그 값으로 돌아가므로 저장해도 결과가 달라지지 않고, 대신 null이 하나씩 사라진다', () => {
    expect(weightForForm(null)).toBe(DEFAULT_WEIGHT)
  })

  it('0은 그대로 둔다 — 제목만 고치려는 저장이 "집계 제외"를 등급으로 덮으면 커밋 da96ebe와 같은 사고다', () => {
    expect(weightForForm(0)).toBe(0)
  })

  it('척도 밖 값도 그대로 둔다', () => {
    expect(weightForForm(30)).toBe(30)
    expect(weightForForm(4)).toBe(4)
  })

  it('척도 위의 값은 손대지 않는다', () => {
    expect(weightForForm(8)).toBe(8)
    expect(weightForForm(1)).toBe(1)
  })
})
