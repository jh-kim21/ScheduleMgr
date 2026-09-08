# Step 01. 기존 구조 분석 및 구현 계획 (결과 보고서)

| 구분 | 내용 |
|---|---|
| 대상 저장소 | `D:\Git\ScheduleMgr` (branch `main`, 기준 커밋 `aeb7459`) |
| 참고 설계 | [WBS_Agile_Hybrid_PM_Design.md](../WBS_Agile_Hybrid_PM_Design.md) |
| 지시서 | [Hybrid_PM_Step01_Analysis.md](../Hybrid_PM_Step01_Analysis.md) |
| 작성일 | 2026-09-07 |
| 이번 단계 성격 | **분석 전용. 애플리케이션 코드와 DB를 변경하지 않았다.** |

## 0. 보고서 위치에 대한 메모

지시서의 산출물 이름(`Hybrid_PM_Step01_Analysis.md`)이 지시서 파일 이름과 같아 같은 폴더에 두면
지시서를 덮어쓴다. 저장소에는 이미 `설계서/` 문서 규칙이 있으므로 `docs/hybrid-pm/`를 새로 만들지 않고
`설계서/agile_design/results/` 아래에 산출물 이름을 그대로 두었다. Step 2~7의 `*_Result.md`도 같은
폴더를 쓰면 지시서와 결과가 분리된다.

## 1. 요약

- 현재 구현은 **프로젝트 · WBS · 간트 · RACI · RAID + 내보내기/가져오기**가 end-to-end로 동작하며,
  레이어드 아키텍처(domain → application → infrastructure/presentation)와 "파생 값은 저장하지 않는다"는
  원칙이 일관되게 지켜지고 있다. 재사용 가치가 높다.
- 하이브리드 설계가 요구하는 것 중 **실행 계층(Backlog·Sprint·Board)과 기준 정보 계층(가중치·
  체크포인트·Baseline·변경 이력·스냅샷)은 스키마에 전혀 없다.** 7개 테이블 중 Agile 관련은 0개다.
- **최하위 작업(Work Package) 구분은 신규 추가 대상이다.** 현재 leaf/summary는 저장된 구분이 아니라
  "자식이 있는가"로 조회 시점에 결정된다(`WbsNode.summary()`). 통합의 연결 축이 Work Package이므로
  Step 2는 **실행 방식 컬럼과 함께 `wbs_items.node_type`을 신규로 추가**한다. 기존 기능의 변경이
  아니라 추가이며, 기존 행은 현재 트리 모양대로 백필하면 화면 동작이 그대로 유지된다(§6.1).
- Step 5의 선행 조건 네 가지(**가중치 · 완료 기준 · Baseline · 변경 이력**)는 **모두 미구현**이다.
  Step 5는 계산 구현 전에 5-A에서 기준 정보 스키마를 먼저 세워야 하며, 이 부분이 Step 5 내부에서
  가장 큰 덩어리다.
- 설계의 상위 집계식(직계 자식 **가중치** 가중 평균)은 현재 구현(하위 **leaf 개수** 가중 평균)과
  결과가 다르다. Step 5에서 교체하면 **기존 프로젝트의 화면 숫자가 변한다.** 전환 정책을 미리 정해야 한다.
- RAID 분류가 설계 예시(Risk/Action/Issue/Decision)와 실제 구현(Risk/**Assumption**/Issue/**Dependency**)이
  다르다. 설계 §9가 이미 "기존 분류를 유지·매핑한다"고 지시하므로 **기존 분류를 유지**하고 Step 6-C에서
  매핑만 정의한다.

## 2. 조사 범위와 실행·검증 결과

### 2.1 확인 방법

스키마(Flyway 마이그레이션 7개), 도메인·애플리케이션·프레젠테이션 계층 전체 소스 목록, WBS·간트·
RACI·RAID·내보내기/가져오기의 주요 클래스와 프론트엔드 feature/store/api 파일을 직접 읽어 확인했다.
운영 데이터는 조사하지 않았다(지시서 4항: 스키마와 개발용 예시로 분석).

### 2.2 실행한 검증

이번 세션에서 개발 환경을 새로 구성한 뒤 저장소의 기존 검증 명령을 그대로 실행했다.

| 명령 | 결과 |
|---|---|
| `cd backend && ./gradlew build` | **BUILD SUCCESSFUL**. 테스트 **85건, 실패 0, 오류 0** (테스트 클래스 17개) |
| `cd frontend && npm ci` | 77개 패키지, 취약점 0 |
| `cd frontend && npm run build` | `vue-tsc -b` 타입체크 통과 + vite 빌드 성공 (102 모듈) |
| `cd frontend && npm test` | **5개 파일, 65건 전부 통과** (vitest) |

참고 환경: JDK 21(Temurin 21.0.12.1, Gradle toolchain이 Windows 레지스트리에서 자동 인식),
Node v24.19.0 / npm 11.17.0, Gradle 9.7.1(wrapper). `JAVA_HOME`은 설정하지 않았고 필요하지도 않았다.

### 2.3 실행하지 않은 검증과 이유

| 미실행 항목 | 이유 |
|---|---|
| `./gradlew bootRun` 후 실제 화면 조작 | 이번 단계는 분석 전용이고, 기동 시 `desktop` 프로필이 `~/.project-flow/data`의 H2 파일을 연다. 코드 확인으로 목적을 달성할 수 있어 생략했다. |
| Docker 이미지 빌드 | 이 장비에 Docker가 없다(미설치). 요구사항 3.5·10.5는 여전히 미검증 상태다. |
| jpackage 설치본 생성 | 분석 범위 밖이고 WiX 등 추가 의존이 필요하다. |
| 운영 데이터 기반 마이그레이션 리허설 | 이 장비의 H2 파일은 위 테스트 실행으로 오늘 새로 생성되어 실질 데이터가 없다. §9에 미확인 사항으로 남긴다. |

## 3. 현재 구조

### 3.1 기술 스택 · 실행 · 검증

| 항목 | 내용 |
|---|---|
| 백엔드 | Spring Boot 3.5.16 / Java 21 toolchain / Spring Data JPA / Flyway, Gradle 9.7.1 |
| DB | `desktop` 프로필 = H2 파일(`~/.project-flow/data`), `server` 프로필 = PostgreSQL(환경변수) |
| 프론트엔드 | Vue 3 + TypeScript + Vite 8, vue-router, vitest |
| 스키마 관리 | Flyway (`backend/src/main/resources/db/migration/V1~V7`) |
| **JPA DDL 정책** | **`ddl-auto: validate`** (`application.yml`). 엔티티에 필드를 추가하면 **대응 마이그레이션 없이는 기동 자체가 실패**한다. Step 2에서 코드와 마이그레이션은 한 커밋에서 함께 움직여야 한다. |
| 인증·권한 | **없음.** `spring-security` 의존성이 없고 사용자·로그인 개념이 없다. `project_members`는 계정이 아니라 프로젝트 스코프의 이름표다. |
| 프로젝트 격리 | 요청마다 경로변수 `projectId` + 서비스의 `requireProject()`/`requireItemOfProject()`로 강제. 조회는 전부 `findByProjectId`. 교차 프로젝트 참조 검증 패턴이 이미 있다(`RaidService.requireWbsItemOfProject`, `requireOwnerOfProject`) — **Step 3의 Backlog→WBS 귀속 검증에 그대로 재사용할 수 있다.** |

### 3.2 저장 스키마 (전체)

| 마이그레이션 | 테이블 | 주요 컬럼 | 삭제 정책 |
|---|---|---|---|
| V1 | `projects` | name, description, status, start_date, end_date, created_at, updated_at | — |
| V2 | `wbs_items` | project_id, parent_id, name, description, start_date, end_date, **progress(0~100)**, sort_order | project CASCADE, parent **CASCADE**(하위 동반 삭제) |
| V3 | `wbs_dependencies` | predecessor_id, successor_id, **lag_days** (관계 종류 컬럼 없음 = FS 고정), UNIQUE(pred, succ) | CASCADE |
| V4 | `project_members` | name, email, position, UNIQUE(project_id, name) | CASCADE |
| V5 | `raci_assignments` | wbs_item_id, member_id, raci_role, UNIQUE(wbs_item_id, member_id, raci_role) | CASCADE |
| V6 | `raid_items` | raid_type, title, description, status, probability, impact, owner_member_id, due_date, response | owner **SET NULL** |
| V7 | `raid_items.wbs_item_id` 추가 | (선택적 WBS 연결) | **SET NULL** |

Enum: `ProjectStatus`(PLANNED/IN_PROGRESS/ON_HOLD/COMPLETED), `RaciRole`(R/A/C/I),
`RaidType`(**RISK/ASSUMPTION/ISSUE/DEPENDENCY**), `RaidStatus`(OPEN/IN_PROGRESS/CLOSED),
`RaidLevel`(LOW/MEDIUM/HIGH), `DelayStatus`(6종).

### 3.3 화면에서 저장까지의 연결 (실제 파일 경로)

공통 경로: 화면 → feature 컴포저블(모듈 스코프 상태) → `src/api/*.ts` → `src/api/http.ts` →
컨트롤러 → 서비스 → 도메인 리포지토리 **포트** → `infrastructure/persistence/*Adapter` → Spring Data JPA → 테이블.

| 화면 | 프론트엔드 | 백엔드 | 테이블 |
|---|---|---|---|
| 프로젝트 | `views/ProjectsView.vue` → `features/projects/{ProjectList,ProjectForm}.vue`, `useProjects.ts`, `statusLabels.ts` → `api/projectApi.ts` | `presentation/ProjectController` → `application/ProjectService` → `domain/ProjectRepository` → `…/ProjectRepositoryAdapter` | `projects` |
| WBS | `views/WbsView.vue` → `features/wbs/{WbsTree,WbsForm}.vue`, `useWbs.ts`, `wbsTree.ts` → `api/wbsApi.ts` | `WbsController` → `WbsService` → **`domain/WbsTreeAssembler`(코드·Summary 일정·진행률 파생)** → `WbsItemRepository` | `wbs_items` |
| 간트 차트 | `views/GanttView.vue` → `features/gantt/{GanttChart,DependencyEditor}.vue`, `useGantt.ts`, `ganttScale.ts` → `api/ganttApi.ts` | `GanttController` → `GanttService` → `ScheduleCalculator`·`DelayCalculator`·`CriticalPathCalculator`·`DependencyGraph` → `WbsDependencyRepository` | `wbs_dependencies` (+`wbs_items`) |
| RACI | `views/RaciView.vue` → `features/raci/{RaciMatrix,MemberEditor}.vue`, `useRaci.ts`, `shared/raci.ts` → `api/{raciApi,memberApi}.ts` | `RaciController` / `ProjectMemberController` → `RaciService` / `ProjectMemberService` → `domain/RaciValidator` | `raci_assignments`, `project_members` |
| RAID | `views/RaidView.vue` → `features/raid/{RaidList,RaidForm}.vue`, `useRaid.ts`, `raidFilter.ts`, `shared/raid.ts` → `api/raidApi.ts` | `RaidController` → `RaidService` → `domain/RaidAssessor` | `raid_items` |
| 내보내기 (JSON) | `features/export/ExportMenu.vue` (평범한 링크, `Content-Disposition: attachment`) | `ExportController` → `ExportService` → `dto/ProjectExportResponse` | 전체 읽기 |
| 내보내기 (CSV) | `shared/exportRows.ts` + `shared/csv.ts` — **클라이언트에서 생성**(UTF-8 BOM 포함) | (기존 조회 API 재사용) | — |
| 가져오기 | `ExportMenu.vue` (파일 선택) | `ImportController` → `ImportService` (**항상 새 프로젝트 생성 + 전체 id 재매핑**) | 전체 쓰기 |

공유 상태·캐시: `stores/projectSelection.ts`(선택 프로젝트), `stores/scheduleCache.ts`
(캐시 키 = `프로젝트 : wbsRevision : 로컬날짜`, `markWbsChanged()`로 무효화), `stores/theme.ts`.

### 3.4 재사용해야 하는 기존 설계 원칙 (확인됨)

Step 2 이후가 이 원칙들을 깨지 않아야 한다. 모두 코드와 `CLAUDE.md`에서 확인했다.

1. **파생 값은 저장하지 않는다.** WBS 코드·Summary 일정·Summary 진행률·레벨은 조회 시점 계산
   (`WbsTreeAssembler`). 설계 §11.3-8("저장된 집계값은 재생성 가능한 캐시")과 방향이 같다.
2. **모든 변경 API가 갱신된 전체 데이터를 반환**한다(WBS 트리, RACI 매트릭스, RAID 로그).
3. **기준일은 서버가 정해 `referenceDate`로 함께 반환**한다. 클라이언트 시계를 쓰지 않는다.
4. **만족 불가능한 구조는 거부(400), 서로 안 맞는 계획은 표시**한다. 순환 의존성·상하위 간 선후행은
   거부하고, 일정 위반·RACI 위반·지연은 배지로 표시한다.
5. **부모/프로젝트 참조는 JPA 연관이 아니라 단순 id 컬럼**이다(N+1 회피).
6. 색은 전부 `frontend/src/style.css`의 CSS 변수로만 정의한다(다크 모드).

## 4. 전체 설계와의 차이

### 4.1 데이터 모델 (설계 §11.1 기준)

| 설계 엔터티 | 현재 상태 | 비고 |
|---|---|---|
| Project | **있음** (`projects`) | 설계 필드와 일치 |
| WbsItem | **부분** (`wbs_items`) | 있음: id, projectId, parentId, name, startDate, endDate, progress, sortOrder / **없음: nodeType, executionMode, ownerId, weight, forecastEnd, actualStart, actualEnd, acceptanceStatus** (code는 파생으로 존재) |
| Team | **없음** | Step 4 선행. 로그인·사용자 개념 자체가 없음 |
| Baseline / BaselineItem | **없음** | Step 5-A, Step 6-A 선행 |
| ProgressPolicy | **없음** | Hybrid α, 정책 버전 저장처 없음 |
| BacklogItem | **없음** | Step 3 |
| Sprint / SprintItem | **없음** | Step 4 |
| AcceptanceCheckpoint | **없음** | Waterfall/Hybrid 진척의 분모 자체가 없음 |
| RaciAssignment | **있음** (`raci_assignments`) | 설계의 **상속·재정의 표시**는 없음 (Step 6-B) |
| RaidItem | **부분** (`raid_items`) | severity 없음(impact로 대체). **유형 체계가 설계 예시와 다름** |
| RaidLink | **없음** | 현재 `raid_items.wbs_item_id` 단일 컬럼 = WBS와 **1:1**. 설계는 WBS·Sprint·Backlog **복수 연결** (Step 6-C) |
| Dependency | **부분** (`wbs_dependencies`) | **relationType 없음 = FS 고정**, lag 있음 |
| ProgressSnapshot | **없음** | Step 5-C, Step 7 |
| ChangeLog | **없음** | `created_at`/`updated_at`만 존재. 요구사항 8.2(진행률 변경 이력)도 미구현 |

### 4.2 기능·정책 차이

| 영역 | 설계 | 현재 | 영향 단계 |
|---|---|---|---|
| 메뉴 | 프로젝트·WBS·**Agile**·간트·RACI·RAID·**Dashboard** | 프로젝트·WBS·간트·RACI·RAID (`router/index.ts` 5개 라우트) | 3, 4, 7 |
| 최하위 작업(Work Package) 구분 | `nodeType`(SUMMARY/WORK_PACKAGE/MILESTONE) 저장 | **저장 구분 없음.** `WbsNode.summary()`가 자식 유무로 판정 → **Step 2 신규 추가** | **2** |
| 실행 방식 | Work Package별 Waterfall/Agile/Hybrid | 없음 | **2** |
| 진행률 입력 | Story·Bug의 Done 여부, 체크포인트 승인 | **leaf에 사람이 0~100 직접 입력** (`wbs_items.progress`) | 5 |
| 상위 집계 | Σ(직계 자식 **가중치** × 진척) / Σ가중치 | **하위 leaf 개수 가중 평균** (`WbsTreeAssembler.assembleNode`) | **5 (숫자 변경)** |
| 가중치 | WbsItem.weight, BacklogItem.progressWeight (별개 값) | 없음 | 5 |
| 산정 전 상태 | 분모 0이면 `산정 전` 표시·전파 | 없음. leaf가 없으면 `progress = 0`으로 단정 | 5 |
| 인수 상태 | 실행 진척과 `acceptanceStatus` 분리 | 없음. 진행률 100%가 곧 완료 | 5 |
| Baseline | 승인 기준 버전 별도 저장 | 없음. `start_date`/`end_date` 한 벌뿐 | 5, 6 |
| 실적 | actualStart / actualEnd / forecastEnd | 없음. **"실제 실적" 표시가 현재 데이터로 불가능** | 6-A |
| 마일스톤 | `nodeType = MILESTONE`, 가중치 없음 | 없음 | 2(구조), 6-A |
| RAID 유형 | Risk / **Action** / Issue / **Decision** | Risk / **Assumption** / Issue / **Dependency** | 6-C (매핑) |
| RAID 연결 | 복수 대상(WBS·Sprint·Backlog) | WBS 1개 (선택) | 6-C |
| 권한 | approvedBy·changedBy·권한 체계 전제 | **사용자·로그인 없음** | 5, 6-B, 7 |
| 변경 이력 | ChangeLog, 스냅샷 | 없음 | 2(최소), 5 |

### 4.3 확인된 사실과 미확인 사항의 구분

위 §3~§4.2는 **모두 코드·스키마에서 직접 확인한 사실**이다. 미확인 사항은 §9에 모았다.

## 5. 7단계 의존 관계

```text
Step 1 (분석) ─▶ Step 2 (Work Package 식별 + Execution Mode)
                        │
                        ▼
                 Step 3 (Backlog ─ Work Package 귀속)
                        │
                        ▼
                 Step 4 (Sprint · Board · 이월/재오픈)
                        │
                        ▼
                 Step 5 (5-A 기준정보 → 5-B 공통집계 → 5-C 화면·스냅샷)
                        │
                        ▼
                 Step 6 (6-A 간트 → 6-B RACI → 6-C RAID)
                        │
                        ▼
                 Step 7 (Dashboard + 통합 검증)
```

| Step | 선행 조건 | 이 저장소에서의 실제 충족 여부 |
|---|---|---|
| 2 | Step 1 분석 | 충족 (이 문서) |
| 3 | Work Package 식별 + Execution Mode 저장·조회 | Step 2가 만들어야 함 |
| 4 | Backlog와 귀속 검증 | Step 3 필요. 추가로 **Team 모델이 없어** Step 4에서 "기본 팀" 도입 여부를 결정해야 한다 |
| 5 | Step 4 완료 + Baseline 분석 | **선행 4종(가중치·완료 기준·Baseline·변경 이력) 전부 미구현** → §7 참조 |
| 6 | 공통 진척 조회 + 기준·현재 일정 구분 | Step 5-A(Baseline)와 **`actual*`/`forecastEnd` 컬럼**이 없으면 6-A의 3막대 구분이 불가능 |
| 7 | 전 영역 연결 | Step 6 필요 |

체인이 직렬이라 **Step 2가 추가하는 Work Package 구분이 이후 6단계 전체의 연결 축**이 된다.

## 6. Step 2 변경안

### 6.1 최하위 작업(Work Package) 구분 — 신규 추가

이것은 기존 기능의 변경이 아니라 **WBS와 Agile을 잇기 위해 새로 추가하는 구조**다. 설계 §1이 두 계층의
연결 축을 Work Package로 정의하므로, "어느 항목이 Work Package인가"가 저장되어 있지 않으면 Step 3의
Backlog 귀속과 Step 5의 가중치 분모가 성립하지 않는다.

현재는 저장된 구분이 없고 "자식이 있으면 Summary"다. 이대로 실행 방식을 leaf에만 붙이면
**leaf에 자식을 하나 추가하는 순간 그 항목이 Summary로 바뀌어 실행 방식·(이후) 가중치·Backlog 귀속이
말없이 고아가 된다.** Step 2 지시서 2항이 "구분이 없으면 최소 구조를 추가하고 자식 추가 시 규칙도
정한다"고 요구하는 지점이다.

**Step 2에서 `wbs_items.node_type` 컬럼을 추가한다** (`SUMMARY` / `WORK_PACKAGE`. `MILESTONE`은 Step 6까지 보류).

- 근거 1: 설계 §11.1이 `nodeType`을 전제하고 Milestone까지 같은 컬럼으로 표현한다.
- 근거 2: Step 3의 "Summary 연결 차단" 검증이 **저장된 사실**을 필요로 한다. 자식 유무로 판정하면
  검증 결과가 다른 행의 편집으로 바뀐다.
- 근거 3: 파생으로 두면 Step 5에서 가중치의 분모가 트리 편집만으로 변한다.
- **자식 추가 규칙(권고):** `WORK_PACKAGE`에 자식을 추가하려면 명시적으로 `SUMMARY`로 전환해야 하며,
  전환 시 실행 방식·연결 정보는 삭제하지 않고 보관하고 화면에 정리 대상으로 알린다
  (설계 §5 "실행 방식 변경 시 기존 실적과 연결 정보를 삭제하지 않는다"와 같은 태도).
- 기존 레코드 값: **자식이 있으면 `SUMMARY`, 없으면 `WORK_PACKAGE`** 로 채운다. 현재 화면 동작과
  1:1로 일치하므로 이 마이그레이션만으로는 보이는 것이 달라지지 않는다.

### 6.2 실행 방식 기본값 — 권고안

**권고: 컬럼은 nullable, DB 기본값을 두지 않고 `NULL` = `미지정`으로 표시한다.**

- `WATERFALL`을 기본값으로 하면 안 된다. 설계 §6.3의 Waterfall 진척은 **승인 체크포인트 가중치 비율**인데
  기존 데이터에는 체크포인트가 없어, Step 5에서 기존 프로젝트 전부가 0% 또는 `산정 전`이 된다.
  "기존 동작이 유지되는 기본값"(지시서 4항)이 아니다.
- `AGILE`도 같은 이유로 부적절하다(Backlog가 없어 분모 0).
- `NULL`(미지정)은 **"수동 진행률 입력을 계속 쓴다"** 는 현재 동작을 정확히 보존하고, 실행 방식 선택을
  사용자의 명시적 행위로 남긴다. Step 5는 `미지정`을 "수동 진척 유지"로 처리하면 된다(§7 전환 정책).
- 화면 표시는 `미지정`. Summary 행은 자식 실행 방식의 요약(예: `Agile 3 · 미지정 1`)을 읽기 전용으로 표시.

### 6.3 수정 대상 파일 (구체 목록)

**백엔드 — 신규**

| 파일 | 내용 |
|---|---|
| `backend/src/main/resources/db/migration/V8__add_wbs_node_type_and_execution_mode.sql` | `node_type`(백필 후 NOT NULL), `execution_mode`(NULL 허용) |
| `backend/src/main/resources/db/migration/V9__create_wbs_execution_mode_history.sql` | 실행 방식 변경 이력 (지시서 7항) |
| `backend/src/main/java/com/projectflow/domain/WbsNodeType.java` | enum SUMMARY / WORK_PACKAGE |
| `backend/src/main/java/com/projectflow/domain/ExecutionMode.java` | enum WATERFALL / AGILE / HYBRID |
| `domain/WbsExecutionModeChange.java` (+ Repository 포트, JpaRepository, Adapter) | 이력 엔티티. 기존 포트/어댑터 패턴을 따른다 |

**백엔드 — 수정**

| 파일 | 변경 |
|---|---|
| `domain/WbsItem.java` | `nodeType`, `executionMode` 필드 + `update(...)` 시그니처 |
| `domain/WbsNode.java` | Summary의 자식 실행 방식 요약 필드 |
| `domain/WbsTreeAssembler.java` | 요약값 계산 (**진행률 집계 로직은 건드리지 않는다** — Step 5 범위) |
| `application/WbsService.java` | Summary에 실행 방식 설정 거부, `WORK_PACKAGE`에 자식 추가 시 전환 규칙, 이력 기록 |
| `application/dto/WbsItemCreateRequest.java`, `WbsItemUpdateRequest.java`, `WbsNodeResponse.java` | 필드 추가 |
| `application/dto/ProjectExportResponse.java` | `ExportedWbsItem`에 두 필드 추가 + **`formatVersion` 1 → 2** |
| `application/ExportService.java` | 새 필드 채우기 |
| `application/ImportService.java` | **`SUPPORTED_FORMAT_VERSION` 2로 올리고, 값이 없는 구형 파일은 §6.1·§6.2 규칙으로 보정**(nodeType은 자식 유무로, executionMode는 NULL) |

**프론트엔드 — 수정/신규**

| 파일 | 변경 |
|---|---|
| `frontend/src/api/wbsApi.ts` | `WbsNode`, `WbsItemInput`에 필드 추가 |
| `frontend/src/shared/executionMode.ts` (신규) | 라벨 맵. `shared/delay.ts`·`features/projects/statusLabels.ts`와 같은 패턴 |
| `frontend/src/features/wbs/WbsForm.vue` | 실행 방식 `<select>` (Summary일 때 비활성 — 기존 `rolledUp` 처리와 같은 방식) |
| `frontend/src/features/wbs/WbsTree.vue` | `실행 방식` 열 추가 (현재 헤더: WBS·업무명·시작일·종료일·진행률) |
| `frontend/src/shared/exportRows.ts` | WBS CSV에 열 추가 |
| `frontend/src/style.css` | 새 색이 필요하면 **여기에만** 토큰으로 정의 |

**테스트**

`domain/WbsTreeAssemblerTest`(요약 계산), `application/ImportServiceTest`(구형·신형 파일 왕복 — 기존
in-memory fake 패턴 재사용), 신규 `WbsService` 검증 테스트(Summary 거부, 전환 규칙).

### 6.4 마이그레이션과 롤백

- **Flyway Community에는 `undo`가 없다.** 롤백은 (a) 되돌리는 정방향 마이그레이션(`V10__…`) 또는
  (b) H2 파일 백업 복원뿐이다. Step 2에서는 (b)를 기본으로 하고 (a)의 SQL을 보고서에 적어 둔다.
- `ddl-auto: validate`이므로 **스키마만 되돌리면 기동이 실패한다.** 코드와 스키마를 함께 되돌려야 한다.
- 개발용 백업 절차: 앱 종료 후 `~/.project-flow/data` 폴더 복사. `AUTO_SERVER=TRUE`라 실행 중 복사는
  일관성이 보장되지 않는다.
- H2와 PostgreSQL 양쪽에서 동작하는 SQL만 쓴다(기존 마이그레이션이 지키는 규칙).

### 6.5 Step 2 검증 항목

1. `./gradlew build`(현재 85건 통과 유지), `npm run build`, `npm test`(65건 유지).
2. **마이그레이션 전 데이터 보존:** 개발용 DB에 Summary/leaf가 섞인 트리를 만든 뒤 V8/V9 적용 →
   전 행 조회·수정·재조회, WBS 코드·Summary 일정·진행률이 **이전과 동일**함을 확인.
3. Summary 행에 실행 방식 설정 시 400. 잘못된 enum 값은 이미 `HttpMessageNotReadableException` 핸들러가
   400 + 메시지로 처리하므로 그 경로가 유지되는지 확인.
4. `WORK_PACKAGE`에 자식 추가 시 정한 규칙대로 동작하고, 전환 시 실행 방식이 보관됨.
5. **내보내기·가져오기 왕복:** ① `formatVersion: 1` 구형 파일(필드 없음) → 기본값 보정 후 정상 생성,
   ② 신형 내보내기 → 가져오기 → 값 보존, ③ `formatVersion: 3` 파일 → 기존처럼 거부.
6. 드래그&드롭 이동·삭제 후에도 코드·집계가 기존과 동일.
7. 실행 방식 변경 시 이력 1건 적재.

### 6.6 Step 3 연결 지점

Step 3은 `node_type = WORK_PACKAGE AND execution_mode IN (AGILE, HYBRID)` 를 Backlog 귀속 대상 조건으로
쓰고, 교차 프로젝트 검증은 `RaidService.requireWbsItemOfProject` 패턴을 재사용한다.

## 7. Step 5 선행 조건 현황 (지시서 7항)

| 선행 요소 | 현재 지원 | 확인 근거 |
|---|---|---|
| **가중치** | **없음** | `wbs_items`에 weight 컬럼 없음. 상위 집계는 leaf 개수 가중 평균 |
| **완료 기준** | **없음** | acceptanceCriteria·acceptanceStatus·체크포인트 테이블 없음. 진행률 100%가 곧 완료 |
| **Baseline** | **없음** | Baseline 테이블 없음. `start_date`/`end_date` 한 벌만 있어 수정하면 원래 계획이 사라진다 |
| **변경 이력** | **없음** | `created_at`/`updated_at`만. 요구사항 8.2도 미구현. 스냅샷 없음 |

**Step 5 내부에서 순차 처리할 최소 범위 제안** (5-A에서, 이 순서로):

1. `wbs_items.weight`(nullable, 음수 금지) + Backlog의 `progress_weight`. **가중치 미입력 = `산정 전`**
   으로 전파하고 0으로 단정하지 않는다.
2. `acceptance_checkpoints`(wbs_item_id, title, weight, status, approved_at) — Waterfall·Hybrid 분모.
   승인자 컬럼은 로그인이 없으므로 **자유 입력 문자열**로 두고 계정 도입 시 승격한다.
3. `progress_policies`(wbs_item_id, method, agile_ratio α, effective_from) — Hybrid 비중과 정책 버전.
4. `baselines` + `baseline_items`(scope·start·end·weight·completion_criteria) — **자동 승인 금지**,
   사용자가 명시적으로 승인할 때만 생성.
5. `change_logs`(entityType, entityId, before, after, reason, changed_at) — Step 2의 좁은 실행방식
   이력을 이 시점에 흡수. + `progress_snapshots`(asOf, baselineId, scopeVersion, policyVersion, metrics).

**전환 정책(권고):** 실행 방식 `미지정`인 Work Package는 **기존 수동 `progress` 값을 그대로 진척으로
사용**한다. 상위 집계는 가중치가 하나도 없는 가지에서는 **현재의 leaf 개수 가중 평균을 유지**하고,
가중치가 입력된 가지에서만 설계 §6.4 식으로 계산한다. 이렇게 하면 사용자가 가중치를 넣기 전까지
기존 프로젝트의 숫자가 변하지 않는다. (§9 위험 1의 완화책)

## 8. 의사결정 분리

### 8.1 사용자 결정이 필요한 항목

신규 기능을 **추가**하는 선택은 Agent가 설계와 기존 코드에 맞춰 정한다(§8.2). 아래는 그 범위를 넘는 것들
— **기존 데이터의 의미를 재해석하거나, 조직의 운영 방식에 달린 것들**이다.

| # | 결정 | 왜 Agent가 못 정하는가 | 권고 |
|---|---|---|---|
| 1 | **상위 진행률 산식 교체 시점과 방식** (Step 5) | 신규 추가가 아니라 **기존 프로젝트에 표시되던 숫자가 변하는** 변경이다 | §7 전환 정책(가중치가 입력된 가지만 새 식) |
| 2 | **RAID 유형 매핑** (Assumption·Dependency ↔ Action·Decision) | 기존 데이터의 의미와 조직의 RAID 운영 방식에 달렸다. 설계 §9·Step 6-C도 사용자 확인을 요구 | 기존 4종 유지, Dashboard 라벨만 매핑 |
| 3 | **팀(Team) 단위 도입 범위** (Step 4) | 조직 구조 문제. 단일 팀이면 불필요한 복잡도 | 기본 팀 1개로 시작해 필요 시 확장 |
| 4 | **승인자·변경자 기록 방식** | 로그인이 없다. 계정 도입은 이 7단계 범위를 넘는다 | 당분간 자유 입력 문자열 |
| 5 | **Baseline 승인 주체와 시점** | 프로세스 결정. 자동 승인은 설계가 금지 | 사용자 명시적 승인만 |

### 8.2 Agent가 기존 코드에 맞춰 판단할 항목

- **최하위 작업(Work Package) 구분 저장 방식** — `wbs_items.node_type` 추가와 자식 추가 시 전환 규칙(§6.1).
  통합을 위한 신규 구조이고, 기존 행은 현재 트리 모양대로 백필해 화면 동작이 그대로 유지된다.
- **실행 방식 컬럼과 기본값** — `execution_mode` nullable, `NULL` = `미지정`(§6.2).
- enum 이름·컬럼 이름·마이그레이션 번호, DTO 필드 배치, 라벨 문자열.
- 이력 테이블을 Step 2의 좁은 전용 테이블로 시작할지(권고: 그렇게 하고 Step 5에서 `change_logs`로 흡수).
- 프론트엔드 컴포넌트 분할, CSS 토큰 추가 위치(`style.css`), 라벨 맵 파일 위치(`shared/`).
- 테스트 작성 방식(도메인은 순수 단위 테스트, 서비스는 in-memory fake — 기존 `ImportServiceTest` 패턴).
- 새 API의 응답 형태(기존 규칙대로 변경 시 전체 반환, `referenceDate` 동반).

## 9. 위험 · 미확인 사항

**위험**

1. **기존 진행률 숫자가 변할 수 있다** (사용자 영향이 가장 큼). 현재 상위 집계는 leaf 개수 가중 평균,
   설계는 가중치 가중 평균이다. Step 5에서 무조건 교체하면 사용자가 아무것도 바꾸지 않았는데 진척이
   달라진다. → §7 전환 정책으로 완화하되 §8.1-2 결정이 필요.
2. **`ddl-auto: validate` + Flyway undo 부재**로 스키마 롤백 비용이 높다. 코드·스키마를 함께 되돌려야 한다.
3. **`wbs_items.parent_id`가 ON DELETE CASCADE**다. Backlog·Sprint 배정이 붙은 뒤 WBS 항목을 지우면
   실행 이력까지 조용히 사라질 수 있다. Step 3의 "연결 항목이 있는 WBS 삭제" 정책이 이 제약과 충돌하므로
   Step 3에서 삭제 정책을 반드시 다시 설계해야 한다.
4. **Summary 행에도 `progress` 값이 저장되어 있으나 조회 시 무시된다.** 가중치가 들어오면 이 죽은 값이
   혼란을 준다. Step 5에서 정리 대상.
5. **간트의 "실제 실적"이 현재 데이터로 표현 불가**하다(actual/forecast 컬럼 없음). Step 6-A는
   Step 5-A에서 이 컬럼들이 추가되는 것에 의존한다.
6. 프로젝트당 전체 항목을 매 요청 메모리에 올리는 구조라, Backlog·Sprint가 붙으면 조회량이 증가한다.
   현재 규모에서는 문제가 아니지만 Step 5의 동기 집계와 함께 다시 볼 필요가 있다.

**미확인 사항**

1. **운영 데이터의 실제 규모·계층 깊이·Summary 비율.** 이 장비의 H2 파일은 오늘 테스트 실행으로 새로
   생성되어 실질 데이터가 없다. §6.1·§6.2의 백필 규칙은 스키마 근거로만 세웠으므로, 실제 데이터가 있는
   설치본에서 한 번 확인하는 것이 좋다.
2. **`formatVersion: 1` 내보내기 파일이 실제로 유통되었는지.** 유통 이력이 없으면 가져오기 하위 호환의
   중요도가 낮아진다(구현 비용이 작아 권고 자체는 유지).
3. **로그인·권한 도입 계획.** 설계는 approvedBy·changedBy·권한을 전제하지만 시스템에 사용자 개념이 없다.
   Step 5·6-B·7의 승인·책임 표현 수준이 이 답에 달렸다.
4. **Docker(요구사항 3.5·10.5) 검증 상태.** 이 장비에 Docker가 없어 확인하지 못했다.
5. 설계 §4.3 예시에 등장하는 **Excel Import(US-008)** 는 요구사항 문서에 없다. 범위 밖으로 본다.

## 10. 결론

Step 2는 **`node_type` + `execution_mode` 두 컬럼과 좁은 이력 테이블**을 신규로 추가하는 것으로 시작한다.
둘 다 추가이고, `node_type`은 현재 트리 모양대로 백필하고 `execution_mode`는 `미지정`(NULL)이 기본이므로,
**기존 프로젝트의 숫자나 화면은 이 단계에서 달라지지 않는다.** 되돌릴 때도 두 컬럼과 한 테이블을
떨어뜨리면 되므로 범위가 작다.

**차단 요소는 없다 — Step 2는 §8.1의 결정을 기다리지 않고 착수할 수 있다.** §8.1의 1번(진행률 산식
전환)은 Step 5, 2번(RAID 매핑)은 Step 6에서 답이 필요하다.
