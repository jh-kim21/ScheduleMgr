import type { CheckpointDetail, CheckpointInput } from '../../api/progressApi'
import { DEFAULT_WEIGHT, weightForForm } from '../../shared/weight'

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

/** 추가 모드의 빈 입력값. 가중치는 기본 등급(보통)으로 미리 골라 둔다. */
export function emptyDraft(): CheckpointDraft {
  return { title: '', weight: DEFAULT_WEIGHT, criteria: '' }
}

/**
 * 수정 버튼을 눌렀을 때 폼에 채울 값.
 *
 * 미입력(`null`)은 기본 등급으로 preselect 한다 — 계산이 이미 그 값으로 돌아가므로(백엔드
 * `weightOf`) 저장해도 결과가 달라지지 않고, 대신 `null`이 하나씩 사라진다. `0`과 척도 밖 값은
 * **그대로 둔다**: 제목만 고치려는 저장이 가중치를 덮으면 안 된다.
 */
export function draftFrom(checkpoint: CheckpointDetail): CheckpointDraft {
  return {
    title: checkpoint.title,
    weight: weightForForm(checkpoint.weight),
    criteria: checkpoint.completionCriteria ?? '',
  }
}

/**
 * 서버로 보낼 형태. 제목은 trim, 완료조건은 비면 null. 가중치는 **그대로 보존한다** — 척도 밖
 * 값(`0`·`30`)을 등급으로 반올림하지 않는다. 제목만 고치려는 저장이 가중치를 덮으면 커밋
 * `da96ebe`와 같은 사고다.
 *
 * 폼은 이제 `null`을 보내지 않는다(`emptyDraft`·`draftFrom`이 `DEFAULT_WEIGHT`로 채운다). 그래도
 * 타입은 `number | null`로 남겨 둔다 — 서버가 계속 받는 값이고, `null`(미입력, 서버가 보통(3)으로
 * 계산)과 `0`(진척에 기여하지 않음)은 여전히 다른 뜻이다.
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
