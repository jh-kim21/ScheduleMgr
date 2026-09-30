/**
 * 진척 가중치의 등급 척도. 체크포인트(Waterfall·Hybrid 분모), Backlog 항목(Agile 분모),
 * WBS 형제(롤업 비중)가 함께 쓰므로 feature가 아니라 shared에 둔다 — 세 화면이 다른 척도를
 * 말하면 안 된다.
 *
 * 값은 DB에 그대로 저장되는 정수다. 등급은 입력 어휘일 뿐이라 서버는 이 척도를 모르고,
 * 척도 밖 값도 거부하지 않는다(구형 파일 가져오기·커밋 복원이 그런 값을 싣고 온다).
 *
 * **어휘는 셋이 공유하지만 `null`의 뜻은 둘이다.** 체크포인트·Backlog의 미입력은 상수
 * `DEFAULT_WEIGHT`(백엔드 `ProgressCalculator.weightOf`와 짝)이고, WBS 형제의 미입력은
 * 형제 크기에 비례하는 값(`ProgressCalculator.unitPerLeaf`)이라 상수로 적을 수 없다.
 * 그래서 아래 함수가 용도별로 나뉘어 있다 — 돌려쓰지 말 것(지시서 §2-c).
 */
export interface WeightGrade {
  value: number
  label: string
  /** 한 Work Package 안(체크포인트·Backlog)에서 이 등급을 고를 판단 기준. */
  hint: string
  /** 형제 WBS 항목 사이에서 이 등급을 고를 판단 기준. 같은 값이라도 견주는 대상이 다르다. */
  wbsHint: string
}

/** 큰 것부터. 드롭다운이 이 순서로 읽힌다. */
export const WEIGHT_GRADES: WeightGrade[] = [
  { value: 8, label: '아주 큼', hint: '이 업무의 절반 이상', wbsHint: '이 단계의 절반 이상을 차지한다' },
  { value: 5, label: '큼', hint: '큰 덩어리 하나', wbsHint: '형제 중 가장 무거운 축이다' },
  { value: 3, label: '보통', hint: '평범한 한 단계', wbsHint: '형제와 비슷한 크기다' },
  { value: 2, label: '작음', hint: '짧게 끝나는 단계', wbsHint: '형제보다 가볍게 끝난다' },
  { value: 1, label: '아주 작음', hint: '형식적 확인', wbsHint: '거의 형식적이다' },
]

/**
 * 미입력(`null`)을 대신하는 값 — **체크포인트·Backlog 전용**.
 *
 * **백엔드 `ProgressCalculator.weightOf`의 폴백과 같아야 한다** — 두 값을 잇는 컴파일 타임
 * 장치가 없으므로 한쪽만 고치면 조용히 어긋난다.
 *
 * **WBS 형제에는 쓰지 말 것.** 그쪽 미입력은 `unitPerLeaf × leafCount`라 상수가 아니고,
 * 이 값을 박아 넣으면 그 프로젝트의 진척이 즉시 바뀐다(지시서 §2-c).
 */
export const DEFAULT_WEIGHT = 3

/** 드롭다운에서 고를 수 있는 값인가. `0`과 척도 밖 값은 레거시 전용이라 `false`. */
export function isOnScale(weight: number | null): boolean {
  return weight !== null && WEIGHT_GRADES.some((g) => g.value === weight)
}

/**
 * 척도 위의 값과 레거시 값을 읽을 수 있는 한 마디로. `null`은 다루지 않는다 —
 * 미입력의 뜻이 용도마다 다르기 때문이다(`weightLabel` / `wbsWeightLabel`).
 */
export function gradeLabel(weight: number): string {
  if (weight === 0) return '집계 제외(0)'
  return WEIGHT_GRADES.find((g) => g.value === weight)?.label ?? `사용자 지정 ${weight}`
}

/**
 * 체크포인트·Backlog 표시용.
 *
 * `null`은 `보통`이다 — 계산이 실제로 `DEFAULT_WEIGHT`로 돌아가므로 그렇게 적는 것이 사실에
 * 맞는다.
 */
export function weightLabel(weight: number | null): string {
  if (weight === null) return WEIGHT_GRADES.find((g) => g.value === DEFAULT_WEIGHT)!.label
  return gradeLabel(weight)
}

/**
 * WBS 형제 표시용.
 *
 * `null`은 `미지정`이다 — 이 값의 몫은 상수가 아니라 형제의 선언값에서 나오므로(`unitPerLeaf`)
 * 어떤 등급 이름으로도 적을 수 없다. `보통`이라 적으면 거짓이다.
 */
export function wbsWeightLabel(weight: number | null): string {
  if (weight === null) return '미지정'
  return gradeLabel(weight)
}

/**
 * 폼에 채울 값 — **체크포인트·Backlog 전용**. 미입력은 기본 등급으로 미리 골라 둔다(§2-f).
 *
 * WBS 폼은 이 함수를 쓰지 않는다 — `null`을 `null`인 채로 열고 그대로 돌려보내야 한다.
 */
export function weightForForm(weight: number | null): number {
  return weight ?? DEFAULT_WEIGHT
}
