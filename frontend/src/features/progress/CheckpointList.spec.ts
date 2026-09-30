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
      deleteCheckpoint: vi.fn(),
    },
  }
})

const mockedAddCheckpoint = vi.mocked(progressApi.addCheckpoint)
const mockedUpdateCheckpoint = vi.mocked(progressApi.updateCheckpoint)
const mockedSetApproval = vi.mocked(progressApi.setApproval)
const mockedDeleteCheckpoint = vi.mocked(progressApi.deleteCheckpoint)

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

/**
 * 가중치 칸은 자유 입력 숫자가 아니라 다섯 등급 `<select>`다(지시서 §3-C-2). `input[type="number"]`로
 * 찾던 예전 셀렉터는 이제 아무것도 집지 못하므로 클래스로 잡는다.
 */
function weightSelect(wrapper: VueWrapper) {
  return wrapper.find<HTMLSelectElement>('.cp-form select.cp-weight-input')
}

function weightOptions(wrapper: VueWrapper) {
  return weightSelect(wrapper).findAll<HTMLOptionElement>('option')
}

/** 지금 `<select>`가 고르고 있는 옵션의 글자. 잘린 라벨이 아니라 옵션 전체 문장이다. */
function selectedWeightText(wrapper: VueWrapper) {
  const select = weightSelect(wrapper).element
  return weightOptions(wrapper).find((o) => o.element.value === select.value)?.text()
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

function removeButtons(wrapper: VueWrapper) {
  return wrapper.findAll('button').filter((b) => b.text() === '삭제')
}

describe('CheckpointList', () => {
  let wrapper: VueWrapper | undefined

  afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
    mockedAddCheckpoint.mockReset()
    mockedUpdateCheckpoint.mockReset()
    mockedSetApproval.mockReset()
    mockedDeleteCheckpoint.mockReset()
    vi.unstubAllGlobals()
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
      expect(wrapper.text()).toContain('가중치 작음')
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
      // 드롭다운에는 "고르지 않음" 칸이 없으므로 빈 값이 아니라 기본 등급(보통)이 골라져 있다.
      expect(weightSelect(wrapper).element.value).toBe('3')
      expect(formSubmitButton(wrapper).text()).toBe('추가')
      expect(addButton(wrapper)).toBeUndefined()
    })

    it('수정 버튼을 누르면 기존 값이 폼에 채워진다 — 이 변경에서 가장 깨지기 쉬운 자리다: 폼을 감추는 쪽만 신경 쓰면 수정 버튼이 폼을 못 열어 수정 기능이 통째로 죽는다', async () => {
      wrapper = renderList([checkpoint({ title: '설계 리뷰', weight: 3, completionCriteria: '리뷰어 승인' })], true)

      await editButton(wrapper)!.trigger('click')

      expect(wrapper.find('.cp-form').exists()).toBe(true)
      expect(titleInput(wrapper).element.value).toBe('설계 리뷰')
      expect(weightSelect(wrapper).element.value).toBe('3')
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

    it('승인 상태 칩(.cp-approved·.cp-pending)은 editable: true에서도 <button>이 아니다 — 상태는 표시이지 동작이 아니다. 클릭해서 바뀌는 것은 아래 [승인]·[승인 취소] 버튼뿐이다', () => {
      wrapper = renderList(
        [checkpoint({ id: 1, approved: false }), checkpoint({ id: 2, approved: true, approvedBy: '김재학', approvedAt: '2026-03-02' })],
        true,
      )

      expect(wrapper.find('.cp-pending').element.tagName).toBe('SPAN')
      expect(wrapper.find('.cp-approved').element.tagName).toBe('SPAN')
    })

    it('목록 행은 숫자가 아니라 등급 라벨로 읽힌다 — null은 "보통"(서버 weightOf가 실제로 그 값으로 폴백한다), 0은 "집계 제외(0)", 척도 밖 값은 숫자째로 드러낸다', () => {
      wrapper = renderList(
        [
          checkpoint({ id: 1, weight: null }),
          checkpoint({ id: 2, weight: 0 }),
          checkpoint({ id: 3, weight: 8 }),
          checkpoint({ id: 4, weight: 30 }),
        ],
        true,
      )

      const weights = wrapper.findAll('.cp-weight').map((el) => el.text())
      expect(weights).toContain('가중치 보통')
      expect(weights).toContain('가중치 집계 제외(0)')
      expect(weights).toContain('가중치 아주 큼')
      expect(weights).toContain('가중치 사용자 지정 30')
      expect(weights.join(' ')).not.toContain('균등')
    })

    it('미입력을 "가중치 1"로 적지 않는다 — 1은 이제 척도의 최하단(아주 작음)이라, 그렇게 적으면 실제 계산값(보통=3)과 어긋난다', () => {
      wrapper = renderList([checkpoint({ weight: null })], true)

      expect(wrapper.find('.cp-weight').text()).not.toBe('가중치 1')
    })
  })

  /**
   * 가중치 칸은 자유 입력 숫자에서 다섯 등급 드롭다운이 되었다(지시서 §2-b). 등급마다 판단 기준
   * 문장이 붙는 것이 이 변경의 본체이므로 옵션 글자까지 본다 — 드롭다운만 만들고 기준을 빼면
   * "무엇을 적어야 하나"가 그대로 남는다.
   */
  describe('editable: true — 가중치 등급 드롭다운', () => {
    it('다섯 등급이 큰 것부터 있고, 각 옵션에 값과 판단 기준 문장이 함께 적힌다', async () => {
      wrapper = renderList([checkpoint()], true)

      await addButton(wrapper)!.trigger('click')

      const texts = weightOptions(wrapper).map((o) => o.text())
      expect(texts).toEqual([
        '아주 큼 (8) — 이 업무의 절반 이상',
        '큼 (5) — 큰 덩어리 하나',
        '보통 (3) — 평범한 한 단계',
        '작음 (2) — 짧게 끝나는 단계',
        '아주 작음 (1) — 형식적 확인',
      ])
    })

    it('추가 폼에서는 보통이 미리 골라져 있다 — 미입력도 서버가 보통으로 계산하므로 기본값이 왜곡을 만들지 않는다', async () => {
      wrapper = renderList([checkpoint()], true)

      await addButton(wrapper)!.trigger('click')

      expect(selectedWeightText(wrapper)).toBe('보통 (3) — 평범한 한 단계')
    })

    it('placeholder가 없는 <select>라 aria-label로 접근 가능한 이름을 남긴다', async () => {
      wrapper = renderList([checkpoint()], true)

      await addButton(wrapper)!.trigger('click')

      expect(weightSelect(wrapper).attributes('aria-label')).toBe('가중치')
    })

    it('미입력(null)이던 체크포인트를 열면 보통이 골라져 있고 비활성 옵션이 끼어들지 않는다 — 계산이 이미 그 값이라 그대로 저장해도 진척이 변하지 않는다', async () => {
      wrapper = renderList([checkpoint({ weight: null })], true)

      await editButton(wrapper)!.trigger('click')

      expect(weightSelect(wrapper).element.value).toBe('3')
      expect(weightOptions(wrapper)).toHaveLength(5)
    })

    it('등급을 골라 저장하면 그 숫자가 그대로 제출된다', async () => {
      mockedUpdateCheckpoint.mockResolvedValueOnce({} as never)
      wrapper = renderList([checkpoint({ id: 7, weight: 3 })], true)

      await editButton(wrapper)!.trigger('click')
      await weightSelect(wrapper).setValue('8')
      await formSubmitButton(wrapper).trigger('click')
      await flushPromises()

      expect(mockedUpdateCheckpoint).toHaveBeenCalledWith(1, 7, expect.objectContaining({ weight: 8 }))
    })
  })

  /**
   * `<option disabled>`는 *고를 수 없다*는 뜻이지 *선택 상태로 둘 수 없다*는 뜻이 아니다
   * (지시서 §7-3). 제목만 고치려고 연 편집이 저장할 때 가중치를 임의의 등급으로 덮으면 커밋
   * `da96ebe`와 같은 사고다 — 소스 리뷰로는 놓치기 쉬워 제출값까지 본다.
   */
  describe('editable: true — 레거시 가중치 보존', () => {
    it('0이 저장된 체크포인트는 "집계 제외(0)"가 비활성으로 선택돼 있다 — 새로 고를 수는 없지만 지금 값이 무엇인지는 보여야 한다', async () => {
      wrapper = renderList([checkpoint({ weight: 0 })], true)

      await editButton(wrapper)!.trigger('click')

      expect(weightSelect(wrapper).element.value).toBe('0')
      expect(selectedWeightText(wrapper)).toBe('집계 제외(0)')
      expect(weightOptions(wrapper)[0].attributes('disabled')).toBeDefined()
    })

    it('0인 체크포인트의 제목만 고쳐 저장하면 가중치가 0 그대로 나간다', async () => {
      mockedUpdateCheckpoint.mockResolvedValueOnce({} as never)
      wrapper = renderList([checkpoint({ id: 7, title: '설계 리뷰', weight: 0 })], true)

      await editButton(wrapper)!.trigger('click')
      await titleInput(wrapper).setValue('설계 리뷰 v2')
      await formSubmitButton(wrapper).trigger('click')
      await flushPromises()

      expect(mockedUpdateCheckpoint).toHaveBeenCalledWith(
        1,
        7,
        expect.objectContaining({ title: '설계 리뷰 v2', weight: 0 }),
      )
    })

    it('척도 밖 값(30)은 "사용자 지정 30"으로 비활성 선택된다 — 구형 파일 가져오기·커밋 복원이 싣고 오는 정상 데이터다', async () => {
      wrapper = renderList([checkpoint({ weight: 30 })], true)

      await editButton(wrapper)!.trigger('click')

      expect(weightSelect(wrapper).element.value).toBe('30')
      expect(selectedWeightText(wrapper)).toBe('사용자 지정 30')
      expect(weightOptions(wrapper)[0].attributes('disabled')).toBeDefined()
    })

    it('30인 체크포인트의 제목만 고쳐 저장하면 가중치가 30 그대로 나간다 — 가까운 등급으로 반올림하지 않는다', async () => {
      mockedUpdateCheckpoint.mockResolvedValueOnce({} as never)
      wrapper = renderList([checkpoint({ id: 7, title: '설계 리뷰', weight: 30 })], true)

      await editButton(wrapper)!.trigger('click')
      await titleInput(wrapper).setValue('설계 리뷰 v2')
      await formSubmitButton(wrapper).trigger('click')
      await flushPromises()

      expect(mockedUpdateCheckpoint).toHaveBeenCalledWith(
        1,
        7,
        expect.objectContaining({ title: '설계 리뷰 v2', weight: 30 }),
      )
    })

    it('척도 위의 값(3)에는 비활성 옵션이 붙지 않는다 — 레거시 옵션은 필요할 때만 생긴다', async () => {
      wrapper = renderList([checkpoint({ weight: 3 })], true)

      await editButton(wrapper)!.trigger('click')

      expect(weightOptions(wrapper)).toHaveLength(5)
      expect(weightOptions(wrapper).every((o) => o.attributes('disabled') === undefined)).toBe(true)
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
      await weightSelect(wrapper).setValue('5')
      await formSubmitButton(wrapper).trigger('click')
      await flushPromises()

      expect(mockedAddCheckpoint).toHaveBeenCalledTimes(1)
      expect(wrapper.find('.cp-form').exists()).toBe(true)
      expect(titleInput(wrapper).element.value).toBe('')
      // 가중치는 "비워지는" 것이 아니라 기본 등급으로 돌아간다 — 드롭다운에 빈 칸이 없다.
      expect(weightSelect(wrapper).element.value).toBe('3')
      expect(formSubmitButton(wrapper).text()).toBe('추가')
    })

    it('추가 성공 — 직전에 고른 등급이 다음 건으로 이어지지 않는다: 연달아 넣을 때 앞 건의 5가 남아 있으면 고르지도 않은 비중이 박힌다', async () => {
      mockedAddCheckpoint.mockResolvedValueOnce({} as never)
      wrapper = renderList([checkpoint()], true)

      await addButton(wrapper)!.trigger('click')
      await titleInput(wrapper).setValue('코드 리뷰')
      await weightSelect(wrapper).setValue('8')
      await formSubmitButton(wrapper).trigger('click')
      await flushPromises()

      expect(mockedAddCheckpoint).toHaveBeenCalledWith(1, expect.objectContaining({ weight: 8 }))
      expect(weightSelect(wrapper).element.value).toBe('3')
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

    it('가중치 <select>에서도 Enter로 저장된다 — 숫자 입력칸이 드롭다운이 되면서 핸들러를 빠뜨리기 쉬운 자리다(지시서 §7-8)', async () => {
      mockedAddCheckpoint.mockResolvedValueOnce({} as never)
      wrapper = renderList([checkpoint()], true)

      await addButton(wrapper)!.trigger('click')
      await titleInput(wrapper).setValue('코드 리뷰')
      await weightSelect(wrapper).trigger('keydown.enter')
      await flushPromises()

      expect(mockedAddCheckpoint).toHaveBeenCalledTimes(1)
    })

    it('가중치 <select>에서도 Esc로 폼이 닫힌다 — 세 칸 어디서든 같아야 한다', async () => {
      wrapper = renderList([checkpoint()], true)

      await addButton(wrapper)!.trigger('click')
      await weightSelect(wrapper).trigger('keydown.esc')

      expect(wrapper.find('.cp-form').exists()).toBe(false)
      expect(addButton(wrapper)).toBeTruthy()
    })

    /**
     * Esc 핸들러가 입력칸에만 붙어 있으면 **버튼 위에서 죽는다** — 버튼을 클릭하면 브라우저가
     * 거기에 포커스를 주고(Windows/Chrome), Tab으로 옮겨도 마찬가지다. 그래서 핸들러를
     * `.approve-row`·`.cp-form` 컨테이너로 올렸고, 아래 두 건이 그것을 고정한다.
     *
     * 버튼에 `keydown`을 쏘는 것이 곧 검증이다 — `trigger`는 그 요소에서 이벤트를 만들고
     * 버블링시키므로, 핸들러가 컨테이너에 없으면 이 테스트가 빨개진다.
     */
    it('승인 행의 [취소] 버튼에서 Esc를 눌러도 닫힌다 — 버튼에 포커스가 간 뒤 Esc가 죽던 자리다', async () => {
      wrapper = renderList([checkpoint()], true)

      await approveButtons(wrapper)[0].trigger('click')
      expect(approverInput(wrapper).exists()).toBe(true)

      await wrapper.find('.approve-row button.ghost').trigger('keydown.esc')

      expect(wrapper.find('.approve-row').exists()).toBe(false)
    })

    it('추가 폼의 [추가] 버튼에서 Esc를 눌러도 닫힌다 — 같은 결함이 폼 쪽에도 있었다', async () => {
      wrapper = renderList([checkpoint()], true)

      await addButton(wrapper)!.trigger('click')
      await titleInput(wrapper).setValue('코드 리뷰')

      await formSubmitButton(wrapper).trigger('keydown.esc')

      expect(wrapper.find('.cp-form').exists()).toBe(false)
      expect(addButton(wrapper)).toBeTruthy()
    })

    /**
     * Esc와 달리 **Enter는 컨테이너로 올리지 않았다** — 실제 브라우저에서 `<button>` 위의 Enter는
     * 이미 click을 일으키므로, 컨테이너에도 핸들러가 있으면 같은 저장이 두 번 나간다.
     *
     * 이 테스트가 보는 것은 그 이중 호출 자체가 아니라 **컨테이너에 Enter 핸들러가 없다**는
     * 사실이다: `trigger`는 이벤트만 쏘고 브라우저의 click 합성까지 흉내 내지 않으므로, 여기서
     * 저장이 불린다면 그것은 오직 컨테이너 핸들러가 생겼다는 뜻이다(= 되돌리면 빨개진다).
     */
    it('추가 폼 컨테이너에는 Enter 핸들러가 없다 — 버튼 위 Enter로 저장이 두 번 나가지 않게', async () => {
      mockedAddCheckpoint.mockResolvedValue({} as never)
      wrapper = renderList([checkpoint()], true)

      await addButton(wrapper)!.trigger('click')
      await titleInput(wrapper).setValue('코드 리뷰')
      await formSubmitButton(wrapper).trigger('keydown.enter')
      await flushPromises()

      expect(mockedAddCheckpoint).not.toHaveBeenCalled()
    })
  })

  /**
   * 삭제는 다른 화면의 관례(`WbsView.vue`, `MemberEditor.vue`)와 같은 `confirm()`을 거친다.
   * 승인된 체크포인트는 진척 계산의 분모라서(CLAUDE.md "진척 집계"), 미승인 항목과 문구가
   * 달라야 한다는 것까지 함께 고정한다.
   */
  describe('editable: true — 삭제 확인', () => {
    it('확인 대화상자에서 취소하면 삭제 API를 부르지 않는다', async () => {
      // happy-dom은 window.confirm을 구현하지 않는다 — vi.spyOn 대상 함수 자체가 없어
      // vi.stubGlobal로 통째로 채워 넣는다(afterEach의 vi.unstubAllGlobals가 되돌린다 —
      // vi.restoreAllMocks는 stubGlobal을 되돌리지 못하므로 그것으로 바꾸지 마라).
      vi.stubGlobal('confirm', vi.fn().mockReturnValue(false))
      wrapper = renderList([checkpoint()], true)

      await removeButtons(wrapper)[0].trigger('click')

      expect(mockedDeleteCheckpoint).not.toHaveBeenCalled()
    })

    it('확인하면 삭제 API를 부른다', async () => {
      vi.stubGlobal('confirm', vi.fn().mockReturnValue(true))
      mockedDeleteCheckpoint.mockResolvedValueOnce({} as never)
      wrapper = renderList([checkpoint({ id: 9 })], true)

      await removeButtons(wrapper)[0].trigger('click')
      await flushPromises()

      expect(mockedDeleteCheckpoint).toHaveBeenCalledWith(1, 9)
    })

    it('미승인 항목은 부작용 문구 없이 묻는다', async () => {
      const confirmSpy = vi.fn().mockReturnValue(false)
      vi.stubGlobal('confirm', confirmSpy)
      wrapper = renderList([checkpoint({ title: '설계 리뷰', approved: false })], true)

      await removeButtons(wrapper)[0].trigger('click')

      expect(confirmSpy).toHaveBeenCalledWith('"설계 리뷰" 체크포인트를 삭제할까요?')
    })

    it('승인된 항목은 진척 숫자가 바뀐다는 문구를 덧붙여 묻는다', async () => {
      const confirmSpy = vi.fn().mockReturnValue(false)
      vi.stubGlobal('confirm', confirmSpy)
      wrapper = renderList(
        [checkpoint({ title: '설계 리뷰', approved: true, approvedBy: '김재학', approvedAt: '2026-03-02' })],
        true,
      )

      await removeButtons(wrapper)[0].trigger('click')

      expect(confirmSpy).toHaveBeenCalledWith(
        '"설계 리뷰" 체크포인트를 삭제할까요?\n이미 승인되어 있어 지우면 이 Work Package의 진척 숫자가 즉시 바뀝니다.',
      )
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
