// @vitest-environment happy-dom
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../../api/http'
import { progressApi, type Progress } from '../../api/progressApi'
import { selectedProjectId } from '../../stores/projectSelection'
import ProgressPanel from './ProgressPanel.vue'

/**
 * `useProgress()`는 모듈 스코프 공유 상태다(CheckpointList.spec.ts와 같은 이유) — 실제
 * `progressApi`를 그대로 두면 happy-dom엔 fetch가 없어 초기 로드부터 실패한다.
 */
vi.mock('../../api/progressApi', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../api/progressApi')>()
  return {
    ...actual,
    progressApi: {
      ...actual.progressApi,
      get: vi.fn(),
      snapshots: vi.fn(),
      approveBaseline: vi.fn(),
    },
  }
})

const mockedGet = vi.mocked(progressApi.get)
const mockedSnapshots = vi.mocked(progressApi.snapshots)
const mockedApproveBaseline = vi.mocked(progressApi.approveBaseline)

function baseProgress(overrides: Partial<Progress> = {}): Progress {
  return {
    referenceDate: '2026-03-01',
    project: {
      actualPercent: 40,
      basis: 'MANUAL',
      workPackageCount: 0,
      notEstimableCount: 0,
      incomplete: false,
      plannedPercent: null,
      comparablePercent: null,
      variancePoints: null,
      varianceExcludedCount: 0,
      acceptancePending: 0,
    },
    workPackages: [],
    baseline: null,
    scope: {
      hasBaseline: false,
      baselineItemCount: 0,
      currentItemCount: 0,
      added: [],
      removed: [],
      weightChanged: [],
    },
    ...overrides,
  }
}

async function renderPanel() {
  mockedGet.mockResolvedValueOnce(baseProgress())
  mockedSnapshots.mockResolvedValueOnce({ snapshots: [] })
  selectedProjectId.value = 1
  const wrapper = mount(ProgressPanel, { attachTo: document.body })
  await flushPromises()
  return wrapper
}

function openBaselineButton(wrapper: VueWrapper) {
  return wrapper.findAll('button').find((b) => b.text() === '기준선 승인')!
}

function approverInput() {
  return document.body.querySelector<HTMLInputElement>('.panel input[placeholder="예: 김재학"]')!
}

/** teleport된 네이티브 input에는 VTU의 `setValue`가 없으므로 v-model이 반응하는 이벤트를 직접 낸다. */
async function typeInto(input: HTMLInputElement, value: string) {
  input.value = value
  input.dispatchEvent(new Event('input'))
  await flushPromises()
}

function confirmButton() {
  return [...document.body.querySelectorAll<HTMLButtonElement>('.panel button')].find(
    (b) => b.textContent === '승인',
  )!
}

describe('ProgressPanel — 기준선 승인 대화상자', () => {
  let wrapper: VueWrapper | undefined

  afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
    selectedProjectId.value = null
    mockedGet.mockReset()
    mockedSnapshots.mockReset()
    mockedApproveBaseline.mockReset()
  })

  /**
   * CLAUDE.md가 명시적으로 경고하는 결함이다: "화면 본문의 <p class="error">는 대화상자
   * 뒤에 가려지므로, 그것만 있으면 저장이 조용히 실패한 것처럼 보입니다." 손으로 만든
   * `.dialog` 오버레이(z-index: 50)가 화면 본문의 에러 문단을 완전히 덮어, 기준선 승인이
   * 거부돼도 화면에 아무 일도 일어나지 않는 것처럼 보이던 결함을 여기서 고정한다.
   */
  it('승인이 거부되면 대화상자가 열린 채 남고, 그 안에 오류 메시지가 보인다', async () => {
    wrapper = await renderPanel()
    mockedApproveBaseline.mockRejectedValueOnce(new ApiError(400, '이미 승인된 기준선이 있습니다'))

    await openBaselineButton(wrapper).trigger('click')
    await wrapper.vm.$nextTick()
    await typeInto(approverInput(), '김재학')
    confirmButton().click()
    await flushPromises()

    // 대화상자 자체가 아직 열려 있다 — ModalDialog로 옮기기 전에는 이 자리에 아무 것도
    // 없었다(손수 만든 오버레이는 언제나 v-if="baselineOpen" 그대로였으나, 검증 대상은
    // "메시지가 실제로 보이는가"다).
    expect(document.body.querySelector('.panel[role="dialog"]')).not.toBeNull()
    const alert = document.body.querySelector('[role="alert"]')
    expect(alert).not.toBeNull()
    expect(alert?.textContent).toBe('이미 승인된 기준선이 있습니다')
    // 입력값도 보존된다 — 거부됐다고 다시 치게 만들지 않는다(CLAUDE.md 화면 규칙).
    expect(approverInput().value).toBe('김재학')
  })

  it('승인에 성공하면 대화상자가 닫힌다', async () => {
    wrapper = await renderPanel()
    mockedApproveBaseline.mockResolvedValueOnce(baseProgress({ baseline: {
      id: 1,
      version: 1,
      approvedBy: '김재학',
      approvedAt: '2026-03-01T00:00:00',
      note: null,
      itemCount: 0,
    } }))

    await openBaselineButton(wrapper).trigger('click')
    await wrapper.vm.$nextTick()
    await typeInto(approverInput(), '김재학')
    confirmButton().click()
    await flushPromises()

    expect(document.body.querySelector('.panel[role="dialog"]')).toBeNull()
  })
})
