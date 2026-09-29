# 부분 가중치가 형제를 진척 집계에서 떨어뜨리는 결함을 고친다

`ProgressCalculator.rollUp()`에서 **형제 중 하나라도 가중치가 있으면 가중치 없는 형제가 평균에서
제외**된다. 제외를 없애고 **선언된 형제 가중치의 평균**으로 폴백한다.

- 계산 로직만 바꾼다. 스키마 변경 없음, 마이그레이션 없음, API 필드 추가·삭제 없음.
- **가중치가 하나도 없는 프로젝트의 화면 숫자는 전혀 바뀌지 않아야 한다.** 그것이 검증 방법이다.
- 가중치 입력 UI는 **그대로 둔다**(사용자 결정).

---

## 1. 해결할 문제

### 1-a. 형제가 조용히 탈락한다

`backend/src/main/java/com/projectflow/domain/ProgressCalculator.java`

```java
// :184
boolean anyWeight = children.stream().anyMatch(child -> child.item().getWeight() != null);

// :203-212
if (anyWeight) {
    if (declared == null) {
        incompleteWeights = true;
        continue;                    // ← 이 형제는 분자·분모 어디에도 들어가지 않는다
    }
    weight = declared;
} else {
    weight = leafCount(child);
}
```

살아남은 자식이 하나뿐이면 분자·분모에 같은 `w`가 들어가 **약분된다.** 그래서 **가중치로 무엇을
적든 결과가 같고**, 그 자식의 진척이 곧 가지 전체의 진척이 된다. `30`을 적은 사람은 "30만큼의
비중"을 준다고 믿지만 실제로는 "이것만 본다"를 선언한 것이다.

### 1-b. 실측 — 호스팅 서버의 실제 프로젝트

`http://192.168.60.70:5151` · 프로젝트 **AEGIS**(id=34) · 기준일 2026-09-16

- Work Package 10개, 체크포인트 8개 — **가중치 입력 0건**(전부 `null`)
- 그래서 현재는 전 구간 `LEGACY_ROLLUP`, 프로젝트 진척 **38.75%**
  (검산: `1`=42.5×leaf5 + `2`=50×2 + `3`=0×1 + `6`=25×1 + `7`=50×1 = 387.5, 분모 10.
  `4`·`5`는 자식 없는 Summary라 `percent==null`이라 양쪽에서 빠진다.)

여기서 **최상위에 항목 하나를 가중치와 함께 추가하면**:

| 상황 | 현행 결과 |
|---|---|
| 새 항목에 진척이 있음 | `1`~`7`이 전부 탈락 → **프로젝트 진척 = 새 항목 하나의 값** |
| 새 항목이 Agile이고 Story 0건 | `percent == null`이라 그것도 탈락 → `weightSum == 0` → **프로젝트 진척이 "산정 전"(빈칸)** |
| 가중치를 `0` 하나만 입력 | `anyWeight`는 `!= null` 검사라 켜짐 → **그 가지 전체가 산정 전** |

### 1-c. 그리고 화면에 드러나지 않는다

| 지점 | 문제 |
|---|---|
| `ProgressCalculator.java:267` | `incomplete()`가 `incompleteWeights \|\| incompleteChildren`로 **OR 합침** |
| `application/dto/WbsNodeResponse.java:141` | 그 합친 값이 `progressIncomplete` **한 boolean**으로 나감 — API 밖에서 두 사건을 구분할 수 없다 |
| `frontend/src/features/wbs/WbsTree.vue:524` | 뱃지 하나이고 `title`이 *"일부 하위가 산정 전**이거나** 가중치가 없습니다"* 로 두 경우를 묶음 |
| `frontend/src/features/progress/ProgressPanel.vue:207` | 같은 "또는" 문구가 하나 더 |
| `application/DashboardService.java:383` | `WEIGHT_MISSING`이 `weight() == null`인 Work Package를 **전부** 셈 |

**AEGIS의 노드 `1`은 이미 `progressIncomplete: true`다**(`1.1`이 `NOT_ESTIMABLE`). 그래서 위 사고가
나도 뱃지가 달라지지 않는다. 대시보드 `WEIGHT_MISSING`도 지금 "10"으로 떠 있는데 그 상태는 완전히
정상이라, **안전한 상태와 위험한 상태가 같은 숫자를 낸다.**

### 1-d. 같은 `null`이 네 곳에서 다르게 읽힌다

| 어디 | `weight`가 `null`이면 | 근거 |
|---|---|---|
| 체크포인트 (Waterfall 분모) | **1로 취급** | `ProgressCalculator.java:140` → `weightOf` `:239-241` |
| Backlog `progress_weight` (Agile 분모) | **1로 취급** | `:122` → `weightOf` `:239-241` |
| 기준선 (계획 진척) | **1로 취급** | `application/ProgressService.java:234` |
| **WBS 롤업** | **제외** | `:184`, `:203-207` |

게다가 `baseline_items.weight`는 `wbs_items.weight`를 **그대로 복사한 값**이다
(`application/ProgressBasisService.java:242`). **같은 컬럼의 같은 `null`이 실제 진척에서는 "빼라",
계획 진척에서는 "1로 쳐라"를 뜻한다.**

---

## 2. 결정된 방향

### 폴백은 `1`이 아니라 **선언된 형제 가중치의 평균**이다

가중치는 **상대값**이고 합 제약이 없다(`V15__add_wbs_progress_basis.sql:18-19`는 `weight >= 0`만
본다). 계산도 `weighted / weightSum`이라 **배수를 곱해도 결과가 같아야 한다.** 폴백 `1`은 이 성질을
깨뜨린다.

| 형제 가중치 | 폴백 `1` | 폴백 **평균** |
|---|---|---|
| `[10, 20, null]` | `[10, 20, 1]` | `[10, 20, 15]` |
| `[100, 200, null]` | `[100, 200, 1]` | `[100, 200, 150]` |
| **같은 비율인가** | ❌ 전혀 다름 | ✅ 동일 |

폴백 `1`에서는 *"`10`을 적었나 `100`을 적었나"* 가 결과를 바꾼다. 같은 뜻이어야 할 두 입력이 다른
답을 낸다. 평균은 그 성질을 지킨다.

> 체크포인트·Backlog가 `1` 폴백을 쓰는 것은 **그쪽 형제들이 균질하기 때문**이다(한 Work Package
> 안의 같은 종류 항목). WBS 형제는 가지 크기가 제각각이라 상황이 다르다. "네 곳을 `weightOf`로
> 통일한다"는 논거는 여기서는 약하다.

평균 폴백은 **"하나만 적으면 아무것도 안 적은 것과 거의 같다"** 는 성질도 갖는다 — 작은 행동이
작은 결과를 낸다. 자식이 전부 leaf면 `leafCount` 가중 = 단순 평균 = 균등 폴백이라 **숫자가 아예
움직이지 않는다.**

### 하지 않는 것과 이유

| 하지 않음 | 이유 |
|---|---|
| **`anyWeight` 분기 제거** | 지우면 가중치가 하나도 없을 때도 균등이 되어 `leafCount` 가중이 사라진다. **기존 프로젝트의 숫자가 바뀐다.** CLAUDE.md의 전환 정책("아무것도 지정하지 않은 프로젝트의 화면 숫자는 Step 5 이전과 같다")이 깨진다 |
| **가중치 입력 UI 제거** | 사용자 결정 — 지금처럼 둔다 |
| **`incompleteWeights` 필드·`incomplete()` 제거** | 필드를 지우면 응답 shape이 바뀌어 **과거 커밋**(`computed_payload`)을 그리는 경로가 흔들린다. 값만 항상 `false`가 되게 하고 필드는 남긴다 |
| **API 플래그 분리**(`progressIncompleteWeights` 등) | 폴백을 넣으면 `incompleteWeights`가 참이 될 수 없으므로 분리할 대상이 없다 |
| **가중치에 합=100 제약 도입** | 상대값이라는 성질을 바꾸는 별건 |
| **계산식의 다른 부분** | `agileProgress`·`waterfallProgress`·`hybridProgress`·`MANUAL`은 손대지 않는다 |
| 체크포인트 제거 · Work Package 하위 구조 · `nodeType` 제거 · 0/100 강제 | 논의했으나 **전부 접었다**(사용자 결정). 구조는 현행 유지 |

---

## 3. 작업

### A-1. `backend/src/main/java/com/projectflow/domain/ProgressCalculator.java` — 폴백

`rollUp()`(`:183` 부근부터)을 아래처럼 바꾼다.

**before** (`:184-226` 발췌)

```java
boolean anyWeight = children.stream().anyMatch(child -> child.item().getWeight() != null);
boolean incompleteChildren = false;
boolean incompleteWeights = false;
...
    Integer declared = child.item().getWeight();
    double weight;
    if (anyWeight) {
        if (declared == null) {
            // 조용히 빼지 않고 불완전으로 알린다 (지시서 5-B).
            incompleteWeights = true;
            continue;
        }
        weight = declared;
    } else {
        weight = leafCount(child);
    }
    weighted += weight * childResult.percent();
    weightSum += weight;
}

if (weightSum == 0) {
    return new ProgressResult(null, ProgressBasis.NOT_ESTIMABLE, incompleteWeights, true, ...);
}
return new ProgressResult(weighted / weightSum,
        anyWeight ? ProgressBasis.ROLLUP : ProgressBasis.LEGACY_ROLLUP,
        incompleteWeights, incompleteChildren, null);
```

**after**

```java
boolean anyWeight = children.stream().anyMatch(child -> child.item().getWeight() != null);
double fallbackWeight = evenFallback(children);   // 루프 밖에서 한 번만
boolean incompleteChildren = false;
// incompleteWeights 지역변수는 사라진다 — 더 이상 참이 될 경로가 없다.
...
    Integer declared = child.item().getWeight();
    double weight = anyWeight
            ? (declared != null ? declared : fallbackWeight)
            : leafCount(child);
    weighted += weight * childResult.percent();
    weightSum += weight;
}

if (weightSum == 0) {
    return new ProgressResult(null, ProgressBasis.NOT_ESTIMABLE, false, true, ...);
}
return new ProgressResult(weighted / weightSum,
        anyWeight ? ProgressBasis.ROLLUP : ProgressBasis.LEGACY_ROLLUP,
        false, incompleteChildren, null);
```

새 헬퍼를 `weightOf`(`:239-241`) 옆에 둔다.

```java
/**
 * 미선언 형제에게 줄 가중치 — 선언된 <b>양수</b> 가중치의 평균.
 *
 * <p>가중치는 상대값이라(합 제약이 없다) 배수를 곱해도 결과가 같아야 한다. 고정값 1로 폴백하면
 * {@code [10, null]}과 {@code [100, null]}이 다른 비율이 되어 그 성질이 깨진다. 평균은 지킨다.
 *
 * <p>{@code 0}은 "진척에 기여하지 않음"이라는 별도의 뜻이라 평균에서 뺀다 — 포함시키면 0 하나가
 * 미선언 형제들의 몫까지 끌어내린다. 프론트엔드의 {@code weightSuggestion.ts}도 같은 규칙으로
 * {@code weight > 0}만 센다.
 *
 * <p>양수 선언값이 하나도 없으면(전부 {@code 0}이거나 전부 미선언) 1을 쓴다. 0을 돌려주면
 * 분모가 0이 되어 가지 전체가 "산정 전"이 된다.
 */
private static double evenFallback(List<WbsNode> children) {
    double sum = 0;
    int count = 0;
    for (WbsNode child : children) {
        Integer declared = child.item().getWeight();
        if (declared != null && declared > 0) {
            sum += declared;
            count++;
        }
    }
    return count == 0 ? 1 : sum / count;
}
```

`rollUp`의 javadoc(`:175-182`)도 함께 고친다 — 지금은 *"With weights present, children that lack one
are left out of the average and reported through `incompleteWeights` rather than being treated as
zero"* 라고 적혀 있어 새 동작과 정반대다.

**`weightOf`(`:239-241`)는 그대로 둔다** — 체크포인트(`:140`)와 Backlog(`:122`)가 계속 쓴다.

### A-2. `ProgressResult.incompleteWeights` — 필드는 유지, 값은 항상 `false`

`:257`의 필드와 `:266-268`의 `incomplete()`는 **건드리지 않는다.** 응답 shape을 유지해 과거 커밋
(`computed_payload`)을 그리는 경로가 흔들리지 않게 한다. 다만 `incomplete()`의 javadoc에 *"현재
`incompleteWeights`는 항상 false다 — 미선언 가중치는 평균으로 채워지므로 불완전이 아니다"* 를
한 줄 남긴다.

### A-3. `backend/src/test/java/com/projectflow/domain/ProgressCalculatorTest.java`

**깨지는 테스트는 `partialWeightsAreReported`(`:174-185`) 하나뿐이다.** 나머지 다섯은 그대로 통과한다
(아래 §5의 표 참고). 이름·`@DisplayName`·주석의 판단 근거까지 함께 바꾼다.

```java
@Test
@DisplayName("일부 자식만 가중치가 있으면 나머지를 선언값 평균으로 채운다 — 빼지 않는다")
void partialWeightsFallBackToDeclaredAverage() {
    WbsItem parent = summary("단계");
    manual(parent, "가중치 있음", 100, 10);
    manual(parent, "가중치 없음", 0, null);

    // 빼면 100%가 되는데, 자식이 100%/0%인 상황에서 그것도 근거 없는 숫자다.
    // 선언값이 10 하나뿐이므로 미선언 자식도 10 → (10×100 + 10×0) / 20 = 50.
    assertThat(percentOf(parent)).isEqualTo(50.0);
    assertThat(resultOf(parent).incompleteWeights()).isFalse();
    assertThat(basisOf(parent)).isEqualTo(ProgressBasis.ROLLUP);
}
```

**새 테스트 셋을 추가한다.** 기대값 표기는 기존 `keepsPrecision`(`:220-228`)의 `isEqualTo(100.0 / 3)`
관습을 따른다.

```java
@Test
@DisplayName("폴백은 상대값 성질을 지킨다 — 가중치에 배수를 곱해도 결과가 같다")
void fallbackPreservesRatio() {
    WbsItem a = summary("A");
    manual(a, "가", 100, 10);
    manual(a, "나", 0, 20);
    manual(a, "다", 0, null);       // 폴백 = (10+20)/2 = 15

    WbsItem b = summary("B");
    manual(b, "가", 100, 100);
    manual(b, "나", 0, 200);
    manual(b, "다", 0, null);       // 폴백 = 150

    // A: (10×100) / (10+20+15) = 1000/45,  B: (100×100) / (100+200+150) = 10000/450
    assertThat(percentOf(a)).isEqualTo(1000.0 / 45);
    assertThat(percentOf(b)).isEqualTo(percentOf(a));
}

@Test
@DisplayName("0은 평균에서 뺀다 — 진척에 기여하지 않는다는 뜻이지 '작다'가 아니다")
void zeroIsExcludedFromTheAverage() {
    WbsItem parent = summary("단계");
    manual(parent, "빼달라", 100, 0);
    manual(parent, "센다", 100, 10);
    manual(parent, "미입력", 0, null);   // 폴백 = 10 (0은 평균에서 제외)

    // (0×100 + 10×100 + 10×0) / (0+10+10) = 1000/20
    assertThat(percentOf(parent)).isEqualTo(50.0);
}

@Test
@DisplayName("양수 선언값이 하나도 없으면 폴백은 1 — 분모가 0이 되면 안 된다")
void allZeroDeclaredFallsBackToOne() {
    WbsItem parent = summary("단계");
    manual(parent, "빼달라", 100, 0);
    manual(parent, "미입력", 40, null);   // 양수 선언값 없음 → 폴백 1

    // (0×100 + 1×40) / (0+1) = 40
    assertThat(percentOf(parent)).isEqualTo(40.0);
}
```

> **기대값을 베끼지 말고 직접 계산해 확인할 것.** 위 산술은 `evenFallback`이 "양수 선언값의 평균,
> 없으면 1" 이라는 정의를 전제한다. 구현이 다르면 값도 달라진다.

### A-4. 화면 문구 세 곳 — "또는"을 없앤다

`incompleteWeights`가 더 이상 참이 되지 않으므로 사유가 하나뿐이다.

**`frontend/src/features/wbs/WbsTree.vue:524`**

```html
<!-- before -->
title="일부 하위가 산정 전이거나 가중치가 없습니다."
<!-- after -->
title="일부 하위가 아직 산정 전입니다."
```

뱃지 글자("불완전")는 그대로 둔다.

**`frontend/src/features/progress/ProgressPanel.vue:207-210`**

```html
<!-- before -->
일부 하위가 산정 전이거나 가중치가 없어 <strong>집계가 불완전</strong>합니다. 위 숫자는
셀 수 있는 부분만을 말합니다.
<!-- after -->
일부 하위가 아직 산정 전이라 <strong>집계가 불완전</strong>합니다. 위 숫자는
셀 수 있는 부분만을 말합니다.
```

**`frontend/src/features/wbs/WbsForm.vue:224-227`** — 지금 문장은 부분 입력 상황에서 **거짓**이다
("비워 두면 하위 평균으로 집계됩니다"는 형제가 모두 비었을 때만 참).

```html
<!-- after -->
<p class="hint muted">
  형제가 모두 비어 있으면 하위 leaf 개수로 집계합니다. 형제 중 누군가 가중치를 입력하면,
  비워 둔 항목은 입력된 값들의 평균으로 봅니다. 0은 "진척에 기여하지 않음"이라 미입력과 다릅니다.
</p>
```

`:229-239`의 제안 안내문(`weightSuggestion`)은 그대로 둔다 — 제안 로직(`weight > 0`만 세는 평균·기간
비례)이 새 폴백 규칙과 어긋나지 않는다.

### A-5. `backend/src/main/java/com/projectflow/application/DashboardService.java` — gap 제거

`:377-384`의 `WEIGHT_MISSING` 블록을 **삭제한다.** 이제 미입력 가중치가 평균으로 채워지므로
"누락"이 아니고, 지금은 정상 상태에서 항상 울려(AEGIS "10건") 아무 신호도 되지 못한다.

`DataGap` 레코드와 다른 gap 종류(`EXECUTION_MODE_UNSPECIFIED`·`NOT_ESTIMABLE`·`BACKLOG_UNLINKED`·
`BACKLOG_LINK_BROKEN`)는 **그대로 둔다.**

프론트엔드는 손댈 필요가 없다 — `GapsCard.vue`가 `v-for="gap in data.gaps"`로 일반 렌더한다.
다만 대시보드 테스트 픽스처(`frontend/src/features/dashboard/dashboardFixture.ts` 등)에
`WEIGHT_MISSING`이 들어 있으면 함께 정리한다.

---

## 4. 건드리지 않는 것

| 대상 | 이유 |
|---|---|
| `wbs_items.weight` · `acceptance_checkpoints.weight` 컬럼과 마이그레이션 | 계산만 바꾼다. `baseline_items`·커밋 `raw_payload`·내보내기 JSON이 이미 참조한다 |
| `weightOf`(`:239-241`) | 체크포인트·Backlog가 계속 쓴다 |
| `agileProgress`(`:115-`) · `waterfallProgress`(`:136-`) · `hybridProgress` | 이미 `weightOf` 폴백을 쓴다 |
| `ProgressService.plannedProgress`(`:211-247`) | 이미 `1` 폴백이다(`:234`). 계층 vs 평면 문제는 §6 |
| `ProgressResult`의 필드·`incomplete()`·`displayPercent()` | 응답 shape 유지 — 과거 커밋 호환 |
| `backlog_items.progress_weight` | 이미 `1` 폴백이라 안전하다. 별건 |
| `weightSuggestion.ts` | 제안 로직이 새 규칙과 어긋나지 않는다 |
| 가중치 입력 UI · 체크포인트 · `nodeType` · 실행 방식 | 사용자 결정 — 현행 유지 |

---

## 5. 확인 방법

```bash
cd backend  && ./gradlew test
cd frontend && npm run build && npm test
```

### 기존 테스트 판정 (전수 확인 완료)

| 테스트 | 줄 | 수정 후 | 왜 |
|---|---|---|---|
| `legacyWeightingWhenNoWeights` | `:158` | ✅ 통과 | 가중치 0건 → `anyWeight=false` → `leafCount` 그대로 |
| `partialWeightsAreReported` | `:174` | ❌ **재작성 대상** | `100.0` → `50.0` |
| `notEstimableChildPropagates` | `:186` | ✅ 통과 | 둘 다 `weight=10` 선언 — 폴백이 개입하지 않는다 |
| `allChildrenNotEstimable` | `:199` | ✅ 통과 | 둘 다 `percent==null`이라 가중치 검사 전에 빠진다 |
| `parentManualProgressIgnored` | `:210` | ✅ 통과 | 자식 하나뿐이고 `weight=null` → `anyWeight=false` |
| `keepsPrecision` | `:220` | ✅ 통과 | `[1, 2]` 둘 다 선언 — 폴백 불필요 |

### 수동 확인 — 중요한 순서대로

- [ ] **가중치가 하나도 없는 프로젝트의 숫자가 작업 전과 완전히 동일하다.**
      AEGIS 기준: 프로젝트 **38.75%**, `1`=42.5, `1.2`=42.5, `2`=50.0, `3`=0.0, `6`=25.0, `7`=50.0.
      `progressBasis`도 전부 `LEGACY_ROLLUP` / `MANUAL` / `WATERFALL` / `NOT_ESTIMABLE` 그대로여야 한다
- [ ] 형제 일부에만 가중치가 있는 가지를 만들어(진척 탭의 `PUT .../progress/work-packages/{id}/basis`
      또는 WBS 폼) **빠지는 자식 없이** 평균으로 채워져 계산된다
- [ ] **최상위 항목 하나에 가중치를 넣어도 프로젝트 진척이 그 항목 값으로 바뀌지 않는다**
- [ ] 그 최상위 항목이 Agile이고 Story가 0건이어도 **프로젝트 진척이 "산정 전"이 되지 않는다**
- [ ] 가중치를 `0` 하나만 넣어도 그 가지가 "산정 전"이 되지 않는다
- [ ] WBS 폼에서 저장해도 **기존 `weight` 값이 지워지지 않는다**(폼은 전체를 보내므로)
- [ ] WBS 트리 뱃지·진척 탭 문구가 "산정 전" 단일 사유로 읽힌다
- [ ] 대시보드에서 "가중치가 없는 Work Package" 카드가 사라졌다
- [ ] **커밋 조회 모드에서 과거 커밋의 WBS·진척·대시보드 화면이 그대로 그려진다**
      (응답 shape을 안 바꿨으므로 깨지면 안 된다)

---

## 6. 이번에 하지 않는 것 (후속 후보)

**6-1. 계층 vs 평면 — 같은 가중치가 두 계산에서 다른 몫을 뜻한다.**
실제 진척(`rollUp`)은 **직계 형제 사이** 상대값인데, 계획 진척(`ProgressService.plannedProgress`
`:219-238`)은 트리를 타지 않고 **프로젝트 전체 Work Package를 평면으로** 가중한다(`:198-200` 주석이
인정하고 있다). Summary의 가중치는 평면 쪽에서 **통째로 무시**된다(`:220-222`). 그래서
`actualPercent`와 `comparablePercent`가 같은 가중치로 다른 몫을 만든다. 고치려면 `baseline_items`에
계층 정보를 넣거나 계획 쪽도 트리로 계산해야 한다. **이번 수정으로 왜곡 폭은 줄어든다** — 가중치가
안 들어오면 양쪽 다 균등이다.

**6-2. `MANUAL`(실행 방식 미지정) 정리.**
AEGIS의 Work Package 10개 중 5개가 미지정이고 진행률이 손입력이다 — `1.2.1`(100)·`1.2.2`(30)·
`1.2.3`(30)·`1.2.4`(10)·`7`(50). 실무가 기피하는 `% complete` 방식이고 프로젝트 진척의 상당 부분을
만든다. 가중치를 아무리 정교하게 해도 그 위에 얹히는 숫자가 감이면 의미가 없다.

**6-3. 데이터 정리 (코드 아님).**
- `2.2 히스토그램` — 입력 진행률 `0`인데 계산값 `100`(체크포인트 1/1 승인). `2.1`은 반대로 입력 `10`에 계산 `0`
- 기준선 v1이 **항목 2개**뿐(현재 16개) → 계획 대비 편차가 `null`, 대시보드 "계획 대비"가 계속 "산정 전"
- 자식 없는 Summary 2개(`4 IBBU`, `5 Klear View`) → 진척 미산정, Work Package 집계에서 빠짐

**6-4. 가중치의 단위를 정의한다.** 지금은 상대값이고 합 제약이 없어 `30`에 절대적 의미가 없다.
합=100 제약이나 공수(MD) 컬럼 도입은 별건.

---

## 7. 함정

1. **`anyWeight` 분기까지 지우는 것** — 지우면 가중치가 하나도 없을 때도 균등(평균→1)이 되어
   `leafCount` 가중이 사라진다. **기존 프로젝트의 숫자가 바뀐다.** 전환 정책이 깨진다.
2. **폴백을 `1`로 하는 것** — 배수 불변이 깨진다(§2). `weightOf`를 재사용하고 싶은 유혹이 있는데,
   그 함수는 체크포인트·Backlog 전용으로 남긴다.
3. **`evenFallback`을 루프 안에서 계산하는 것** — 부모마다 한 번이면 된다. 루프 안에 두면 O(n²).
4. **평균에 `0`을 포함시키는 것** — `0` 하나가 미선언 형제들의 몫까지 끌어내린다.
5. **양수 선언값이 없을 때 `0`을 돌려주는 것** — 분모가 0이 되어 가지 전체가 "산정 전"이 된다.
   `count == 0 ? 1 : ...` 가드를 빠뜨리지 말 것.
6. **`incompleteWeights` 필드를 지우는 것** — 응답 shape이 바뀌어 과거 커밋 렌더가 흔들린다.
   값만 항상 `false`가 되게 한다.
7. **`weight`를 WBS 폼 payload에서 빼는 것** — 폼은 저장 시 전체를 보내므로 기존 값이 `null`로
   덮인다(커밋 `da96ebe`와 같은 종류의 사고).
8. **`rollUp`의 javadoc을 그대로 두는 것** — 지금 주석이 새 동작과 **정반대**를 설명한다.
