import type { WbsItemInput, WbsNode } from '../../api/wbsApi'

/**
 * Maps a WBS node being edited to the form's initial values — exactly the assignment
 * `WbsForm.vue`'s `watch(props.editing, …)` performs. Pulled out into its own module so the
 * "editing without changing anything must resend every field" contract (결함 3) is a plain
 * function call in a test rather than a mounted-component assertion — the contract is about which
 * fields land in the form, not how the form renders, so a pure function pins it faster and more
 * precisely than mounting `WbsForm.vue` would (CLAUDE.md "컴포넌트 테스트").
 *
 * <p>`actualStartDate`/`actualEndDate`/`forecastEndDate` fall back to `null` when the node does not
 * carry them — today that is every node, because the WBS tree endpoint (`WbsNodeResponse`) does not
 * return them yet (only the Gantt endpoint does). Once it does, this same mapping picks them up
 * without further changes here.
 */
export function nodeToFormInput(item: WbsNode): WbsItemInput {
  return {
    name: item.name,
    description: item.description ?? '',
    startDate: item.startDate,
    endDate: item.endDate,
    progress: item.progress,
    weight: item.weight,
    agileRatio: item.agileRatio,
    acceptanceStatus: item.acceptanceStatus,
    nodeType: item.nodeType,
    // 보관된 값도 그대로 담아 되돌려 보낸다. Summary에서 값을 비워 보내면 서버가
    // "실행 방식을 바꾸려 한다"고 보고 거부한다.
    executionMode: item.executionMode,
    actualStartDate: item.actualStartDate ?? null,
    actualEndDate: item.actualEndDate ?? null,
    forecastEndDate: item.forecastEndDate ?? null,
    // 항상 배열이다 — `null`은 "변경 없음"이라 폼이 쓸 값이 아니다. Summary가 보관 중인 태그도
    // 그대로 담아 되돌려 보낸다: 서버는 Summary의 태그를 *바꾸는* 것을 거부하므로, 비워 보내면
    // 아무것도 안 고친 저장이 거부된다(실행 방식과 같은 함정).
    tagIds: (item.tags ?? []).map((tag) => tag.id),
  }
}
