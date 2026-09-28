# RACI 화면의 모든 클릭이 죽는다 — 범례 글자의 히트 영역이 화면을 덮는다

대상 체크아웃: `D:\git\ScheduleMgr` (main `4572a29` "wbs-tree-density 지시서를 done 으로 옮긴다")
모든 줄번호는 그 커밋에서 확인한 값이다.

사용자 보고: **"RACI 화면에서 Refresh를 하거나 다른 화면 갔다가 오면 갑자기 모든 이벤트가 화면에서
안 먹는다."**

---

## 1. 무엇이 일어나고 있나

원인이 확정됐다. **범례(legend)의 글자 견본이 `<span class="letter">`인데, 히트 영역 확장용
`::after`가 그 span에 붙어 문서 첫 화면 전체를 투명하게 덮고 클릭을 전부 먹는다.**

### 1-1. 코드

```
RaciMatrix.vue:377-381
.letter::after {
  content: '';
  position: absolute;
  inset: -0.075rem;
}
```

`RaciMatrix.vue:372-376`의 주석이 의도를 적어 놨다 — *"히트 영역만 24×24px로 키운다(WCAG 2.5.8).
투명한 `::after`를 버튼 밖으로 살짝 넘치게 그려 클릭·탭 판정 영역만 넓힌다."* 표의 글자
버튼(`RaciMatrix.vue:168-190`, `<button class="letter">`)에는 이것이 정확히 의도대로 동작한다 —
전역 `button { position: relative }`(`style.css:532-533`)이 기준을 그 버튼으로 잡아 주기 때문이다.

**그런데 `.letter`를 쓰는 곳이 하나 더 있다.** 범례다.

```
RaciMatrix.vue:198-211
<div v-if="hasColumns && data.tasks.length > 0" class="legend">
  <span v-for="role in RACI_ORDER" … class="legend-item" …>
    <span class="letter held" :data-role="role">…</span>   ← span, 4개
  </span>
  <span class="legend-item">
    <span class="letter inherited" data-role="ACCOUNTABLE">A</span>   ← span, 1개
    상위에서 상속
  </span>
  …
</div>
```

`<span>`은 `button`이 아니므로 `position: relative`를 받지 못한다(`.letter` 자신도 `position`을
선언하지 않는다 — `RaciMatrix.vue:361-370`은 `width`·`height`·`padding`·`border-radius`·색·글꼴
뿐이다). 그래서 이 span들의 `::after`는 `position: absolute`의 기준을 찾아 올라가다 **어떤
positioned 조상도 만나지 못하고 초기 컨테이닝 블록(ICB)에 도달한다.** ICB는 문서 원점에 붙은
뷰포트 크기의 사각형이므로, `inset: -0.075rem`은 **문서 맨 위 한 화면 전체**를 덮는다 — 상단
메뉴·제목·프로젝트 선택까지.

그 층이 **다섯 장** 쌓이고(범례 span 5개), 전역 상태 레이어(`style.css:557-564` `button::before`)와
달리 **`pointer-events: none`이 없다.** 있으면 안 된다 — 클릭을 *받는* 것이 이 `::after`의 존재
이유다. 그래서 화면 위의 모든 클릭이 범례의 글자 견본에 떨어진다. 그 span에는 핸들러가 없으니
아무 일도 일어나지 않는다.

### 1-2. 관찰과 전부 맞는다

| 사용자가 본 것 | 왜 |
|---|---|
| 모든 이벤트가 안 먹음 (상단 메뉴까지) | ICB 사각형이 문서 첫 화면 전체를 덮는다 |
| 콘솔에 아무 오류도 없음 | 순수 CSS다. 던지는 것이 없다 |
| Network에 요청이 아예 안 나감 | 클릭이 핸들러에 도달하지 않는다 |
| 디버거 ⏸ 를 눌러도 멈추지 않음 | 스레드는 멀쩡하다. 무한 반복이 아니다 |
| Elements에서 최상단이 `::after` | 바로 이것이다 |
| `document.elementFromPoint(60, 20)` → `<span class="letter inherited" data-role="ACCOUNTABLE">` | 상단 메뉴 자리를 범례 글자가 점유하고 있다 |

### 1-3. "Refresh하거나 다른 화면 갔다가 오면"이 조건인 이유

범례는 `v-if="hasColumns && data.tasks.length > 0"`(`:198`)이다. `useRaci`의 `data`는 모듈 스코프
캐시이고 초기값이 `EMPTY`(`useRaci.ts:16-19`)다.

- **차가운 첫 방문**: `data`가 EMPTY라 매트릭스도 범례도 렌더되지 않는다 → 응답이 오기 전까지
  화면이 정상이다. 그래서 "처음엔 됐다"로 느껴진다.
- **Refresh / 다른 화면 갔다 오기**: 모듈 스코프 캐시가 이미 채워져 있어(또는 저장된 프로젝트
  선택으로 즉시 로드되어) **첫 페인트부터 범례가 있다** → 도착하는 순간 죽는다.

이 화면에서만 나타나는 이유도 같다. 히트 영역 `::after`를 쓰는 다른 다섯 곳
(`SprintBoard.vue:502` `.link`, `WbsTree.vue:1473` `.chip`, `:1731` `.toggle`, `:1906` `.cp-badge`,
`:1984` 툴바 필터 버튼)은 **대상이 전부 `<button>`**이어서 전역 규칙에 갇힌다(확인함:
`SprintBoard.vue:270`, `WbsTree.vue:964-966`·`:976-978`·`:1061-1063`·`:1168-1170`).

---

## 2. 결정된 방향

**두 가지를 함께 한다.** 하나만 하면 같은 사고가 다시 난다.

### 2-1. 히트 영역 `::after`를 실제로 누를 수 있는 것에만 붙인다

선택자를 `button.letter::after`로 좁힌다. 범례의 글자 견본은 장식이고(`.legend .letter`에
`cursor: default`가 이미 붙어 있다 — `RaciMatrix.vue:425-427`) **넓은 히트 영역이 필요한 이유가
없다.** 없어야 할 곳에 만들지 않는 것이 첫 번째 방어선이고, 선택자 자체가 "이건 버튼용"이라고
말해 준다.

### 2-2. `.letter`에 `position: relative`를 명시한다

`::after`의 기준을 **전역 규칙에 의존하지 않는다.** 지금 코드는 "이 `.letter`는 언젠가도 항상
`<button>`이고, 전역 `button` 규칙은 언젠가도 항상 `position: relative`를 갖는다"는 두 가정 위에
서 있고, 첫 번째 가정이 같은 파일 안에서 이미 깨져 있었다.

`position: relative`가 있으면 `.letter`가 무엇으로 렌더되든 `::after`가 그 요소 안에 갇힌다 —
**화면 전체를 덮는 실패 자체가 불가능해진다.** 이것이 진짜 수정이고, 2-1은 그 위의 위생이다.

### 2-3. `pointer-events: none`으로 고치지 않는다

이 `::after`는 **클릭을 받으려고** 있는 것이다(WCAG 2.5.8 히트 영역 24×24). `pointer-events: none`을
붙이면 화면은 살아나지만 글자 버튼의 히트 영역이 시각 크기(21.6px)로 되돌아가 접근성 기준을
다시 어긴다. 받아야 할 영역을 **버튼 안으로 가두는** 것이 맞다.

### 2-4. 범례 마크업을 바꾸지 않는다

`.letter`를 범례와 표가 공유하는 것 자체는 옳다 — 같은 글자가 같은 모양·같은 색(`data-role`)으로
보여야 하고, 클래스를 둘로 나누면 팔레트가 두 곳에서 관리된다. 문제는 공유가 아니라 **공유된
클래스에 상호작용 전용 규칙이 붙어 있었던 것**이다.

---

## 3. 작업

`frontend/src/features/raci/RaciMatrix.vue` 한 파일, `<style scoped>` 안에서만 바뀐다.

**(a) `.letter`(`:361-370`)에 `position: relative`를 첫 줄로 추가한다.**

```css
/*
 * position: relative 는 아래 `button.letter::after`(히트 영역)의 기준이다. 전역
 * `button { position: relative }`(style.css)에 맡기지 않고 여기 명시한다 — 이 클래스는
 * 표의 <button> 과 범례의 <span> 이 함께 쓰는데, span 은 그 전역 규칙을 받지 못해
 * ::after 가 초기 컨테이닝 블록으로 올라가 화면 전체를 덮고 모든 클릭을 먹었다.
 */
.letter {
  position: relative;
  width: 1.35rem;
  …
}
```

주석을 반드시 남긴다. `position: relative`는 없어도 대부분의 경우 화면이 멀쩡해 보이므로,
이유가 적혀 있지 않으면 다음 정리 때 "쓰이지 않는 선언"으로 지워진다.

**(b) `.letter::after`(`:377-381`)의 선택자를 `button.letter::after`로 좁힌다.**
주석(`:372-376`)에 **왜 `button`으로 한정하는지** 한 줄을 더한다 — 범례의 견본(`<span>`)에는
히트 영역이 필요 없다는 사실.

이 둘 말고는 아무것도 바꾸지 않는다. **템플릿·스크립트·다른 CSS는 그대로다.**

---

## 4. 건드리지 않는 것

| 무엇 | 이유 |
|---|---|
| 범례 마크업(`:198-211`) | `.letter` 공유는 옳다(2-4). 클래스를 나누면 팔레트가 두 곳이 된다 |
| `.legend .letter { cursor: default }`(`:425-427`) | 이미 "이건 누르는 것이 아니다"를 말하고 있다 |
| 전역 `button { position: relative }`(`style.css:532-533`) | 상태 레이어(`button::before`)의 기준이다. 지우면 그쪽이 깨진다 |
| `button::before`의 `pointer-events: none`(`style.css:563`) | 그건 *보이기만* 하는 층이라 맞는 설정이다. 히트 영역 `::after`와 목적이 반대다 |
| 나머지 히트 영역 다섯 곳 | 대상이 모두 `<button>`이라 지금은 안전하다(1-3에서 확인). **다만 5장에 점검 항목으로 남긴다** |
| `useRaci`의 모듈 스코프 캐시 | 재방문이 조건이 된 이유일 뿐 원인이 아니다. 캐시는 의도된 설계다 |
| `RaciMatrix`가 프래그먼트 루트인 것 | 이 버그와 무관하다(emits는 선언돼 있다 — `:27-30`) |

---

## 5. 확인 방법

```bash
cd frontend
npm test          # 이 변경은 CSS라 기존 테스트가 깨지지 않아야 한다
npm run build
```

**수동 확인이 이 작업의 실질 검증이다.** CSS 컨테이닝은 `happy-dom`에 레이아웃이 없어
단위 테스트로 잡을 수 없다.

구성원과 WBS 항목이 모두 있는 프로젝트로(= 범례가 렌더되는 조건):

1. `/raci`로 이동한다 → 매트릭스와 **범례가 보인다**
2. **다른 화면(WBS 등)으로 갔다가 다시 `/raci`로 돌아온다** → 이때가 원래 죽던 시점이다
3. 상단 메뉴(WBS·간트·RAID)를 누른다 → **이동된다**
4. `/raci`로 돌아와 브라우저를 **새로 고친다** → 상단 메뉴가 여전히 눌린다
5. 프로젝트 선택 `<select>`를 바꾼다 → 바뀐다
6. R·A·C·I 글자를 누른다 → 배정되고 해제된다(Network에 요청이 보인다)
7. **회귀 확인 한 줄** — 그 화면의 Console에서:
   ```js
   document.elementFromPoint(60, 20)
   ```
   → 상단 메뉴의 `<a>`나 `<nav>`·`<div>`가 나와야 한다. `<span class="letter …">`가 나오면
   고쳐지지 않은 것이다. **이 한 줄을 커밋 메시지나 주석에 남겨 두면 다음에 같은 의심이 들 때
   30초에 판별된다.**
8. **히트 영역이 살아 있는지** — 표의 글자 버튼 **경계 바로 밖**(1~2px)을 누른다 → 여전히
   토글된다(24×24 히트 영역이 유지됐다는 뜻이다. `pointer-events: none`으로 고쳤다면 여기서
   실패한다)
9. 범례의 글자 견본을 누른다 → **아무 일도 일어나지 않는다**(장식이 맞다)
10. 라이트·다크 양쪽에서 범례와 표의 글자 모양이 같다
11. 커밋 조회 모드로 RACI를 본다 → 글자가 `disabled`이고 화면은 정상이다

**함께 점검(회귀 예방)**: 히트 영역 `::after`를 쓰는 다른 다섯 곳이 여전히 `<button>`에만
붙어 있는지 확인한다 — `SprintBoard.vue:502`, `WbsTree.vue:1473`·`:1731`·`:1906`·`:1984`.
지금은 전부 버튼이다(1-3). 하나라도 `<span>`·`<a>`·`<div>`로 바뀌면 같은 사고가 그 화면에서
재현된다.

---

## 6. 이번에 하지 않는 것 (후속 후보)

- **히트 영역 확장을 전역 유틸리티로 빼기.** 같은 패턴이 여섯 곳에 복사돼 있다
  (`.letter`·`.link`·`.chip`·`.toggle`·`.cp-badge`·툴바 필터). `style.css`에
  `.hit-24 { position: relative }` + `.hit-24::after { … }` 한 쌍을 두면 **`position: relative`가
  히트 영역과 같은 규칙에 묶여** 이 사고가 구조적으로 불가능해진다. 이번에는 터진 곳만 고치고,
  나머지 다섯 곳이 모두 버튼임을 확인해 뒀다
- **CSS 컨테이닝을 테스트로 고정하기.** 실제 레이아웃이 필요해 `happy-dom`으로는 불가능하다.
  브라우저 기반 테스트(Playwright 등)를 들이는 것은 별건이고, 그때 `elementFromPoint` 검사가
  첫 케이스가 될 만하다
- **RACI 매트릭스의 DOM 크기.** 버튼이 `업무 × 구성원 × 4`개라 큰 프로젝트에서는 수천 개가 된다.
  이번 증상의 원인은 아니었지만(조사 중 한 번 의심했다) 구성원이 늘면 실제로 무거워질 수 있다 —
  열 가상화나 "행이 선택됐을 때만 버튼화"가 후보다
