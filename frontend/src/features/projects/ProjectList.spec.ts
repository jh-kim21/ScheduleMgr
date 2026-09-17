// @vitest-environment happy-dom
import { mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, describe, expect, it } from 'vitest'
import type { CommitPayload } from '../../api/commitApi'
import type { Project } from '../../api/projectApi'
import { enterCommitView, exitCommitView } from '../../stores/commitView'
import ProjectList from './ProjectList.vue'

/**
 * 업무 분야(태그) 마스터는 구성원과 같은 자리에서 관리한다 — 프로젝트 행의 버튼이다. 그래서 새
 * 쓰기 진입점이 하나 늘었고, **커밋 조회 중에는 잠겨야 한다.**
 *
 * 이 파일이 보는 것은 그 잠금이 화면에 실제로 걸려 있는지 하나다. 잠금 판단 자체는 화면마다
 * 짓지 않고 `stores/commitView.ts`의 파생값 `readOnly` 하나를 보므로(CLAUDE.md), 여기서는
 * 이웃한 `구성원` 버튼과 같은 상태가 되는지로 확인한다 — 한쪽만 잠기면 절차가 장식이 된다.
 */
function project(): Project {
  return {
    id: 1,
    name: '웹사이트 개편',
    description: null,
    status: 'IN_PROGRESS',
    startDate: '2026-01-01',
    endDate: '2026-06-30',
  } as Project
}

function render() {
  return mount(ProjectList, {
    props: { projects: [project()] },
    // 내보내기 메뉴는 이 테스트와 무관하고 body 로 teleport 한다.
    global: { stubs: { ExportMenu: true } },
  })
}

function buttonLabelled(wrapper: VueWrapper, label: string) {
  const found = wrapper
    .findAll('button')
    .find((button) => button.text().trim() === label)
  expect(found, `"${label}" 버튼을 찾지 못했습니다`).toBeDefined()
  return found!
}

describe('ProjectList — 분야 버튼', () => {
  let wrapper: VueWrapper | undefined

  afterEach(() => {
    exitCommitView()
    wrapper?.unmount()
    wrapper = undefined
  })

  it('평소에는 눌러서 분야 관리를 연다', async () => {
    wrapper = render()

    await buttonLabelled(wrapper, '분야').trigger('click')

    expect(wrapper.emitted('tags')).toHaveLength(1)
  })

  it('커밋 조회 중에는 구성원 버튼과 나란히 잠긴다', () => {
    enterCommitView(
      {
        projectId: 1,
        id: 42,
        version: 3,
        asOf: '2026-03-01',
        message: null,
        committedBy: null,
        formatVersion: 7,
      },
      {} as CommitPayload,
    )
    wrapper = render()

    const tags = buttonLabelled(wrapper, '분야')
    expect(tags.attributes('disabled')).toBeDefined()
    expect(tags.attributes('title')).toBe('커밋 시점을 보는 동안에는 분야를 바꿀 수 없습니다.')
    expect(buttonLabelled(wrapper, '구성원').attributes('disabled')).toBeDefined()
  })
})
