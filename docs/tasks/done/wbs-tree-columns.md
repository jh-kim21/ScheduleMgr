# WBS 트리 — 열 표시·숨김과 고정 열

대상 체크아웃: `D:\git\ScheduleMgr` (main `091d4a6` "체크포인트·진척 탭의 UX 세 곳을 고친다")

> **선행 작업이 있다.** `wbs-tree-toolbar`(요구 1·5)를 먼저 끝내고 커밋한 뒤 시작한다. 그 작업이
> [열 설정] 버튼이 놓일 툴바를 만들고, 액션 열을 없애 열 수를 11 → 10으로 줄인다. 아래에 적힌
> `WbsTree.vue` 줄번호는 `091d4a6` 기준이므로 **그 작업 뒤에는 달라진다** — 줄번호가 아니라
> 선택자·심볼 이름으로 찾을 것. 열 수(10)와 `colspan`은 그 작업의 결과를 전제로 한다.

---

## 1. 해결할 문제

열이 11개(툴바 작업 뒤 10개)다 — WBS · 업무명 · 시작일 · 종료일 · 실행 방식 · 연결 Backlog ·
진척 · 체크포인트 · 담당자 · 분야 (`WbsTree.vue:445-458`).

| 계층 | 지금 |
|---|---|
| 열 정의 | 없다. `<thead>`에 `<th>` 11개가 직접 적혀 있고(`:447-457`) 셀도 `<td>` 11개가 직접 적혀 있다(`:474-669`) |
| 폭 | CSS에 고정값으로 흩어져 있다 — `.code 5.5rem`(`:794`) · `.date 7rem`(`:801`) · `.mode 11rem`(`:807`) · `.backlog 10rem`(`:822`) · `.progress 13rem`(`:897`) · `.checkpoint 8rem`(`:945`) · `.owner 11rem`(`:954`) · `.tags 12rem`(`:990`). **업무명만 폭이 없다**(남는 폭을 먹는다) |
| 고정 | 없다. 가로로 스크롤하면 WBS 코드·업무명이 함께 밀려나 어느 행을 보는지 알 수 없다 |
| 숨김 | 없다. 담당자·분야를 안 쓰는 프로젝트도 그 두 열을 계속 본다 |

가로 스크롤에서 식별 열이 밀려나는 문제는 이 저장소가 이미 한 번 풀었다 —
`RaciMatrix.vue:279-300`이 업무 열을 `position: sticky; left: 0`으로 고정하며 **이 화면과 똑같은
이유**를 주석에 적어 뒀다: *"구성원이 늘어나면 가로로 스크롤되는데, 어느 업무의 행인지 놓치면
매트릭스를 읽을 수 없다."*

---

## 2. 결정된 방향

### 2-1. 열을 데이터로 다루되, 셀 마크업은 일반화하지 않는다

`wbsColumns.ts`에 열 정의를 두고 `<th>`/`<td>` 쌍에 `v-if`를 두른다. **범용 셀 렌더러(슬롯·
컴포넌트 맵)로 바꾸지 않는다** — 열마다 들어 있는 것이 다르다: 들여쓰기 + 토글 + 지연 배지 +
설명(업무명), Backlog 링크, 진척 막대 + 기준 + 불완전 + 인수 대기, 체크포인트 펼침 버튼, RACI
링크 + 상속 표시, 태그 칩 + 보관 배지. 일반화하면 1000줄을 다시 쓰는 일이 되고, 얻는 것은 `v-if`
20개를 줄이는 것뿐이다.

### 2-2. 업무명은 숨길 수 없다

나머지 아홉 개는 숨길 수 있다. 업무명을 숨기면 그 행이 무슨 일인지 알 수 없어진다 — WBS 코드만
남은 표는 읽을 수 없다. 규칙을 하나로 못 박아 두면 "코드도 숨기면?"을 매번 다시 묻지 않는다.

### 2-3. 고정은 앞에서부터 세 단계 (사용자 결정)

`없음 / WBS / WBS + 업무명`.

임의의 열을 고정하게 하면 고정된 열이 표 가운데에 떠 있는 모양이 만들어지고(스크롤되는 열 사이에
낀다), 누적 `left` 오프셋을 런타임에 재야 한다(`ResizeObserver`). 앞에서부터 연속으로 제한하면
**오프셋이 상수로 떨어진다** — `WBS`는 `left: 0`, `업무명`은 `left: 5.5rem`(코드 열 폭).

사용자가 원하는 것("어느 업무의 행인지 놓치지 않는다")은 이 세 단계로 다 답한다.

**업무명을 고정하려면 그 열에 폭이 있어야 한다.** 지금은 가변이라 오프셋을 계산할 수 없다. 고정
상태일 때만 폭을 확정한다(비고정일 때는 지금처럼 남는 폭을 먹는 것이 맞다).

**숨겨진 열은 오프셋에서 빠진다.** WBS 코드를 숨긴 채 업무명을 고정하면 업무명의 `left`는 `0`이다.
이 계산을 컴포넌트에 흘리지 말고 `wbsColumns.ts`가 답한다.

### 2-4. 팝오버를 새로 만들지 않는다 — `ModalDialog`를 쓴다

`ExportMenu.vue`가 461줄인 이유가 팝업 비용이다(teleport, `position: fixed`, 위로 뒤집기, 바깥
`pointerdown`, `Escape`, `prefers-reduced-motion` — `:70-96`, `:124`, `:144-145`). CLAUDE.md도
그 비용을 경고한다: *"팝업이 바깥 클릭·키보드·잘림 처리를 다 요구하는 데 비해 얻는 것이 항목
세 개를 감추는 것뿐"*.

열 설정은 체크박스 열 개 + 라디오 세 개다. 이미 있고 테스트도 있는 `ModalDialog`로 띄운다
(CLAUDE.md 화면 규칙: *"모든 추가·수정 폼은 대화상자"*). 폼 컴포넌트가 자기 `title`을 계산해
넘기고, 패널 크롬은 두지 않는다 — 같은 규칙이다.

### 2-5. 설정은 `localStorage`에 남긴다 (사용자 결정)

키는 `project-flow.wbs-columns` 하나. `stores/theme.ts:26`·`:70`이 같은 방식의 선례다(키
`project-flow.theme`).

**프로젝트별로 나누지 않는다.** 열 구성은 "이 사람이 무엇을 보고 싶은가"이지 프로젝트의 속성이
아니다. 프로젝트마다 다르게 저장하면 프로젝트를 옮길 때마다 열이 바뀌어 더 놀랍다.

**읽기·쓰기를 모두 `try/catch`로 감싼다.** 사생활 보호 모드에서 `localStorage` 접근이 던질 수
있고, 열 설정을 못 읽는 것이 화면을 못 그릴 이유는 아니다(기본값으로 떨어진다).

---

## 3. 작업

### 3-1. `frontend/src/features/wbs/wbsColumns.ts` (새 파일)

열의 단일 정의다. 폭을 여기 적으면 CSS와 두 곳에 같은 값이 생기므로, **폭은 CSS에 두고 여기에는
고정 오프셋 계산에 필요한 것만** 둔다(= 고정 가능한 열의 폭만). 나머지 폭은 지금 CSS 그대로다.

```ts
export type WbsColumnKey =
  | 'code' | 'name' | 'startDate' | 'endDate' | 'mode'
  | 'backlog' | 'progress' | 'checkpoint' | 'owner' | 'tags'

export interface WbsColumn {
  key: WbsColumnKey
  label: string          // 머리글에 쓰는 말 그대로 ('WBS', '업무명', …)
  hideable: boolean      // 'name'만 false
  /** 고정 단계에 참여하는 열의 폭(rem). 고정 오프셋 계산에만 쓴다. */
  pinWidthRem?: number   // code: 5.5, name: (고정 시 폭)
}

export const WBS_COLUMNS: readonly WbsColumn[]   // 화면 순서 그대로

export type PinLevel = 'none' | 'code' | 'name'  // 'name' = WBS + 업무명

export interface WbsColumnPrefs {
  hidden: WbsColumnKey[]
  pin: PinLevel
}

export const DEFAULT_PREFS: WbsColumnPrefs        // { hidden: [], pin: 'none' }

/** 표시할 열 — 순서는 WBS_COLUMNS 그대로. `name`은 hidden에 있어도 남는다. */
export function visibleColumns(prefs: WbsColumnPrefs): WbsColumn[]

/** 고정된 열의 left 오프셋(rem). 고정되지 않은 열은 키가 없다. 숨겨진 열은 누적에서 빠진다. */
export function pinOffsets(prefs: WbsColumnPrefs): Partial<Record<WbsColumnKey, number>>

/** 모르는 키·잘못된 pin 값을 걸러 낸다 — localStorage 값은 신뢰할 수 없다. */
export function normalizePrefs(raw: unknown): WbsColumnPrefs
```

spec `wbsColumns.spec.ts`:

- `name`은 `hidden`에 넣어도 표시된다 (2-2를 고정한다)
- `pin: 'name'`이면 `{ code: 0, name: 5.5 }`
- **`code`를 숨긴 채 `pin: 'name'`이면 `{ name: 0 }`** — 숨긴 열이 오프셋을 밀면 안 된다
- `pin: 'none'`이면 빈 객체
- `normalizePrefs`가 `null`·문자열·모르는 키·잘못된 `pin`을 기본값으로 떨군다

### 3-2. `frontend/src/features/wbs/wbsColumnPrefs.ts` (새 파일)

모듈 스코프 `ref` + `localStorage`. `stores/theme.ts`의 모양을 그대로 따른다.

```ts
const STORAGE_KEY = 'project-flow.wbs-columns'
export const columnPrefs = ref<WbsColumnPrefs>(load())   // 모듈 스코프 — 화면을 옮겨도 유지된다
export function setColumnPrefs(next: WbsColumnPrefs): void   // 저장 + 반영
export function resetColumnPrefs(): void
```

`load()`/저장은 `try/catch`. `theme.ts`처럼 파일 맨 위 주석에 **저장 키를 바꿀 때 같이 고칠 곳**을
적어 둔다.

### 3-3. `frontend/src/features/wbs/WbsColumnSettings.vue` (새 파일)

`ModalDialog`로 자기를 감싸고(`MemberEditor.vue`가 같은 방식의 선례다) 자기 `title`을 계산한다.

- 열 목록: 체크박스 아홉 개(업무명은 목록에 **보이되 비활성** — 왜 못 끄는지 title로 알린다.
  아예 빼면 "왜 없지?"를 묻게 된다)
- 고정: 라디오 세 개 (`없음` / `WBS` / `WBS + 업무명`)
- [기본값으로] 버튼
- [저장]/[닫기]는 `ModalDialog` 규칙대로. 이 폼은 즉시 반영해도 되고(설정이라 되돌리기 쉽다)
  저장 버튼을 둬도 된다 — **즉시 반영을 권한다**: 뒤의 표가 바뀌는 것이 곧 확인 신호다.
  즉시 반영으로 할 경우 [닫기]만 두고, 대화상자 규칙의 "성공했을 때만 닫는다"는 해당 없다
  (서버로 나가는 요청이 없다)

### 3-4. `frontend/src/features/wbs/WbsTree.vue`

**(a) 업무명 열에 클래스를 붙인다.** `<th>업무명</th>`(`:448`)과 그 `<td>`(`:475`)에 **클래스가
없다.** `col-name` 같은 새 이름을 쓰고 **`.name`을 재사용하지 말 것** — `.name`은 셀 안쪽의
flex 컨테이너(`:854-858`)다. 겹치면 들여쓰기·토글 정렬이 깨진다.

**(b) 시작일·종료일이 같은 클래스(`.date`)를 쓴다**(`:449-450`, `:505-506`). 표시·숨김은 열
모델로 판정하므로 문제가 없지만, **`.date`에 고정 CSS를 걸지 말 것** — 두 열에 동시에 걸린다.
(이 세 단계 고정에서는 두 열 모두 고정 대상이 아니라 실제로 걸 일이 없다.)

**(c) `<th>`/`<td>` 쌍에 `v-if`를 두른다.** 열 열 개 × 2 = 20곳.

```
<th v-if="shows('startDate')" class="date">시작일</th>
…
<td v-if="shows('startDate')" class="date">{{ row.node.startDate ?? '-' }}</td>
```

`shows(key)`는 `visibleColumns(columnPrefs)`에서 만든 `Set`을 보는 헬퍼다. **`v-for`로 열을
돌지 않는다**(2-1).

**(d) `colspan`을 계산값으로 바꾼다.** 툴바 작업에서 `10`이 된 자리(`091d4a6` 기준 `:682`)를
`:colspan="visibleCount"`로. `WbsTreeColumns.spec.ts:81-89`가 `thead th` 개수와 비교하므로
**틀리면 테스트가 잡는다.** 이 표에서 두 번 사고 난 자리다(`done/wbs-tree-improvements.md`가
함정으로 두 번 적어 뒀다).

**(e) 고정 열.** 고정된 열의 `<th>`/`<td>`에 인라인 스타일로 `position: sticky`와 `left`를 준다.

```
:style="pinStyle('code')"   →  { position: 'sticky', left: '0rem', zIndex: … } | undefined
```

색이 아니라 배치라서 인라인 스타일이 토큰 규칙과 무관하다. 다만 **배경은 반드시 CSS 클래스로**
준다:

```css
/* 고정 셀은 배경이 없으면 스크롤된 셀이 통과해 보인다. */
.pinned { background: var(--surface); }
thead .pinned { background: var(--surface); z-index: 3; }   /* 머리글 고정과 겹치는 칸 */
```

- **z-index 층이 셋이다**: 고정 머리글(툴바 작업에서 `z-index: 2`) < 그보다 위에 있어야 하는
  "고정 열 × 고정 머리글" 교차 칸(`3`) , 고정된 본문 셀(`1`).
- **선택·드롭 강조가 고정 셀을 이겨야 한다.** `tbody tr.selected > td`(`:774-777`)는 특이도가
  `.pinned`보다 높아 그대로 이긴다. `.drop-before`/`.drop-after`/`.drop-inside`/`.focused`
  (`:744-760`)도 같다. **`.pinned`에 `!important`를 붙이지 말 것** — 붙이면 선택된 행의 고정
  셀만 배경이 다르게 남는다. 수동 확인 목록에 들어 있다.
- 고정된 마지막 열에 오른쪽 경계선을 준다(`RaciMatrix.vue:290`의 `border-right`와 같은 이유 —
  고정 영역과 스크롤 영역의 경계가 보여야 한다).

**(f) 업무명 고정 시 폭.** `pin: 'name'`일 때만 `col-name`에 폭을 준다(`pinWidthRem`과 같은
값이어야 한다 — 어긋나면 오른쪽 열들이 고정 영역 아래로 밀려 들어간다). 클래스 하나로
처리한다(`.pin-name .col-name { width: 22rem }` 같은 식, 폭 값은 실제 화면에서 정한다).

**(g) 툴바에 [열 설정] 버튼을 붙인다.** 선행 작업이 만든 툴바의 오른쪽. 선택과 무관하므로 항상
활성이고, **커밋 조회 중에도 활성이다** — 열 구성은 쓰기가 아니다.

### 3-5. `frontend/src/features/wbs/WbsTreeColumns.spec.ts` (기존 파일 확장)

- 열을 숨기면 `thead th` 수와 서랍 `colspan`이 **함께** 줄어든다 (기존 `:81-89` 테스트를 숨김
  상태로 한 번 더 돌린다)
- 숨긴 열의 `<td>`가 사라진다 (담당자를 숨기면 RACI 링크가 없다)
- 업무명은 숨기려 해도 남는다
- 고정하면 해당 `<th>`/`<td>`에 `position: sticky`가 붙고, 고정하지 않은 열에는 없다

---

## 4. 건드리지 않는 것

| 무엇 | 이유 |
|---|---|
| 셀 마크업 자체 | `v-if`만 두른다. 범용 렌더러로 바꾸면 1000줄 재작성이다(2-1) |
| 각 열의 기존 폭 CSS | 숨김·고정과 무관하다. 고정 오프셋에 필요한 값만 모델이 안다 |
| `.name`(`:854-858`) | 셀 안쪽 flex 컨테이너다. 열 클래스로 재사용하면 들여쓰기가 깨진다 |
| `.date` 클래스 | 두 열이 공유한다. 여기에 고정 CSS를 걸면 둘 다 걸린다 |
| `tbody tr.selected` / `.drop-*` / `.focused` 배경 규칙 | 고정 셀 배경을 이겨야 한다. `.pinned`에 `!important` 금지 |
| `ExportMenu.vue` | 팝오버를 재사용하지도, 공용으로 추출하지도 않는다. 동작하는 화면을 건드릴 이유가 없다 |
| 서버·API | 열 구성은 화면 설정이다. 어떤 엔드포인트도 바뀌지 않는다 |
| `WbsTreeColumns.spec.ts`의 `colspan` 테스트 | 숨김 케이스를 **추가**한다. 기존 단정을 느슨하게 고치지 말 것 |

---

## 5. 확인 방법

```bash
cd frontend
npm test
npm run build
```

수동 확인 (열이 다 차 있는 프로젝트로):

1. [열 설정] → 대화상자가 열리고 업무명 체크박스가 비활성이며 이유가 title로 보인다
2. 담당자·분야를 끈다 → 표에서 두 열이 사라지고 머리글 수와 서랍 폭이 함께 맞는다
3. 체크포인트를 펼친 상태로 열을 끈다 → 서랍이 표를 깨뜨리지 않는다
4. 고정을 `WBS + 업무명`으로 바꾼다 → 가로로 스크롤해도 두 열이 왼쪽에 남고, 스크롤되는 셀이 그
   아래로 **통과해 보이지 않는다**
5. 세로로 스크롤한다 → 고정 머리글과 고정 열이 겹치는 왼쪽 위 칸이 둘 다 위에 있다(글자가 겹치지
   않는다)
6. 고정 상태에서 행을 **선택**한다 → 고정된 두 칸도 선택 배경으로 바뀐다(다른 색으로 남으면
   `!important`나 특이도 문제다)
7. 고정 상태에서 행을 **드래그**한다 → 드롭 위치 강조(위/아래/가운데)가 고정 칸에서도 보인다
8. WBS 코드만 숨기고 업무명을 고정한다 → 업무명이 **왼쪽 끝**에 붙는다(빈 5.5rem이 남으면 오프셋
   계산이 숨김을 반영하지 않은 것이다)
9. 새로 고친다 → 열 구성과 고정 단계가 그대로다
10. 프로젝트를 바꾼다 → 열 구성이 그대로다(프로젝트별로 나누지 않는다)
11. [기본값으로] → 열 열 개가 모두 보이고 고정이 풀린다
12. 라이트·다크 양쪽에서 고정 열의 배경과 경계선을 확인한다
13. 커밋 조회 모드 → [열 설정]이 여전히 눌린다(쓰기가 아니다)
14. 브라우저의 사생활 보호 모드에서 화면을 연다 → `localStorage`가 막혀도 기본 열 구성으로 뜬다

---

## 6. 이번에 하지 않는 것 (후속 후보)

- **열 순서 바꾸기(드래그).** 고정이 "앞에서부터"에 기대고 있어 순서까지 자유로워지면 오프셋
  계산과 고정 단계를 다시 설계해야 한다
- **열 폭 조절.** 폭이 CSS에 있는 채로 사용자 조절을 허용하려면 폭의 단일 출처를 모델로 옮겨야
  한다(= 모든 열 CSS를 손댄다)
- **임의 열 고정.** 미연속 고정의 시각적 결과와 런타임 폭 측정이 필요하다(2-3)
- **다른 표에 같은 기능.** RAID·Backlog도 열이 많다. 이 화면에서 검증한 뒤 `wbsColumns.ts`를
  일반화할지 본다
- **프로젝트별 열 구성.** 지금은 사람 단위 하나다
