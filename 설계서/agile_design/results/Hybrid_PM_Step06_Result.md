# Step 06. Gantt·RACI·RAID 연계 (결과 보고서)

| 구분 | 내용 |
|---|---|
| 대상 저장소 | `D:\Git\ScheduleMgr` (branch `main`) |
| 지시서 | [Hybrid_PM_Step06_Gantt_RACI_RAID.md](../Hybrid_PM_Step06_Gantt_RACI_RAID.md) |
| 선행 보고서 | [Step 01](Hybrid_PM_Step01_Analysis.md) · [02](Hybrid_PM_Step02_Result.md) · [03](Hybrid_PM_Step03_Result.md) · [04](Hybrid_PM_Step04_Result.md) · [05](Hybrid_PM_Step05_Result.md) |
| 작성일 | 2026-09-08 |
| 상태 | **완료.** |

## 1. 요약

기존 일정·책임·통제 화면 세 개를 Step 2~5가 만든 실행 데이터에 연결했다. 세 영역 모두 **기존 기능을
바꾸지 않고 덧붙이는 방향**으로 갔다 — 간트의 막대, RACI의 글자, RAID의 네 분류는 그대로다.

- **간트가 세 가지 일정을 구분한다** — 승인된 기준 일정(Baseline), 현재 계획, 실제 실적. 예상 종료가
  기준 종료일을 넘으면 경고하고 원본 WBS 행으로 이동한다. **기준선이 없으면 "미등록"이라고 적고 현재
  계획을 기준선으로 그리지 않는다.**
- **Sprint는 자기 레인 하나**다. 여러 Work Package에 걸쳐 있어도 한 번만 그리고, 일정·진척을 만들지
  않는다. WBS 행을 고르면 관련 Sprint가, Sprint를 고르면 관련 WBS 행이 강조된다.
- **RACI가 상위에서 상속된다.** 역할별로 따로 상속하고, 하위가 다시 정하면 재정의로 표시한다. 누락
  검증은 상속까지 본 뒤 leaf에만, 책임자 중복은 그 글자를 실제로 가진 행에 한 번만 보고한다.
- **Story 담당자는 RACI가 아니다.** 매트릭스 행 머리에 참고로 보여줄 뿐 글자로 세지 않고, 담당자를
  바꿔도 그 업무의 A는 움직이지 않는다.
- **RAID 한 항목이 여러 대상에 걸린다** — WBS·Sprint·Backlog. 기존 단일 `wbs_item_id` 컬럼을 링크 표로
  옮겼고, 값을 잃지 않았다. 차단된 Board 카드에서 관련 Issue와 담당자를 볼 수 있다.
- 검증: 백엔드 **197건**(+22), 프론트엔드 **95건**, 실 DB·실 서버 시나리오 **34항목** 전부 통과.

## 2. 변경 내용

### 2.1 스키마 (신규 마이그레이션 2개)

| 파일 | 내용 |
|---|---|
| `V20__add_wbs_actual_dates.sql` | `wbs_items.actual_start_date`, `actual_end_date`, `forecast_end_date` |
| `V21__create_raid_links_table.sql` | `raid_links` 신설 + **기존 `raid_items.wbs_item_id`를 옮기고 컬럼 삭제** |

V21은 데이터를 옮기는 마이그레이션이다. `INSERT ... SELECT`로 기존 단일 연결을 `WBS_ITEM` 링크로 옮긴
뒤 FK·인덱스·컬럼을 지운다. **두 곳에 같은 사실을 두지 않는다** — 남겨 두면 곧 서로 어긋난다.

`target_id`에는 FK가 없다. 세 종류의 대상을 한 컬럼으로 가리키기 때문이고, 대상 존재와 프로젝트 일치는
`RaidService`가 검증한다(설계 §11.3-7). 그래서 **참조가 끊어지는 것을 막는 유일한 장치가 애플리케이션**
이고, 아래 §2.4의 참조 정책이 그 대가다.

### 2.2 6-A. 간트 차트

**세 일정을 한 행에 쌓았다.** 행을 셋으로 늘리지 않은 이유는 접어둔 WBS의 이점이 사라지고 같은 업무의
세 일정을 눈으로 잇기 어려워지기 때문이다.

| 무엇 | 출처 | 화면 |
|---|---|---|
| 기준 일정 | `baseline_items` (Step 5) | 막대 위 얇은 회색 선 |
| 현재 계획 | `wbs_items.start_date/end_date` | 기존 막대 (지연 색·위반 점선 그대로) |
| 실제 실적 | `wbs_items.actual_*` (신규) | 막대 아래 얇은 완료색 선 |
| 예상 종료 | `wbs_items.forecast_end_date` (신규) | 오른쪽 삼각 표식, 초과면 경고색 |

- **기준선이 없으면 `hasBaseline: false`**를 내려보내고 화면이 "기준 일정 미등록"이라고 적는다. 현재
  계획을 기준선 자리에 복사하지 않는다 — 그렇게 하면 초과를 영원히 못 본다.
- **초과 판정은 예상 종료 우선, 없으면 현재 계획**으로 한다. 예측을 적지 않았다는 것이 "늦지 않는다"는
  뜻은 아니다. `baselineSlipDays`로 며칠인지 함께 준다.
- **초과 경고는 원본으로 이동한다** — 간트 화면 배너의 링크가 `/wbs?focus={id}`로 가고, 그 행이 펼쳐져
  강조된다(Step 3이 Backlog→WBS용으로 만든 경로를 그대로 쓴다).
- **Sprint 종료일을 실제 완료일로 복사하지 않는다.** Sprint가 끝난 것과 Work Package의 산출물이 완료된
  것은 다른 사실이고, 한 Sprint가 여러 Work Package에 걸칠 수 있다. 두 값 사이에 코드 경로가 없다.
- **진척은 Step 5의 공통 집계를 그대로 읽는다**(`ProgressService.resultsByWbsItem`). 간트가 자기 식으로
  다시 계산하면 WBS 화면과 다른 숫자가 나온다. 산정 전은 여기서도 `null`이고 0%로 눕히지 않는다.
- 차트 폭은 기준·실적·예상·Sprint까지 포함해 잡는다. 그러지 않으면 계획 밖으로 나간 실적이 잘린다.

**Sprint 레인**은 업무 행 위의 별도 스트립이다. `GanttResponse.sprints`가 Sprint당 한 항목이고, 그
Sprint가 건드리는 Work Package id를 참조로 싣는다. 업무 행에는 반대 방향 참조(`sprintIds`)가 붙는다.
**어느 쪽도 일정이나 진척을 파생시키지 않는다** — 강조에만 쓴다. 그래서 한 Sprint가 세 Work Package에
걸쳐도 집계가 세 번 되지 않는다.

### 2.3 6-B. RACI

[`RaciInheritance`](../../../backend/src/main/java/com/projectflow/domain/RaciInheritance.java)를 새로
두었다. **아무것도 저장하지 않는다** — WBS 코드·상위 일정과 같이 조회 시점에 계산한다. 저장하면 항목을
옮길 때 조상의 글자가 하위에 복사본으로 남는다.

- **역할별로 상속한다.** 자기 R이 있어도 상위 A는 그대로 온다. 자기 글자 하나를 전체 재정의로 보면,
  담당자를 적는 순간 단계의 책임자가 조용히 사라진다.
- **가장 가까운 상위가 이긴다.** 단계와 하위 단계가 모두 A를 지정하면 하위 단계의 것이 유효하고, 이것이
  *재정의*다. 상위의 글자는 셀에 남기되 취소선으로 무효임을 보인다 — 숨기면 왜 다른지 알 수 없다.
- **셀은 자기 글자와 상속 글자를 나눠서 싣는다**(`roles` / `inherited`). 상속 글자에는 배정 id가 없다 —
  이 행에서 지울 수 없고, 그 글자를 가진 행을 고쳐야 한다.
- **검증 규칙이 바뀌었다** (의도한 변경):
  - 누락(A·R)은 **상속까지 본 뒤 leaf에만**. 단계의 A를 물려받은 Work Package는 누락이 아니다. 예전
    규칙 그대로 두면 매트릭스가 이미 답한 것을 다시 입력하라고 요구하게 된다.
  - 책임자 중복은 **그 글자를 실제로 가진 행에** 보고한다(Summary 포함). 그 행이 정리할 곳이고, 하위
    모두에 되풀이하면 실수 하나가 leaf 수만큼 불어난다. **조용히 지우지 않는다** — 지시서대로 정리
    대상으로 알린다.
- **Story 담당자·Reviewer와 RACI를 구분한다.** `RaciTaskResponse.storyAssignees`로 Work Package별 Backlog
  담당자와 건수를 함께 주되, 열이 아니라 행 머리의 주석으로 그린다. Backlog 담당자를 바꾸는 경로와
  `raci_assignments`를 쓰는 경로 사이에 코드가 없다 — 어느 쪽도 다른 쪽을 자동 변경하지 않는다.

### 2.4 6-C. RAID

**기존 네 분류(Risk·Assumption·Issue·Dependency)를 그대로 유지했다.** 문서 예시 때문에 바꾸지 않았고,
저장 값·라벨·설명 모두 손대지 않았다. 매핑은 §5에 적었다.

- **한 항목이 여러 대상에 걸린다.** `raid_links(project_id, raid_item_id, target_type, target_id)`이고
  `(raid_item_id, target_type, target_id)`가 UNIQUE다. 같은 대상 중복은 애플리케이션에서 400으로 먼저
  막는다 — UNIQUE 위반은 500으로 나가고 "이미 연결됨"은 화면이 보여줄 수 있는 말이다.
- **수정은 남길 것을 남긴다.** 전부 지우고 다시 넣으면 살아남은 연결도 새 id와 새 `created_at`을 받아,
  무관한 편집마다 "언제 붙였나"가 오늘로 바뀐다. 그래서 차집합만 지우고 새것만 넣는다.
- **일정 의존성과 구분한다.** RAID의 `DEPENDENCY`는 프로젝트 밖에서 받아야 하는 것이고, WBS의 선후행은
  안쪽의 순서 제약이다. 링크는 연관일 뿐 일정 제약이 아니며, 두 코드 경로는 만나지 않는다.
- **Board의 차단 카드가 관련 Issue와 담당자를 보여준다.** 그 Backlog 항목에 걸린 것과 그 항목의 Work
  Package에 걸린 것을 합쳐, 종결되지 않은 것만. 차단 카드에서만 편다 — 모든 카드에 붙이면 보드가 읽히지
  않는다. 화면은 읽기만 하고 등록부를 고치지 않는다.
- **참조 정책** (지시서 "연결 대상 보관·삭제 시"):
  - **삭제**: 연결만 끊고 RAID 항목은 남긴다(`RaidService.detachTargets`). WBS·Backlog·Sprint 삭제
    경로 세 곳에서 부른다. 예전 `ON DELETE SET NULL`과 같은 태도다 — 계획이 사라진다고 위험 기록까지
    사라지면 안 된다.
  - **보관**: 아무것도 하지 않는다. 보관은 "접어둔다"이지 "없던 일"이 아니고, 복구하면 연결도 함께
    돌아와야 한다.
  - RAID 항목 자체를 지우면 그 연결도 함께 사라진다(`raid_item_id`는 CASCADE).

### 2.5 내보내기·가져오기 (formatVersion 6)

- `wbs_items`의 실적·예상 종료가 함께 나간다. 저장된 상태이므로 판정값 제외 원칙에 걸리지 않는다.
- RAID는 `links`로 나가고 `wbsItemId`는 **항상 비운다** — 둘 다 쓰면 서로 어긋날 수 있다.
- **가져오기는 두 모양을 다 읽는다.** formatVersion 5 이하의 단일 `wbsItemId`는 `WBS_ITEM` 링크 하나가
  된다(V21이 DB에 한 것과 같다). 6 이상이면 `links`를 쓴다.
- **링크 삽입은 맨 마지막**이다. Sprint·Backlog를 가리킬 수 있어 모든 대상의 새 id가 나온 뒤여야 한다.
  파일에 없는 대상을 가리키면 거부한다 — FK가 없어 아무도 잡아 주지 않고, 등록부에 빈 줄로 남는다.

## 3. 검증

### 3.1 자동 테스트

```
cd backend && ./gradlew test          # 197건 통과 (Step 5 대비 +22)
cd frontend && npm test -- --run      # 95건 통과
cd frontend && npm run build          # vue-tsc + vite 빌드 성공
```

신규: `RaciInheritanceTest` 8건, `RaidServiceTest` 10건, `RaciValidatorTest` 상속 3건,
`ImportServiceTest` 링크 2건.

### 3.2 실 DB·실 서버 시나리오 (34항목)

빈 H2 파일에 V1~V21을 처음부터 적용하고(마이그레이션 전체가 새 DB에서 도는 것을 함께 확인) 실제 API로
지시서의 완료 기준을 밟았다. 스크립트는 `scratchpad/verify6.py`, 결과 **34/34 통과**.

| 완료 기준 | 확인한 것 |
|---|---|
| Gantt → WBS → Backlog → Sprint 이동 | 간트의 초과 경고가 `/wbs?focus=`로, Backlog 행이 Work Package로, Board가 Sprint로 — 모두 같은 프로젝트 안에서 |
| 기준 일정이 현재 계획 수정으로 덮어써지지 않음 | 종료일을 1/20 → 3/1로 바꿔도 `baselineEnd`는 1/20 그대로 |
| 기준선 미등록 처리 | `hasBaseline=false`, 행의 baseline 필드 `null`, 초과 판정 안 함 |
| Sprint 중복 표시가 집계 중복을 만들지 않음 | 레인 1개, 업무 행의 일정·실적 불변, 진척은 공통 집계값 그대로 |
| RACI 상속·재정의 | 상위 A가 하위 셀에 상속으로, 하위 지정 시 재정의로, 누락 보고 사라짐 |
| 담당자 변경 분리 | Story 담당자 지정 후에도 그 사람의 RACI 글자는 A 하나 그대로 |
| RAID 복수 연결 | 한 항목에 WBS·Sprint·Backlog 세 개, 각각 이름·코드 해석됨 |
| 잘못된 프로젝트 연결 차단 | 다른 프로젝트의 WBS id → 400 |
| 중복 연결 차단 | 같은 대상 두 번 → 400 |
| 대상 삭제 시 참조 정책 | Sprint 삭제 후 그 링크만 사라지고 항목과 나머지 연결은 그대로 |
| 왕복 | formatVersion 6 내보내기 → 가져오기, 연결 3개 유지·id 전부 재매핑 |
| 구형 파일 | formatVersion 5의 `wbsItemId` → `WBS_ITEM` 링크 |

### 3.3 실행하지 않은 검증

- **브라우저에서의 시각 확인.** 간트의 세 막대와 Sprint 레인, RACI의 상속 표시는 타입 검사와 빌드까지만
  확인했고 실제 렌더링은 보지 않았다. 값은 API 레벨에서 34항목으로 확인했다.
- **PostgreSQL(`server` 프로필)에서의 V20·V21.** H2로만 돌렸다. 두 마이그레이션은 표준 SQL만 쓰지만
  (`ALTER TABLE ADD COLUMN`, `CREATE TABLE`, `INSERT ... SELECT`, `DROP CONSTRAINT`/`DROP INDEX`),
  `DROP INDEX idx_raid_wbs_item`은 PostgreSQL에서 `ALTER TABLE` 없이도 동작해야 하며 실측하지 않았다.

## 4. 결정과 근거

| 결정 | 대안 | 왜 이쪽인가 |
|---|---|---|
| 기준선 없으면 `hasBaseline: false` | 현재 계획을 기준선으로 | 기준선이 계획을 따라다니면 초과가 영원히 0이다. 지시서 명시 |
| Sprint를 레인 하나로 | Work Package마다 반복 | 지시서 명시. 반복하면 없는 일정·진척을 만든다 |
| 실적 날짜를 손으로 입력 | Sprint 종료일 복사 | 지시서 명시. 두 사실이 다르고 Sprint는 여러 WP에 걸친다 |
| 상속을 저장하지 않고 계산 | 하위에 복사본 저장 | 이동·재부모화가 즉시 낡은 값을 만든다. WBS 코드와 같은 이유 |
| 상속을 역할별로 | 자기 글자 하나면 전체 재정의 | R을 적는 순간 상위 A가 사라지는 것은 매트릭스의 목적에 반한다 |
| 누락 판정에 상속 반영 | 예전처럼 자기 글자만 | 반영하지 않으면 이미 답한 것을 다시 입력하라고 요구한다. 의도한 동작 변경이며 보고서에 명시 |
| 책임자 중복은 선언한 행에 | 물려받은 leaf마다 | 정리할 곳은 한 곳이다. leaf 수만큼 불어나면 목록이 못 쓰게 된다 |
| Story 담당자를 행 머리 주석으로 | 매트릭스 열로 | 열이 되면 RACI 글자와 나란히 서서 역할처럼 읽힌다 |
| 링크 표(`target_type` + `target_id`) | nullable FK 3개 | 종류가 늘어난다(Step 4에서 Sprint가 생겼다). 셋 중 하나만 찬 행은 타입 코드가 말하지 않는 것을 말하지 못한다 |
| 삭제 시 링크만 끊기 | 항목까지 삭제 / 그냥 두기 | 그냥 두면 등록부에 빈 줄이 남는다(FK가 없어 아무도 안 지운다). 항목까지 지우면 기록이 사라진다 |
| Board의 관련 RAID를 클라이언트에서 매칭 | Sprint 응답에 실어 보내기 | 등록부는 이미 한 번에 오는 payload이고, 두 화면이 `useRaid` 하나를 공유해 요청이 늘지 않는다 |

## 5. 기존 RAID 분류 매핑 (지시서 요구)

바꾸지 않았다. 아래는 확인 결과다.

| 저장 값 | 화면 라벨 | 뜻 (`shared/raid.ts`) | 설계 문서와의 관계 |
|---|---|---|---|
| `RISK` | 위험 | 아직 일어나지 않았지만 일어나면 곤란한 일 | 그대로 |
| `ASSUMPTION` | 가정 | 참이라고 보고 계획한 것. 틀리면 위험이 된다 | 그대로 |
| `ISSUE` | 이슈 | 이미 일어나서 대응이 필요한 일 | 그대로. Board 차단 카드가 이 종류를 주로 보여준다 |
| `DEPENDENCY` | 의존성 | 프로젝트 밖에서 받아야 하는 것 | 그대로. **WBS 선후행 관계와 다르다** — 링크는 연관이지 일정 제약이 아니다 |

상태(`OPEN`/`IN_PROGRESS`/`CLOSED`)와 노출도(확률 × 영향) 규칙도 그대로다.

## 6. Step 7에서 쓸 조회 기능

대시보드가 새로 계산할 것은 없다. 아래를 읽어 모으면 된다.

| 필요 | 어디서 | 비고 |
|---|---|---|
| 전체·단계별 진척, 계획 대비 차이 | `GET /projects/{id}/progress` | Step 5. `basis`와 `incomplete*`를 함께 봐야 산정 전을 0%로 눕히지 않는다 |
| 지연·임계 경로·기준 초과 | `GET /projects/{id}/gantt` | 행마다 `delayStatus`, `criticalPath`, `baselineExceeded`, `baselineSlipDays`, `acceptancePending` |
| 기준선 등록 여부 | 같은 응답의 `hasBaseline`, `baselineVersion` | 미등록이면 대시보드도 "기준 대비"를 말하면 안 된다 |
| Sprint 진행·완료 | `GET /projects/{id}/sprints` 또는 간트의 `sprints` 레인 | 레인 쪽이 가볍다(`plannedItems`/`doneItems`만) |
| 책임 공백 | `GET /projects/{id}/raci`의 `issues` | 상속 반영된 값이다. 다시 세지 말 것 |
| 위험·이슈 | `GET /projects/{id}/raid` | `exposureLevel`, `overdue`, `links`. 기준일은 응답의 `referenceDate` |
| 차단된 실행 항목 | `GET /projects/{id}/sprints`의 `items[].blocked` | 관련 RAID는 링크로 맞춘다 |

**모든 판정의 기준일은 서버가 정해 응답에 싣는다.** 대시보드가 자기 시계를 쓰면 카드마다 다른 "오늘"이
된다.

## 7. 남은 문제

- **`raid_links.target_id`에 FK가 없다.** 애플리케이션이 유일한 방어선이고, 새로운 삭제 경로를 만들면
  `detachTargets`를 함께 불러야 한다. 잊으면 등록부에 이름 없는 줄이 남는다(화면은 "(없음)"으로 그려
  숨기지는 않는다).
- **실적 날짜를 넣는 화면이 WBS 편집 폼뿐이다.** Board에서 완료로 옮길 때 실제 완료일을 묻는 것이
  자연스럽지만, 지시서가 자동 복사를 금지했고 "묻는 것"은 Step 6 범위 밖이라 넣지 않았다.
- **기준선은 프로젝트당 최신 하나만 본다.** 여러 버전을 나란히 비교하는 화면은 없다(Step 5와 같은 상태).
- **RACI 상속에 예외를 둘 수 없다.** "이 Work Package만 단계의 A를 따르지 않음"을 표현하려면 그 행에
  다른 사람을 지정해야 한다. 빈 재정의(=아무도 아님)는 표현할 수 없다.
