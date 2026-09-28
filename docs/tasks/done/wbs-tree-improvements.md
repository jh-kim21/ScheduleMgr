# WBS 트리 화면 개선 4건

사용자 요구 네 가지를 한 번에 다룬다. 전부 WBS 트리 화면(`/wbs`)에서 드러나는 문제다.

| # | 요구 | Phase | 성격 |
|---|---|---|---|
| 1 | Work Package 가중치 입력 삭제 (무의미) | **A** | 백엔드 + 프론트, 계산 포함 |
| 2 | 체크포인트 입력 UI가 불편함 | **B** | 프론트만 |
| 3 | Work Package 담당자를 WBS 트리에도 표시 | **C** | 백엔드 + 프론트, 새 컬럼 없음 |
| 4 | 업무 분야 Tag 표시 (Service/Web 등) | **D** | 마이그레이션 포함, 가장 큼 |

**네 Phase는 독립이다.** A만 하고 멈춰도 되고 순서를 바꿔도 된다. 다만 A와 B는 같은 파일
(`CheckpointList.vue`)의 가중치 입력을 건드리므로 **A → B 순서가 편하다.** C와 D는 둘 다 WBS 트리에
열을 추가하므로 **연달아 하는 편이 낫다**(`<thead>`를 두 번 고치지 않는다).

---

## 1. 해결할 문제

### 1-A. 수동 가중치는 적을 근거가 없는 값이다

`wbs_items.weight`는 형제 사이의 **상대값**이다. 합이 100이어야 한다는 제약이 없고
(`V15__add_wbs_progress_basis.sql:18-19`는 `weight >= 0`만 본다), 계산은 자기 합으로 나눈다.
그래서 `[1,1,2]`·`[10,10,20]`·`[50,50,100]`이 **완전히 같은 결과**를 낸다.

표준(EVM)에서 가중치가 힘을 갖는 것은 **원가·공수와 묶이기 때문**인데 `wbs_items`에는 둘 다 없다
(전체 컬럼: 날짜·진행률·가중치·α·인수 상태·실행 방식·구분·실적/예상일). 파생시킬 원천 없이
결과값만 입력받고 있다. 그리고 크기 차이는 이미 `leafCount`가 반영한다 — 잘 쪼갠 WBS는 leaf가
균질해지므로(8/80 규칙) 개수가 곧 크기다.

**실증 — 호스팅 서버 `http://192.168.60.70:5151`, 프로젝트 AEGIS(id=34), 기준일 2026-09-16:**

| 대상 | 개수 | 가중치 입력 |
|---|---|---|
| Work Package | 10 | **0건** (전부 `null`) |
| 체크포인트 | 8 | **0건** (전부 `null`) |

한 번도 쓰이지 않았다. 크기가 다른 곳은 가중치가 아니라 **체크포인트로 쪼개서** 맞췄다 —
`6 Email 전송 기능 개선`(21일)은 `UI 기능 개발 / DB 개발 / Backend 개발 / 테스트` 4개로.

### 1-A-2. 그런데 부분 입력이 프로젝트 진척을 조용히 파괴한다 (입력을 없애도 남는 위험)

`ProgressCalculator.rollUp()`은 직계 형제 중 **하나라도** 가중치가 있으면(`:184`의 `anyWeight`),
가중치 없는 형제를 `continue`로 **평균에서 제외**한다(`:199-205`).

```java
if (anyWeight) {
    if (declared == null) { incompleteWeights = true; continue; }
    weight = declared;
} else {
    weight = leafCount(child);
}
```

살아남은 자식이 하나뿐이면 분자·분모의 `w`가 약분되어 **그 자식의 진척이 곧 가지 전체의 진척**이
된다. AEGIS(현재 프로젝트 진척 38.75%) 기준:

| 시나리오 | 현행 결과 |
|---|---|
| 최상위에 항목 하나를 가중치와 함께 추가 | `1`~`7`이 전부 탈락 → **프로젝트 진척 = 그 항목 하나의 값** |
| 위와 같되 그 항목이 Agile·Story 0건 | `percent==null`이라 그것도 탈락 → `weightSum==0`(`:217-221`) → **프로젝트 진척이 통째로 "산정 전"** |
| 가중치를 `0` 하나만 입력 | `anyWeight`는 `!=null` 검사라 켜짐 → **그 가지 전체가 산정 전** |

**입력 UI를 없애도 이 경로는 남는다** — 가져오기(`ImportService`), 커밋 복원, 기존 데이터, 직접 DB
조작으로 값이 들어올 수 있다. 그래서 계산 쪽 안전망이 함께 필요하다.

그리고 이 사고는 화면에 드러나지 않는다.

1. `ProgressCalculator.java:266-268` — `incomplete()`가 `incompleteWeights || incompleteChildren`로
   OR 합쳐 `WbsNodeResponse.java:141`에서 `progressIncomplete` 한 boolean으로 나간다. 밖에서는
   두 사건을 구분할 수 없다.
2. `WbsTree.vue:524` — 뱃지가 "불완전" 하나이고 `title`도 "일부 하위가 산정 전이거나 가중치가
   없습니다"로 둘을 묶는다. AEGIS의 노드 `1`은 **이미** `progressIncomplete: true`라(`1.1`이
   NOT_ESTIMABLE) 사고가 나도 뱃지가 달라지지 않는다.
3. `ProgressPanel.vue:207-210` — 같은 "또는" 문구가 하나 더 있다.
4. `DashboardService.java:377-384` — `WEIGHT_MISSING`이 `weight()==null`인 Work Package를 **전부**
   센다. AEGIS는 지금 "10"으로 떠 있는데 완전히 정상 상태다. **안전한 상태와 위험한 상태가 같은
   숫자를 낸다.**

### 1-A-3. 같은 `null`이 네 곳에서 다르게 읽힌다

| 어디 | `weight`가 `null`이면 | 근거 |
|---|---|---|
| 체크포인트 (Waterfall 분모) | **1로 취급** | `ProgressCalculator.java:140`, `weightOf` `:239-241` |
| Backlog `progress_weight` (Agile 분모) | **1로 취급** | `:122`, `:239-241` |
| 기준선 (계획 진척) | **1로 취급** | `ProgressService.java:234` |
| **WBS 롤업** | **제외** | `:184, 199-205` |

넷 중 셋이 `1` 폴백이고 WBS 롤업만 혼자 제외한다. 게다가 `baseline_items.weight`는
`wbs_items.weight`를 그대로 복사한 값이다(`ProgressBasisService.java:242`) — **같은 컬럼의 같은
`null`이 실제 진척에서는 "빼라", 계획 진척에서는 "1로 쳐라"를 뜻한다.**

### 1-B. 체크포인트 입력이 한 건마다 폼을 다시 열게 한다

`CheckpointList.vue`의 `submit()`은 성공하면 `closeForm()`을 부른다. 그래서 `6 Email`의 체크포인트
4개를 넣으려면 `＋ 체크포인트 추가`를 **네 번** 눌러야 한다.

**CLAUDE.md는 이미 반대 규칙을 확립해 놨다** (RAID 절):

> **수정 저장 후에는 닫고, 추가 후에는 열어 둡니다** — 여러 건을 연달아 기록하는 것이 흔하고,
> 아래 표에 새 행이 나타나는 것이 이미 확인 신호입니다.

체크포인트만 이 규칙을 따르지 않는다. 그 밖에 확인된 마찰:

| 증상 | 근거 |
|---|---|
| 승인할 때마다 승인자 이름을 처음부터 입력 | `startApprove()`가 `approver.value = ''`로 매번 비운다. AEGIS에 "김재학"·"이승하"가 반복해 들어가 있다 |
| 제목 입력에서 Enter로 저장이 안 됨 | 승인 입력에는 `@keydown.enter="confirmApprove"`가 있는데(`:186`) 제목 입력(`:213`)에는 없다. 비대칭 |
| 거의 안 쓰는 가중치 칸이 탭 순서 한가운데 | `:214` — 제목과 완료조건 사이. Phase A로 사라진다 |

### 1-C. WBS 트리에 담당자가 없다

`wbs_items`에 담당자 컬럼이 없다(위 컬럼 목록 확인). 사람이 WBS 항목에 붙는 유일한 경로는
`raci_assignments`이고, `RaciRole.RESPONSIBLE`이 이미 **"실무를 수행하는 사람"** 으로 정의돼 있다.
`RaciInheritance`가 상위 단계에서 물려받는 것까지 조회 시점에 계산한다.

그런데 **그 정보가 RACI 화면에만 있다.** 일정·진척을 보는 화면에서 "이거 누가 하지"를 알 수 없어
화면을 옮겨야 한다.

### 1-D. 업무 분야를 표시할 자리가 없다

태그·분류 개념이 코드베이스에 **전혀 없다** (`db/migration`에 `tag`/`category` 0건; 도메인의 grep
매치는 "percen**tag**e" 오탐이었다). Service/Web 같은 분야로 훑거나 필터링할 방법이 없다.

---

## 2. 결정된 방향

### 사용자가 정한 것

- **담당자는 RACI의 Responsible을 재사용한다** — 새 컬럼을 두지 않는다. `wbs_items`에 담당자를
  따로 두면 RACI의 R과 둘 중 무엇이 진짜인지 모호해지고, 같은 사실을 두 곳에서 관리하게 된다.
  **WBS 트리는 읽기만 한다. 바꾸려면 RACI 화면으로 간다.**
- **Tag는 프로젝트별 태그 목록 + Work Package에 복수 연결**한다. `project_members`와 같은 패턴
  (프로젝트 스코프, 이름 유일)이라 이 코드베이스에 자연스럽고, 자유 문자열이면 `Web`/`web`/`WEB`이
  섞여 필터가 깨진다. 한 업무가 두 분야에 걸치는 것이 정상이므로 단일 분류로 두지 않는다.

### 하지 않는 것과 이유

| 하지 않음 | 이유 |
|---|---|
| `wbs_items.weight`·`acceptance_checkpoints.weight` **컬럼 삭제** | `baseline_items`·커밋 `raw_payload`·내보내기 JSON에 이미 박혀 있다. 빼는 비용이 남기는 비용보다 크다 |
| `anyWeight` 분기·`ROLLUP` basis **제거** | 가져오기·복원으로 값이 들어올 수 있고, 들어오면 여전히 올바르게 동작해야 한다. **지우면 가중치가 하나도 없을 때도 균등이 되어 `leafCount` 가중이 사라지고 기존 프로젝트 숫자가 바뀐다** |
| 가중치를 **등급(상/중/하)·피보나치로 교체** | 척도를 무엇으로 하든 "적을 근거"가 생기지 않는다. 크기는 쪼개서 맞춘다 |
| 체크포인트를 **진짜 WBS 자식으로** 만들기 | CLAUDE.md가 경고한 그대로 — "Work Package에는 하위를 둘 수 없다"는 규칙, `WbsNode.summary()`의 자식 유무 판정(체크포인트 하나를 추가하는 순간 그 Work Package의 일정이 하위 파생값이 되어 입력한 날짜가 사라진다), 진척 계산 이중화가 깨진다 |
| WBS 폼에서 **RACI 역할을 배정**하게 하기 | 쓰기 경로가 둘이 되면 `RaciValidator` 규칙을 두 벌 관리하게 된다. 읽기 전용으로 두고 RACI 화면 링크만 단다 |
| Tag를 **Summary에도** 붙이기 | 분야는 실제 작업의 속성이다. Summary는 하위 Tag를 **요약해서 보여주기만** 한다(`ExecutionModeSummary`와 같은 방식) |

### 요구 1의 범위 해석

사용자 표현은 "최상위 워크패키지 가중치 입력 기능 삭제"였다. 이 지시서는 이를
**WBS 항목의 가중치 입력 전부(레벨 무관) + 체크포인트 가중치 입력**으로 읽었다. 근거는 "무의미하기
때문"이라는 사유가 레벨과 무관하게 성립하고(위 1-A), 최상위만 남기면 나머지 레벨에서 부분 입력
사고가 그대로 남기 때문이다. **이 해석이 틀렸으면 착수 전에 사용자에게 확인할 것.**

---

## 3. 작업

### Phase A — 가중치 입력 제거와 개수 기반 통일

#### A-1. `backend/.../domain/ProgressCalculator.java` — 폴백 (안전망)

`rollUp()` 안(`:199-205`):

```java
// after — 나머지 셋(체크포인트·Backlog·기준선)과 같은 규칙
if (anyWeight) {
    weight = weightOf(declared);   // null → 1
} else {
    weight = leafCount(child);
}
```

- **`incompleteWeights`를 더 이상 세우지 않는다.** 폴백이 정직한 값을 내므로 "불완전"이 아니다.
  필드와 `incomplete()`는 그대로 두되 항상 `false`가 된다(커밋 payload 호환).
- `anyWeight` 분기는 **유지한다.** 가중치가 하나도 없으면 여전히 `leafCount`(`LEGACY_ROLLUP`) —
  전환 정책이 이것으로 지켜진다.
- `weightSum == 0` 가드(`:217-221`)는 그대로. 선언값이 전부 `0`인 경우가 남는다.

#### A-2. `backend/src/test/.../ProgressCalculatorTest.java`

`partialWeightsAreReported`(`:175-184`)가 반대 결정을 고정하고 있다. 이름·`@DisplayName`·주석의
판단 근거까지 함께 바꾼다.

```java
@DisplayName("일부 자식만 가중치가 있으면 나머지를 1로 채운다 — 빼지 않는다")
void partialWeightsFallBackToOne() {
    WbsItem parent = summary("단계");
    manual(parent, "가중치 있음", 100, 10);
    manual(parent, "가중치 없음", 0, null);
    // (10×100 + 1×0) / 11
    assertThat(percentOf(parent)).isEqualTo(<실제 계산값>);
    assertThat(resultOf(parent).incompleteWeights()).isFalse();
}
```

> **기대값을 여기서 베끼지 말 것.** `1000/11 = 90.909…`이며 정밀도 처리는 기존
> `keepsPrecision`(`:222`) 테스트와 같은 방식으로 맞춘다. 실제로 돌려 확인하고 적는다.

`legacyWeightingWhenNoWeights`(`:158-172`)는 **그대로 통과해야 한다**(가중치 0건이라 영향 없음).
통과하지 않으면 `anyWeight` 분기를 잘못 건드린 것이다.

#### A-3. `frontend/src/features/wbs/WbsForm.vue` — 가중치 입력 제거

| 위치 | 조치 |
|---|---|
| `:184-186` | 가중치 `<label>`·`<input>` **삭제**. 같은 `<div class="row">`의 `Hybrid 비중 α`·`인수 상태`는 남긴다 |
| `:224-227` | 고정 안내문 **삭제** ("가중치를 비워 두면 … 하위 평균으로 집계됩니다"는 형제에 값이 있으면 거짓이었다) |
| `:229-239` | 제안 안내문 **삭제** |
| `:77-89` | `weightSuggestion`·`weightPlaceholder` computed **삭제** |
| `:99` | `weightEmpty` computed **삭제** |
| `:16` | `suggestWeight` import **삭제** |
| `:21-22` | `siblings` prop **삭제** |
| `:38` | `empty`의 `weight: null`은 **남긴다** (아래 주의) |

> **주의 — 폼은 저장 시 전체를 보낸다.** 가중치 입력을 없앤다고 `weight`를 payload에서 빼면
> **기존에 값이 있던 항목이 `null`로 덮인다.** `wbsFormMapping.ts`가 노드에서 읽어 온 값을 그대로
> 되돌려 보내는 현재 동작을 유지할 것. 커밋 `da96ebe`와 같은 종류의 사고다.

#### A-4. `weightSuggestion.ts` + `weightSuggestion.spec.ts` — 삭제

`WbsForm`이 유일한 사용처다. 삭제 전 `grep -rn "suggestWeight\|weightSuggestion" frontend/src`로 확인.

#### A-5. `frontend/src/views/WbsView.vue`

`:120-130`의 `siblings` computed와 `:261`의 `:siblings="siblings"` 바인딩 **삭제**.
`findParent`가 다른 곳에서 안 쓰이면 함께 정리한다.

#### A-6. `frontend/src/features/progress/CheckpointList.vue` — 가중치 제거

| 위치 | 조치 |
|---|---|
| `:159` | `<span class="cp-weight">가중치 {{ cp.weight ?? '균등' }}</span>` **삭제**. `[30,30,40,null]`에서 `null`은 균등이 아니라 1이라 이 표시 자체가 거짓이었다 |
| `:214` | 가중치 `<input>` **삭제** |
| `:266` | `.cp-weight` 스타일 정리 |
| `:27` | prop 주석의 "제목·가중치·완료조건·승인 상태"에서 가중치 제거 |

`checkpointForm.ts` — `CheckpointDraft.weight`, `emptyDraft`, `draftFrom`, `toCheckpointInput`에서
`weight` 제거. `normalizeWeight`가 다른 곳에서 안 쓰이면 함께 삭제. `checkpointForm.spec.ts`
픽스처도 따라간다.

> **API의 `weight` 필드는 남긴다.** 프론트가 안 보내면 서버가 `null`로 받고 `weightOf`가 1로
> 폴백해 전부 균등이 된다. 가져오기·복원으로 들어온 값은 계속 존중된다.

#### A-7. `backend/.../application/DashboardService.java`

`:377-384`의 `WEIGHT_MISSING` 블록 **삭제**. 묻지 않는 값을 "누락"으로 보고하면 안 된다.
`DataGap` 레코드와 다른 gap 종류는 그대로 둔다.

프론트는 손댈 필요 없다 — `GapsCard.vue`가 `v-for="gap in data.gaps"`로 일반 렌더한다.
`dashboardFixture.ts` 등 테스트 픽스처에 `WEIGHT_MISSING`이 있으면 정리한다.

#### A-8. 문구 두 곳 — "또는"을 없앤다

`incompleteWeights`가 더 이상 참이 되지 않으므로 사유가 하나뿐이다.

| 파일 | after |
|---|---|
| `WbsTree.vue:524` | `title="일부 하위가 아직 산정 전입니다."` |
| `ProgressPanel.vue:207-210` | "일부 하위가 아직 산정 전이라 집계가 불완전합니다. 위 숫자는 셀 수 있는 부분만을 말합니다." |

뱃지 글자("불완전")는 그대로 둔다.

---

### Phase B — 체크포인트 입력 UI

전부 `frontend/src/features/progress/CheckpointList.vue` 안이다. **API·스키마 변경 없음.**

#### B-1. 추가 후에는 폼을 열어 둔다 (수정은 닫는다)

`submit()`이 성공했을 때:

- **추가 모드**(`editingId === null`) → `closeForm()` 대신 `draft`만 비우고 폼 유지 +
  제목 입력에 다시 포커스(`focusTitleInput()` 재사용)
- **수정 모드** → 지금처럼 닫는다

CLAUDE.md의 RAID 규칙과 같은 근거다 — 여러 건을 연달아 기록하는 것이 흔하고, 위 목록에 새 행이
나타나는 것이 이미 확인 신호다. **CLAUDE.md의 체크포인트 관련 서술에도 이 규칙을 한 줄 추가할 것.**

#### B-2. 승인자 이름을 기억한다

`startApprove()`의 `approver.value = ''`를 없애고, 마지막으로 입력한 승인자를 **모듈 스코프
`ref`** 에 둔다(`useProgress`가 모듈 스코프 상태를 쓰는 것과 같은 방식). 한 세션에서 여러 건을
승인하는 것이 주 사용 패턴이므로 이것으로 충분하다.

- 값이 있으면 입력칸에 미리 채우되 **전체 선택 상태로 포커스**해서 다른 사람이 바로 덮어쓸 수 있게 한다
- `localStorage`까지는 두지 않는다(§6 후속)

#### B-3. 제목 입력에서 Enter/Esc

`:213`의 제목 `<input>`에 승인 입력(`:186`)과 같은 핸들러를 단다.

```html
@keydown.enter="isSubmittable(draft) && submit()"
@keydown.esc="closeForm"
```

완료조건 입력에도 같은 핸들러를 달아 두 칸 어디서든 Enter로 저장되게 한다.

#### B-4. 빈 상태 문구

A-6으로 가중치가 사라지면 폼이 `제목 / 완료조건 / [추가] [취소]` 두 칸이 된다. 한 줄에 들어가므로
`.cp-form`의 레이아웃을 확인하고 필요하면 정리한다.

---

### Phase C — WBS 트리에 담당자 표시

#### C-1. 백엔드 — `WbsService`가 RACI를 읽는다

`WbsTreeResponse`를 만들 때 `RaciAssignmentRepository`와 `ProjectMemberRepository`를 읽고
`RaciInheritance`로 유효 담당자를 계산한다. **RACI 화면과 같은 함수를 쓴다** — 두 화면이 다른
담당자를 말하면 안 된다.

`WbsNodeResponse`에 추가:

```java
List<MemberRef> responsible,           // 이 행에 직접 배정된 RESPONSIBLE
List<MemberRef> responsibleInherited   // 상위 단계에서 물려받은 것
```

- 두 목록을 **나눠서 싣는다.** RACI 셀이 `roles`/`inherited`를 나누는 것과 같은 이유 —
  상속된 이름은 이 행에서 지울 수 없고, 그 글자를 가진 행을 고쳐야 한다.
- `MemberRef`는 `(memberId, name)`이면 충분하다. 트리는 배정 id를 쓰지 않는다.
- **`ACCOUNTABLE`은 싣지 않는다.** 요구는 "담당자"이고, 열을 둘로 늘리면 트리가 RACI 매트릭스를
  흉내 내기 시작한다. 필요해지면 그때 추가한다.

> **레이어 주의:** `WbsService`가 RACI를 읽게 되지만 `RaciService`를 부르지는 않는다
> (application → application 호출은 `DashboardService`만 하는 예외다). 리포지토리와 도메인
> (`RaciInheritance`)을 직접 쓴다.

#### C-2. 캐시 무효화 — `raciRev`를 새로 만든다

`frontend/src/stores/scheduleCache.ts`에 **RACI 리비전이 없다**(현재 `revision`(wbs)·`backlogRev`·
`sprintRev`·`memberRev` 넷뿐). WBS 트리가 담당자를 보여주는 순간 **RACI 배정 변경이 WBS를 낡게
만든다.**

1. `raciRev` + `markRaciChanged()` 추가
2. `useRaci`의 모든 변경(배정·해제)에서 `markRaciChanged()` 호출
3. `wbsCacheKeyFor`(`:89-90`)에 **`memberRev`와 `raciRev`를 함께** 넣는다 — 이름이 바뀌어도,
   배정이 바뀌어도 낡는다
4. `raciCacheKeyFor`에도 `raciRev`를 넣을지 확인한다(모든 변경 API가 매트릭스 전체를 반환하므로
   자기 화면에는 불필요할 수 있다 — 불필요하면 넣지 않는다)

> **CLAUDE.md를 함께 고칠 것.** 지금 이렇게 적혀 있다: *"구성원 이름을 보여주지 않는
> WBS·간트·Progress에는 넣지 않습니다."* WBS는 이제 보여주므로 이 문장이 거짓이 된다.

#### C-3. 프론트

- `wbsApi.ts`의 `WbsNode`에 두 필드 추가
- `WbsTree.vue` `<thead>`(`:405-414`)에 `담당자` 열 추가 → **9칸이 10칸이 된다.**
  `<tr class="checkpoint-row">`의 `colspan="9"`(`:586`)도 함께 고쳐야 한다. **빠뜨리면 체크포인트
  서랍이 표를 깨뜨린다**
- 자기 담당자는 그대로, 상속된 담당자는 **흐리게 + `title`로 "상위 단계에서 물려받음"**
- 여러 명이면 쉼표로 잇되 `.cell-clip`으로 폭 상한을 두고 `title`에 전체를 넣는다
- 아무도 없으면 `-` (Summary·Work Package 모두)
- 셀을 눌러 `/raci`로 가는 링크를 둔다(읽기 전용임을 동선으로 알린다)

---

### Phase D — 업무 분야 Tag

#### D-1. 마이그레이션 `V23__create_wbs_tags_tables.sql`

```sql
CREATE TABLE wbs_tags (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    project_id BIGINT NOT NULL,
    name VARCHAR(50) NOT NULL,
    color VARCHAR(20),
    sort_order INT NOT NULL,
    CONSTRAINT fk_wbs_tag_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT uq_wbs_tag_name UNIQUE (project_id, name)
);

CREATE TABLE wbs_item_tags (
    wbs_item_id BIGINT NOT NULL,
    tag_id BIGINT NOT NULL,
    PRIMARY KEY (wbs_item_id, tag_id),
    CONSTRAINT fk_wbs_item_tag_item FOREIGN KEY (wbs_item_id) REFERENCES wbs_items (id) ON DELETE CASCADE,
    CONSTRAINT fk_wbs_item_tag_tag  FOREIGN KEY (tag_id) REFERENCES wbs_tags (id) ON DELETE CASCADE
);
```

- **이식 가능한 문법만 쓴다** (`BIGINT GENERATED BY DEFAULT AS IDENTITY`, 표준 `FOREIGN KEY`/
  `UNIQUE`). H2·PostgreSQL 양쪽에서 돌아야 한다
- **연결은 양쪽 다 `CASCADE`** — WBS 항목이나 태그가 사라지면 연결만 사라진다. 여기서는 기록을
  남길 것이 없다(RAID 링크와 다른 점: 태그는 그 자체로 이력이 아니다)
- `color`는 선택. 없으면 화면이 이름 해시로 정한다(대시보드 부록 A-6과 같은 규칙 — **랜덤이면
  다시 그릴 때마다 바뀐다**)

#### D-2. 백엔드

| 계층 | 할 일 |
|---|---|
| `domain/` | `WbsTag` 엔티티, `WbsTagRepository` 포트, `WbsItemTagRepository` 포트 |
| `infrastructure/persistence/` | JPA 구현체 (기존 어댑터 패턴 그대로) |
| `application/` | `WbsTagService` — 목록·생성·수정·삭제, 항목에 연결/해제 |
| `presentation/` | `GET POST /api/projects/{id}/wbs/tags`, `PUT DELETE .../tags/{tagId}` |

- **항목의 태그는 WBS 수정 API에서 함께 받는다** (`PUT /wbs/{itemId}`의 `tagIds`). 별도 엔드포인트를
  두면 폼 저장이 두 번 나간다
- **수정은 남길 것을 남긴다** — 전부 지우고 다시 넣지 말고 차집합만 처리한다(RAID 링크와 같은 이유)
- 다른 프로젝트의 태그 id가 오면 **400으로 거부**한다
- 새 도메인 예외를 만들면 **`GlobalExceptionHandler`에 함께 등록한다** (Step 4에서 빠뜨려 Sprint의
  모든 거부가 500으로 나갔다)

`WbsNodeResponse`에 추가:

```java
List<TagRef> tags,          // 이 행에 직접 붙은 태그 (Work Package만)
List<TagRef> tagSummary     // Summary: 하위에 붙은 태그의 합집합 (자식 없으면 null)
```

`ExecutionModeSummary`와 같은 방식이다 — 상위는 하위를 요약해서 보여주고, 자기 값을 갖지 않는다.

#### D-3. 내보내기·가져오기·커밋

- `ProjectExportResponse`에 태그 마스터와 항목별 연결을 싣는다
- `ImportService`가 **태그 id를 재매핑**한다 (프로젝트·구성원과 같은 방식). 파일의 id는 그것을
  만든 설치본의 것이다
- **`formatVersion`을 올린다.** 가져오기가 파일보다 낮은 버전이면 거부한다
- 커밋은 `WbsService` 응답을 그대로 박제하므로 자동으로 따라온다

#### D-4. 프론트

- `WbsTree.vue` `<thead>`에 `분야` 열 추가 → **Phase C와 합쳐 11칸이 된다. `colspan`을 한 번만
  고치도록 C와 D를 연달아 할 것**
- 태그는 알약(칩)으로 그린다. 색은 `color` 또는 이름 해시. **컴포넌트 스코프에 하드코딩 색을 두지
  말고 `style.css` 토큰으로** (다크 모드에서 그 자리만 라이트로 남는다)
- `WbsForm.vue`에 태그 다중 선택 추가
- 태그 마스터 관리 UI: **프로젝트 화면의 `구성원` 버튼 옆에 `분야` 버튼**을 둔다. 구성원 관리와
  같은 이유로 — 태그는 프로젝트 스코프 개념이라 WBS에 종속될 이유가 없다. `MemberEditor`가 자기
  제목을 계산해 `ModalDialog`로 스스로를 감싸는 패턴을 그대로 따른다
- **커밋 조회 중에는 잠근다** — 새 쓰기 진입점은 전부 `readOnly` 파생값 하나를 본다

---

## 4. 건드리지 않는 것

| 대상 | 이유 |
|---|---|
| `acceptance_checkpoints` 테이블 구조 | 화면만 바꾼다. 진짜 WBS 자식으로 만들면 일정·진척 판정이 깨진다 |
| `wbs_items.weight` / `acceptance_checkpoints.weight` 컬럼 | 기준선·커밋·내보내기에 박혀 있다 |
| `agileProgress`·`waterfallProgress`·`hybridProgress` | 이미 `weightOf` 폴백을 쓴다 |
| `ProgressService.plannedProgress` | 이미 `1` 폴백이다(`:234`) |
| `backlog_items.progress_weight` 입력 | 이미 `1` 폴백이라 안전하다. 별건 |
| RACI 화면의 배정 UI | Phase C는 **읽기만** 한다 |
| 대시보드 (`DashboardView.vue`, `features/dashboard/**`) | 백엔드 `DashboardService`만 건드리고 `GapsCard.vue`는 일반 렌더라 수정이 필요 없다 |

> **작업 전 확인:** 착수 시점(`aef29eb`)에 대시보드 리디자인이 **커밋되지 않은 채** 작업 트리에
> 있었다(`DashboardView.vue` 1156줄 변경, 신규 23개). 겹치는 파일은 없지만, 시작 전에 `git status`를
> 보고 그쪽을 커밋·분리할지 사용자에게 확인할 것.

---

## 5. 확인 방법

```bash
cd backend  && ./gradlew test
cd frontend && npm run build && npm test
```

### Phase A — **기존 숫자가 하나도 바뀌지 않는 것이 검증이다**

- [ ] AEGIS(가중치 0건)의 진척이 작업 전과 동일하다 — 프로젝트 **38.75%**, `1`=42.5, `1.2`=42.5,
      `2`=50.0, `3`=0.0, `6`=25.0, `7`=50.0
- [ ] 가중치가 **일부만** 있는 가지를 손으로 만들어(가져오기 JSON) 넣었을 때 빠지는 자식 없이
      균등으로 채워진다
- [ ] 최상위 항목 하나에 가중치를 넣어도 프로젝트 진척이 **그 항목 값으로 바뀌지 않는다**
- [ ] WBS 폼에 가중치 입력이 없고, **저장해도 기존 `weight` 값이 지워지지 않는다**
- [ ] 대시보드에서 "가중치가 없는 Work Package" 카드가 사라졌다

### Phase B

- [ ] 체크포인트를 **연달아 4개** 추가할 때 폼이 닫히지 않고 제목 칸에 포커스가 돌아온다
- [ ] 수정 저장 후에는 폼이 닫힌다
- [ ] 두 번째 승인부터 승인자 이름이 미리 채워져 있고, 전체 선택 상태라 바로 덮어쓸 수 있다
- [ ] 제목 칸에서 Enter로 저장되고 Esc로 닫힌다

### Phase C

- [ ] WBS 트리에 담당자가 보이고, **RACI 화면이 말하는 것과 같다**(상속 포함)
- [ ] 상속된 담당자가 흐리게 구분된다
- [ ] RACI에서 배정을 바꾸고 WBS로 오면 **다시 읽는다**(`raciRev`)
- [ ] 구성원 이름을 바꾸고 WBS로 오면 다시 읽는다(`memberRev`)
- [ ] **체크포인트를 펼쳐도 표가 깨지지 않는다** (`colspan`)

### Phase D

- [ ] 태그를 만들고 Work Package에 여러 개 붙이면 트리에 칩으로 보인다
- [ ] Summary 행에 하위 태그가 요약돼 보인다
- [ ] 같은 이름의 태그를 두 번 만들면 거부된다(400, 500이 아님)
- [ ] 다른 프로젝트의 태그 id를 보내면 400
- [ ] 태그를 지우면 연결만 사라지고 WBS 항목은 남는다
- [ ] 내보내기 → 가져오기 왕복에서 태그가 살아남고 **id가 재매핑된다**
- [ ] 다크 모드에서 칩이 읽힌다(**하드코딩 색 0건**)
- [ ] 커밋 조회 중에는 태그를 바꿀 수 없다

---

## 6. 이번에 하지 않는 것 (후속 후보)

**6-1. 체크포인트를 트리의 자식 행으로 그린다.**
CLAUDE.md는 *"화면에서만 Work Package의 자식처럼 그립니다"* 라고 적어 놨는데 실제로는 9칸을 통으로
먹는 서랍이다(`WbsTree.vue:584-596` + `CheckpointList.vue:155-157`의 `<ul>`). 계산으로 보면 이미
같은 구조다 — Waterfall 진척 `Σ(승인 가중치)/Σ(전체)`는 자식 진척이 0 또는 100인 `rollUp`과 같은
식이다. 즉 체크포인트는 이미 "0/100 판정을 받는 자식"이다. Phase B로 입력 마찰을 먼저 없애고,
행 렌더는 별건으로 다룬다(`CheckpointList`에 `layout` prop을 두되 편집 로직은 한 벌로 유지).

**6-2. 체크포인트에 일정을 준다.**
AEGIS의 체크포인트 제목이 `- 테스트 10/1 ~ 2`다 — **날짜 칸이 없어 제목에 적어 넣었다.**
`acceptance_checkpoints`에 `start_date`/`end_date`가 필요하고, 그러면 간트에 그릴지까지 이어진다.

**6-3. 계층 vs 평면 — 같은 가중치가 두 계산에서 다른 몫을 뜻한다.**
실제 진척(`rollUp`)은 **직계 형제 사이** 상대값인데 계획 진척(`ProgressService.plannedProgress:
219-238`)은 트리를 타지 않고 **프로젝트 전체 Work Package를 평면으로** 가중한다(`:198-200` 주석이
인정하고 있다). Summary의 가중치는 평면 쪽에서 통째로 무시된다(`:220-222`). **Phase A로 왜곡 폭은
줄어든다** — 가중치가 안 들어오면 양쪽 다 균등이 된다.

**6-4. `MANUAL`(실행 방식 미지정) 정리.**
AEGIS의 Work Package 10개 중 5개가 미지정이고 진행률이 손입력이다 — `1.2.1`(100)·`1.2.2`(30)·
`1.2.3`(30)·`1.2.4`(10)·`7`(50). 실무가 기피하는 `% complete` 방식이고("90% 증후군") 프로젝트
진척의 절반을 만든다. **가중치를 아무리 정교하게 해도 그 위에 얹히는 숫자가 감이면 의미가 없다.**

**6-5. Tag로 필터·집계.** 열에 표시만 하고 끝낸다. "Web 분야 진척 몇 %"는 다음 단계다.

**6-6. 승인자를 `localStorage`에 남긴다.** Phase B-2는 세션 내 기억까지만 한다.

**6-7. 공수(MD) 컬럼.** 표준 EVM에 가장 가깝고 가중치를 파생값으로 되돌릴 수 있다.

**6-8. WBS를 균질하게 쪼개는 운영 원칙(8/80)을 안내 문서에 남긴다.**
가중치를 없애면 분해 균질성이 유일한 보정 수단이 된다. AEGIS 기준 `6`(21일)·`3.1`(34일)·
`7`(45일)이 8/80을 넘는다.

---

## 부록. 함정

1. **`weight`를 payload에서 빼는 것** — 폼이 전체를 보내므로 기존 값이 `null`로 덮인다(A-3).
2. **`incompleteWeights`를 계속 세우는 것** — 폴백이 정직한 값을 내므로 "불완전"이 아니다.
   계속 세우면 A-8의 문구가 다시 거짓이 된다.
3. **`anyWeight` 분기까지 지우는 것** — 지우면 가중치가 하나도 없을 때도 균등이 되어 `leafCount`
   가중이 사라지고 **기존 프로젝트의 숫자가 바뀐다.** 전환 정책이 깨진다.
4. **`colspan="9"`를 안 고치는 것** — Phase C·D로 열이 11칸이 되면 체크포인트 서랍이 표를 깨뜨린다.
5. **WBS 폼에서 RACI를 배정하게 만드는 것** — 쓰기 경로가 둘이 되면 검증 규칙이 두 벌이 된다.
6. **`raciRev` 없이 담당자만 싣는 것** — RACI에서 배정을 바꿔도 WBS가 옛 담당자를 계속 보여준다.
7. **태그 색을 랜덤으로 정하는 것** — 다시 그릴 때마다 바뀐다. 이름 해시로 고정한다.
8. **태그 수정에서 전부 지우고 다시 넣는 것** — 살아남은 연결도 새 행이 된다. 차집합만 처리한다.
9. **새 도메인 예외를 `GlobalExceptionHandler`에 등록하지 않는 것** — 모든 거부가 500으로 나가고
   서비스 단위 테스트로는 드러나지 않는다.
10. **`record`에 bean 모양의 `isX()`를 두는 것** — Jackson이 프로퍼티로 읽어 응답에 필드가 샌다
    (`BacklogSummary.isEmpty()`가 모든 WBS 노드에 `"empty": false`를 흘렸다).
