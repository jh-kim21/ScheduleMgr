import type { TagRef, WbsNode } from '../../api/wbsApi'

/** WBS 트리의 분야 열이 한 행에 그릴 것. */
export interface TagCell {
  /** 그릴 칩들. 비어 있으면 `-`를 적는다. */
  chips: TagRef[]
  /** 하위에서 모아 온 값인가 — 그렇다면 이 행 자신의 분야가 아니므로 흐리게 그린다. */
  rolledUp: boolean
  /**
   * Summary가 전환 전부터 들고 있는 자기 태그. 실행 방식의 "보관 N"과 같은 값이고, 요약(`chips`)
   * 에는 들어가지 않는다 — 지금 적용되는 값이 아니라는 사실만 따로 알린다.
   */
  retained: TagRef[]
}

type TagSource = Pick<WbsNode, 'tags' | 'tagSummary' | 'children'>

/**
 * 분야 열의 판정. 실행 방식 열과 **같은 규칙**이다 — 자식이 있으면 자기 값을 쓰지 않고 하위 요약을
 * 보여주고, 자기 값은 "보관 중"으로만 알린다.
 *
 * <p>자식 유무로 가르는 것(`nodeType`이 아니라)도 실행 방식 열과 맞춘 것이다. 서버가
 * `tagSummary`를 채우는 조건이 "자식이 있는가"이므로, 화면이 `nodeType`으로 물으면 Summary로
 * 전환했지만 아직 자식이 없는 과도 상태에서 요약 자리에 `null`을 그리게 된다.
 *
 * <p>세 필드 모두 없을 수 있다 — Phase D 이전에 찍은 커밋의 payload에는 이 필드가 없다.
 */
export function tagCell(node: TagSource): TagCell {
  const own = node.tags ?? []
  if (node.children.length === 0) {
    return { chips: own, rolledUp: false, retained: [] }
  }
  return { chips: node.tagSummary ?? [], rolledUp: true, retained: own }
}
