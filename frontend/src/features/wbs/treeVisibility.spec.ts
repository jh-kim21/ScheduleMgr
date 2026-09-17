import { describe, expect, it } from 'vitest'
import { showsLoadingInsteadOfTree } from './treeVisibility'

/**
 * `treeVisibility.ts`는 `WbsView.vue`가 "불러오는 중" 문구와 `WbsTree`를 어떻게 나눌지 정하는
 * 순수 함수다. `WbsView`는 라우터와 컴포저블 네 개를 mock해야 마운트되는 화면이라 뷰 레벨
 * 테스트가 없다(docs/tasks/checkpoint-ux-fix.md §3-2) — 그래서 이 규칙을 되돌리는 회귀를
 * 잡아낼 수 있는 것은 이 spec뿐이다.
 */
describe('showsLoadingInsteadOfTree', () => {
  it('첫 로드(행 0개 + 로딩 중)에는 트리 대신 로딩 문구를 보여준다', () => {
    expect(showsLoadingInsteadOfTree(true, 0)).toBe(true)
  })

  it('행이 이미 있으면 갱신 중이어도 트리를 계속 보여준다 — 언마운트하면 펼친 체크포인트와 폼이 함께 사라진다', () => {
    expect(showsLoadingInsteadOfTree(true, 5)).toBe(false)
  })

  it('로딩이 끝났고 행도 없으면(빈 프로젝트) 트리를 보여준다 — 그 자리에서 "등록된 WBS 항목이 없습니다"를 그린다', () => {
    expect(showsLoadingInsteadOfTree(false, 0)).toBe(false)
  })

  it('로딩이 끝났고 행이 있으면 당연히 트리를 보여준다', () => {
    expect(showsLoadingInsteadOfTree(false, 5)).toBe(false)
  })
})
