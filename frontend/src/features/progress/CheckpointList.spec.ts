// @vitest-environment happy-dom
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { CheckpointDetail } from '../../api/progressApi'
import { progressApi } from '../../api/progressApi'
import CheckpointList from './CheckpointList.vue'

/**
 * `CheckpointList`는 `Teleport`를 쓰지 않으므로(ModalDialog와 달리 트리·진척 탭의 한 행 자리에
 * 직접 그려진다) `wrapper.find(...)`가 실제 내용을 그대로 찾는다 — `document.body`를 거칠 필요가
 * 없다. 그래도 컴포넌트가 언마운트되지 않고 남으면 다음 테스트가 이전 인스턴스의 리스너를 함께
 * 건드릴 수 있으므로 `afterEach`에서 정리한다(CLAUDE.md 컴포넌트 테스트 절의 관례를 그대로 따름).
 *
 * `progressApi.addCheckpoint`/`updateCheckpoint`를 목(mock)한다 — `useProgress()`가 모듈 스코프
 * 공유 상태라 실제 함수를 그대로 두면 happy-dom엔 fetch가 없어 저장 버튼을 누르는 테스트가 전부
 * 실패한다. 렌더링·토글만 보는 테스트는 애초에 이 버튼들을 누르지 않으므로 목이 없어도 통과하지만,
 * 파일 전체에 걸어 두면 앞으로 추가될 클릭 테스트도 안전하다.
 */
vi.mock('../../api/progressApi', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../api/progressApi')>()
  return {
    ...actual,
    progressApi: {
      ...actual.progressApi,
      addCheckpoint: vi.fn(),
      updateCheckpoint: vi.fn(),
      setApproval: vi.fn(),
    },
  }
})

const mockedAddCheckpoint = vi.mocked(progressApi.addCheckpoint)
const mockedUpdateCheckpoint = vi.mocked(progressApi.updateCheckpoint)
const mockedSetApproval = vi.mocked(progressApi.setApproval)

function checkpoint(overrides: Partial<CheckpointDetail> = {}): CheckpointDetail {
  return {
    id: 1,
    title: '설계 리뷰',
    weight: 2,
    completionCriteria: '리뷰어 승인',
    approved: false,
    approvedBy: null,
    approvedAt: null,
    ...overrides,
  }
}

/**
 * `attachTo: document.body`가 필요하다 — 기본 마운트는 컴포넌트를 문서에 붙이지 않아
 * `focus()`가 `document.activeElement`를 바꾸지 않는다. 이 화면은 "추가 후 제목 칸으로 포커스가
 * 돌아온다"·"승인자 칸이 전체 선택된 채 열린다"가 곧 기능이라 포커스를 검증할 수 있어야 한다.
 * 문서에 붙인 만큼 `afterEach`의 `unmount()`가 더 중요해진다(안 하면 다음 테스트가 이전
 * 인스턴스의 입력칸까지 함께 찾는다).
 */
function renderList(checkpoints: CheckpointDetail[], editable: boolean) {
  return mount(CheckpointList, {
    props: { projectId: 1, wbsItemId: 10, checkpoints, editable },
    attachTo: document.body,
  })
}

function addButton(wrapper: VueWrapper) {
  return wrapper.findAll('button').find((b) => b.text() === '＋ 체크포인트 추가')
}

function editButton(wrapper: VueWrapper) {
  return wrapper.findAll('button').find((b) => b.text() === '수정')
}

function formSubmitButton(wrapper: VueWrapper) {
  return wrapper.find('.cp-form button:not(.ghost)')
}

function formCancelButton(wrapper: VueWrapper) {
  return wrapper.find('.cp-form button.ghost')
}

function titleInput(wrapper: VueWrapper) {
  return wrapper.find<HTMLInputElement>('.cp-form input[type="text"]')
}

function weightInput(wrapper: VueWrapper) {
  return wrapper.find<HTMLInputElement>('.cp-form input[type="number"]')
}

function criteriaInput(wrapper: VueWrapper) {
  return wrapper.find<HTMLInputElement>('.cp-criteria-input')
}

function approveButtons(wrapper: VueWrapper) {
  return wrapper.findAll('button').filter((b) => b.text() === '승인')
}

function approverInput(wrapper: VueWrapper) {
  return wrapper.find<HTMLInputElement>('.approve-row input')
}

function approveConfirmButton(wrapper: VueWrapper) {
  return wrapper.find('.approve-row button:not(.ghost)')
}

describe('CheckpointList', () => {
  let wrapper: VueWrapper | undefined

  afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
    mockedAddCheckpoint.mockReset()
    mockedUpdateCheckpoint.mockReset()
    mockedSetApproval.mockReset()
  })

  describe('editable: false (진척 탭 — 조망 전용)', () => {
    it('버튼이 하나도 렌더링되지 않는다 — ＋ 체크포인트 추가·승인·승인취소가 한때 editable 밖에 있어 조망 화면에서도 조작 가능했던 결함이 실제로 있었다. 소스 리뷰만으로는 다음번에 또 놓칠 수 있어 여기서 고정한다', () => {
      wrapper = renderList(
        [checkpoint({ approved: false }), checkpoint({ id: 2, approved: true, approvedBy: '김재학', approvedAt: '2026-03-02' })],
        false,
      )

      expect(wrapper.findAll('button')).toHaveLength(0)
    })

    it('추가/수정 폼(.cp-form)이 렌더링되지 않는다', () => {
      wrapper = renderList([checkpoint()], false)

      expect(wrapper.find('.cp-form').exists()).toBe(false)
    })

    it('그래도 정보는 보인다 — 버튼을 없앤 것이지 내용을 없앤 것이 아니다: 제목·가중치·완료조건', () => {
      wrapper = renderList([checkpoint({ title: '설계 리뷰', weight: 2, completionCriteria: '리뷰어 승인' })], false)

      expect(wrapper.text()).toContain('설계 리뷰')
      expect(wrapper.text()).toContain('가중치 2')
      expect(wrapper.text()).toContain('리뷰어 승인')
    })

    it('미승인 항목은 "미승인"으로 표시된다', () => {
      wrapper = renderList([checkpoint({ approved: false })], false)

      expect(wrapper.find('.cp-pending').text()).toBe('미승인')
    })

    it('승인된 항목은 승인자·승인일을 함께 보여준다', () => {
      wrapper = renderList(
        [checkpoint({ approved: true, approvedBy: '김재학', approvedAt: '2026-03-02T00:00:00' })],
        false,
      )

      const approved = wrapper.find('.cp-approved').text()
      expect(approved).toContain('김재학')
      expect(approved).toContain('2026-03-02')
    })

    it('체크포인트가 없으면 WBS 화면으로 안내하는 문구를 보여준다(진척 탭 전용 문구)', () => {
      wrapper = renderList([], false)

      expect(wrapper.text()).toContain('WBS 화면에서 해당 업무를 펼쳐 등록하세요')
    })
  })

  describe('editable: true — 폼 토글', () => {
    it('기본으로는 ＋ 체크포인트 추가 버튼만 보이고 폼은 닫혀 있다 — 트리 안에 인라인으로 들어가므로 항상 펼쳐 두면 거슬린다', () => {
      wrapper = renderList([checkpoint()], true)

      expect(addButton(wrapper)).toBeTruthy()
      expect(wrapper.find('.cp-form').exists()).toBe(false)
    })

    it('＋ 체크포인트 추가를 누르면 빈 폼이 열리고, 추가 버튼 자체는 사라진다(교체됨)', async () => {
      wrapper = renderList([checkpoint()], true)

      await addButton(wrapper)!.trigger('click')

      expect(wrapper.find('.cp-form').exists()).toBe(true)
      expect(titleInput(wrapper).element.value).toBe('')
      expect(weightInput(wrapper).element.value).toBe('')
      expect(formSubmitButton(wrapper).text()).toBe('추가')
      expect(addButton(wrapper)).toBeUndefined()
    })

    it('수정 버튼을 누르면 기존 값이 폼에 채워진다 — 이 변경에서 가장 깨지기 쉬운 자리다: 폼을 감추는 쪽만 신경 쓰면 수정 버튼이 폼을 못 열어 수정 기능이 통째로 죽는다', async () => {
      wrapper = renderList([checkpoint({ title: '설계 리뷰', weight: 3, completionCriteria: '리뷰어 승인' })], true)

      await editButton(wrapper)!.trigger('click')

      expect(wrapper.find('.cp-form').exists()).toBe(true)
      expect(titleInput(wrapper).element.value).toBe('설계 리뷰')
      expect(weightInput(wrapper).element.value).toBe('3')
      expect(formSubmitButton(wrapper).text()).toBe('저장')
    })

    it('추가 폼에서 취소를 누르면 폼이 완전히 사라지고 ＋ 버튼이 돌아온다 — 빈 값으로 남는 게 아니라 폼 자체가 닫힌다', async () => {
      wrapper = renderList([checkpoint()], true)

      await addButton(wrapper)!.trigger('click')
      await formCancelButton(wrapper).trigger('click')

      expect(wrapper.find('.cp-form').exists()).toBe(false)
      expect(addButton(wrapper)).toBeTruthy()
    })

    it('수정 모드에서 취소를 누르면 폼이 완전히 사라지고 ＋ 버튼이 돌아온다', async () => {
      wrapper = renderList([checkpoint({ title: '설계 리뷰', weight: 3 })], true)

      await editButton(wrapper)!.trigger('click')
      await formCancelButton(wrapper).trigger('click')

      expect(wrapper.find('.cp-form').exists()).toBe(false)
      expect(addButton(wrapper)).toBeTruthy()
    })

    it('미승인 항목에는 [승인] 버튼이, 승인된 항목에는 [승인 취소] 버튼이 있다 — editable:false의 대조군', () => {
      wrapper = renderList(
        [checkpoint({ id: 1, approved: false }), checkpoint({ id: 2, approved: true, approvedBy: '김재학', approvedAt: '2026-03-02' })],
        true,
      )

      const labels = wrapper.findAll('button').map((b) => b.text())
      expect(labels).toContain('승인')
      expect(labels).toContain('승인 취소')
    })

    it('수정·삭제 버튼이 렌더링된다', () => {
      wrapper = renderList([checkpoint()], true)

      const labels = wrapper.findAll('button').map((b) => b.text())
      expect(labels).toContain('수정')
      expect(labels).toContain('삭제')
    })

    it('가중치 null은 1로, 0은 0 그대로 보여준다 — 서버가 미입력을 1로 폴백하므로 "균등"은 형제 중 하나라도 값이 있으면 거짓이었다. 0과 미입력은 여전히 다른 값이다', () => {
      wrapper = renderList(
        [checkpoint({ id: 1, weight: null }), checkpoint({ id: 2, weight: 0 })],
        true,
      )

      const weights = wrapper.findAll('.cp-weight').map((el) => el.text())
      expect(weights).toContain('가중치 1')
      expect(weights).toContain('가중치 0')
      expect(weights.join(' ')).not.toContain('균등')
    })
  })

  /**
   * 저장 결과에 따라 폼이 하는 일이 세 갈래로 갈린다 — 한 덩어리로 묶으면 다음에 하나만 바뀔 때
   * 어느 규칙이 깨졌는지 안 보이므로 따로 고정한다.
   */
  describe('editable: true — 저장 결과', () => {
    it('추가 성공 — 폼은 열린 채 입력값만 비워진다(추가 모드 유지) — 여러 건을 연달아 등록하는 것이 이 화면의 흔한 사용이라 매번 ＋ 버튼을 다시 누르게 하면 안 된다', async () => {
      mockedAddCheckpoint.mockResolvedValueOnce({} as never)
      wrapper = renderList([checkpoint()], true)

      await addButton(wrapper)!.trigger('click')
      await titleInput(wrapper).setValue('코드 리뷰')
      await weightInput(wrapper).setValue(5)
      await formSubmitButton(wrapper).trigger('click')
      await flushPromises()

      expect(mockedAddCheckpoint).toHaveBeenCalledTimes(1)
      expect(wrapper.find('.cp-form').exists()).toBe(true)
      expect(titleInput(wrapper).element.value).toBe('')
      expect(weightInput(wrapper).element.value).toBe('')
      expect(formSubmitButton(wrapper).text()).toBe('추가')
    })

    it('추가 성공 — 제목 칸으로 포커스가 돌아온다: 폼만 열어 두고 커서가 저장 버튼에 남으면 다음 건을 치려고 마우스를 다시 잡아야 한다', async () => {
      mockedAddCheckpoint.mockResolvedValueOnce({} as never)
      wrapper = renderList([checkpoint()], true)

      await addButton(wrapper)!.trigger('click')
      await titleInput(wrapper).setValue('코드 리뷰')
      await formSubmitButton(wrapper).trigger('click')
      await flushPromises()

      expect(document.activeElement).toBe(titleInput(wrapper).element)
    })

    it('수정 저장 성공 — 폼이 닫힌다(추가와 반대) — 고칠 항목은 목록에서 다시 골라야 하므로 열어 둘 이유가 없다', async () => {
      mockedUpdateCheckpoint.mockResolvedValueOnce({} as never)
      wrapper = renderList([checkpoint({ id: 7, title: '설계 리뷰' })], true)

      await editButton(wrapper)!.trigger('click')
      await titleInput(wrapper).setValue('설계 리뷰 v2')
      await formSubmitButton(wrapper).trigger('click')
      await flushPromises()

      expect(mockedUpdateCheckpoint).toHaveBeenCalledTimes(1)
      expect(wrapper.find('.cp-form').exists()).toBe(false)
      expect(addButton(wrapper)).toBeTruthy()
    })

    it('추가가 거부되면 폼이 열린 채 입력값이 그대로 남는다', async () => {
      mockedAddCheckpoint.mockRejectedValueOnce(new Error('네트워크 오류'))
      wrapper = renderList([checkpoint()], true)

      await addButton(wrapper)!.trigger('click')
      await titleInput(wrapper).setValue('코드 리뷰')
      await formSubmitButton(wrapper).trigger('click')
      await flushPromises()

      expect(wrapper.find('.cp-form').exists()).toBe(true)
      expect(titleInput(wrapper).element.value).toBe('코드 리뷰')
      expect(formSubmitButton(wrapper).text()).toBe('추가')
    })

    it('수정 저장이 거부되면 수정 모드와 입력값이 그대로 남는다 — 거부됐다고 입력을 다시 치게 만들면 안 된다(CLAUDE.md 화면 규칙)', async () => {
      mockedUpdateCheckpoint.mockRejectedValueOnce(new Error('네트워크 오류'))
      wrapper = renderList([checkpoint({ id: 1, title: '설계 리뷰', weight: 3 })], true)

      await editButton(wrapper)!.trigger('click')
      await titleInput(wrapper).setValue('설계 리뷰 v2')
      await formSubmitButton(wrapper).trigger('click')
      await flushPromises()

      expect(wrapper.find('.cp-form').exists()).toBe(true)
      expect(titleInput(wrapper).element.value).toBe('설계 리뷰 v2')
      expect(formSubmitButton(wrapper).text()).toBe('저장')
    })
  })

  /**
   * 폼이 `<form>`이 아니라 `<div>`다 — 트리 행 안에 들어가므로 중첩 폼을 만들 수 없다. 그래서
   * 브라우저의 기본 Enter-submit이 없고, 이 핸들러가 곧 Enter 저장의 전부다. 승인 입력에만
   * 달려 있던 비대칭을 없앤 것이라 두 글자 칸 모두에서 고정한다.
   */
  describe('editable: true — 키보드', () => {
    it('제목 칸에서 Enter를 누르면 저장된다', async () => {
      mockedAddCheckpoint.mockResolvedValueOnce({} as never)
      wrapper = renderList([checkpoint()], true)

      await addButton(wrapper)!.trigger('click')
      await titleInput(wrapper).setValue('코드 리뷰')
      await titleInput(wrapper).trigger('keydown.enter')
      await flushPromises()

      expect(mockedAddCheckpoint).toHaveBeenCalledTimes(1)
    })

    it('완료조건 칸에서 Enter를 누르면 저장된다 — 두 칸 어디서든 같아야 한다', async () => {
      mockedAddCheckpoint.mockResolvedValueOnce({} as never)
      wrapper = renderList([checkpoint()], true)

      await addButton(wrapper)!.trigger('click')
      await titleInput(wrapper).setValue('코드 리뷰')
      await criteriaInput(wrapper).setValue('리뷰어 승인')
      await criteriaInput(wrapper).trigger('keydown.enter')
      await flushPromises()

      expect(mockedAddCheckpoint).toHaveBeenCalledTimes(1)
    })

    it('제목이 비어 있으면 Enter를 눌러도 저장되지 않는다 — 저장 버튼의 disabled와 같은 판정이어야 한다', async () => {
      wrapper = renderList([checkpoint()], true)

      await addButton(wrapper)!.trigger('click')
      await titleInput(wrapper).trigger('keydown.enter')
      await flushPromises()

      expect(mockedAddCheckpoint).not.toHaveBeenCalled()
      expect(wrapper.find('.cp-form').exists()).toBe(true)
    })

    it('제목 칸에서 Esc를 누르면 폼이 닫힌다', async () => {
      wrapper = renderList([checkpoint()], true)

      await addButton(wrapper)!.trigger('click')
      await titleInput(wrapper).setValue('코드 리뷰')
      await titleInput(wrapper).trigger('keydown.esc')

      expect(wrapper.find('.cp-form').exists()).toBe(false)
      expect(addButton(wrapper)).toBeTruthy()
    })
  })

  /**
   * 승인자는 **모듈 스코프**에 기억되므로 이 파일 안에서 테스트끼리 값이 이어진다. 그래서 각
   * 테스트가 자기 안에서 먼저 한 번 승인해 기준값을 만들고, 그 뒤를 검증한다 — "처음에는 비어
   * 있다"를 단독으로 고정하면 실행 순서에 묶여 깨지기 쉽다.
   */
  describe('editable: true — 승인자 기억', () => {
    it('두 번째 승인부터 직전 승인자가 미리 채워지고, 전체 선택 상태라 바로 덮어쓸 수 있다', async () => {
      mockedSetApproval.mockResolvedValueOnce({} as never)
      wrapper = renderList([checkpoint({ id: 1 }), checkpoint({ id: 2 })], true)

      await approveButtons(wrapper)[0].trigger('click')
      await approverInput(wrapper).setValue('김재학')
      await approveConfirmButton(wrapper).trigger('click')
      await flushPromises()

      await approveButtons(wrapper)[1].trigger('click')
      await flushPromises()

      const input = approverInput(wrapper).element
      expect(input.value).toBe('김재학')
      expect(document.activeElement).toBe(input)
      expect(input.selectionStart).toBe(0)
      expect(input.selectionEnd).toBe('김재학'.length)
    })

    it('거부된 이름은 기억하지 않는다 — 실패한 입력을 다음 승인에 미리 채워 주면 같은 실패를 되풀이하게 된다', async () => {
      mockedSetApproval.mockResolvedValueOnce({} as never)
      wrapper = renderList([checkpoint({ id: 1 }), checkpoint({ id: 2 })], true)

      // 기준값을 만든다 — 성공한 승인 하나.
      await approveButtons(wrapper)[0].trigger('click')
      await approverInput(wrapper).setValue('이승하')
      await approveConfirmButton(wrapper).trigger('click')
      await flushPromises()

      // 다른 이름으로 시도했다가 거부된다.
      mockedSetApproval.mockRejectedValueOnce(new Error('네트워크 오류'))
      await approveButtons(wrapper)[1].trigger('click')
      await approverInput(wrapper).setValue('오타친이름')
      await approveConfirmButton(wrapper).trigger('click')
      await flushPromises()

      // 거부됐으므로 승인 입력은 열린 채 남고, 다시 열면 기억된 이름은 직전 성공값이다.
      expect(approverInput(wrapper).exists()).toBe(true)
      await wrapper.findAll('button').filter((b) => b.text() === '취소')[0].trigger('click')
      await approveButtons(wrapper)[1].trigger('click')
      await flushPromises()

      expect(approverInput(wrapper).element.value).toBe('이승하')
    })
  })
})
