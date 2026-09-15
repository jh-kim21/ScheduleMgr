import type { CheckpointDetail, CheckpointInput } from '../../api/progressApi'

/**
 * The shape the add/edit form holds while the user is typing — kept separate from
 * `CheckpointInput` because the form also needs a title mid-edit (`''`) and a criteria box the
 * user hasn't decided to clear yet, neither of which the server accepts as-is.
 */
export interface CheckpointDraft {
  title: string
  weight: number | null
  criteria: string
}

/** 추가 모드의 빈 입력값. */
export function emptyDraft(): CheckpointDraft {
  return { title: '', weight: null, criteria: '' }
}

/** 수정 버튼을 눌렀을 때 폼에 채울 값. */
export function draftFrom(checkpoint: CheckpointDetail): CheckpointDraft {
  return {
    title: checkpoint.title,
    weight: checkpoint.weight,
    criteria: checkpoint.completionCriteria ?? '',
  }
}

/**
 * 서버로 보낼 형태. 제목은 trim, 완료조건은 비면 null. 가중치는 그대로 보존한다 — `null`(균등)과
 * `0`(진척에 기여하지 않음)은 다른 뜻이라 여기서 하나를 다른 것으로 바꾸면 안 된다.
 */
export function toCheckpointInput(wbsItemId: number, draft: CheckpointDraft): CheckpointInput {
  return {
    wbsItemId,
    title: draft.title.trim(),
    weight: draft.weight,
    completionCriteria: draft.criteria.trim() || null,
  }
}

/** 제목이 비어 있으면 저장 버튼을 누를 수 없다. */
export function isSubmittable(draft: CheckpointDraft): boolean {
  return draft.title.trim().length > 0
}
