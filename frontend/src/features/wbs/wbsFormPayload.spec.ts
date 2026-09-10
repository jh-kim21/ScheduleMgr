import { describe, it } from 'vitest'

/**
 * 이 파일이 고정하려던 계약(설계서/발견한_결함.md WBS §2·§3, CLAUDE.md "WBS 설계상 알아둘 점")은
 * Developer2가 다음과 같이 해소했다. 원래의 `it.todo` 목록은 아래로 대체됐다 — 지워서 "확인되지
 * 않은 채 넘어감"으로 보이게 하지 않기 위해 무엇이 어디로 옮겨졌는지 남긴다.
 *
 *   1~3. 실적/예상 종료일이 편집 없는 저장에서 사라지는 문제:
 *      `WbsItemInput`(frontend/src/api/wbsApi.ts)에 `actualStartDate`/`actualEndDate`/
 *      `forecastEndDate`를 추가하고, `WbsForm.vue`가 편집 대상을 폼에 담는 로직을
 *      `wbsFormMapping.ts`의 `nodeToFormInput`으로 뽑아냈다. 이 저장소에는
 *      `@vue/test-utils`도 DOM 환경(jsdom/happy-dom)도 없어 컴포넌트를 마운트해 제출
 *      payload를 재현할 수 없다는 사정은 여전하지만, `nodeToFormInput`이 정확히
 *      `watch(props.editing, …)`이 하는 대입이라 — 즉 `onSubmit`이 그 값을 손대지 않고
 *      그대로 `emit('submit', {...form})`한다 — 순수 함수 호출만으로 같은 계약을 검증할 수
 *      있다. 실제 단정문은 `wbsFormMapping.spec.ts`에 있다.
 *
 *   4. Summary(집계된) 항목의 startDate/endDate/progress가 자기 값으로 저장되는 문제:
 *      화면 쪽(폼 비활성화)이 아니라 서버 쪽에서 막기로 했다 — `WbsService.updateItem`이
 *      하위가 있는 항목이면 요청에 무엇이 오든 무시하고 저장된 값을 유지한다
 *      (backend/src/main/java/com/projectflow/application/WbsService.java 결함 2 주석 참고).
 *      화면이 입력칸을 비활성화해도 폼 상태에는 여전히 롤업값이 남아 제출되므로, 서버가
 *      최종 방어선이어야 우회 경로(다른 클라이언트, 재현되지 않은 버그)에도 안전하다.
 *      이 결정은 이 파일이 원래 상정했던 "폼이 그 값을 보내지 않는다"는 방향과 다르다 —
 *      그래서 프론트에는 이에 대응하는 단정문이 없고, 실제 검증은
 *      backend/src/test/java/com/projectflow/application/WbsServiceTest.java의
 *      "Summary 저장 시 집계값 보호 — 결함 2" 쪽에 있다.
 */
describe('WbsForm — 제출 payload', () => {
  it('실적/예상 종료일 보존은 wbsFormMapping.spec.ts로 옮겼다', () => {})
  it('Summary 집계값 보호는 서버 쪽으로 옮겼다 — WbsServiceTest.java 참고', () => {})
})
