import type { WbsItemInput, WbsNode } from '../../api/wbsApi'

/**
 * Maps a WBS node being edited to the form's initial values — exactly the assignment
 * `WbsForm.vue`'s `watch(props.editing, …)` performs. Pulled out into its own module so the
 * "editing without changing anything must resend every field" contract (결함 3) is a plain
 * function call in a test, rather than something only observable by mounting the component
 * (this repo has no `@vue/test-utils`/DOM environment installed to do that with).
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
  }
}
