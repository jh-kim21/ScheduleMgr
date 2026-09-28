# WBS 트리 — 행 액션을 툴바로, 표를 화면에 붙는 스크롤 패널로

대상 체크아웃: `D:\git\ScheduleMgr` (main `091d4a6` "체크포인트·진척 탭의 UX 세 곳을 고친다")
모든 줄번호는 그 커밋에서 확인한 값이다.

사용자 요구 다섯 건 중 둘이다. 나머지 셋은 별도 지시서로 나눠 두었고 **이 지시서가 먼저다** —
툴바가 생겨야 [열 설정]·[필터] 버튼이 놓일 자리가 있고, 열 수가 11 → 10으로 줄어야 열 표시·숨김이
그 위에 얹힌다.

| 순서 | 지시서 | 요구 |
|---|---|---|
| **1 (이 문서)** | `wbs-tree-toolbar` | 행 액션 → 상단 툴바 / 수평 스크롤바 위치 |
| 2 | `wbs-tree-columns` | 고정 열 / 열 표시·숨김 |
| 3 | `wbs-tree-filter` | 열별 값 필터 |

---

## 1. 해결할 문제

### 1-1. 행마다 버튼 세 개

`WbsTree.vue:639-669`의 마지막 열에 행마다 [하위][수정][삭제]가 붙어 있다. 열이 11개인 표의
**맨 오른쪽**이라, 가로로 스크롤하지 않으면 닿지 않는다. 행이 30개면 버튼이 90개고, 세 개 중
둘(`수정`·`삭제`)은 어느 행에서나 판정이 같다(`readOnly`뿐).

선택은 이미 있다 — `selectedId`(`:180-182`)와 클릭·방향키 이동(`:233-300`)이 동작하고, 선택된 행은
배경과 왼쪽 강조선으로 표시된다(`:774-780`). **동작을 툴바로 옮기는 데 필요한 상태가 이미 전부
있다.**

### 1-2. 수평 스크롤바가 표 맨 아래에 있다

| 계층 | 지금 |
|---|---|
| 전역 유틸리티 | `style.css:341-349` `.table-scroll { overflow-x: auto }` — 높이 상한 없음 |
| WBS 화면 | `WbsTree.vue:443`에서 그 클래스로 표를 감싼다 |
| 결과 | 페이지가 세로로 스크롤되고, 가로 스크롤바는 **표의 맨 아래**에 남는다 |

행이 화면보다 많으면 가로로 스크롤하려고 페이지 끝까지 내려가야 하고, 그러면 머리글이 화면에서
사라져 어느 열을 보고 있는지 알 수 없다. 열 11개 중 오른쪽 절반(진척·체크포인트·담당자·분야)은
항상 가로 스크롤 뒤에 있다.

---

## 2. 결정된 방향

### 2-1. 툴바는 `WbsTree`가 갖는다

`selectedId`가 거기 있다. 툴바를 `WbsView`에 두면 선택을 prop/emit으로 끌어올려야 하는데, 얻는
것이 없다.

**페이지 머리의 [＋ 파일에서 가져오기]·[＋ 최상위 항목 추가](`WbsView.vue:213-231`)는 그대로
둔다.** 선택과 무관한 프로젝트 레벨 동작이다. 두 줄로 나뉘는 것이 맞다 — 위는 프로젝트, 아래는
이 표와 선택된 행.

**선택된 행의 코드·이름을 툴바에 적는다.** 지금은 버튼이 그 행 안에 있어서 무엇을 지우는지 물을
필요가 없었다. 툴바로 옮기면 그 맥락이 사라지므로 `선택: 1.2.1 화면 설계`를 함께 보여준다 —
이것이 이 변경에서 유일하게 새로 생기는 위험이고, 기존 삭제 확인 대화상자(`WbsView.vue`의
`handleRemove`)는 그대로 남는다.

### 2-2. 표를 뷰포트 높이 스크롤 패널로 만들고 머리글을 고정한다 (사용자 결정)

스크롤바를 따로 만들어 `scrollLeft`를 동기화하는 방식(스티키 프록시)이 아니라, **표 자체를 화면
높이에 맞춘 스크롤 영역으로** 만든다. 그러면 가로 스크롤바가 항상 그 영역의 아래 끝 = 화면 안에
있고, **머리글 고정이 공짜로 따라온다** — 동기화 상태를 들고 있을 필요가 없어 엇갈릴 수 없다.

`SprintAssignTable.vue:265-273`이 이미 같은 방식으로 `thead`를 고정한다(`position: sticky; top: 0`).

**`.table-scroll`은 고치지 않는다.** RAID·Backlog·RACI·진척 탭이 모두 쓰는 전역 유틸리티다
(`style.css:341-349`). 높이 상한을 거기 넣으면 모든 표가 내부 스크롤 패널이 된다. WBS 전용
클래스를 하나 더 얹어(`.tree-scroll`) opt-in한다.

**높이는 한 번만 잰다.** 위쪽에 무엇이 있는지(커밋 배너·지연 경고줄·프로젝트 선택)가 상황에 따라
달라서 CSS 상수로는 못 맞춘다. 대신 **패널 전체를 flex column으로 두고 바깥 래퍼의 top만 재면**
브라우저가 나머지를 나눈다 — 드롭존·설명문의 높이를 코드가 알 필요가 없다.

```
<div class="tree-pane" :style="{ maxHeight }">   ← 측정값 하나만 받는다 (flex column)
  <div class="table-scroll tree-scroll"> … 표 … </div>   ← flex: 1 1 auto; min-height: 0
  <div class="root-dropzone"> … </div>                    ← flex: none
  <p class="legend"> … </p>                               ← flex: none
</div>
```

`maxHeight = window.innerHeight - pane.getBoundingClientRect().top - 여백`. `min-height: 0`을
빼먹으면 flex 아이템이 줄어들지 않아 스크롤 영역이 내용 높이만큼 늘어난다(= 지금과 같아진다).

---

## 3. 작업

### 3-1. `frontend/src/features/wbs/wbsToolbar.ts` (새 파일)

버튼 세 개의 활성/비활성 판정을 순수 함수로 둔다. 지금 행 버튼이 쓰는 판정을 그대로 옮기되
"선택 없음"이 더해진다.

```ts
export interface ToolbarState {
  canAddChild: boolean
  canEdit: boolean
  canRemove: boolean
  /** 비활성일 때 버튼의 title — 이유를 말하지 않으면 왜 못 누르는지 알 수 없다. */
  addChildHint: string | null
}
export function toolbarState(selected: WbsNode | null, readOnly: boolean): ToolbarState
```

판정(현재 `WbsTree.vue:641-668`의 조건을 그대로 옮긴다):

| 버튼 | 비활성 조건 | title |
|---|---|---|
| 하위 | `readOnly` · 선택 없음 · `nodeType === 'WORK_PACKAGE'` | `readOnly`면 `READONLY_HINT`, Work Package면 지금 쓰는 문구 그대로(`'Work Package에는 하위 항목을 둘 수 없습니다. 수정에서 구분을 Summary로 바꾸세요.'`), 선택 없으면 `'행을 먼저 고르세요'` |
| 수정 | `readOnly` · 선택 없음 | 같은 규칙 |
| 삭제 | `readOnly` · 선택 없음 | 같은 규칙 |

`READONLY_HINT`는 `WbsTree.vue:46`에 이미 있다 — 문구를 새로 짓지 말고 그것을 쓴다(화면 전체
공통 문구다).

spec `wbsToolbar.spec.ts`: 선택 없음 / Summary 선택 / Work Package 선택 / `readOnly` 네 경우.

### 3-2. `frontend/src/features/wbs/WbsTree.vue`

**(a) 툴바를 표 위에 추가한다.** `:441`의 `<template v-else>` 바로 안쪽, `.tree-pane` 바깥.

```
<div class="tree-toolbar">
  <button :disabled="!state.canAddChild" :title="…">하위 추가</button>
  <button :disabled="!state.canEdit">수정</button>
  <button class="danger" :disabled="!state.canRemove">삭제</button>
  <span class="selected-label">…</span>   ← 선택: {code} {name} / 없으면 "행을 고르세요"
</div>
```

- `emit('addChild' | 'edit' | 'remove', selectedNode)` — **emit 시그니처는 그대로다**(`:49-54`).
  `WbsView`는 이 변경을 모른다.
- 선택된 노드는 `selectedId`로 트리에서 찾는다. `rows`에서 찾지 말 것 — 조상이 접혀 있으면
  선택된 행이 `rows`에 없다(`resolveVisibleSelection`(`:222-232`)이 그 상태를 전제로 만들어져
  있다). `nodeExists`(`:186-192`) 옆에 `findNode(tree, id)`를 두거나 `wbsTree.ts`에 추가한다.
- 버튼 모양은 지금 행 액션(`:1075-1094` `.actions button`)의 것을 옮겨 쓴다. 다만 툴바는 행
  안이 아니므로 크기를 한 단계 키운다 — `WbsView.vue:317-336`의 `.add` 버튼과 같은 계열로
  맞춘다. **새 색 토큰을 만들지 않는다**(`--danger`·`--border-input`·`--disabled-bg`/`--disabled-fg`가
  이미 있다).
- 비활성 버튼은 배경과 글자색을 **함께** 지정한다(`--disabled-bg`/`--disabled-fg`) — 배경만
  옅게 하면 다크에서 글자가 묻힌다(CLAUDE.md 다크 모드 규칙).

**(b) 행의 액션 열을 없앤다.**

| 줄 | 할 일 |
|---|---|
| `:457` | `<th></th>` (빈 머리글) 삭제 → `thead th`가 **10개**가 된다 |
| `:639-669` | `<td class="actions">` 전체 삭제 |
| `:682` | `colspan="11"` → `colspan="10"` |
| `:1070-1094` | `.actions` / `.actions button` / `.actions button.danger` / `.actions button:disabled` 스타일 삭제 — 툴바로 옮긴 만큼만 남긴다 |

**`colspan`은 이 표에서 두 번 사고가 난 자리다**(완료된 지시서 `done/wbs-tree-improvements.md`가
함정 목록에 두 번 적어 뒀다: 9 → 11로 늘 때마다 체크포인트 서랍이 표를 깨뜨렸다). 다행히
`WbsTreeColumns.spec.ts:81-89`가 `thead th` 개수와 서랍의 `colspan`을 비교하므로 **숫자를 안
고치면 테스트가 잡는다.** 테스트를 고쳐서 통과시키지 말 것.

**(c) 더블클릭 가드 주석을 고친다.** `:314-317`이 "`하위/수정/삭제`" 버튼을 이유로 들고 있는데
그 버튼이 사라진다. 가드 자체(`closest('button, a')`)는 **지우지 말 것** — 토글(▼/▶)·체크포인트
배지·Backlog 링크·담당자 링크가 여전히 행 안에 있다.

**(d) 스크롤 패널.** `:443`의 `<div class="table-scroll">`를 위 2-2의 3단 구조로 감싼다.
`root-dropzone`(`:697-705`)과 `legend`(`:707-710`)를 패널 안으로 들여 flex 아이템으로 만든다 —
표만 스크롤되고 이 둘은 늘 패널 아래에 붙는다.

```ts
const pane = ref<HTMLElement | null>(null)
const maxHeight = ref<string | null>(null)
const BOTTOM_GAP = 16

function measure() {
  const el = pane.value
  if (!el) return
  const available = window.innerHeight - el.getBoundingClientRect().top - BOTTOM_GAP
  maxHeight.value = `${Math.max(320, available)}px`   // 좁은 창에서 패널이 사라지지 않게 하한
}
```

- `onMounted`에서 한 번, `window.resize`에 한 번 건다. `onUnmounted`에서 **반드시 해제한다** —
  이 파일에는 이미 `onUnmounted`가 있다(`:1`의 import, 하이라이트 타이머 정리용).
- `watch(() => props.tree, () => nextTick(measure))` — 지연 경고줄(`WbsView.vue:264-275`)이
  생기거나 사라지면 패널의 top이 움직인다.
- 스크롤 영역에 `overscroll-behavior: contain`을 준다. `.table-scroll`에는 이미 가로 방향만
  있다(`style.css:344`) — 세로도 막아 패널 끝에서 페이지가 따라 움직이지 않게 한다.

**(e) 머리글 고정.**

```css
thead th {
  position: sticky;
  top: 0;
  z-index: 2;
  background: var(--surface);   /* ← 반드시 */
}
```

**지금 `th`에는 배경이 없다**(`:730-734`). 배경 없이 sticky로 만들면 스크롤된 행이 머리글을
통과해 보인다. `--surface`가 이 표의 실제 배경과 같은지 **라이트·다크 양쪽에서 확인**하고, 다르면
`RaciMatrix.vue:270-277`처럼 `--surface-alt`를 쓴다(그 파일이 같은 문제를 이미 풀어 뒀다).

머리글 아래 경계선은 지금 `th, td`의 `border-bottom`(`:718-728`)인데, sticky 요소의 border는
스크롤 시 겹쳐 보일 수 있다. 어긋나면 `box-shadow: inset 0 -1px 0 var(--border)`로 바꾼다.

**(f) 키보드 이동은 그대로 동작한다.** `watch(selectedId, …)`의 `scrollIntoView({ block: 'nearest' })`
(`:206-212`)와 `revealFocused`의 `smooth`/`center`는 스크롤 컨테이너 안에서도 같은 뜻으로 동작한다.
**확인만 하고 고치지 말 것.**

### 3-3. `frontend/src/features/wbs/WbsTreeToolbar.spec.ts` (새 파일)

`WbsTreeColumns.spec.ts`가 이미 `WbsTree`를 마운트하는 선례다(`// @vitest-environment happy-dom`
docblock을 파일 맨 위에 적는다 — 전역 DOM 환경을 켜지 않는 것이 이 저장소의 규칙이다).

- 선택이 없으면 세 버튼이 모두 `disabled`다
- 행을 클릭하면 [수정]·[삭제]가 활성되고, 버튼이 그 행의 노드로 emit한다
- Work Package 행을 고르면 [하위 추가]만 `disabled`이고 title이 이유를 말한다
- `readOnly`면 선택해도 세 버튼이 모두 `disabled`다
- **행 안에는 액션 버튼이 없다** — `thead th`가 10개다
- 조상이 접힌 상태에서 선택한 행을 고른 뒤 접어도 툴바가 그 행을 계속 가리킨다(3-2-a의
  `rows`가 아니라 트리에서 찾는다는 결정을 고정한다)

---

## 4. 건드리지 않는 것

| 무엇 | 이유 |
|---|---|
| `style.css:341-349` `.table-scroll` | 다섯 화면이 쓰는 전역 유틸리티다. WBS 전용 클래스로 얹는다 |
| `WbsTree`의 `emit` 시그니처(`:49-54`) | `WbsView`는 이 변경을 몰라야 한다 |
| `WbsView.vue:213-231` 페이지 머리 버튼 | 선택과 무관한 프로젝트 레벨 동작이다 |
| `WbsView.vue:128`·`treeVisibility.ts` | `091d4a6`이 방금 고친 자리다. 갱신 중 트리를 감추지 않는 규칙은 그대로 유효하고, 패널로 감싸는 것과 무관하다 |
| `onRowDblClick`의 `closest('button, a')` 가드(`:314-320`) | 토글·체크포인트 배지·링크가 여전히 행 안에 있다 |
| 드래그·드롭 전체(`:330-435`, `resolveDropPosition`) | 이 지시서는 행을 지우지 않는다. 필터가 들어올 때 잠근다(3번 지시서) |
| `WbsTreeColumns.spec.ts`의 `colspan` 테스트 | **통과시키려고 테스트를 고치지 말 것.** 숫자를 고치면 통과한다 |
| `selectedId`·방향키·`resolveVisibleSelection` | 툴바가 읽기만 한다 |

---

## 5. 확인 방법

```bash
cd frontend
npm test
npm run build
```

수동 확인 (`npm run dev` + 백엔드 `bootRun`, 행이 20개 이상인 프로젝트):

1. 행을 클릭한다 → 툴바의 [수정]·[삭제]가 활성되고 `선택: 1.2 설계`가 보인다
2. [수정] → 그 행의 편집 대화상자가 열린다. [하위 추가] → 그 행 아래에 추가하는 폼이 열린다
3. [삭제] → 확인 문구에 그 행의 코드·이름이 나온다(하위가 있으면 경고도)
4. Work Package 행을 고른다 → [하위 추가]만 비활성이고 마우스를 올리면 이유가 보인다
5. 아무 행도 고르지 않은 상태(첫 진입, 또는 트리가 갱신되어 선택이 무효화된 뒤) → 세 버튼 비활성
6. 행을 더블클릭 → 편집이 열린다. 토글(▼)·체크포인트 배지·Backlog 링크를 **더블클릭** → 편집이
   열리지 **않는다**
7. 창을 좁혀 가로 스크롤을 만든다 → **스크롤바가 화면 안에 있다.** 세로로 스크롤해도 머리글이
   남고, 가로로 스크롤해도 스크롤바 위치가 그대로다
8. 세로로 끝까지 스크롤 → 드롭존과 설명문이 패널 아래에 붙어 있고 화면 밖으로 밀려나지 않는다
9. 창 높이를 바꾼다 → 패널 높이가 따라온다. 지연 경고줄이 있는 프로젝트와 없는 프로젝트를 번갈아
   골라도 패널이 화면을 넘지 않는다
10. 체크포인트를 펼친다 → 서랍이 표 폭 전체를 먹고 열이 어긋나지 않는다
11. 방향키 ↑↓←→ → 선택이 움직이고, 화면 밖 행을 고르면 패널이 그 행까지 스크롤된다
12. 커밋 조회 모드 → 툴바 세 버튼이 모두 비활성이고 title이 이유를 말한다
13. 라이트·다크 양쪽에서 머리글 배경이 불투명한지 확인한다(스크롤된 행이 비쳐 보이면 안 된다)

---

## 6. 이번에 하지 않는 것 (후속 후보)

- **[열 설정]·[필터] 버튼** — 툴바에 자리만 만들어 두고 버튼은 2·3번 지시서에서 붙인다
- **키보드 단축키**(Enter = 수정, Delete = 삭제). 툴바로 옮기면서 마우스 이동이 늘었으니 자연스러운
  후속이지만, 표 안에서 Delete가 바로 삭제로 이어지는 것은 확인 대화상자가 있어도 위험해 따로 본다
- **다중 선택.** 툴바는 다중 선택의 전제 조건이지만(행 버튼으로는 애초에 불가능했다) 삭제·이동의
  의미를 새로 정해야 한다
- **툴바 자체를 sticky로** — 패널이 화면 높이에 맞춰지면 툴바는 거의 항상 화면에 있다. 페이지가
  조금이라도 스크롤되는 경우가 거슬리면 그때 본다
- **다른 화면의 표를 같은 패널 구조로** — RAID·Backlog도 열이 많다. 이 화면에서 검증한 뒤 옮긴다
