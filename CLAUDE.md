# CLAUDE.md

이 파일은 이 저장소에서 작업할 때 Claude Code(claude.ai/code)에게 제공하는 가이드입니다.

## 프로젝트 개요

일정관리 (project-flow) — 일정 관리, WBS, 간트 차트, RACI, RAID 로그를 포괄하는 프로젝트 관리 애플리케이션입니다.

설계 문서: [설계서/프로젝트구조.md](설계서/프로젝트구조.md), [설계서/요구사항_WBS.md](설계서/요구사항_WBS.md)

## 현재 상태

기반 개발(백엔드/프론트엔드 프로젝트 생성, DB 연결), **프로젝트 CRUD**, **WBS 기능**, **간트 차트**,
**지연 업무 자동 판정**, **임계 경로(Critical Path) 표시**, **다크 모드**, **프로젝트 구성원 관리**,
**RACI 매트릭스**, **RAID 로그**가 end-to-end로 동작합니다. 설계서의 화면은 모두 채워졌습니다.

여기에 얹은 **WBS + Agile 하이브리드 관리**
([설계서/agile_design/](설계서/agile_design/), 7단계)는 **Step 7까지 모두 완료**했습니다 —
관리 단위·실행 방식, Product Backlog, Sprint·Board, 공통 진척 집계, 간트·RACI·RAID 연계,
프로젝트 대시보드까지 동작합니다. 단계별 결과 보고서는
[설계서/agile_design/results/](설계서/agile_design/results/)에 있고, **쓰는 법과 알려진 제한은
[운영 안내](설계서/agile_design/results/Hybrid_PM_Operation_Guide.md)** 에 정리돼 있습니다.
**팀은 하나를 전제합니다** (사용자 결정) — 그래서 팀 테이블이 없고, 실행 중인 Sprint도 하나뿐이며,
속도 추세도 계열이 하나입니다.

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
                       #   WbsTreeAssembler(트리·코드·집계·실행 방식 요약), ScheduleCalculator(FS 일정 계산),
                       #   DependencyGraph(순환 검증), DelayCalculator(지연 판정),
                       #   CriticalPathCalculator(임계 경로), RaciValidator(RACI 규칙 검증),
                       #   RaidAssessor(노출도·기한 초과 판정),
                       #   WbsNodeType·ExecutionMode·ExecutionModeSummary(관리 단위와 실행 방식),
                       #   BacklogItemType·BacklogAssessor(실행 항목과 연결 판정),
                       #   Sprint·SprintItem·SprintAssessor·CompletionCheck(실행 주기와 완료 절차)
    application/       # ProjectService / WbsService / GanttService / BacklogService / SprintService /
                       #   ProgressService(공통 집계) / ProgressBasisService(가중치·체크포인트·Baseline) /
                       #   DashboardService(다른 서비스의 응답을 모으기만 함) /
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
  src/features/        # projects, dashboard, wbs, backlog, sprint, progress, gantt, raci, raid
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
  단 **연결된 Backlog가 있으면 삭제 자체를 거부**합니다(아래 Backlog 절) — 이 연쇄가 실행 기록까지
  끌고 가기 때문입니다.
- **순환 이동은 서버에서 거부**합니다(400). 프론트엔드도 드롭 자체를 막지만, 서버 검증이 최종 방어선입니다.

### 실행 방식(Execution Mode) 설계상 알아둘 점

WBS와 Agile을 잇는 하이브리드 설계의 첫 조각입니다
([설계서/agile_design/](설계서/agile_design/), 결과 보고서는 `results/` 아래).

- **관리 단위 구분은 저장하고(`wbs_items.node_type`), 일정 집계는 여전히 자식 유무로 판단합니다.**
  두 값은 다른 질문에 답합니다 — `nodeType`은 "이 항목이 최하위 관리 단위(Work Package)인가",
  [`WbsNode.summary()`](backend/src/main/java/com/projectflow/domain/WbsNode.java)는 "이 행의 일정·진행률이
  하위에서 온 것인가"입니다. 마이그레이션이 자식 유무로 백필했으므로 기존 데이터에서는 둘이 항상
  일치하고, **`SUMMARY`로 전환했지만 아직 자식이 없는 과도 상태에서만 갈라집니다.** 이때 일정까지
  `nodeType` 기준으로 바꾸면 그 항목의 날짜가 빈칸이 되어 입력한 값이 화면에서 사라집니다.
- **실행 방식 미지정은 `NULL`입니다.** `ExecutionMode`에 `UNSPECIFIED` 상수를 두지 마세요 — 드롭다운에서
  고를 수 있는 값이 되어 "아직 정하지 않음"과 구분할 수 없게 됩니다. `WATERFALL`을 기본값으로 두는 것도
  안 됩니다: 설계의 Waterfall 진척은 승인 체크포인트 가중치 비율이라 체크포인트가 없는 기존 데이터가
  전부 0%가 됩니다.
- **Work Package에는 하위를 둘 수 없습니다**(생성·이동 모두 400). 하위를 두려면 구분을 `SUMMARY`로
  명시적으로 전환해야 합니다. 파생으로 두면 자식 하나를 추가하는 순간 실행 방식과 Backlog 귀속이
  말없이 고아가 됩니다.
- **전환할 때 실행 방식을 지우지 않고 보관합니다.** 그래서 "Summary에 실행 방식 금지"는 *"null이어야
  한다"가 아니라 "바꿀 수 없다"*로 구현되어 있습니다 — 편집 폼이 보관값을 그대로 되돌려 보내므로,
  값이 같으면 변경이 아니라 통과시켜야 합니다. 되돌리지 마세요.
- **상위는 하위 Work Package의 실행 방식을 개수로 요약합니다**(`ExecutionModeSummary`). 손자까지 세며,
  0건인 모드는 빼고 `미지정`은 숨기지 않습니다(배정되지 않은 Work Package가 확인 대상입니다).
  자식이 없는 항목에서는 `null`입니다. 상위가 보관 중인 값은 **요약에 넣지 않습니다.**
- **진행률 집계식은 Step 5에서 실행 방식 기반으로 바뀌었습니다**(아래 "진척 집계" 절). 다만 실행
  방식 미지정과 가중치 없는 가지는 예전 계산을 그대로 씁니다 — 아무것도 지정하지 않은 프로젝트의
  화면 숫자가 변하지 않게 하려는 전환 정책입니다.
- **실행 방식 변경 이력은 값이 실제로 달라질 때만** 남깁니다(`wbs_execution_mode_changes`). 양방향
  (미지정↔지정) 모두 기록해서 두 컬럼이 NULL을 허용합니다. **변경자·사유 컬럼은 두지 않았습니다** —
  로그인이 없고 사유를 받을 화면도 없어 아무도 채우지 않는 값이 됩니다. **이 표는 Step 5에서
  `change_logs`가 흡수했습니다**(V18이 행을 옮긴 뒤 삭제) — 이제 실행 방식 변경도 거기에 쌓입니다.
- **가져오기는 `node_type`을 파일 값 그대로 믿지 않습니다.** 자식이 있으면 파일이 `WORK_PACKAGE`라고
  적어도 `SUMMARY`로 바로잡습니다(`code`를 다시 계산하는 것과 같은 이유). 값이 없는 구형 파일
  (`formatVersion 1`)도 같은 규칙으로 채웁니다.
- **enum 범위를 벗어난 값은 필드명과 가능한 값을 담아 400을 돌려줍니다**
  ([`GlobalExceptionHandler`](backend/src/main/java/com/projectflow/presentation/GlobalExceptionHandler.java)의
  `unknownEnumValue`). 예전에는 Jackson 실패가 "요청 내용을 읽을 수 없습니다"로만 나와 무엇이 틀렸는지
  알 수 없었습니다.

### Backlog 설계상 알아둘 점

WBS(범위)와 Agile(실행)을 잇는 실제 연결 지점입니다. 자세한 근거는
[Step 03 결과 보고서](설계서/agile_design/results/Hybrid_PM_Step03_Result.md).

- **귀속은 상속합니다 — 검증하는 것이 아니라 불가능하게 만듭니다.** 상위가 있는 항목의
  `wbs_item_id`는 상위에서 가져오고, 상위를 재귀속하면 하위 전체가 따라갑니다. 요청이 다른 값을
  지정하면 조용히 덮어쓰지 않고 400으로 거부합니다 — 클라이언트가 "Task를 다른 Work Package로
  옮겼다"고 착각하면 안 됩니다.
- **Backlog는 최하위 Work Package에만 붙습니다.** Summary 귀속은 400입니다. 단 Step 2가 Work Package
  → Summary 전환을 허용하므로 **이미 붙은 뒤에 대상이 Summary가 되는 일은 생깁니다** — 그때는
  `linkedToSummary`로 표시하고 Sprint 대상에서 빼되 막지는 않습니다. **Step 5부터는 살아 있는
  Backlog가 붙은 Work Package의 Summary 전환 자체를 거부**하므로(사용자 결정) 새로 생기지는 않지만,
  그전 데이터에는 남아 있을 수 있어 표시는 유지합니다(대시보드의 "데이터 누락"도 이것을 셉니다).
- **Product Backlog는 정렬된 목록이지 계층 노드가 아닙니다**(설계 §4.1). WBS처럼 코드를 파생하지
  않고, `depth`는 화면 들여쓰기 전용입니다.
- **집계 대상은 Story·Bug만**입니다(`BacklogItemType.aggregated()`). Epic을 그 안의 Story와 함께,
  Task를 그 Story와 함께 세면 같은 일을 두 번 셉니다. Step 5의 집계는 이 한 곳만 물어보면 됩니다.
- **`storyPoint`와 `progressWeight`는 다른 값입니다.** 포인트는 팀의 추정, 가중치는 같은 Work
  Package 안에서의 비중입니다. 같은 단위로 합산하지 마세요(설계 §11.2).
- **보관(`archived_at`)은 상태와 직교합니다.** 보관해도 마지막 상태가 남아야 하고, 상태를 한 칸
  늘리면 Board 상태 전이에 "보관"이 섞여 듭니다.
- **연결된 Backlog가 있는 WBS 항목은 삭제되지 않습니다.** 삭제 범위(자기 + 모든 하위) 전체를 보고
  거부하며, **보관된 항목만 남았으면** 사유(`WBS_ITEM_DELETED`)를 남기고 분리한 뒤 지웁니다.
  `wbs_items.parent_id`가 `ON DELETE CASCADE`라 이 가드가 없으면 상위 하나를 지우는 것으로 실행
  기록이 조용히 사라집니다. **DB 쪽은 `SET NULL`로 두세요** — 마지막 방어선이 데이터를 지우는 쪽이면
  안 됩니다. Backlog 자체의 삭제도 같은 태도입니다(하위가 있으면 거부).
- **순환 검사는 지금 도달할 수 없습니다.** 유형 규칙(Story·Bug의 상위는 Epic뿐, Epic은 상위 없음)이
  이미 순환을 막습니다. 검사는 Epic 중첩이 허용되는 날을 위한 안전장치로 남겨 두었고, 테스트는
  실제로 일어나는 거부(계층 위반)를 검증합니다.
- **캐시 리비전이 둘입니다.** WBS 트리가 연결 Backlog 수를 실어서 Backlog 변경도 WBS를 낡게 만들지만,
  간트는 Backlog를 모릅니다. 그래서 `markBacklogChanged()`를 따로 두고 `wbsCacheKeyFor`에만 넣습니다.
  Backlog 자신의 키에는 **WBS 리비전이 들어갑니다**(행마다 Work Package 코드·실행 방식을 보여주므로).
- **record에 bean 모양의 `isX()`를 두지 마세요.** Jackson이 프로퍼티로 읽어 응답에 새 필드가
  생깁니다 — `BacklogSummary.isEmpty()`가 모든 WBS 노드에 `"empty": false`를 흘렸고 `hasNone()`으로
  바꿔 고쳤습니다.

### Sprint·Board 설계상 알아둘 점

실행 주기입니다. 자세한 근거는
[Step 04 결과 보고서](설계서/agile_design/results/Hybrid_PM_Step04_Result.md).

- **팀 테이블이 없습니다 — 단일 팀 전제입니다.** 그래서 **실행 중인 Sprint는 프로젝트당 하나**이고,
  이 규칙이 "동시에 두 활성 Sprint에 배정하지 않는다"를 보장합니다. 팀을 도입한다면 `teams` +
  `sprints.team_id`를 추가하고 기존 행을 기본 팀으로 백필한 뒤, **이 규칙을 팀 단위로 바꿔야 합니다.**
  잊으면 두 팀이 각자 Sprint를 돌릴 수 없습니다.
- **한 항목의 살아 있는 배정은 열린 Sprint 전체에서 하나**입니다. 계획 중인 다음 Sprint에 미리 넣어
  두는 것도 막습니다 — 지금 하는 일을 다음 계획에도 세면 계획이 두 배가 됩니다.
- **배정 행은 이력입니다.** 제거는 `removed_at`을, 종료는 `outcome`·`points_at_close`를 찍습니다.
  **`(sprint_id, backlog_item_id)`에 UNIQUE를 걸지 마세요** — 설계 §11.3-4는 권하지만, 그러면 뺐다가
  다시 넣을 때 "한 번 빠졌다"가 사라집니다. 살아 있는 배정이 하나라는 제약은 애플리케이션이 지킵니다
  (부분 유니크 인덱스는 PostgreSQL에만 있고 H2에는 없습니다).
- **완료 실적은 정확히 한 Sprint에만 쌓입니다.** 이월된 항목은 원 Sprint에 `CARRIED_OVER`, 다음
  Sprint에 `DONE`으로 남습니다. 종료된 Sprint의 숫자는 그 뒤에 무슨 일이 있어도 움직이지 않습니다.
- **종료는 항목의 상태를 바꾸지 않습니다.** 종료는 Sprint의 진술이고, 일이 Review에 있는지는 항목의
  사정입니다. 이 분리가 이월을 가능하게 합니다.
- **차단(`blocked`)은 상태와 직교합니다.** 칸은 그대로 두고 표시만 하며, 차단된 항목은 완료할 수
  없습니다. 상태를 한 칸 늘리지 마세요 — Board 전이에 "차단"이 섞여 듭니다(보관도 같은 이유로 분리).
- **완료로 가는 전이에는 확인 표시가 필요합니다**([`CompletionCheck`](backend/src/main/java/com/projectflow/domain/CompletionCheck.java)).
  시스템에 Definition of Done이 없으므로 "확인했다"는 사실을 호출자가 밝혀야 합니다. **Board와
  Backlog 폼 양쪽에 적용**되어 있습니다 — 한쪽만 막으면 절차가 장식이 됩니다. 단 *전이*만 검사하므로
  이미 완료인 항목을 다시 저장할 때는 묻지 않습니다.
- **Task를 모두 완료해도 Story는 자동 완료되지 않습니다.** 대신 완료되지 않은 하위 수를 카드와 확인
  대화상자에 실어 사람이 판단합니다. 하위가 남아 있어도 완료는 가능합니다(불필요해진 Task가 정상입니다).
- **재오픈은 `done_at`만 비웁니다.** 종료된 Sprint의 `outcome`은 불변이고, 그 배정은 `reopened`로
  표시됩니다 — 현재 상태와 과거 실적은 다른 사실입니다.
- **진행 중 Sprint에 배정된 Backlog 항목은 보관·삭제할 수 없습니다.** `sprint_items`가 Backlog 항목에
  CASCADE라서 지우면 그 Sprint의 배정 기록까지 사라집니다(WBS 삭제 가드와 같은 이유).
- **캐시 리비전이 셋입니다** (WBS / Backlog / Sprint). Board 이동은 Backlog 상태 변경이라 두 화면이
  함께 낡지만, 간트는 둘 다 모릅니다. 하나로 묶으면 카드를 한 번 옮길 때마다 간트까지 다시 읽습니다.
- **새 도메인 예외를 만들면 `GlobalExceptionHandler`에 함께 등록하세요.** Step 4에서 빠뜨려 Sprint의
  모든 거부가 500으로 나갔고, 서비스 단위 테스트로는 드러나지 않았습니다.

### 진척 집계 설계상 알아둘 점 (Step 5)

[`ProgressCalculator`](backend/src/main/java/com/projectflow/domain/ProgressCalculator.java)가
Work Package의 실행 방식에 따라 네 가지 식으로 계산하고, 상위는 가중치로 올려 접습니다.

| 실행 방식 | 분자 / 분모 |
|---|---|
| `AGILE` | 완료 Story·Bug / 전체 Story·Bug |
| `WATERFALL` | 승인된 체크포인트 가중치 / 전체 체크포인트 가중치 |
| `HYBRID` | Agile × α + Waterfall × (1−α). α는 `wbs_items.agile_ratio` |
| 미지정(`null`) | 입력한 `progress` 그대로 (`MANUAL`) |

- **산정 전(`null`)은 0%가 아닙니다.** 분모가 없으면 `null`을 돌려주고, 부모는 그 자식을 조용히
  빼지 않고 `incompleteChildren`으로 전파합니다. 화면도 막대를 그리지 않고 "산정 전"이라 적습니다.
  이 구분을 없애면 착수 전 프로젝트가 "0% 진행"으로 보이고, 정말 0%인 것과 구별되지 않습니다.
- **전환 정책으로 기존 숫자를 지켰습니다.** 실행 방식 미지정은 `MANUAL`, 가중치가 하나도 없는 가지는
  예전과 같은 leaf 개수 가중 평균(`LEGACY_ROLLUP`)입니다. **아무것도 지정하지 않은 프로젝트의 화면
  숫자는 Step 5 이전과 같습니다.** 되돌리지 마세요.
- **계획 진척은 승인된 Baseline에서만 나옵니다.** 없으면 미산정이고, 시간이 지났다는 것만으로 실제
  진척을 채우지 않습니다. 편차는 **기준선에 든 항목만으로** 양쪽을 계산해 뺍니다 — 범위가 다르면
  뺄 수 없는 두 숫자입니다.
- **`storyPoint`·`progressWeight`·`weight`는 서로 다른 값**입니다. 포인트는 팀의 추정, `progressWeight`는
  Backlog 항목의 비중, `weight`는 WBS 형제 사이의 비중입니다. 같은 단위로 합산하지 마세요.
- **인수(`acceptance_status`)는 진척과 직교**합니다. 실행이 100%여도 인수가 남았으면 완료로 보지
  않습니다(`acceptancePending`).
- **모든 화면이 이 서비스 하나를 읽습니다.** WBS 트리·간트·대시보드가 각자 계산하면 같은 프로젝트에
  세 가지 숫자가 생깁니다. 새 화면을 만들 때도 `ProgressService`를 부르세요.

### 간트의 세 가지 일정과 Sprint 레인 (Step 6-A)

한 행에 위에서부터 **기준 일정 · 현재 계획 · 실적**을 쌓습니다. 행을 셋으로 늘리지 않은 이유는 접어둔
WBS의 이점이 사라지고 같은 업무의 세 일정을 눈으로 잇기 어려워지기 때문입니다.

- **기준선이 없으면 `hasBaseline: false`**를 내려보내고 화면이 "기준 일정 미등록"이라 적습니다.
  **현재 계획을 기준선 자리에 복사하지 마세요** — 그러면 초과를 영원히 못 봅니다.
- **초과 판정은 예상 종료 우선, 없으면 현재 계획**입니다. 예측을 적지 않았다는 것이 "늦지 않는다"는
  뜻은 아닙니다.
- **Sprint 종료일을 `actual_end_date`로 복사하지 않습니다.** Sprint가 끝난 것과 Work Package의
  산출물이 완료된 것은 다른 사실이고, 한 Sprint가 여러 Work Package에 걸칠 수 있습니다.
- **Sprint 레인은 Sprint당 한 줄**입니다. 여러 Work Package에 걸쳐도 반복해 그리지 않습니다 —
  없는 일정과 진척을 만들게 됩니다. 양방향 참조(`sprintIds` / `wbsItemIds`)는 **강조 전용**이며
  어떤 값도 파생시키지 않습니다.
- 차트 폭은 기준·실적·예상·Sprint까지 포함해 잡습니다. 그러지 않으면 계획 밖으로 나간 실적이 잘립니다.

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
  기준으로 판정하는 값이 없습니다. 단 Step 6에서 행마다 Backlog 담당자가 붙어 **Backlog 리비전은
  들어갑니다.**

### RACI 상속 설계상 알아둘 점 (Step 6-B)

[`RaciInheritance`](backend/src/main/java/com/projectflow/domain/RaciInheritance.java)가 조회 시점에
계산합니다. **아무것도 저장하지 않습니다** — WBS 코드·상위 일정과 같은 이유로, 저장하면 항목을 옮길 때
조상의 글자가 하위에 복사본으로 남습니다.

- **역할별로 상속합니다.** 자기 R이 있어도 상위 A는 그대로 옵니다. 자기 글자 하나를 전체 재정의로
  보면 담당자를 적는 순간 단계의 책임자가 조용히 사라집니다.
- **가장 가까운 상위가 이깁니다.** 하위가 같은 역할에 다른 사람을 지정하면 *재정의*이고, 상위의
  글자는 셀에 남기되 취소선으로 무효임을 보입니다. 숨기면 왜 다른지 알 수 없습니다.
- **셀은 자기 글자(`roles`)와 상속 글자(`inherited`)를 나눠 싣습니다.** 상속 글자에는 배정 id가
  없습니다 — 이 행에서 지울 수 없고, 그 글자를 가진 행을 고쳐야 합니다.
- **검증 규칙이 Step 6에서 바뀌었습니다** (의도한 변경):
  - 누락(A·R)은 **상속까지 본 뒤 leaf에만**. 단계의 A를 물려받은 Work Package는 누락이 아닙니다.
  - 책임자 중복은 **그 글자를 실제로 가진 행에**(Summary 포함) 한 번만. 하위마다 되풀이하면 실수
    하나가 leaf 수만큼 불어납니다. **조용히 지우지 않고** 정리 대상으로 알립니다.
- **Backlog 담당자는 RACI 역할이 아닙니다**(`storyAssignees`). 열이 아니라 행 머리의 주석으로
  그립니다 — 열이 되면 RACI 글자와 나란히 서서 역할처럼 읽힙니다. 담당자를 바꾸는 경로와
  `raci_assignments`를 쓰는 경로 사이에 코드가 없습니다.

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
- **RAID 캐시 키는 `(프로젝트, WBS·Backlog·Sprint 리비전, 로컬 날짜)`입니다.** 세 리비전이 모두
  들어가는 이유는 위 연결 때문입니다 — 항목이 WBS 업무·Sprint·Backlog 어디에나 붙고, 등록부가 그
  대상의 이름을 보여주므로 어느 쪽이 이동·개명돼도 낡습니다. 로컬 날짜는 기한 초과가 날짜 기준이라
  필요합니다(표시하는 기준일은 항상 서버의 `referenceDate`).
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

### RAID 복수 연결 설계상 알아둘 점 (Step 6-C)

`raid_items.wbs_item_id` 하나였던 연결이 **`raid_links` 표**가 되었습니다(V21). 같은 위험이 여러
Story·Sprint에 걸려도 원본은 하나로 관리해야 하기 때문입니다.

- **`target_id`에 외래 키가 없습니다.** 세 종류(WBS·Sprint·Backlog)를 한 컬럼으로 가리키기 때문이고,
  대상 존재와 프로젝트 일치는 `RaidService`가 검증합니다. **그래서 참조가 끊어지는 것을 막는 유일한
  장치가 애플리케이션입니다** — 새 삭제 경로를 만들면 `RaidService.detachTargets`를 함께 부르세요.
- **삭제하면 연결만 끊고 항목은 남깁니다.** 예전 `ON DELETE SET NULL`과 같은 태도입니다 — 계획이
  사라진다고 위험 기록까지 사라지면 안 됩니다. **보관은 연결을 건드리지 않습니다**(복구하면 함께
  돌아와야 합니다).
- **수정은 남길 것을 남깁니다.** 전부 지우고 다시 넣으면 살아남은 연결도 새 id와 새 `created_at`을
  받아, 무관한 편집마다 "언제 붙였나"가 오늘로 바뀝니다. 차집합만 지우고 새것만 넣습니다.
- **같은 대상 중복은 애플리케이션에서 400으로 먼저 막습니다.** UNIQUE 위반은 500으로 나가고,
  "이미 연결됨"은 화면이 보여줄 수 있는 말입니다.
- **RAID의 `DEPENDENCY`와 WBS 선후행은 다릅니다.** 전자는 프로젝트 밖에서 받아야 하는 것,
  후자는 안쪽의 순서 제약입니다. 링크는 연관일 뿐 일정 제약이 아닙니다.
- **`formatVersion 5` 이하의 단일 `wbsItemId`도 계속 읽습니다** — `WBS_ITEM` 링크 하나가 됩니다
  (V21이 DB에 한 것과 같습니다). 내보낼 때는 `links`만 쓰고 `wbsItemId`는 비웁니다.

### 대시보드 설계상 알아둘 점 (Step 7)

[`DashboardService`](backend/src/main/java/com/projectflow/application/DashboardService.java)는
**아무것도 계산하지 않습니다.** 진척·간트·Sprint·Backlog·RACI·RAID 서비스를 한 번씩 불러 배치할
뿐입니다.

- **자체 산술을 넣지 마세요.** "WBS·간트·대시보드의 진척이 일치한다"가 완료 기준이고, 대시보드가
  자기 식으로 계산하면 같은 프로젝트에 네 번째 의견이 생깁니다.
- **한 요청, 한 기준일.** 여섯 payload를 서버가 모아 하나의 `referenceDate`와 함께 줍니다.
  브라우저가 여섯 번 부르면 카드마다 다른 "오늘"이 될 수 있습니다.
- **카드당 5건만 싣되 id는 남깁니다.** 대시보드는 "어디를 볼지" 정하는 화면이고, 전체 목록은 각
  화면에 있습니다. 숫자에서 원인 항목으로 갈 수 없으면 카드가 막다른 길이 됩니다.
- **데이터 누락을 별도 카드로 보고합니다**(실행 방식 미지정·가중치 없음·산정 불가·미연결 Backlog).
  각주로 두면 헤드라인만 읽고 지나갑니다. **임의로 채우지 않습니다.**
- **속도 추세는 종료된 Sprint만.** 진행 중인 것은 아직 움직이는 숫자라 Sprint 중간에 보면 매번
  하락으로 읽힙니다(`inProgress`로 따로 싣습니다). 단일 팀이라 계열은 하나이고, **팀이 생기면
  나뉘어야 하며 합산해서는 안 됩니다.**
- **읽기 전용입니다.** 변경 API가 없습니다 — 같은 값을 고치는 경로가 둘이면 검증 규칙도 두 벌이
  됩니다.
- **캐시 키에 모든 리비전과 로컬 날짜가 들어갑니다**(`dashboardCacheKeyFor`). 여섯 화면의 데이터를
  읽으므로 어느 편집도 카드를 움직일 수 있습니다.

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
- **컨트롤은 Material 스타일이지만 팔레트는 앱 것을 씁니다.** 형태(알약·모서리), 고도
  (`--elevation-*`), 상태 레이어(`--state-hover`/`--state-press`)만 Material 방식으로 얹었습니다.
  Material 팔레트를 따로 들이면 다크 모드를 두 벌 관리해야 하고 나머지 화면과도 어긋납니다.
  - **상태 레이어는 배경색 교체가 아니라 겹치는 반투명 층**입니다(`::before`). 그래서 어떤 배경
    위에서도 같은 규칙으로 동작하고, 다크 모드용 hover 색을 따로 정의할 필요가 없습니다.
  - **메뉴는 `body`로 teleport 하고 `position: fixed`** 입니다. 표 행 안에 두면 스크롤·클리핑되는
    조상에 걸려 잘립니다(간트 툴팁이 같은 이유로 같은 방식).
  - **위로 뒤집을 때는 `bottom` 기준으로 잡습니다.** `top`을 트리거 위에 두면 메뉴가 거기서
    아래로 자라 트리거를 덮고 화면 밖으로 흘러내립니다 — 실제로 그렇게 만들었다가 고쳤습니다.
  - `prefers-reduced-motion`에서 애니메이션을 끕니다.
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
캐시 키는 화면마다 다르고, 그 화면이 읽는 것을 모두 담습니다. 간트는
`(프로젝트, WBS·Backlog·Sprint 리비전, 로컬 날짜)`이고 — Step 6부터 Sprint 레인과 공통 진척을 함께
싣기 때문입니다 — 대시보드는 여섯 화면을 읽으므로 같은 조합을 씁니다. 아래 규칙을 지킵니다.

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
| `GET` `POST` | `/api/projects/{projectId}/backlog` | Product Backlog 조회 / 항목 추가 |
| `PUT` `DELETE` | `/api/projects/{projectId}/backlog/{itemId}` | 항목 수정(재귀속 포함) / 삭제 |
| `PUT` | `/api/projects/{projectId}/backlog/{itemId}/archive` | 보관·복구 (`{"archived": true}`) |
| `GET` `POST` | `/api/projects/{projectId}/sprints` | Sprint 목록(+배정·집계) / 생성 |
| `PUT` `DELETE` | `/api/projects/{projectId}/sprints/{sprintId}` | 수정 / 삭제(계획 상태의 빈 Sprint만) |
| `POST` | `/api/projects/{projectId}/sprints/{sprintId}/start` | 시작 (다른 활성 Sprint가 없어야 함) |
| `POST` | `/api/projects/{projectId}/sprints/{sprintId}/close` | 종료 (`carryOverToSprintId`로 미완료 재배정) |
| `POST` `DELETE` | `/api/projects/{projectId}/sprints/{sprintId}/items[/{backlogItemId}]` | 배정 / 제거 |
| `PUT` | `/api/projects/{projectId}/sprints/{sprintId}/board/{backlogItemId}` | Board 이동 (상태·차단·완료 확인) |
| `GET` | `/api/projects/{projectId}/progress` | 공통 진척 집계 (프로젝트·Work Package별, 계획 대비 편차, 범위 비교) |
| `GET` `POST` | `/api/projects/{projectId}/progress/checkpoints` | 승인 체크포인트 목록/추가 (Waterfall·Hybrid의 분모) |
| `PUT` `DELETE` | `/api/projects/{projectId}/progress/checkpoints/{checkpointId}` | 수정 / 삭제 |
| `PUT` | `/api/projects/{projectId}/progress/checkpoints/{checkpointId}/approval` | 승인·승인 취소 |
| `POST` | `/api/projects/{projectId}/progress/baselines` | 기준선 승인 (명시적 행위, 자동 경로 없음) |
| `GET` `POST` | `/api/projects/{projectId}/progress/snapshots` | 보고 스냅샷 조회 / 저장 |
| `GET` | `/api/projects/{projectId}/dashboard` | 대시보드 (다른 조회들을 한 기준일로 모은 것, 읽기 전용) |
| `GET` `POST` | `/api/projects/{projectId}/raid` | RAID 로그 조회 / 항목 추가 (`links`로 WBS·Sprint·Backlog에 복수 연결) |
| `PUT` `DELETE` | `/api/projects/{projectId}/raid/{itemId}` | 항목 수정 / 삭제 |
| `GET` | `/api/projects/{projectId}/export` | 프로젝트 전체를 JSON 한 파일로 내려받기 (attachment) |
| `POST` | `/api/projects/import` | 내보낸 파일로 **새 프로젝트** 생성 (본문 = export 응답 그대로) |

WBS·간트의 모든 변경 API는 부분 응답이 아니라 갱신된 전체 데이터를 반환합니다.

## 다음 단계 (설계서/요구사항_WBS.md 기준)

- 4.3 프로젝트 상태 관리(세부 규칙)
- 8. 진행 관리 — 8.3 지연 판정, 8.4 대시보드 완료. 남은 것은 **8.2 진행률 변경 이력 화면**
  (`change_logs`에 자리는 있으나 보여 주는 화면이 없습니다)
- 3.5 Docker 환경 구성 실제 빌드/검증, 10. 테스트 및 배포
- **PostgreSQL에서 마이그레이션 전체를 한 번 돌려 보기.** 특히 V18(이력 표 흡수)과 V21(RAID 컬럼
  이전)은 데이터를 옮기는 마이그레이션인데 H2로만 확인했습니다
- 일정 기능 확장 후보: 영업일/휴일 달력(지연 일수·기대 진행률이 함께 정확해집니다),
  FS 이외의 관계 종류(SS/FF/SF)
