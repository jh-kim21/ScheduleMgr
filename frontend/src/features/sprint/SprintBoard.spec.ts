// @vitest-environment happy-dom
import { mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, describe, expect, it } from 'vitest'
import type { Sprint, SprintItem } from '../../api/sprintApi'
import SprintBoard from './SprintBoard.vue'

/**
 * 칸반 카드 이동의 키보드·비-마우스 대안(WCAG 2.5.7·2.1.1) — 이동 `<select>`와 Alt+←/→가
 * 기존 드래그 경로(`emit('move', …)`)를 그대로 타는지, `frozen`(종료된 Sprint·읽기 전용)일 때
 * 잠기는지를 고정한다. 판정 자체(`moveTargets`/`adjacentStatus`)는 `boardMove.spec.ts`가
 * 순수 함수로 덮는다.
 */
function item(overrides: Partial<SprintItem> = {}): SprintItem {
  return {
    assignmentId: 1,
    backlogItemId: 10,
    itemType: 'STORY',
    title: '로그인 화면 구현',
    priority: 'MEDIUM',
    status: 'TODO',
    blocked: false,
    blockedReason: null,
    assigneeMemberId: null,
    assigneeName: null,
    acceptanceCriteria: null,
    storyPoint: 3,
    wbsItemId: null,
    wbsCode: null,
    wbsName: null,
    pointsAtStart: null,
    pointsAtClose: null,
    outcome: null,
    removed: false,
    openChildCount: 0,
    reopened: false,
    doneAt: null,
    ...overrides,
  }
}

function sprint(items: SprintItem[], overrides: Partial<Sprint> = {}): Sprint {
  return {
    id: 1,
    name: 'Sprint 1',
    goal: null,
    startDate: '2026-03-01',
    endDate: '2026-03-14',
    status: 'ACTIVE',
    closedAt: null,
    plannedItems: items.length,
    plannedPoints: 0,
    doneItems: 0,
    donePoints: 0,
    blockedItems: 0,
    carriedOverItems: 0,
    canStart: false,
    canDelete: false,
    items,
    ...overrides,
  }
}

function render(
  items: SprintItem[],
  props: Record<string, unknown> = {},
  sprintOverrides: Partial<Sprint> = {},
) {
  return mount(SprintBoard, {
    props: { sprint: sprint(items, sprintOverrides), raidItems: [], ...props },
  })
}

describe('SprintBoard — 카드 이동(드래그 대안)', () => {
  let wrapper: VueWrapper | undefined

  afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
  })

  it('카드는 Tab으로 포커스할 수 있다', () => {
    wrapper = render([item()])

    const card = wrapper.get('article.card')
    expect(card.attributes('tabindex')).toBe('0')
  })

  it('이동 select를 고르면 그 칸으로 move가 emit된다', async () => {
    wrapper = render([item({ status: 'TODO' })])

    const select = wrapper.get('.move-select select')
    await select.setValue('IN_PROGRESS')

    expect(wrapper.emitted('move')?.[0]).toEqual([10, { status: 'IN_PROGRESS' }])
  })

  it('완료로 옮기면 드래그와 같은 확인 대화상자를 거친다 — select만으로는 끝나지 않는다', async () => {
    wrapper = render([item({ status: 'REVIEW' })])

    const select = wrapper.get('.move-select select')
    await select.setValue('DONE')

    expect(wrapper.emitted('move')).toBeUndefined()
    expect(document.body.querySelector('.subject')?.textContent).toBe('로그인 화면 구현')

    const confirmButton = Array.from(document.body.querySelectorAll('button')).find(
      (button) => button.textContent === '확인하고 완료',
    )
    await confirmButton!.click()
    await wrapper.vm.$nextTick()

    expect(wrapper.emitted('move')?.[0]).toEqual([
      10,
      { status: 'DONE', acceptanceConfirmed: true },
    ])
  })

  it('Alt+ArrowRight 는 다음 칸으로 move를 emit한다', async () => {
    wrapper = render([item({ status: 'TODO' })])

    await wrapper.get('article.card').trigger('keydown', { key: 'ArrowRight', altKey: true })

    expect(wrapper.emitted('move')?.[0]).toEqual([10, { status: 'IN_PROGRESS' }])
  })

  it('맨 끝 칸에서 Alt+ArrowRight 는 아무 일도 하지 않는다', async () => {
    wrapper = render([item({ status: 'DONE' })])

    await wrapper.get('article.card').trigger('keydown', { key: 'ArrowRight', altKey: true })

    expect(wrapper.emitted('move')).toBeUndefined()
  })

  it('Alt 없는 화살표는 이동을 emit하지 않는다', async () => {
    wrapper = render([item({ status: 'TODO' })])

    await wrapper.get('article.card').trigger('keydown', { key: 'ArrowRight' })

    expect(wrapper.emitted('move')).toBeUndefined()
  })

  it('종료된 Sprint(frozen)에서는 이동 select도 Alt+화살표도 없다', async () => {
    wrapper = render([item({ status: 'TODO' })], {}, { status: 'CLOSED' })

    expect(wrapper.find('.move-select').exists()).toBe(false)

    await wrapper.get('article.card').trigger('keydown', { key: 'ArrowRight', altKey: true })
    expect(wrapper.emitted('move')).toBeUndefined()
  })

  it('readOnly(커밋 조회 중)에서도 이동 select가 없다', () => {
    wrapper = render([item({ status: 'TODO' })], { readOnly: true })

    expect(wrapper.find('.move-select').exists()).toBe(false)
  })

  it('카드를 옮기면 숨김 영역에 결과를 알린다', async () => {
    wrapper = render([item({ status: 'TODO' })])

    await wrapper.get('article.card').trigger('keydown', { key: 'ArrowRight', altKey: true })

    expect(wrapper.get('[role="status"]').text()).toContain('로그인 화면 구현')
    expect(wrapper.get('[role="status"]').text()).toContain('진행 중')
  })
})
