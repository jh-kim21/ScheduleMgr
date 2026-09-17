/**
 * 업무 분야 태그 칩의 색을 정한다. **판정만 하고 색값은 만들지 않는다** — 이 모듈이 돌려주는 것은
 * 팔레트 슬롯의 *이름*이고, 실제 색은 `style.css`의 `--tag-<이름>-bg`/`-fg` 토큰에 있다. 컴포넌트에
 * 하드코딩된 색이 하나라도 남으면 그 자리만 라이트로 남아 다크 모드에서 눈에 튄다
 * (CLAUDE.md "다크 모드").
 *
 * **랜덤이 아니라 이름 해시다**(지시서 부록 함정 7). 색을 그릴 때마다 뽑으면 같은 태그가 스크롤·
 * 재렌더마다 다른 색이 되어 "분야별로 훑는다"는 이 열의 목적 자체가 없어진다. 같은 이름은 언제
 * 어느 프로젝트에서 보든 같은 슬롯을 받는다.
 */

/**
 * 고를 수 있는 슬롯. 지연 상태 팔레트(`--status-*`)를 빌려 쓰지 않는다 — 같은 표 안에서 빨간 칩이
 * "지연"으로 읽히고, 분야는 상태가 아니다.
 */
export const TAG_COLORS = [
  'blue',
  'teal',
  'green',
  'lime',
  'amber',
  'orange',
  'rose',
  'violet',
] as const

export type TagColor = (typeof TAG_COLORS)[number]

/** 사용자가 고를 수 있는 값인지. 가져오기·구형 데이터로 들어온 모르는 값은 이름 해시로 떨어진다. */
export function isTagColor(value: string | null | undefined): value is TagColor {
  return value !== null && value !== undefined && (TAG_COLORS as readonly string[]).includes(value)
}

/**
 * FNV-1a 32비트. 암호학적 성질이 필요한 자리가 아니라 **결정적이기만 하면 되는** 자리이고, 짧은
 * 한글·영문 이름에서도 슬롯이 고르게 흩어져야 해서 자릿수 단순 합산 대신 이 함수를 쓴다.
 * `Math.imul`은 곱셈을 32비트로 유지하기 위한 것이다 — 일반 `*`는 정밀도를 넘겨 값이 뭉개진다.
 */
function hash(value: string): number {
  let result = 2166136261
  for (let i = 0; i < value.length; i += 1) {
    result ^= value.charCodeAt(i)
    result = Math.imul(result, 16777619)
  }
  return result >>> 0
}

/**
 * 이 태그가 쓸 슬롯. 저장된 `color`가 아는 슬롯이면 그것을, 아니면 이름 해시로 고른다.
 *
 * `color`를 자유 색값(`#3366ff`)이 아니라 슬롯 이름으로 다루는 것은 의도적이다 — 임의의 색을
 * 그대로 칠하면 다크 모드에서 읽히는지 아무도 보장하지 못하고, "하드코딩 색 0건" 규칙이 데이터
 * 쪽으로 새어 나갈 뿐이다.
 */
export function tagColorFor(tag: { name: string; color?: string | null }): TagColor {
  if (isTagColor(tag.color)) return tag.color
  return TAG_COLORS[hash(tag.name) % TAG_COLORS.length]
}
