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
