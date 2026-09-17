import { http } from './http'

/**
 * 업무 분야 태그의 마스터 레코드. 프로젝트 스코프이고 이름은 프로젝트 안에서 유일하다
 * (`project_members`와 같은 패턴) — 자유 문자열이면 `Web`/`web`/`WEB`이 섞여 분류가 깨진다.
 */
export interface WbsTag {
  id: number
  name: string
  /** 팔레트 슬롯 이름. 비어 있으면 화면이 태그 이름 해시로 고른다 (`tagColor.ts`). */
  color: string | null
  sortOrder: number
}

export interface WbsTagInput {
  name: string
  color: string | null
  sortOrder: number | null
}

/**
 * 모든 변경이 갱신된 **전체 목록**을 돌려준다 — RACI·RAID와 같은 태도다. 부분 응답이면 정렬
 * 순서가 바뀐 뒤를 클라이언트가 다시 조립해야 하고, 조립 규칙이 서버와 두 벌이 된다.
 *
 * 경로가 `/wbs/tags`라 `/wbs/{itemId}`와 한 이름공간을 쓴다. 항목에 태그를 붙이는 것은 여기가
 * 아니라 WBS 수정 API의 `tagIds`로 한다 — 별도 엔드포인트를 두면 폼 저장이 두 번 나간다.
 */
export const wbsTagApi = {
  list: (projectId: number) => http.get<WbsTag[]>(`/projects/${projectId}/wbs/tags`),
  create: (projectId: number, input: WbsTagInput) =>
    http.post<WbsTag[]>(`/projects/${projectId}/wbs/tags`, input),
  update: (projectId: number, tagId: number, input: WbsTagInput) =>
    http.put<WbsTag[]>(`/projects/${projectId}/wbs/tags/${tagId}`, input),
  remove: (projectId: number, tagId: number) =>
    http.delete<WbsTag[]>(`/projects/${projectId}/wbs/tags/${tagId}`),
}
