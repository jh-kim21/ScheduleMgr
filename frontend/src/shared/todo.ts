export type TodoPriority = 'HIGH' | 'MEDIUM' | 'LOW'

/** 높음 → 낮음 순서. 정렬과 드롭다운이 같은 순서를 쓴다. */
export const TODO_PRIORITY_ORDER: TodoPriority[] = ['HIGH', 'MEDIUM', 'LOW']

export const TODO_PRIORITY_LABELS: Record<TodoPriority, string> = {
  HIGH: '높음',
  MEDIUM: '보통',
  LOW: '낮음',
}

/** 폼의 기본값. 할 일에는 언제나 상대적인 급함이 있어 "미지정"을 따로 두지 않는다. */
export const DEFAULT_TODO_PRIORITY: TodoPriority = 'MEDIUM'
