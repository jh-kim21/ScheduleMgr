# Step 04. Sprint 및 Board 구현 (결과 보고서)

| 구분 | 내용 |
|---|---|
| 대상 저장소 | `D:\Git\ScheduleMgr` (branch `main`) |
| 지시서 | [Hybrid_PM_Step04_Sprint_Board.md](../Hybrid_PM_Step04_Sprint_Board.md) |
| 선행 보고서 | [Step 01](Hybrid_PM_Step01_Analysis.md) · [Step 02](Hybrid_PM_Step02_Result.md) · [Step 03](Hybrid_PM_Step03_Result.md) |
| 참고 설계 | [WBS_Agile_Hybrid_PM_Design.md](../WBS_Agile_Hybrid_PM_Design.md) |
| 작성일 | 2026-09-07 |
| 상태 | **완료.** Step 5는 자동 실행하지 않았다. |

## 1. 요약

Sprint 계획 → Board 실행 → 종료 → 미완료 이월까지 한 흐름으로 동작한다.

- **단일 팀으로 결정**(사용자 결정)했으므로 `teams` 테이블도 `sprints.team_id`도 두지 않았다.
  그 대신 **실행 중인 Sprint는 프로젝트당 하나**라는 규칙을 세웠고, 이것이 "동시에 두 활성 Sprint에
  배정하지 않는다"를 자연히 보장한다.
- **배정은 지우지 않는다.** 제거는 `removed_at`을 찍고, 종료는 `outcome`과 `points_at_close`를
  남긴다. 그래서 이월된 항목을 다음 Sprint에서 완료해도 **지난 Sprint의 실적은 움직이지 않는다** —
  완료 실적은 정확히 한 Sprint에만 쌓인다(실측 확인).
- **차단은 상태와 직교**하다(`blocked`/`blocked_reason`). 칸은 그대로 두고 표시만 하며, 차단된 항목은
  완료할 수 없다.
- **최소 완료 절차**를 두었다: 완료로 옮기려면 확인 표시가 필요하고, 차단 중이면 거부한다.
  **Task를 모두 완료해도 Story는 자동 완료되지 않는다**(테스트로 고정). Board와 Backlog 폼 **양쪽**에
  같은 규칙을 적용했다 — 한쪽만 막으면 절차가 장식이 된다.
- **재오픈은 현재 상태와 과거 실적을 구분**한다. 다시 열면 `done_at`이 비워지고 `reopened`로 표시되며,
  종료된 Sprint가 찍어 둔 `DONE`은 그대로 남는다.
- 검증: 백엔드 **156건**(+25), 프론트엔드 **89건**(+4) 전부 통과. Sprint 전체 흐름·거부 경로·이월
  회계·내보내기 왕복을 API로 실측했다.

## 2. 변경 내용

### 2.1 스키마 (신규 마이그레이션 3개)

| 파일 | 내용 |
|---|---|
| `V12__add_backlog_execution_fields.sql` | `backlog_items.blocked`, `blocked_reason`, `done_at` |
| `V13__create_sprints_table.sql` | `sprints` |
| `V14__create_sprint_items_table.sql` | `sprint_items` (배정 + 결과) |

**`sprints`**: name(프로젝트 내 UNIQUE), goal, start_date/end_date(둘 다 NOT NULL, `start <= end` CHECK),
status(`PLANNED`/`ACTIVE`/`CLOSED`), closed_at.

**`sprint_items`**: sprint_id, backlog_item_id, added_at, `removed_at`(NULL=현재 배정),
`points_at_start`, `points_at_close`, `outcome`(`DONE`/`CARRIED_OVER`/`REMOVED`).

V12는 이미 `DONE`인 항목의 `done_at`을 `updated_at`으로 채운다. 비워 두면 "Done인데 완료 시각이 없는"
항목이 남아 재오픈 판정이 흔들린다.

### 2.2 백엔드

**신규**: `domain/Sprint`, `SprintItem`, `SprintStatus`, `SprintItemOutcome`, `SprintAssessor`,
`CompletionCheck`, `SprintRepository`, `SprintItemRepository`, `SprintNotFoundException`,
`InvalidSprintException`, 두 개의 persistence 어댑터,
`application/SprintService`, `application/dto/SprintRequests`, `SprintResponse`,
`presentation/SprintController`.

**수정**

| 파일 | 변경 |
|---|---|
| `domain/BacklogItem.java` | `blocked`/`blockedReason`/`doneAt` + `changeStatus`. 상태 변경을 **하나의 `applyStatus`** 로 모아, 이미 완료인 항목을 다시 저장할 때 완료 시각이 밀리지 않게 했다 |
| `application/BacklogService.java` | 완료 절차 게이트(폼 경로), 진행 중 Sprint 배정 항목의 보관·삭제 거부, 응답에 `blocked`/`doneAt`/`openSprintName` |
| `application/dto/BacklogItemRequest.java`, `BacklogResponse.java` | 위 필드 |
| `application/ExportService.java`, `ImportService.java` | Sprint·배정·실행 상태 + `FORMAT_VERSION` 3 → **4** |
| `presentation/GlobalExceptionHandler.java` | 새 예외 두 개 등록 (§4.3) |

### 2.3 프론트엔드

| 파일 | 변경 |
|---|---|
| `shared/sprint.ts` (+ `.spec.ts`) | 타입·라벨·기간 표기·남은 기간 |
| `api/sprintApi.ts` | 클라이언트 |
| `features/sprint/useSprints.ts` | 컴포저블 (모듈 스코프 공유) |
| `features/sprint/SprintBoard.vue` | 4열 Board, 드래그 이동, 완료 확인·차단 대화상자 |
| `features/sprint/SprintForm.vue` | Sprint 생성·수정 |
| `views/SprintView.vue` | Sprint 목록·현황·배정·종료 대화상자 |
| `router/index.ts`, `App.vue` | `/sprint` 라우트와 메뉴 |
| `stores/scheduleCache.ts` | Sprint 리비전 분리 (§3.6) |
| `api/backlogApi.ts`, `features/backlog/BacklogList.vue`, `BacklogForm.vue` | 차단·Sprint 배정 표시, 완료 확인 체크박스 |
| `features/backlog/backlogFilter.spec.ts`, `shared/exportRows.ts` | 새 필드 반영, CSV `차단` 열 |

## 3. 결정과 근거

### 3.1 단일 팀 — 팀 컬럼을 두지 않는다

**결정:** `teams` 테이블도 `sprints.team_id`도 만들지 않았다.

**근거:** 팀이 하나면 모든 Sprint가 같은 팀의 것이라 컬럼이 언제나 같은 값을 갖는다. 설계 §11.1의
Team은 Sprint와 추정의 팀 기준이고 Step 7의 팀별 속도 추세에 쓰이지만, 그 추세도 팀이 하나면
프로젝트 전체 추세와 같다.

**둘 이상 필요해지면:** `teams`를 만들고 `sprints.team_id`를 추가한 뒤 기존 행을 기본 팀 하나로
백필하면 된다. 그때 **§3.2의 "활성 Sprint는 하나" 규칙을 팀 단위로 바꿔야 한다** — 지금 규칙의 근거가
단일 팀 전제이기 때문이다. 마이그레이션 경로를 `V13` 주석에 적어 두었다.

### 3.2 실행 중인 Sprint는 하나 — 중복 계산을 막는 뼈대

세 규칙이 함께 "한 일이 두 번 세어지지 않는다"를 만든다.

1. **활성 Sprint는 프로젝트당 하나.** 단일 팀이므로 동시에 두 Sprint를 돌릴 이유가 없다.
2. **한 항목의 살아 있는 배정은 열린 Sprint 전체에서 하나.** 계획 중인 다음 Sprint에 미리 넣어
   두는 것도 막는다 — 지금 Sprint에서 아직 하는 일을 다음 Sprint의 계획에도 세면 계획이 두 배가 된다.
3. **완료 실적은 종료 시점의 `outcome`으로 확정.** 이월된 항목은 원 Sprint에 `CARRIED_OVER`로 남고
   다음 Sprint에서 `DONE`이 된다. 두 Sprint의 `donePoints`를 더해도 한 번만 세어진다(§4.2-13 실측).

### 3.3 배정 행은 이력이다 (설계 §11.3-4에서 벗어난 부분)

설계는 `(sprintId, backlogId)`의 유일성을 권하지만 **UNIQUE를 걸지 않았다.**

**근거:** 유일성을 걸면 같은 Sprint에서 뺐던 항목을 다시 넣을 때 기존 행을 되살려야 하고, "한 번
빠졌다"는 사실이 사라진다. 지시서 8항은 배정과 제거를 **모두** 보존하라고 한다. 그래서 행을 쌓고,
**살아 있는 배정(`removed_at IS NULL`)은 (sprint, item)당 하나**라는 제약을 애플리케이션에서 지킨다.

부분 유니크 인덱스로 DB에 맡길 수도 있지만 **PostgreSQL에는 있고 H2에는 없어** 양쪽에 같은 DDL을
쓸 수 없다. 이 저장소는 두 DB를 모두 지원하므로 애플리케이션 쪽을 골랐고, `V14` 주석에 적었다.

### 3.4 최소 완료 절차 (지시서 7항)

[`CompletionCheck`](../../../backend/src/main/java/com/projectflow/domain/CompletionCheck.java)가 두
가지만 본다.

1. **차단된 항목은 완료할 수 없다.** 차단은 보드와 모순되는 상태다.
2. **확인 표시가 없으면 완료할 수 없다.** 시스템에 Definition of Done이 저장되어 있지 않으므로,
   "확인했다"는 사실 자체를 호출자가 밝혀야 한다. 화면은 수용 조건을 보여주며 한 번 묻는다.

**하위 Task는 완료를 막지 않는다.** 지시서는 *자동* 완료를 금지할 뿐이고, 불필요해진 Task가 남는 것은
정상이다. 대신 **완료되지 않은 하위 수를 카드와 확인 대화상자에 싣는다** — 누르는 사람이 알아야 한다.

**Backlog 폼에도 같은 게이트를 적용했다.** 폼으로 우회할 수 있으면 절차가 장식이 된다. 단 **완료로
바뀌는 전이만** 확인을 요구한다 — 이미 완료인 항목의 제목만 고쳐도 체크를 다시 누르게 하면 안 된다.

### 3.5 종료와 재오픈은 서로 다른 사실을 남긴다

- **종료는 항목의 상태를 바꾸지 않는다.** 종료는 "이 Sprint가 무엇을 달성했나"에 대한 Sprint의 진술이고,
  일이 아직 Review에 있는지는 항목 자신의 사정이다. 이 분리가 이월을 가능하게 한다(실측 §4.2-11).
- **재오픈은 `done_at`만 비운다.** 종료된 Sprint의 `outcome`은 건드리지 않고, 대신 응답의
  `reopened`로 "그 Sprint는 완료로 기록했지만 지금은 아니다"를 표시한다.
- **종료된 Sprint는 수정·삭제·보드 이동이 모두 거부된다.** 결과가 이력이기 때문이다.
- **삭제는 계획 상태의 빈 Sprint만.** 실행 중인 것은 먼저 종료해야 하고(종료가 기록을 남기는 행위다),
  배정이 남아 있으면 거부한다.

### 3.6 캐시 리비전이 셋이 된 이유

Board 이동은 Backlog 상태 변경이므로 Sprint를 고치면 Backlog도 낡는다. 반대로 Backlog를 고치면
Sprint의 카드 제목·추정도 낡는다. 그런데 **간트는 둘 다 모른다.**

| 키 | 들어가는 리비전 | 왜 |
|---|---|---|
| `cacheKeyFor` (간트) | WBS | Backlog·Sprint를 모른다 |
| `wbsCacheKeyFor` (WBS) | WBS + Backlog | 연결 Backlog 수를 싣는다 |
| `backlogCacheKeyFor` | WBS + Backlog + Sprint | Work Package 코드와 Sprint 배정 표시 |
| `sprintCacheKeyFor` | WBS + Backlog + Sprint | 카드가 Backlog 항목을 보여준다 |

리비전 하나로 묶으면 Board 카드를 한 번 옮길 때마다 간트까지 다시 읽는다.

### 3.7 화면에서의 선택

- **Board와 Sprint 목록을 한 화면에 두었다.** 설계의 `Agile ├─ Sprint / Board`를 두 라우트로 쪼개면
  같은 데이터를 두 번 읽고, 실행 중에는 늘 같은 Sprint의 보드를 보게 된다. Sprint를 고르면 그 보드가
  바로 아래에 열린다.
- **드래그로 칸을 옮기고, 완료로 가는 이동만 확인을 묻는다.** 나머지 전이는 되돌리기 쉬우므로 막지 않는다.
- **종료는 한 번 묻는다.** 되돌릴 수 없고, 그 자리에서 이월 대상 Sprint를 고를 수 있다(없으면 나중에).
- **배정 목록은 배정 가능한 것만 보여준다** (`readyForSprint` && 다른 열린 Sprint에 없음). 서버가
  최종 방어선이고, 화면은 실패가 확정된 선택을 내놓지 않는다.
- **종료된 Sprint는 제거된 배정까지 보여준다** — 왜 빠졌는지가 이력이다. 열린 Sprint는 지금 든 것만.
- **Backlog 화면에 Sprint 이름·차단 배지를 달고, 삭제·보관 버튼을 비활성**했다. 왜 거부되는지 미리
  설명하는 편이 눌러 보고 오류를 받는 것보다 낫다.

### 3.8 내보내기·가져오기

- `FORMAT_VERSION` **3 → 4**. Sprint·배정·실행 상태(차단·완료 시각)가 함께 넘어간다.
- **종료 상태와 `outcome`을 그대로 복원한다.** 종료된 Sprint가 계획으로 되살아나면 프로젝트가 실제로
  한 일이 지워지고, 결과를 다시 유도할 수도 없다 — 항목의 현재 상태는 몇 달 전 종료 시점을 말해 주지
  않는다.
- 구조는 검증한다: 끊어진 Sprint·Backlog 참조, 거꾸로 된 기간, 이름 중복, **활성 Sprint 2개 이상**,
  한 Sprint에 같은 항목 중복 배정. 종료된 Sprint의 항목이 지금 재오픈되어 있는 것은 **그대로 들여온다** —
  Step 4가 구분하려는 바로 그 상태다.

## 4. 검증

### 4.1 실행한 명령

| 명령 | 결과 |
|---|---|
| `cd backend && ./gradlew clean build` | **BUILD SUCCESSFUL**, 테스트 **156건 / 실패·오류 0** (Step 3 시점 131건 → +25) |
| `cd frontend && npm run build` | `vue-tsc -b` 타입체크 통과 + vite 빌드 성공 |
| `cd frontend && npm test` | **9개 파일 89건 전부 통과** (Step 3 시점 85건 → +4) |

추가 테스트: `SprintServiceTest`(21건 — 수명주기·배정·Board·종료/이월/재오픈), `sprint.spec.ts`(4건).

### 4.2 API 실측 (Step 3 검증에 쓴 DB를 이어서 사용)

V11 상태의 DB에 앱을 띄워 **Flyway가 V12·V13·V14를 증분 적용**하는 것부터 확인했다
(`Successfully applied 3 migrations ... now at version v14`, `ddl-auto: validate` 통과).

| # | 확인 항목 | 결과 |
|---|---|---|
| 1 | Sprint 생성 | 201 |
| 2 | 같은 이름 | **400** "같은 이름의 Sprint가 이미 있습니다: Sprint 1" |
| 3 | 기간 거꾸로 | **400** "종료일이 시작일보다 앞설 수 없습니다." |
| 4 | 같은 Sprint에 중복 배정 | **400** "이미 이 Sprint에 배정된 항목입니다." |
| 5 | 다른 열린 Sprint에 이미 배정 | **400** "'Sprint 1'에 이미 배정되어 있습니다…" |
| 6 | **두 번째 Sprint 시작** | **400** "이미 실행 중인 Sprint가 있습니다: 'Sprint 1'. 한 번에 하나만 실행합니다 (단일 팀)." |
| 7 | 실행 중 Sprint 삭제 | **400** "먼저 종료하세요." |
| 8 | 배정된 계획 Sprint 삭제 | **400** "배정된 항목이 … 있어 삭제할 수 없습니다." |
| 9 | 없는 Sprint | **404** |
| 10 | **완료 절차** | 확인 없이 완료 → **400** "수용 조건을 확인했다는 표시가 없습니다: tests pass" / 차단 중 완료 → **400** "차단된 항목은 완료할 수 없습니다" / 차단 해제 + 완료를 한 번에 → 200 |
| 11 | **종료** | A(DONE)·B(REVIEW) 배정 상태에서 종료 → A `outcome=DONE`, B `CARRIED_OVER`, **B의 상태는 REVIEW 그대로** |
| 12 | **이월** | `carryOverToSprintId`로 종료하니 Sprint 2에 B의 새 배정 생성 |
| 13 | **완료 실적 중복 방지** | B를 Sprint 2에서 완료·종료 → Sprint 1 `done=1/5SP` `carried=1`, Sprint 2 `done=1/5SP`. 각 Sprint가 자기 것만 한 번 |
| 14 | **재오픈** | 폼으로 A를 진행 중으로 → Sprint 1은 `done=1/5SP` 그대로, `reopened=true`, `doneAt` 비워짐 |
| 15 | 폼에서의 완료 절차 | 확인 없이 → **400**, 확인과 함께 → 200 |
| 16 | 진행 중 Sprint 항목의 보관·삭제 | 둘 다 **400** "진행 중인 Sprint에 배정된 항목이라 …. Sprint에서 먼저 제거하세요." → 배정 해제 후 보관 200 |
| 17 | Backlog의 `openSprintName` | 배정 중에는 Sprint 이름이, 해제 후에는 `null` |
| 18 | 내보내기 | `formatVersion: 4`, Sprint 3개(CLOSED/CLOSED/PLANNED), 배정 4건의 `outcome`·`pointsAtClose`, 항목의 `blocked`·`doneAt` |
| 19 | 가져오기 왕복 | 종료 상태·결과·이월 이력 모두 보존 (Sprint 1 `done=1/5SP carried=1`, Sprint 2 `done=1/5SP`) |
| 20 | 여러 Work Package 혼합 배정 | 단위 테스트로 확인 (`spansWorkPackages`) |
| 21 | Task 자동 완료 금지 | 단위 테스트로 확인 (`tasksDoNotCompleteTheStory`) |

### 4.3 검증 중 발견해 고친 결함

**Sprint 예외가 500으로 나갔다.** `InvalidSprintException`과 `SprintNotFoundException`을
`GlobalExceptionHandler`에 등록하지 않아, 위 §4.2의 거부가 전부 **500 + 메시지 없음**이었다. 단위
테스트는 예외 타입만 보므로 잡히지 않았고, API 실측에서 드러났다. 등록 후 400/404 + 메시지로 바뀌었다.

**얻은 교훈:** 새 도메인 예외를 만들 때 핸들러 등록이 함께 가야 한다. Step 3에서도 같은 등록을 했는데
이번에 빠뜨렸다 — 서비스 단위 테스트로는 확인되지 않는 항목이다.

### 4.4 실행하지 않은 검증과 이유

| 미실행 항목 | 이유 |
|---|---|
| 브라우저에서 화면 조작 | 프론트엔드는 타입체크·빌드·단위 테스트까지만. 드래그 이동, 완료·차단 대화상자, 종료 대화상자의 실제 조작은 미확인 |
| PostgreSQL(`server` 프로필) | 이 장비에 PostgreSQL·Docker가 없다. 양쪽에서 동작하는 구문만 썼고, 특히 부분 유니크 인덱스를 피한 이유가 그것이다(§3.3) |
| 동시성 (두 사용자가 같은 Sprint를 동시에 시작) | "활성 Sprint 하나"는 애플리케이션 검사이고 DB 제약이 아니다. 데스크톱 단일 사용자 전제에서는 문제가 아니지만 §6에 남겼다 |
| 대량 데이터 성능 | 프로젝트 단위로 메모리에 올리는 기존 구조를 그대로 따랐다 |

## 5. 상태 전이 · 완료 기준 · 이월/재오픈 정책 (요약)

**Sprint**

```text
PLANNED ──start──▶ ACTIVE ──close──▶ CLOSED
   │                  │                 (되돌아가지 않음)
   ├ 항목 배정·제거      ├ 항목 배정·제거
   ├ 수정·삭제(빈 것만)   ├ Board 이동
   └ start: 다른 ACTIVE가 없어야 함, 배정의 points_at_start 재확정
                      └ close: 배정마다 outcome·points_at_close, 선택적 이월
```

**Backlog 항목 (Board)**

```text
TODO ⇄ IN_PROGRESS ⇄ REVIEW ⇄ DONE
                        │
   blocked / blocked_reason 는 어느 칸에서든 켤 수 있다 (직교)
   DONE 진입 조건: 차단 아님 + 확인 표시
   DONE 이탈: 언제든 가능, done_at 비움 (Sprint outcome은 불변)
```

**이월 정책:** 종료 시 미완료 = `CARRIED_OVER`. 항목 상태는 그대로. 이월 대상 Sprint를 주면 그
Sprint에 **새 배정 행**이 생긴다(원 Sprint의 행은 결과가 찍힌 채 남는다).

**재오픈 정책:** 상태를 DONE에서 내리면 `done_at`만 비운다. 종료된 Sprint의 `outcome`은 불변이고,
그 배정은 `reopened=true`로 표시된다.

## 6. 남은 문제

1. **"활성 Sprint 하나"는 DB 제약이 아니다.** 애플리케이션 검사이므로, 두 요청이 동시에 서로 다른
   Sprint를 시작하면 이론적으로 둘 다 통과할 수 있다. 데스크톱 단일 사용자에서는 일어나지 않지만,
   `server` 프로필로 여러 사용자가 붙으면 부분 유니크 인덱스(PostgreSQL) 또는 잠금이 필요하다.
2. **팀이 하나라는 전제가 코드에 박혀 있다.** §3.1의 마이그레이션 경로를 적어 두었지만, 팀을 도입할
   때 "활성 Sprint 하나" 규칙을 팀 단위로 바꾸는 것을 잊으면 안 된다.
3. **속도(velocity) 추세 화면이 없다.** `donePoints`는 Sprint마다 계산되어 응답에 있지만, 종료 Sprint를
   나열해 추세를 보여주는 것은 Step 7(Dashboard)의 몫이다.
4. **회고를 기록할 곳이 없다.** 지시서 범위 밖이고, 설계도 회고 결과를 RAID의 Action으로 연결하라고
   하므로 Step 6에서 다룰 문제다.
5. **Backlog 순서를 손으로 바꿀 수 없다** (Step 3에서 남긴 것과 동일).
6. **화면 조작 미검증** (§4.4).

## 7. Step 5 연결 지점 (집계 입력)

Step 5는 진척을 계산한다. 그 입력이 지금 모두 갖춰졌다.

| 필요한 것 | 어디에서 |
|---|---|
| 집계 대상 구분 | `BacklogItemType.aggregated()` — Story·Bug만 |
| 완료 여부 | `backlog_items.status = DONE` (+ `done_at`으로 "지금 완료인지" 확인 가능) |
| Backlog 내부 가중치 | `backlog_items.progress_weight` (Step 3에서 저장, 아직 아무 계산에도 쓰이지 않음) |
| 귀속 Work Package | `backlog_items.wbs_item_id` (상속 규칙으로 항상 채워짐) |
| 실행 방식 | `wbs_items.execution_mode` — Agile/Waterfall/Hybrid 분기 |
| Sprint별 완료 실적 | `sprint_items.outcome = DONE` + `points_at_close`, 또는 응답의 `donePoints` |
| 이월·재오픈 구분 | `outcome`(과거) vs `status`/`done_at`(현재). **집계는 현재 상태를 쓰고, 보고 스냅샷은 과거를 쓴다** |

**주의할 점 두 가지.**

- **Step 3 §6-1의 문제가 여전히 열려 있다.** Work Package → Summary 전환이 허용되므로 Backlog가
  Summary에 붙은 상태가 만들어질 수 있다(`linkedToSummary`). 집계가 시작되면 이중 계산이 되므로,
  **Step 5-A에서 이 상태를 어떻게 다룰지 먼저 정해야 한다.**
- **Story Point를 가중치로 변환하지 않는다**(설계 §6.1). `points_at_close`는 팀 속도용이고,
  진척 분모는 `progress_weight`다. 두 값을 같은 식에 넣으면 안 된다.
