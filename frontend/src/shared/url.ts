/**
 * 이 값을 `<a href>`에 넣어도 되는가. 이 앱에서 사용자 입력이 href로 들어가는 자리는 WBS
 * 항목의 Action Item 주소 하나뿐이고, `javascript:`·`data:`·`vbscript:`가 들어가면 그 링크를
 * 누르는 것만으로 스크립트가 실행된다.
 *
 * 서버도 같은 규칙으로 막지만 **여기가 마지막 방어선**이다 — 규칙이 생기기 전에 저장된 행,
 * 손으로 편집한 가져오기 파일이 남아 있을 수 있고, 실제로 실행하는 것은 브라우저다.
 * 안전하지 않으면 호출자는 링크 대신 평문으로 그린다.
 */
export function isSafeHttpUrl(value: string | null | undefined): boolean {
  if (!value) return false
  try {
    const protocol = new URL(value).protocol
    return protocol === 'http:' || protocol === 'https:'
  } catch {
    return false
  }
}
