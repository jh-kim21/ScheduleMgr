// @vitest-environment happy-dom
import { mount } from '@vue/test-utils'
import { afterEach, describe, expect, it } from 'vitest'
import WorkloadPanel from './WorkloadPanel.vue'
import { EMPTY_DASHBOARD, FULL_DASHBOARD } from './dashboardFixture'

const stubs = { RouterLink: { template: '<a :href="to"><slot /></a>', props: ['to'] } }

describe('WorkloadPanel', () => {
  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('구성원이 없으면 표 대신 안내문을 낸다', () => {
    const wrapper = mount(WorkloadPanel, { props: { data: EMPTY_DASHBOARD }, global: { stubs } })
    expect(wrapper.text()).toContain('구성원이 없습니다')
    expect(wrapper.find('table').exists()).toBe(false)
  })

  it('전원을 그린다 — 요약 카드가 자르는 5명 초과분도 포함한다', () => {
    const wrapper = mount(WorkloadPanel, { props: { data: FULL_DASHBOARD }, global: { stubs } })
    const rows = wrapper.findAll('tbody tr')
    expect(rows).toHaveLength(FULL_DASHBOARD.workload.members.length)
    expect(wrapper.text()).toContain('한지호') // 6번째, 카드에서는 잘리는 사람
  })

  it('포인트가 없는 사람은 "-"이고, 0인 사람은 "0"이다 — null과 0을 구분한다', () => {
    const wrapper = mount(WorkloadPanel, { props: { data: FULL_DASHBOARD }, global: { stubs } })
    const rows = wrapper.findAll('tbody tr')
    // 박도윤: storyPoints null, 최하은: storyPoints 0
    const dowoon = rows.find((r) => r.text().includes('박도윤'))
    const haeun = rows.find((r) => r.text().includes('최하은'))
    expect(dowoon?.findAll('td')[5].text()).toBe('-')
    expect(haeun?.findAll('td')[5].text()).toBe('0')
  })

  it('storyPoints가 undefined로 와도(NON_NULL 직렬화 가정) "-"로 그린다 — ?? 는 null과 undefined를 같이 받는다', () => {
    const data = {
      ...FULL_DASHBOARD,
      workload: {
        unassignedActiveCount: 0,
        members: [
          { memberId: 1, memberName: '가', activeCount: 1, delayedCount: 0, atRiskCount: 0, openStoryCount: 0, storyPoints: undefined as unknown as null, openRaidCount: 0 },
        ],
      },
    }
    const wrapper = mount(WorkloadPanel, { props: { data }, global: { stubs } })
    expect(wrapper.find('tbody tr td:nth-child(6)').text()).toBe('-')
  })

  it('항상 "이 프로젝트 안에서의 비교" 문구가 있다', () => {
    const wrapper = mount(WorkloadPanel, { props: { data: FULL_DASHBOARD }, global: { stubs } })
    expect(wrapper.text()).toContain('이 프로젝트 안에서의 비교입니다')
  })

  it('미배정 진행 업무 각주는 0이 아닐 때만 뜬다', () => {
    const shown = mount(WorkloadPanel, { props: { data: FULL_DASHBOARD }, global: { stubs } })
    expect(shown.text()).toContain('담당자 미지정 진행 업무 2건')

    const hidden = mount(WorkloadPanel, {
      props: { data: { ...FULL_DASHBOARD, workload: { ...FULL_DASHBOARD.workload, unassignedActiveCount: 0 } } },
      global: { stubs },
    })
    expect(hidden.text()).not.toContain('담당자 미지정')
  })

  it('링크는 화면까지만 데려간다는 것을 문구로 밝힌다', () => {
    const wrapper = mount(WorkloadPanel, { props: { data: FULL_DASHBOARD }, global: { stubs } })
    expect(wrapper.text()).toContain('담당자로 필터된 화면이 아직 없어')
  })

  it('동시 진행은 /raci, 미완료 Story는 /backlog, 열린 RAID는 /raid로 링크한다', () => {
    const wrapper = mount(WorkloadPanel, { props: { data: FULL_DASHBOARD }, global: { stubs } })
    const firstRow = wrapper.findAll('tbody tr')[0]
    const hrefs = firstRow.findAll('a').map((a) => a.attributes('href'))
    expect(hrefs).toEqual(['/raci', '/backlog', '/raid'])
  })

  it('열 머리글을 누르면 그 값으로 정렬하고, 다시 누르면 방향이 뒤집힌다', async () => {
    const wrapper = mount(WorkloadPanel, { props: { data: FULL_DASHBOARD }, global: { stubs } })
    const pointsHeader = wrapper.findAll('th button').find((b) => b.text().startsWith('포인트'))!
    await pointsHeader.trigger('click')

    // 내림차순: 13(김민준), 5(이서연), 0(최하은), null 둘(박도윤·정우진·한지호)은 항상 뒤.
    let names = wrapper.findAll('tbody tr').map((r) => r.findAll('td')[0].text())
    expect(names.slice(0, 3)).toEqual(['김민준', '이서연', '최하은'])

    await pointsHeader.trigger('click')
    names = wrapper.findAll('tbody tr').map((r) => r.findAll('td')[0].text())
    // 오름차순이어도 null은 여전히 뒤로 간다.
    expect(names.slice(-3).sort()).toEqual(['박도윤', '정우진', '한지호'].sort())
    expect(names[0]).toBe('최하은') // 0이 가장 작은 숫자값
  })
})
