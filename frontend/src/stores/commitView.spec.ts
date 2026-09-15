import { beforeEach, describe, expect, it } from 'vitest'
import type { CommitPayload } from '../api/commitApi'
import { activeCommit, commitPayload, enterCommitView, exitCommitView, readOnly, type ActiveCommit } from './commitView'

// commitView의 세 값(activeCommit/commitPayload/readOnly)은 모듈 스코프다(useProjects/useWbs와 같은
// 패턴, scheduleCache.spec.ts와 같은 이유) — 9개 화면의 모든 쓰기 진입점이 이 하나의 readOnly를
// 검사하므로, "커밋 진입 → readOnly:true", "나가기 → readOnly:false"가 어긋나면 어느 화면 하나가
// 아니라 전부가 조용히 잘못된다. 모듈 스코프라 테스트 간에 값이 새어나갈 수 있어, 각 테스트를
// exitCommitView()로 시작해 알려진 초기 상태에서 출발한다.
const FAKE_COMMIT: ActiveCommit = {
  projectId: 1,
  id: 10,
  version: 3,
  asOf: '2026-03-01',
  message: '1차 검수 완료',
  committedBy: '김담당',
  formatVersion: 6,
}

// 실제 CommitPayload는 아홉 화면 응답을 모두 요구하지만, 이 테스트는 store가 참조를 그대로
// 들고 있다가 그대로 내놓는지만 본다 — 내용을 해석하지 않으므로 최소 형태로 캐스팅한다.
const FAKE_PAYLOAD = { asOf: '2026-03-01' } as unknown as CommitPayload

beforeEach(() => {
  exitCommitView()
})

describe('commitView 초기 상태', () => {
  it('아무것도 활성화하지 않으면 readOnly는 false다', () => {
    expect(activeCommit.value).toBeNull()
    expect(commitPayload.value).toBeNull()
    expect(readOnly.value).toBe(false)
  })
})

describe('enterCommitView', () => {
  it('activeCommit과 commitPayload를 채우고 readOnly를 true로 만든다', () => {
    enterCommitView(FAKE_COMMIT, FAKE_PAYLOAD)

    // `ref()`는 객체를 담으면 반응형 Proxy로 감싸므로, 원본과 참조가 같은지(toBe)가 아니라
    // 내용이 같은지(toEqual)로 비교한다 — Vue의 반응형 규칙이지 이 store의 버그가 아니다.
    expect(activeCommit.value).toEqual(FAKE_COMMIT)
    expect(commitPayload.value).toEqual(FAKE_PAYLOAD)
    // readOnly는 activeCommit의 파생값(computed)이어야 한다 — 별도 boolean이면 둘이 어긋날 수 있다.
    expect(readOnly.value).toBe(true)
  })
})

describe('exitCommitView', () => {
  it('활성화된 상태에서 나가면 둘 다 비우고 readOnly를 false로 되돌린다', () => {
    enterCommitView(FAKE_COMMIT, FAKE_PAYLOAD)
    expect(readOnly.value).toBe(true)

    exitCommitView()

    expect(activeCommit.value).toBeNull()
    expect(commitPayload.value).toBeNull()
    expect(readOnly.value).toBe(false)
  })

  it('이미 나가 있는 상태에서 다시 불러도 안전하다 (멱등)', () => {
    exitCommitView()
    exitCommitView()

    expect(activeCommit.value).toBeNull()
    expect(readOnly.value).toBe(false)
  })
})

describe('readOnly는 activeCommit의 파생값이다', () => {
  it('activeCommit이 바뀌는 즉시(별도 갱신 호출 없이) readOnly도 같이 바뀐다', () => {
    expect(readOnly.value).toBe(false)
    enterCommitView(FAKE_COMMIT, FAKE_PAYLOAD)
    expect(readOnly.value).toBe(true)
    exitCommitView()
    expect(readOnly.value).toBe(false)
  })
})
