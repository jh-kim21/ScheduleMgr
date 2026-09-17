// @vitest-environment happy-dom
import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import BarList from './BarList.vue'
import KpiCard from './KpiCard.vue'
import MiniBar from './MiniBar.vue'

/**
 * 산정 전(`null`)과 0% 를 가르는 규칙이 여기 세 컴포넌트에만 있다 — 카드들이 직접 `?? 0` 을
 * 쓰지 못하게 모아 둔 자리라서, 이 구분이 깨지면 대시보드 전체가 한꺼번에 거짓말을 한다.
 */
describe('KpiCard', () => {
  it('산정 전에는 큰 숫자 대신 문구를 쓰고 막대를 그리지 않는다', () => {
    const wrapper = mount(KpiCard, {
      props: { label: '전체 진척', value: null, unit: '%', percent: null, icon: 'progress' },
    })
    expect(wrapper.text()).toContain('산정 전')
    expect(wrapper.text()).not.toContain('0%')
    expect(wrapper.findComponent(MiniBar).exists()).toBe(false)
  })

  it('0 은 0 으로 그린다 — 산정 전과 같은 자리에 오지 않는다', () => {
    const wrapper = mount(KpiCard, {
      props: { label: '지연 업무', value: 0, icon: 'delay' },
    })
    expect(wrapper.text()).toContain('0')
    expect(wrapper.text()).not.toContain('산정 전')
  })

  it('편차의 산정 전은 "미산정"이다 — 진척의 "산정 전"과 말이 다르다', () => {
    const wrapper = mount(KpiCard, {
      props: { label: '계획 대비', value: null, unit: '%p', signed: true, unsetText: '미산정', icon: 'variance' },
    })
    expect(wrapper.text()).toContain('미산정')
    expect(wrapper.text()).not.toContain('0')
  })

  it('편차 0 은 ±0 — "모른다"가 아니라 "계획과 같다"이다', () => {
    const wrapper = mount(KpiCard, {
      props: { label: '계획 대비', value: 0.04, unit: '%p', signed: true, icon: 'variance' },
    })
    expect(wrapper.text()).toContain('±0')
    expect(wrapper.find('.value').classes()).toContain('fg-gray')
  })

  it('편차는 부호를 유지하고, 방향에 따라 색 클래스를 바꾼다', () => {
    const ahead = mount(KpiCard, {
      props: { label: '계획 대비', value: 3.24, unit: '%p', signed: true, icon: 'variance' },
    })
    expect(ahead.text()).toContain('+3.2')
    expect(ahead.find('.value').classes()).toContain('fg-green')

    const behind = mount(KpiCard, {
      props: { label: '계획 대비', value: -1.5, unit: '%p', signed: true, icon: 'variance' },
    })
    expect(behind.text()).toContain('-1.5')
    expect(behind.find('.value').classes()).toContain('fg-red')
  })
})

describe('MiniBar', () => {
  it('산정 전이면 채움을 그리지 않는다', () => {
    const wrapper = mount(MiniBar, { props: { percent: null } })
    expect(wrapper.find('.fill').exists()).toBe(false)
    expect(wrapper.text()).toContain('산정 전')
  })

  it('100 을 넘는 값도 트랙 밖으로 나가지 않는다', () => {
    const wrapper = mount(MiniBar, { props: { percent: 140 } })
    expect(wrapper.find('.fill').attributes('style')).toContain('100%')
  })
})

describe('BarList', () => {
  it('항목이 없으면 길이 0 인 막대 대신 문구를 쓴다', () => {
    const wrapper = mount(BarList, { props: { items: [], emptyText: '아직 없습니다.' } })
    expect(wrapper.text()).toBe('아직 없습니다.')
    expect(wrapper.findComponent(MiniBar).exists()).toBe(false)
  })

  it('분모가 0 이면 비율을 만들지 않는다 (NaN 이 막대 폭으로 새지 않게)', () => {
    const wrapper = mount(BarList, {
      props: { items: [{ key: 'a', label: '미지정', value: 0, total: 0 }] },
    })
    expect(wrapper.text()).toContain('0 / 0')
    expect(wrapper.text()).not.toContain('NaN')
    expect(wrapper.find('.fill').exists()).toBe(false)
  })
})
