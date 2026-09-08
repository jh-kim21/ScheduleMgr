# Step 05. 기준 정보 정비 및 진행률 자동 집계 (결과 보고서)

| 구분 | 내용 |
|---|---|
| 대상 저장소 | `D:\Git\ScheduleMgr` (branch `main`) |
| 지시서 | [Hybrid_PM_Step05_Progress.md](../Hybrid_PM_Step05_Progress.md) |
| 선행 보고서 | [Step 01](Hybrid_PM_Step01_Analysis.md) · [02](Hybrid_PM_Step02_Result.md) · [03](Hybrid_PM_Step03_Result.md) · [04](Hybrid_PM_Step04_Result.md) |
| 작성일 | 2026-09-08 |
| 상태 | **완료.** Step 6은 자동 실행하지 않았다. |

## 1. 요약

Step 1이 "모두 미구현"이라고 보고했던 선행 4종(**가중치·완료 기준·Baseline·변경 이력**)을 갖추고, 그
위에 공통 집계를 얹었다.

- **집계식 네 가지**를 구현했다 — Agile / Waterfall / Hybrid / 상위 WBS. 지시서가 완료 기준으로 든
  세 숫자(60%, 54%, 71%)를 테스트로 고정했다.
- **산정 전은 0%가 아니다.** 분모가 없으면 `null`을 돌려주고, 부모는 그 자식을 조용히 빼지 않고
  `incomplete`로 전파한다. 화면도 막대를 그리지 않고 "산정 전"이라고 적는다.
- **전환 정책으로 기존 숫자를 지켰다.** 실행 방식 미지정 Work Package는 입력한 진행률을 그대로 쓰고
  (`MANUAL`), 가중치가 하나도 없는 가지는 예전과 같은 leaf 개수 가중 평균을 쓴다(`LEGACY_ROLLUP`).
  **아무것도 지정하지 않은 프로젝트의 화면 숫자는 Step 5 이전과 같다.**
- **계획 진척은 승인된 기준선에서만 나온다.** 없으면 미산정이고, 시간이 지났다는 것만으로 실제 진척을
  채우지 않는다.
- **두 좁은 이력 표를 `change_logs`로 흡수**했다 (Step 2·3에서 약속한 대로). 마이그레이션이 기존 행을
  모두 옮긴 뒤 표를 지웠고, 실측으로 확인했다.
- **Work Package → Summary 전환을 막았다**(사용자 결정). Step 3이 표시만 하던 이중 계산 위험이
  구조적으로 사라졌다.
- 검증: 백엔드 **175건**(+19), 프론트엔드 **95건**(+6) 전부 통과.

## 2. 변경 내용

### 2.1 스키마 (신규 마이그레이션 5개)

| 파일 | 내용 |
|---|---|
| `V15__add_wbs_progress_basis.sql` | `wbs_items.weight`, `agile_ratio`, `acceptance_status` |
| `V16__create_acceptance_checkpoints_table.sql` | `acceptance_checkpoints` — Waterfall의 분모 |
| `V17__create_baselines_tables.sql` | `baselines` + `baseline_items` (승인 시점 복사본) |
| `V18__create_change_logs_table.sql` | `change_logs` + **기존 두 이력 표 흡수 후 삭제** |
| `V19__create_progress_snapshots_table.sql` | `progress_snapshots` |

### 2.2 백엔드

**신규 도메인**: `ProgressCalculator`(집계식 전부), `ProgressBasis`, `AcceptanceStatus`,
`AcceptanceCheckpoint`, `Baseline`, `BaselineItem`, `ChangeLog`, `ChangeReason`, `ProgressSnapshot`
+ 각 포트·어댑터.

**신규 애플리케이션**: `ProgressService`(읽기·집계), `ProgressBasisService`(체크포인트·기준선·스냅샷),
`ProgressController`, `ProgressResponse`/`ProgressRequests`/`SnapshotResponse`.

**제거**: `WbsExecutionModeChange`·`BacklogLinkChange`와 그 포트·어댑터·enum
(→ `ChangeLog`/`ChangeReason`으로 흡수).

**수정**: `WbsItem`(세 필드), `WbsService`(전환 차단·가중치 이력·공통 결과 적용),
`BacklogService`(이력 저장소 교체), `WbsNodeResponse`(computed 필드),
`ExportService`/`ImportService`(`FORMAT_VERSION` 4 → **5**).

### 2.3 프론트엔드

`shared/progress.ts`(+spec), `api/progressApi.ts`, `features/progress/useProgress.ts`,
`views/ProgressView.vue`, 라우트·메뉴(`/progress` "진척"),
`features/wbs/WbsTree.vue`·`WbsForm.vue`(공통 결과 표시 + 가중치·α·인수 상태 입력),
`stores/scheduleCache.ts`(진척 캐시 키), `shared/exportRows.ts`(CSV에 진척·기준·가중치).

## 3. 집계식과 누락값 정책

### 3.1 네 가지 식

| 대상 | 식 | 분모가 없을 때 |
|---|---|---|
| **Agile** | 100 × Σ(완료 Story·Bug 가중치) / Σ(집계 대상 가중치) | 집계 대상 0건 → **산정 전** |
| **Waterfall** | 100 × Σ(승인된 체크포인트 가중치) / Σ(전체 체크포인트 가중치) | 체크포인트 0건 → **산정 전** |
| **Hybrid** | α × Agile + (1 − α) × Waterfall | α 미정 또는 한쪽 요소 없음 → **산정 전** |
| **상위 WBS** | Σ(직계 자식 가중치 × 자식 진척) / Σ(직계 자식 가중치) | 셀 수 있는 자식 없음 → **산정 전** |

- **미입력 가중치는 균등(1)로 본다** — Backlog 항목과 체크포인트 모두. "동일 가중치 10개 중 6개
  완료 = 60%"가 기본 사례이고, 항목마다 가중치를 넣어야만 숫자가 나오면 기능을 쓸 수 없다.
- **가중치 0은 미입력과 다르다.** 0은 "이 항목은 진척에 기여하지 않는다"이고 미입력은 "아직 정하지
  않았다"이다. 전부 0이면 분모가 0이므로 산정 전이다.
- **Hybrid는 세 입력이 모두 있어야 한다.** 한쪽만으로 비중을 적용하면 합의되지 않은 숫자를 만드는
  셈이다.
- **Epic·Task는 분모에 들어가지 않는다.** `ProgressService`가 `aggregated()`로 걸러 넘기고,
  보관된 항목도 뺀다.
- **Story Point는 가중치로 변환하지 않는다.** 분모는 `progress_weight`, 속도는 `points_at_close`다.

### 3.2 산정 전과 불완전의 전파

| 상황 | 결과 |
|---|---|
| 분모 없음 | `percent = null`, `basis = NOT_ESTIMABLE`, 이유를 `note`에 담는다 |
| 자식 일부가 산정 전 | 그 자식을 빼고 계산하되 `incompleteChildren = true` |
| 자식 일부만 가중치 있음 | 가중치 없는 자식을 빼고 계산하되 `incompleteWeights = true` |
| 셀 수 있는 자식 없음 | 부모도 산정 전 |

**0%로 단정하지 않고, 조용히 제외하지도 않는다.** 화면은 "집계가 불완전합니다"와 산정 전 목록을
함께 보여준다.

### 3.3 반올림

계산은 `double`로 하고 **API는 반올림하지 않은 값**을 보낸다. 반올림은 화면(`progressText`)과
CSV에서만 한다 — 상위 집계가 이미 반올림된 자식 값으로 계산되면 오차가 쌓인다.

## 4. 전환 정책 (기존 숫자를 지키는 방법)

Step 1 §8.1-2가 사용자 결정으로 남겨 둔 항목이다. 권고안대로 구현했다.

| 상황 | 동작 | 근거 |
|---|---|---|
| 실행 방식 **미지정** Work Package | 입력한 `progress`를 그대로 사용 (`MANUAL`) | 기존 데이터 전부가 이 상태다 |
| 직계 자식에 가중치가 **하나도 없는** 상위 | 기존과 같은 **leaf 개수 가중 평균** (`LEGACY_ROLLUP`) | 직계 균등 평균으로 바꾸면 기존 숫자가 변한다 |
| 자식 중 **하나라도** 가중치가 있는 상위 | 설계 §6.4의 가중 평균 (`ROLLUP`) | 가중치를 넣은 것이 곧 새 식으로의 전환 의사 |

**결과: 아무것도 지정하지 않은 프로젝트의 숫자는 Step 5 이전과 같다.** 가지 단위로 옮겨 간다.

기존 수동 진척(`wbs_items.progress`)은 **지우지 않는다.** 미지정 Work Package의 근거값이며,
실행 방식을 지정한 뒤에도 컬럼에 남는다(되돌리면 다시 쓰인다).

## 5. 기준 정보

### 5.1 가중치·비중·인수 (`V15`)

- `weight` — 형제 간 비중. Backlog의 `progress_weight`, Story Point와 **다른 값**이다.
- `agile_ratio` — Hybrid의 α(0~100). 기본값을 두지 않았다: 비중은 실행 전에 합의되어야 하는
  값이라 임의값을 넣으면 합의를 건너뛴다.
- `acceptance_status` — `PENDING`/`ACCEPTED`/`null`. **실행 진척과 분리**되어 있어, 100%인데
  `PENDING`이면 "인수 대기"로 표시하고 완료로 세지 않는다.

### 5.2 승인 체크포인트 (`V16`)

Waterfall·Hybrid 진척의 분모다. 승인은 `approved_by`와 `approved_at`을 함께 찍는다 — 승인은
"누가"가 핵심이라 승인자 없이는 거부한다(400). 승인 취소는 둘 다 비우고, 그 사실은 `change_logs`에
남는다.

### 5.3 Baseline (`V17`)

- **자동 생성·자동 승인 경로가 없다.** 사용자가 승인자를 적어 명시적으로 승인할 때만 만들어진다.
- 승인 시점의 범위·일정·가중치를 **복사**한다. 참조로 두면 계획이 바뀌는 순간 "승인했던 계획"이
  사라진다.
- `code`·`name`도 복사한다 — WBS 코드는 트리 위치에서 파생되므로 나중에 재현할 수 없다.
- Work Package의 **완료 기준**은 그 체크포인트들의 제목·조건을 한 줄로 이어 붙여 담는다.
- `wbs_item_id`에 FK를 걸지 않는다: 항목이 지워진 뒤에도 "그때 이 범위를 승인했다"가 남아야 한다.

### 5.4 변경 이력 통합 (`V18`)

Step 2의 `wbs_execution_mode_changes`와 Step 3의 `backlog_link_changes`를 `change_logs`로
**흡수하고 두 표를 삭제**했다. 마이그레이션이 기존 행을 먼저 옮긴다.

기록 대상: 실행 방식 변경, Backlog 귀속 변경(상속·삭제 분리 포함), **가중치·α 변경**,
**체크포인트 승인·가중치 변경**. 분모가 움직인 이유를 설명할 수 있어야 한다는 설계 §6.1의 요구다.

변경자 컬럼은 여전히 없다 — 로그인이 없어 null만 남는다.

### 5.5 스냅샷 (`V19`)

진척은 저장하지 않고 매번 계산한다. 그래서 **지난 보고는 재현할 수 없다** — 그때의 범위·가중치·정책이
지금과 다르기 때문이다. 보고 시점의 프로젝트 수준 숫자를 JSON 문자열로 적어 둔다. 항목별 상세는
담지 않는다(최소 스냅샷의 범위를 넘고 크기가 폭발한다).

## 6. 계획 진척과 범위 비교

- **계획 진척은 승인된 기준선의 날짜에서만** 나온다. 기준선이 없으면 `null`(미산정)이고, 화면도
  그렇게 적는다. 시간 경과는 실제 진척으로 쓰지 않는다.
- 비교는 **기준선에 담긴 Work Package 중 날짜가 있는 것들**에 대해, **기준선의 가중치로** 이뤄진다.
  실제 값도 같은 집합·같은 가중치로 다시 계산해 `comparablePercent`로 내려보낸다 — 그래야 뺄 수 있다.
  그래서 헤드라인 `actualPercent`(현재 범위 전체)와 `comparablePercent`(기준선 집합)는 다를 수 있고,
  화면이 그렇게 라벨링한다.
- **범위 비교**는 추가/제외/가중치 변경을 항목 이름과 함께 보여준다. 진척이 "일이 되어서"가 아니라
  "분모가 바뀌어서" 움직였을 때 그것을 설명하는 자리다.

## 7. Work Package → Summary 전환 차단 (사용자 결정)

Step 3 §6-1과 Step 4 §7이 남긴 문제다. **전환을 막는 쪽으로 결정**되어 그대로 구현했다.

연결된(보관되지 않은) Backlog가 있는 Work Package는 Summary로 바꿀 수 없다(400). 삭제 가드와 같은
방식이고, 보관된 항목은 막지 않는다. 이로써 `linkedToSummary` 상태가 **새로 생기지 않는다** —
Summary가 하위를 합산하는데 그 아래 Story 가중치를 또 세는 이중 계산이 구조적으로 불가능해졌다.

**이미 그 상태인 기존 데이터**는 남을 수 있다. 그때는 Backlog가 Summary에 붙은 것이므로 그 Summary의
집계에 들어가지 않고(집계 대상은 Work Package뿐), 화면이 `linkedToSummary`로 계속 표시한다 —
정리 대상이라는 뜻이다.

## 8. 검증

### 8.1 실행한 명령

| 명령 | 결과 |
|---|---|
| `cd backend && ./gradlew clean build` | **BUILD SUCCESSFUL**, 테스트 **175건 / 실패·오류 0** (Step 4 시점 156건 → +19) |
| `cd frontend && npm run build` | 타입체크 + 빌드 성공 |
| `cd frontend && npm test` | **10개 파일 95건 전부 통과** (Step 4 시점 89건 → +6) |

추가 테스트: `ProgressCalculatorTest`(19건), `progress.spec.ts`(6건).

### 8.2 완료 기준 숫자 (테스트로 고정)

| 기준 | 결과 |
|---|---|
| 동일 가중치 10개 중 6개 완료 → **60%** | ✅ `sixOfTen` |
| 자식 가중치 20·50·30, 진척 80·40·60 → **54%** | ✅ `fiftyFour` |
| Hybrid α=0.7, Agile 80%, Waterfall 50% → **71%** | ✅ `seventyOne` |
| 분모 0 → 산정 전 | ✅ `noItemsIsNotZero`, `zeroDenominator`, `noCheckpoints` |
| 미산정 자식 → 전파 | ✅ `notEstimableChildPropagates`, `allChildrenNotEstimable` |
| Epic·Task 중복 제외 | ✅ `aggregationUnitsOnly` (+ `ProgressService`가 걸러 넘김) |
| 상위의 수동 진척 미사용 | ✅ `parentManualProgressIgnored` |
| 반올림은 표시 단계에서만 | ✅ `keepsPrecision` (100/3을 그대로 반환, 표시만 33) |

### 8.3 API 실측 (Step 4 검증에 쓴 DB를 이어서)

V14 상태의 DB에 **V15~V19를 증분 적용**하고 (`Successfully applied 5 migrations ... now at v19`,
`ddl-auto: validate` 통과) 아래를 확인했다.

| # | 확인 항목 | 결과 |
|---|---|---|
| 1 | **이력 통합** | `change_logs`에 6종 그룹(WBS 실행 방식 2, Backlog 귀속 14, 가중치 1, 체크포인트 승인 1). 옛 두 표는 `information_schema` 조회 결과 **0개** |
| 2 | 체크포인트 추가 | 201 |
| 3 | 승인자 없이 승인 | **400** "승인자를 입력해야 승인할 수 있습니다." |
| 4 | 승인 | 200, Waterfall 진척 **30%** (30/100 가중치) |
| 5 | Summary에 체크포인트 | **400** "…Summary라 승인 체크포인트를 둘 수 없습니다…" |
| 6 | 기준선 승인 | 201, v1 / 4개 항목 복사 |
| 7 | 계획 진척 | 기준선 승인 전 `null`(미산정) → 승인 후 **40.0**, 편차 산출 |
| 8 | 범위 비교 | 가중치를 30 → 55로 바꾸니 `weightChanged`에 `4 acceptance (30 → 55)` |
| 9 | 스냅샷 | 저장된 metrics가 그때의 값 그대로 (`actual 30.0 / planned 40.0 / variance -40.0 / scopeWeightChanged 1`) |
| 10 | 전환 정책 | 미지정 WP는 `MANUAL`, 가중치 없는 상위는 `LEGACY_ROLLUP` |

### 8.4 실행하지 않은 검증과 이유

| 미실행 | 이유 |
|---|---|
| 브라우저 화면 조작 | 타입체크·빌드·단위 테스트까지만. 진척 화면의 체크포인트 편집·기준선/승인 대화상자·스냅샷 목록의 실제 조작은 미확인 |
| PostgreSQL | 이 장비에 없다. 양쪽에서 동작하는 구문만 썼다 (`V18`의 `CAST(... AS VARCHAR)` 포함) |
| 내보내기·가져오기 API 왕복 | 단위 테스트로만 확인했다 (`formatVersion 5`, 승인 상태·기준선·스냅샷 보존) |
| 되돌리기 리허설 | V15~V19의 역방향 SQL을 실행해 보지 않았다. Flyway Community에 undo가 없다는 제약은 Step 2와 동일하다 |

## 9. 남은 문제

1. **계획 진척은 날짜가 있는 기준선 항목만 본다.** 기준선에 담겼지만 날짜가 없는 항목은 비교에서
   빠지고, 그래서 `actualPercent`와 `comparablePercent`가 다를 수 있다. 화면이 라벨로 구분하지만,
   "왜 두 숫자가 다른가"는 여전히 설명이 필요하다.
2. **비교가 평면적이다.** 기준선은 트리가 아니라 스냅샷 목록이라, 계획 대비 비교는 계층 없이
   기준선 가중치로만 이뤄진다. 헤드라인 진척은 계층 집계다.
3. **`ProgressPolicy` 엔티티를 만들지 않았다.** α는 `wbs_items.agile_ratio`에 두고 변경은
   `change_logs`에 남긴다. 설계 §11.1의 정책 버전·`effectiveFrom`은 구현하지 않았다 — 정책을
   시점별로 갈아 끼우는 요구가 아직 없고, 최소 구조를 우선했다.
4. **지연 판정은 여전히 `progress`(저장값)를 쓴다.** 공통 집계 결과는 `computedProgress`로 따로
   내려간다. 미지정 Work Package에서는 둘이 같아 차이가 없지만, Agile Work Package에서는 지연 배지가
   집계값이 아니라 수동값 기준이다. 두 값을 잇는 것은 Step 6의 간트 작업과 함께 보는 편이 맞다.
5. **스냅샷은 프로젝트 수준만 담는다.** 항목별 과거 진척은 재현할 수 없다.
6. **화면 조작 미검증** (§8.4).

## 10. Step 6 연결 지점

- **공통 진척 조회**: `GET /api/projects/{id}/progress`, 그리고 WBS 트리의 `computedProgress` /
  `progressBasis` / `progressIncomplete`. 간트·RACI·RAID가 같은 숫자를 쓰려면 여기만 보면 된다.
- **기준 일정과 현재 일정의 구분**: `baselines`/`baseline_items`에 승인 시점의 `start_date`·
  `end_date`가 있다. 간트의 "기준 일정 막대"는 이것을, "현재 계획 막대"는 `wbs_items`를 읽는다.
  기준선이 없으면 **미등록으로 표시**하고 현재 계획을 Baseline으로 그리지 않는다(지시서 6-A).
- **실적 컬럼은 아직 없다.** `actual_start`/`actual_end`/`forecast_end`는 만들지 않았다 —
  Step 1 §9 위험 5가 예고한 대로, 간트의 "실제 실적" 막대는 Step 6에서 이 컬럼들을 먼저 추가해야
  한다.
- **인수 상태**: `acceptance_status`와 응답의 `acceptancePending`으로 "진척 100% ≠ 완료"를 이미
  구분한다. Step 6의 Dashboard·간트 경고가 그대로 쓸 수 있다.
