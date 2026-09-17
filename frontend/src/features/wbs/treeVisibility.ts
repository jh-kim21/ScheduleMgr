/**
 * Whether `WbsView` should show "불러오는 중..." instead of `WbsTree`.
 *
 * 첫 로드에만 트리를 감춘다 — 갱신 중에 감추면 펼친 체크포인트와 그 폼이 함께 사라진다. 체크포인트
 * 추가·수정·승인·삭제는 `markWbsChanged()`를 부르고, `WbsView`의 `watch(progressData, …)`가 그
 * 트리를 다시 읽는다(승인 후 트리 행의 진행률·「승인 N/M」 배지를 낡지 않게 하려는 것, CLAUDE.md
 * "화면 레이아웃 규칙"). 재요청이 도는 동안 `loading`이 다시 `true`가 되는데, 그때 `<WbsTree>`를
 * 통째로 언마운트하면 트리 안에 펼쳐 둔 체크포인트 목록·입력 폼·포커스가 함께 사라진다 — 저장할
 * 때마다 창이 닫히는 것으로 보였던 버그의 원인이다.
 *
 * `rowCount`가 이미 0보다 크면(=이전에 한 번이라도 트리를 그렸으면) 갱신 중에도 트리를 계속
 * 보여준다. 반면 첫 로드(`rowCount === 0`)에는 여전히 "불러오는 중"을 보여줘야 한다 — 그러지
 * 않으면 `WbsTree`의 빈 화면 문구("등록된 WBS 항목이 없습니다")가 먼저 번쩍인다. "항목이 없다"와
 * "아직 응답이 안 왔다"는 다른 말이다.
 */
export function showsLoadingInsteadOfTree(loading: boolean, rowCount: number): boolean {
  return loading && rowCount === 0
}
