// @vitest-environment happy-dom
import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import BaselineCard from './BaselineCard.vue'
import GapsCard from './GapsCard.vue'
import KpiStrip from './KpiStrip.vue'
import ProgressCard from './ProgressCard.vue'
import RaciCard from './RaciCard.vue'
import RiskCard from './RiskCard.vue'
import SprintCard from './SprintCard.vue'
import TimelineCard from './TimelineCard.vue'
import VelocityCard from './VelocityCard.vue'
import { EMPTY_DASHBOARD, FULL_DASHBOARD } from './dashboardFixture'

const cards = { KpiStrip, ProgressCard, SprintCard, TimelineCard, RiskCard, BaselineCard, RaciCard, VelocityCard, GapsCard }
const stubs = { RouterLink: { template: '<a><slot /></a>' } }

describe('카드 아홉 장이 두 극단에서 모두 그려진다', () => {
  for (const [name, component] of Object.entries(cards)) {
    it(`${name} — 값이 다 있을 때`, () => {
      const wrapper = mount(component, { props: { data: FULL_DASHBOARD }, global: { stubs } })
      expect(wrapper.text()).not.toContain('NaN')
      expect(wrapper.text()).not.toContain('undefined')
    })
    it(`${name} — 빈 프로젝트`, () => {
      const wrapper = mount(component, { props: { data: EMPTY_DASHBOARD }, global: { stubs } })
      expect(wrapper.text()).not.toContain('NaN')
      expect(wrapper.text()).not.toContain('undefined')
    })
  }
})
