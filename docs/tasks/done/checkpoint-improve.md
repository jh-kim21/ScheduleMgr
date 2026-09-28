# 체크포인트 입력 개선 — 구현 지시서

> 구현을 맡을 에이전트/개발자용 작업 지시서입니다. 작업 전에 [CLAUDE.md](../CLAUDE.md)의
> "진척 집계 설계상 알아둘 점", "화면 레이아웃 규칙", "화면 간 상태 유지", "화면 구조" 절을
> 먼저 읽으세요.

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
`addCheckpoint, setApproval, deleteCheckpoint, approveBaseline, saveSnapshot` 뿐입니다.
프론트엔드 전체에서 `updateCheckpoint`를 호출하는 곳이 없습니다.

그 결과 체크포인트의 **제목·가중치·완료조건을 한번 만들면 고칠 수 없고**, 삭제 후 다시
만들어야 합니다.

### 문제 2 — 실행 방식별로 "진척을 올리는 곳"이 비대칭이다

| 실행 방식 | 진척을 올리는 행위 | 현재 위치 |
|---|---|---|
| **AGILE** | Story·Bug를 `DONE`으로 전이 | Board / Backlog (자기 작업 화면) |
| **WATERFALL** | 체크포인트 승인 | **Dashboard 진척 탭** ← 여기만 다름 |

Agile은 자기 작업 화면에서 진척이 오르고 진척 탭에는 결과 숫자만 나타나는데, Waterfall만
진척 탭 안에서 직접 올립니다. 게다가 체크포인트를 **정의하는 것**(계획 행위)까지 진척 탭에
있어서, 실행 방식을 `WATERFALL`로 지정한 직후 다른 화면으로 이동해 그 Work Package를 다시
찾아야 합니다.

참고로 Work Package 단위의 다른 승인 성격 값인 **인수(`acceptanceStatus`)는 이미 WBS
폼**(`WbsForm.vue:172`)에 있습니다. 체크포인트만 떨어져 있는 셈입니다.

---

## 2. 결정된 방향

원칙: **진척을 올리는 행위는 각자의 작업 화면에서 한다. 진척 탭은 그 결과를 본다.**

| 화면 | 역할 | 하는 일 |
|---|---|---|
| **WBS** | Work Package 단위의 모든 것 | 실행 방식·가중치·α·인수 + **체크포인트 정의·수정·삭제·승인** |
| **Board / Backlog** | Agile 실행 | Story·Bug 완료 전이 (변경 없음) |
| **Dashboard 진척** | **조망 + 프로젝트 레벨 판단** | 진척 읽기, 기준선 승인, 보고 스냅샷 저장 |

- 체크포인트는 **WBS 트리에서 Work Package 행을 펼쳐 인라인으로** 다룬다 (대화상자 없음)
- 진척 탭에서는 체크포인트가 **읽기 전용**이 된다 (추가·수정·삭제·승인 모두 제거)
- **기준선 승인과 보고 스냅샷 저장은 진척 탭에 남긴다.** Work Package 단위 실행이 아니라
  프로젝트 레벨 판단이고, 진척 숫자를 보면서 내리는 결정이기 때문이다

### 데이터 모델은 바꾸지 않는다

체크포인트를 **진짜 WBS 항목으로 만들지 않는다.** `acceptance_checkpoints` 테이블을 그대로
두고 **화면에서만 자식처럼 그린다.** 진짜 자식으로 만들면 아래가 전부 깨진다.

- CLAUDE.md 규칙 — *"**Work Package에는 하위를 둘 수 없습니다**(생성·이동 모두 400)"*
- `WbsNode.summary()`의 "자식이 있으면 집계 노드" 판정 (체크포인트를 하나 추가하는 순간
  그 Work Package의 일정이 하위 파생 값으로 바뀌어 입력한 날짜가 화면에서 사라진다)
- 진척 계산 이중화 — WATERFALL 공식과 일반 rollUp이 같은 일을 하게 된다
- 승인자·승인일이 갈 자리가 없어진다 (WBS 항목은 진행률만 가진다)

---

## 3. Phase 1 — 체크포인트 UI 컴포넌트 추출 (+ 수정 기능)

> **수정 기능을 별도 작업으로 먼저 붙이지 마세요.** 어차피 이 컴포넌트로 옮겨지므로,
> `ProgressPanel`에 붙였다가 Phase 2에서 다시 옮기면 헛일이 됩니다. 추출하면서 같이 넣습니다.

### 3.1 신규 `frontend/src/features/progress/CheckpointList.vue`

`ProgressPanel.vue`의 체크포인트 목록(`ul.checkpoints`)과 추가 폼(`div.cp-form`)을 옮겨 옵니다.

> 위치를 `features/progress`로 두는 이유: 체크포인트 API가 `/progress/checkpoints`이고 도메인
> 소유가 그쪽입니다. `features/wbs`에서 import 하게 되는데, 이 앱은 이미 기능 간 참조가
> 있으므로(RACI 화면이 Backlog 담당자를 읽음) 문제 없습니다.

**Props**
```ts
projectId: number
wbsItemId: number
checkpoints: CheckpointDetail[]   // progressApi.ts:5
editable: boolean                 // false면 순수 읽기 전용 (승인 버튼도 없음)
```

**행 구성**: 제목 · 가중치 · 완료조건 · 승인 상태(+승인자·승인일)
- `editable === true` → `[수정] [승인/승인취소] [삭제]` + 하단에 추가/수정 겸용 폼
- `editable === false` → 버튼 없음. 표시만

**폼을 추가/수정 겸용으로**
```ts
const editing = ref<{ id: number; title: string; weight: number | null; criteria: string } | null>(null)
```
- `editing`이 있으면 입력칸에 기존 값을 채우고 버튼을 **저장**으로, 없으면 **추가**로
- 저장: `updateCheckpoint(projectId, editing.id, { title, weight, completionCriteria })`
- 추가: 기존 `addCheckpoint` 그대로
- **취소 버튼**을 반드시 둘 것 — 수정 모드에서 빠져나갈 길이 있어야 합니다

**주의**
- `useProgress()`의 변경 함수들은 **성공 여부를 반환**합니다. 성공했을 때만 `editing`을 비우고
  입력칸을 초기화하세요. 거부되면 입력값이 남아 있어야 합니다 (CLAUDE.md 화면 규칙)
- 가중치는 `null` 허용이며 화면에 `균등`으로 표시합니다. **`0`과 `null`은 다릅니다** — 모든
  가중치가 0이면 분모가 0이 되어 진척이 **산정 전**이 됩니다
- 색은 `style.css`의 CSS 변수만 사용 (컴포넌트에서 토큰 재정의 금지)

### 3.2 변경 `ProgressPanel.vue`

추출한 컴포넌트를 그 자리에 배치합니다. 이 시점에는 `editable`을 `true`로 두어 **동작이
지금과 같되 수정만 가능해진** 상태로 만듭니다 (Phase 3에서 `false`로 바꿈).

**이 단계까지만 해도 문제 1은 해결됩니다.**

---

## 4. Phase 2 — WBS 트리에 통합

### 4.1 화면 모양

```
▸ 1.2   화면 개발                      [Summary]
  ▾ 1.2.1 로그인 화면 개발              [Work Package · WATERFALL]  60%  승인 1/3
      ◇ 설계서 승인      가중치 30   승인 · 김재학 · 03-02   [수정] [승인취소] [삭제]
      ◇ 코드 리뷰 완료   가중치 30   미승인                  [승인] [수정] [삭제]
      ◇ 최종 검수        가중치 40   미승인                  [승인] [수정] [삭제]
      ＋ 체크포인트 추가
  ▸ 1.2.2 대시보드 개발                 [Work Package · AGILE]      40%
```

- 실행 방식이 `WATERFALL` 또는 `HYBRID`인 Work Package에만 펼침 가능
- **기본은 접힘.** WBS는 구조를 보는 화면이므로 평소엔 방해하지 않아야 합니다
- 접힌 상태에서도 행에 **`승인 1/3` 배지**를 표시 (펼치지 않아도 상태를 알 수 있게)

### 4.2 반드시 지킬 것

**하위 항목처럼 보이게 하지 말 것.** Work Package에는 하위를 둘 수 없다는 규칙이 있으므로,
체크포인트 행이 WBS 자식으로 오인되면 안 됩니다.

- **WBS 코드를 주지 않는다** (`1.2.1.1` 같은 걸 붙이면 진짜 항목으로 읽힙니다). `◇` 기호나
  다른 배경색으로 구분
- **펼침 아이콘을 Summary의 삼각형과 다르게** 한다. 같은 모양이면 "이 Work Package에 하위가
  있다"로 읽힙니다. `승인 1/3` 배지 자체를 클릭 대상으로 삼는 편이 안전합니다
- **드래그 이동 불가** — 순서에 의미가 없고 다른 Work Package로 옮기는 개념도 없습니다
- **선후행 관계 대상에서 제외** — 관계 걸기 UI에 나타나지 않아야 합니다
- 지연 배지·진행률 막대 없음 (승인/미승인 두 상태뿐)

**WBS 폼에서는 체크포인트를 다루지 않는다.** 폼은 실행 방식·가중치·α·인수만 고릅니다.
폼은 이미 `ModalDialog` 안이라 목록을 넣으면 대화상자 안에 목록이 겹치고, 폼의 "저장/취소"와
체크포인트의 즉시 저장이 한 화면에서 엇갈립니다. **트리에서 다루면 즉시 저장이 자연스럽습니다**
(트리의 다른 동작 — 이동·정렬 — 도 원래 즉시 반영됩니다).

### 4.3 데이터를 어디서 가져오나

**권장: 프론트엔드에서 `useProgress`를 함께 읽어 `wbsItemId`로 매칭한다.**

- `/progress` 응답의 `workPackages[]`에 이미 각 Work Package의 `checkpoints`,
  `checkpointApproved`, `checkpointTotal`이 들어 있습니다 (`progressApi.ts:29-32`)
- WBS 화면에서 `useProgress().ensureLoaded(projectId)`를 호출하고 인덱싱하면 됩니다
- **캐시 무효화는 이미 동작합니다** — `useProgress`의 모든 변경이 `markWbsChanged()`를
  부르므로 WBS 트리도 함께 갱신됩니다
- 비용: WBS 화면에서 요청이 하나 늘어납니다

**대안**: WBS 트리 응답에 체크포인트를 실어 보낸다(백엔드 변경). 트리가 이미 연결 Backlog 수를
싣고 있으므로 선례는 있지만, 모든 WBS 조회의 payload가 커집니다. 위 방식의 추가 요청이 문제가
될 때만 택하세요.

---

## 5. Phase 3 — 진척 탭을 조망 전용으로

`ProgressPanel.vue`에서 `CheckpointList`에 넘기는 `editable`을 **`false`** 로 바꿉니다.

- 남는 것: 체크포인트 **목록 표시만** (제목·가중치·승인 상태·승인자·승인일)
- 사라지는 것: 추가 폼, 수정·삭제 버튼, **승인/승인취소 버튼**
- 체크포인트가 없는 Work Package의 안내 문구를 등록 경로를 알려주도록 수정
  > "체크포인트가 없습니다. WBS 화면에서 해당 업무를 펼쳐 등록하세요. Waterfall·Hybrid
  > 진척은 이 목록이 분모이므로, 없으면 산정 전입니다."
- **기준선 승인과 보고 스냅샷 저장은 그대로 둡니다** (프로젝트 레벨 판단이므로)

---

## 6. Phase 4 — 테스트 · 문서

**단위 테스트 (vitest)**
- `CheckpointList`: 추가 / 수정 / 삭제 / 승인·승인취소
- `CheckpointList`: 수정 버튼을 누르면 **기존 값이 폼에 채워지는지**, 취소로 빠져나오는지
- `CheckpointList`: `editable: false`일 때 **모든 버튼과 폼이 렌더링되지 않는지**
- `CheckpointList`: 저장이 거부되면 입력값이 유지되는지
- `WbsTree`: `WATERFALL`/`HYBRID` Work Package에만 펼침이 생기는지, `AGILE`·미지정·Summary에는
  안 생기는지
- `WbsTree`: 체크포인트 행이 드래그 대상이 아닌지

**수동 확인**
- WBS에서 Work Package를 `WATERFALL`로 지정 → 같은 화면에서 펼쳐 체크포인트 추가 →
  **가중치 수정** → 승인 → 진척 %가 갱신되는지
- 접힌 상태에서 `승인 1/3` 배지가 보이는지
- 진척 탭에 추가·수정·삭제·**승인** UI가 더 이상 없는지
- 체크포인트 행이 WBS 하위 항목으로 오인되지 않는지 (코드 없음, 아이콘 구분, 드래그 불가)

**문서**
- [CLAUDE.md](../CLAUDE.md) — "진척 집계 설계상 알아둘 점"과 "화면 구조" 절에 체크포인트
  관리 위치가 WBS 트리로 옮겨졌음을 반영. 진척 탭이 조망 전용(+ 기준선·스냅샷)이 되었음도 기록
- [운영 안내](agile_design/results/Hybrid_PM_Operation_Guide.md)의 체크포인트 등록 경로 설명 갱신

---

## 7. 완료 기준

- [ ] 체크포인트의 **제목·가중치·완료조건을 수정**할 수 있다
- [ ] 수정 버튼을 누르면 기존 값이 폼에 채워지고, 취소로 빠져나올 수 있다
- [ ] 저장이 거부되면 입력값이 사라지지 않는다
- [ ] **WBS 트리에서** Work Package를 펼쳐 체크포인트를 추가·수정·삭제·승인할 수 있다
- [ ] 접힌 상태에서도 `승인 N/M` 배지로 상태를 알 수 있다
- [ ] 체크포인트 행이 WBS 항목으로 오인되지 않는다 (코드 없음 · 아이콘 구분 · 드래그 불가 ·
      선후행 대상 아님)
- [ ] `AGILE`·실행 방식 미지정·Summary 항목에는 체크포인트 UI가 나타나지 않는다
- [ ] WBS 폼(`ModalDialog`)에는 체크포인트가 없다
- [ ] 진척 탭에는 읽기 전용 목록만 있다 (추가·수정·삭제·승인 없음)
- [ ] 진척 탭의 기준선 승인·스냅샷 저장은 그대로 동작한다
- [ ] 체크포인트를 바꾸면 해당 Work Package의 진척 %와 대시보드 숫자가 갱신된다
- [ ] 다크 모드에서 새로 추가한 UI의 색이 어색하지 않다 (CSS 변수만 사용했는지)

## 8. 참고

- 체크포인트 API: `POST/PUT/DELETE /api/projects/{projectId}/progress/checkpoints[/{id}]`,
  승인은 `PUT .../{checkpointId}/approval`
- 모든 변경 API가 **진척 payload 전체를 반환**합니다 (체크포인트 하나를 승인하면 그 Work
  Package의 진척과 상위 집계가 함께 움직이므로)
- WATERFALL 진척식: `100 × Σ(승인된 체크포인트 가중치) / Σ(전체 체크포인트 가중치)`
  — 체크포인트가 없거나 가중치 합이 0이면 **산정 전(`null`)**이며 0%가 아닙니다
  ([`ProgressCalculator.java:132-146`](../backend/src/main/java/com/projectflow/domain/ProgressCalculator.java))
- 인수(`acceptanceStatus`)는 진척과 **직교**합니다. 실행이 100%여도 인수가 남으면 완료로 보지
  않습니다. 이 값은 WBS 폼(`WbsForm.vue:172`)에 있고 이번 작업에서 건드리지 않습니다
