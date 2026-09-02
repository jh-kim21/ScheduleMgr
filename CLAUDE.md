# CLAUDE.md

이 파일은 이 저장소에서 작업할 때 Claude Code(claude.ai/code)에게 제공하는 가이드입니다.

## 프로젝트 개요

일정관리 (project-flow) — 일정 관리, WBS, 간트 차트, RACI, RAID 로그를 포괄하는 프로젝트 관리 애플리케이션입니다.

설계 문서: [설계서/프로젝트구조.md](설계서/프로젝트구조.md), [설계서/요구사항_WBS.md](설계서/요구사항_WBS.md)

## 현재 상태

기반 개발(백엔드/프론트엔드 프로젝트 생성, DB 연결), **프로젝트 CRUD**, **WBS 기능**, **간트 차트**,
**지연 업무 자동 판정**, **임계 경로(Critical Path) 표시**, **다크 모드**, **프로젝트 구성원 관리**,
**RACI 매트릭스**, **RAID 로그**가 end-to-end로 동작합니다. 설계서의 화면은 모두 채워졌습니다.

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
packaging/desktop/jpackage/build-desktop.sh   # 프론트 빌드 → backend 정적 리소스로 복사 → OS별 설치 파일 생성
APP_TYPE=app-image packaging/desktop/jpackage/build-desktop.sh   # 설치 없이 실행할 앱 폴더
docker build -f packaging/server/Dockerfile -t project-flow-backend .   # 서버 배포용 이미지
```

- **jpackage는 크로스 빌드를 못 합니다.** macOS에서는 `app-image`/`dmg`/`pkg`만 만들 수 있고 `--type exe`는
  "Invalid or unsupported type"으로 거부됩니다. Windows `.exe`는 Windows에서, `.deb`는 Linux에서 같은
  스크립트를 실행해야 합니다. 장비가 없으면
  [`.github/workflows/desktop-installer.yml`](.github/workflows/desktop-installer.yml)을
  Actions에서 수동 실행하면 windows/macos 러너가 각각 만들어 아티팩트로 올려줍니다.
- **설치 파일 버전은 프로젝트 버전과 별개**입니다(`APP_VERSION`, 기본 `1.0.0`). macOS는 첫 자리가 0이면
  거부하고, 설치 파일에는 `SNAPSHOT` 같은 접미사를 넣을 수 없습니다.
- **`--win-upgrade-uuid`는 절대 바꾸지 마세요.** 값이 바뀌면 새 버전이 기존 설치를 덮어쓰지 않고 나란히
  설치됩니다.
- **Windows의 exe/msi 생성은 WiX Toolset 3.x**(candle.exe)에 의존합니다. WiX 4/5는 JDK 21의 jpackage가
  쓰지 못합니다. 스크립트가 미리 확인해 안내합니다.
- **데스크톱 빌드는 프론트엔드를 품은 서버**라서 실행해도 창이 없습니다. 창이 없다는 것이 곧
  "실패해도 아무 일도 안 일어난 것처럼 보인다"는 뜻이라, 세 가지를 함께 둡니다. 모두
  `project-flow.desktop.enabled` 하나로 켜지고 기본값은 꺼져 있습니다 — 개발 중 `bootRun`에서
  탭이 열리거나 포트가 바뀌면 방해가 됩니다.
  - [`DesktopBrowserLauncher`](backend/src/main/java/com/projectflow/infrastructure/config/DesktopBrowserLauncher.java):
    기동 후 브라우저를 엽니다. 포트는 실제 바인딩된 값(`local.server.port`)에서 읽습니다.
  - [`DesktopPortFallback`](backend/src/main/java/com/projectflow/infrastructure/config/DesktopPortFallback.java):
    8080이 사용 중이면 빈 포트로 옮깁니다. 이게 없으면 다른 서버가 8080을 쓰는 순간 앱이
    조용히 죽습니다. **`EnvironmentPostProcessor`이고 `META-INF/spring.factories`에 등록**합니다 —
    포트는 웹 서버가 바인딩하기 전에 정해져야 해서 빈으로는 늦고, `.imports` 방식은
    auto-configuration 전용이라 등록되지 않습니다(그렇게 했다가 동작하지 않았습니다).
    **호스팅(`server` 프로필)에서는 켜지 않습니다** — 거기서 포트는 주소의 일부라 조용히
    바뀌면 안 되고, 크게 실패하는 것이 맞습니다.
  - [`ProjectFlowApplication`](backend/src/main/java/com/projectflow/ProjectFlowApplication.java):
    기동 실패 시 오류 창을 띄웁니다. Spring Boot는 명시된 `java.awt.headless` 값을 유지하므로
    런처가 `-Djava.awt.headless=false`를 넘깁니다.
- **설치본의 설정은 `~/.project-flow/application.properties`에서 읽습니다.** 런처가
  `-Dspring.config.additional-location=optional:file:${user.home}/.project-flow/`를 넘깁니다.
  데이터와 같은 폴더라 재설치·업그레이드에도 남고, 설치 폴더(관리자 권한이 필요할 수 있는 곳)를
  건드리지 않습니다. `optional:`이라 파일이 없어도 그냥 기동합니다.
  - 예: 포트를 바꾸려면 `server.port=8090` 한 줄. 브라우저도 그 주소로 열립니다
    (`DesktopBrowserLauncher`가 실제 바인딩된 포트를 읽으므로).
  - **NSIS 런처만 `$PROFILE`을 씁니다.** NSIS에서 `${...}`는 자신의 define 문법이라 Spring
    플레이스홀더를 쓸 수 없습니다. jpackage는 `--java-options`에서 `$APPDIR` 계열만 치환하므로
    `${user.home}`이 그대로 통과합니다(생성된 `.cfg`로 확인).
  - 설정 파일 없이 즉석으로 바꾸려면 인자나 환경변수도 됩니다:
    `ProjectFlow.exe --server.port=8090`, 또는 `SERVER_PORT=8090`.

## 아키텍처

```
backend/
  src/main/java/com/projectflow/
    domain/           # 엔티티, 리포지토리 포트, 도메인 예외,
                       #   WbsTreeAssembler(트리·코드·집계), ScheduleCalculator(FS 일정 계산),
                       #   DependencyGraph(순환 검증), DelayCalculator(지연 판정),
                       #   CriticalPathCalculator(임계 경로), RaciValidator(RACI 규칙 검증),
                       #   RaidAssessor(노출도·기한 초과 판정)
    application/       # ProjectService / WbsService / GanttService /
                       #   ProjectMemberService / RaciService / RaidService(유스케이스), dto/
    infrastructure/     # JPA 리포지토리 구현체, CORS 설정
    presentation/       # 컨트롤러, 전역 예외 핸들러
  src/main/resources/
    application.yml              # 공통 설정 (기본 프로필: desktop)
    application-desktop.yml       # H2 파일 DB (~/.project-flow/data)
    application-server.yml        # PostgreSQL (환경변수 기반)
    db/migration/                 # Flyway 마이그레이션

frontend/
  src/api/            # REST API 클라이언트 (fetch 기반)
  src/features/        # projects, wbs, gantt, raci, raid
  src/shared/          # 여러 feature가 공유하는 도메인 개념 (delay 상태 라벨 등)
  src/stores/          # 화면 간 공유 상태 (선택된 프로젝트, 캐시 무효화 신호)
  src/views/           # 라우트별 화면
  src/router/          # vue-router 설정
```

- 백엔드는 레이어드 아키텍처(domain → application → infrastructure/presentation)를 따르며, 리포지토리는 도메인 포트로 선언하고 `infrastructure.persistence`가 Spring Data JPA로 구현합니다.
- 프론트엔드는 feature 단위로 분리되어 있고, 새 기능(간트 등)을 추가할 때는 `src/features/<feature>/`에 컴포넌트·컴포저블을 두고 `src/views/`에 화면을, `src/router/index.ts`에 라우트를 추가하는 패턴을 따릅니다.
- 개발 중에는 Vite의 `server.proxy`(`vite.config.ts`)가 `/api` 요청을 백엔드(8080)로 전달합니다. 프록시로 붙는 다른 포트를 쓰려면 `VITE_API_TARGET`으로 대상을 바꿉니다.
- **CORS 허용 오리진은 프로필 설정값**(`project-flow.cors.allowed-origin-patterns`, 쉼표 구분)이고
  [`WebConfig`](backend/src/main/java/com/projectflow/infrastructure/config/WebConfig.java)가 읽습니다.
  `desktop`은 루프백 전 포트(`http://localhost:[*]`, `http://127.0.0.1:[*]`)를 허용하고, `server`는
  `CORS_ALLOWED_ORIGINS`가 없으면 아무 교차 오리진도 허용하지 않습니다.
  - **포트를 하나로 고정하지 마세요.** 브라우저는 same-origin이라도 GET 이외의 메서드에는 `Origin`을
    붙이고, Vite 프록시(`changeOrigin: true`)는 Host만 바꾸고 Origin은 그대로 넘깁니다. 그래서 Vite가
    5173 대신 다른 포트로 뜨면 **화면은 뜨는데 저장만 403으로 실패**합니다(GET은 Origin이 없어 통과).
    예전에 `allowedOrigins("http://localhost:5173")`로 고정했다가 이 문제를 겪었으니 되돌리지 마세요.
  - 목록이 비면 매핑 자체를 등록하지 않습니다 — 값이 없는 것과 빈 문자열 하나가 들어온 것을
    구분하려고 `List` 대신 문자열로 받아 직접 자릅니다.

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

### RACI 설계상 알아둘 점

- **구성원(요구사항 4.2)이 매트릭스의 열입니다.** RACI는 (업무 × 사람) 표라서 사람 없이는 표가
  성립하지 않습니다. 그래서 RACI를 하려면 구성원 관리가 먼저 필요합니다.
- **구성원은 프로젝트 스코프**입니다. 로그인이 없고, 한 프로젝트 안에서 뜻이 통하는 이름("PL",
  "외주 개발")이 시스템 전체에서 유일할 필요도 없습니다. 대신 **프로젝트 안에서 이름은 유일**해야
  합니다 — 겹치면 매트릭스 열을 구분할 수 없습니다.
- **한 칸은 글자의 집합입니다.** `(업무, 구성원, 역할)`로 유일성을 잡아 한 사람이 A와 R을 겸할 수
  있습니다. 담당자가 책임자를 겸하는 흔한 경우를 한 글자로 제한하면 7.4(Responsible 누락) 검증이
  잘못 걸립니다. UI에서는 칸마다 R·A·C·I 토글 네 개로 보여주고, 눌러서 배정/해제합니다.
- **규칙 위반은 막지 않고 표시합니다**([`RaciValidator`](backend/src/main/java/com/projectflow/domain/RaciValidator.java)).
  책임자가 둘인 상태는 인수인계 중에 반드시 지나가는 정상적인 저장 상태이고, 거부하면
  "지우고 다시 넣기"를 강요하게 됩니다. 일정 쪽과 같은 기준입니다 — 만족 불가능한 구조(순환)는
  거부하고, 계획이 서로 안 맞는 것은 표시합니다.
- **검증은 leaf만** 합니다. 하위가 있는 항목에 담당자가 없는 것은 공백이 아니고(일은 하위에 있음),
  같이 세면 한 누락이 레벨마다 중복 보고됩니다. 단 **Summary에도 배정은 가능**합니다 — 단계 전체의
  최종 책임자를 적는 것은 정상적인 사용입니다.
- **모든 변경 API가 매트릭스 전체를 반환**합니다. 글자 하나가 그 행의 위반을 해소하거나 만들 수
  있는데, 부분 응답으로는 클라이언트가 알 수 없습니다.
- **구성원 API만 예외로 구성원 하나만 반환**합니다(요구사항 4.2는 프로젝트 기능이라 RACI를 몰라야
  합니다). 대신 RACI 화면이 구성원 변경 후 매트릭스를 다시 읽습니다.
- **삭제는 DB 연쇄에 의존합니다.** `raci_assignments`의 `member_id`·`wbs_item_id`가
  `ON DELETE CASCADE`라서 구성원이나 WBS 항목을 지우면 배정도 함께 사라집니다.
- **RACI 캐시 키에는 날짜가 들어가지 않습니다**(`raciCacheKeyFor`). 행은 WBS에서 오지만 "오늘"을
  기준으로 판정하는 값이 없습니다.

### RAID 설계상 알아둘 점

- **네 종류를 한 테이블에 둡니다**(`raid_items.raid_type`). 제목·상태·소유자·기한 등 대부분의 필드를
  공유하고 한 화면에서 함께 읽히므로, 종류별 테이블은 CRUD를 네 벌로 늘리면서 얻는 것이 없습니다.
- **상태 수명주기도 하나입니다**(`OPEN`/`IN_PROGRESS`/`CLOSED`). 종류별 표현("확인됨" vs "해소")은
  라벨 문제라 화면이 처리하고, 저장 상태를 네 갈래로 쪼개면 필터·집계 코드가 네 배가 됩니다.
- **노출도(exposure)는 저장하지 않고 확률 × 영향으로 계산**합니다
  ([`RaidAssessor`](backend/src/main/java/com/projectflow/domain/RaidAssessor.java)). 1·2·3 가중치라
  값은 1~9이고, 6 이상 높음 / 3~4 보통 / 그 아래 낮음으로 묶습니다(1,2,3,4,6,9만 나올 수 있음).
- **노출도에 종류 제한을 두지 않습니다.** 확률과 영향이 둘 다 있으면 환산합니다 — 이미 발생한 이슈에
  영향만 적는 것도 정상이고, "이 종류는 숫자를 가질 수 없다"는 규칙을 만들면 입력한 값을 화면이
  숨기게 됩니다. 대신 **폼이 종류별로 묻는 항목만 보여줍니다**(위험=확률+영향, 이슈=영향).
- **기한 초과는 지연 판정과 같은 규칙**입니다. 기준일은 서버가 정해 응답에 함께 싣고, 기한 당일은
  아직 초과가 아니며, **종결된 항목은 기한이 지났어도 초과가 아닙니다**(남은 일이 없으므로).
- **소유자는 프로젝트 구성원을 가리키고 `ON DELETE SET NULL`입니다.** 구성원이 빠지면 항목은 남고
  소유자만 비워져야 합니다 — CASCADE면 기록이 사라집니다.
- **모든 변경 API가 로그 전체를 반환**합니다. WBS와 이유가 다릅니다(행 간 파생 값이 없음) — 기한 초과가
  응답의 `referenceDate` 기준이라 클라이언트가 자기 시계를 쓰면 안 되고, 둘을 함께 돌려주면
  클라이언트가 행을 목록에 병합할 필요도 없어집니다.
- **항목을 WBS 업무에 연결할 수 있습니다**(선택, `raid_items.wbs_item_id`). 소유자와 같은 이유로
  `ON DELETE SET NULL`입니다 — WBS 항목이 지워져도 위험 기록 자체는 남아야 합니다. 표시용 WBS
  코드는 트리 위치에서 파생되므로 **서버가 트리를 조립해 코드·이름을 함께 실어 보냅니다**.
- **RAID 캐시 키는 `(프로젝트, WBS 리비전, 로컬 날짜)`입니다.** WBS 리비전이 들어가는 이유는 위 연결
  때문입니다 — 업무가 이동·개명되면 목록과 선택기의 코드가 낡습니다. 로컬 날짜는 기한 초과가
  날짜 기준이라 필요합니다(표시하는 기준일은 항상 서버의 `referenceDate`).
- **필터·정렬은 클라이언트에서** 합니다([`raidFilter.ts`](frontend/src/features/raid/raidFilter.ts)).
  로그는 한 화면 분량이고, 이건 데이터가 아니라 "지금 이 화면"에 대한 질문이라 서버로 보내면
  드롭다운마다 왕복이 생기고 조합마다 캐시 키가 필요해집니다. 순수 함수라 vitest로 검증합니다.
- **입력 패널은 기본으로 접혀 있습니다.** 이 화면의 주된 행위는 로그를 *읽는* 것이고 항목 입력은
  간헐적이라, 항상 펼쳐진 폼이 표를 화면 아래로 밀어내면 안 됩니다. 목록 머리말의 `＋ 항목 추가`나
  행의 `수정`으로 열립니다. 폼이 표 위에 있으므로 아래쪽 행에서 열면 `scrollIntoView`로 이동시킵니다.
  **수정 저장 후에는 닫고, 추가 후에는 열어 둡니다** — 여러 건을 연달아 기록하는 것이 흔하고,
  아래 표에 새 행이 나타나는 것이 이미 확인 신호입니다.
- **상단 배너는 필터를 무시하고 전체를 셉니다.** "기한 초과 2건"은 프로젝트에 대한 사실인데,
  필터를 걸어서 숫자가 줄면 잘못 읽힙니다. 필터를 따르는 것은 표뿐입니다.
- **정렬에서 값이 없는 항목은 뒤로 보냅니다.** 기한 없는 위험이 내일 마감보다 급하지 않고,
  등급 미지정이 가장 노출된 것도 아닙니다. 동점은 id 순이라 편집 중에 행이 튀지 않습니다.

### 데이터 내보내기 설계상 알아둘 점

공유 경로가 두 가지이고, 목적이 달라서 형식도 다릅니다.

- **프로젝트 전체 JSON**(`/export`)은 **다시 읽어들이기 위한 것**입니다. 데스크톱 빌드는 각자
  로컬 H2 파일에 데이터를 두므로 공유 서버가 없고, 프로젝트를 남에게 넘기는 방법은 파일뿐입니다.
  그래서 **저장된 상태만** 담고 판정값(지연 상태·선후행 위반·float·노출도·기한 초과)은 넣지
  않습니다 — 모두 "오늘" 또는 다른 행을 기준으로 계산되는 값이라, 파일에 박히는 순간 거짓이
  됩니다. 예외는 WBS 코드로, 사람이 읽을 때 필요해서 넣되 파생 값입니다.
- **화면별 CSV**는 **사람이 Excel에서 읽기 위한 것**입니다. 그래서 반대로 판정값을 포함하고,
  enum 대신 라벨("지연" not "DELAYED")을 씁니다. 기준일을 열 머리글에 박아 며칠 뒤 열어도 무엇
  기준인지 알 수 있게 합니다.
- **내보내기·가져오기는 프로젝트 화면 한 곳에만 둡니다.** 내보내는 단위가 프로젝트이므로,
  화면마다 버튼을 두면 "전체 JSON"이 네 벌로 중복되고 무엇이 내보내지는 단위인지도 흐려집니다.
  프로젝트 행의 셀렉트 하나로 전체(JSON)·WBS·RACI·RAID(CSV)를 고릅니다
  ([`ExportMenu`](frontend/src/features/export/ExportMenu.vue)).
- **CSV는 클라이언트에서 만듭니다**([`csv.ts`](frontend/src/shared/csv.ts),
  [`exportRows.ts`](frontend/src/shared/exportRows.ts)). 서버에 CSV 엔드포인트를 세 개 더 두는
  것보다 이미 있는 조회 API를 눌러 쓰는 편이 단순합니다.
  - **화면의 필터를 따르지 않습니다.** 프로젝트 화면에서 부르므로 그 프로젝트의 WBS·RACI·RAID를
    새로 읽어 전부 내보냅니다. 예전에는 화면마다 버튼을 두어 "보이는 것만" 나갔는데, 버튼을
    한 곳으로 모으면서 그 성질을 잃었습니다 — 필터를 반영한 내보내기가 필요해지면 화면 쪽에
    다시 두어야 합니다.
- **CSV 앞에 UTF-8 BOM을 붙입니다.** 없으면 Windows Excel이 시스템 코드페이지로 읽어 한글이
  깨집니다. 눈에 보이지 않는 문자에 의존하지 않도록 `'\uFEFF'` 이스케이프로 적고, 테스트로
  고정했습니다(`withBom`). 참고로 `Blob.text()`는 BOM을 제거하고 디코딩하므로, 검증할 때는
  `arrayBuffer()`로 바이트를 봐야 합니다.
- **JSON 다운로드는 평범한 링크**입니다. 서버가 `Content-Disposition: attachment`로 내려주므로
  fetch·blob 코드가 필요 없고, 개발 프록시와 설치본에서 똑같이 동작합니다. 한글 파일명은
  `ContentDisposition`이 RFC 5987로 인코딩합니다.
- **`formatVersion`을 함께 싣습니다.** 가져오기가 파일보다 낮은 버전이면 거부합니다 — 모르는
  필드를 조용히 버리는 것보다 낫습니다.

### 가져오기 설계상 알아둘 점

- **항상 새 프로젝트를 만듭니다**([`ImportService`](backend/src/main/java/com/projectflow/application/ImportService.java)).
  기존 프로젝트에 병합하려면 "이 항목이 이름만 바뀐 같은 업무인가"를 행마다 판단해야 하는데,
  파일은 그 답을 갖고 있지 않습니다. 공유가 실제로 필요한 것도 "받은 프로젝트를 내 것 옆에
  놓고 보는 것"입니다.
- **모든 id를 다시 매깁니다.** 파일의 id는 그것을 만든 설치본의 것이라 여기서는 의미가 없습니다.
  그래서 **상위부터 삽입하며 old→new 맵**을 만들고, 선후행·RACI·RAID의 참조를 그 맵으로 바꿉니다.
  파일 순서에 의존하지 않습니다 — 손으로 편집한 파일은 트리 순서가 아닐 수 있습니다.
- **입력 타입으로 export 응답 타입을 그대로 씁니다.** 모양을 두 벌 정의하면 왕복이 어긋날 수
  있습니다.
- **구조는 검증하고, 계획의 품질은 검증하지 않습니다.** DB 제약에 걸리거나 참조가 끊어지는 것
  (파일에 없는 상위, 자기 자신 선후행, 중복 RACI 글자 등)은 무엇이 문제인지 적어 거부합니다.
  반면 계획이 서로 안 맞는 것(순환 선후행, 상위·하위 간 관계)은 그대로 가져옵니다 — 사용자
  자신의 데이터이고, 일정 화면이 이미 그 문제를 설명해 줍니다. 거부하면 데이터를 아예 못
  넣게 됩니다.
- **검증이 삽입보다 앞에 있습니다.** 트랜잭션 롤백에만 기대지 않고, 거부된 파일은 아무것도
  만들지 않는 것을 테스트로 고정했습니다.
- **이름이 겹치면 뒤에 "(가져옴)"을 붙입니다.** 프로젝트 이름에 유일성 제약은 없지만, 목록에
  같은 이름이 둘이면 구분할 수 없습니다.
- **파싱 실패에 메시지를 답니다.** Spring 기본 처리는 본문을 못 읽을 때 message 없는 400을
  주고, 그것이 화면에 "요청 실패 (400)"으로 나옵니다. 파일을 잘못 고르는 것이 가장 흔한 실수인
  가져오기에서 그건 막다른 길이라 `HttpMessageNotReadableException` 핸들러를 두었습니다.

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
| `GET` `POST` | `/api/projects/{projectId}/members` | 프로젝트 구성원 목록 / 등록 |
| `PUT` `DELETE` | `/api/projects/{projectId}/members/{memberId}` | 구성원 수정 / 삭제 (RACI 배정 연쇄 삭제) |
| `GET` | `/api/projects/{projectId}/raci` | RACI 매트릭스 (열 + 행 + 셀 + 규칙 위반) |
| `POST` | `/api/projects/{projectId}/raci/assignments` | 역할 배정 (`wbsItemId`, `memberId`, `role`) |
| `DELETE` | `/api/projects/{projectId}/raci/assignments/{assignmentId}` | 역할 해제 (글자 하나) |
| `GET` `POST` | `/api/projects/{projectId}/raid` | RAID 로그 조회 / 항목 추가 (`wbsItemId`로 업무 연결 가능) |
| `PUT` `DELETE` | `/api/projects/{projectId}/raid/{itemId}` | 항목 수정 / 삭제 |
| `GET` | `/api/projects/{projectId}/export` | 프로젝트 전체를 JSON 한 파일로 내려받기 (attachment) |
| `POST` | `/api/projects/import` | 내보낸 파일로 **새 프로젝트** 생성 (본문 = export 응답 그대로) |

WBS·간트의 모든 변경 API는 부분 응답이 아니라 갱신된 전체 데이터를 반환합니다.

## 다음 단계 (설계서/요구사항_WBS.md 기준)

- 4.3 프로젝트 상태 관리(세부 규칙)
- 8. 진행 관리 — 8.3 지연 업무 판정은 완료. 남은 것은 8.2 진행률 변경 이력, 8.4 프로젝트 대시보드
  (대시보드는 간트 API의 `delayStatus` 집계를 그대로 쓰면 됩니다)
- 3.5 Docker 환경 구성 실제 빌드/검증, 10. 테스트 및 배포
- 일정 기능 확장 후보: 영업일/휴일 달력(지연 일수·기대 진행률이 함께 정확해집니다),
  FS 이외의 관계 종류(SS/FF/SF)
