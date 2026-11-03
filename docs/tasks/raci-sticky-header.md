# RACI 매트릭스 머리글 고정 (raci-sticky-header)

대상 체크아웃: `D:\Git\ScheduleMgr` (main `aa6ec3c`). 아래 줄번호는 모두 이 커밋 기준입니다.

## 1. 해결할 문제

RACI 화면에서 세로로 스크롤하면 **페이지 전체가 올라가서**, 열 머리글(구성원 이름·직책)이 화면 위로
사라집니다. 업무가 많은 프로젝트에서는 아래쪽 행의 R·A·C·I 글자가 누구 열인지 알 수 없게 되어,
매트릭스를 읽으려면 위아래로 계속 오가야 합니다.

| 계층 | 지금 상태 |
|---|---|
| 가로 스크롤 | 됩니다. `.matrix-wrap { overflow-x: auto }`([`RaciMatrix.vue:251-255`](../../frontend/src/features/raci/RaciMatrix.vue)) |
| 업무 열 고정(가로) | 됩니다. `.task-col { position: sticky; left: 0 }`(`:284-292`) |
| 머리글 고정(세로) | **안 됩니다.** `thead th`(`:271-278`)에 sticky가 없습니다 |
| 세로 스크롤 주체 | **페이지 전체**입니다. `.matrix-wrap`에 높이 제한이 없습니다 |

**`thead th`에 `position: sticky; top: 0`만 붙여서는 해결되지 않습니다.** `overflow-x: auto`가
있으면 `overflow-y`도 `auto`로 계산되어 `.matrix-wrap`이 sticky의 기준 스크롤 상자가 됩니다.
그런데 이 상자에는 높이 제한이 없어 세로로 스크롤되지 않으므로, 머리글은 "고정"되어 있어도
상자와 함께 페이지 밖으로 밀려 나갑니다. **상자 자체를 세로로 스크롤되게 만들어야 합니다.**

## 2. 결정된 방향

**WBS 트리가 이미 쓰는 방식을 그대로 가져옵니다**([`WbsTree.vue:240-320`](../../frontend/src/features/wbs/WbsTree.vue)).
표를 화면 높이에 맞춘 스크롤 상자로 만들고, 머리글을 그 상자의 위쪽에 고정합니다.

- **높이는 CSS 상수가 아니라 측정값입니다.** 계산식은
  `max-height = window.innerHeight − wrap.getBoundingClientRect().top − 16px`이고, **최소 320px**을
  보장합니다(`WbsTree.vue:296-299`와 같은 값). 표 위에 놓이는 것이 화면 상태에 따라 달라지기
  때문입니다 — 커밋 배너, 규칙 위반 줄(`RaciView.vue:111`), 정상 표시 줄(`:131`), 프로젝트 선택.
  자기 `top`을 재면 위에 무엇이 있는지 이 코드가 알 필요가 없습니다.
- **측정 함수는 WbsTree와 공유하지 않고 RaciMatrix에 따로 둡니다.** WbsTree의 `measure()`는
  머리글 높이·고정 열 오프셋까지 함께 재는 함수라, 뽑아내려면 WBS 쪽을 손대야 합니다. 이번 범위는
  RACI뿐입니다. 공통 컴포저블로 빼는 것은 6절 후속 후보로 남깁니다.

### 하지 않기로 한 것

| 안 하는 것 | 이유 |
|---|---|
| `max-height: calc(100vh - Nrem)` 같은 CSS 상수 | 표 위의 내용 높이가 화면 상태마다 다릅니다. 위반 줄이 여러 줄로 늘어나면 상수가 틀립니다 |
| `ResizeObserver`로 위쪽 요소 추적 | WbsTree와 같은 수준(창 크기 변경 + 데이터 변경 시 재측정)으로 충분합니다. 방식을 맞춰 두어야 나중에 공통화할 수 있습니다 |
| 페이지 전체를 고정 레이아웃으로 바꾸기 | 다른 화면과 구조가 달라집니다 |

## 3. 작업

전부 [`frontend/src/features/raci/RaciMatrix.vue`](../../frontend/src/features/raci/RaciMatrix.vue) 한 파일입니다.

### 3-1. 스크립트 — 높이 측정

- `const wrap = ref<HTMLElement | null>(null)`, `const maxHeight = ref<string | null>(null)`,
  `const headHeight = ref<number | null>(null)`, `BOTTOM_GAP = 16`
- `measure()`
  - 위 계산식으로 `maxHeight`를 정합니다.
  - 머리글 행 높이를 재어 `headHeight`에 넣습니다(3-3의 `scroll-padding-top`에 씁니다).
- **측정 시점 — 이것을 빠뜨리면 동작하지 않습니다.**
  - `.matrix-wrap`은 `v-else` 아래(`:119`)에 있어서, **컴포넌트가 마운트될 때는 아직 없을 수
    있습니다**(데이터를 불러오는 중이거나 구성원이 없을 때). `onMounted`에서 한 번 재는 것으로는
    부족하므로 `watch(wrap, (el) => { if (el) measure() })`로 **요소가 나타나는 시점에** 잽니다.
  - `watch(() => props.data, () => nextTick(measure))`도 둡니다. 배정을 바꾸면 RaciView의 규칙
    위반 줄이 생기거나 사라져 표의 `top`이 움직입니다(`WbsTree.vue:312-315`와 같은 이유).
  - `window` `resize`에 `measure`를 걸고, **`onUnmounted`에서 반드시 뗍니다.** WbsTree에서 해제
    코드를 확인하고 같은 방식으로 맞춥니다.

### 3-2. 템플릿

- `:119` — `<div v-else ref="wrap" class="matrix-wrap" :style="{ maxHeight: maxHeight ?? undefined, '--head-h': headHeight ? headHeight + 'px' : undefined }">`
- 머리글 행(`:122`의 `<tr>`)에 높이를 잴 `ref`를 붙입니다.

### 3-3. 스타일

- **`.matrix-wrap`(`:251`)**
  - `overflow-x: auto`를 `overflow: auto`로 바꿉니다.
  - `overscroll-behavior: contain`을 추가합니다. 표 끝에 닿았을 때 스크롤이 페이지로 넘어가 화면이
    튀지 않게 합니다(`WbsTree.vue` `.tree-scroll`과 같음).
  - `scroll-padding-top: var(--head-h, 0px)`을 추가합니다. 근거는 다음과 같습니다.
    - 키보드로 행을 옮기면 [`useRowSelection.ts:83`](../../frontend/src/shared/useRowSelection.ts)이
      `scrollIntoView({ block: 'nearest' })`를 부릅니다.
    - 머리글이 고정되면, 위로 이동할 때 선택된 행이 **고정된 머리글 밑에 가려진 채로** 멈춥니다.
    - `scroll-padding-top`을 주면 브라우저가 머리글 높이만큼 비워 두고 스크롤합니다.
- **`thead th`(`:271`)**
  - `position: sticky; top: 0; z-index: 2;`
  - **`border-bottom` 대신 `box-shadow: inset 0 -1px 0 var(--border)`** 를 씁니다.
    `border-collapse: collapse`에서는 경계선을 표가 그리므로 sticky 셀과 함께 움직이지 않습니다
    (`WbsTree.vue:1466-1474`와 같은 이유).
  - 배경 `var(--surface-alt)`은 이미 불투명이므로 그대로 둡니다. 투명하면 스크롤된 행이 비쳐 보입니다.
- **`thead .task-col`(`:294`)** — 왼쪽 위 모서리 칸은 **가로·세로 양쪽으로 고정**됩니다.
  - `z-index: 3`을 줍니다. 그래야 가로 스크롤 때 머리글 칸(2) 위에, 세로 스크롤 때 본문 업무 칸(1)
    위에 그려집니다.
  - 경계선은 아래 선과 오른쪽 선을 **둘 다** 그립니다:
    `box-shadow: inset 0 -1px 0 var(--border), inset -1px 0 0 var(--border)`
    (`WbsTree.vue:1676-1680`과 같은 이유 — 하나만 쓰면 다른 쪽 선이 사라집니다).
  - `.task-col`(`:284-292`)이 가진 `border-right`가 sticky 상태에서 함께 움직이는지도 이 기회에
    확인합니다. 움직이지 않으면 본문 업무 칸도 `box-shadow: inset -1px 0 0 var(--border)`로 바꿉니다.
- **`button.letter::after`(히트 영역, `:393` `inset: -0.1rem`)** 는 바꾸지 않습니다. 다만 이제
  `.matrix-wrap`이 세로로도 넘친 부분을 잘라내므로, **첫 행 글자의 위쪽 히트 영역이 고정된 머리글
  밑으로 들어갑니다.** 머리글이 위에 그려지므로 클릭은 머리글로 갑니다. 0.1rem 차이라 기준
  (24×24px)에 영향은 없을 것으로 보지만, 수동 확인 목록에 넣었습니다.

## 4. 건드리지 않는 것

| 대상 | 이유 |
|---|---|
| `WbsTree.vue` | 같은 기법의 원본입니다. 공통화는 후속 과제(6절) |
| `RaciView.vue` | 높이를 자기 `top`으로 재므로, 위에 무엇이 있는지 뷰가 알려 줄 필요가 없습니다 |
| `style.css`의 `.table-scroll` | 전역 유틸리티입니다. 세로 스크롤을 넣으면 모든 표가 바뀝니다 |
| `button.letter::after` 규칙 | CLAUDE.md "컨트롤 층" 절의 사고 기록 — 고칠 이유가 생기지 않는 한 손대지 않습니다 |

## 5. 확인 방법

```bash
cd frontend && npm test && npm run build
```

**자동 테스트**(`RaciMatrix` 컴포넌트 테스트가 있으면 거기에, 없으면 새로 만들지 않아도 됩니다)
- `happy-dom`에는 레이아웃이 없어 높이 값은 단정할 수 없습니다(CLAUDE.md "컴포넌트 테스트").
- **`maxHeight`의 px 값이나 sticky 동작을 단정하는 테스트, 소스 문자열을 grep하는 테스트는
  만들지 마세요.** 할 수 있는 것은 "데이터가 로드된 뒤 `.matrix-wrap`에 `max-height` 인라인
  스타일이 붙는다(측정이 호출됐다)" 정도입니다. `getBoundingClientRect`가 0을 돌려주므로 값은
  `innerHeight − 16` 또는 하한 320px이 됩니다.

**수동 확인 — 브라우저에서 반드시 확인합니다.** 이 저장소의 CSS 버그는 추론으로 고치면 틀린
전례가 있습니다. 증상이 *사라지지 않고 줄어들기만* 하면 모델이 틀린 것입니다.
- 업무가 많은(화면보다 긴) 프로젝트에서 표를 **끝까지가 아니라 중간까지만** 스크롤한 상태로
  구성원 머리글이 표 상단에 붙어 있는지 확인합니다. 끝 상태에서는 틀린 값도 맞아 보일 수 있습니다.
- 페이지 자체는 더 이상 세로로 스크롤되지 않거나 거의 스크롤되지 않는지, 그리고 표 바닥이 창 아래
  16px 위에서 끝나는지 확인합니다. 콘솔에서
  `innerHeight - document.querySelector('.matrix-wrap').getBoundingClientRect().bottom`이 약 16이어야 합니다.
- 가로로도 스크롤한 상태에서 왼쪽 위 `업무` 칸이 구성원 머리글과 업무 칸 **둘 다의 위에** 그려지는지,
  아래·오른쪽 경계선이 같이 따라오는지 확인합니다.
- 행을 선택하고 ↑/↓로 위아래 끝까지 옮겼을 때 선택 행이 **고정된 머리글 밑에 가려지지 않는지** 확인합니다.
- 배정을 하나 바꿔 규칙 위반 줄이 생기거나 사라질 때 표 높이가 다시 맞춰지는지 확인합니다.
- 창 크기를 줄였다 늘렸을 때, 그리고 아주 낮은 창에서 320px 하한이 지켜지는지 확인합니다.
- 첫 행의 R·A·C·I 글자 위쪽 가장자리를 눌러도 배정이 토글되는지 확인합니다(히트 영역).
- 콘솔에서 `document.elementFromPoint(60, 20)`이 상단 메뉴를 가리키는지 확인합니다(오버레이 사고 회귀).
- 라이트·다크 두 테마에서 머리글 배경이 불투명한지, 스크롤된 행이 비쳐 보이지 않는지 확인합니다.
- 커밋 조회 모드(배너가 표 위에 하나 더 붙음)에서도 높이가 맞는지 확인합니다.

## 6. 이번에 하지 않는 것 (후속 후보)

- **WbsTree와 RaciMatrix의 높이 측정을 공통 컴포저블로 빼기**(예: `shared/useViewportFitHeight.ts`).
  같은 기법이 두 벌이 되므로, 세 번째 화면(RAID·Backlog 등)에 필요해지면 그때 묶습니다.
- **WBS 트리의 같은 잠재 문제 확인.** `WbsTree.vue:453`도 `scrollIntoView({ block: 'nearest' })`를
  쓰는데 `scroll-padding-top`이 없어, ↑로 이동하면 선택 행이 고정 머리글(+ 필터 행) 밑에 가려질
  가능성이 있습니다. **확인하지 않았습니다** — 브라우저에서 확인 후 필요하면 같은 한 줄을 넣습니다.
- RAID·Backlog 등 다른 긴 표의 머리글 고정.
