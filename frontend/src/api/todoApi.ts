import { http } from './http'
import type { TodoPriority } from '../shared/todo'

/** `completed`·`overdue`는 서버가 계산한다(`TodoAssessor`). 완료 여부의 원본은 `completedAt` 하나다. */
export interface TodoItem {
  id: number
  title: string
  priority: TodoPriority
  assigneeMemberId: number | null
  /** 서버가 채우므로 목록이 구성원을 따로 조회할 필요가 없다. */
  assigneeName: string | null
  dueDate: string | null
  memo: string | null
  completed: boolean
  completedAt: string | null
  overdue: boolean
  overdueDays: number
  createdAt: string
  updatedAt: string
}

export interface TodoList {
  /** 기한 초과를 판정한 서버 기준일. */
  referenceDate: string
  items: TodoItem[]
}

/** `completed`는 없다 — 완료는 별도 경로(`setCompleted`)로만 바꾼다. */
export interface TodoItemInput {
  title: string
  priority: TodoPriority
  assigneeMemberId: number | null
  dueDate: string | null
  memo: string | null
}

export const todoApi = {
  list: (projectId: number) => http.get<TodoList>(`/projects/${projectId}/todos`),
  create: (projectId: number, input: TodoItemInput) =>
    http.post<TodoList>(`/projects/${projectId}/todos`, input),
  update: (projectId: number, itemId: number, input: TodoItemInput) =>
    http.put<TodoList>(`/projects/${projectId}/todos/${itemId}`, input),
  setCompleted: (projectId: number, itemId: number, completed: boolean) =>
    http.put<TodoList>(`/projects/${projectId}/todos/${itemId}/completion`, { completed }),
  remove: (projectId: number, itemId: number) =>
    http.delete<TodoList>(`/projects/${projectId}/todos/${itemId}`),
}
