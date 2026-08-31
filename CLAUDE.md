# CLAUDE.md

이 파일은 이 저장소에서 작업할 때 Claude Code(claude.ai/code)에게 제공하는 가이드입니다.

## 프로젝트 개요

일정관리 (project-flow) — 일정 관리, WBS, 간트 차트, RACI, RAID 로그를 포괄하는 프로젝트 관리 애플리케이션입니다.

설계 문서: [설계서/프로젝트구조.md](설계서/프로젝트구조.md), [설계서/요구사항_WBS.md](설계서/요구사항_WBS.md)

## 현재 상태

기반 개발(백엔드/프론트엔드 프로젝트 생성, DB 연결), **프로젝트 CRUD**, **WBS 기능**, **간트 차트**,
**지연 업무 자동 판정**, **임계 경로(Critical Path) 표시**, **다크 모드**가 end-to-end로 동작합니다.
RACI/RAID는 라우트와 폴더만 준비되어 있고 기능은 아직 구현되지 않았습니다 (`설계서/요구사항_WBS.md`의 7·9번 항목).

- 백엔드: Spring Boot 3.5.16 (Java 21) + Spring Data JPA + Flyway, `desktop`(H2 파일 DB) / `server`(PostgreSQL) 프로필 분리 완료.
- 프론트엔드: Vue 3 + TypeScript + Vite, `vue-router`로 화면 라우팅.
- 패키징: jpackage 스크립트, 서버용 Dockerfile 작성 완료 (아직 실행/검증은 안 함).

## 명령어

### 백엔드 (`backend/`)

```bash
cd backend
./gradlew bootRun          # desktop 프로필(H2 파일 DB)로 실행, http://localhost:8080
./gradlew test             # 테스트 실행
./gradlew build            # 빌드 (테스트 포함)
```

서버 프로필로 실행하려면 `SPRING_PROFILES_ACTIVE=server`와 `DB_HOST`/`DB_PORT`/`DB_NAME`/`DB_USERNAME`/`DB_PASSWORD` 환경변수를 지정합니다 (PostgreSQL 필요).

### 프론트엔드 (`frontend/`)

```bash
cd frontend
npm install
npm run dev                # 개발 서버, http://localhost:5173 (Vite 프록시로 /api → :8080)
npm run build               # 타입체크(vue-tsc) + 프로덕션 빌드
npm test                    # 단위 테스트 (vitest)
```

### 패키징

```bash
packaging/desktop/jpackage/build-desktop.sh   # 프론트 빌드 → backend 정적 리소스로 복사 → jpackage 앱 이미지 생성
docker build -f packaging/server/Dockerfile -t project-flow-backend .   # 서버 배포용 이미지
```

## 아키텍처

```
backend/
  src/main/java/com/projectflow/
    domain/           # 엔티티, 리포지토리 포트, 도메인 예외,
                       #   WbsTreeAssembler(트리·코드·집계), ScheduleCalculator(FS 일정 계산),
                       #   DependencyGraph(순환 검증), DelayCalculator(지연 판정)
    application/       # ProjectService / WbsService / GanttService(유스케이스), dto/
    infrastructure/     # JPA 리포지토리 구현체, CORS 설정
    presentation/       # 컨트롤러, 전역 예외 핸들러
  src/main/resources/
    application.yml              # 공통 설정 (기본 프로필: desktop)
    application-desktop.yml       # H2 파일 DB (~/.project-flow/data)
    application-server.yml        # PostgreSQL (환경변수 기반)
    db/migration/                 # Flyway 마이그레이션

frontend/
  src/api/            # REST API 클라이언트 (fetch 기반)
  src/features/        # projects, wbs, gantt(구현됨) / raci, raid(폴더만 존재)
  src/shared/          # 여러 feature가 공유하는 도메인 개념 (delay 상태 라벨 등)
  src/stores/          # 화면 간 공유 상태 (선택된 프로젝트, 캐시 무효화 신호)
  src/views/           # 라우트별 화면
  src/router/          # vue-router 설정
```

- 백엔드는 레이어드 아키텍처(domain → application → infrastructure/presentation)를 따르며, 리포지토리는 도메인 포트로 선언하고 `infrastructure.persistence`가 Spring Data JPA로 구현합니다.
- 프론트엔드는 feature 단위로 분리되어 있고, 새 기능(간트 등)을 추가할 때는 `src/features/<feature>/`에 컴포넌트·컴포저블을 두고 `src/views/`에 화면을, `src/router/index.ts`에 라우트를 추가하는 패턴을 따릅니다.
- 개발 중에는 Vite의 `server.proxy`(`vite.config.ts`)가 `/api` 요청을 백엔드(8080)로 전달하므로 별도 CORS 설정 없이 동작합니다. `infrastructure/config/WebConfig`의 CORS 허용은 프록시를 거치지 않는 직접 호출을 위한 보조 설정입니다.

### WBS 설계상 알아둘 점

WBS나 그 위에 얹는 기능(간트, 진행 관리 등)을 건드릴 때 아래 규칙을 전제로 하고 있습니다.

- **파생 값은 저장하지 않습니다.** WBS 코드(`1`, `1.2.1`), Summary 항목의 일정·진행률, 계층 레벨은 모두
  [`WbsTreeAssembler`](backend/src/main/java/com/projectflow/domain/WbsTreeAssembler.java)가 조회 시점에 계산합니다.
  이동·재정렬로 다른 행의 코드까지 바뀌기 때문에, 저장하면 곧바로 낡은 값이 됩니다.
- **부모/프로젝트 참조는 JPA 연관이 아니라 단순 id 컬럼**입니다. WBS 작업은 항상 프로젝트 전체 항목을 한 번에
  읽어 메모리에서 트리를 만들기 때문에, 지연 로딩 연관은 N+1만 늘립니다.
- **Summary 진행률은 하위 leaf 개수로 가중 평균**합니다. 직접 자식 기준 단순 평균이 아니므로, leaf 1개짜리
  가지와 leaf 10개짜리 가지가 같은 비중을 갖지 않습니다.
- **모든 변경 API가 트리 전체를 반환**합니다(`POST`/`PUT`/`DELETE` 포함). 부분 응답으로는 다른 행의 코드 변경을
  클라이언트가 알 수 없습니다.
- **삭제는 하위 항목까지 함께 지웁니다.** `wbs_items.parent_id`의 `ON DELETE CASCADE`에 의존합니다.
- **순환 이동은 서버에서 거부**합니다(400). 프론트엔드도 드롭 자체를 막지만, 서버 검증이 최종 방어선입니다.

### 간트/일정 설계상 알아둘 점

- **선후행 관계는 FS(Finish-to-Start) 한 종류만 지원**합니다. 그래서 `wbs_dependencies`에 관계 종류 컬럼이
  없고, 대신 `lag_days`로 "선행 종료 + lag일 다음 날부터 시작 가능"을 표현합니다. SS/FF/SF가 필요해지면
  컬럼 추가 마이그레이션과 [`ScheduleCalculator`](backend/src/main/java/com/projectflow/domain/ScheduleCalculator.java)
  수정이 함께 필요합니다.
- **날짜는 종료일 포함(inclusive), 달력일 기준**입니다. 영업일/휴일 달력은 아직 없으므로 주말에도 일정이 흘러갑니다.
  차트에서 주말을 음영으로만 구분합니다.
- **선후행 관계는 Summary 항목에도 걸 수 있습니다.** 이때 판정에는 집계된 일정을 쓰고, 재계산 시에는
  하위 leaf 전체를 같은 일수만큼 밀어냅니다 — Summary의 일정은 파생 값이라 직접 옮길 수 없기 때문입니다.
- **일정 재계산은 뒤로만 밀어냅니다.** 여유가 생겨도 앞으로 당기지 않습니다. 단조 증가라서 DAG에서는
  반드시 수렴하며, 반복 상한은 순환이 검증을 빠져나간 경우를 잡는 안전장치입니다.
- **위반은 막지 않고 표시합니다.** 일정 자체는 자유롭게 입력할 수 있고, 제약을 어기면 `scheduleViolation`으로
  표시해 사용자가 재계산 여부를 결정합니다. 반면 **순환 관계는 등록 자체를 거부**합니다(400) — 순환이 있으면
  유효한 일정이 존재하지 않아 차트도 재계산도 의미가 없습니다.
- **일정이 없는 항목은 제약에 참여하지 않습니다.** 판정 대상도, 이동 대상도 되지 않습니다.
- **상위·하위 관계인 항목끼리는 선후행 관계를 걸 수 없습니다**(400). Summary 일정이 하위에서 계산되는
  파생 값이라 만족할 수 있는 일정이 아예 없고, 재계산이 후행의 leaf를 밀면 선행(=조상)의 종료일도 같은
  일수만큼 밀려 위반이 그대로 남습니다 — `relax`가 영원히 수렴하지 않습니다. 순환과 같은 이유로 등록
  자체를 거부합니다.
- **단, WBS 이동은 막지 않습니다.** 형제였던 두 항목에 관계를 걸어둔 뒤 한쪽을 다른 쪽 하위로 옮기면
  위 상태가 그대로 만들어집니다. 그래서 재계산은 진입 시점에
  [`ScheduleCalculator.selfReferentialDependencies`](backend/src/main/java/com/projectflow/domain/ScheduleCalculator.java)로
  이런 관계를 찾아 **문제되는 관계의 WBS 코드를 담은 400**을 돌려줍니다. 예전에는 여기서 `relax`가
  `IllegalStateException`을 던져 500이 났으니 되돌리지 마세요.

### 지연 판정 설계상 알아둘 점

간트 API는 성격이 다른 두 가지 신호를 함께 실어 보냅니다. 헷갈리기 쉬우니 구분해서 다뤄야 합니다.

| 필드 | 의미 | 질문 |
| --- | --- | --- |
| `scheduleViolation` | 계획이 자기모순 | "선행이 끝나기 전에 후행이 시작하나?" |
| `delayStatus` | 계획 대비 현실 | "오늘 기준으로 진행이 밀렸나?" |

- **지연은 입력값이 아니라 계산값**입니다. [`DelayCalculator`](backend/src/main/java/com/projectflow/domain/DelayCalculator.java)가
  WBS 일정·진행률을 기준일과 비교해 판정합니다. 사용자가 지연 일수를 직접 적는 곳은 없습니다.
- **선후행 관계의 `lagDays`는 지연이 아니라 계획상 대기 기간**입니다. UI에서도 "대기(일)"로 표기합니다.
  과거에 "지연(일)"로 적었다가 실제 지연과 혼동을 일으켜 바꿨으니 되돌리지 마세요.
- **기준일은 서버가 정해 `referenceDate`로 함께 반환**합니다. 클라이언트가 자기 시계를 쓰면 한 응답 안의
  행들이 서로 다른 "오늘"로 판정되거나, 오래 열어둔 탭에서 차트의 오늘 선과 배지가 어긋납니다.
- **기대 진행률은 계획 기간에 대한 선형 baseline**입니다(종료일 포함). 공수 산정이나 실제 착수/완료일이
  없는 상태에서 이보다 정교한 가중치를 둘 근거가 없고, 선형이라야 사용자가 값을 예측할 수 있습니다.
- **진행률 100%는 종료일이 지났어도 완료로 봅니다.** 실제 완료일을 기록하지 않으므로 "지연 완료"를
  구분할 근거가 없습니다. 필요해지면 실제 완료일 컬럼이 먼저 있어야 합니다.
- **종료일 당일은 아직 지연이 아닙니다.** 그날이 지나야 `DELAYED`가 됩니다.
- **화면의 건수는 leaf만 셉니다.** Summary는 하위 항목의 지연을 이미 반영하므로 함께 세면 중복 집계됩니다.
  단, **배지는 Summary 행에도 붙입니다** — 접어둔 상태에서 하위의 지연을 놓치면 안 되기 때문입니다.
  그래서 WBS 화면의 배너는 "지연 **업무** N건"으로 적어 숫자가 leaf 기준임을 드러냅니다.
- **WBS 화면은 지연/지연 위험만 배지로 표시**합니다. 구조를 다루는 화면이라 모든 행에 상태를 달면 소음이
  되고, 전체 상태는 간트에서 봅니다.

### 임계 경로 설계상 알아둘 점

[`CriticalPathCalculator`](backend/src/main/java/com/projectflow/domain/CriticalPathCalculator.java)가
간트 조회 시점에 계산해 각 행의 `floatDays`·`criticalPath`와 각 관계의 `criticalPath`로 실어 보냅니다.

- **선후행 관계에 참여하는 항목만 판정합니다.** 임계 경로는 "사슬"에 대한 이야기라서, 아무 관계도
  없는 항목은 사슬 위에 있지 않습니다. 이 항목의 `floatDays`는 `null`이며 "여유 0"과 구분해야 합니다.
- **전진 패스는 계획된 날짜 그대로입니다.** 즉 `EF = 입력된 종료일`이고, 사용자가 일부러 남긴 간격은
  실제 여유(float)로 셉니다. "이 업무가 밀리면 종료일이 밀리는가"라는 실무 질문에 답하려면, 아무도
  입력하지 않은 이상적 일정이 아니라 입력된 계획을 봐야 합니다.
- **`lagDays`는 여유가 아닙니다.** 계획상 대기이므로 후속 판정의 기준선에 포함되며, lag만큼 떨어져
  붙어 있으면 float은 0입니다.
- **float이 음수면 계획이 이미 모순**입니다(= 그 행의 `scheduleViolation`). 줄 여유가 없으므로
  임계 경로로 봅니다.
- **Summary는 집계 일정을 쓰는 단일 노드**입니다(`ScheduleCalculator.analyze`와 동일). 관계가 걸려
  있지 않은 Summary는 판정 대상이 아니라 `floatDays`가 `null`입니다.
- **화면에서 Summary 막대는 흐리게 처리하지 않습니다.** 일정이 하위에서 계산되는 구조 표시라서,
  임계인 자식 바로 위에서 흐려지면 잘못 읽힙니다.
- **임계 경로에 새 색을 쓰지 않습니다.** 채움은 지연 상태가, 점선 외곽선은 선후행 위반이 이미 쓰고
  있어 색 계열을 더하면 서로 헷갈립니다. 대신 `--critical`(배경과 대비가 가장 큰 중립색) 실선 외곽선과
  굵은 화살표로 강조하고, 나머지를 반투명으로 물러나게 합니다.

### 간트 차트 툴팁 (프론트엔드)

- **날짜는 좌표에서 읽습니다.** [`dateAt`](frontend/src/features/gantt/ganttScale.ts)가 `xFor`의 역함수라
  막대 위가 아니어도 커서 아래 날짜를 알 수 있습니다. 축은 날짜 칸을 다 그리지만 숫자는 일부만 적고,
  하루 폭이 좁아지면(`showsDayNumbers`) 아예 안 적기 때문에 이 기능이 필요합니다.
- **리스너는 SVG 하나에만 겁니다.** 막대마다 걸면 막대 사이 빈 곳을 놓치는데, "이 위치가 며칠이지?"는
  거기서 가장 많이 묻습니다.
- **좌표는 `offsetX`가 아니라 SVG의 `getBoundingClientRect()` 기준**으로 계산합니다. SVG 자식 위에서는
  `offsetX`가 그 자식 기준이라, 커서가 막대에 올라가는 순간 값이 튑니다.
- **툴팁은 `position: fixed`**입니다. 타임라인이 가로 스크롤 컨테이너 안에 있어서, 컨테이너 기준
  절대 좌표로 두면 스크롤할 때 어긋나거나 잘립니다.
- **막대의 `<title>`은 제거했습니다.** 커스텀 툴팁과 네이티브 툴팁이 이중으로 뜹니다. 접근성 이름은
  `role="img"` + `aria-label`로 유지하니 되돌리지 마세요.

### 다크 모드 (프론트엔드)

- **색은 전부 [`style.css`](frontend/src/style.css)의 CSS 변수로만 정의합니다.** 컴포넌트 스코프
  스타일에 하드코딩된 색이 하나 남으면 그 자리만 라이트로 남아 다크에서 눈에 튑니다.
- **컴포넌트에서 토큰을 재정의하지 마세요.** 과거 `GanttChart`가 `.gantt { --status-completed: … }`로
  팔레트를 갖고 있었는데, 값을 토큰으로 옮기면서 `--status-completed: var(--status-completed)` 자기
  참조가 되어 SVG 채움이 전부 검게 나왔습니다.
- **`data-theme`은 항상 `light`/`dark`로 확정된 값**입니다. "시스템 설정"은
  [`stores/theme.ts`](frontend/src/stores/theme.ts)가 미리 해석해 넣으므로, 다크 팔레트를 미디어
  쿼리용과 수동 토글용으로 두 번 적을 필요가 없습니다.
- **첫 페인트 전 테마 확정은 `index.html`의 인라인 스크립트**가 합니다. 저장 키(`project-flow.theme`)를
  바꿀 때 `stores/theme.ts`와 같이 고쳐야 합니다.
- **`color-scheme`을 테마별로 지정합니다.** `<input type="date">`·`<select>` 같은 네이티브 컨트롤이
  이것 없이는 흰 배경으로 남습니다.
- **비활성 컨트롤은 배경과 글자색을 함께 지정**합니다(`--disabled-bg`/`--disabled-fg`). 배경만 옅게
  하면 다크에서 글자가 배경에 묻힙니다.

### 화면 간 상태 유지 (프론트엔드)

탭을 옮겨도 선택과 데이터가 유지되어야 하므로, 컴포저블의 상태를 **모듈 스코프**에 두어 모든 뷰가 하나의
인스턴스를 공유합니다(`useProjects`/`useWbs`/`useGantt`). 뷰마다 새 `ref`를 만들면 이동할 때마다 선택이
첫 프로젝트로 되돌아가고 매번 재요청이 발생합니다.

공유 캐시에는 무효화가 따라와야 합니다. [`stores/scheduleCache.ts`](frontend/src/stores/scheduleCache.ts)의
캐시 키는 `(프로젝트, WBS 리비전, 로컬 날짜)`이고, 아래 규칙을 지킵니다.

- **WBS 항목이 바뀌면 리비전을 올립니다** → 간트가 다음 방문에 다시 읽습니다.
- **선후행 관계 추가/삭제는 리비전을 올리지 않습니다** → 의존성은 WBS 트리에 없으므로 WBS는 그대로 둡니다.
- **일정 재계산은 WBS 날짜를 바꾸므로 리비전을 올립니다** → WBS가 다시 읽습니다.
- **로컬 날짜가 캐시 키에 들어갑니다** → 탭을 밤새 열어둬도 다음 날 지연 판정이 갱신됩니다. 이 날짜는
  무효화용이며, 화면에 표시하는 기준일은 항상 서버의 `referenceDate`입니다.
- **`ensureLoaded`는 진행 중 요청을 공유합니다.** 라우트 전환 시 뷰 마운트와 선택 watcher가 같은 tick에
  겹쳐 동일 요청이 두 번 나가던 문제가 있었습니다. 뷰의 로드 경로는 `watch(selectedProjectId, …,
  { immediate: true })` **하나**로 유지하세요 — `onMounted`에서 추가로 부르면 그 중복이 되살아납니다.

## API 요약

| 메서드 | 경로 | 설명 |
| --- | --- | --- |
| `GET` | `/api/projects` | 프로젝트 목록 |
| `POST` | `/api/projects` | 프로젝트 생성 |
| `GET` `PUT` `DELETE` | `/api/projects/{id}` | 프로젝트 조회/수정/삭제 |
| `GET` | `/api/projects/{projectId}/wbs` | WBS 트리 조회 (`{ referenceDate, nodes }`, 각 노드에 지연 판정 포함) |
| `POST` | `/api/projects/{projectId}/wbs` | WBS 항목 생성 (`parentId` 없으면 최상위) |
| `PUT` | `/api/projects/{projectId}/wbs/{itemId}` | WBS 항목 수정 |
| `PUT` | `/api/projects/{projectId}/wbs/{itemId}/move` | 재부모화·재정렬 (`parentId`, `position`) |
| `DELETE` | `/api/projects/{projectId}/wbs/{itemId}` | WBS 항목 삭제 (하위 포함) |
| `GET` | `/api/projects/{projectId}/gantt` | 간트 데이터 (막대 + 선후행 + 선후행 위반 + 지연 판정 + 임계 경로) |
| `POST` | `/api/projects/{projectId}/gantt/dependencies` | 선후행 관계 등록 (`predecessorId`, `successorId`, `lagDays`) |
| `PUT` | `/api/projects/{projectId}/gantt/dependencies/{dependencyId}` | 선후행 관계 수정 (선행·후행·`lagDays` 모두 변경 가능) |
| `DELETE` | `/api/projects/{projectId}/gantt/dependencies/{dependencyId}` | 선후행 관계 삭제 |
| `POST` | `/api/projects/{projectId}/gantt/recalculate` | 선후행 제약을 만족하도록 일정 재계산 |

WBS·간트의 모든 변경 API는 부분 응답이 아니라 갱신된 전체 데이터를 반환합니다.

## 다음 단계 (설계서/요구사항_WBS.md 기준)

- 4.2 프로젝트 구성원 관리, 4.3 프로젝트 상태 관리(세부 규칙)
- 7. RACI (Accountable 중복 검증, Responsible 누락 검증)
- 8. 진행 관리 — 8.3 지연 업무 판정은 완료. 남은 것은 8.2 진행률 변경 이력, 8.4 프로젝트 대시보드
  (대시보드는 간트 API의 `delayStatus` 집계를 그대로 쓰면 됩니다)
- 9. RAID 관리
- 3.5 Docker 환경 구성 실제 빌드/검증, 10. 테스트 및 배포
- 일정 기능 확장 후보: 영업일/휴일 달력(지연 일수·기대 진행률이 함께 정확해집니다),
  FS 이외의 관계 종류(SS/FF/SF)
