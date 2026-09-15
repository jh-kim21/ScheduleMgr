import { describe, expect, it } from 'vitest'
import type { WbsNode } from '../../api/wbsApi'
import { approvalBadge, supportsCheckpoints } from './checkpointRow'

/**
 * `checkpointRow.ts`는 `WbsTree.vue`가 "이 행에 체크포인트 펼침을 보여줄지"를 판정하는 순수
 * 함수다. `WbsTree`를 마운트해서도 확인할 수 있지만, 여기서 고정하려는 것은 화면에 무엇이
 * 그려지는지가 아니라 그 판정 규칙 자체라서 순수 함수 테스트가 더 빠르고 규칙을 더 또렷하게
 * 고정한다(CLAUDE.md "컴포넌트 테스트"). `WbsTree`가 이 함수를 실제로 쓰는지는 소스 리뷰로
 * 확인한다.
 */
function node(overrides: Partial<Pick<WbsNode, 'nodeType' | 'executionMode'>>): Pick<
  WbsNode,
  'nodeType' | 'executionMode'
> {
  return {
    nodeType: 'WORK_PACKAGE',
    executionMode: null,
    ...overrides,
  }
}

describe('supportsCheckpoints', () => {
  it('WATERFALL Work Package는 true다 — Waterfall 진척의 분모가 체크포인트다', () => {
    expect(supportsCheckpoints(node({ nodeType: 'WORK_PACKAGE', executionMode: 'WATERFALL' }))).toBe(true)
  })

  it('HYBRID Work Package는 true다 — 진척식의 (1-α) 항이 여전히 체크포인트 가중치를 쓴다', () => {
    expect(supportsCheckpoints(node({ nodeType: 'WORK_PACKAGE', executionMode: 'HYBRID' }))).toBe(true)
  })

  it('AGILE Work Package는 false다 — 진척을 올리는 행위가 Board/Backlog의 완료 전이이지 체크포인트 승인이 아니다', () => {
    expect(supportsCheckpoints(node({ nodeType: 'WORK_PACKAGE', executionMode: 'AGILE' }))).toBe(false)
  })

  it('실행 방식 미지정(null) Work Package는 false다 — 분모 개념 자체가 없는 MANUAL 진척이라 체크포인트를 등록할 근거가 없다', () => {
    expect(supportsCheckpoints(node({ nodeType: 'WORK_PACKAGE', executionMode: null }))).toBe(false)
  })

  it('SUMMARY는 executionMode가 WATERFALL이어도 false다 — Summary는 전환 전 실행 방식을 보관만 할 뿐 사용하지 않는다 (CLAUDE.md "실행 방식 설계상 알아둘 점": Summary로 전환해도 값을 지우지 않고 보관한다). executionMode만 보고 판정하면 보관값이 남은 Summary에도 체크포인트 UI가 뜨는데, 서버는 Work Package에만 체크포인트를 허용하므로(Backlog 귀속과 같은 이유) 저장이 거부된다. WbsForm이 실행 방식 드롭다운을 Summary에서 잠글 때 같은 함정(nodeType과 executionMode를 혼동)에 걸렸던 적이 있다', () => {
    expect(supportsCheckpoints(node({ nodeType: 'SUMMARY', executionMode: 'WATERFALL' }))).toBe(false)
  })

  it('SUMMARY는 executionMode가 HYBRID여도 false다 (WATERFALL과 같은 이유)', () => {
    expect(supportsCheckpoints(node({ nodeType: 'SUMMARY', executionMode: 'HYBRID' }))).toBe(false)
  })
})

describe('approvalBadge', () => {
  it('승인 1/3 형태로 진행 상태를 보여준다 — 접힌 상태에서도 승인 현황을 알 수 있어야 한다', () => {
    expect(approvalBadge(1, 3)).toBe('승인 1/3')
  })

  it('0/3(전부 미승인)은 승인 0/3으로 적는다 — 체크포인트는 있으니 "없음"이 아니다', () => {
    expect(approvalBadge(0, 3)).toBe('승인 0/3')
  })

  it('3/3(전부 승인)은 승인 3/3으로 적는다', () => {
    expect(approvalBadge(3, 3)).toBe('승인 3/3')
  })

  it('0/0은 "체크포인트 없음"이다 — "승인 0/0"으로 적으면 "체크포인트가 아예 없는 상태"와 "체크포인트는 있지만 전부 미승인인 상태"가 같은 문구로 보인다. 진척식에서도 이 둘은 다르다: 후자는 분모가 있어 0%로 계산되지만 전자는 분모가 없어 산정 전(null)이다', () => {
    expect(approvalBadge(0, 0)).toBe('체크포인트 없음')
  })
})
