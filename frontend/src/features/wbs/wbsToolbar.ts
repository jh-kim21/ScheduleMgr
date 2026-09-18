import type { WbsNode } from '../../api/wbsApi'

/** 5.2 표의 화면 전체 공통 문구 — `WbsTree.vue`가 전에 갖고 있던 지역 상수를 그대로 옮긴다. */
export const READONLY_HINT = '커밋 시점 조회 중에는 변경할 수 없습니다'
export const NO_SELECTION_HINT = '행을 먼저 고르세요'
export const WORK_PACKAGE_HINT =
  'Work Package에는 하위 항목을 둘 수 없습니다. 수정에서 구분을 Summary로 바꾸세요.'

export interface ToolbarState {
  canAddChild: boolean
  canEdit: boolean
  canRemove: boolean
  /** 비활성일 때 [하위 추가] 버튼의 title — 이유를 말하지 않으면 왜 못 누르는지 알 수 없다. */
  addChildHint: string | null
}

/**
 * 툴바 세 버튼의 활성/비활성 판정. 지금 행 버튼이 쓰던 조건(`WbsTree.vue:641-668`)을 그대로
 * 옮기되 "선택 없음"이 더해진다 — 행 버튼은 항상 자기 행이 선택된 상태였으므로 이 경우가 없었다.
 */
export function toolbarState(selected: WbsNode | null, readOnly: boolean): ToolbarState {
  const canEdit = !readOnly && selected !== null
  const canRemove = canEdit
  const isWorkPackage = selected?.nodeType === 'WORK_PACKAGE'
  const canAddChild = canEdit && !isWorkPackage

  let addChildHint: string | null = null
  if (readOnly) {
    addChildHint = READONLY_HINT
  } else if (selected === null) {
    addChildHint = NO_SELECTION_HINT
  } else if (isWorkPackage) {
    addChildHint = WORK_PACKAGE_HINT
  }

  return { canAddChild, canEdit, canRemove, addChildHint }
}

/**
 * [수정]·[삭제] 버튼의 title — 두 버튼의 비활성 조건이 같아(`readOnly` 또는 선택 없음) 판정도
 * 하나로 공유한다. 누를 수 있으면 이유가 없으므로 `null`이다.
 */
export function actionHint(selected: WbsNode | null, readOnly: boolean): string | null {
  if (readOnly) return READONLY_HINT
  if (selected === null) return NO_SELECTION_HINT
  return null
}
