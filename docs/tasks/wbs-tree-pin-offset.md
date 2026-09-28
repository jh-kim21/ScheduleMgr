# WBS 트리 — 고정 열 오프셋을 선언값이 아니라 실제 렌더 폭에서 잰다

대상 체크아웃: `D:\git\ScheduleMgr` (main `932284c` "마우스 전용 조작에 키보드 대안을 만들고 접근성 공백을 메운다")
모든 줄번호는 그 커밋에서 확인한 값이다.

`wbs-tree-columns`(완료, `done/`)가 넣은 고정 열의 버그를 고친다. **원인은 그 지시서에 있다** —
`left`를 "`.code`의 CSS 폭과 반드시 같은 값"으로 적었고, 그것이 렌더 폭이 아니라는 것을 짚지
않았다. 그 문장이 `wbsColumns.ts:42-44`와 `WbsTree.vue:1466-1468`의 주석으로 그대로 옮겨져 있다.

---

## 1. 해결할 문제

사용자가 보고한 두 증상이다.

1. `열 설정 → 고정: WBS + 업무명`으로 두면 **WBS와 업무명 열 사이가 떠서 이상하게 동작**한다
2. **첫 행(머리글)도 사이가 떠서, 좌우로 스크롤하면 그 틈으로 화면이 보인다**

둘은 같은 뿌리다. 고정 오프셋이 **선언한 CSS 폭**을 그대로 누적한다.

| 계층 | 값 |
|---|---|
| 열 모델 | `wbsColumns.ts:47` `{ key: 'code', pinWidthRem: 5.5 }` |
| 누적 | `wbsColumns.ts:105` `cursor += column?.pinWidthRem ?? 0` → 업무명 `left: 5.5rem` |
| CSS | `WbsTree.vue:1419-1424` `.code { width: 5.5rem }` |
| 적용 | `WbsTree.vue:683-686` `pinStyle()` → `left: '5.5rem'` |

그런데 그 값은 셀이 실제로 차지하는 폭이 아니다. 두 가지가 겹친다.

- **`th, td { padding: 0.5rem 0.6rem }`**(`WbsTree.vue:1232-1241`)이고 이 프로젝트에는
  `box-sizing: border-box`가 **한 줄도 없다**(`style.css`·`WbsTree.vue` 전체 grep). 기본값
  `content-box`라 `width: 5.5rem`은 내용 폭이고, 셀의 실제 폭은 `5.5 + 1.2 = 6.7rem`이다.
- **`table-layout`이 `auto`이고 표에 `width: 100%`**가 걸려 있다(`WbsTree.vue:1227-1230`). auto
  레이아웃에서 `width`는 *제안값*이라 내용이 길면(`1.10.12` 같은 코드) 더 넓어지고 남는 폭도
  분배된다 — **선언값과 렌더 폭이 같다는 보장이 애초에 없다.**

그래서 `WBS + 업무명`을 고정하면:

- 업무명이 코드 열의 **실제 오른쪽 끝보다 1.2rem 왼쪽**에 달라붙는다. 둘 다 `z-index: 1`이고
  (`WbsTree.vue:1443-1446`) DOM 순서상 업무명이 나중이라 코드 열의 오른쪽 끝을 덮는다 → 증상 1.
- 고정 영역의 실제 폭은 `6.7 + 23.2 = 29.9rem`인데 sticky가 덮는 구간은 `0 ~ 28.7rem`이다 →
  **오른쪽 끝 1.2rem이 어느 고정 셀에도 덮이지 않아 그 틈으로 스크롤되는 열이 지나간다.**
- 머리글은 가로·세로 양쪽으로 고정이고 배경이 본문과 다른 색이라(`thead th.pinned`는
  `--surface-alt`(`:1448-1451`), 본문 `.pinned`는 `--bg`(`:1443-1446`)) 이 틈이 가장 잘 보인다 →
  증상 2.

**테스트가 잡지 못한 이유**: `wbsColumns.spec.ts:37-38`과 `WbsTreeColumns.spec.ts:241-251`이
`left`가 `5.5`인지를 단정한다 — 코드와 **같은 잘못된 가정**을 확인하고 있어서 화면이 깨진 채로
통과한다.

---

## 2. 결정된 방향

### 2-1. 상수를 6.7로 바꾸는 것은 답이 아니다

패딩을 바꾸는 순간 같은 버그가 돌아오고, 무엇보다 위 두 번째 이유(auto 레이아웃) 때문에
**어떤 상수도 맞지 않는다.** 코드 열은 내용에 따라 더 넓어질 수 있다.

같은 이유로 `box-sizing: border-box`를 얹는 것도 부분적인 해결일 뿐이다 — 선언값이 최소 제안값이
되는 것은 그대로다.

### 2-2. 폭은 DOM이 답한다

머리글 행의 셀 폭을 재서 누적한다. 이 저장소가 파생값을 저장하지 않는 것과 같은 방식이다(WBS
코드·상위 일정·진척 모두 조회 시점 계산). **`pinWidthRem`은 CSS 값의 복사본이고, 복사본이 어긋난
것이 이 버그다 — 지운다.**

`measure()`가 이미 있고(`WbsTree.vue:256-266`) `headRow` 참조(`:253`)·`resize` 등록(`:267-270`)·
`nextTick` 재측정 watch(`:272-279`)가 전부 갖춰져 있다. 같은 자리에 얹는다 — 머리글 높이를
`--head-h`로 재는 것과 **똑같은 이유·똑같은 방식**이고, 그 결정은 이미 옳았다(`:247-252`의 주석:
*"px 상수로 박으면 글꼴이 바뀌는 순간 어긋나므로"*). 폭에도 같은 판단을 적용하지 않은 것이 이
버그다.

### 2-3. 순수 함수는 정책만 답한다

`pinOffsets(prefs)`는 숫자를 잃고 **순서**를 답한다.

```
pinnedSequence(prefs): WbsColumnKey[]   // 'none' → [], 'code' → ['code'], 'name' → ['code','name']
                                        // 숨긴 열은 빠진다
```

"앞에서부터 연속으로만, 숨긴 열은 제외"라는 정책은 그대로 테스트로 고정되고, 폭만 DOM으로
넘어간다.

### 2-4. 고정 경계선도 같은 이유로 고친다

`.pinned-edge { border-right: 1px solid var(--border) }`(`WbsTree.vue:1453-1456`). **`border-collapse:
collapse`에서는 경계선을 표가 그리므로 sticky 셀과 함께 움직이지 않는다.** 머리글이 이미 같은
이유로 `border-bottom`을 `box-shadow: inset`으로 바꿔 뒀다(`:1262-1266`의 주석: *"sticky 요소의
border-bottom은 스크롤 중 겹쳐 보일 수 있어 box-shadow로 대신한다"*). 오른쪽 경계선도 같은 처리가
필요하다.

### 2-5. 숫자를 단정하는 테스트는 만들 수 없다

`happy-dom`에는 레이아웃이 없어 `getBoundingClientRect()`가 0을 돌려준다. `left`의 px 값을 단정하는
컴포넌트 테스트는 **원리상 쓸 수 없다.** 순수 함수가 정책을 고정하고, 컴포넌트 테스트는 "어느 셀에
`position: sticky`가 붙는가"만 본다.

---

## 3. 작업

### 3-1. `frontend/src/features/wbs/wbsColumns.ts`

| 줄 | 할 일 |
|---|---|
| `:36-37` | `pinWidthRem` 필드 **삭제** |
| `:41-45` | 그 위 주석 삭제 — *"`.code` 폭과 반드시 같은 값이어야 한다"*가 이 버그의 근원이다 |
| `:47-48` | 두 열의 `pinWidthRem: 5.5` / `22` 삭제 |
| `:90-109` | `pinOffsets` → `pinnedSequence(prefs): WbsColumnKey[]`. `PIN_SEQUENCE`(`:67-70`)를 그대로 쓰되 `visibleColumns`에 없는 키를 걸러 배열로 돌려준다. 누적(`cursor`)은 사라진다 |

새 주석에 **왜 폭이 여기 없는지**를 적는다: 선언한 CSS 폭은 `content-box`이고 `table-layout: auto`
에서는 제안값일 뿐이라 렌더 폭과 다르다, 그래서 폭은 `WbsTree.vue`가 잰다.

### 3-2. `frontend/src/features/wbs/wbsColumns.spec.ts`

`describe('pinOffsets')`(`:28-42`)를 `pinnedSequence`로 바꾼다. **`:37-38`의 `{ code: 0, name: 5.5 }`
단정은 지운다** — 이 단정이 깨진 화면을 통과시켰다.

남길 판정(이름만 바꿔 그대로):

- `pin: 'none'` → `[]`
- `pin: 'code'` → `['code']`
- `pin: 'name'` → `['code', 'name']`
- `hidden: ['code'], pin: 'name'` → `['name']` (숨긴 열이 고정 순서에서 빠진다)

### 3-3. `frontend/src/features/wbs/WbsTree.vue`

**(a) 오프셋을 잰다.** `measure()`(`:256-266`)에 얹는다.

```ts
/** 고정된 열의 left(px). 선언한 CSS 폭이 아니라 머리글 셀의 실제 렌더 폭을 누적한다. */
const pinnedLeft = ref<Partial<Record<WbsColumnKey, number>>>({})

function measurePins() {
  const row = headRow.value
  const sequence = pinnedSequence(columnPrefs.value)
  if (!row || sequence.length === 0) {
    pinnedLeft.value = {}
    return
  }
  // 머리글 셀은 visibleColumns 와 같은 순서·같은 개수다.
  const keys = visibleColumns(columnPrefs.value).map((column) => column.key)
  const widths = new Map<WbsColumnKey, number>()
  Array.from(row.children).forEach((cell, index) => {
    const key = keys[index]
    if (key) widths.set(key, cell.getBoundingClientRect().width)
  })

  const next: Partial<Record<WbsColumnKey, number>> = {}
  let cursor = 0
  for (const key of sequence) {
    next[key] = cursor
    cursor += widths.get(key) ?? 0
  }
  pinnedLeft.value = next
}
```

`measure()` 끝에서 부른다(머리글 높이를 재는 바로 그 자리, `:262-264`).

**다시 재야 하는 시점** — 셋은 이미 걸려 있고 **하나가 빠져 있다**:

| 언제 | 지금 | 할 일 |
|---|---|---|
| 마운트 | `onMounted`(`:267-270`) | 그대로 |
| 창 크기 변경 | `resize`(`:269`) | 그대로 |
| 트리 변경 | `watch(() => props.tree)`(`:272-276`) | 그대로 — 긴 코드가 생겨 열이 넓어지는 경우를 덮는다 |
| 필터 행 토글 | `watch(filterRowOpen)`(`:279`) | 그대로 |
| **열 설정 변경** | **없음** | **추가한다** — `watch(columnPrefs, () => nextTick(measure), { deep: true })` |

마지막이 없으면 고정을 켜는 순간이나 열을 숨기는 순간의 오프셋이 이전 상태의 것으로 남는다.
`nextTick`이 필수다 — `pin: 'name'`으로 바꾸면 `.pin-name` 클래스가 업무명 열의 폭을 바꾸므로
(`:1470-1473`) DOM이 갱신된 뒤에 재야 한다.

**(b) `pinStyle`을 px로.** `:683-686`.

```ts
function pinStyle(key: WbsColumnKey, header: boolean) {
  const left = pinnedLeft.value[key]
  if (left === undefined) return undefined
  return { position: 'sticky' as const, left: `${left}px`, zIndex: header ? 3 : 1 }
}
```

`isPinned`(`:664-666`)와 `lastPinnedKey`(`:669-674`)도 `pinnedLeft`를 읽게 바꾼다. `lastPinnedKey`는
오프셋 최대값 비교(`reduce`) 대신 **`pinnedSequence`의 마지막 원소**로 두는 편이 단순하고, 폭이
0으로 측정되는 환경(테스트)에서도 답이 흔들리지 않는다.

**측정 전(0px) 처리**: `headRow`가 없거나 폭이 0이면 모든 오프셋이 0이라 고정 열이 겹친다. 실제
브라우저에서는 `onMounted`의 첫 `measure()`가 페인트 전에 돌아 사실상 보이지 않고, 레이아웃이 없는
테스트 환경에서는 `position: sticky`만 검증하므로 문제되지 않는다. **이 사실을 주석으로 남긴다** —
다음 사람이 "0이 나올 수 있는데?"에서 멈추지 않게.

**(c) 고정 경계선을 `box-shadow`로.** `:1453-1456`.

```css
.pinned-edge {
  /* border-collapse: collapse 에서 경계선은 표가 그리므로 sticky 셀과 함께 움직이지 않는다 —
     머리글의 border-bottom 을 box-shadow 로 바꾼 것(위 thead th)과 같은 이유다. */
  box-shadow: inset -1px 0 0 var(--border);
}
```

머리글의 고정 칸은 `thead th`가 이미 `inset 0 -1px 0`을 쓰고 있다(`:1265`) — 두 그림자가 한 셀에
겹치므로 **쉼표로 이어 붙여야 한다**(`box-shadow`는 뒤 선언이 앞을 덮는다). `thead th.pinned-edge`에
`inset 0 -1px 0 var(--border), inset -1px 0 0 var(--border)`를 함께 준다. 빠뜨리면 고정된 머리글
칸의 아래 선이 사라진다.

**(d) 주석을 고친다.** `:1462-1473`의 `.col-name`/`.pin-name .col-name` 주석이 *"`wbsColumns.ts`의
`name.pinWidthRem`(22)과 반드시 같은 값이어야 한다"*고 적고 있다. 그 필드가 사라지므로 문장을
바꾼다 — **폭 자체(`width: 22rem`)는 남긴다.** 고정된 열의 폭이 확정돼야 가로 스크롤 중에 흔들리지
않는다(그 이유만 주석에 남긴다).

### 3-4. `frontend/src/features/wbs/WbsTreeColumns.spec.ts`

`:241-251`의 `left`가 `0`/`5.5`인지 보는 단정을 **지운다**(2-5). 남기고 강화할 것:

- `pin: 'code'` → `th.code`/`td.code`에 `position: sticky`, `.mode`에는 없다 (`:231-238`, 그대로)
- `pin: 'name'` → `.code`와 `.col-name` 둘 다 `sticky`다 (숫자 단정만 제거)
- `hidden: ['code'], pin: 'name'` → `.col-name`이 `sticky`다 (`:253-260`에서 `left === 0` 단정 제거)
- **새로**: `pin: 'name'`일 때 `pinned-edge` 클래스가 `.col-name`에**만** 붙는다(고정 영역의 끝이
  하나여야 한다)

---

## 4. 건드리지 않는 것

| 무엇 | 이유 |
|---|---|
| `.code`·`.pin-name .col-name`의 `width` | 폭 자체는 필요하다. 그 값을 **복사해 두는 것**이 문제였다 |
| `th, td`의 `padding`(`:1232-1241`) | 줄이면 증상이 작아 보이지만 원인은 그대로다. 표 전체의 여백을 이 버그 때문에 바꾸지 않는다 |
| `box-sizing` | 전역에 `border-box`를 넣는 것은 이 저장소 모든 화면의 레이아웃을 건드린다. `table-layout: auto`인 한 어차피 해결되지 않는다(2-1) |
| `table-layout: auto`(`:1227-1230`) | `fixed`로 바꾸면 모든 열에 폭을 줘야 하고 업무명의 가변 폭을 잃는다 |
| `measure()`의 `--head-h`·`maxHeight` 계산 | 이미 같은 원리로 옳게 돼 있다. 폭도 그 방식에 맞추는 것이 이 작업이다 |
| `thead th`의 sticky·배경·`box-shadow`(`:1256-1266`) | 세로 고정은 정상이다. 틈은 가로 오프셋 때문이다 |
| `.pinned`의 배경 선택(`--bg` vs `--surface-alt`) | `:1425-1442`의 주석이 왜 그런지 설명한다. 고정 열이 밝은 세로 띠로 보이지 않게 한 결정이다 |
| z-index 층 셋(1 / 2 / 3) | 배치 문제가 아니라 폭 문제다 |
| `WbsColumnSettings.vue`·`wbsColumnPrefs.ts` | 설정은 옳게 저장되고 있다 |

---

## 5. 확인 방법

```bash
cd frontend
npm test
npm run build
```

수동 확인 — **가로 스크롤이 생기도록 창을 좁히거나 열을 다 켠 상태**에서:

1. `열 설정 → 고정: WBS` → 가로로 스크롤한다. 코드 열이 왼쪽에 남고, **그 오른쪽 경계선 바로 옆에
   틈이 없다**(스크롤되는 셀이 비쳐 보이면 실패)
2. `고정: WBS + 업무명` → 두 열이 **딱 붙어** 남는다. 업무명이 코드 열을 덮지 않고, 업무명 오른쪽
   끝에도 틈이 없다
3. 그 상태로 **세로로도 스크롤**한다 → 머리글의 고정 칸(왼쪽 위)으로 본문 행이 통과해 보이지 않는다
4. 좌우로 여러 번 빠르게 스크롤한다("왔다갔다") → 어느 위치에서도 틈이 생기지 않는다
5. 브라우저 확대/축소를 90%·110%·150%로 바꾼다 → 여전히 틈이 없다(rem 상수였다면 여기서 깨진다)
6. **WBS 코드를 숨기고** 업무명만 고정한다 → 업무명이 왼쪽 끝(0)에 붙는다
7. 고정을 켠 채 **열을 하나 숨겼다 켠다** → 오프셋이 즉시 따라온다(3-3-a의 새 watch가 없으면 여기서
   한 박자 어긋난다)
8. 고정을 켠 채 **필터 행을 열고 닫는다** → 필터 행의 고정 칸도 같은 위치에 선다
9. 코드가 긴 항목(`1.10.12` 등)이 생기도록 하위를 여러 개 추가한다 → 코드 열이 넓어져도 업무명이
   그만큼 밀려 붙는다(상수였다면 여기서 겹친다)
10. 고정 상태에서 행을 **선택**한다 → 고정된 두 칸도 선택 배경으로 바뀐다
11. 고정 상태에서 행을 **드래그**한다 → 드롭 위치 강조가 고정 칸에서도 보인다
12. 고정된 마지막 열의 **오른쪽 경계선이 셀과 함께 움직인다**(원래 자리에 남아 있으면 3-3-c 실패)
13. 머리글의 고정 칸에 **아래 선이 그대로 있다**(3-3-c의 `box-shadow` 두 개를 쉼표로 잇지 않으면
    사라진다)
14. 라이트·다크 양쪽에서 1~13을 다시 본다
15. 새로 고친다 → 저장된 고정 단계가 그대로 복원되고 오프셋도 맞다

---

## 6. 이번에 하지 않는 것 (후속 후보)

- **`ResizeObserver`로 열 폭을 감시.** 지금은 `resize` + 트리 변경 + 열 설정 변경으로 재측정한다.
  글꼴이 늦게 로드되는 경우까지 덮으려면 관찰자가 필요하지만, 그 경우가 실제로 보이면 그때 넣는다
- **임의 열 고정.** 측정 방식으로 바뀌면 미연속 고정의 기술적 장벽은 사라지지만, 고정된 열이 표
  가운데에 떠 보이는 문제는 그대로다(`wbs-tree-columns` 2-3의 결정)
- **`box-sizing: border-box` 전역 도입.** 이 버그와 무관하게 검토할 가치는 있으나 모든 화면의
  레이아웃을 건드리는 별건이다
- **다른 표의 고정 열**(RAID·Backlog). 이 화면에서 검증한 뒤 같은 방식으로 옮긴다 — 옮길 때
  **폭 상수를 복사하지 않는 것**이 이 작업의 교훈이다
