# WBS 항목 설명(여러 줄·칩+창)과 Action Item 링크, 그리고 체크포인트 입력 버그 둘

대상 체크아웃: `D:\Git\ScheduleMgr` (main `4614345` "가중치를 자유 입력 숫자에서 5단계 등급
선택으로 바꾼다"). **모든 줄번호는 이 커밋에서 확인한 값이다** — 작업 전에 `git log -1`로 대조하고,
달라졌으면 인용한 줄을 먼저 다시 찾아라.

작업 전에 [CLAUDE.md](../../CLAUDE.md)의 **"WBS 설계상 알아둘 점"**, **"화면 레이아웃 규칙"**,
**"컨트롤 층"**, **"컴포넌트 테스트"** 절을 먼저 읽어라.

네 건이다. A·B는 버그, C·D는 기능이다.

| # | 무엇 | 계층 |
|---|---|---|
| **A** | 체크포인트 입력칸에서 누른 방향키를 WBS 트리가 가로챈다 | 프론트 |
| **B** | 버튼에 포커스가 있으면 Esc가 승인 입력·체크포인트 폼을 닫지 못한다 | 프론트 |
| **C** | WBS 항목 설명 — 여러 줄 입력, 행에서는 칩 + 클릭 시 창 | 프론트 |
| **D** | WBS 항목에 Action Item URL 추가 | DB → 화면 전 계층 |

**한 사람이 순서대로 한다.** A·B·C·D가 전부 `WbsTree.vue`를 만지고 A·B는 `CheckpointList.vue`를
공유한다 — 나눠 맡으면 병합이 충돌한다. 권장 순서는 **A → B → C → D**다. A·B가 작고 서로
독립이며, 먼저 끝내 두면 C·D를 손으로 확인할 때 방향키·Esc가 이미 정상이라 확인이 쉬워진다.

---

## 1. 해결할 문제

### A. 체크포인트 입력칸에서 방향키가 트리로 샌다

`WbsTree.vue:1032`가 `<tbody>` **하나에** `@keydown="onKeydown"`을 걸고, `onKeydown`은 **이벤트
타깃을 전혀 보지 않는다.**

```
frontend/src/features/wbs/WbsTree.vue:516  const ARROW_KEYS = [...]
                                      :522  function onKeydown(event)
                                      :531  event.preventDefault()   ← Alt 경로
                                      :548  event.preventDefault()   ← 일반 경로, 타깃 무관
                                      :549  resolveVisibleSelection()
                                     :1032  <tbody ... @keydown="onKeydown">
```

체크포인트 행은 `<tr class="checkpoint-row">`로 **같은 `<tbody>` 안**에 있다(`:1237-1250`). 그래서
`CheckpointList`의 입력칸에서 누른 방향키가 `input → td → tr → tbody`로 버블링해 그대로
`preventDefault()`를 맞는다.

| 키 | 지금 일어나는 일 |
|---|---|
| `←` `→` | 캐럿이 안 움직이고, 대신 트리 행이 접히거나 펼쳐진다 |
| `↑` `↓` | 트리의 선택 행이 이동한다 |
| 가중치 `<select>`의 `↑` `↓` | **등급을 고를 수 없다** — 사용자가 보고하지 않았지만 같은 원인이다 |
| `Alt`+방향키 | **타이핑 중에 WBS 항목이 실제로 이동·들여쓰기된다**(`:531`) |

**같은 저장소에 이미 같은 사고를 막아 둔 선례가 있다** — `SprintAssignTable.vue:94`의
`if ((event.target as HTMLElement).tagName === 'INPUT') return`. 규칙이 없었던 게 아니라 여기서
새어나간 것이다.

### B. Esc가 버튼 위에서 죽는다

`@keydown.esc`가 **입력칸에만** 붙어 있다.

| 위치 | Esc |
|---|---|
| 승인 입력칸 `CheckpointList.vue:235` | ✅ |
| 승인 행의 `[확인]`·`[취소]` 버튼 `:237-238` | ❌ |
| 추가/수정 폼의 세 칸 `:273` `:282` `:298` | ✅ |
| 추가/수정 폼의 `[추가]`·`[취소]` 버튼 | ❌ |

버튼을 클릭하면 브라우저가 그 버튼에 포커스를 준다(Windows/Chrome). Tab으로 옮겨도 마찬가지다.
그 상태에서 누른 Esc는 아무 데도 닿지 않는다.

### C. 설명이 한 줄 입력이고, 행에서는 이름 옆에 잘려 붙는다

- **입력**: `WbsForm.vue:142`가 `<input type="text">`다. 줄바꿈을 넣을 수 없다.
- **표시**: `WbsTree.vue:1083-1087`이 이름 바로 옆에 `.desc cell-clip`으로 붙인다. DB는 이미
  `VARCHAR(2000)`인데(`V2__create_wbs_items_table.sql:6`) 행 하나에 그 길이를 그대로 들이민다.

### D. 외부 Action Item을 걸 자리가 없다

WBS 항목에 회의록·티켓·산출물 URL을 적을 칸이 없다. 지금은 설명에 URL을 텍스트로 적는 수밖에
없고, 그러면 클릭할 수 없다.

---

## 2. 결정된 방향

### A. 트리는 입력 중인 칸의 키에 손대지 않는다

`onKeydown` 맨 앞에서 **타깃이 입력 컨트롤이면 즉시 반환**한다. 이 한 줄이 `Alt` 경로와 일반
경로를 **동시에** 막는다(순서가 중요하다 — `event.altKey` 분기보다 **위**여야 한다).

**`event.target === tbody`로 좁히지 않는다.** 그렇게 하면 트리 행 안의 토글 버튼이나 링크에
포커스가 있을 때 방향키 탐색이 죽는다. 막아야 할 것은 "입력 중"이지 "tbody가 아님"이 아니다.

**판정은 순수 함수로 뺀다**(CLAUDE.md "판정을 순수 함수로 빼는 관습은 그대로 유지합니다").
단 `instanceof HTMLInputElement`를 쓰지 마라 — 기본 테스트 환경이 `node`라 `HTMLInputElement`가
정의되어 있지 않아 `ReferenceError`가 난다. `tagName` 문자열로 판정하면 `{ tagName: 'INPUT' }`
같은 평범한 객체로 테스트할 수 있고, 이 파일만 `happy-dom`으로 올릴 필요가 없다.

### B. Esc 핸들러를 컨테이너로 올린다

`.approve-row`·`.cp-form` **`<div>` 자체**에 `@keydown.esc`를 건다. 안쪽의 입력칸·`<select>`·버튼
어디서 눌러도 버블링으로 닿는다.

**`@keydown.enter`는 올리지 마라.** Enter가 `<button>`에 닿으면 브라우저가 이미 click을
발생시키는데, 컨테이너에도 핸들러가 있으면 **같은 저장이 두 번** 나간다. Enter는 지금처럼
입력칸에만 둔다.

### C. 입력은 기존 자리에서 여러 줄, 표시는 칩 + 창

- **입력은 `WbsForm.vue`의 설명 칸 그대로**다(사용자 결정). `<textarea rows="3">`로만 바꾼다.
  **DB·DTO·서버는 손대지 않는다** — `VARCHAR(2000)`이 이미 줄바꿈을 받는다.
- **표시는 `[설명]` 칩 하나**로 접고, 누르면 `ModalDialog`가 전문을 보여준다. 읽기 전용
  모드(`readOnly`, 커밋 조회 포함)에서도 **열린다** — CLAUDE.md "읽기 동작은 잠그지 않습니다".
- **창에서 편집하게 만들지 마라.** 쓰기 경로가 둘이 되면 검증·매핑을 두 벌 관리하게 된다
  (CLAUDE.md가 체크포인트 UI를 한 곳에만 두라고 적은 것과 같은 이유). 창은 보기 전용이다.

**알고 넘어갈 부작용**: `wbsTreeFilter.ts:137`이 검색어를 `name + description`에 맞춘다. 칩으로
접으면 "설명에만 걸린 행"이 왜 나왔는지 화면에서 바로 안 보인다. **그대로 둔다** — 설명이 있는
행에는 반드시 칩이 있으므로 눌러서 확인할 수 있다.

### D. Action Item URL — `wbs_items`의 새 칼럼 하나

- **모든 WBS 항목에 허용한다**(사용자 결정). 설명과 같은 층위의 자유 메모라, 실행 방식·분야처럼
  "Summary에는 못 붙이되 보관한다"는 규칙을 한 벌 더 만들 이유가 없다.
- **체크포인트당 하나가 아니라 항목당 하나**다. 여러 개가 필요해지면 그때 링크 표로 승격한다.
- **라벨은 받지 않는다**(사용자 결정). 화면에는 `🔗` 아이콘만 그리고 전체 URL은 `title` 툴팁에
  둔다 — 긴 URL이 좁은 트리 행을 밀어내면 안 된다.
- **생성자를 건드리지 않는다.** `WbsItem`의 10-인자 생성자를 테스트 20여 곳이 쓰고 있어
  파라미터를 더하면 전부 고쳐야 한다. `update()`(호출 3곳)에만 더하고, 생성·가져오기 경로에는
  기존 `restoreProgressBasis`/`restoreActualDates` 옆에 `restoreActionItemUrl`을 나란히 둔다.

#### 보안 — 이 앱에서 사용자 입력이 `href`에 들어가는 첫 자리다

지금 저장소의 외부 링크는 `App.vue:29`의 `#main-content` 하나뿐이고 나머지는 전부 `RouterLink`다.
`javascript:`·`data:`·`vbscript:` URL을 그대로 `href`에 넣으면 **그 링크를 클릭하는 것만으로
스크립트가 실행된다.** 세 겹으로 막는다.

1. **서버** — `http://` 또는 `https://`만 허용. 새 도메인 예외를 만들지 말고 **Jakarta Bean
   Validation**으로 처리한다(`@Pattern` + `@Size`). 이미 `MethodArgumentNotValidException`
   핸들러가 필드명과 함께 400을 돌려준다(`GlobalExceptionHandler:134`).
2. **가져오기** — 손으로 편집한 파일은 bean validation을 타지 않는다. `ImportService`의 **검증
   단계**(삽입보다 앞)에서 같은 규칙으로 보고 `InvalidImportException`을 던진다(이미
   `GlobalExceptionHandler:75`에 등록되어 400이다).
3. **화면** — 그래도 렌더 직전에 한 번 더 본다. 규칙이 생기기 전 저장된 행이 남아 있을 수 있고,
   **마지막 방어선이 화면이어야 브라우저가 실제로 실행하는 것을 막는다.** 안전하지 않으면 링크
   대신 평문으로 그린다.

---

## 3. 작업

### A. 트리 키 핸들러가 입력 컨트롤을 비켜간다

**새 파일** `frontend/src/features/wbs/treeKeyTarget.ts`

```ts
/**
 * 지금 키를 받는 것이 "글자를 입력하는 칸"인가. WBS 트리의 `<tbody>`는 키 핸들러를 하나만
 * 두는데(`WbsTree.onKeydown`), 그 안에 펼친 Work Package의 체크포인트 폼(`CheckpointList`)이
 * 들어온다. 그 입력칸에서 누른 방향키가 여기까지 버블링해 `preventDefault()`를 맞으면 캐럿이
 * 움직이지 않고, `<select>`의 가중치 등급도 고를 수 없다. `Alt`+방향키는 더 나쁘다 — 타이핑
 * 중에 WBS 항목이 실제로 이동한다.
 *
 * `SprintAssignTable.vue:94`가 같은 종류의 사고를 같은 방법으로 막고 있다.
 *
 * **`instanceof HTMLInputElement`를 쓰지 마라** — 이 저장소의 기본 vitest 환경은 `node`라
 * `HTMLInputElement`가 정의되어 있지 않아 `ReferenceError`가 난다. `tagName` 문자열로 보면
 * 평범한 객체로 테스트할 수 있어 이 파일만 `happy-dom`으로 올릴 필요가 없다
 * (CLAUDE.md "컴포넌트 테스트 (프론트엔드)").
 */
const TEXT_ENTRY_TAGS = new Set(['INPUT', 'SELECT', 'TEXTAREA'])

export function isTextEntry(target: EventTarget | null): boolean {
  const el = target as { tagName?: unknown; isContentEditable?: unknown } | null
  if (!el) return false
  if (typeof el.tagName === 'string' && TEXT_ENTRY_TAGS.has(el.tagName)) return true
  return el.isContentEditable === true
}
```

**새 파일** `frontend/src/features/wbs/treeKeyTarget.spec.ts` — `INPUT`/`SELECT`/`TEXTAREA`가
`true`, `TBODY`/`BUTTON`/`TR`이 `false`, `null`이 `false`, `isContentEditable: true`가 `true`.

**`frontend/src/features/wbs/WbsTree.vue:522`**

```ts
function onKeydown(event: KeyboardEvent) {
  if (!ARROW_KEYS.includes(event.key)) return
  // ★ 추가 — altKey 분기보다 반드시 위여야 한다. 아래에 두면 타이핑 중 Alt+방향키가 항목을 옮긴다.
  if (isTextEntry(event.target)) return
  if (event.altKey) { ... }
```

import를 함께 추가한다(`:8`의 `checkpointRow` import 옆).

### B. Esc를 컨테이너로 올린다

**`frontend/src/features/progress/CheckpointList.vue`**

`:228` 승인 행 — 컨테이너에 Esc를 걸고 입력칸에서는 **Esc만** 뺀다(Enter는 남긴다).

```diff
-        <div v-if="editable && approvingId === cp.id" class="approve-row">
+        <!-- Esc는 컨테이너에서 받는다 — 입력칸에만 두면 [확인]·[취소]에 포커스가 간 뒤
+             (클릭하면 브라우저가 그렇게 한다) Esc가 아무 데도 닿지 않는다.
+             Enter는 올리지 않는다 — <button> 위에서 Enter는 이미 click을 일으키므로
+             컨테이너에도 핸들러가 있으면 같은 저장이 두 번 나간다. -->
+        <div v-if="editable && approvingId === cp.id" class="approve-row" @keydown.esc="cancelApprove">
           <input
             ...
             @keydown.enter="confirmApprove"
-            @keydown.esc="cancelApprove"
           />
```

`:265` 추가/수정 폼 — 같은 방식. `.cp-form` `<div>`에 `@keydown.esc="closeForm"`을 걸고, 세 칸
(`:273` `:282` `:298`)의 `@keydown.esc`를 지운다. **`@keydown.enter`는 세 칸에 그대로 둔다.**
`<select>`의 것도 지우지 마라 — 빠뜨리면 그 칸에서만 Enter 저장이 죽는다(그래서 원래 붙여 둔 것이다).

**테스트** — `CheckpointList.spec.ts`에 두 건을 더한다(이 파일은 이미 `happy-dom` docblock이 있다).

- 승인 입력을 편 뒤 `[취소]` 버튼에 `keydown` `Escape`를 쏘면 승인 행이 닫힌다.
- 추가 폼을 편 뒤 `[추가]` 버튼에 `keydown` `Escape`를 쏘면 폼이 닫힌다.

### C-1. 설명 입력을 여러 줄로

**`frontend/src/features/wbs/WbsForm.vue`**

`:140-143` — 설명을 **자기 `.row`로 내린다.** 3줄짜리 `textarea`를 업무명 옆에 두면 두 칸의
높이가 크게 어긋난다.

```diff
       <div class="row">
         <label class="grow">
           업무명
           <input v-model="form.name" type="text" required placeholder="업무명" />
         </label>
-        <label class="grow">
-          설명
-          <input v-model="form.description" type="text" placeholder="설명 (선택)" />
-        </label>
       </div>
+
+      <div class="row">
+        <label class="grow">
+          설명
+          <!-- 여러 줄. self-closing 하지 말고 `></textarea>`로 닫아라 — 태그 사이의 공백이
+               그대로 초기값이 된다. -->
+          <textarea v-model="form.description" rows="3" placeholder="설명 (선택)"></textarea>
+        </label>
+      </div>
```

`:345` 스타일 — **여기가 이 작업에서 가장 흔한 실수 자리다**(`raid_multiline` 지시서가 같은
경고를 남겼다).

```diff
 input,
-select {
+select,
+textarea {
   padding: 0.45rem 0.6rem;
   border: 1px solid var(--border-input);
   border-radius: 6px;
   font: inherit;
 }
+
+/* 세로로만 늘린다 — 가로로 늘리면 대화상자 폭을 넘어간다. */
+textarea {
+  resize: vertical;
+}
```

`font: inherit`에 `textarea`를 빠뜨리면 **그 칸만 브라우저 기본 고정폭 글꼴**로 나온다.
`:353`의 `input:disabled, select:disabled`에도 `textarea`를 더해 둔다(설명은 비활성되지 않지만,
나중에 다른 `textarea`가 붙었을 때를 위해 짝을 맞춘다).

### C-2. 트리 행의 설명을 칩 + 창으로

**`frontend/src/features/wbs/WbsTree.vue`**

`:1083-1087` 교체:

```diff
-                <span
-                  v-if="row.node.description"
-                  class="desc cell-clip"
-                  :title="row.node.description"
-                >{{ row.node.description }}</span>
+                <!-- 설명은 행에 펼치지 않는다 — VARCHAR(2000)이 한 행을 통째로 늘린다.
+                     `readOnly`에서도 살아 있다: 보는 것은 쓰기가 아니다. -->
+                <button
+                  v-if="row.node.description"
+                  type="button"
+                  class="desc-chip"
+                  :aria-label="`설명 보기 — ${row.node.name}`"
+                  @click.stop="descriptionNode = row.node"
+                >설명</button>
```

`@click.stop`을 반드시 붙여라 — 행에 `@click="onRowClick"`이 걸려 있어서 설명을 들여다보는
것만으로 선택이 바뀐다. (`onRowDblClick`은 `closest('button, a')` 가드가 이미 `<button>`을
걸러낸다.)

스크립트에 상태 하나와 `ModalDialog` import를 더한다:

```ts
/** 설명 전문을 보여줄 항목. `null`이면 창이 닫혀 있다. 보기 전용이다 — 여기서 고치지 않는다
 *  (고치는 자리는 `WbsForm` 하나여야 한다). */
const descriptionNode = ref<WbsNode | null>(null)
```

템플릿 맨 끝(`</table>`을 감싼 스크롤 컨테이너 **바깥**)에:

```html
<!-- `ModalDialog`가 body로 teleport 하므로 표의 overflow에 잘리지 않는다. -->
<ModalDialog
  v-if="descriptionNode"
  :title="`설명 — ${descriptionNode.code} ${descriptionNode.name}`"
  @close="descriptionNode = null"
>
  <p class="desc-full">{{ descriptionNode.description }}</p>
</ModalDialog>
```

스타일 — `:1692`의 `.desc`를 `.desc-chip`으로 바꾼다.

```css
/* 다른 칩(BacklogList `.chip`, CheckpointList `.cp-approved`)과 같은 계열. 타깃 24×24px은
   패딩만으로 채운다 — `::after`로 히트 영역을 넓히는 기법은 여기서 쓰지 마라(CLAUDE.md
   "히트 영역을 ::after로 넓힐 때": 기준 요소를 못 찾으면 뷰포트 전체를 덮어 모든 클릭을 먹는다).
   최소치에 정확히 맞추지도 마라 — 값 하나만 바뀌어도 조용히 기준 아래로 떨어진다. */
.desc-chip {
  flex: none;
  padding: 0.2rem 0.45rem;
  border-radius: 999px;
  background: var(--badge-neutral-bg);
  color: var(--badge-neutral-fg);
  font-size: 0.68rem;
  line-height: 1.4;
  min-height: 25px;
  white-space: nowrap;
}

.desc-full {
  margin: 0;
  white-space: pre-wrap;   /* 줄바꿈을 살린다 — 이게 없으면 여러 줄 입력이 무의미하다 */
  overflow-wrap: anywhere; /* 공백 없는 긴 문자열이 창을 넘어가지 않게 */
}
```

`.cell-clip`을 더 이상 쓰지 않으니 남은 참조가 없는지 확인한다(다른 열에서도 쓰고 있으면 그대로 둔다).

### D. Action Item URL

#### D-1. 마이그레이션 — `backend/src/main/resources/db/migration/V24__add_wbs_action_item_url.sql`

```sql
-- WBS 항목이 가리키는 외부 Action Item(회의록·티켓·산출물) 주소.
--
-- 설명과 같은 층위의 자유 메모라 모든 항목에 허용한다 — 실행 방식·분야처럼 "Summary에는 붙일
-- 수 없되 보관한다"는 규칙을 두지 않는다.
--
-- http/https만 허용한다. 검증은 애플리케이션(요청 DTO의 @Pattern, 가져오기의 검증 단계)이
-- 하고, 화면도 렌더 직전에 한 번 더 본다 — 이 값이 그대로 <a href>로 들어가기 때문이다.
-- CHECK 제약을 걸지 않는 이유는 H2/PostgreSQL의 정규식 문법이 갈리기 때문이다.
ALTER TABLE wbs_items ADD COLUMN action_item_url VARCHAR(2000);
```

#### D-2. 백엔드

| 파일 | 할 일 |
|---|---|
| `domain/WbsItem.java` | `@Column(name = "action_item_url", length = 2000) private String actionItemUrl;` + getter |
| 〃 `:175` `update(...)` | **마지막 파라미터**로 `String actionItemUrl` 추가 + 대입 (호출 3곳: `WbsService:325`, `BacklogServiceTest:396`, `ProgressVarianceRegressionTest:135`) |
| 〃 `:195` 근처 | `restoreProgressBasis`/`restoreActualDates` 옆에 `restoreActionItemUrl(String)` 추가 |
| 〃 생성자 | **건드리지 않는다**(테스트 20여 곳이 쓴다) |
| `dto/WbsItemCreateRequest.java:32`, `dto/WbsItemUpdateRequest.java:30` | `description` 아래에 `actionItemUrl` 추가 + 검증 |
| `dto/WbsNodeResponse.java:69` / `:167` 팩토리 | `description` 옆에 `actionItemUrl` 추가 |
| `application/WbsService.java:176` | 저장 직후 `saved.restoreActionItemUrl(request.actionItemUrl())` — `hasBasis`/`hasActuals`와 같은 자리에서 한 번만 저장하도록 묶어라 |
| 〃 `:325` | `item.update(...)`에 `request.actionItemUrl()` 전달 |
| `dto/ProjectExportResponse.java:107` `ExportedWbsItem` | `description` 옆에 `actionItemUrl` 추가 |
| `application/ExportService.java:353` | 생성자에 값 추가 |
| 〃 `:81` | **`FORMAT_VERSION` 7 → 8** |
| `application/ImportService.java:103` | **`SUPPORTED_FORMAT_VERSION` 7 → 8** |
| 〃 `:628` 삽입 | `row.restoreActionItemUrl(item.actionItemUrl())`를 `restoreActualDates` 옆(`:642`)에 |
| 〃 검증 단계 | 안전하지 않은 URL이면 `InvalidImportException` (아래) |

**요청 DTO 검증** — 새 예외 클래스를 만들지 마라. 이미 있는 것으로 끝난다.

```java
@Size(max = 2000)
@Pattern(regexp = "^$|^https?://\\S+$",
         message = "Action Item 주소는 http:// 또는 https:// 로 시작해야 합니다")
String actionItemUrl,
```

`@Pattern`은 `null`을 통과시키고(미입력), `^$`가 빈 문자열을 통과시킨다(폼이 지운 경우). 서버는
빈 문자열을 `null`로 정규화해 저장한다 — `ProgressBasisService`의 `blankToNull`과 같은 처리를
`WbsService`에 둔다. 실패는 `MethodArgumentNotValidException` → `GlobalExceptionHandler:134`가
필드명과 함께 400을 돌려준다.

**가져오기 검증** — 손으로 편집한 파일은 bean validation을 타지 않는다. `ImportService`의
**삽입 전 검증 단계**에서 같은 규칙을 보고 `InvalidImportException`을 던진다(이미
`GlobalExceptionHandler:75`에 등록되어 400이다). CLAUDE.md의 "구조는 검증하고 계획의 품질은
검증하지 않는다"에 어긋나지 않는다 — 이건 계획의 품질이 아니라 **실행 가능한 스크립트**다.

#### D-3. 프론트엔드

| 파일 | 할 일 |
|---|---|
| `api/wbsApi.ts:41` `WbsNode` | `actionItemUrl: string \| null` |
| 〃 `:116` `WbsItemInput` | 같음 |
| `features/wbs/wbsFormMapping.ts:19` | **★ `actionItemUrl: item.actionItemUrl ?? null` — 아래 경고 참조** |
| `features/wbs/WbsForm.vue` | `empty`(`:39`)에 `actionItemUrl: null`, 설명 아래 줄에 입력칸 |
| `features/wbs/WbsTree.vue` | 이름 셀에 `🔗` 링크(설명 칩 옆) |
| `shared/url.ts` (새 파일) | `isSafeHttpUrl(value)` — 렌더 가드 |
| `shared/exportRows.ts:50` / `:73` | 행과 머리글에 `Action Item` 한 칸 추가 |

**★ `wbsFormMapping.ts`가 이 작업에서 가장 위험한 한 줄이다.**
`nodeToFormInput`에 `actionItemUrl`을 빠뜨리면 **제목만 고쳐 저장해도 URL이 조용히 사라진다** —
커밋 `da96ebe`와 정확히 같은 사고이고, 이 파일의 주석이 "결함 3"이라고 부르는 것이다.
`wbsFormMapping.spec.ts:73`("나머지 필드도 편집 없이 저장할 수 있도록 그대로 옮긴다")에
`actionItemUrl` 단정을 **반드시 추가하라.**

**폼 입력칸** (설명 `.row` 아래):

```html
<div class="row">
  <label class="grow">
    Action Item 주소
    <input
      v-model="form.actionItemUrl"
      type="url"
      placeholder="https://… (선택)"
    />
  </label>
</div>
```

`type="url"`은 편의일 뿐이다 — 브라우저 검증은 우회 가능하니 **서버 검증을 빼지 마라.**

**렌더 가드** `frontend/src/shared/url.ts`:

```ts
/**
 * 이 값을 `<a href>`에 넣어도 되는가. 이 앱에서 사용자 입력이 href로 들어가는 자리는 WBS
 * 항목의 Action Item 주소 하나뿐이고, `javascript:`·`data:`·`vbscript:`가 들어가면 그 링크를
 * 누르는 것만으로 스크립트가 실행된다.
 *
 * 서버도 같은 규칙으로 막지만 **여기가 마지막 방어선**이다 — 규칙이 생기기 전에 저장된 행,
 * 손으로 편집한 가져오기 파일이 남아 있을 수 있고, 실제로 실행하는 것은 브라우저다.
 * 안전하지 않으면 호출자는 링크 대신 평문으로 그린다.
 */
export function isSafeHttpUrl(value: string | null | undefined): boolean {
  if (!value) return false
  try {
    const protocol = new URL(value).protocol
    return protocol === 'http:' || protocol === 'https:'
  } catch {
    return false
  }
}
```

`new URL()`은 상대 경로에서 던지므로 `catch`가 그것도 함께 막는다. 순수 함수이니
`shared/url.spec.ts`에 `javascript:alert(1)`, `JavaScript:alert(1)`(대소문자), `data:text/html,…`,
`  javascript:…`(앞 공백), `not-a-url`, `https://example.com` 을 고정하라.

**트리 링크** (설명 칩 옆):

```html
<a
  v-if="isSafeHttpUrl(row.node.actionItemUrl)"
  class="action-link"
  :href="row.node.actionItemUrl!"
  target="_blank"
  rel="noopener noreferrer"
  :title="row.node.actionItemUrl!"
  :aria-label="`Action Item 열기 — ${row.node.name}`"
  @click.stop
>🔗</a>
```

`rel="noopener noreferrer"`를 빼지 마라. `@click.stop`은 설명 칩과 같은 이유다.
`.action-link`도 타깃 24×24px을 패딩으로 채우고, `::after` 확장 기법은 쓰지 마라.

---

## 4. 건드리지 않는 것

| 대상 | 이유 |
|---|---|
| `AcceptanceCheckpoint` 및 체크포인트의 DB·DTO | 이번 작업에 체크포인트 **스키마 변경은 없다**. A·B는 순수 화면 버그다 |
| `wbs_items.description` 스키마 | 이미 `VARCHAR(2000)`이라 여러 줄을 그대로 받는다 |
| `WbsItem` 생성자(8·10 인자) | 테스트 20여 곳이 쓴다. `update()` + `restoreActionItemUrl`로 끝낸다 |
| `wbsColumns.ts` · `wbsColumnPrefs.ts` · `visibleColumnCount` | 설명 칩과 🔗를 **이름 셀 안에** 두므로 열이 늘지 않는다 |
| `WbsImportParser`(Excel·CSV 가져오기) | 열 다섯 개 고정이 의도된 계약이다 — 새 필드를 끼워 넣지 마라 |
| `ScheduleCalculator` · `ProgressCalculator` · `DelayCalculator` | 설명·URL은 계산에 참여하지 않는다 |
| `csv.ts` | `escapeField`가 `\r\n` 포함 필드를 이미 따옴표로 감싼다 — 줄바꿈 설명이 그대로 안전하다 |
| `ModalDialog.vue` | 중첩·teleport·Escape가 이미 다 된다. 새 prop이 필요 없다 |
| `stores/scheduleCache.ts`의 리비전 | WBS 항목 수정은 이미 `markWbsChanged()`를 부른다. 새 리비전을 만들지 마라 |

---

## 5. 확인 방법

```bash
cd backend && ./gradlew test
cd frontend && npm run build && npm test
```

### 손으로 확인할 것

**A — 방향키** (Waterfall Work Package를 펼쳐 체크포인트 폼을 연다)

1. 제목 칸에 글자를 넣고 `←` `→` — **캐럿이 움직이고 트리는 접히지 않는다.**
2. 가중치 `<select>`에 포커스를 두고 `↑` `↓` — **등급이 바뀐다.**
3. 제목 칸에서 `Alt`+`↑`/`↓`/`←`/`→` — **WBS 항목이 움직이지 않는다.**
4. 트리 행(입력칸 밖)을 클릭하고 방향키 — **선택 이동·펼침이 예전 그대로 동작한다.**
5. 트리 행을 클릭하고 `Alt`+방향키 — **항목 이동이 예전 그대로 동작한다**(기능을 죽이지 않았다).

**B — Esc**

6. `[승인]` → 입력칸에서 Esc — 닫힌다(기존 동작 유지).
7. `[승인]` → `[취소]` 버튼을 **한 번 클릭해 포커스를 주고**(닫히면 다시 열고 Tab으로 이동) Esc — **닫힌다.**
8. `＋ 체크포인트 추가` → `[추가]` 버튼에 Tab으로 이동 후 Esc — **닫힌다.**
9. 제목 칸에서 Enter — 저장이 **한 번만** 나간다(네트워크 탭에서 요청 수를 본다).
10. **한글 IME**: 승인자 이름을 한글로 치는 도중(마지막 글자가 조합 중일 때) Esc를 누른다.
    한 번에 닫히지 않으면 IME가 첫 Esc를 먹은 것이다 — **그 사실을 보고만 하고 이번 작업에서
    고치지 마라**(별도 판단이 필요하다).

**C — 설명**

11. WBS 항목 수정 폼에서 설명에 **여러 줄**을 넣고 저장 → 다시 열면 줄바꿈이 남아 있다.
12. 폼의 설명 칸 글꼴이 옆의 업무명 칸과 **같다**(고정폭으로 튀지 않는다). 라이트·다크 양쪽에서
    테두리·배경도 같다.
13. 트리 행에 `[설명]` 칩이 뜨고, 눌러도 **행 선택이 바뀌지 않는다.**
14. 칩을 누르면 창이 뜨고 **줄바꿈이 보인다.** Esc·배경 클릭·✕로 닫힌다.
15. 설명이 없는 행에는 칩이 없다.
16. 커밋 조회 모드(읽기 전용)에서도 칩이 **살아 있고 창이 열린다.**

**D — Action Item**

17. `https://example.com`을 넣고 저장 → 행에 `🔗`가 뜨고 새 탭에서 열린다. 마우스를 올리면 전체
    URL이 툴팁으로 보인다.
18. `javascript:alert(1)`을 넣고 저장 → **400으로 거부되고 대화상자 안에 사유가 보인다.**
19. **제목만 고쳐 저장한다 → URL이 그대로 남아 있다.** (결함 3 재발 확인 — 가장 중요하다)
20. 같은 방법으로 **설명·가중치·실행 방식·분야·실적 일자**도 그대로 남아 있다(기존 계약).
21. 프로젝트를 JSON으로 내보내고 다시 가져온다 → 설명(줄바꿈 포함)과 URL이 살아 온다.
22. 내보낸 파일의 `actionItemUrl`을 `javascript:alert(1)`로 손으로 고쳐 가져온다 →
    **400으로 거부되고 프로젝트가 만들어지지 않는다.**
23. `formatVersion`이 `7`인 예전 파일을 가져온다 → 거부되지 않고 URL만 빈 채로 들어온다.
24. WBS CSV를 내려받아 Excel에서 연다 → `Action Item` 열이 있고, 줄바꿈 설명이 **한 셀 안에**
    들어간다(행이 깨지지 않는다).

> **자동으로 못 덮는 것**: `happy-dom`에는 레이아웃 엔진이 없어 칩·링크의 실제 타깃 크기(24×24px)와
> 겹침을 테스트로 단정할 수 없고, `<style scoped>`가 주입되지 않아 `getComputedStyle`도 못 쓴다
> (CLAUDE.md "컴포넌트 테스트"). 12·13·17번은 **눈으로** 확인하라. 소스 문자열을 grep하는 우회
> 테스트를 만들지 마라.

---

## 6. 이번에 하지 않는 것

- **체크포인트의 설명·URL.** 이번 결정은 "URL은 Checkpoint가 아니라 Work Package 기준"이다.
  필요해지면 별도 지시서로 같은 계층을 한 번 더 지나가야 한다.
- **Action Item을 여러 개 걸기.** 항목당 하나로 시작한다. 여러 개가 필요해지면 `raid_links`와
  같은 모양의 링크 표로 승격한다 — 그때 이 칼럼은 첫 링크로 이전한다(V21이 RAID에 한 것과 같다).
- **링크 라벨(`JIRA-1234`).** 받지 않기로 했다. 넣으려면 칼럼 하나와 "비었을 때 무엇을 보이나"
  폴백 규칙이 함께 필요하다.
- **IME 조합 중 Esc**(수동 확인 10번). 재현되면 별도로 판단한다.
- **설명 칩의 검색 강조.** 검색어가 설명에만 맞은 행에서 어디가 맞았는지 화면에 표시하지 않는다.
- **`CheckpointList`의 인라인 폼을 대화상자로 올리는 것.** "추가 후 폼을 열어 두고 제목 칸으로
  포커스를 되돌린다"는 기존 결정과 부딪힌다.
