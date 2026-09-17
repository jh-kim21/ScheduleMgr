import type { MemberRef, WbsNode } from '../../api/wbsApi'

/** WBS 트리의 담당자 열이 이름 하나를 그리는 데 필요한 전부. */
export interface ResponsibleName {
  memberId: number
  name: string
  /** 상위 단계에서 물려받은 이름. 이 행에서는 지울 수 없고, 그 글자를 가진 행을 고쳐야 한다. */
  inherited: boolean
}

type ResponsibleSource = Pick<WbsNode, 'responsible' | 'responsibleInherited'>

/**
 * 한 행에 표시할 담당자들. 자기 담당자가 먼저, 물려받은 담당자가 뒤다 — 이 행이 실제로 선언한
 * 사람이 먼저 읽혀야 한다.
 *
 * <p>두 배열을 합치되 **한 사람이 두 번 나오지 않게** 한다. 계약상 한 역할의 유효 배정은 하나라
 * (자기 것이 있으면 상속은 오지 않는다) 실제로는 겹치지 않지만, 겹치는 순간 화면에 같은 이름이
 * 두 번 찍혀 결함처럼 보인다. 한 줄로 막을 수 있는 것을 계약에만 맡기지 않는다.
 *
 * <p>양쪽 모두 `?? []`로 받는다. Phase C 이전에 찍은 커밋의 payload에는 이 필드가 아예 없고
 * (커밋은 그때의 응답을 그대로 박제한다), 그 커밋을 열면 `undefined`가 들어온다.
 */
export function responsibleNames(node: ResponsibleSource): ResponsibleName[] {
  const seen = new Set<number>()
  const collect = (members: MemberRef[] | undefined, inherited: boolean) =>
    (members ?? [])
      .filter((member) => !seen.has(member.memberId) && seen.add(member.memberId))
      .map((member) => ({ memberId: member.memberId, name: member.name, inherited }))

  return [...collect(node.responsible, false), ...collect(node.responsibleInherited, true)]
}

/**
 * 셀 전체의 `title` — 이름이 많아 `.cell-clip`으로 잘렸을 때 전부를 보여 주기 위한 것이다
 * (CLAUDE.md 표 규칙). 아무도 없으면 `null`이라 `title` 자체가 붙지 않는다.
 *
 * 물려받은 이름에 꼬리표를 붙이는 이유는, 잘린 목록을 툴팁으로 펼쳤을 때 화면에서 흐리게 그린
 * 구분이 사라지기 때문이다.
 */
export function responsibleTitle(names: ResponsibleName[]): string | null {
  if (names.length === 0) return null
  const listed = names
    .map((entry) => (entry.inherited ? `${entry.name} (상위 단계에서 물려받음)` : entry.name))
    .join(', ')
  return `담당자 ${listed} — RACI 화면에서 바꿉니다`
}
