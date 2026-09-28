# 누구에게 일이 몰려 있는지 본다 — 구성원별 부하

대상 체크아웃: `D:\git\ScheduleMgr` (main `4572a29` "wbs-tree-density 지시서를 done 으로 옮긴다")
모든 줄번호는 그 커밋에서 확인한 값이다.

사용자 요구: **"현재 어떤 사람한테 과도하게 일이 몰려 있는지를 판단하려고 한다."**

---

## 1. 해결할 문제

사람에게 붙는 것이 네 곳에 흩어져 있고, **한 사람 기준으로 모아 보는 자리가 없다.**

| 출처 | 사람에게 붙는 필드 | 지금 볼 수 있는 곳 |
|---|---|---|
| RACI | `raci_assignments` + 상속(`RaciInheritance.resolve`, `:61`) | RACI 화면의 열 하나를 눈으로 훑기 |
| Backlog | `assigneeMemberId`(`BacklogResponse:64`), `storyPoint`(`:67`) | Backlog 목록의 담당자 열 |
| Sprint | Backlog 담당자를 그대로 씀(`SprintResponse:73`) | Board 카드 |
| RAID | `ownerMemberId`(`RaidLogResponse:44`) | RAID 등록부의 소유자 열 |

이 코드베이스는 이 질문을 **행 단위로는 이미 한 번 답했다.**
`RaciMatrixResponse.StoryAssigneeResponse`(`:53-58`)의 주석이 그것이다 — *"@param itemCount how many
Backlog entries they hold here — enough to see **who is loaded** without opening the Backlog
screen."* Work Package 한 줄 안에서 "누가 지고 있나"를 이미 보여 준다. 이번 작업은 그것을
**프로젝트 전체 × 사람 단위로 모으는 것**이다.

### 1-1. 없는 것 — 이것이 설계를 정한다

**(a) 가용 공수(capacity)가 없다.** `ProjectMember`는 `name`·`email`·`position`뿐이다
(`ProjectMember.java:30-49`). WBS에도 공수(MD)가 없고, 가중치 입력마저 "형제 사이의 상대값인데
원가·공수와 묶이지 않아 적을 근거가 없다"는 이유로 없앴다(CLAUDE.md). **그래서 "과부하"를 절대
기준으로 판정할 수 없다** — 5건이 많은지 적은지 말해 줄 분모가 없다.

→ **이번에 capacity 필드를 만들지 않는다**(사용자 결정). 아무도 채우지 않는 칸을 만들면 부하가
전원 "산정 전"이 되고, 그건 진척에서 이미 겪은 실패 방식이다(`WATERFALL`을 기본값으로 두면 안 되는
것과 같은 이유). **할 수 있는 것은 한 프로젝트 안에서의 상대 비교이고, 통각은 사람이 한다.**

**(b) 구성원은 프로젝트 스코프다.** 로그인이 없고 이름은 프로젝트 안에서만 유일하다
(`ProjectMember.java:15-19`의 주석). **여러 프로젝트를 합산하면 거짓이 된다** — 다른 프로젝트의
같은 이름이 같은 사람인지 알 방법이 없다. 실무에서 "일이 몰렸다"는 보통 사람 단위(프로젝트 여러
개에 걸침)인데, 이 앱은 **프로젝트 하나 안에서만** 말할 수 있다. **이 한계를 화면에 적는다**
(3-4). 적지 않으면 사용자가 전사 현황으로 읽는다.

**(c) 팀 테이블이 없다**(단일 팀 전제). 부하를 팀별로 나눌 수 없고, 나눌 필요도 지금은 없다.

---

## 2. 결정된 방향

### 2-1. 부하는 "지금 동시에 몇 개를 맡고 있나"로 센다

공수가 없을 때 가장 정직한 대리 지표다. **기준일에 진행 중인 담당 업무 수**가 주 지표이고,
나머지는 보조 열이다.

건수 총합(R 업무 + Story + RAID를 다 더하기)을 주 지표로 두지 않는다 — 끝난 일과 내년에 할 일을
함께 세어 **항상 무거운 사람**이 생기고, 성질이 다른 셋을 한 숫자로 섞는 것은 CLAUDE.md가
`storyPoint`·`progressWeight`·`weight`를 섞지 말라고 한 것과 같은 종류의 변질이다.

Story Point를 주 지표로 두지도 않는다 — 팀이 직접 추정한 유일한 값이라 매력적이지만 **Agile Work
Package에만 붙는다.** Waterfall·미지정이 섞인 프로젝트에서는 담당자 상당수의 부하가 0으로 보인다.
대신 보조 열로 함께 싣는다.

### 2-2. 절대 임계값을 만들지 않는다

"5건 이상은 빨강" 같은 선을 두지 않는다. 근거가 없고(2-1-a), 한 번 그으면 사용자가 그 선을 사실로
읽는다. 대신 **프로젝트 내 최댓값 대비 막대 + 내림차순 정렬**로 분포만 보여 준다. CLAUDE.md의
태도와 같다 — *"데이터 누락을 별도 카드로 보고합니다. 임의로 채우지 않습니다."*

### 2-3. 계산은 `WorkloadService`가, 배치는 `DashboardService`가

`DashboardService`는 **아무것도 계산하지 않는다**는 규칙이 그 파일 머리에 적혀 있다(`:13-19`:
*"a dashboard with its own arithmetic is a fourth opinion"*). 그 규칙을 지키려면 부하 집계의 주인이
따로 있어야 한다.

- `WorkloadService`(application): 네 서비스의 응답을 받아 사람별로 모은다. `DashboardService`가
  이미 그 넷을 부르고 있으므로(`:105-110`) **응답을 넘겨받는 형태**로 두어 같은 조회를 두 번 하지
  않는다.
- `WorkloadAssessor`(domain, 순수): 모아진 숫자에서 판정할 것(정렬, 최댓값 대비 비율, 미배정 건수)을
  맡는다. DTO를 모르는 단순 record만 받는다 — 도메인이 application dto에 의존하면 레이어가 뒤집힌다
  (`RaidAssessor`·`DelayCalculator`와 같은 자리·같은 성격).

**새 엔드포인트를 만들지 않는다.** 부하는 대시보드 payload에 실어 보낸다 — "한 요청, 한 기준일"이
그 화면의 규칙이고(`DashboardResponse:28-29`), 브라우저가 따로 부르면 카드마다 다른 "오늘"이 될 수
있다. 다른 화면이 부하를 읽게 되는 날 컨트롤러를 추가하면 된다.

### 2-4. 전원을 싣는다 — `LIST_LIMIT = 5`를 적용하지 않는다

`DashboardService.LIST_LIMIT`(`:63`)은 **주의 목록**(지연 업무·RAID처럼 길어질 수 있는 것)에 대한
규칙이다. 구성원 수는 팀 크기로 묶여 있어 전원을 실어도 payload가 커지지 않고, 탭에서 전체를 보려면
어차피 전원이 필요하다. **요약 카드가 상위 5명만 그리고, 탭이 같은 배열을 전부 그린다.**

### 2-5. 화면은 요약 카드 + 세 번째 탭 (사용자 결정)

- 요약 카드 `WorkloadCard`: 상위 5명. 기존 카드들과 같은 결로 들어간다
- 자세히: `/dashboard?tab=workload` — 구성원 × 출처 표
- **새 최상위 메뉴를 만들지 않는다.** 설계서 §2.2의 일곱 개를 유지하려고 진척도 탭으로 넣었다
  (`DashboardView.vue:30-33`의 주석)
- RACI 화면의 열 합계는 이번에 하지 않는다(6장) — RACI만 세어 Backlog·RAID를 못 본다

---

## 3. 작업

### 3-1. `backend/.../domain/WorkloadAssessor.java` (새 파일, 순수)

```java
/** 한 사람의 부하 — 어느 서비스의 DTO도 모른다. */
public record MemberLoad(
        Long memberId, String memberName,
        int activeCount,        // 기준일에 진행 중인 담당(R) 업무 수  ← 주 지표
        int delayedCount,       // 그중 DELAYED
        int atRiskCount,        // 그중 AT_RISK
        int openStoryCount,     // 미완료 담당 Story·Bug
        Integer storyPoints,    // 그 Story·Bug의 포인트 합. 아무것도 없으면 null
        int openRaidCount       // 열린 소유 RAID
) {}

public static List<MemberLoad> rank(List<MemberLoad> loads)   // activeCount 내림차순, 동점은 이름
public static int maxActive(List<MemberLoad> loads)           // 막대의 분모. 0이면 막대를 안 그린다
```

**`null`과 `0`을 구분한다.** `storyPoints`는 담당 Story가 하나도 없으면 `null`(= 산정할 것이 없음)
이고 포인트가 안 적힌 Story만 있으면 `0`이 아니라 `null`이다 — 진척의 "산정 전은 0%가 아니다"와 같은
규칙이다.

spec `WorkloadAssessorTest`: 정렬(동점 포함), `maxActive`가 0일 때, `storyPoints`의 `null`/`0` 구분.

### 3-2. `backend/.../application/WorkloadService.java` (새 파일)

입력은 이미 조회된 네 payload다(같은 조회를 반복하지 않는다):

```java
public List<MemberLoad> summarize(RaciMatrixResponse raci, GanttResponse gantt,
                                  BacklogResponse backlog, RaidLogResponse raid)
```

집계 규칙 — **여기가 이 작업의 핵심이고, 각 줄이 기존 규칙의 재사용이다**:

1. **열(사람)은 `raci.members()`에서 온다.** 부하 0인 사람도 행에 남긴다 — 비어 있다는 것이 답이다.
2. **`activeCount`는 `R`을 가진 업무만 센다.** 셀의 `roles`와 `inherited` **양쪽**을 본다
   (`RaciCellResponse:72-78`) — 단계에서 물려받은 담당도 그 사람의 일이다(`RaciInheritance` 설계).
3. **leaf만 센다.** `gantt`의 `summary == true`(`GanttResponse:72`)는 제외한다. Summary는 하위를
   이미 반영하므로 함께 세면 중복 집계다 — 지연 건수가 leaf만 세는 것과 **같은 규칙**이다.
4. **"진행 중"은 `startDate <= referenceDate <= endDate`이고 완료되지 않은 것**이다. 완료 판정은
   새로 만들지 말고 `delayStatus`(`:78`)를 읽는다 — `COMPLETED`면 제외. 일정이 없는 항목은 제외한다
   (일정 없는 항목은 제약에 참여하지 않는다는 기존 규칙과 같다).
5. **`delayedCount`·`atRiskCount`는 그 `activeCount` 안에서 센다.** 별도 모집단을 만들면 두 숫자가
   서로 설명하지 못한다.
6. **Backlog는 Story·Bug만**(`BacklogItemType.aggregated()`). Epic을 그 안의 Story와 함께, Task를
   그 Story와 함께 세면 같은 일을 두 번 센다. 보관된 항목과 완료 상태는 제외한다.
7. **RAID는 `CLOSED`가 아닌 것만**, 소유자가 그 사람인 것.
8. **`referenceDate`는 인자로 받은 것 하나만 쓴다.** `LocalDate.now()`를 다시 읽지 않는다 —
   커밋 조회에서 화면마다 "오늘"이 갈리는 것을 막는 기존 규칙이다.

또 하나 싣는다: **`unassignedActiveCount`** — 진행 중인 leaf 중 담당(R)이 상속까지 봐도 없는 것의 수.
부하가 0인 사람이 여럿인데 실은 아무도 배정되지 않은 상황을 구분하려면 이 숫자가 필요하다. RACI
위반 자체는 `ControlCard`가 이미 보고하므로(`missingResponsibleCount`) **중복 판정을 만들지 말고
그 값을 그대로 읽어 쓰거나, 세는 모집단이 다르면(진행 중인 것만) 그 차이를 주석에 적는다.**

spec `WorkloadServiceTest`: 규칙 2(상속 포함)·3(leaf만)·4(기간·완료)·6(Story·Bug만) 각각 하나씩,
그리고 구성원이 없을 때 빈 목록.

### 3-3. `DashboardResponse` / `DashboardService`

- `DashboardResponse`(`:31-42`)에 `WorkloadCard workload`를 더한다.
  `WorkloadCard(List<MemberLoad> members, int unassignedActiveCount)`
- `DashboardService`: 필드로 `WorkloadService`를 받고, `getDashboard`(`:101-124`)에서
  이미 가진 네 payload를 넘겨 결과를 배치한다. **`DashboardService` 안에 산술을 넣지 않는다**(2-3).
- `LIST_LIMIT`을 적용하지 않는다(2-4).

### 3-4. 프론트엔드

**`features/dashboard/WorkloadCard.vue`** (새 파일) — 기존 카드와 같은 문법(`DashCard`·`BarList`·
`StatChip`을 재사용한다. 새 카드 문법을 짓지 않는다).

- 상위 5명, 이름 + 동시 진행 수 막대(분모는 `maxActive`) + 지연 배지
- 카드 아래 각주 두 줄:
  - **"이 프로젝트 안에서의 비교입니다"** — (b)의 한계. 문구로 적지 않으면 전사 현황으로 읽힌다
  - **"담당자 미지정 진행 업무 N건"** — 0이 아닐 때만
- **절대 임계 색을 쓰지 않는다.** 지연 배지는 기존 `--status-delayed`/`--status-at-risk`를 그대로
  쓰고, 부하 자체에는 색을 얹지 않는다(2-2)

**`features/dashboard/WorkloadPanel.vue`** (새 파일) — 탭 본문. 구성원 × 출처 표:

| 구성원 | 동시 진행 | 지연 | 위험 | 미완료 Story | 포인트 | 열린 RAID |

- 표 규칙은 기존과 같다 — `.table-scroll`로 감싸고 `th, td`에 `nowrap`
- 열 머리글을 눌러 정렬한다면 `raidFilter.ts`처럼 **순수 함수로 빼서** vitest로 고정한다
- 값이 없는 칸은 `-`로, `0`과 구분한다(3-1의 `null` 규칙)
- 행에서 그 사람의 항목으로 가는 링크를 둔다 — 숫자에서 원인으로 갈 수 없으면 막다른 길이다
  (`DashboardResponse:26-27`의 규칙). 지금 갈 수 있는 곳은 `/raci`(열 강조는 없으니 화면만),
  `/backlog`, `/raid`다. **담당자로 필터된 화면이 없으므로 링크는 화면까지만 데려간다는 것을
  문구로 밝힌다** — 없는 기능을 암시하지 않는다

**`views/DashboardView.vue`** — 탭을 셋으로 늘린다.
`type Tab = 'summary' | 'progress' | 'workload'`(`:34`), 쿼리 매핑(`:35`·`:40`·`:45-47`)에 한 갈래를
더한다. `watch(tab, …)`(`:56`)의 "요약으로 돌아올 때 다시 확인한다"는 그대로 유효하다 — 부하 탭은
읽기 전용이라 무효화를 만들지 않는다.

**캐시**: `dashboardCacheKeyFor`는 모든 리비전 + 로컬 날짜를 이미 담고 있다. **새 키도, 새 리비전도,
새 요청도 없다.**

---

## 4. 건드리지 않는 것

| 무엇 | 이유 |
|---|---|
| `ProjectMember` 스키마 | capacity를 만들지 않는다(사용자 결정, 2-1-a). 마이그레이션 없음 |
| `DashboardService`의 "계산하지 않는다" 규칙 | 집계는 `WorkloadService`가 주인이다 |
| `LIST_LIMIT = 5`(`:63`) | 주의 목록의 규칙이다. 구성원 목록에 적용하지 않는다(2-4) |
| RACI 화면 | 열 합계는 이번에 하지 않는다(6장) |
| `raci_assignments`·`RaciInheritance` | 읽기만 한다. 담당자를 바꾸는 경로는 RACI 화면 하나로 남는다 |
| 지연 판정·`DelayStatus` | 완료·지연 판정을 새로 만들지 않고 `delayStatus`를 읽는다(3-2 규칙 4) |
| `ControlCard`의 RACI 위반 수 | 부하 카드가 같은 위반을 다시 세지 않는다(3-2) |
| 새 엔드포인트 | 대시보드 payload에 싣는다(2-3) |
| 다른 프로젝트와의 합산 | 구성원이 프로젝트 스코프라 거짓이 된다(2-1-b). **하지 말라고 주석에 적는다** |

---

## 5. 확인 방법

```bash
cd backend && ./gradlew test
cd frontend && npm test && npm run build
```

수동 확인 — 구성원 4명 이상, WBS에 진행 중·지연·완료 업무가 섞여 있고, Backlog 담당자와 RAID
소유자가 지정된 프로젝트로:

1. `/dashboard` 요약 → 부하 카드에 상위 5명이 동시 진행 수 내림차순으로 나온다
2. 막대의 분모가 **그 프로젝트의 최댓값**이다(1위가 가득 찬 막대)
3. **Summary 업무에 R을 배정해 본다** → 부하가 **늘지 않는다**(leaf만 센다, 규칙 3)
4. **단계(상위)에만 R이 있는 사람** → 그 하위의 진행 중 leaf가 부하로 잡힌다(상속, 규칙 2)
5. 담당 업무를 진행률 100%로 바꾼다 → 동시 진행 수에서 빠진다(규칙 4)
6. 종료일이 지난 담당 업무 → `지연` 열에 잡히고 동시 진행 수에도 남아 있다(모집단이 같다, 규칙 5)
7. 일정이 비어 있는 담당 업무 → 어느 열에도 잡히지 않는다(규칙 4)
8. Epic과 Task에 담당자를 지정한다 → **미완료 Story 수가 늘지 않는다**(Story·Bug만, 규칙 6)
9. RAID 항목을 `CLOSED`로 바꾼다 → 열린 RAID 수에서 빠진다
10. 담당 Story가 없는 사람 → 포인트 칸이 `-`다(`0`이 아니다)
11. 아무도 배정되지 않은 진행 중 업무를 만든다 → 각주의 "담당자 미지정 진행 업무 N건"이 늘어난다
12. **각주에 "이 프로젝트 안에서의 비교"가 적혀 있다**
13. 구성원이 0명인 프로젝트 → 카드가 "구성원이 없습니다"로 조용히 빈다(오류가 아니다)
14. `/dashboard?tab=workload` → 전원이 보인다(5명 초과분 포함). 요약 카드와 **같은 숫자**다
15. 탭을 요약↔부하↔진척으로 왕복한다 → 숫자가 흔들리지 않고 기준일이 하나다
16. RACI에서 배정을 바꾸고 대시보드로 돌아온다 → 부하가 갱신된다(`raciRev`가 대시보드 키에 있다)
17. 커밋 조회 모드 → 그 시점의 부하가 보이고 쓰기 진입점이 없다
18. 라이트·다크 양쪽에서 막대와 배지가 읽힌다

---

## 6. 이번에 하지 않는 것 (후속 후보)

- **RACI 화면의 열별 합계 한 줄.** 그 화면은 이미 (업무 × 사람) 표라 밑에 합계를 붙이는 것이
  자연스럽고 값도 있다. 다만 RACI 배정만 세어 Backlog·RAID 부하는 보지 못한다 — 대시보드 쪽이
  먼저다
- **담당자로 필터된 화면.** Backlog·RAID·WBS에 "이 사람의 항목만" 필터가 있으면 카드의 숫자에서
  바로 원인으로 갈 수 있다. WBS에는 이미 담당자 필터가 있다(`wbs-tree-filter`) — Backlog·RAID로
  넓히는 것이 다음이다
- **가용 공수(capacity)와 절대 판정.** 업무별 공수(MD)가 먼저 있어야 분자·분모의 단위가 맞는다.
  CLAUDE.md의 후속 후보 "공수(MD) 컬럼"과 한 묶음이다
- **여러 프로젝트 합산.** 구성원을 프로젝트 밖의 사람으로 승격(전역 `people` 테이블 + 프로젝트별
  참조)해야 한다. 로그인이 없는 지금 구조에서는 이름이 같다는 것만으로 합칠 수 없다
- **기간별 부하 추이.** "다음 달에 누가 몰리나"는 기준일을 옮겨 같은 계산을 돌리면 나온다. 지금은
  "오늘"만 답한다
- **Sprint 단위 부하(속도 대비).** 종료된 Sprint의 1인당 완료 포인트를 보면 추정이 가능하지만,
  단일 팀 전제·팀 속도 계열이 하나라는 제약과 함께 봐야 한다
