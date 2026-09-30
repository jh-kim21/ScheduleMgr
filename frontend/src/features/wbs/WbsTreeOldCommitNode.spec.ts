// @vitest-environment happy-dom
import { mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, describe, expect, it } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import type { WbsNode } from '../../api/wbsApi'
import WbsTree from './WbsTree.vue'

/**
 * 트리가 **V24 이전에 찍힌 커밋의 `computed_payload`** 를 그릴 때.
 *
 * `WbsTreeDescriptionLink.spec.ts`는 `actionItemUrl`이 `null`인 노드를 덮는다. 여기서 보는
 * 것은 그것과 다른 상태다 — 커밋은 불변이므로 그날 저장된 JSON에는 **키 자체가 없고**, 화면은
 * 그 payload를 라이브 응답과 같은 자리에서 그대로 받는다(`useWbs.ts:70`). `WbsNode.actionItemUrl`이
 * `?: string | null`인 이유가 이 상태이고, 옵셔널을 필수로 좁히면 여기가 타입에서 먼저 막힌다.
 *
 * 노드를 JSON 문자열에서 만든다 — 객체 리터럴로 쓰면 "키를 안 적었다"가 편집 한 번에 조용히
 * 사라지지만, 저장된 payload를 흉내 낸 문자열은 무엇을 재현하는지가 코드에 남는다.
 */
const router = createRouter({
  history: createMemoryHistory(),
  routes: [
    { path: '/', component: { template: '<div />' } },
    { path: '/raci', component: { template: '<div />' } },
    { path: '/backlog', component: { template: '<div />' } },
  ],
})

/** V24 이전 `computed_payload`의 WBS 노드 한 줄 — `actionItemUrl` 키가 없다. */
const PRE_V24_NODE = `{
  "id": 1, "parentId": null, "code": "1", "level": 1,
  "name": "요구사항 정의", "description": "첫 줄\\n둘째 줄",
  "startDate": "2026-03-01", "endDate": "2026-03-31", "progress": 20,
  "summary": false, "nodeType": "WORK_PACKAGE",
  "executionMode": null, "executionModeSummary": null, "backlogSummary": null,
  "weight": null, "agileRatio": null, "acceptanceStatus": null,
  "computedProgress": 20, "progressBasis": "MANUAL", "progressIncomplete": false,
  "progressNote": null, "acceptancePending": false,
  "delayStatus": "ON_TRACK", "expectedProgress": 20, "progressGap": 0, "delayDays": 0,
  "responsible": [], "responsibleInherited": [], "tags": [], "tagSummary": null,
  "children": []
}`

let wrapper: VueWrapper | null = null

afterEach(() => {
  wrapper?.unmount()
  wrapper = null
})

describe('V24 이전 커밋의 WBS 노드', () => {
  it('주소 키가 아예 없어도 링크도 평문도 그리지 않는다 — 키 없음은 "없음"이다', () => {
    const node = JSON.parse(PRE_V24_NODE) as WbsNode
    expect('actionItemUrl' in node).toBe(false)

    wrapper = mount(WbsTree, {
      props: { tree: [node], projectId: 7, readOnly: true },
      global: { plugins: [router] },
    })

    expect(wrapper.find('tr[data-row-id="1"] a.action-link').exists()).toBe(false)
    expect(wrapper.find('tr[data-row-id="1"] .action-link.unsafe').exists()).toBe(false)
  })

  it('같은 노드에서 설명 칩은 살아 있고 창이 열린다 — 커밋 조회에서도 보는 것은 쓰기가 아니다', async () => {
    wrapper = mount(WbsTree, {
      props: { tree: [JSON.parse(PRE_V24_NODE) as WbsNode], projectId: 7, readOnly: true },
      global: { plugins: [router] },
    })

    await wrapper.get('tr[data-row-id="1"] .desc-chip').trigger('click')

    expect(document.body.querySelector('.desc-full')?.textContent).toBe('첫 줄\n둘째 줄')
  })
})
