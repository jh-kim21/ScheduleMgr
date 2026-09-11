/**
 * A placeholder-only suggestion for the WBS form's 가중치(weight) field.
 *
 * <p>Weight decides how much of a Summary's rolled-up progress a Work Package accounts for
 * (설계 §11.2), but nothing in the tree tells a PM what a "reasonable" number is — unlike Story
 * Points there is no team estimate to fall back on. Leaving the field with a bare placeholder
 * ("형제 간 비중") gives no anchor at all, so this derives one from what the sibling rows already
 * carry: their weight and, when available, how long they run. It is never written anywhere —
 * `null` still means "미입력" (CLAUDE.md 대시보드 절 "임의로 채우지 않습니다"와 같은 태도), the caller
 * only shows the number as a hint the user can ignore.
 *
 * <p>Kept as a pure function, not a computed inside `WbsForm.vue`: this repo has no
 * `@vue/test-utils`/DOM environment to mount a component with, so anything that needs a unit test
 * has to be extractable (같은 이유로 `ganttProgress.ts`, `wbsFormMapping.ts`, `rowSelection.ts`가
 * 뽑혀 나왔다).
 */

export interface WeightSuggestionSibling {
  weight: number | null
  startDate: string | null
  endDate: string | null
}

export interface WeightSuggestion {
  value: number
  /** DURATION: 형제의 기간당 가중치 비율로 추정. SIBLING_AVERAGE: 형제 가중치의 평균. */
  basis: 'DURATION' | 'SIBLING_AVERAGE'
}

/**
 * @param self     지금 폼에 입력된 시작·종료일. 사용자가 날짜를 입력하는 동안 값이 따라 움직이도록
 *                 폼이 매번 새로 계산해 넘긴다.
 * @param siblings 같은 부모 아래의 다른 항목들(자기 자신은 호출하는 쪽에서 이미 뺀 목록).
 */
export function suggestWeight(
  self: { startDate: string | null; endDate: string | null },
  siblings: WeightSuggestionSibling[],
): WeightSuggestion | null {
  // 가중치를 입력한 형제가 하나도 없으면 근거가 없다 — 숫자를 지어내지 않고 null을 돌려준다.
  const weighted = siblings.filter(
    (sibling): sibling is WeightSuggestionSibling & { weight: number } =>
      sibling.weight !== null && sibling.weight > 0,
  )
  if (weighted.length === 0) return null

  const dated = weighted.filter(
    (sibling) =>
      sibling.startDate !== null &&
      sibling.endDate !== null &&
      sibling.endDate >= sibling.startDate,
  )

  if (
    self.startDate !== null &&
    self.endDate !== null &&
    self.endDate >= self.startDate &&
    dated.length > 0
  ) {
    // 기간 비례: 형제들의 "가중치 / 기간"이 대략 일정하다고 보고, 그 단위를 이 항목의 기간에 적용한다.
    const totalWeight = dated.reduce((sum, sibling) => sum + (sibling.weight ?? 0), 0)
    const totalDays = dated.reduce(
      (sum, sibling) => sum + daysInclusive(sibling.startDate as string, sibling.endDate as string),
      0,
    )
    const unit = totalWeight / totalDays
    const selfDays = daysInclusive(self.startDate, self.endDate)
    // 0은 "진척에 기여하지 않음"이라는 뜻이 따로 있으므로(폼의 안내문과 같음) 제안값으로 내밀지 않는다.
    return { value: Math.max(1, Math.round(unit * selfDays)), basis: 'DURATION' }
  }

  const average = weighted.reduce((sum, sibling) => sum + sibling.weight, 0) / weighted.length
  return { value: Math.max(1, Math.round(average)), basis: 'SIBLING_AVERAGE' }
}

/**
 * Inclusive day count between two `YYYY-MM-DD` dates (CLAUDE.md "날짜는 종료일 포함(inclusive),
 * 달력일 기준"과 같은 규칙). Parsed by splitting the string and building a UTC timestamp rather
 * than `new Date(str)` — a local-timezone parse would shift the count by a day depending on the
 * machine running it, which would make the suggestion itself non-reproducible.
 */
function daysInclusive(start: string, end: string): number {
  const startUtc = toUtcDays(start)
  const endUtc = toUtcDays(end)
  return endUtc - startUtc + 1
}

function toUtcDays(value: string): number {
  const [year, month, day] = value.split('-').map(Number)
  return Date.UTC(year, month - 1, day) / 86_400_000
}
