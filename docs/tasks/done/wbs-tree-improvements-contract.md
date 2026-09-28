# WBS 트리 개선 — 공유 계약 (Team Lead 확정)

원본 지시서: [`wbs-tree-improvements.md`](wbs-tree-improvements.md) — **반드시 먼저 통독할 것.**
이 문서는 그 지시서에 대한 **사용자 결정 반영분과 병렬 작업용 계약**만 적는다. 충돌하면 이 문서가 이긴다.

---

## 0. 사용자가 이번에 결정한 것 (지시서와 다른 부분)

### 0-1. 요구 1의 범위 = **WBS 폼 가중치 전부 제거, 체크포인트는 유지**

> **주의 — 이 절은 한 번 뒤집혔다.** 처음에는 사용자가 좁은 쪽(*"최상위 항목만 숨김, 하위 레벨은
> 그대로"*)을 골랐고 그대로 구현·배포됐다(`WbsForm`의 `isRoot`). **이후 사용자가 넓은 쪽으로
> 다시 확정했다** — WBS 폼의 가중치 입력은 레벨 무관 전부 삭제한다. 다만 체크포인트 가중치는
> 계속 유지한다(지시서 §2의 넓은 해석과도 이 점이 다르다). 아래 표가 최종이다.

지시서 §2 "요구 1의 범위 해석"은 *"WBS 항목 가중치 전부(레벨 무관) + 체크포인트 가중치"* 로 읽었다.
**사용자는 WBS 쪽은 넓게, 체크포인트는 좁게 골랐다.** 따라서:

| 지시서 항목 | 원래 | **최종 결정** |
|---|---|---|
| A-3 `WbsForm.vue` 가중치 입력 | 무조건 삭제 | **레벨 무관 전부 삭제.** `isRoot` 분기도 함께 사라진다. 단 `form.weight`는 payload에 계속 실어 보낸다(기존 값 보존) |
| A-4 `weightSuggestion.ts` 삭제 | 삭제 | **삭제한다** (`WbsForm`이 유일한 사용처였다) |
| A-5 `WbsView.vue`의 `siblings` | 삭제 | **삭제한다** (로컬 `findParent`도 함께. `wbsTree.ts`의 동명 export는 드래그앤드롭용이라 건드리지 않는다) |
| A-6 `CheckpointList.vue` 가중치 입력 | 삭제 | **삭제하지 않는다.** 단 거짓 라벨은 고친다(아래 0-2) |
| A-1 계산 폴백 | 넣는다 | **그대로 넣는다** (범위와 무관하게 필요) |
| A-2 테스트 | 바꾼다 | **그대로 바꾼다** |
| A-7 `WEIGHT_MISSING` gap 삭제 | 삭제 | **그대로 삭제한다** (폴백 이후 `null`은 정직한 1이지 누락이 아니다) |
| A-8 "또는" 문구 정리 | 고친다 | **그대로 고친다** |

### 0-2. 가중치가 남는 자리의 문구는 폴백에 맞춘다

A-1로 `null` 가중치의 뜻이 **"제외"에서 "1"로** 바뀐다. 그래서 가중치를 계속 받는 자리의 안내문이
전부 거짓이 된다. 값을 남기더라도 **설명은 반드시 고친다.**

| 위치 | 지금 (거짓) | 바꿀 뜻 |
|---|---|---|
| `WbsForm.vue:224-227` 고정 안내문 | "가중치를 비워 두면 … 하위 평균으로 집계됩니다" | 비워 두면 **1로 계산**된다. 형제 중 누구도 값이 없으면 하위 개수(leaf) 가중이 된다 |
| `WbsForm.vue:229-239` 제안 안내문 | 제안값 안내 | 폴백을 반영해 다시 쓴다. 제안 기능 자체는 유지 |
| `CheckpointList.vue:159` | `가중치 {{ cp.weight ?? '균등' }}` | `균등`이 아니라 **1**이다. `가중치 {{ cp.weight ?? 1 }}` 형태로 정직하게 |

### 0-3. 진행 범위 = **Phase A~D 전부**

---

## 1. 백엔드 계약 (프론트가 이 모양을 전제로 코딩한다)

### 1-A. `ProgressCalculator.rollUp` (Phase A)

```java
if (anyWeight) {
    weight = weightOf(declared);   // null → 1. continue 하지 않는다
} else {
    weight = leafCount(child);
}
```

- **`incompleteWeights`를 세우지 않는다.** 필드와 `incomplete()`는 남기되 항상 `false`.
- **`anyWeight` 분기는 유지한다.** 지우면 `LEGACY_ROLLUP`(leaf 개수 가중)이 사라져 기존 프로젝트
  숫자가 바뀐다 (지시서 부록 함정 3).
- `weightSum == 0` 가드는 그대로.

### 1-C. `WbsNodeResponse` 추가 필드 — 담당자 (Phase C)

```java
// application/dto/MemberRef.java (신규)
public record MemberRef(Long memberId, String name) {}
```

`WbsNodeResponse`에 **record 끝에 이어서** 추가:

```java
List<MemberRef> responsible,          // 이 행에 직접 배정된 RESPONSIBLE. 없으면 빈 리스트(null 아님)
List<MemberRef> responsibleInherited  // 상위에서 물려받은 것. 없으면 빈 리스트(null 아님)
```

- `RaciInheritance.resolve(tree, assignments)`가 돌려주는 `EffectiveRole` 하나를
  `source`로 갈라 담는다 — `OWN`이면 `responsible`, `INHERITED`면 `responsibleInherited`.
  **둘이 동시에 차는 일은 없다** (역할당 `EffectiveRole`이 하나다). 프론트는 그것을 전제해도 된다.
- `RaciRole.RESPONSIBLE`만 싣는다. `ACCOUNTABLE`은 싣지 않는다.
- `WbsService`는 `RaciService`를 부르지 않는다 — `RaciAssignmentRepository`·`ProjectMemberRepository`와
  도메인 `RaciInheritance`를 **직접** 쓴다 (application → application 호출은 `DashboardService`만의 예외).

### 1-D. `WbsNodeResponse` 추가 필드 — 태그 (Phase D)

```java
// application/dto/TagRef.java (신규)
public record TagRef(Long id, String name, String color) {}
```

```java
List<TagRef> tags,       // 이 행에 직접 붙은 태그. 없으면 빈 리스트
List<TagRef> tagSummary  // 하위(손자 포함)에 붙은 태그의 합집합. 자식이 없으면 null
```

`ExecutionModeSummary`와 같은 규칙이다 — **자식이 없으면 `null`**, 상위가 보관 중인 자기 값은
요약에 넣지 않는다.

### 1-D-2. 태그 마스터 API (Phase D)

| 메서드 | 경로 | 본문 / 응답 |
|---|---|---|
| `GET` | `/api/projects/{projectId}/wbs/tags` | → `List<WbsTagResponse>` |
| `POST` | `/api/projects/{projectId}/wbs/tags` | `WbsTagRequest` → **갱신된 전체 목록** |
| `PUT` | `/api/projects/{projectId}/wbs/tags/{tagId}` | `WbsTagRequest` → **갱신된 전체 목록** |
| `DELETE` | `/api/projects/{projectId}/wbs/tags/{tagId}` | → **갱신된 전체 목록** |

```java
public record WbsTagResponse(Long id, String name, String color, int sortOrder) {}
public record WbsTagRequest(String name, String color, Integer sortOrder) {}
```

- 목록 반환은 RACI·RAID와 같은 태도다 (부분 응답이면 클라이언트가 병합해야 한다).
- **경로가 `/wbs/tags`이므로 `/wbs/{itemId}`와 충돌하지 않게 매핑 순서를 확인할 것.**
  `{itemId}`가 `Long`이라 `tags`는 타입 불일치로 걸러지지만, 컨트롤러가 나뉘면 명시적으로 확인한다.

### 1-D-3. 항목에 태그 붙이기 — `tagIds`

`WbsItemCreateRequest`·`WbsItemUpdateRequest`에 `List<Long> tagIds` 추가.

> **`null` = 변경 없음. `[]` = 전부 해제.**
>
> 커밋 `da96ebe`·지시서 부록 함정 1과 같은 사고를 막는 유일한 방법이다 — 이 필드를 모르는
> 호출자(가져오기, 구형 클라이언트, 다른 화면)가 저장하면 태그가 조용히 전부 지워진다.
> **프론트 `WbsForm`은 항상 명시적으로 배열을 보낸다.**

- **수정은 차집합만 처리한다.** 전부 지우고 다시 넣지 않는다 (RAID 링크와 같은 이유, 함정 8).
- 다른 프로젝트의 태그 id → **400**.
- `nodeType == SUMMARY`인 항목의 태그는 **바꿀 수 없다**(400). 단 *요청 집합이 보관값과 같으면
  통과시킨다* — 실행 방식의 "지우지 않고 보관, 바꿀 수만 없음" 규칙을 그대로 따른다. 폼이 보관값을
  되돌려 보내도 거부되면 안 된다.
- 새 도메인 예외는 **`GlobalExceptionHandler`에 함께 등록한다** (함정 9).

### 1-D-4. 마이그레이션

`V23__create_wbs_tags_tables.sql` — 지시서 D-1의 DDL 그대로. **V22가 마지막이므로 V23이 맞다.**

### 1-D-5. 내보내기·가져오기

- `ExportService.FORMAT_VERSION` **6 → 7**, `ImportService.SUPPORTED_FORMAT_VERSION` **6 → 7**.
- `ProjectExportResponse`에 태그 마스터 목록 + WBS 항목별 `List<Long> tagIds` 추가.
- `ImportService`가 태그 id를 **재매핑**한다 (old→new 맵, 프로젝트·구성원과 같은 방식).
- `formatVersion` 6 이하 파일은 태그가 없는 것으로 읽는다(거부하지 않는다).

---

## 2. 프론트엔드 계약

### 2-C. `frontend/src/api/wbsApi.ts` — `WbsNode` 타입 추가

```ts
export interface MemberRef { memberId: number; name: string }
export interface TagRef { id: number; name: string; color: string | null }

// WbsNode 안
responsible: MemberRef[]
responsibleInherited: MemberRef[]
tags: TagRef[]
tagSummary: TagRef[] | null
```

### 2-C-2. `frontend/src/stores/scheduleCache.ts` — `raciRev` 신설

1. `const raciRev = ref(0)` + `export function markRaciChanged()`
2. `useRaci`의 **모든 변경**(배정·해제)에서 `markRaciChanged()` 호출
3. `wbsCacheKeyFor`에 **`memberRev`와 `raciRev`를 함께** 넣는다:
   `` `${projectId}:${revision.value}:${backlogRev.value}:${memberRev.value}:${raciRev.value}:${localToday()}` ``
4. `raciCacheKeyFor`에는 **넣지 않는다** — RACI의 모든 변경 API가 매트릭스 전체를 반환하므로
   자기 화면은 이미 최신이다. (지시서 C-2-4의 "불필요하면 넣지 않는다")
5. **`cacheKeyFor`(간트)·`progressCacheKeyFor`에는 넣지 않는다** — 담당자를 보여주지 않는다.
6. 태그 마스터가 바뀌면 트리의 칩 이름·색이 낡는다 → **태그 마스터 변경도 `markWbsChanged()`로
   충분하다**(트리 자체를 다시 읽으므로). 별도 리비전을 만들지 않는다.

### 2-CD. `WbsTree.vue` 열 수 — **9 → 11**

| 순서 | 열 |
|---|---|
| … 기존 9칸 … | |
| +1 | **담당자** (Phase C) |
| +1 | **분야** (Phase D) |

**`:588`의 `<td colspan="9">`를 `colspan="11"`로 함께 고친다.** 빠뜨리면 체크포인트 서랍이 표를
깨뜨린다 (함정 4). **C와 D를 한 사람이 연달아 하므로 한 번만 고친다.**

### 2-D. 태그 칩 색

- 하드코딩 색 **0건**. `frontend/src/style.css`에 토큰을 정의하고 컴포넌트는 `var(--…)`만 쓴다.
- `color`가 없으면 **이름 해시**로 팔레트에서 고른다. **랜덤 금지** (함정 7).
- 다크 모드에서 읽혀야 한다.

### 2-D-2. 태그 마스터 관리 UI

프로젝트 화면 행의 `구성원` 버튼 **옆에 `분야` 버튼**. `MemberEditor`가 자기 제목을 계산해
스스로를 `ModalDialog`로 감싸는 패턴을 그대로 따른다.

### 2-공통. 커밋 조회 잠금

새로 만드는 **모든 쓰기 진입점**(태그 추가·수정·삭제, 항목의 태그 변경)은
`stores/commitView.ts`의 `readOnly` 파생값 하나를 본다. 각자 판단하지 않는다.

---

## 3. 파일 소유권 (동시 편집 금지)

| 파일/영역 | 담당 |
|---|---|
| `domain/ProgressCalculator.java` + 그 테스트 | **be-progress** |
| `application/DashboardService.java` + 대시보드 테스트 | **be-progress** |
| `features/dashboard/dashboardFormat.ts` + `.spec.ts` | **be-progress** (`WEIGHT_MISSING` 분기 정리) |
| `application/WbsService.java`, `dto/WbsNodeResponse.java`, `dto/MemberRef.java`, `dto/TagRef.java` | **be-wbs** |
| 태그 전 계층(`domain/WbsTag*`, `infrastructure/persistence/**Tag**`, `application/WbsTagService`, `presentation/**`), `V23`, `ExportService`, `ImportService`, `GlobalExceptionHandler` | **be-wbs** |
| `features/wbs/WbsForm.vue`, `views/WbsView.vue`, `features/progress/CheckpointList.vue`, `checkpointForm.ts` | **fe-ab** (1차) |
| `features/wbs/WbsTree.vue`, `api/wbsApi.ts`, `stores/scheduleCache.ts`, `features/raci/**`, 태그 컴포넌트 신규, `views/ProjectsView.vue`, `style.css` | **fe-cd** (2차) |
| `WbsForm.vue`의 **태그 다중 선택 추가** | **fe-cd** (2차, fe-ab가 끝난 뒤에 들어간다) |

**`WbsForm.vue`는 1차에 fe-ab가, 2차에 fe-cd가 만진다. 절대 동시에 열지 않는다.**

---

## 4. 검증

```bash
cd backend  && ./gradlew test
cd frontend && npm run build && npm test
```

**Phase A의 완료 기준은 "숫자가 하나도 안 바뀌는 것"이다.** 가중치가 0건인 프로젝트
(호스팅 서버 AEGIS, id=34)의 진척 **38.75%** 가 그대로여야 한다. `legacyWeightingWhenNoWeights`
테스트가 통과하지 않으면 `anyWeight` 분기를 잘못 건드린 것이다.

나머지 체크리스트는 지시서 §5를 그대로 쓴다. 단 Phase A 체크리스트의
"WBS 폼에 가중치 입력이 없고" 는 **"어느 레벨에서도 없다"** 로 읽는다(§0-1의 뒤집힌 결정).
체크포인트 가중치 입력은 그대로 있어야 한다 — 그쪽은 삭제 범위가 아니다.
