# Step 02. WBS Execution Mode 추가 (결과 보고서)

| 구분 | 내용 |
|---|---|
| 대상 저장소 | `D:\Git\ScheduleMgr` (branch `main`) |
| 지시서 | [Hybrid_PM_Step02_Execution_Mode.md](../Hybrid_PM_Step02_Execution_Mode.md) |
| 선행 보고서 | [Hybrid_PM_Step01_Analysis.md](Hybrid_PM_Step01_Analysis.md) |
| 참고 설계 | [WBS_Agile_Hybrid_PM_Design.md](../WBS_Agile_Hybrid_PM_Design.md) |
| 작성일 | 2026-09-07 |
| 상태 | **완료.** Step 3은 자동 실행하지 않았다. |

## 1. 요약

WBS 항목에 **관리 단위 구분(`node_type`)** 과 **실행 방식(`execution_mode`)** 을 신규로 추가했다. 둘 다
추가이며 기존 동작을 바꾸지 않는다 — 기존 레코드는 현재 트리 모양대로 백필되고 실행 방식은 `미지정`으로
남으므로, **이 단계에서 기존 프로젝트의 화면 숫자와 동작은 달라지지 않는다.**

- 기존 데이터가 있는 DB에 실제 Flyway로 V8·V9를 증분 적용해 백필과 조회·수정·재조회를 확인했다.
- Summary에 실행 방식을 지정하는 것, Work Package 아래에 하위를 만드는 것, 하위가 있는 항목을 Work
  Package로 되돌리는 것을 모두 400으로 거부한다.
- 구형(`formatVersion 1`) 내보내기 파일도 그대로 가져올 수 있고, 새 필드는 왕복에서 보존된다.
- 실행 방식이 실제로 바뀔 때만 이력 1건을 남긴다.
- 검증: 백엔드 **99건**(+14), 프론트엔드 **71건**(+6) 전부 통과. 빌드 성공.

## 2. 변경 내용

### 2.1 스키마 (신규 마이그레이션 2개)

| 파일 | 내용 |
|---|---|
| `V8__add_wbs_node_type_and_execution_mode.sql` | `wbs_items.node_type`(백필 후 NOT NULL), `wbs_items.execution_mode`(NULL 허용, DB 기본값 없음) |
| `V9__create_wbs_execution_mode_changes_table.sql` | `wbs_execution_mode_changes` (실행 방식 변경 이력) |

백필 규칙은 **지금 화면이 판정하는 것과 동일**하다 — 자식이 있으면 `SUMMARY`, 없으면 `WORK_PACKAGE`.
`UPDATE ... WHERE id IN (SELECT parent_id FROM wbs_items WHERE parent_id IS NOT NULL)` 한 줄이며 H2와
PostgreSQL 양쪽에서 동작하는 구문만 썼다.

`MILESTONE`은 넣지 않았다. 마일스톤 표시는 Step 6의 범위이고, 지금 상수만 추가하면 처리하지 않는 값을
모든 분기에서 다뤄야 한다.

### 2.2 백엔드

**신규**

| 파일 | 역할 |
|---|---|
| `domain/WbsNodeType.java` | `SUMMARY` / `WORK_PACKAGE` |
| `domain/ExecutionMode.java` | `WATERFALL` / `AGILE` / `HYBRID` |
| `domain/ExecutionModeSummary.java` | 상위가 보여줄 하위 실행 방식 집계 (모드별 개수) |
| `domain/WbsExecutionModeChange.java` | 변경 이력 엔티티 |
| `domain/WbsExecutionModeChangeRepository.java` | 도메인 포트 |
| `infrastructure/persistence/WbsExecutionModeChange{JpaRepository,RepositoryAdapter}.java` | 기존 포트/어댑터 패턴 그대로 |

**수정**

| 파일 | 변경 |
|---|---|
| `domain/WbsItem.java` | 두 필드 + 접근자 + `workPackage()`. **기존 8인자 생성자는 남겨** 도메인 테스트를 건드리지 않았다(기본 `WORK_PACKAGE`, 미지정) |
| `domain/WbsNode.java` | `executionModeSummary` 추가. `summary()`는 **그대로 자식 유무 기준** (§3.2) |
| `domain/WbsTreeAssembler.java` | 하위 Work Package의 실행 방식 집계. **진행률·일정 집계 로직은 손대지 않았다**(Step 5 범위) |
| `application/WbsService.java` | 구조 규칙 검증 3종, 전환 시 값 보관, 이력 기록 |
| `application/dto/WbsItem{Create,Update}Request.java` | 두 필드 (둘 다 nullable) |
| `application/dto/WbsNodeResponse.java` | `nodeType`, `executionMode`, `executionModeSummary` |
| `application/dto/ProjectExportResponse.java` | `ExportedWbsItem`에 두 필드 |
| `application/ExportService.java` | `FORMAT_VERSION` 1 → **2** |
| `application/ImportService.java` | `SUPPORTED_FORMAT_VERSION` 2, 구형 파일 보정, 새 필드 보존 |
| `presentation/GlobalExceptionHandler.java` | enum 범위를 벗어난 값에 필드명과 가능한 값을 담은 메시지 (§4.2 5번) |

### 2.3 프론트엔드

| 파일 | 변경 |
|---|---|
| `shared/executionMode.ts` (신규) | 타입·라벨·요약 문자열. `shared/delay.ts`와 같은 위치·같은 이유(여러 화면이 같은 값을 다르게 부르지 않도록) |
| `shared/executionMode.spec.ts` (신규) | 요약 문자열 규칙 6건 |
| `api/wbsApi.ts` | `WbsNode`·`WbsItemInput`에 필드 추가 |
| `features/wbs/WbsForm.vue` | `구분`·`실행 방식` 셀렉트, Summary 안내와 보관값 안내 |
| `features/wbs/WbsTree.vue` | `실행 방식` 열, Work Package 행의 `하위` 버튼 비활성, Work Package 안으로의 드롭 차단 |
| `features/wbs/wbsTree.spec.ts` | 픽스처에 새 필드 |
| `shared/exportRows.ts` | WBS CSV의 `구분`을 관리 단위 라벨로, `실행 방식` 열 추가 |

## 3. 결정과 근거

### 3.1 기본값 — `NULL`(미지정), `WATERFALL`이 아니다

**결정:** `execution_mode`는 nullable, DB 기본값 없음. `NULL`은 `미지정`으로 표시하고 "수동 진행률
입력을 계속 쓴다"는 뜻이다. 새 항목의 `node_type` 기본값은 `WORK_PACKAGE`.

**근거:** 설계 §6.3의 Waterfall 진척은 *승인 체크포인트 가중치 비율*인데 기존 데이터에는 체크포인트가
없다. `WATERFALL`을 기본값으로 넣으면 Step 5에서 기존 프로젝트 전부가 0% 또는 `산정 전`이 되어,
지시서 4항이 요구하는 "기존 동작이 유지되는 기본값"의 정반대가 된다. `AGILE`도 Backlog가 없어 분모가
0이라 같은 문제다. `미지정`은 실행 방식 선택을 사용자의 명시적 행위로 남기고 그때까지 아무 숫자도
바꾸지 않는다.

`ExecutionMode`에 `UNSPECIFIED` 상수를 두지 않은 것도 같은 이유다 — 드롭다운에서 고를 수 있는 값이
되어 버리고, "아직 정하지 않음"과 "미지정으로 정함"을 구분할 수 없게 된다.

### 3.2 관리 단위 구분을 저장한다 (그리고 `summary`는 그대로 둔다)

Step 1 보고서 §6.1의 권고대로 `node_type`을 저장한다. 저장하지 않으면 Work Package에 자식 하나를
추가하는 순간 실행 방식과 (Step 3 이후의) Backlog 귀속이 말없이 고아가 된다.

**`WbsNode.summary()`는 바꾸지 않았다.** 두 값은 서로 다른 질문에 답한다.

| 값 | 질문 | 기준 |
|---|---|---|
| `summary` | 이 행의 일정·진행률은 어디서 왔나? | 자식 유무 (기존과 동일) |
| `nodeType` | 이 항목이 최하위 관리 단위인가? | 저장값 |

백필 덕분에 기존 데이터에서는 둘이 항상 일치하고, **`SUMMARY`로 전환했지만 아직 자식이 없는 과도
상태에서만 갈라진다.** 그때 일정 집계를 `nodeType` 기준으로 바꿔 버리면 자식이 없는 그 항목의 날짜가
빈칸이 되어 입력한 값이 화면에서 사라진다. 그래서 일정은 계속 자식 유무로 판단한다.

### 3.3 자식 추가 규칙과 전환 시 값 보관

- **Work Package에는 하위를 둘 수 없다.** 생성·이동 모두 400으로 거부한다.
- 하위를 두려면 **구분을 `SUMMARY`로 명시적으로 전환**해야 한다.
- **전환할 때 실행 방식을 지우지 않고 보관한다**(설계 §5: 실행 방식 변경 시 기존 실적과 연결 정보를
  삭제하지 않는다). 구분을 `WORK_PACKAGE`로 되돌리면 그 값이 다시 적용된다.
- 보관 중인 값은 응답에 그대로 실려 화면이 `보관 Agile` 배지로 **정리 대상임을 알린다.** 지금 적용되는
  값이 아니라는 사실만 따로 표시하고, 상위 요약에는 절대 넣지 않는다.

이 때문에 "Summary에 실행 방식 금지"를 **"null이어야 한다"가 아니라 "바꿀 수 없다"** 로 구현했다.
편집 폼은 보관값을 그대로 되돌려 보내므로, 값이 같으면 변경이 아니라 통과시켜야 한다.

### 3.4 상위 WBS 표시 규칙

- 상위는 실행 방식을 **직접 갖지 않고**, 하위 Work Package의 실행 방식을 개수로 요약해 보여준다
  (예: `Waterfall 1 · Agile 3 · 미지정 1`).
- **손자까지 센다.** 직접 자식만 세면 자식이 모두 Summary인 상위가 아무것도 보고하지 못한다.
- **0건인 실행 방식은 빼고, `미지정`은 숨기지 않는다.** 배정되지 않은 Work Package가 PM이 확인해야 할
  대상이다.
- `executionModeSummary`는 자식이 없는 항목에서 `null`이다 — 요약할 것이 없고, 그 항목은 자기 실행
  방식이 곧 답이다.

### 3.5 이력의 범위

`wbs_execution_mode_changes`는 `(project_id, wbs_item_id, previous_mode, new_mode, changed_at)`만
갖는다. **변경자·사유 컬럼은 두지 않았다** — 로그인이 없어 변경자를 알 수 없고, 사유를 받을 화면도
없는 상태에서 컬럼만 만들면 아무도 채우지 않는 값이 남는다(Step 1 보고서 §9 위험 4가 지적한 상황).
설계 §11.1의 일반 `ChangeLog`는 Step 5에서 가중치·Baseline 이력과 함께 도입하고 이 표를 흡수한다 —
지금 만들면 Step 5의 기능을 선행 구현하는 셈이다.

**기록 시점:** 값이 실제로 달라질 때만. 같은 값 재저장은 이력이 아니고, `미지정 → 지정`과
`지정 → 미지정` 양방향 모두 기록한다(그래서 두 컬럼 모두 NULL 허용). 구분만 바뀌고 값이 그대로인
전환은 기록하지 않는다.
**가져오기는 이력을 남기지 않는다** — 초기 상태를 세우는 것이지 누가 실행 방식을 바꾼 것이 아니다.

### 3.6 가져오기·내보내기 하위 호환

- `formatVersion`을 **1 → 2**로 올리고, 가져오기는 2까지 지원한다. 3 이상은 기존처럼 거부한다.
- **구형 파일(두 필드 없음)** 은 마이그레이션과 똑같은 규칙으로 채운다: 파일 안에서 자식이 있으면
  `SUMMARY`, 없으면 `WORK_PACKAGE`, 실행 방식은 `미지정`.
- **`node_type`은 파일 값을 그대로 믿지 않는다.** 자식이 있으면 파일이 `WORK_PACKAGE`라고 적어도
  `SUMMARY`로 바로잡는다. `code`를 다시 계산하는 것과 같은 이유다 — 자식 있는 Work Package는 편집
  규칙이 만들 수 없는 상태이고, 그대로 넣으면 사용자가 고칠 수도 없다. 거부하지 않고 바로잡는 것은
  "거부하면 데이터를 아예 못 넣게 된다"는 기존 방침을 따른 것이다.
- Summary가 보관 중인 실행 방식은 그대로 가져온다(§3.3과 같은 이유).

## 4. 검증

### 4.1 실행한 명령

| 명령 | 결과 |
|---|---|
| `cd backend && ./gradlew clean build` | **BUILD SUCCESSFUL**, 테스트 **99건 / 실패·오류 0** (Step 1 시점 85건 → +14) |
| `cd frontend && npm run build` | `vue-tsc -b` 타입체크 통과 + vite 빌드 성공 |
| `cd frontend && npm test` | **6개 파일 71건 전부 통과** (Step 1 시점 65건 → +6) |

추가된 테스트: `WbsServiceTest`(11건 — 실행 방식·구분 규칙), `ImportServiceTest`의
`ExecutionModeFields`(3건), `executionMode.spec.ts`(6건).

### 4.2 기존 데이터에 대한 마이그레이션·API 검증 (실측)

`flyway.target=7`로 **Step 2 이전 스키마**를 만든 뒤 기존 데이터를 넣고, 앱을 정상 기동해 실제 Flyway가
V8·V9를 증분 적용하도록 했다. 사용한 데이터는 상위 1 + 하위 2 + 최상위 단독 leaf 1.

| # | 확인 항목 | 결과 |
|---|---|---|
| 1 | Flyway 증분 적용 | `Successfully applied 2 migrations ... now at version v9`. `ddl-auto: validate` 통과 |
| 2 | 백필 | 자식 있는 항목 → `SUMMARY`, leaf 3개 → `WORK_PACKAGE`, 실행 방식 전부 `null` |
| 3 | 기존 동작 보존 | 진행률 집계 그대로(80·20 → 상위 50), 일정·정렬·WBS 코드 그대로 |
| 4 | 기존 레코드 수정·재조회 | leaf에 `AGILE` 지정 → 재조회에서 유지, 상위 요약이 `agile 1 · unspecified 1`로 갱신 |
| 5 | Summary에 실행 방식 지정 | **400** "상위(Summary) 항목에는 실행 방식을 지정할 수 없습니다…" |
| 6 | Work Package 아래 하위 추가 | **400** "'단독 업무'은(는) Work Package라 하위 항목을 둘 수 없습니다…" |
| 7 | 하위가 있는 항목을 Work Package로 | **400** "하위 항목이 있는 항목은 Work Package로 바꿀 수 없습니다…" |
| 8 | 잘못된 실행 방식 값 | **400** "'SCRUM'은(는) executionMode에 허용되지 않는 값입니다. 가능한 값: WATERFALL, AGILE, HYBRID" |
| 9 | 잘못된 구분 값 | **400** "'MILESTONE'은(는) nodeType에 허용되지 않는 값입니다. 가능한 값: SUMMARY, WORK_PACKAGE" |
| 10 | 내보내기 | `formatVersion: 2`, 각 항목에 `nodeType`·`executionMode` |
| 11 | 신형 파일 가져오기 | 새 프로젝트 생성, 두 필드 보존 |
| 12 | 구형 파일(`formatVersion 1`) 가져오기 | 새 프로젝트 생성, 자식 유무로 구분 채움, 실행 방식 `미지정` |
| 13 | 이력 | `wbs_execution_mode_changes`에 `(11, null → AGILE)` 1건. 가져오기는 0건 |

8·9번은 처음에 400이지만 메시지가 "요청 내용을 읽을 수 없습니다"로 뭉개져 무엇이 틀렸는지 알 수 없었다.
지시서의 완료 기준이 "유효하지 않은 실행 방식을 검증한다"이므로 `GlobalExceptionHandler`가 enum 값
오류에 필드명과 가능한 값을 붙이게 고쳤다. 다른 enum 필드(RAID 종류, 프로젝트 상태 등)도 함께 좋아진다.

### 4.3 되돌리기 가능성

Flyway Community에는 `undo`가 없어 두 방법뿐이고, 둘 다 확인했다.

**(a) 되돌리는 SQL** — 별도 H2 파일에서 실제로 실행해 컬럼·테이블이 사라지고 **WBS 4행이 그대로**
남는 것을 확인했다.

```sql
DROP TABLE IF EXISTS wbs_execution_mode_changes;
ALTER TABLE wbs_items DROP COLUMN execution_mode;
ALTER TABLE wbs_items DROP COLUMN node_type;
DELETE FROM flyway_schema_history WHERE version IN ('8', '9');
```

**(b) 파일 백업** — 앱 종료 후 `~/.project-flow/data` 폴더 복사. `AUTO_SERVER=TRUE`라 실행 중 복사는
일관성이 보장되지 않는다.

**중요:** 스키마만 되돌리면 **기동이 실패한다.** 이번 검증에서 `flyway.target=7`로 기동했을 때
`ddl-auto: validate`가 `Schema-validation: missing table [wbs_execution_mode_changes]`로 거부하는 것을
실제로 확인했다. Step 1 보고서 §9 위험 2가 말한 그대로이며, **코드와 스키마를 함께 되돌려야 한다.**

### 4.4 실행하지 않은 검증과 이유

| 미실행 항목 | 이유 |
|---|---|
| PostgreSQL(`server` 프로필)에서의 마이그레이션 | 이 장비에 PostgreSQL과 Docker가 없다. 양쪽에서 동작하는 구문만 썼으나 실측하지 못했다 |
| 브라우저에서 화면 조작 | 프론트엔드는 타입체크·빌드·단위 테스트까지만 확인했다. 셀렉트·배지·드롭 차단의 실제 조작은 미확인 |
| 다크 모드 육안 확인 | 새로 쓴 색은 없고 기존 토큰(`--disabled-bg`/`--disabled-fg`, `--text-faint` 등)만 사용했다 |
| 대량 데이터에서의 성능 | 이번 변경은 컬럼 2개와 트리 조립 시 카운터 누적뿐이라 복잡도가 그대로다 |

### 4.5 검증 중 발견한 오해 하나 (Step 2와 무관)

검증용 DB에 기존 데이터를 **id를 직접 지정한 SQL로** 심었더니, 그 뒤 가져오기가 간헐적으로 500을
냈다. 원인은 H2의 `GENERATED BY DEFAULT AS IDENTITY`가 명시적 INSERT로는 시퀀스를 올리지 않기
때문이다 — 발급된 id가 심어둔 id(10~13)와 겹치는 동안만 실패하고, 시퀀스가 그 구간을 지나면 매번
성공한다(3회 연속 201로 확인). **Step 2 변경과 무관하고 실제 사용 경로에서는 나타나지 않는다**(모든
행이 시퀀스에서 나오므로). 다만 누군가 SQL로 데이터를 복원할 때는 시퀀스를 함께 올려야 한다.

## 5. 기존 데이터·사용자 변경 사항 보존

- **진척 데이터를 초기화하지 않았다.** 실행 방식을 골랐다는 이유로 `progress`를 건드리는 코드는 없다.
- **진행률 집계식을 바꾸지 않았다.** `WbsTreeAssembler`의 leaf 개수 가중 평균은 그대로다(설계의 가중치
  기반 집계는 Step 5, 사용자 결정이 필요한 항목).
- **일정·정렬·WBS 코드·삭제 동작 모두 그대로.** 드래그&드롭도 Work Package 안으로 넣는 경우만 막았고,
  형제 순서 변경은 제약이 없다.
- 마이그레이션은 `ALTER`/`UPDATE`만 하고 행을 지우거나 새로 만들지 않는다.

## 6. 남은 문제

1. **Summary가 보관 중인 실행 방식은 화면에 배지로만 알린다.** 일괄 정리 기능은 없다. 설계 §5의
   "정리 대상 안내"까지가 이번 범위이고, 정리 UI는 필요해지면 별도로 다룬다.
2. **이력을 읽는 화면이 없다.** 테이블과 저장 경로만 있고 조회 API·화면은 없다. Step 5에서 `change_logs`로
   흡수하면서 그때 함께 만드는 것이 맞다고 판단했다(요구사항 8.2도 같은 자리).
3. **`MILESTONE` 미도입.** Step 6에서 `node_type`에 상수를 추가하는 형태가 된다.
4. **PostgreSQL 미검증** (§4.4).
5. **화면 조작 미검증** (§4.4).

## 7. Step 3 연결 지점

- Backlog 귀속 대상 조건: `node_type = 'WORK_PACKAGE'`. Summary 연결 차단은 이제 **저장된 값**으로
  판정하므로 다른 행의 편집에 흔들리지 않는다.
- Agile 실행 연결이 가능한 항목: `execution_mode IN ('AGILE', 'HYBRID')`. `WATERFALL`·`미지정` 항목에
  Backlog를 붙이려 할 때 "실행 방식 변경이 필요하다"고 안내하라는 지시서 8항의 판정 근거가 된다.
- 교차 프로젝트 참조 검증은 `RaidService.requireWbsItemOfProject`/`requireOwnerOfProject` 패턴을
  그대로 재사용하면 된다.
- **주의:** `wbs_items.parent_id`가 `ON DELETE CASCADE`다. Backlog 귀속이 붙은 뒤 상위 WBS를 지우면
  실행 이력까지 함께 사라질 수 있어, Step 3에서 삭제 정책을 반드시 다시 설계해야 한다
  (Step 1 보고서 §9 위험 3).
- 응답 규칙은 그대로 유지했다 — 변경 API는 갱신된 트리 전체를 반환하고 `referenceDate`를 함께 싣는다.
