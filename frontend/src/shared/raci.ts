/**
 * RACI roles and the wording for them, shared by the matrix and the issue banner so the two
 * screens cannot drift into naming the same letter differently — the same reason
 * `shared/delay.ts` exists.
 */

export type RaciRole = 'RESPONSIBLE' | 'ACCOUNTABLE' | 'CONSULTED' | 'INFORMED'

export type RaciIssueType =
  | 'MULTIPLE_ACCOUNTABLE'
  | 'MISSING_ACCOUNTABLE'
  | 'MISSING_RESPONSIBLE'

/** R, A, C, I — the order the letters are conventionally read in. */
export const RACI_ORDER: RaciRole[] = ['RESPONSIBLE', 'ACCOUNTABLE', 'CONSULTED', 'INFORMED']

export const RACI_LETTERS: Record<RaciRole, string> = {
  RESPONSIBLE: 'R',
  ACCOUNTABLE: 'A',
  CONSULTED: 'C',
  INFORMED: 'I',
}

export const RACI_LABELS: Record<RaciRole, string> = {
  RESPONSIBLE: '실무 담당',
  ACCOUNTABLE: '최종 책임',
  CONSULTED: '자문',
  INFORMED: '통보',
}

export const RACI_DESCRIPTIONS: Record<RaciRole, string> = {
  RESPONSIBLE: '실제로 일을 하는 사람. 업무마다 최소 한 명 필요합니다.',
  ACCOUNTABLE: '결과를 책임지는 사람. 업무마다 한 명이어야 합니다.',
  CONSULTED: '진행 중 의견을 구하는 사람.',
  INFORMED: '결과를 통보받는 사람.',
}

/**
 * 약자가 무엇의 머리글자인지. 화면이 제목 옆에 풀어 적는 데 쓴다.
 * 단수로 적는다 — "그 글자가 무슨 단어인가"에 대한 답이라 항목 하나를 가리킬 때도 그대로 쓸 수 있다.
 * 약자를 통째로 풀 때는 흔히 복수(Responsibilities 등)를 쓰지만, 한 벌만 두는 이상 단수가 재사용에 맞다.
 */
export const RACI_ENGLISH: Record<RaciRole, string> = {
  RESPONSIBLE: 'Responsible',
  ACCOUNTABLE: 'Accountable',
  CONSULTED: 'Consulted',
  INFORMED: 'Informed',
}

/** Sorts a cell's letters into RACI order regardless of the order they were assigned. */
export function sortRoles(roles: RaciRole[]): RaciRole[] {
  return roles.slice().sort((a, b) => RACI_ORDER.indexOf(a) - RACI_ORDER.indexOf(b))
}

export function issueSummary(type: RaciIssueType): string {
  switch (type) {
    case 'MULTIPLE_ACCOUNTABLE':
      return '최종 책임자 중복'
    case 'MISSING_ACCOUNTABLE':
      return '최종 책임자 없음'
    case 'MISSING_RESPONSIBLE':
      return '실무 담당자 없음'
  }
}
