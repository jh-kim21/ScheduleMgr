# Step 03. Backlog와 Work Package 연결 (결과 보고서)

| 구분 | 내용 |
|---|---|
| 대상 저장소 | `D:\Git\ScheduleMgr` (branch `main`) |
| 지시서 | [Hybrid_PM_Step03_Backlog_Link.md](../Hybrid_PM_Step03_Backlog_Link.md) |
| 선행 보고서 | [Step 01 분석](Hybrid_PM_Step01_Analysis.md) · [Step 02 결과](Hybrid_PM_Step02_Result.md) |
| 참고 설계 | [WBS_Agile_Hybrid_PM_Design.md](../WBS_Agile_Hybrid_PM_Design.md) |
| 작성일 | 2026-09-07 |
| 상태 | **완료.** Step 4는 자동 실행하지 않았다. |

## 1. 요약

Product Backlog(Epic·Story·Bug·Task)를 추가하고 **하나의 Work Package에 귀속**시켰다. Agile / Backlog
화면과 WBS ↔ Backlog 양방향 이동도 붙였다.

- **귀속은 아래로 상속된다.** 상위가 있는 항목은 항상 상위의 Work Package를 갖고, 상위를 옮기면
  하위 전체가 따라간다. "상하위 귀속 불일치"는 검증 대상이 아니라 **구조적으로 불가능**하다.
- **Summary 귀속·Task 고아·잘못된 계층·다른 프로젝트 참조는 거부**(400/404)하고,
  **미연결 초안과 Waterfall Work Package는 표시**한다 — 기존 저장소의 "만족 불가능한 구조는 거부,
  안 맞는 계획은 표시" 기준을 그대로 따랐다.
- **연결 항목이 있는 WBS는 삭제되지 않는다.** 하위 전체 범위를 보고 거부하며, 보관한 항목만 붙어
  있으면 사유(`WBS_ITEM_DELETED`)를 남기고 분리한다. Step 1 보고서 §9 위험 3이 지적한 CASCADE 유실을
  막았다.
- **Epic·Task는 집계 대상에서 구분**했고(`aggregated`), Sprint 투입 가능 여부(`readyForSprint`)를
  Step 4가 그대로 쓸 수 있게 응답에 실었다.
- 검증: 백엔드 **131건**(+32), 프론트엔드 **85건**(+14) 전부 통과. API 왕복·거부 경로·삭제 정책·
  Flyway 증분 적용까지 실측했다.

## 2. 변경 내용

### 2.1 스키마 (신규 마이그레이션 2개)

| 파일 | 내용 |
|---|---|
| `V10__create_backlog_items_table.sql` | `backlog_items` |
| `V11__create_backlog_link_changes_table.sql` | `backlog_link_changes` (귀속 변경 이력) |

**`backlog_items` 필드**

| 필드 | 의미 |
|---|---|
| `project_id` | 프로젝트 (CASCADE) |
| `wbs_item_id` | 귀속 Work Package. **NULL = 미연결(초안)**. `ON DELETE SET NULL` |
| `parent_id` | Epic → Story·Bug, Story·Bug → Task. self FK, `ON DELETE CASCADE` |
| `item_type` | `EPIC` / `STORY` / `BUG` / `TASK` |
| `title`, `description` | |
| `priority` | `HIGH` / `MEDIUM` / `LOW` |
| `status` | `TODO` / `IN_PROGRESS` / `REVIEW` / `DONE` |
| `assignee_member_id` | 담당자 = 프로젝트 구성원. `ON DELETE SET NULL` |
| `acceptance_criteria` | 수용 조건 |
| `story_point` | 팀의 추정 (`>= 0`) |
| `progress_weight` | 같은 Work Package 안에서의 진척 비중 (`>= 0`) |
| `archived_at` | 보관 시각. **상태와 직교** |
| `sort_order` | 등록 순서 |

**한 테이블에 네 유형을 둔 이유**는 RAID와 같다 — 제목·상태·담당자·우선순위 등 대부분을 공유하고 한
화면에서 함께 읽히므로, 유형별 테이블은 CRUD를 네 벌로 늘리면서 얻는 것이 없다. 계층은 `parent_id`
하나로 표현하고, **Product Backlog는 정렬된 목록이지 계층 노드가 아니므로**(설계 §4.1) WBS처럼 코드를
파생하지 않는다.

### 2.2 백엔드

**신규**

| 파일 | 역할 |
|---|---|
| `domain/BacklogItemType.java` | 네 유형 + `aggregated()`(Story·Bug) + `topLevel()` |
| `domain/BacklogPriority.java`, `BacklogStatus.java` | 우선순위·상태 |
| `domain/BacklogItem.java` | 엔티티. 가져오기용 `archivedAt` 생성자 포함 |
| `domain/BacklogAssessor.java` | 연결 상태 판정(미연결·Summary 연결·연결 끊김·실행 방식 확인·Sprint 투입 가능) |
| `domain/BacklogLinkChange.java`, `BacklogLinkChangeReason.java` | 귀속 변경 이력 |
| `domain/Backlog{Item,LinkChange}Repository.java` | 도메인 포트 |
| `domain/{BacklogItemNotFound,InvalidBacklogItem}Exception.java` | 예외 |
| `infrastructure/persistence/Backlog*{JpaRepository,RepositoryAdapter}.java` | 기존 패턴 |
| `application/BacklogService.java` | 유스케이스 + 구조 검증 + 상속·이력 |
| `application/dto/BacklogItemRequest.java`, `BacklogResponse.java`, `BacklogArchiveRequest.java`, `BacklogSummary.java` | |
| `presentation/BacklogController.java` | |

**수정**

| 파일 | 변경 |
|---|---|
| `application/WbsService.java` | **삭제 가드**(§3.4) + 트리에 연결 Backlog 수 |
| `application/dto/WbsNodeResponse.java` | `backlogSummary`(하위 합산) |
| `application/ExportService.java` | Backlog 절 + `FORMAT_VERSION` 2 → **3** |
| `application/ImportService.java` | Backlog 검증·삽입, `SUPPORTED_FORMAT_VERSION` **3** |
| `presentation/GlobalExceptionHandler.java` | 새 예외 두 개 등록 |

### 2.3 프론트엔드

| 파일 | 변경 |
|---|---|
| `shared/backlog.ts` (+ `.spec.ts`) | 타입·라벨·`backlogSummaryText`·`aggregatedType` |
| `api/backlogApi.ts` | 클라이언트 |
| `features/backlog/backlogFilter.ts` (+ `.spec.ts`) | 필터 (순수 함수) |
| `features/backlog/useBacklog.ts` | 컴포저블 (모듈 스코프 공유) |
| `features/backlog/BacklogList.vue`, `BacklogForm.vue` | 목록·입력 |
| `views/BacklogView.vue` | 화면 |
| `router/index.ts`, `App.vue` | `/backlog` 라우트와 메뉴 |
| `features/wbs/WbsTree.vue` | `연결 Backlog` 열(클릭 이동) + `focusId` 강조 |
| `views/WbsView.vue` | `?focus=` 처리 |
| `api/wbsApi.ts` | `backlogSummary` |
| `stores/scheduleCache.ts` | Backlog 리비전 분리 (§3.6) |
| `shared/exportRows.ts`, `features/export/ExportMenu.vue` | Backlog CSV |

## 3. 결정과 근거

### 3.1 귀속은 상속한다 (검증하지 않고 불가능하게 만든다)

지시서 4·5항은 "Epic의 하위 항목도 같은 귀속 유지", "Task는 부모의 귀속을 따른다"를 요구한다.
이를 *검증*으로 구현하면 두 값이 어긋난 상태가 저장될 수 있고, 화면마다 어긋남을 표시해야 한다.

**결정:** 상위가 있는 항목의 `wbs_item_id`는 **상위에서 가져온다.** 요청이 다른 값을 지정하면
400으로 거부한다(조용히 덮어쓰지 않는다 — 클라이언트가 "Task를 다른 Work Package로 옮겼다"고
착각하면 안 된다). 상위를 재귀속하면 하위 전체를 함께 옮기고 **각 항목마다 이력**을 남긴다.

결과적으로 완료 기준의 "상하위 귀속 불일치 차단"은 저장 자체가 불가능해서 충족된다.

### 3.2 무엇을 거부하고 무엇을 표시하는가

| 상황 | 처리 | 근거 |
|---|---|---|
| Summary에 귀속 | **400** | Summary는 관리 단위가 아니라 하위 합계다. 만족 가능한 상태가 아니다 |
| 상위 없는 Task | **400** | Task는 Story·Bug의 실행 상세다 (설계 §4.1) |
| 허용되지 않는 계층(Epic 아래 Task, Epic에 상위) | **400** | 모델이 가질 수 없는 모양 |
| 다른 프로젝트의 WBS·구성원 | **404** | 기존 `RaidService` 패턴 재사용 |
| 하위가 있는 항목 삭제 | **400** | DB CASCADE가 하위를 함께 지운다. 조용한 유실 금지 |
| **미연결 초안** | 허용 + 표시 | 지시서 6항. 초안 등록은 정상적인 순서다 |
| **Waterfall·미지정 Work Package 연결** | 허용 + **안내** | 지시서 8항이 "실행 방식 변경이 필요함을 안내"라고 지시 |
| **귀속 대상이 Summary로 전환됨** | 허용 + 표시 | Step 2가 그 전환을 허용하므로 실제로 생긴다 (§6-2) |

### 3.3 Epic·Task를 집계에서 구분하는 방법

`BacklogItemType.aggregated()`가 Story·Bug만 참이고, 응답의 `aggregated`로 내려간다. Epic을 그 안의
Story와 함께, Task를 그 Story와 함께 세면 같은 일을 두 번 세게 된다(설계 §6.1). **Step 5의 집계는
이 한 곳만 물어보면 된다.**

`readyForSprint`는 Step 4가 쓸 판정이다. 세 가지가 막는다: 집계 단위가 아님(Epic·Task), 보관됨,
연결이 쓸 수 없음(미연결·Summary 연결·연결 끊김). **Waterfall·미지정은 막지 않는다** — 안내일 뿐이고,
Step 4 지시서도 미연결과 Epic만 차단 대상으로 든다.

### 3.4 삭제 정책 (Step 1 보고서 §9 위험 3의 해소)

`wbs_items.parent_id`가 `ON DELETE CASCADE`라 상위 WBS를 지우면 하위 전체가 사라지고, 그에 붙은
Backlog의 귀속도 함께 끊긴다. 설계 §11.3-6은 "연결 항목이 있는 WBS 삭제 시 재귀속 또는 명시적 보관
처리를 요구한다"고 한다.

**두 단계로 구현했다.**

1. `WbsService.deleteItem`이 **삭제 범위(자기 + 모든 하위)** 에 귀속된 **보관되지 않은** Backlog를
   찾아 있으면 400으로 거부한다. 메시지에 건수와 제목 예시를 담는다.
2. **보관된 항목은 막지 않는다.** 보관이 곧 규칙이 요구하는 "명시적 처리"다. 대신 삭제 직전에
   서비스가 직접 분리하며 `WBS_ITEM_DELETED` 사유로 이력을 남긴다 — FK의 `SET NULL`에만 맡기면
   어느 날 링크가 그냥 없어진 것처럼 보인다.

DB 쪽은 `SET NULL`로 두었다(CASCADE 아님). 서비스가 먼저 막으므로 거기까지 오지 않지만,
**마지막 방어선이 데이터를 지우는 쪽이어서는 안 된다.**

Backlog 자체의 삭제도 같은 태도다: 하위가 있으면 거부하고, DB CASCADE는 안전장치로만 둔다.

### 3.5 이력의 범위

`backlog_link_changes`에 `(previous, new, reason, changed_at)`을 남긴다. 사유는 자유 입력이 아니라
코드다 — 로그인이 없어 변경자를 알 수 없고 사유를 받을 화면도 없지만, **"사용자가 옮긴 것"과
"WBS가 삭제되어 끊긴 것"은 나중에 완전히 다른 의미**여서 구분이 필요하다.

| 사유 | 언제 |
|---|---|
| `REASSIGNED` | 사용자가 귀속을 지정·변경 |
| `INHERITED_FROM_PARENT` | 상위가 옮겨져 하위가 따라감 |
| `WBS_ITEM_DELETED` | 귀속된 WBS가 삭제되어 분리 (보관된 항목만) |

Step 2의 `wbs_execution_mode_changes`와 같은 성격의 **두 번째 좁은 표**다. 설계 §11.1의 일반
`ChangeLog`는 Step 5에서 가중치·Baseline 이력과 함께 도입하며 **두 표를 그때 흡수**한다. 지금 일반
표를 만들면 Step 5의 기능을 선행 구현하는 셈이다.

### 3.6 캐시 리비전을 나눈 이유

WBS 트리가 Work Package별 연결 Backlog 수를 싣게 되었으므로, Backlog가 바뀌면 WBS 화면도 다시 읽어야
한다. 그런데 기존 `wbsRevision` 하나를 올리면 **Backlog를 고칠 때마다 간트도 재요청**한다 — 간트는
Backlog를 모르므로 바뀔 수 없는 응답이다.

그래서 `backlogRev`를 따로 두고, `wbsCacheKeyFor`(WBS 화면)에는 둘 다, `cacheKeyFor`(간트)에는 WBS
리비전만 넣었다. Backlog 자신의 키(`backlogCacheKeyFor`)에는 **WBS 리비전이 들어간다** — 각 행이
Work Package의 코드와 실행 방식을 보여주므로 WBS 개명·이동·실행 방식 변경에 낡는다. 날짜는 넣지
않는다(오늘을 기준으로 판정하는 값이 없다). RACI 키와 같은 모양, 같은 이유다.

### 3.7 화면에서의 선택

- **메뉴는 `Backlog` 하나로 넣었다.** 설계의 `Agile ├─ Backlog` 묶음은 Sprint·Board가 생기는 Step 4에
  형제가 갖춰질 때 만드는 것이 맞다고 판단했다. 항목 하나짜리 묶음 메뉴는 지금 소음이다.
- **한 가족이 붙어서 보인다.** 서버가 깊이 우선으로 정렬하고 `depth`를 함께 보내 들여쓰기한다.
  필터가 하위만 걸러내면 **상위를 맥락으로 남기고 흐리게 표시**한다 — Task만 떠 있으면 어느 Story의
  것인지 알 수 없다.
- **입력 패널은 접혀 있다.** RAID 화면과 같은 이유(읽는 것이 주된 행위). 수정 저장 후에는 닫고,
  추가 후에는 열어 둔다.
- **배너는 필터를 무시하고 전체를 센다** — "미연결 3건"은 프로젝트에 대한 사실이다 (RAID 배너 규칙).
- **보관은 기본으로 감춘다.** 접어둔 것이 목록에 섞이면 열린 일이 실제보다 많아 보인다. 단
  WBS에서 "보관 N"을 눌러 오면 `보관 포함`으로 열어 빈 목록이 나오지 않게 한다.
- **WBS 행의 숫자는 Work Package 행만 링크**다. 상위 행의 숫자는 하위 합계라 이동 대상이 하나가
  아니므로 글자만 보여준다.
- **`하위가 있는 항목`의 삭제 버튼은 비활성**이고 title로 이유를 알린다 (Step 2의 `하위` 버튼과 같은 방식).

### 3.8 내보내기·가져오기

- `FORMAT_VERSION` **2 → 3**. 넣지 않으면 프로젝트를 넘겼을 때 **실행 항목이 조용히 사라진다.**
- 구형 파일(2 이하)에는 Backlog 절이 없고, 없는 것은 빈 것과 같게 처리한다.
- **판정값은 넣지 않는다**(미연결·실행 방식 확인 등) — 귀속 대상 기준으로 계산되는 값이라 파일에
  박히면 거짓이 된다. `archivedAt`은 저장된 상태이므로 넘어간다.
- **하위의 귀속은 파일 값이 아니라 상위에서 물려받는다.** 이 모델에서 하위의 귀속 값은 정보를 담고
  있지 않다 — Step 2에서 `nodeType`을 다시 계산한 것과 같은 이유다.
- 구조는 검증한다: 상위 미존재, 순환, 상위 없는 Task, 허용되지 않는 계층, 끊어진 귀속·담당자 참조.
  미연결과 Summary 연결은 그대로 들어온다.

## 4. 검증

### 4.1 실행한 명령

| 명령 | 결과 |
|---|---|
| `cd backend && ./gradlew clean build` | **BUILD SUCCESSFUL**, 테스트 **131건 / 실패·오류 0** (Step 2 시점 99건 → +32) |
| `cd frontend && npm run build` | `vue-tsc -b` 타입체크 통과 + vite 빌드 성공 |
| `cd frontend && npm test` | **8개 파일 85건 전부 통과** (Step 2 시점 71건 → +14) |

추가 테스트: `BacklogServiceTest`(19건), `WbsServiceTest`의 `DeleteWithBacklog`(4건),
`ImportServiceTest`의 `Backlog`(7건), `backlogFilter.spec.ts`(8건), `backlog.spec.ts`(6건).

### 4.2 API 실측 (Step 2 검증에 쓴 DB를 이어서 사용)

V9 상태의 DB에 앱을 띄워 **Flyway가 V10·V11을 증분 적용**하는 것부터 확인했다
(`Successfully applied 2 migrations ... now at version v11`, `ddl-auto: validate` 통과).

| # | 확인 항목 | 결과 |
|---|---|---|
| 1 | Epic → Story → Task 생성 | 201. `depth` 0/1/2, 한 가족이 붙어서 정렬 |
| 2 | **귀속 상속** | Story·Task를 `wbsItemId: null`로 만들었는데 둘 다 Epic의 Work Package(1.1)를 받음 |
| 3 | **집계 단위 구분** | Epic·Task `aggregated=false`, Story·Bug `true` |
| 4 | **Sprint 투입 가능 판정** | Story·Bug만 `readyForSprint=true`. 미연결 초안은 `false` |
| 5 | 미연결 초안 | 생성 허용, `unlinked=true`, `unlinkedCount=1` |
| 6 | Waterfall·미지정 안내 | 미지정 Work Package의 Bug에 `requiresExecutionModeChange=true`, 그래도 `readyForSprint=true` |
| 7 | Summary 귀속 | **400** "'설계 단계'은(는) Summary라 Backlog를 귀속시킬 수 없습니다…" |
| 8 | 다른 프로젝트의 WBS | **404** |
| 9 | 상위 없는 Task | **400** "Task는 상위 항목(Story 또는 Bug)이 있어야 합니다…" |
| 10 | Epic 아래 Task | **400** "TASK는 EPIC의 하위가 될 수 없습니다." |
| 11 | 하위인데 다른 귀속 주장 | **400** "하위 항목의 귀속은 상위 항목을 따릅니다…" |
| 12 | 다른 프로젝트 구성원을 담당자로 | **404** |
| 13 | 잘못된 유형 값 | **400** "'FEATURE'은(는) itemType에 허용되지 않는 값입니다. 가능한 값: EPIC, STORY, BUG, TASK" (Step 2에서 개선한 enum 메시지) |
| 14 | 하위가 있는 항목 삭제 | **400** "…하위 항목이 1건 있어 삭제할 수 없습니다…" |
| 15 | **프로젝트 격리** | 프로젝트 2의 경로로 프로젝트 1의 항목 수정 → **404** |
| 16 | **WBS 삭제 가드** | 연결된 WP 삭제 → 400(3건 명시). 그 **상위 Summary** 삭제 → 400(하위까지 4건, "등" 표기) |
| 17 | **재귀속 연쇄** | Epic을 1.2로 옮기니 Story·Task가 함께 이동 |
| 18 | 보관 후 삭제 | 붙은 항목을 모두 보관하니 WP 삭제 200. **항목은 남고 귀속만 비워짐** |
| 19 | **이력** | 생성 `REASSIGNED` → 하위 `INHERITED_FROM_PARENT` → 이동 시 3건 → 삭제 시 4건 `WBS_ITEM_DELETED` |
| 20 | WBS 화면의 연결 수 | Work Package 행과 상위 Summary 행 모두 표시(상위는 하위 합산) |
| 21 | 빈 Work Package 삭제 | 연결이 없으면 예전처럼 200 |

### 4.3 검증 중 발견해 고친 결함

**`BacklogSummary.isEmpty()`가 API에 노출됐다.** record에 bean 모양의 `isX()`를 두면 Jackson이
프로퍼티로 읽어, 모든 WBS 노드의 `backlogSummary`에 `"empty": false`가 함께 나갔다.
`hasNone()`으로 이름을 바꿔 없앴고, 이유를 주석으로 남겼다. 다른 직렬화 record에 같은 모양이 없는지도
확인했다.

부수적으로 `BacklogAssessment.hasWarning()`이 쓰이지 않아 제거했다(화면이 개별 플래그로 판단한다).

### 4.4 알게 된 것: 순환 검사는 도달 불가능하다

완료 기준의 "부모 순환 차단"을 위해 `BacklogService`와 `ImportService` 양쪽에 순환 검사를 두었는데,
**유형 규칙만으로 이미 순환이 불가능하다**는 것을 테스트에서 확인했다: Story·Bug의 유일한 상위는
Epic이고 Epic은 상위를 가질 수 없으므로, 순환이 되려면 반드시 Epic이 상위를 갖거나 같은 유형끼리
엮여야 하고 둘 다 먼저 거부된다.

두 검사를 **남겨 두었다** — 요구된 규칙이고, Epic 중첩이 허용되는 날 유일한 방어선이 된다. 대신
도달 불가능하다는 사실을 주석으로 적고, 테스트는 실제로 일어나는 거부(계층 위반)를 검증하도록
이름과 단정을 고쳤다. **도달할 수 없는 코드를 통과하는 것처럼 보이는 테스트를 남기지 않았다.**

### 4.5 실행하지 않은 검증과 이유

| 미실행 항목 | 이유 |
|---|---|
| 브라우저에서 화면 조작 | 프론트엔드는 타입체크·빌드·단위 테스트까지만. 필터·맥락 행 표시·`focus` 강조·CSV 다운로드의 실제 조작은 미확인 |
| PostgreSQL(`server` 프로필) | 이 장비에 PostgreSQL과 Docker가 없다. 양쪽에서 동작하는 구문만 썼으나 실측하지 못했다 |
| 내보내기·가져오기 왕복(Backlog 포함) | 단위 테스트(`ImportServiceTest`)로 검증했고 API 왕복은 하지 않았다. Step 2에서는 실측했으나 이번에는 시간을 §4.2의 삭제·상속 검증에 썼다 |
| 대량 데이터 성능 | 프로젝트 단위로 메모리에 올리는 기존 구조를 그대로 따랐다 (Step 1 보고서 §9 위험 6) |

## 5. 필드·관계 요약 (Step 4에서 쓸 것)

```text
Project
└─ WbsItem (node_type = WORK_PACKAGE, execution_mode)
   └─ BacklogItem            귀속: wbs_item_id (NULL = 미연결)
      ├─ EPIC                집계 제외, Sprint 배정 불가
      │  └─ STORY / BUG      집계 대상, Sprint 배정 가능
      │     └─ TASK          집계 제외, Sprint 배정 불가
      └─ STORY / BUG         (Epic 없이 직접 연결 가능)
```

| API | 설명 |
|---|---|
| `GET` `/api/projects/{projectId}/backlog` | 목록 (미연결 건수 + 항목별 판정) |
| `POST` `/api/projects/{projectId}/backlog` | 항목 추가 |
| `PUT` `/api/projects/{projectId}/backlog/{itemId}` | 항목 수정 (재귀속 포함, 하위 연쇄) |
| `PUT` `/api/projects/{projectId}/backlog/{itemId}/archive` | 보관·복구 (`{"archived": true|false}`) |
| `DELETE` `/api/projects/{projectId}/backlog/{itemId}` | 삭제 (하위 있으면 400) |

**Step 4가 재사용할 검증 로직**

| 무엇 | 어디 |
|---|---|
| Sprint 배정 가능 여부 (미연결·Epic·Task·보관·연결 오류 차단) | `BacklogAssessor.assess(...).readyForSprint`, 응답의 `readyForSprint` |
| 집계 대상 구분 | `BacklogItemType.aggregated()`, 응답의 `aggregated` |
| 교차 프로젝트 참조 검증 | `BacklogService.requireAssigneeOfProject`, `resolveLink`의 프로젝트 확인 |
| 삭제 시 연결 손실 방지 | `BacklogService.activeItemsLinkedTo` / `detachArchivedBefore` (Sprint 배정에도 같은 방식이 필요하다) |
| 이력 기록 | `BacklogLinkChange` + `BacklogLinkChangeReason` (Sprint 배정·이월 이력은 같은 패턴으로 얹을 수 있다) |

## 6. 남은 문제

1. **Work Package → Summary 전환을 막지 않는다.** Step 2가 허용하는 전환이라 Backlog가 붙은 채로
   Summary가 될 수 있다. 그때 `linkedToSummary`로 표시하고 `readyForSprint`를 내리지만, **막지는
   않는다.** "만족 불가능한 구조는 거부, 계획 문제는 표시" 기준을 따른 선택이지만,
   **Step 5의 집계 전에는 정리되어야 한다** — Summary가 하위를 합산하는데 그 아래 Story의 가중치가
   따로 세지면 이중 계산이 된다. 삭제와 같은 가드를 전환에도 두는 것이 다음 후보다.
2. **이력을 읽는 화면이 없다.** 두 번째 좁은 이력 표가 생겼고 조회 API·화면은 여전히 없다.
   Step 5에서 `change_logs`로 흡수할 때 함께 만드는 것이 맞다고 판단했다(요구사항 8.2와 같은 자리).
3. **진척은 아직 아무것도 자동 집계하지 않는다.** `progress_weight`를 받아 저장할 뿐이고, WBS 진행률은
   여전히 수동 입력값이다(Step 5 범위). WBS 화면의 `4개 완료`는 **건수**이지 진척이 아니다.
4. **Backlog 순서를 손으로 바꿀 수 없다.** 우선순위와 등록 순서로만 정렬한다. 드래그 정렬은 지시서
   범위에 없어 넣지 않았다.
5. **화면 조작 미검증** (§4.5).

## 7. Step 4 연결 지점

- Sprint 배정 대상은 `readyForSprint = true`인 항목이다. 지시서 4항의 "미연결 항목과 Epic의 배정
  차단"이 이 한 필드로 판정된다.
- Board 상태는 이미 `TODO / IN_PROGRESS / REVIEW / DONE`으로 저장되어 있어 **매핑이 필요 없다.**
  보관은 상태와 직교하게 두었으므로 Board의 상태 전이에 섞이지 않는다.
- **팀(Team) 모델은 여전히 없다.** Step 4에서 기본 팀 도입 여부를 정해야 한다
  (Step 1 보고서 §8.1의 사용자 결정 항목).
- **주의:** Sprint 배정이 붙으면 삭제 정책을 한 번 더 봐야 한다. 지금 Backlog 삭제는 "하위가 있으면
  거부"뿐이고, Sprint에 배정된 항목의 삭제·보관은 Step 4가 정할 문제다. §3.4의 두 단계 방식
  (활성은 거부, 보관은 사유를 남기고 분리)을 그대로 확장하면 된다.
