# RAID 입력 폼 여러 줄 입력 — 구현 지시서

> 구현을 맡을 에이전트/개발자용 작업 지시서입니다. 작업 전에 [CLAUDE.md](../CLAUDE.md)의
> "RAID 설계상 알아둘 점", "화면 레이아웃 규칙", "다크 모드", "데이터 내보내기 설계상 알아둘 점"
> 절을 먼저 읽으세요.
>
> **이 작업은 프론트엔드 화면만 바꿉니다. 백엔드·DB·API는 손대지 않습니다.**

## 1. 해결할 문제

RAID 항목의 **설명**과 **대응 방안**이 한 줄짜리 `<input type="text">`입니다.

- [`RaidForm.vue:216`](../frontend/src/features/raid/RaidForm.vue) — 설명
- [`RaidForm.vue:294`](../frontend/src/features/raid/RaidForm.vue) — 대응 방안(종류에 따라
  "대응 방안 / 확인 방법 / 해결 방안 / 확보 방안")

위험을 기술하고 대응을 적는 일은 원래 여러 문단입니다. 지금은

1. **줄바꿈을 아예 넣을 수 없습니다** — `<input>` 안에서 Enter는 폼의 암묵적 제출이라 저장돼 버립니다.
2. **쓴 것을 다시 읽을 수 없습니다** — 두세 문장만 넘어가도 좁은 칸이 옆으로 흐르고 앞부분이 보이지 않습니다.

### 저장 경로는 이미 여러 줄을 받습니다

| 계층 | 위치 | 줄바꿈 |
|---|---|---|
| DB | `V6__create_raid_items_table.sql` `description/response VARCHAR(2000)` | ✅ |
| 요청 DTO | `RaidItemRequest` `@Size(max = 2000)` (형식 제약 없음) | ✅ |
| API 클라이언트 | `frontend/src/api/raidApi.ts:9,23,51,58` `string \| null` | ✅ |
| **입력 화면** | `RaidForm.vue:216,294` `<input type="text">` | ❌ **여기만 막혀 있음** |
| 목록 표시 | `RaidList.vue:85-89` | △ 한 줄로 접힘 — **의도한 동작**(§2.3) |
| CSV 내보내기 | `shared/csv.ts:14` `escapeField` | ✅ `\r\n` 포함 시 따옴표로 감쌈 (테스트 `csv.spec.ts:12`) |
| JSON 내보내기·가져오기 | 문자열 그대로 | ✅ |

따라서 **마이그레이션도, DTO 수정도, API 변경도 필요 없습니다.** 순수 UI 작업입니다.

---

## 2. 결정된 방향

### 2.1 제목은 한 줄로 둔다

`title`은 "한 줄로 요약"이고, 표의 행 머리·대화상자 제목(`항목 수정 — {title}`,
`RaidForm.vue:144`)·대시보드 카드(`DashboardView.vue:380`)에 그대로 박힙니다. 여러 줄이 되면
그 자리들이 전부 어그러집니다. **`title`은 건드리지 마세요.**

### 2.2 설명·대응 방안만 `<textarea>`로 바꾼다

자유 서술이 정상인 칸은 이 둘뿐입니다.

### 2.3 표는 지금처럼 한 줄로 보여준다

CLAUDE.md의 표 규칙(`th, td { white-space: nowrap }` + `.cell-clip`)을 그대로 따릅니다. HTML이
줄바꿈을 공백으로 접으므로 **표에서는 아무것도 바뀌지 않습니다** — 행 높이가 들쭉날쭉해지지
않습니다. 전체 내용은 `title` 네이티브 툴팁이 줄바꿈까지 그대로 보여줍니다.

다만 **대응 방안(`RaidList.vue:89`)에는 `.cell-clip`이 빠져 있습니다.** 설명에는 있는데
(`:86-88`) 대응 방안에는 없어서, 긴 값 하나가 표를 화면 몇 배로 늘립니다. 여러 줄 입력이
가능해지면 그 값이 실제로 길어지므로 이번에 함께 맞춥니다.

### 2.4 자동 높이 조절(auto-grow) 스크립트는 넣지 않는다

`rows` + `resize: vertical`로 충분합니다. JS auto-grow를 넣으면 (1) 대화상자 높이와 배경 스크롤
잠금 계산이 흔들리고, (2) `watch(() => props.editing)`으로 값이 프로그램에서 바뀔 때마다 높이를
다시 계산해 줘야 하며, (3) 사용자가 직접 늘려 둔 높이를 덮어씁니다. 얻는 것에 비해 들어가는
상태가 많습니다.

---

## 3. 작업

### 3.1 `frontend/src/features/raid/RaidForm.vue` — 템플릿

**설명 (`:213-218`)**

```vue
<!-- before -->
<label class="grow">
  설명
  <input v-model="form.description" type="text" placeholder="선택" />
</label>

<!-- after -->
<label class="grow">
  설명
  <textarea
    v-model="form.description"
    rows="4"
    maxlength="2000"
    placeholder="선택 — Ctrl+Enter로 저장"
    @keydown.ctrl.enter.prevent="onSubmit"
    @keydown.meta.enter.prevent="onSubmit"
  ></textarea>
</label>
```

**대응 방안 (`:291-296`)** — 같은 방식, `rows="3"`, placeholder는 `"선택"` 유지.

지킬 것:

- **`v-model`과 `placeholder`의 의미를 바꾸지 마세요.** `form.description`은 `string | null`이고
  빈 문자열이 들어가는 것은 지금과 같습니다 — 저장 경로(`onSubmit`)는 그대로 둡니다.
- **`<textarea>`는 self-closing 하지 말고 `></textarea>`로 닫습니다.** 안쪽 공백이 초기값이 되므로
  태그 사이에 아무것도 넣지 마세요.
- **Ctrl/Cmd+Enter 저장을 함께 넣습니다.** Enter가 줄바꿈이 되는 순간 그 칸에서 저장하는 방법이
  사라집니다. `onSubmit`은 이미 `submittable` 검사를 자기 안에서 하므로(`:173`) 그대로 부르면
  됩니다. macOS는 Cmd라 두 수식키를 모두 답니다.
- **제목 입력칸의 Enter 저장은 그대로 유지됩니다**(submit 버튼이 있는 폼의 암묵적 제출). 건드리지 마세요.
- `maxlength="2000"`은 서버 `@Size(max = 2000)`와 같은 값입니다. 다 쓰고 나서 400으로 거부당하는
  것보다 애초에 못 넘기는 편이 낫습니다. **서버 검증을 대신하는 것이 아니라 덧대는 것이므로,
  서버 쪽 제약을 지우지 마세요.**

### 3.2 `RaidForm.vue` — 스타일

```css
/* :447 — textarea 를 반드시 이 선택자에 함께 넣는다 */
input,
select,
textarea {
  padding: 0.4rem 0.55rem;
  border: 1px solid var(--border-input);
  border-radius: 6px;
  font: inherit;
  font-size: 0.85rem;
}

textarea {
  width: 100%;
  box-sizing: border-box;
  resize: vertical;      /* 가로로 늘리면 대화상자 폭을 넘는다 */
  min-height: 3.2rem;
  line-height: 1.45;
}
```

- **`font: inherit`에 `textarea`를 빠뜨리는 것이 이 작업에서 가장 흔한 실수입니다.** 브라우저
  기본값이 monospace라, 그 칸만 다른 글꼴로 렌더돼 폼이 어긋나 보입니다.
- `label.grow input { width: 100% }` (`:427`)에도 `textarea`를 더하거나, 위 `textarea` 규칙이
  `width: 100%`를 이미 갖고 있으므로 그대로 두어도 됩니다 — **둘 중 하나는 반드시 있어야 합니다.**
- **새 색을 정의하지 마세요.** 기존 토큰(`--border-input`)만 씁니다. 컴포넌트 스코프에 색을
  하드코딩하면 그 자리만 라이트로 남습니다(CLAUDE.md 다크 모드 절). `color-scheme`이 이미
  테마별로 지정돼 있어 네이티브 스크롤바와 캐럿은 따라옵니다.
- `.row { align-items: flex-end }` (`:406`)는 그대로 둡니다. 설명·대응 방안 행은 `label` 하나뿐이라
  영향이 없습니다. 다만 나중에 그 행에 다른 필드를 붙이면 textarea **바닥**에 정렬된다는 점만
  기억하세요.

### 3.3 `frontend/src/features/raid/RaidList.vue` — 대응 방안에도 폭 상한

```vue
<!-- :89  before -->
<span v-if="item.response" class="response">↳ {{ item.response }}</span>

<!-- after -->
<span v-if="item.response" class="response cell-clip" :title="item.response">↳ {{ item.response }}</span>
```

`.cell-clip`은 전역 유틸리티(`style.css:256`)라 새 스타일이 필요 없습니다. `.description,
.response` 규칙(`:247`)의 `display: block`은 `.cell-clip`과 같은 값이라 충돌하지 않습니다.

---

## 4. 건드리지 않는 것

| 대상 | 이유 |
|---|---|
| 백엔드 전체 (`RaidItem`, `RaidItemRequest`, `RaidService`, 마이그레이션) | 이미 2000자 문자열이고 줄바꿈 제약이 없음 |
| `shared/csv.ts` | `escapeField`가 `\r\n`을 이미 RFC 4180대로 따옴표 처리, 테스트로 고정됨 |
| `shared/exportRows.ts` | 값을 그대로 넘기기만 함 |
| `ModalDialog.vue` | 포커스 트랩 셀렉터(`:32`)와 초기 포커스 셀렉터(`:69`)에 `textarea`가 **이미** 들어 있음. Esc는 document 레벨이라 textarea 안에서도 닫힘. 초기 포커스는 지금처럼 "종류" select |
| `useRaid.ts`, `raidFilter.ts`, 캐시 키 | 저장 값의 모양이 바뀌지 않음 |
| 제목·연결 대상·확률·영향·소유자·기한 | 이번 범위 밖 |

---

## 5. 확인 방법

`cd frontend && npm run build` (vue-tsc 타입체크 포함), `npm test` — 기존 테스트가 깨지지 않아야
합니다. 이 작업에 새 단위 테스트는 필요 없습니다(순수 함수가 아니라 템플릿·스타일 변경이고,
줄바꿈 CSV 처리는 `csv.spec.ts:12`가 이미 덮고 있습니다).

수동 확인:

1. RAID → `＋ 항목 추가` → 설명에 세 줄 입력. **Enter가 줄바꿈으로 동작하고 저장되지 않아야** 합니다.
2. Ctrl+Enter(또는 Cmd+Enter)로 저장됩니다.
3. 제목 칸에서 Enter → 예전처럼 저장됩니다.
4. 표에서 그 행이 **한 줄로** 보이고, 마우스를 올리면 툴팁에 세 줄 그대로 나옵니다.
5. `수정`으로 다시 열면 세 줄이 그대로 남아 있습니다(왕복 보존).
6. 대응 방안에 긴 값을 넣어도 표가 가로로 늘어나지 않고 `…`로 잘립니다(§3.3).
7. 다크 모드에서 textarea의 테두리·배경·글꼴이 옆의 input·select와 같습니다.
8. 프로젝트 화면 → 내보내기 → RAID(CSV)를 Excel에서 열면 셀 **안에서** 줄이 바뀝니다.
9. 프로젝트 전체(JSON) 내보내기 → 가져오기 → 줄바꿈이 보존됩니다.
10. Esc로 닫히고, Tab이 textarea를 지나 순환합니다.

---

## 6. 이번에 하지 않는 것 (후속 후보)

같은 문제가 남아 있는 곳입니다. **이번 작업 범위가 아닙니다** — RAID에서 모양이 확정된 뒤
같은 패턴으로 옮기는 편이 낫습니다.

- `frontend/src/features/wbs/WbsForm.vue:114` — WBS 항목 설명
- `frontend/src/features/backlog/BacklogForm.vue:229` — Backlog 항목 설명

`ProjectForm.vue:66`은 이미 `<textarea rows="2">`입니다. 세 폼이 같은 모양이 되면 그때
`.field-textarea` 같은 전역 규칙으로 묶는 것을 검토하세요(지금 미리 만들면 쓰는 곳이 하나입니다).
