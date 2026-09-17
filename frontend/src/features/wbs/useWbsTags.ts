import { ref } from 'vue'
import { ApiError } from '../../api/http'
import { wbsTagApi, type WbsTag, type WbsTagInput } from '../../api/wbsTagApi'
import { markWbsChanged } from '../../stores/scheduleCache'
import { readOnly } from '../../stores/commitView'

/**
 * 프로젝트의 업무 분야 태그 마스터.
 *
 * `useMembers`와 같은 모양이고 같은 이유다 — 관리 대화상자는 프로젝트 화면의 *행*에서 열리므로
 * "지금 선택된 프로젝트"라는 개념이 없고, 캐시 키는 프로젝트 id 하나면 된다. 여기에는 "오늘"을
 * 기준으로 판정하는 값도, 다른 화면이 올려 주는 리비전도 들어가지 않는다. 이 목록 자체가 WBS
 * 트리가 무효화의 근거로 삼는 원천이다(아래 `markWbsChanged`).
 *
 * 모듈 스코프라 대화상자를 닫았다 다시 열어도, 폼과 관리 화면이 각각 불러도 한 벌만 읽는다.
 */
const tags = ref<WbsTag[]>([])
const loading = ref(false)
const error = ref<string | null>(null)
/** 어느 프로젝트의 목록이 캐시돼 있는지; 아무것도 없으면 null. */
let cacheProjectId: number | null = null
/** 진행 중인 요청 — 같은 tick에 겹쳐 부르는 호출자들이 하나를 나눠 쓴다. */
let inFlight: { projectId: number; promise: Promise<void> } | null = null

export function useWbsTags() {
  function describe(e: unknown, fallback: string) {
    return e instanceof ApiError ? e.message : fallback
  }

  async function load(projectId: number) {
    loading.value = true
    error.value = null
    try {
      tags.value = await wbsTagApi.list(projectId)
      cacheProjectId = projectId
    } catch (e) {
      tags.value = []
      cacheProjectId = null
      error.value = describe(e, '분야 목록을 불러오지 못했습니다.')
    } finally {
      loading.value = false
    }
  }

  /** 캐시된 목록이 다른 프로젝트의 것일 때만 다시 읽는다. */
  function ensureLoaded(projectId: number): Promise<void> {
    if (cacheProjectId === projectId) return Promise.resolve()
    if (inFlight?.projectId === projectId) return inFlight.promise
    const promise = load(projectId).finally(() => {
      if (inFlight?.projectId === projectId) inFlight = null
    })
    inFlight = { projectId, promise }
    return promise
  }

  /**
   * 변경을 실행하고 서버가 돌려준 **전체 목록**으로 갈아 끼운 뒤 WBS를 낡은 것으로 표시한다.
   *
   * 태그 전용 리비전을 따로 두지 않는다 — 트리의 칩은 이름과 색을 이 목록에서 가져오지만, 그 값은
   * WBS 응답에 실려 오므로 트리를 다시 읽으면 함께 따라온다. 리비전을 하나 더 만들면 같은 무효화를
   * 두 곳에서 관리하게 된다.
   *
   * 서버가 거부했는지 호출자가 알아야 대화상자를 열어 둔 채 사유를 보여 줄 수 있으므로 boolean을
   * 돌려준다 (CLAUDE.md "화면 레이아웃 규칙").
   */
  async function mutate(action: () => Promise<WbsTag[]>, fallback: string): Promise<boolean> {
    // 화면에서 이미 막혀 있어야 하지만 여기서도 한 번 더 막는다 — 커밋 조회 중에는 서버에 아무것도
    // 보내지 않는다.
    if (readOnly.value) return false
    error.value = null
    try {
      tags.value = await action()
      markWbsChanged()
      return true
    } catch (e) {
      error.value = describe(e, fallback)
      return false
    }
  }

  const create = (projectId: number, input: WbsTagInput) =>
    mutate(() => wbsTagApi.create(projectId, input), '분야를 추가하지 못했습니다.')

  const update = (projectId: number, tagId: number, input: WbsTagInput) =>
    mutate(() => wbsTagApi.update(projectId, tagId, input), '분야를 수정하지 못했습니다.')

  const remove = (projectId: number, tagId: number) =>
    mutate(() => wbsTagApi.remove(projectId, tagId), '분야를 삭제하지 못했습니다.')

  return { tags, loading, error, load, ensureLoaded, create, update, remove }
}
