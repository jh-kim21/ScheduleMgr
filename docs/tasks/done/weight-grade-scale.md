# 가중치를 자유 입력 숫자에서 5단계 등급 선택으로 바꾼다

> **대상 체크아웃**: `D:/Git/ScheduleMgr` · 브랜치 **`main`** · HEAD `6ddde45`.
> `6ddde45` 가 `ProgressCalculator` 의 WBS 롤업 폴백을 `evenFallback` → `unitPerLeaf` 로 바꿔 둔
> 상태이고, 아래 줄번호는 전부 그 상태에서 확인한 값입니다.
>
> 이 문서를 처음 쓸 때는 `progress/weight-leaf-scaling` 브랜치였는데, 그 뒤 사용자가 `main` 에
> fast-forward 병합하고 푸시했습니다(`git reflog`: `merge progress/weight-leaf-scaling`).
> **HEAD 는 그대로라 줄번호는 전부 유효합니다.** 작업 브랜치만 다시 정하면 됩니다.

가중치를 적는 **세 곳 전부**를 `<input type="number">`에서 **1/2/3/5/8 다섯 등급 `<select>`**로
바꾼다 — 체크포인트, Backlog 진척 가중치, 그리고 **WBS 형제 가중치**(지금은 입력 화면 자체가 없다).
등급마다 판단 기준 문장을 붙여 "무엇을 적어야 하나"에 답을 준다.

- **스키마 변경 없음, 마이그레이션 없음, API 필드 변경 없음.** 컬럼은 계속 `INT`다.
- 백엔드 변경은 **한 줄**이다 — `ProgressCalculator.weightOf`의 미입력 폴백 `1` → `3`.
  **`unitPerLeaf`는 건드리지 않는다.**
- **기존 백엔드 테스트는 전부 통과한다**(§5에 전수 확인). 프론트엔드는 `WbsForm.spec.ts`의
  11개가 "입력칸이 없다"를 고정하고 있어 **뒤집어야 한다**(§3-F).
- 어휘는 세 곳이 공유하지만 **`null`의 뜻은 둘**이다. 그 비대칭이 이 작업의 핵심이다(§2-c).

---

## 0. 이 문서의 내력 — 두 지시서를 합친 것이다

처음에는 **체크포인트·Backlog만** 다루는 지시서였고, 거기 §2-f·§6-2가 WBS 형제 가중치를
*"사용자 결정으로 없앤 것이라 되살리지 않는다"* 로 명시했다. 그 뒤 사용자가 **되살리기로
결정**하면서 같은 이름의 두 번째 계획이 생겼고, 둘을 합친 것이 이 문서다.

바뀐 결정을 여기 못박아 둔다 — 옛 문장을 그대로 믿고 작업하면 안 된다.

| 옛 결정 | 지금 결정 |
|---|---|
| WBS 형제 가중치 입력은 되살리지 않는다 | **되살린다.** WBS 항목 수정 폼에 등급 `<select>`를 둔다 |
| 척도는 체크포인트·Backlog 전용 | **세 곳이 같은 척도를 쓴다** — 한 앱에 어휘가 둘이면 `3`이 어디선 `보통`이고 어디선 아무 뜻도 없게 된다 |
| `ProgressPanel.vue:262`·WBS CSV는 범위 밖 | **범위 안.** 같은 라벨로 읽혀야 한다 |

되살리는 이유는 CLAUDE.md가 적어 둔 삭제 사유의 뒷부분이 **절반만 참**이기 때문이다.

> `wbs_items.weight`는 형제 사이의 상대값인데 원가·공수와 묶이지 않아 적을 근거가 없고,
> **크기 차이는 이미 `leafCount`가 반영합니다.**

앞부분("적을 근거가 없다")은 이 작업의 **기준 문장**이 해소한다. 뒷부분은 Summary끼리 비교할 때만
성립한다 — **`leafCount`는 구조이지 규모가 아니다.** Work Package는 1일짜리든 3개월짜리든 leaf
하나다. 형제가 전부 leaf인 가지에서는 `leafCount`가 전원 1이라 크기 정보를 **전혀** 싣지 못한다.
그 자리가 등급이 메울 곳이다.

---

## 1. 해결할 문제

### 1-a. 가중치에 단위가 없다

`backend/src/main/resources/db/migration/V15__add_wbs_progress_basis.sql:18-19`와
`V16__create_acceptance_checkpoints_table.sql:28`은 `weight >= 0`만 본다. 합 제약이 없으므로
`30`에 절대적 의미가 없고, 화면이 주는 것은 `type="number" min="0"` 칸 하나뿐이다.

```html
<!-- frontend/src/features/progress/CheckpointList.vue:284-292 -->
<input v-model.number="draft.weight" type="number" min="0" class="cp-weight-input" placeholder="가중치" ... />

<!-- frontend/src/features/backlog/BacklogForm.vue:255 -->
<input v-model.number="form.progressWeight" type="number" min="0" placeholder="비중" />
```

**무엇을 적어야 할지 알려 주는 것이 하나도 없다.** 이것이 사용자가 말한 "기준이 모호하다"이고,
`docs/tasks/done/progress-weight-fallback.md` §6-4가 이미 후속 과제로 적어 둔 항목이다.

### 1-b. WBS 형제 가중치는 아예 적을 수가 없다

| 경로 | 상태 |
|---|---|
| `WbsForm.vue` | 입력칸 삭제됨. `WbsForm.spec.ts:78-170`의 11개 테스트가 **부재를 고정** |
| 진척 탭 | 인라인 편집기 삭제됨. `ProgressPanel.vue:262`는 `{{ wp.weight ?? '-' }}` 읽기 전용 |
| `PUT .../progress/work-packages/{id}/basis` | 프론트엔드에 **호출자 0건** (계약으로만 남음) |
| `PUT /wbs/{itemId}` | 폼이 **받은 값을 그대로 되돌려 보낼 뿐** — 새 값을 만들지 못함 |
| JSON 가져오기 | 값을 복원하지만 **이 앱의 내보내기 결과는 전부 `null`** |

그래서 `ProgressCalculator.rollUp:195`의 `anyWeight == true` 분기는 **이 앱에서 만들고 편집한
프로젝트에서 도달 불가**다. 이 브랜치가 방금 고친 `unitPerLeaf`가 실행될 일이 없다.

남은 것은 **항상 `-`인 `가중치` 열**(`ProgressPanel.vue:238·262`)과, 그 열이 왜 비어 있는지를
설명하는 다섯 줄짜리 안내(`:223-229`), 그리고 `WbsForm.vue:259`의 "보관 중" 안내뿐이다.
**일어날 수 없는 규칙을 설명하는 자리만 셋 남아 있다.**

### 1-c. 가중치가 쓰이는 네 곳

| # | 대상 | 입력 위치 | 집계에서의 역할 | 미입력 시 | 이번 범위 |
|---|---|---|---|---|---|
| 1 | `acceptance_checkpoints.weight` | `CheckpointList.vue:285` | Waterfall·Hybrid **분모** | `1` (`weightOf`) | **✅ 등급 + 폴백 3** |
| 2 | `backlog_items.progress_weight` | `BacklogForm.vue:255` | Agile **분모** | `1` (같은 함수) | **✅ 등급 + 폴백 3** |
| 3 | `wbs_items.weight` | **없음** | 형제 롤업 | `unitPerLeaf × leafCount` | **✅ 등급만. 폴백은 그대로** |
| 4 | `baseline_items.weight` | 없음 (#3의 복사본) | 계획 진척 | `1` (`ProgressService.java:234`) | ❌ 범위 밖 |

**#1·#2와 #3은 폴백 함수가 다르다.** 전자는 `weightOf`(상수 폴백), 후자는 `unitPerLeaf`(선언값이
암시하는 leaf당 비중). 이 작업은 **`weightOf`만** 건드린다.

\#4는 #3의 복사본이라 함께 남는다 — 여기만 3으로 바꾸면 같은 컬럼의 같은 `null`이 실제 진척과
계획 진척에서 또 다른 몫을 뜻하게 된다.

### 1-d. 미입력(`null`)이 피보나치 척도의 최하단과 겹친다 — #1·#2에 한해

`backend/src/main/java/com/projectflow/domain/ProgressCalculator.java:245-248`

```java
/** 미입력 가중치는 균등(1)으로 본다 — "가중치를 아직 안 넣었다"는 "비중이 같다"의 흔한 표현이다. */
private static double weightOf(Integer weight) {
    return weight == null ? 1 : weight;
}
```

척도가 `1/2/3/5/8`이면 `1`은 **아주 작음**이다. 그대로 두면 `미지정 == 아주 작음`이 되어,
새 항목의 기본값을 `보통(3)`으로 둘 수 없다:

> 체크포인트가 `[설계(미지정), 개발(미지정), 테스트(미지정)]`(전부 1, 균등)인 Work Package에
> `인수`를 기본값 `보통(3)`으로 추가하면, **사용자가 고르지도 않았는데 인수가 나머지의 3배 몫**을
> 갖는다. 순전히 이번 변경 때문에 생기는 왜곡이다.

**#3에는 이 문제가 없다.** `unitPerLeaf`는 상수가 아니라 형제의 선언값에서 단위를 뽑으므로
척도의 최하단과 겹칠 자리가 없다. 그래서 #3의 폴백은 **손대지 않는다.**

---

## 2. 결정된 방향

### 2-a. 등급은 **입력 어휘**이고, 저장·계산은 계속 정수다

컬럼을 문자열 enum으로 바꾸지 **않는다.** 가중치를 숫자로 전제하는 곳이 이미 넷이다:

| 어디 | 무엇을 하나 |
|---|---|
| `application/ProgressBasisService.java:288-289` | 가중치를 **합산**한다 (`scope_weight_total`) |
| `application/ProgressService.java:289-293` | 기준선과 **수치 비교**해 `"1.2 화면 (10 → 20)"`을 만든다 |
| `application/dto/ProjectExportResponse.java:114·132·163·219` | `Integer weight` / `Integer progressWeight` |
| 커밋 `raw_payload`·`computed_payload` | 과거 커밋에 이미 숫자가 박제돼 있다 |

문자열로 바꾸면 이 넷이 전부 깨지고 `formatVersion`도 올려야 한다. 없앨 모호함은 *숫자 자체*가
아니라 *"무엇을 적어야 하나"*이고, 그건 **등급마다 기준 문장을 붙이는 것**으로 해결된다.

`RaidLevel.java:4-7`이 같은 패턴의 선례다 — `LOW(1) MEDIUM(2) HIGH(3)`에 `weight()`를 달아 두고
계산은 정수로 한다.

### 2-b. 척도는 5단계 피보나치, **세 곳이 공유한다** (사용자 결정)

| 등급 | 값 | 기준 문장 (#1·#2 — 한 Work Package 안) | 기준 문장 (#3 — 형제 사이) |
|---|---|---|---|
| 아주 큼 | `8` | 이 업무의 절반 이상 | 이 단계의 절반 이상을 차지한다 |
| 큼 | `5` | 큰 덩어리 하나 | 형제 중 가장 무거운 축이다 |
| 보통 | `3` | 평범한 한 단계 | 형제와 비슷한 크기다 |
| 작음 | `2` | 짧게 끝나는 단계 | 형제보다 가볍게 끝난다 |
| 아주 작음 | `1` | 형식적 확인 | 거의 형식적이다 |

최대 비율 **8 : 1**. 3단계(3:1)로는 "개발이 인수의 10배"를 말할 수 없어서 사람이 다시 감으로
적게 된다. Story Point·플래닝 포커와 같은 어휘라 이 앱의 Agile 쪽과도 결이 맞는다.

**값과 라벨은 한 배열에서 나온다.** 기준 문장만 용도별로 둘이다 — 같은 `보통(3)`인데 한쪽은
"한 Work Package 안의 체크포인트 중 평범한 것", 다른 쪽은 "형제 항목과 비슷한 크기"라 문장이
같을 수 없다. **값·라벨을 두 벌로 나누지는 말 것** — 그러면 어휘가 다시 갈라진다.

> **8배까지만 표현된다.** 1일짜리와 3개월짜리가 형제로 나란히 있으면 8:1로는 모자라다. 그런
> 격차는 보통 Summary 계층이 이미 갈라 놓으므로 이번엔 감수하고, 실제로 부족하면 §6-6으로 다룬다.

**기준 문장은 장식이 아니라 이 작업의 본체다.** 드롭다운만 만들고 기준을 빼면 모호함이 그대로
남고, #3은 "적을 근거가 없다"는 삭제 사유가 되살아난다.

### 2-c. `null`의 뜻이 둘이다 — 이 작업에서 가장 중요한 항목

**어휘는 공유하지만 미입력의 의미는 공유하지 않는다.**

| | #1·#2 체크포인트·Backlog | #3 WBS 형제 |
|---|---|---|
| 폴백 함수 | `weightOf` — **상수** | `unitPerLeaf × leafCount` — **형제 크기 비례** |
| 미입력의 계산값 | `1` → **`3`** (§2-d) | 선언 형제에서 뽑은 단위 × 자기 가지 크기 |
| 폼이 `null`을 여는 법 | `보통(3)`으로 preselect → 저장 시 실체화(§2-f) | **`미지정` 그대로. 실체화 금지** |
| 표시 라벨 | `보통` | **`미지정`** |

> **#3에 `weightForForm`·`weightLabel`을 그대로 쓰면 사고가 난다.**
> - `weightForForm(null) === 3`이라 **제목만 고치려는 저장이 `null`을 `3`으로 박제**한다.
>   `3 ≠ unitPerLeaf × leafCount`이므로 **그 프로젝트의 진척 숫자가 즉시 바뀐다.** 커밋
>   `da96ebe`와 같은 종류의 사고이고, 이쪽이 더 나쁘다 — 값이 지워지는 게 아니라 **없던 값이
>   생기면서 형제 전원의 몫이 재계산**된다.
> - `weightLabel(null) === '보통'`은 #3에서 **거짓**이다. 미입력 WBS 항목의 몫은 3이 아니다.
>
> 그래서 §3-B가 함수를 **용도별로 나눠서** 준다. 이름이 비슷하다고 돌려쓰지 말 것.

### 2-d. #1·#2의 미입력은 **보통(3)** 으로 계산한다 (사용자 결정)

`weightOf`의 폴백을 `1` → `3`으로 고친다. 그러면 `미지정 ≡ 보통`이 되어 §1-d의 왜곡이 사라지고,
새 항목의 기본값을 `보통`으로 두어도 안전하다.

**전부 미지정인 집합은 화면 숫자가 바뀌지 않는다** — 가중치는 상대값이라 `1:1:1`과 `3:3:3`이
같은 비율이다. 바뀌는 것은 *일부만 선언된 혼합 집합*뿐이다(`{null, 10}`: `1:10` → `3:10`).
사용자가 이 위험을 알고 선택했다.

**라이브 실측 (2026-09-30, `192.168.60.70:5151` AEGIS, GET만).** `done/progress-weight-fallback.md`가
적어 둔 수치(체크포인트 8 · Work Package 10 · 선언 0건)는 **낡았다.** 지금은 Work Package 15 ·
체크포인트 27이고 **5개 WP가 가중치를 선언**했다 — `1.2.2 [5,6]` · `1.2.3 [5,5]` · `1.2.4 [1,9]` ·
`2.2 [3,7]` · `7.1 [2,8]`. 그런데도 결론은 그대로이고 근거가 더 강해졌다:

```
혼합 집합 0 · 전부 미입력 10 · 전부 선언 5 · 체크포인트 없는 WP 0
Backlog 항목 0            → agileProgress 미실행
wbs_items.weight 선언 0/20 → unitPerLeaf·anyWeight 미실행 (basis = LEGACY_ROLLUP)
```

폴백 `1→3`이 값을 바꾸는 유일한 조건이 혼합 집합인데 **하나도 없다.** 전부 미입력인 10개는 비율이
보존되고, 전부 선언한 5개는 `weightOf` 폴백을 아예 타지 않는다. 그래서 **프로젝트 진척
`52.14141414141414`는 배포 후에도 비트 단위로 같아야 한다** — 배포 뒤 같은 GET을 한 번 더 떠서
이 숫자를 대조하면 §5 첫 항목이 끝난다.

### 2-e. `0`은 선택지에서 뺀다 (사용자 결정) — 세 곳 공통

`0`("진척에 기여하지 않음")은 새로 고를 수 없다. 단 **기존에 `0`이 저장된 행은 거부하지 않고**
`집계 제외(0)`로 읽기 전용 표시하며, 그 값 그대로 저장할 수 있어야 한다. 척도 밖 값(`4`·`30` 등)도
같은 규칙으로 `사용자 지정 30`이라 적는다.

> **이 규칙이 빠지면 사고가 난다.** 제목만 고치려고 편집을 열었는데 저장 시 가중치가 임의의
> 등급으로 덮이면 커밋 `da96ebe`와 같은 종류의 사고다.

`unitPerLeaf`가 `0`을 단위 계산에서 일부러 빼는 것(`declared > 0`)과 일관된다 — `0`은 "작다"가
아니라 별도의 뜻이라 등급 사다리에 올리지 않는다.

### 2-f. #1·#2만 `null`을 폼에서 실체화한다

`draftFrom`/폼 초기화가 `null`을 `DEFAULT_WEIGHT`(3)로 매핑한다. 그래서 레거시 `null` 행을 한 번
저장하면 `3`이 명시적으로 박힌다. **계산 결과는 언제나 동일하다**(§2-d로 `null`도 3이므로) —
어느 집합에서도, 혼합 집합에서도 no-op이다. 그 덕에 `null`이 점진적으로 사라진다.

**#3은 정반대다.** `null`을 `미지정` 옵션으로 열고 그대로 돌려보낸다. 실체화하면 §2-c의 사고가
난다. `WbsForm`은 **`null`을 보낼 수 있어야 한다.**

### 2-g. #3은 Summary에도 입력할 수 있다

`rollUp:203-217`은 `nodeType`을 보지 않고 **모든 자식**에 weight를 적용한다. Summary만 막으려면
분기를 새로 넣어야 하고, 형제로 나란한 Summary끼리 크기가 다른 것은 정상이다.

**다만 사각지대가 하나 생긴다** — 진척 탭 표는 Work Package만 나열하고(`ProgressPanel.vue:246`)
WBS 트리에는 가중치 열이 없어서, **Summary에 넣은 등급은 그 폼을 다시 열기 전엔 어디에도 보이지
않는다.** WBS 트리 열 추가는 §6-5의 후속 후보다.

### 2-h. 하지 않는 것

| 하지 않음 | 이유 |
|---|---|
| **컬럼을 문자열 enum으로 변경** | §2-a의 네 곳이 깨지고 `formatVersion`을 올려야 한다 |
| **서버 측 척도 검증**(1/2/3/5/8만 허용) | 구형 export 파일 가져오기(`ImportService:807·827·880`)와 커밋 복원이 전부 거부된다. 레거시 값은 **정상 데이터**다 |
| **백엔드에 `WeightGrade` enum 신설** | 서버가 등급을 모른다 — 척도는 입력 어휘라 프론트에만 있으면 된다. 쓰이지 않는 enum이 된다 |
| **`unitPerLeaf`(`:269-279`)의 계산 손대기** | 이 브랜치가 방금 고친 폴백이다. 거기 `leaves == 0 ? 1`의 `1`은 등급이 아니라 **분모 0 방지용 상수**이고, 3으로 바꾸면 WBS 롤업이 통째로 움직인다. #3은 **입력만** 되살린다 |
| **`anyWeight` 분기 제거** | 아무도 안 적은 가지는 계속 `LEGACY_ROLLUP`(leaf 개수 가중)이어야 한다. 지우면 기존 프로젝트의 숫자가 바뀐다 |
| **WBS 폼에서 `null` 못 보내게 막기** | §2-c. 미입력을 실체화하면 진척이 즉시 바뀐다 |
| **Story Point를 등급으로** | 가중치가 아니라 팀의 실행 추정치다. CLAUDE.md가 "같은 단위로 합산하지 말라"고 못박은 별개 값 |
| **`ProgressService.java:234`의 기준선 폴백 `1`** | `baseline_items.weight`는 `wbs_items.weight` 복사본이고, 계획 쪽은 트리를 안 타는 별개 문제와 묶여 있다(§6-2) |
| **`incompleteWeights`·`ProgressResult` shape** | 과거 커밋 렌더 호환 — 직전 지시서가 정한 그대로 둔다 |
| **BacklogForm에서 Epic·Task의 가중치 칸 숨기기** | 유형을 중간에 바꿀 때의 처리가 붙는다. §6-3 |
| **대시보드에 "가중치 미지정" DataGap 되살리기** | 되살리면 기존 프로젝트가 첫날부터 전 항목 누락으로 뜬다. §6-4 |
| **`ProgressService.java:289-293` 의 기준선 범위 문자열** | 서버가 `"1.2 화면 (미입력 → 3)"` 을 **숫자로** 만들고 `ProgressPanel.vue:198-200`·`BaselineCard.vue:44·59` 가 그대로 렌더한다. 지금까지는 도달 불가였는데(#3 입력이 없어 기준선과 달라질 일이 없었다) **이번 작업으로 처음 실제로 뜬다.** 그래도 두는 이유는 §2-h의 "백엔드는 척도를 모른다" 와 맞물려서다 — 고치려면 프론트가 문자열을 파싱하거나 백엔드에 어휘를 들여야 하는데 둘 다 나쁘다. **앱에서 가중치가 숫자로 읽히는 유일한 자리로 남는다.** 결함이 아니다(§6-9) |

---

## 3. 작업

순서는 **B → A → C·D·E·F** 다. 공용 척도가 먼저 있어야 나머지가 그것을 부른다.

### A. 백엔드 — 한 곳

#### A-1. `backend/src/main/java/com/projectflow/domain/ProgressCalculator.java:245-248`

**before**

```java
/** 미입력 가중치는 균등(1)으로 본다 — "가중치를 아직 안 넣었다"는 "비중이 같다"의 흔한 표현이다. */
private static double weightOf(Integer weight) {
    return weight == null ? 1 : weight;
}
```

**after**

```java
/**
 * 체크포인트·Backlog의 미입력 가중치는 <b>보통(3)</b>으로 본다.
 *
 * <p>화면이 고르게 하는 척도가 1·2·3·5·8이라(프론트엔드 {@code shared/weight.ts}) 그 한가운데인
 * 3이 "아직 고르지 않았다"의 자연스러운 뜻이다. 예전 폴백 1은 이제 <b>아주 작음</b>이라, 미지정인
 * 형제들 사이에 기본값으로 추가한 항목 하나가 나머지의 3배 몫을 갖게 된다.
 *
 * <p>가중치는 상대값이라 <b>전부 미입력인 집합의 결과는 이 상수에 무관하다</b>(1:1:1 = 3:3:3).
 * 달라지는 것은 일부만 선언된 혼합 집합뿐이다.
 *
 * <p><b>WBS 형제 가중치는 이 함수를 쓰지 않는다</b> — {@link #unitPerLeaf}(선언값이 암시하는
 * leaf당 비중)이다. 그쪽 형제는 가지 크기가 제각각이라 상수 폴백 자체가 맞지 않는다. 화면에서
 * 같은 등급 척도를 고르더라도 <b>미입력의 뜻은 다르다</b>: 여기선 3, 저기선 형제 크기 비례다.
 */
private static double weightOf(Integer weight) {
    return weight == null ? 3 : weight;
}
```

**`unitPerLeaf`(`:269-279`)와 `rollUp`(`:195-232`)은 한 줄도 건드리지 않는다.** #3은 입력만
되살리는 것이고 계산은 이 브랜치가 정한 그대로다.

#### A-2. `backend/src/test/java/com/projectflow/domain/ProgressCalculatorTest.java` — 추가만

기존 테스트는 **하나도 고치지 않는다**(§5의 전수 확인표). 아래 둘을 새로 넣는다.

```java
@Test
@DisplayName("미입력 가중치는 보통(3)으로 센다 — 선언된 3과 구별되지 않는다")
void missingWeightCountsAsMedium() {
    WbsItem wp = workPackage("개발", ExecutionMode.AGILE, null);
    story(wp, null, true);     // 미입력 → 3
    story(wp, 3, false);       // 명시 3

    // 3 / (3 + 3) = 50%
    assertThat(percentOf(wp)).isEqualTo(50.0);
}

@Test
@DisplayName("전부 미입력이면 균등 — 폴백 상수를 무엇으로 두든 결과가 같다")
void allMissingWeightsStayEven() {
    WbsItem wp = workPackage("인수", ExecutionMode.WATERFALL, null);
    checkpoint(wp, null, true);
    checkpoint(wp, null, false);
    checkpoint(wp, null, false);

    assertThat(percentOf(wp)).isEqualTo(100.0 / 3);
}
```

> 기대값을 베끼지 말고 직접 계산해 확인할 것. `keepsPrecision`의 `isEqualTo(100.0 / 3)` 표기
> 관습을 따랐다.

### B. 프론트엔드 신규 — 척도 한 곳

#### B-1. `frontend/src/shared/weight.ts` (신규)

`shared/backlog.ts`·`shared/raid.ts`와 같은 자리·같은 패턴이다. Backlog·Progress·WBS 세 feature가
함께 쓰므로 feature 안에 두지 않는다.

```ts
/**
 * 진척 가중치의 등급 척도. 체크포인트(Waterfall·Hybrid 분모), Backlog 항목(Agile 분모),
 * WBS 형제(롤업 비중)가 함께 쓰므로 feature가 아니라 shared에 둔다 — 세 화면이 다른 척도를
 * 말하면 안 된다.
 *
 * 값은 DB에 그대로 저장되는 정수다. 등급은 입력 어휘일 뿐이라 서버는 이 척도를 모르고,
 * 척도 밖 값도 거부하지 않는다(구형 파일 가져오기·커밋 복원이 그런 값을 싣고 온다).
 *
 * **어휘는 셋이 공유하지만 `null`의 뜻은 둘이다.** 체크포인트·Backlog의 미입력은 상수
 * `DEFAULT_WEIGHT`(백엔드 `ProgressCalculator.weightOf`와 짝)이고, WBS 형제의 미입력은
 * 형제 크기에 비례하는 값(`ProgressCalculator.unitPerLeaf`)이라 상수로 적을 수 없다.
 * 그래서 아래 함수가 용도별로 나뉘어 있다 — 돌려쓰지 말 것(지시서 §2-c).
 */
export interface WeightGrade {
  value: number
  label: string
  /** 한 Work Package 안(체크포인트·Backlog)에서 이 등급을 고를 판단 기준. */
  hint: string
  /** 형제 WBS 항목 사이에서 이 등급을 고를 판단 기준. 같은 값이라도 견주는 대상이 다르다. */
  wbsHint: string
}

/** 큰 것부터. 드롭다운이 이 순서로 읽힌다. */
export const WEIGHT_GRADES: WeightGrade[] = [
  { value: 8, label: '아주 큼', hint: '이 업무의 절반 이상', wbsHint: '이 단계의 절반 이상을 차지한다' },
  { value: 5, label: '큼', hint: '큰 덩어리 하나', wbsHint: '형제 중 가장 무거운 축이다' },
  { value: 3, label: '보통', hint: '평범한 한 단계', wbsHint: '형제와 비슷한 크기다' },
  { value: 2, label: '작음', hint: '짧게 끝나는 단계', wbsHint: '형제보다 가볍게 끝난다' },
  { value: 1, label: '아주 작음', hint: '형식적 확인', wbsHint: '거의 형식적이다' },
]

/**
 * 미입력(`null`)을 대신하는 값 — **체크포인트·Backlog 전용**.
 *
 * **백엔드 `ProgressCalculator.weightOf`의 폴백과 같아야 한다** — 두 값을 잇는 컴파일 타임
 * 장치가 없으므로 한쪽만 고치면 조용히 어긋난다.
 *
 * **WBS 형제에는 쓰지 말 것.** 그쪽 미입력은 `unitPerLeaf × leafCount`라 상수가 아니고,
 * 이 값을 박아 넣으면 그 프로젝트의 진척이 즉시 바뀐다(지시서 §2-c).
 */
export const DEFAULT_WEIGHT = 3

/** 드롭다운에서 고를 수 있는 값인가. `0`과 척도 밖 값은 레거시 전용이라 `false`. */
export function isOnScale(weight: number | null): boolean {
  return weight !== null && WEIGHT_GRADES.some((g) => g.value === weight)
}

/**
 * 척도 위의 값과 레거시 값을 읽을 수 있는 한 마디로. `null`은 다루지 않는다 —
 * 미입력의 뜻이 용도마다 다르기 때문이다(`weightLabel` / `wbsWeightLabel`).
 */
export function gradeLabel(weight: number): string {
  if (weight === 0) return '집계 제외(0)'
  return WEIGHT_GRADES.find((g) => g.value === weight)?.label ?? `사용자 지정 ${weight}`
}

/**
 * 체크포인트·Backlog 표시용.
 *
 * `null`은 `보통`이다 — 계산이 실제로 `DEFAULT_WEIGHT`로 돌아가므로 그렇게 적는 것이 사실에
 * 맞는다.
 */
export function weightLabel(weight: number | null): string {
  if (weight === null) return WEIGHT_GRADES.find((g) => g.value === DEFAULT_WEIGHT)!.label
  return gradeLabel(weight)
}

/**
 * WBS 형제 표시용.
 *
 * `null`은 `미지정`이다 — 이 값의 몫은 상수가 아니라 형제의 선언값에서 나오므로(`unitPerLeaf`)
 * 어떤 등급 이름으로도 적을 수 없다. `보통`이라 적으면 거짓이다.
 */
export function wbsWeightLabel(weight: number | null): string {
  if (weight === null) return '미지정'
  return gradeLabel(weight)
}

/**
 * 폼에 채울 값 — **체크포인트·Backlog 전용**. 미입력은 기본 등급으로 미리 골라 둔다(§2-f).
 *
 * WBS 폼은 이 함수를 쓰지 않는다 — `null`을 `null`인 채로 열고 그대로 돌려보내야 한다.
 */
export function weightForForm(weight: number | null): number {
  return weight ?? DEFAULT_WEIGHT
}
```

#### B-2. `frontend/src/shared/weight.spec.ts` (신규)

순수 함수라 DOM이 필요 없다 — **`// @vitest-environment happy-dom` 주석을 넣지 말 것.**

- `gradeLabel`: `8·5·3·2·1` → 각 라벨 / `0` → `'집계 제외(0)'` / `4`·`30` → `'사용자 지정 4'`·`'사용자 지정 30'`
- `weightLabel`: 위와 같고 **`null` → `'보통'`**
- `wbsWeightLabel`: 위와 같고 **`null` → `'미지정'`**
- **`weightLabel(null) !== wbsWeightLabel(null)`** 을 단정한다 — 둘을 같게 "정리"하려는 다음
  사람을 여기서 멈춘다. 이것이 §2-c를 고정하는 테스트다
- `isOnScale`: 척도값 `true`, `null`·`0`·`4` `false`
- `weightForForm`: `null` → `3`, `0` → `0`(그대로), `30` → `30`(그대로)
- **`DEFAULT_WEIGHT`가 `WEIGHT_GRADES`에 실제로 있는 값인지** 단정한다 — 없으면
  `weightLabel(null)`이 터진다(`!` 단언)
- 모든 등급에 `hint`와 `wbsHint`가 **비어 있지 않은지** 단정한다 — 기준 문장이 이 작업의 본체다

### C. 프론트엔드 — 체크포인트 (#1)

#### C-1. `frontend/src/features/progress/checkpointForm.ts`

`emptyDraft`와 `draftFrom`이 등급 기본값을 채우게 한다.

**before** (`:14-26`)

```ts
/** 추가 모드의 빈 입력값. */
export function emptyDraft(): CheckpointDraft {
  return { title: '', weight: null, criteria: '' }
}

/** 수정 버튼을 눌렀을 때 폼에 채울 값. */
export function draftFrom(checkpoint: CheckpointDetail): CheckpointDraft {
  return {
    title: checkpoint.title,
    weight: checkpoint.weight,
    criteria: checkpoint.completionCriteria ?? '',
  }
}
```

**after**

```ts
/** 추가 모드의 빈 입력값. 가중치는 기본 등급(보통)으로 미리 골라 둔다. */
export function emptyDraft(): CheckpointDraft {
  return { title: '', weight: DEFAULT_WEIGHT, criteria: '' }
}

/**
 * 수정 버튼을 눌렀을 때 폼에 채울 값.
 *
 * 미입력(`null`)은 기본 등급으로 preselect 한다 — 계산이 이미 그 값으로 돌아가므로(백엔드
 * `weightOf`) 저장해도 결과가 달라지지 않고, 대신 `null`이 하나씩 사라진다. `0`과 척도 밖 값은
 * **그대로 둔다**: 제목만 고치려는 저장이 가중치를 덮으면 안 된다.
 */
export function draftFrom(checkpoint: CheckpointDetail): CheckpointDraft {
  return {
    title: checkpoint.title,
    weight: weightForForm(checkpoint.weight),
    criteria: checkpoint.completionCriteria ?? '',
  }
}
```

`toCheckpointInput`(`:28-40`)의 **동작은 그대로 두되 주석을 고친다.** 지금 주석이 *"`null`(미입력,
서버가 1로 계산)과 `0`은 다른 뜻"*이라 적혀 있는데 `1`이 아니라 `3`이 됐고, 폼은 이제 `null`을
보내지 않는다.

#### C-2. `frontend/src/features/progress/CheckpointList.vue`

**(가) 입력 — `:284-292`의 `<input>`을 `<select>`로**

```html
<select
  v-model.number="draft.weight"
  class="cp-weight-input"
  aria-label="가중치"
  @keydown.enter="isSubmittable(draft) && submit()"
  @keydown.esc="closeForm"
>
  <!-- 고를 수는 없지만 저장된 값은 그대로 보이고 그대로 저장돼야 한다(레거시 0·척도 밖). -->
  <option v-if="!isOnScale(draft.weight)" :value="draft.weight" disabled>
    {{ weightLabel(draft.weight) }}
  </option>
  <option v-for="grade in WEIGHT_GRADES" :key="grade.value" :value="grade.value">
    {{ grade.label }} ({{ grade.value }}) — {{ grade.hint }}
  </option>
</select>
```

- `@keydown.enter`·`@keydown.esc`를 **빠뜨리지 말 것**. `:268-273`의 주석이 설명하듯 이 폼은
  `<div>`라 브라우저 기본 submit이 없고, 이 핸들러가 곧 Enter 저장의 전부다.
- `aria-label`이 필요하다 — `<input>`의 `placeholder="가중치"`가 사라지면서 접근 가능한 이름이
  없어진다.

**(나) 표시 — `:207-213`**

```html
<!-- before -->
<!--
  미입력(`null`)은 "균등"이 아니라 **1**이다 — `ProgressCalculator.weightOf`가 1로
  폴백하므로 `[30, 30, 40, null]`에서 마지막 하나는 1/101을 갖는다. 예전 "균등" 표시는
  형제 중 하나라도 값이 있으면 거짓이었다.
-->
<span class="cp-weight">가중치 {{ cp.weight ?? 1 }}</span>
```

```html
<!-- after -->
<!--
  미입력(`null`)은 `보통`으로 적는다 — `ProgressCalculator.weightOf`가 실제로 그 값으로
  폴백하므로 표시와 계산이 어긋나지 않는다. `0`과 척도 밖 값은 고를 수 없지만 저장돼 있을 수
  있어 숫자째로 드러낸다. (WBS 형제 가중치는 `wbsWeightLabel`을 쓴다 — 거긴 미입력이 `미지정`이다.)
-->
<span class="cp-weight">가중치 {{ weightLabel(cp.weight) }}</span>
```

**(다) `normalizeWeight`(`:95-101`)** — `<select>`는 빈 문자열·`NaN`을 만들지 않으므로 존재 이유가
사라진다. **삭제하고** `submit()`(`:125-131`)에서 `draft.value`를 그대로 넘긴다.

```ts
// before
const input = toCheckpointInput(props.wbsItemId, {
  ...draft.value,
  weight: normalizeWeight(draft.value.weight),
})

// after
const input = toCheckpointInput(props.wbsItemId, draft.value)
```

**(라) 스타일** — `.cp-form .cp-weight-input`(`:451`)의 폭이 숫자 칸 기준이라 등급 문장이 잘린다.
기준 문장이 보이는 폭으로 넓힌다. `select`의 기본 모양은 전역 컨트롤 층이 주므로 **로컬에
테두리·패딩·radius를 다시 적지 말 것**(CLAUDE.md 컨트롤 층).

#### C-3. 테스트

- `frontend/src/features/progress/checkpointForm.spec.ts` — `emptyDraft().weight === DEFAULT_WEIGHT`,
  `draftFrom`이 `null`→`3`·`0`→`0`·`30`→`30`으로 매핑하는 것
- `frontend/src/features/progress/CheckpointList.spec.ts` — 파일 맨 위에 이미 있는
  `// @vitest-environment happy-dom`을 유지한다. 추가할 것:
  - 폼을 열면 `<select>`에 등급 다섯 개가 있고 `보통`이 선택돼 있다
  - `weight: 0`인 체크포인트를 수정하면 `집계 제외(0)` 옵션이 `disabled`로 **남아 있고 선택돼
    있으며**, 제목만 고쳐 저장했을 때 **제출값의 `weight`가 `0` 그대로**다
  - `weight: 30`도 같은 성질 (`사용자 지정 30`)
  - 목록 행이 `가중치 보통`처럼 라벨로 읽힌다
  - `confirm()`을 쓰는 케이스는 `vi.stubGlobal` + `afterEach`의 `vi.unstubAllGlobals()` 관습을
    그대로 따른다(이 파일이 이미 그 예시다)

### D. 프론트엔드 — Backlog (#2)

#### D-1. `frontend/src/features/backlog/BacklogForm.vue:253-256`

```html
<!-- before -->
<label>
  진척 가중치
  <input v-model.number="form.progressWeight" type="number" min="0" placeholder="비중" />
</label>
```

```html
<!-- after -->
<label>
  진척 가중치
  <select v-model.number="form.progressWeight">
    <option v-if="!isOnScale(form.progressWeight)" :value="form.progressWeight" disabled>
      {{ weightLabel(form.progressWeight) }}
    </option>
    <option v-for="grade in WEIGHT_GRADES" :key="grade.value" :value="grade.value">
      {{ grade.label }} ({{ grade.value }}) — {{ grade.hint }}
    </option>
  </select>
</label>
```

초기값 두 곳을 함께 고친다.

- `:50` — `progressWeight: null` → `progressWeight: DEFAULT_WEIGHT`
- `:136` — `form.progressWeight = item.progressWeight` → `weightForForm(item.progressWeight)`

`form.storyPoint`(`:49`·`:135`·`:251`)는 **건드리지 않는다** — 가중치가 아니다(§2-h).

#### D-2. `frontend/src/features/backlog/BacklogList.vue:165`

```html
<!-- before -->
<td class="num">{{ row.item.progressWeight ?? '-' }}</td>

<!-- after: 집계 대상(Story·Bug)이 아니면 가중치가 쓰이지 않으므로 값을 말하지 않는다 -->
<td>{{ row.item.aggregated ? weightLabel(row.item.progressWeight) : '-' }}</td>
```

`class="num"`(우측 정렬)을 뗀다 — 숫자가 아니라 라벨이 된다. 머리글(`:99`의
`<th class="num" scope="col">가중치</th>`)도 같이 맞춘다.

#### D-3. `frontend/src/shared/exportRows.ts:173`

```ts
// before
item.progressWeight ?? '',
// after
item.aggregated ? weightLabel(item.progressWeight) : '',
```

CSV는 사람이 Excel에서 읽는 것이라 enum이 아니라 라벨을 쓴다(CLAUDE.md 내보내기 규칙).
머리글 `'진척 가중치'`(`:155`)는 그대로.

### E. 프론트엔드 — WBS 형제 가중치 (#3) **[신규 범위]**

#### E-1. `frontend/src/features/wbs/WbsForm.vue` — 입력칸을 되살린다

`form.weight`는 이미 상태에 있다(`:42`). **`<select>`만 붙이면 된다** — payload 통과 경로
(`:123`의 `emit('submit', { ...form })`)는 그대로 쓴다.

자리는 `실행 방식`(`:170-178`)·`Hybrid 비중 α`·`인수 상태`가 있는 **진척 근거 묶음**이다.
`:181`의 `<div class="row">`에 세 번째 `label`로 넣되, 한 줄이 좁으면 새 `.row`를 만든다.

```html
<label>
  규모
  <select v-model="form.weight">
    <!-- 미입력은 실체화하지 않는다 — 그 몫은 형제의 선언값에서 나온다(지시서 §2-c). -->
    <option :value="null">미지정</option>
    <!-- 고를 수는 없지만 저장된 값은 그대로 보이고 그대로 저장돼야 한다(레거시 0·척도 밖). -->
    <option v-if="form.weight !== null && !isOnScale(form.weight)" :value="form.weight" disabled>
      {{ wbsWeightLabel(form.weight) }}
    </option>
    <option v-for="grade in WEIGHT_GRADES" :key="grade.value" :value="grade.value">
      {{ grade.label }} ({{ grade.value }}) — {{ grade.wbsHint }}
    </option>
  </select>
</label>
```

**`v-model.number`가 아니라 `v-model`이다** — `.number` 수식자는 `null` 옵션을 `0`으로 바꿀 수
있다. `:value`에 숫자 리터럴을 바인딩하므로 문자열이 될 일이 없다.

같은 `.row`에 도움말 한 줄을 둔다:

```html
<p class="hint muted">
  형제 항목 사이의 상대적 크기입니다. 미지정으로 두면 등급을 적은 형제에서 leaf 하나당 비중을
  뽑아 이 항목의 가지 크기만큼 칩니다 — 형제 중 누구도 적지 않았다면 예전처럼 하위 leaf 개수로
  집계합니다.
</p>
```

#### E-2. `WbsForm.vue:253-262` — "보관 중" 안내를 지운다

```html
<!-- 지울 것 -->
<p v-if="form.weight !== null && (form.weight as unknown) !== ''" class="hint muted">
  이 항목에 저장된 가중치({{ form.weight }})는 지우지 않고 그대로 보관하며 집계에도 계속
  쓰입니다. 가중치는 이 폼에서 다루지 않으므로 저장해도 값은 바뀌지 않습니다.
</p>
```

바로 위 주석 블록(`:253-258`, "입력칸은 없앴지만 값은 보관한다")도 함께 지운다 — 입력칸이
돌아왔으므로 설명할 것이 없다. **`form.weight`를 payload에서 빼지 말라는 경고는 유효하므로**
그 문장만 `E-1`의 `<select>` 위 주석으로 옮긴다.

#### E-3. `frontend/src/features/progress/ProgressPanel.vue` — 표시를 라벨로

**(가) `:262`**

```html
<!-- before -->
<td class="num">{{ wp.weight ?? '-' }}</td>
<!-- after -->
<td>{{ wbsWeightLabel(wp.weight) }}</td>
```

`class="num"`을 뗀다. 머리글 `:238`의 `<th class="num" scope="col">가중치</th>`도 맞춘다.
열 이름은 **`규모`** 로 바꾼다 — 폼의 라벨과 같은 말이어야 한다.

**(나) `:223-229`의 안내 문단** — "이 화면에서는 입력하지 않습니다"가 **거짓이 된다.**

```html
<!-- after -->
<p class="notice subtle">
  규모는 같은 상위 아래 형제 항목 사이의 상대적 크기입니다. <strong>WBS 화면의 항목 수정
  폼</strong>에서 다섯 등급 중 고릅니다 — 미지정으로 두면 등급을 적은 형제에서 leaf 하나당
  비중을 뽑아, 그 항목이 거느린 가지 크기만큼 칩니다. 형제 중 누구도 적지 않았다면 그 가지는
  예전처럼 하위 leaf 개수 가중 평균으로 집계됩니다 — <strong>0과 미지정은 다른 값</strong>입니다.
  α(Hybrid의 Agile 비율)도 같은 폼에서 고칩니다.
</p>
```

#### E-4. `frontend/src/shared/exportRows.ts:52`·`:77` — WBS CSV

```ts
// before
node.weight ?? '',
// after
wbsWeightLabel(node.weight),
```

머리글 `'가중치'`는 **`'규모'`** 로 바꾼다. CSV는 사람이 읽는 것이라 라벨을 쓴다(D-3과 같은 규칙).
`exportRows.spec.ts`에 WBS CSV 기대값이 있으면 함께 고친다.

### F. 뒤집어야 하는 기존 테스트 **[신규 범위]**

#### F-1. `frontend/src/features/wbs/WbsForm.spec.ts:78-170`

`describe('WbsForm — 가중치 입력')` 블록의 **11개가 전부 "입력칸이 없다"를 고정**하고 있다.
`weightLabel()` 헬퍼(`:67-70`)는 라벨 글자가 `가중치`로 시작하는 `<label>`을 찾는데, **새 라벨은
`규모`** 이므로 헬퍼의 검색어도 함께 바꾼다.

| 기존 테스트 | 처리 |
|---|---|
| `최상위 항목 추가에는 가중치 입력이 없다` | **뒤집는다** — `규모` 셀렉트가 있고 `미지정`이 선택돼 있다 |
| `하위 항목 추가에도 … 없다 — 레벨 무관으로 삭제됐다` | **뒤집는다** — 레벨과 무관하게 있다 |
| `최상위 항목 수정에도 … 없다` | **뒤집는다** |
| `하위 항목 수정에도 … 없다` | **뒤집는다** |
| `입력칸을 감춘 최상위 항목을 저장해도 기존 가중치가 그대로 실려 나간다` | **유지하되 문구만 고친다** — 감춘 게 아니라 *건드리지 않은* 경우다. `weight: 7`(척도 밖)이 `disabled` 옵션으로 선택돼 있고 저장 시 `7` 그대로여야 한다 |
| `입력칸을 감춘 하위 항목을 …` (`weight: 3`) | **유지 + 강화** — `3`은 이제 척도 위의 `보통`이다. 정상 옵션으로 선택돼 있어야 한다 |
| `가중치가 없던 항목을 저장하면 null 그대로 나간다 — 새로 채워 넣지 않는다` | **그대로 둔다. 가장 중요한 테스트다** — §2-c의 실체화 금지를 고정한다 |
| `저장된 가중치가 있으면 … 보관 중임을 알린다` ×2 | **삭제** — 그 안내가 사라진다(E-2) |
| `가중치가 없으면 보관 안내도 뜨지 않는다` | **삭제** — 같은 이유 |
| `가중치 제안 힌트는 … 남아 있지 않다` | **그대로 둔다** — `weightSuggestion.ts`는 되살리지 않는다. 단 `'제안'` 문자열 검사가 새 도움말 문구와 충돌하지 않는지 확인할 것 |

**추가할 것**

- `0`이 저장된 항목을 열면 `집계 제외(0)`가 `disabled`로 선택돼 있고, 저장 시 `0` 그대로다
- 등급을 고르면 제출값이 그 숫자다
- 고른 뒤 다시 `미지정`으로 되돌리면 제출값이 **`null`** 이다(`0`이 아니다)
- Summary 항목에서도 셀렉트가 보인다(§2-g)

#### F-2. `frontend/src/features/progress/ProgressPanel.spec.ts:168`

문구 고정 테스트가 현재 문구를 박아 두고 있다. E-3(나)로 문구가 바뀌므로 **함께 고친다.**

- **낡은 서술로 새로 넣을 것**: `'이 화면에서는 입력하지'` 가 **없다** — 이번에 거짓이 된 문장이다
- 유지할 단정: `'1로 계산'`이 없다 / `'leaf 하나당 비중'`·`'가지 크기만큼'`·`'leaf 개수 가중'`·
  `'0과 미지정은 다른 값'` 이 있다
- 위 주석(`:161-167`)에 **세 번째 뒤처짐**을 한 줄 추가한다 — 이 문구는 이제 세 번 고쳐졌다

### G. `CLAUDE.md` — 부딪히는 문장

- **`:412`** — `**weightOf(=1 폴백)를 여기에 재사용하지 마세요**` → `(=3(보통) 폴백)`.
  그 뒤의 "그 함수는 체크포인트·Backlog 전용입니다"는 **그대로 옳고 이번 변경으로 더 맞는 말이
  된다** — 두 폴백이 이제 값까지 다르다.
- **`:434-441`** — `**그래서 WBS 폼은 레벨 무관 가중치를 묻지 않습니다**(사용자 결정)` 문단
  **전체를 다시 쓴다.** 이 작업이 정확히 그 결정을 뒤집는다. 새 문단에 담을 것:
  - 다섯 등급 중에서 고른다는 것, 기준 문장이 "적을 근거가 없다"를 해소했다는 것
  - **`leafCount`는 구조이지 규모가 아니라서** 예전 사유의 뒷부분이 형제가 전부 leaf인 가지에서
    성립하지 않는다는 것(§0)
  - **미지정은 실체화하지 않는다**는 것과 그 이유(§2-c)
  - 진척 탭은 **여전히 읽기 전용**이고 입력은 WBS 폼 한 곳이라는 것
- **`:440`** — `**체크포인트(…) 가중치 입력은 그대로 남아 있습니다**` 에 등급 선택으로 바뀌었다는
  사실을 덧붙인다.
- `:400-411`의 `unitPerLeaf` 설명은 **건드리지 않는다** — 계산은 그대로다. 단 "어느 화면에서도
  입력할 수 없습니다"류의 문장이 남아 있으면 함께 고친다.
- "화면 레이아웃 규칙"·"컨트롤 층"에는 새 규칙을 만들지 않는다 — 전역 `select` 스타일을 그대로
  쓰는 것이 이미 규칙이다.

---

## 4. 건드리지 않는 것

| 대상 | 이유 |
|---|---|
| `acceptance_checkpoints`·`backlog_items`·`wbs_items` DDL, 새 마이그레이션 | 컬럼은 계속 `INT`다. 옮길 데이터가 없다 |
| `ProgressCalculator.unitPerLeaf`(`:269-279`)·`rollUp`(`:195-232`) | 이 브랜치가 방금 고친 계산이다. #3은 **입력만** 되살린다 |
| `ProgressCalculator`의 `anyWeight` 분기 | 아무도 안 적은 가지는 계속 `LEGACY_ROLLUP`이어야 한다 |
| `baseline_items.weight` · `ProgressService.java:234` | #4는 범위 밖(§1-c). 계획 쪽은 트리를 안 타는 별개 문제와 묶여 있다 |
| `ProgressResult`의 필드·`incomplete()`·`incompleteWeights` | 과거 커밋 `computed_payload` 렌더 호환 |
| `ProgressBasisService`·`BacklogService`·`WbsService`의 검증 | 서버는 척도를 모른다. 검증을 넣으면 구형 파일 가져오기·커밋 복원이 깨진다 |
| `WbsItemCreateRequest:38`·`WbsItemUpdateRequest:36`의 `@Min(0)` | 범위가 그대로다. 등급만 허용하면 레거시가 400이 된다 |
| `ExportService`·`ImportService`·`ProjectExportResponse`·`formatVersion` | 값의 타입과 범위가 그대로다 |
| `BacklogForm`의 `storyPoint` | 가중치가 아니라 팀의 추정치 |
| `PUT .../progress/work-packages/{id}/basis` | 계약으로만 남은 좁은 경로다. WBS 폼은 기존대로 `PUT /wbs/{itemId}`를 쓴다 |
| `weightSuggestion.ts` | 삭제된 채로 둔다. 기준 문장이 그 역할을 대신한다 |
| 커밋 조회 모드 | 응답 shape을 바꾸지 않았으므로 과거 커밋도 새 라벨로 그려진다 |

---

## 5. 확인 방법

```bash
cd backend  && ./gradlew cleanTest test build
cd frontend && npm run build && npm test
```

> **`./gradlew test`만 쓰면 안 된다** — `UP-TO-DATE`로 건너뛰고 `BUILD SUCCESSFUL`만 찍힌다.
> 반드시 `cleanTest`를 앞에 둔다.

### 기존 백엔드 테스트 판정 (전수 확인 완료 — HEAD `6ddde45` 기준)

`ProgressCalculatorTest`에서 `null` 가중치를 넘기는 호출은 여섯 군데다. **전부 균질 집합이라
비율이 보존되어 `1→3`에도 결과가 같다.**

| 줄 | 호출 | 집합 | 폴백 1 | 폴백 3 | 판정 |
|---|---|---|---|---|---|
| `:38` | `story(wp, null, i < 6)` ×10 | 전부 미입력 | 60.0 | 60.0 | ✅ 통과 |
| `:124-125` | `story(null,true)` + `checkpoint(null,true)` | 각 1건 | 100/100 | 100/100 | ✅ 통과 |
| `:135` | `story(wp, null, true)` | 1건 | 100.0 | 100.0 | ✅ 통과 |
| `:412-413` | `story(null,true)` + `story(null,false)` | 전부 미입력 | 50.0 | 50.0 | ✅ 통과 |

선언값을 쓰는 나머지(`:49-50`, `:69-70`, `:84-85`, `:110-114`)는 `weightOf`의 폴백 경로를 타지
않는다. 이 브랜치가 추가한 `unitPerLeaf` 테스트들은 **WBS 가중치**라 `weightOf`와 무관하다.
**깨지는 백엔드 테스트는 없다** — 하나라도 빨개지면 §2-d의 전제가 틀린 것이니 멈추고 보고할 것.

**프론트엔드는 다르다.** `WbsForm.spec.ts`의 11개가 의도적으로 깨진다(§3-F1). 그것 말고 깨지는
것이 있으면 멈추고 보고할 것.

### 수동 확인 — 중요한 순서대로

- [ ] **가중치를 한 번도 안 넣은 프로젝트의 진척 숫자가 작업 전과 완전히 동일하다.**
      이 브랜치의 `unitPerLeaf` 변경 직후 값을 기준으로 잡을 것(`main`의 값이 아니다)
- [ ] WBS 트리에서 Work Package를 펼쳐 체크포인트를 추가하면 가중치가 **드롭다운**이고 `보통`이
      미리 골라져 있으며, 각 등급 옆에 기준 문장이 보인다
- [ ] 등급을 바꿔 저장하면 목록 행이 `가중치 큼`처럼 라벨로 바뀐다
- [ ] **`weight`에 `0`이나 척도 밖 값이 저장된 기존 체크포인트를 수정해 제목만 바꿔 저장하면
      가중치가 그대로 남는다** (드롭다운에 `집계 제외(0)` / `사용자 지정 30`이 비활성으로 선택돼 있다)
      - **합성 데이터를 만들 필요가 없다.** AEGIS의 `1.2.2`·`1.2.4`·`2.2`에 이미 `6`·`9`·`7`이
        들어 있다. **`사용자 지정 N`은 예외가 아니라 흔하다** — 선언된 다섯 중 셋이 척도 밖이다.
      - **배포 안내에 한 줄 넣을 것.** 저 값들은 합이 10 안팎(`5+6`·`1+9`·`3+7`·`2+8`)이라
        **백분율처럼 적은 것**으로 보인다. `1.2.4 [1,9]`의 체크포인트를 열어 `사용자 지정 9` 대신
        등급을 고르면 `[1,8]`이 되어 그 WP 진척이 `100% → 88.9%`로 움직인다. §2-e대로 **일부러
        바꿀 때만** 움직이니 설계대로지만, 모르고 겪으면 "왜 숫자가 바뀌었지"가 된다.
- [ ] 미입력(`null`)이던 체크포인트를 열면 `보통`이 골라져 있고, 그대로 저장해도 **진척 숫자가
      변하지 않는다**
- [ ] 가중치 칸에서 Enter로 저장, Esc로 닫기가 그대로 동작한다
- [ ] Backlog 폼의 `진척 가중치`도 같은 드롭다운이고, Story Point 칸은 **숫자 입력 그대로**다
- [ ] Backlog 목록에서 Epic·Task 행의 가중치가 `-`이고 Story·Bug만 등급을 보여 준다

**#3(WBS) — 새 범위라 특히 볼 것**

- [ ] WBS 항목 수정 폼에 **`규모` 드롭다운**이 있고, 새 항목·기존 항목 모두 **`미지정`이 선택**돼
      있다 (`보통`이 아니다 — 그러면 §2-c의 사고다)
- [ ] **아무 항목이나 열어 제목만 고쳐 저장했을 때 프로젝트 진척 숫자가 1도 안 바뀐다.**
      이것이 실체화 금지가 지켜졌는지 보는 가장 빠른 방법이다
- [ ] 형제 중 **하나**에만 등급을 넣으면 프로젝트 진척이 **바뀌지 않는다** — 선언값이 하나뿐이면
      비교 대상이 없어 미선언 형제가 같은 단위를 받는다(`6ddde45`가 만든 성질)
- [ ] 형제 중 **둘**에 서로 다른 등급을 넣으면 그때부터 비율이 반영된다
- [ ] `미지정`으로 되돌려 저장하면 값이 `null`로 돌아가고 진척도 원래대로다
- [ ] Summary 항목에도 등급을 넣을 수 있다. (넣은 뒤 그 값이 폼 말고는 안 보이는 것이 §2-g의
      알려진 사각지대다 — 버그가 아니다)
- [ ] 진척 탭의 열 이름이 `규모`이고 값이 `미지정`·`보통` 같은 라벨이다
- [ ] 프로젝트 화면 → 내보내기 → **WBS CSV의 `규모` 열이 라벨**이고 한글이 안 깨진다
- [ ] Backlog CSV의 `진척 가중치` 열도 라벨이다
- [ ] 다크 모드에서 드롭다운이 흰 배경으로 남지 않는다(`color-scheme`이 이미 처리하지만 눈으로 확인)
- [ ] **커밋 조회 모드에서 과거 커밋의 WBS·진척·Backlog 화면이 그대로 그려지고, 쓰기 진입점이
      잠긴다** — 확인할 것은 **수정 폼 자체가 열리지 않는 것**이지 셀렉트가 비활성인 것이 아니다.
      `WbsForm`·`BacklogForm` 에는 `readOnly` prop 이 **없고**, 잠금은 전부 상위에 있다
      (`WbsView.vue:141·148·155·175·192·202` 핸들러 가드 + `WbsTree` 툴바 `:disabled`,
      `BacklogView.vue:59·129·150·157` 동일). 플래그를 받는 유일한 폼인 `CheckpointList` 는
      `WbsTree.vue:1249` 가 `:editable="!readOnly"` 로 이미 넘긴다. **새 셀렉트에 게이팅을 넣지 말 것**
- [ ] 구형 export JSON을 가져오기 해도 거부되지 않고, 척도 밖 가중치가 살아서 들어온다

---

## 6. 이번에 하지 않는 것 (후속 후보)

1. **혼합 집합 점검.** §2-d대로 *일부만 선언된* 집합은 비율이 달라진다(`{null,10}`: `1:10`→`3:10`).
   실측 프로젝트에는 없었지만 다른 프로젝트는 여기서 확인할 수 없었다. 그런 집합을 찾아 보여 주는
   점검 화면이나 대시보드 카드가 후속 후보다.
2. **기준선 폴백(`ProgressService.java:234`)과 계층 vs 평면.** `baseline_items.weight`는
   `wbs_items.weight` 복사본인데 계획 진척은 트리를 타지 않고 Work Package를 **평면으로** 가중한다.
   그래서 같은 `null`이 실제 진척과 계획 진척에서 다른 몫을 뜻한다. 폴백만 맞추는 것은 더 깊은
   문제 위에 칠을 한 겹 더 하는 것이라, 계층화와 함께 다뤄야 한다.
3. **BacklogForm에서 Epic·Task의 가중치 칸 숨기기.** 유형을 중간에 바꿀 때의 처리가 붙는다.
4. **대시보드 "가중치 미지정" DataGap 되살리기.** 지금 `DashboardService:397-425`에는 네 종류만
   있다. 되살리면 기존 프로젝트가 첫날부터 전 항목 누락으로 뜨므로, 등급이 어느 정도 채워진
   뒤가 맞다.
5. **WBS 트리에 `규모` 열 추가.** §2-g의 사각지대를 없애는 정답이지만 `wbsColumns.ts`·열 설정·
   `pinnedSequence`까지 건드리게 되어 범위가 두 배가 된다.
6. **척도 확장(8:1을 넘는 격차).** 1일짜리와 3개월짜리가 형제로 나란한 트리에서는 8배로 모자랄
   수 있다. 실제로 부족하다는 사례가 나오면 13·21을 더하거나 공수(MD) 컬럼을 별도로 둔다.
7. **Story Point도 피보나치 선택으로.** 가중치와 다른 값이라 합치면 안 되지만, 선택지 방식은
   어울린다. 별건으로 다룬다.
8. **기준선 범위 문자열을 등급으로 읽히게 하기.** `ProgressService.java:289-293`이
   `"1.2 화면 (미입력 → 3)"`을 숫자로 만들고 `ProgressPanel.vue:198-200`·`BaselineCard.vue:44·59`가
   그대로 렌더한다. 이번 작업이 #3 입력을 되살리면서 **처음으로 실제로 보이게 된다** — 그전에는
   WBS 가중치를 만들 경로가 없어 기준선과 달라질 일이 자체가 없었다. 고치려면 프론트가 서버 문자열을
   파싱하거나(깨지기 쉽다) 백엔드에 어휘를 들여야 하는데(§2-h가 금지) 둘 다 나쁘다. 서버가 값만
   싣고 문장은 화면이 만들도록 응답 모양을 바꾸는 것이 정답이고, 그건 별건이다.
9. **가중치 합계 표시.** 한 Work Package 안의 등급 합과 각 항목의 몫(%)을 폼 옆에 보여 주면
   "내가 고른 등급이 실제로 몇 %인가"가 즉시 보인다. 이번 작업으로 값이 유한한 다섯 개가 되어
   처음으로 계산 가능해졌다.

---

## 7. 함정

1. **`weightForForm`·`weightLabel`을 WBS에 돌려쓰는 것.** 이 작업에서 가장 비싼 실수다.
   `weightForForm(null) === 3`이라 **제목만 고치려는 저장이 미지정을 `3`으로 박제**하고,
   `3 ≠ unitPerLeaf × leafCount`이므로 **그 프로젝트의 진척이 즉시 바뀐다.** WBS에는
   `wbsWeightLabel`을 쓰고 폼은 `null`을 그대로 돌려보낸다(§2-c). `weight.spec.ts`가
   `weightLabel(null) !== wbsWeightLabel(null)`로 이 경계를 고정한다.
2. **`WbsForm`의 셀렉트에 `v-model.number`를 붙이는 것.** `.number`가 `null`을 `0`으로 바꿀 수
   있고, `0`은 "집계 제외"라는 **다른 뜻**이다. `v-model`을 쓰고 `:value`로 숫자를 바인딩한다.
3. **레거시 값을 고른 채로 저장할 수 없게 만드는 것.** `<option disabled>`는 *고를 수 없다*는
   뜻이지 *선택 상태로 둘 수 없다*는 뜻이 아니다. 제목만 고치려는 저장이 가중치를 덮으면
   커밋 `da96ebe`와 같은 사고다. 세 화면 모두 테스트로 고정할 것.
4. **`DEFAULT_WEIGHT`와 백엔드 폴백이 어긋나는 것.** `shared/weight.ts`의 `3`과
   `ProgressCalculator.weightOf`의 `3`을 잇는 컴파일 타임 장치가 없다. 한쪽만 고치면 미지정 행이
   화면과 계산에서 다른 몫을 갖는다. 양쪽 주석에 서로를 적어 둘 것.

   **장치를 찾지 말 것 — 만들 수 없다.** 지금 있는 최선은 *"연결돼 있다"* 가 아니라
   **"양쪽에서 각각 잡힌다"** 이다: 프론트는 `weight.spec.ts`의 `expect(DEFAULT_WEIGHT).toBe(3)`,
   백엔드는 `missingWeightCountsAsMedium`(폴백을 1로 되돌리면 50.0 → 25.0으로 빨개진다).
   한쪽만 고치면 **반드시 둘 중 하나가 실패한다.** 두 단정을 "중복이니 하나로" 정리하면 그
   성질이 사라진다.
5. **`unitPerLeaf`까지 손대는 것.** 이름이 비슷하고 바로 아래 붙어 있지만 **WBS 형제 가중치**
   전용이고, 이 브랜치가 방금 고친 함수다. 거기 `leaves == 0 ? 1`의 `1`은 등급이 아니라 분모 0
   방지용 상수이며, 3으로 바꾸면 WBS 롤업 숫자가 통째로 움직인다.
6. **`ProgressService.java:234`의 기준선 폴백을 함께 바꾸는 것.** `baseline_items.weight`는
   `wbs_items.weight` 복사본이라 범위 밖이다(§6-2).
7. **서버에 척도 검증을 넣는 것.** 구형 export 파일 가져오기와 커밋 복원이 전부 400이 된다.
   레거시 값은 정상 데이터다.
8. **`<select>`에서 `@keydown.enter`·`@keydown.esc`를 빠뜨리는 것** — 체크포인트에 한해.
   그 폼은 `<div>`라 브라우저 기본 submit이 없고(`CheckpointList.vue:268-273` 주석), 그 핸들러가
   Enter 저장의 전부다. **`WbsForm`은 진짜 `<form>`이라 해당 없다.**
9. **`aria-label` 없이 `placeholder`만 없애는 것.** `<select>`에는 placeholder가 없어 접근 가능한
   이름이 사라진다. `WbsForm`은 `<label>`이 감싸므로 해당 없다.
10. **`select`에 로컬 스타일을 다시 적는 것.** 전역 컨트롤 층이 패딩·radius·focus 링·최소 타깃을
    이미 준다. 폭만 조정한다(CLAUDE.md 컨트롤 층).
11. **`weight.spec.ts`에 `// @vitest-environment happy-dom`을 붙이는 것.** 순수 함수라 DOM이
    필요 없다. 기본 환경은 `node`다.
12. **`WbsForm.spec.ts`의 11개를 지우고 새로 쓰는 것.** 그중 `가중치가 없던 항목을 저장하면 null
    그대로 나간다`는 §2-c를 고정하는 **가장 중요한 테스트**다. 표(§3-F1)대로 하나씩 처리할 것.
13. **`readOnly`(커밋 조회) 게이팅을 새 셀렉트에 빠뜨리는 것.** CLAUDE.md가 "아홉 개 화면의
    진입점이 같은 파생값 하나를 본다"고 못박아 둔 규칙이다. 새 쓰기 컨트롤이 그 규칙에서 새어
    나가면 안 된다.
