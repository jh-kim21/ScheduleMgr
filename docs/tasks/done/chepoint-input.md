# 체크포인트 입력 개선 — 구현 지시서

> 구현을 맡을 에이전트/개발자용 작업 지시서입니다. 작업 전에 [CLAUDE.md](../CLAUDE.md)의
> "진척 집계 설계상 알아둘 점", "화면 레이아웃 규칙", "화면 간 상태 유지" 절을 먼저 읽으세요.

## 1. 해결할 문제

### 문제 1 — 체크포인트를 수정할 수 없다
백엔드·API 클라이언트·컴포저블에는 수정 기능이 **모두 있는데**, 화면이 연결하지 않았습니다.

| 계층 | 위치 | 상태 |
|---|---|---|
| 백엔드 API | `PUT /api/projects/{projectId}/progress/checkpoints/{checkpointId}` | ✅ |
| API 클라이언트 | `frontend/src/api/progressApi.ts:107` `updateCheckpoint` | ✅ |
| 컴포저블 | `frontend/src/features/progress/useProgress.ts:81` (export `:132`) | ✅ |
| **화면** | `frontend/src/features/progress/ProgressPanel.vue` | ❌ **호출 안 함** |

`ProgressPanel.vue`가 `useProgress()`에서 꺼내 쓰는 것은
`addCheckpoint, setApproval, deleteCheckpoint, approveBaseline, saveSnapshot` 뿐이고
`updateCheckpoint`가 빠져 있습니다. 프론트엔드 전체에서 호출하는 곳이 없습니다.

그 결과 체크포인트의 **제목·가중치·완료조건을 한번 만들면 고칠 수 없고**, 삭제 후 다시
만들어야 합니다.

### 문제 2 — 입력 위치가 멀고 화면이 난잡하다
WBS에서 실행 방식을 `WATERFALL`로 바꾼 직후, 정작 체크포인트를 넣으려면 **Dashboard 진척
탭**으로 이동해 해당 Work Package를 다시 찾아야 합니다. 그 화면에는 baseline 승인·보고
스냅샷·전체 Work Package 진척 목록이 함께 있어 입력하기에 밀도가 높습니다.

게다가 같은 폼(`WbsForm.vue`)에 **`weight`와 `agileRatio`는 이미 들어가 있는데 체크포인트만
빠져 있어** 일관성이 없습니다.

## 2. 결정된 방향

체크포인트를 **정의하는 것은 계획 행위**이고, **승인하는 것은 반복되는 운영 행위**입니다.
둘을 성격에 맞는 화면에 나눕니다.

| 행위 | 위치 | 이유 |
|---|---|---|
| 추가 · 수정 · 삭제 | **WBS 폼** (`WbsForm.vue`) | 실행 방식을 정하는 그 자리에서 함께 정의 |
| **승인 · 승인 취소** | **진척 탭** (`ProgressPanel.vue`) 유지 | 여러 Work Package를 한 화면에서 훑으며 처리하는 편이 낫다. 체크 하나 하려고 WBS 편집 대화상자를 여는 것은 불편하다 |

진척 탭에는 **목록과 승인 버튼만 남기고 편집 UI는 제거**합니다. 이로써 Dashboard의 밀도가
낮아지고, 그 화면은 원래 성격(baseline·스냅샷·조망)에 집중합니다.

---

## 3. Phase 1 — 체크포인트 UI 컴포넌트 추출 (+ 수정 기능)

> **수정 기능을 별도 작업으로 먼저 붙이지 마세요.** 어차피 이 컴포넌트로 옮겨지므로,
> `ProgressPanel`에 붙였다가 Phase 2에서 다시 옮기면 헛일이 됩니다. 추출하면서 같이 넣습니다.

### 3.1 신규 `frontend/src/features/progress/CheckpointList.vue`

`ProgressPanel.vue`의 체크포인트 목록(`ul.checkpoints`)과 추가 폼(`div.cp-form`)을 옮겨 옵니다.

**Props**
```ts
projectId: number
wbsItemId: number
checkpoints: CheckpointDetail[]   // progressApi.ts:5
editable: boolean                 // false면 목록 + 승인만 (진척 탭에서 사용)
```

**행 구성**: 제목 · 가중치 · 완료조건 · 승인 상태 + 버튼
- `editable === true` → `[수정] [승인/승인취소] [삭제]`
- `editable === false` → `[승인/승인취소]` 만

**폼을 추가/수정 겸용으로**
```ts
const editing = ref<{ id: number; title: string; weight: number | null; criteria: string } | null>(null)
```
- `editing`이 있으면 입력칸에 기존 값을 채우고 버튼을 **저장**으로, 없으면 지금처럼 **추가**로
- 저장: `updateCheckpoint(projectId, editing.id, { title, weight, completionCriteria })`
- 추가: 기존 `addCheckpoint` 그대로
- **취소 버튼**을 함께 둘 것 — 수정 모드에서 빠져나갈 길이 있어야 합니다

**주의**
- `useProgress()`의 변경 함수들은 **성공 여부를 반환**합니다. 성공했을 때만 `editing`을
  비우고 입력칸을 초기화하세요. 거부되면 입력값이 남아 있어야 합니다 (CLAUDE.md 화면 규칙)
- 가중치는 `null` 허용이며 화면에서 `균등`으로 표시합니다. `0`과 `null`은 다릅니다 —
  모든 가중치가 0이면 분모가 0이 되어 진척이 **산정 전**이 됩니다
- 색은 반드시 `style.css`의 CSS 변수만 사용 (컴포넌트에서 토큰 재정의 금지)

### 3.2 변경 `ProgressPanel.vue`

- 구조 분해에서 체크포인트 관련 함수를 정리하고, 추출한 컴포넌트를 그 자리에 배치
- 이 시점에서는 `editable`을 `true`로 두어 **동작이 지금과 같되 수정만 가능해진** 상태로 만든다
  (Phase 3에서 `false`로 바꿈)

**이 단계까지만 해도 문제 1은 해결됩니다.**

---

## 4. Phase 2 — WBS 폼에 인라인 배치

### 4.1 변경 `frontend/src/features/wbs/WbsForm.vue`

- `executionMode`가 `WATERFALL` 또는 `HYBRID`일 때 `CheckpointList`를 `editable` 로 표시
- 위치: 기존 `weight` / `agileRatio` 필드 바로 아래 (같은 성격의 값들이라 맥락이 같음)
- `features/wbs`에서 `features/progress`의 컴포넌트를 import 하게 되는데, 이 앱은 이미
  기능 간 참조가 있으므로(RACI 화면이 Backlog 담당자를 읽음) 문제 없습니다

### 4.2 반드시 지킬 것

**모달 중첩 금지.** `WbsForm`은 이미 `ModalDialog` 안에서 열립니다. 체크포인트 추가·수정은
**별도 대화상자 없이 인라인 행**으로 처리하세요. 대화상자 안에 대화상자를 띄우면 안 됩니다.

**신규 항목에서는 비활성화.** 체크포인트는 `wbsItemId`가 필요한데, 아직 저장되지 않은 WBS
항목에는 id가 없습니다. 이 경우 입력 영역을 비활성화하고 안내를 표시하세요.
> "항목을 저장한 뒤 다시 열면 체크포인트를 등록할 수 있습니다."

**체크포인트 변경은 즉시 반영됩니다.** 체크포인트 API는 WBS 저장과 별개 경로라,
추가·수정·삭제가 **그 자리에서 곧바로 서버에 반영**됩니다. WBS 폼에서 "취소"를 눌러도
이미 추가한 체크포인트는 남습니다. 사용자가 오해하지 않도록 영역 상단에 한 줄 적으세요.
> "체크포인트 변경은 즉시 저장됩니다."

(폼 저장 시점까지 모아서 커밋하는 방식은 별도 상태 관리가 필요해 이번 범위에서 제외합니다.)

---

## 5. Phase 3 — 진척 탭 정리

`ProgressPanel.vue`에서 `CheckpointList`에 넘기는 `editable`을 **`false`** 로 바꿉니다.

- 남는 것: 체크포인트 **목록 표시 + 승인/승인 취소**
- 사라지는 것: 추가 폼, 수정 버튼, 삭제 버튼
- 체크포인트가 하나도 없는 Work Package의 안내 문구는 유지하되, 등록 경로를 알려주도록 수정
  > "체크포인트가 없습니다. WBS 화면에서 해당 업무를 편집해 등록하세요. Waterfall·Hybrid
  > 진척은 이 목록이 분모이므로, 없으면 산정 전입니다."

---

## 6. Phase 4 — 테스트 · 문서

**단위 테스트 (vitest)**
- `CheckpointList`: 추가 / 수정 / 삭제 / 승인·승인취소
- `CheckpointList`: 수정 버튼을 누르면 **기존 값이 폼에 채워지는지**
- `CheckpointList`: `editable: false`일 때 추가·수정·삭제 UI가 렌더링되지 않는지
- `CheckpointList`: 저장이 거부되면 입력값이 유지되는지
- `WbsForm`: 실행 방식별 조건부 표시(`WATERFALL`/`HYBRID`에만), 신규 항목에서 비활성화

**수동 확인**
- WBS에서 Work Package를 `WATERFALL`로 지정 → 같은 폼에서 체크포인트 추가 → **가중치 수정**
  → 진척 % 가 갱신되는지
- 진척 탭에서 승인 → WBS 폼에서도 승인 상태가 보이는지
- 진척 탭에 추가/수정/삭제 UI가 더 이상 없는지

**문서**
- [CLAUDE.md](../CLAUDE.md) — "진척 집계 설계상 알아둘 점"과 "화면 구조" 절에 체크포인트 입력
  위치가 WBS 폼으로 옮겨졌음을 반영
- [운영 안내](agile_design/results/Hybrid_PM_Operation_Guide.md)에 체크포인트 등록 경로 설명이
  있으면 함께 갱신

---

## 7. 완료 기준

- [ ] 체크포인트의 **제목·가중치·완료조건을 수정**할 수 있다
- [ ] 수정 버튼을 누르면 기존 값이 폼에 채워지고, 취소로 빠져나올 수 있다
- [ ] 저장이 거부되면 입력값이 사라지지 않는다
- [ ] WBS 폼에서 `WATERFALL`/`HYBRID`를 고르면 그 자리에서 체크포인트를 추가·수정·삭제할 수 있다
- [ ] 신규(미저장) WBS 항목에서는 체크포인트 영역이 비활성화되고 안내가 보인다
- [ ] WBS 폼 안에 **대화상자가 중첩되지 않는다**
- [ ] 진척 탭에는 목록과 승인 버튼만 남는다 (추가·수정·삭제 없음)
- [ ] 체크포인트를 바꾸면 해당 Work Package의 진척 %와 대시보드 숫자가 갱신된다
      (`useProgress`의 변경이 `markWbsChanged()`를 부르므로 캐시 키가 바뀐다)
- [ ] 다크 모드에서 새로 추가한 UI의 색이 어색하지 않다 (CSS 변수만 사용했는지)

## 8. 참고

- 체크포인트 API: `POST/PUT/DELETE /api/projects/{projectId}/progress/checkpoints[/{id}]`,
  승인은 `PUT .../{checkpointId}/approval`
- 모든 변경 API가 **진척 payload 전체를 반환**합니다 (체크포인트 하나를 승인하면 그 Work
  Package의 진척과 상위 집계가 함께 움직이므로)
- WATERFALL 진척식: `100 × Σ(승인된 체크포인트 가중치) / Σ(전체 체크포인트 가중치)`
  — 체크포인트가 없거나 가중치 합이 0이면 **산정 전(`null`)**이며 0%가 아닙니다
  ([`ProgressCalculator.java:132-146`](../backend/src/main/java/com/projectflow/domain/ProgressCalculator.java))
