# WBS와 Agile-Scrum을 결합한 하이브리드 프로젝트 관리 설계안

| 구분 | 내용 |
|---|---|
| 문서 목적 | 기존 프로젝트 관리 UI를 확장하여 범위·일정 통제와 반복 실행 관리를 통합 |
| 작성일 | 2026-09-07 |
| 문서 상태 | 설계 제안 v1.0 |
| 기반 자료 | 「WBS와 Scrum 통합 구성」 대화 및 첨부된 현재 UI 화면 |
| 적용 범위 | 프로젝트, WBS, Agile, 간트 차트, RACI, RAID, Dashboard |

## 1. 보고 요약

**WBS는 프로젝트의 범위와 산출물 기준을 관리하고, Agile-Scrum은 해당 범위를 구현하는 실행 계층으로 구성한다. 두 계층의 연결 기준은 WBS의 최하위 관리 단위인 Work Package이다.**

현재 화면의 `프로젝트 | WBS | 간트 차트 | RACI | RAID` 구조를 유지하면서 `Agile`과 `Dashboard`를 추가한다. WBS의 세부 개발 작업을 다시 등록하지 않고, Work Package에 연결된 Backlog를 Sprint와 Board에서 관리한다. 실행 결과는 공통 집계 규칙에 따라 WBS, 간트 차트, Dashboard에 반영한다.

핵심 권장 사항은 다음과 같다.

1. WBS는 산출물과 Work Package 수준까지 분해하고, 구현 Task는 Agile에서 관리한다.
2. Work Package마다 Waterfall·Agile·Hybrid 실행 방식을 지정한다.
3. Backlog 항목은 하나의 Work Package에 귀속시켜 진행률 중복 집계를 방지한다.
4. 프로젝트 진척도는 승인된 가중치와 완료 기준으로 계산하고, Story Point는 팀의 실행 추정과 추세 분석에 사용한다.
5. 승인된 Baseline, 현재 계획, 실제 실적을 분리하여 변경과 지연을 확인한다.
6. RACI와 RAID를 공통 관리 정보로 연결하고, Dashboard를 PM의 의사결정 진입 화면으로 발전시킨다.

이 문서의 상세 필드, 집계식 및 운영 규칙은 원본 대화를 구체화한 제안이다. 현재 구현된 기능은 첨부 화면에서 확인되는 프로젝트 등록·목록 및 메뉴 범위로 한정하며, 내부 데이터 구조는 확인되지 않았다.

## 2. 현재 UI와 권장 메뉴 구조

### 2.1 현재 화면에서 확인된 구성

- 상단 메뉴: 프로젝트, WBS, 간트 차트, RACI, RAID
- 프로젝트 등록: 이름, 설명, 상태, 시작일, 종료일
- 프로젝트 목록: 이름, 상태, 시작일, 종료일 및 수정·삭제
- 데이터 이동: 가져오기, 내보내기
- 공통 설정: 시스템 설정

### 2.2 권장 메뉴

```text
프로젝트 | WBS | Agile | 간트 차트 | RACI | RAID | Dashboard
                 ├─ Backlog
                 ├─ Sprint
                 └─ Board
```

| 메뉴 | 주요 기능 | 대표 연결 동작 |
|---|---|---|
| 프로젝트 | 프로젝트 등록, 선택, 기본 일정, 상태, 구성원 | 프로젝트 선택 시 공통 컨텍스트 설정 |
| WBS | 범위 계층, 산출물, Work Package, 실행 방식, 가중치 | 연결된 Backlog 수를 클릭하여 필터 이동 |
| Agile / Backlog | Epic·Story·Bug, 우선순위, 완료 조건, WBS 연결 | 항목에서 원본 Work Package 조회 |
| Agile / Sprint | Sprint Goal, 기간, 계획 항목, 결과 | Sprint별 Board 및 RAID 조회 |
| Agile / Board | 실행 상태, 담당자, 검토, 차단 사유 | 카드에서 상세 정보와 관련 Issue 조회 |
| 간트 차트 | WBS 일정, 마일스톤, 의존성, Sprint 기간 | 지연 구간에서 WBS·Sprint 상세 이동 |
| RACI | 범위별 수행·최종 책임·협의·공유 역할 | Work Package 책임 구조 조회 |
| RAID | 위험·이슈·조치·결정 및 관련 대상 | 영향받는 WBS·Sprint·Backlog 조회 |
| Dashboard | 진척, 일정 편차, Sprint 현황, 의사결정 필요 항목 | 지표에서 원인 항목으로 이동 |

모든 메뉴에서 선택한 프로젝트와 필터를 유지한다. 프로젝트 목록은 포트폴리오 진입 화면으로 유지하고, 프로젝트를 선택한 후의 기본 화면은 Dashboard로 설정하는 것을 권장한다. 시스템 설정에는 상태 체계, 권한, 완료 기준 및 집계 기본값을 둔다.

## 3. WBS와 Scrum의 역할 분리

| 구분 | WBS / 계획 관리 | Agile-Scrum / 실행 관리 |
|---|---|---|
| 핵심 질문 | 어떤 범위와 산출물을 언제까지 완료해야 하는가? | 이번 Sprint에서 어떤 목표를 달성하고 어떻게 실행할 것인가? |
| 관리 단위 | 상위 범위, Work Package, 마일스톤 | Product Backlog 항목, Sprint, 실행 Task |
| 핵심 정보 | 범위, 산출물, 책임, 기준 일정, 가중치, 인수 조건 | 우선순위, Sprint Goal, 완료 조건, 추정, 실행 상태 |
| 변경 방식 | 승인된 범위·일정 변경을 이력으로 관리 | 목표와 범위 제약 안에서 Backlog를 구체화·재정렬 |
| 완료 판단 | Work Package의 산출물 및 인수 조건 충족 | 항목의 수용 조건과 팀의 Definition of Done 충족 |
| 보고 관점 | 프로젝트 전체 진행률, 일정 편차, 주요 의존성 | 목표 달성, 완료 항목, 미완료 원인, 개선 사항 |

WBS는 Scope Baseline을 구성하는 중심 구조이며, 간트 차트는 일정 정보를 시각화하는 화면이다. 기준 일정은 별도의 버전으로 저장한다. 간트 차트 화면 자체를 Baseline 데이터로 취급하지 않는다.

WBS는 다음처럼 구성할 수 있다.

```text
프로젝트 관리 시스템
├─ 요구사항 및 설계 산출물
│  ├─ 요구사항 정의서 [Work Package]
│  └─ 시스템 아키텍처 [Work Package]
├─ 제품 기능
│  ├─ 프로젝트 관리 기능 [Work Package]
│  ├─ WBS 관리 기능 [Work Package]
│  ├─ 간트 차트 기능 [Work Package]
│  ├─ RACI 관리 기능 [Work Package]
│  └─ RAID 관리 기능 [Work Package]
└─ 출시 준비
   ├─ 통합 검증 결과 [Work Package]
   └─ 고객 인수 및 배포 [Work Package]
```

기존의 요구사항→설계→개발→테스트→배포 분류도 유지할 수 있으나, 각 Work Package의 산출물과 포함 범위는 겹치지 않게 정의한다. API·DTO·화면 수정 등 세부 구현 작업은 WBS 하위에 반복 등록하지 않는다. 개발 Story에 포함된 테스트와 별도 통합 검증 Work Package의 범위도 구분한다.

## 4. Work Package와 Backlog 연결

### 4.1 기본 관계

```text
Project
└─ WBS 계층
   └─ Work Package
      ├─ Epic (선택적 묶음)
      │  ├─ Story ── Sprint 배정 ── 실행 Task
      │  └─ Bug   ── Sprint 배정 ── 실행 Task
      └─ Story / Bug (Epic 없이 직접 연결 가능)
```

Product Backlog는 실행 항목을 정렬하여 관리하는 목록이다. Epic과 Story 사이에 별도 계층 노드로 만들지 않는다. Epic은 큰 범위를 묶는 도구 내 분류이며, Sprint에 실제로 선택하는 단위는 완료 가능한 Story 또는 Bug를 기본으로 한다.

### 4.2 연결 규칙

| 항목 | 권장 규칙 |
|---|---|
| Work Package : Backlog | 1:N. 집계 대상 Story·Bug는 하나의 Work Package에만 귀속 |
| Epic | 초기에는 한 Work Package 안에서 사용하고 하위 항목도 같은 귀속 유지 |
| Story : Task | 1:N. Task는 Story의 실행 상세이며 WBS 진척에 별도 가산하지 않음 |
| Sprint : Story | Sprint는 여러 Work Package의 항목을 포함할 수 있음 |
| 복수 범위 관련 항목 | 주 귀속 Work Package 한 개와 참고 링크를 분리. 실제 산출물이 분리되면 Story도 분할 |
| 미분류 Backlog | 초안 등록은 허용하되 Sprint 투입 전 Work Package 귀속 확정 |
| 반복 Sprint 배정 | 미완료 항목의 재배정 이력 보존. 동시에 두 활성 Sprint에 배정하지 않음 |
| 항목 이동·취소 | 사유와 변경 전후 귀속·가중치·승인 상태 기록 |

### 4.3 WBS 관리 기능 예시

| ID | Backlog 항목 | Story Point 예시 | 계획 Sprint |
|---|---|---:|---|
| US-001 | WBS 계층 등록 | 5 | Sprint 1 |
| US-002 | WBS 수정 | 3 | Sprint 1 |
| US-003 | WBS 삭제 | 2 | Sprint 1 |
| US-005 | 담당자 지정 | 3 | Sprint 1 |
| US-004 | Drag & Drop | 5 | Sprint 2 |
| US-006 | 기간 설정 | 3 | Sprint 2 |
| US-007 | 진행률 계산 | 5 | Sprint 2 |
| US-008 | Excel Import | 8 | Sprint 2 |

귀속 Work Package는 `WBS 관리 기능`, Epic은 `WBS 관리 기능 구현`으로 지정한다. Sprint 1의 목표는 기본 등록·편집 제공, Sprint 2의 목표는 편집 편의와 외부 데이터 연동으로 설정할 수 있다. 위 포인트와 배정은 구조 설명용이며, 실제 Sprint 투입량은 팀의 용량과 실행 경험에 따라 결정한다.

WBS 화면에는 `코드 | 항목 | 기준 기간 | 예상 종료일 | 담당 | 실행 방식 | 진행률 | 연결 Backlog | 상태`를 표시한다. `8개 항목 / 4개 완료`를 클릭하면 해당 Work Package로 필터링한 Backlog를 연다.

## 5. Execution Mode 설계

Execution Mode는 Work Package에 저장한다. 상위 WBS는 자식들의 실행 방식을 요약 표시하며 별도 실행 실적을 입력하지 않는다.

| 실행 방식 | 적용 예 | 실행 관리 | 진행률 산정 |
|---|---|---|---|
| Waterfall | 요구사항 확정, 설계 승인, 고객 인수 | 산출물과 검토·승인 체크포인트 | 승인된 체크포인트 가중치 합산 |
| Agile | 기능 개발, UI 개선 | Backlog, Sprint, Board | 완료된 집계 대상 항목의 가중치 합산 |
| Hybrid | 반복 검증과 공식 인수가 함께 있는 범위 | Agile 실행 요소와 별도 인수 요소 | 두 요소의 사전 합의된 비중으로 합산 |

Hybrid는 집계 대상과 완료 기준을 명확히 구분할 수 있을 때 사용한다. 예를 들어 통합 검증 Work Package에서 결함 해결 Backlog를 70%, 공식 검증 보고서 승인을 30%로 둘 수 있다. 같은 검증 작업을 두 요소에 중복 반영하지 않는다. 분리가 어려우면 하위 Work Package를 Agile과 Waterfall로 나누는 편이 명확하다.

실행 방식 변경 시 기존 실적과 연결 정보를 삭제하지 않는다. 집계 정책의 변경 전후 결과를 미리 보여주고, 적용일·사유·변경자를 기록한다. 기준 진척에 영향을 주면 Baseline 변경 절차에 포함한다.

## 6. 진행률 집계와 완료 정책

### 6.1 집계 원칙

- 프로젝트 진척은 공통 집계 서비스에서 계산하여 모든 화면에 동일하게 제공한다.
- WBS의 상위 노드는 자식 결과만 집계한다. 상위 노드의 수동 진척을 더하지 않는다.
- Epic과 Task는 집계 대상에서 제외하고 Story·Bug 등 지정된 실행 단위만 계산한다.
- Story Point를 WBS 가중치로 자동 변환하거나 다른 팀의 포인트와 합산하지 않는다.
- 가중치는 계획 공수·비용·산출물 중요도 중 조직이 선택한 기준으로 정하고, 동일 집계 수준에서 일관되게 적용한다.
- 가중치와 범위 변경 이력을 보존하여 분모 변경으로 인한 진척 변화를 설명할 수 있게 한다.

### 6.2 Agile Work Package

집계 대상 항목의 가중치를 `wᵢ`, 완료 여부를 `dᵢ`로 정의한다. Done이면 `dᵢ = 1`, 그 외에는 `0`이다.

```text
Agile 진행률(%) = 100 × Σ(wᵢ × dᵢ) / Σwᵢ
```

동일 가중치의 항목 10개 중 6개가 Done이면 60%이다. 가중치 합계가 20이고 완료 항목의 가중치 합계가 12라면 역시 60%이다. Review나 In Progress에는 임의의 부분 완료율을 부여하지 않는다.

Done은 수용 조건 및 Definition of Done을 충족했을 때만 허용한다. Task 전체 완료만으로 Story를 자동 완료하지 않고 필요한 검증 조건을 확인한다. 재오픈된 항목은 현재 진척에서 제외하되 과거 보고 스냅샷은 유지한다.

### 6.3 Waterfall 및 Hybrid

```text
Waterfall 진행률(%) = 100 × 완료·승인된 체크포인트 가중치 합 / 전체 체크포인트 가중치 합

Hybrid 진행률(%) = α × Agile 진행률 + (1 − α) × Waterfall 진행률
```

예를 들어 Agile 비중 `α = 0.7`, Agile 진척 80%, 승인 요소 진척 50%이면 Hybrid 진척은 `0.7 × 80 + 0.3 × 50 = 71%`이다. 비중은 실행 전에 정하고 변경 시 이력을 남긴다.

### 6.4 상위 WBS 및 프로젝트

직접 자식의 가중치를 `Wⱼ`, 진척률을 `Pⱼ`로 정의한다.

```text
상위 진행률(%) = Σ(Wⱼ × Pⱼ) / ΣWⱼ
```

| 하위 Work Package | 가중치 | 진행률 | 가중 기여 |
|---|---:|---:|---:|
| 사용자 관리 | 20 | 80% | 1,600 |
| 프로젝트 관리 | 50 | 40% | 2,000 |
| WBS 관리 | 30 | 60% | 1,800 |
| 합계 | 100 | **54%** | 5,400 |

같은 방식을 계층별로 적용한다. 부모 간 가중치는 각 하위 범위의 전체 규모를 반영하도록 정하며, 직계 자식과 그 손자 항목을 한 분모에 함께 넣지 않는다.

### 6.5 예외와 보고 기준

| 상황 | 처리 규칙 |
|---|---|
| Backlog 미등록 또는 전체 가중치 0 | 0%로 단정하지 않고 `산정 전` 표시 및 집계 누락 범위 안내 |
| 필수 인수 조건 미충족 | 실행 진척 100%라도 `인수 대기` 표시. 최종 완료 상태와 구분 |
| Backlog 추가·제외 | 현재 범위 진척은 재계산하고, 기준 범위 대비 증감은 별도 표시 |
| Sprint 이월 | 원 Sprint 결과에 미완료로 남기고 재배정. 완료 실적 중복 가산 방지 |
| 반올림 | 계산은 원래 정밀도로 수행하고 화면에서만 반올림 |
| 과거 보고 | 보고일·Baseline 버전·범위 버전·집계 정책을 포함한 스냅샷 보존 |

계획 진척은 기준 일정의 체크포인트 또는 승인된 시점별 계획 곡선으로 산정한다. 실제 진척과 동일한 범위·가중치를 사용해야 비교할 수 있다. 일정상 시간이 지났다는 이유만으로 실제 진척을 올리지 않는다.

## 7. 간트 차트 연계

간트 차트는 WBS의 전체 일정과 팀별 Sprint 기간을 함께 보여준다.

| 표시 요소 | 데이터 기준 | 활용 |
|---|---|---|
| 기준 일정 막대 | 승인된 Baseline 시작·종료일 | 최초 또는 승인 변경 계획과 비교 |
| 현재 계획 막대 | 현재 계획·예상 종료일 | 일정 재예측과 영향 분석 |
| 실제 실적 | 실제 시작·완료일, 공통 진척 | 실행 상태 확인 |
| Sprint 구간 | 팀별 Sprint 시작·종료일 | 반복 실행 구간과 WBS 일정 비교 |
| 마일스톤 | 승인·인수·배포 목표일 | 주요 약속과 완료 조건 확인 |
| 의존성 | WBS·마일스톤 간 선후행 관계 | 지연 전파 및 선행 조건 확인 |

Sprint는 여러 Work Package를 포함할 수 있으므로 별도 팀 레인에서 한 번 표시하는 것을 기본으로 한다. 특정 WBS를 선택하면 관련 Sprint를 강조한다. WBS 아래에도 표시할 경우 참조 표시임을 명시하고 일정이나 실적을 중복 생성하지 않는다.

Sprint 종료일을 Work Package의 실제 완료일로 자동 복사하지 않는다. 예상 종료일이 기준 종료일을 넘으면 지연 경고를 표시하고, 원인 Backlog와 RAID로 이동할 수 있게 한다. 간트 차트에서 현재 계획을 수정해도 승인된 Baseline은 보존한다.

## 8. RACI 연계

RACI는 Work Package의 책임 체계이고, Story 담당자와 Reviewer는 실제 실행 배정이다.

| 역할 | 의미 | WBS 관리 기능 예시 |
|---|---|---|
| R — Responsible | 해당 범위 수행 책임 | 개발팀 |
| A — Accountable | 해당 범위의 최종 책임 | 지정된 기능 책임자 또는 PM |
| C — Consulted | 검토·협의 대상 | 사용자 대표, 아키텍트 |
| I — Informed | 결과 공유 대상 | 관련 프로젝트 구성원 |

Work Package별 A는 한 명을 지정하는 정책을 권장한다. 상위 역할을 기본값으로 상속하되 하위에서 명시적으로 재정의할 수 있고, 화면에는 상속 여부를 표시한다.

Product Owner는 Backlog의 가치와 정렬, Developers는 실행 계획과 구현, Scrum Master는 Scrum 운영 개선을 맡는 구조로 역할을 구분한다. PM의 프로젝트 조정 역할과 연결하되, 모든 Story를 PM이 직접 배정·승인하는 방식으로 고정하지 않는다. RACI의 A와 제품 인수 권한은 프로젝트의 실제 의사결정 체계에 맞춰 정한다.

Story 담당자 변경으로 WBS의 A를 자동 변경하지 않는다. RACI 등록은 책임 표현이며, 시스템의 조회·수정·승인 권한은 별도로 관리한다.

## 9. RAID 연계

본 제안은 원본 대화의 Dashboard 예시에 맞춰 RAID를 **Risk, Action, Issue, Decision**으로 사용한다. 기존 시스템이 Assumption·Dependency 등의 다른 분류를 사용한다면 실제 분류와 데이터 의미를 확인하여 유지·매핑한다.

| 유형 | 관리 내용 | 필수 정보 예시 |
|---|---|---|
| Risk | 아직 발생하지 않은 불확실성과 영향 | 발생 가능성, 영향도, 대응, 책임자, 검토일 |
| Action | 필요한 후속 조치 | 담당자, 기한, 완료 조건, 상태 |
| Issue | 이미 발생한 문제 | 심각도, 영향 범위, 해결 담당, 목표일 |
| Decision | 결정 내용과 근거 | 결정자, 결정일, 대안, 영향 범위 |

RAID 항목은 프로젝트에 귀속시키고 WBS·Sprint·Backlog에 복수 연결할 수 있게 한다. 동일 위험이 여러 Story에 영향을 주더라도 원본 RAID 항목은 하나로 관리한다.

예를 들어 `RISK-012: WBS Drag & Drop 구현 난이도로 인한 일정 지연 가능성`을 `WBS 관리 기능`, `Sprint 2`, `US-004`에 연결한다. 실제 차단이 발생하면 관련 Issue를 생성하거나 유형 전환 이력을 남기고, Board 카드에 차단 사유와 해결 담당을 표시한다. 의사결정이 필요하면 Decision으로 근거를 연결한다.

일정 의존성은 별도의 의존성 데이터로 관리하며, RAID에는 그 의존성에서 발생한 위험·문제와 대응을 연결한다.

## 10. Dashboard 구성

Dashboard는 현재 상태와 함께 PM이 조치할 대상을 제시한다. 모든 수치에 기준일을 표시하고 상세 목록으로 이동할 수 있게 한다.

| 영역 | 권장 지표 | 해석 및 동작 |
|---|---|---|
| 전체 진척 | 기준 계획 진척, 실제 진척, 편차 | 동일 범위 기준 비교. 계획 75%, 실제 72%이면 −3%p |
| 일정 | 지연 Work Package, 예상 종료일, 다음 마일스톤 | 영향받는 의존성과 원인으로 이동 |
| Sprint | 현재 Goal, 완료 항목, 차단 항목, 완료 SP | 완료 SP만으로 Goal 달성을 판정하지 않음 |
| 팀 실행 추세 | 종료 Sprint별 완료 SP, 이월 항목 | 같은 팀의 추세 관찰. 진행 중 Sprint는 별도 표시 |
| WBS 상태 | 미착수·진행·인수 대기·완료 수 | 기본 집계 단위는 Work Package로 통일 |
| RAID | 주요 위험, 열린 Issue, 기한 초과 Action, 결정 필요 항목 | 심각도·기한 기준 우선 표시 |
| 범위 변화 | 기준 대비 추가·제외 항목 및 가중치 | 진척률 변동의 원인 설명 |
| 데이터 품질 | 미연결 Backlog, 가중치 누락, 책임자 누락 | 집계의 신뢰도와 보완 대상 표시 |

상단에는 전체 진척과 일정 상태, 중앙에는 Sprint와 WBS, 하단에는 RAID 및 마일스톤을 배치한다. 출시 준비 여부는 품질·인수 조건을 포함하여 별도로 표현한다.

## 11. 데이터 모델 예시

### 11.1 주요 엔터티

| 엔터티 | 주요 필드 | 설계 의도 |
|---|---|---|
| Project | id, name, description, status, startDate, endDate | 기존 프로젝트 정보 유지 |
| Team | id, projectId, name | Sprint와 추정의 팀 기준 |
| WbsItem | id, projectId, parentId, code, name, nodeType, executionMode, ownerId, weight, plannedStart, plannedEnd, forecastEnd, actualStart, actualEnd, acceptanceStatus | 계층과 계획·실제·인수 구분 |
| Baseline | id, projectId, version, approvedBy, approvedAt | 승인된 기준 버전 |
| BaselineItem | baselineId, wbsId, scopeDescription, startDate, endDate, weight, completionCriteria | 승인 범위·일정·가중치 보존 |
| ProgressPolicy | id, wbsId, version, method, agileRatio, effectiveFrom | 실행 방식별 계산 정책 |
| BacklogItem | id, projectId, wbsId, parentId, type, title, priority, storyPoint, progressWeight, assigneeId, reviewerId, status, acceptanceCriteria, doneAt | 제품 실행 항목과 계층 |
| Sprint | id, projectId, teamId, name, goal, startDate, endDate, status | 실행 기간과 목표 |
| SprintItem | id, sprintId, backlogId, addedAt, removedAt, outcome, pointsAtStart, pointsAtClose | 배정과 Sprint 종료 결과 보존 |
| AcceptanceCheckpoint | id, wbsId, title, weight, status, approvedBy, approvedAt | Waterfall·Hybrid 승인 요소 |
| RaciAssignment | id, wbsId, memberId, role | 범위별 R/A/C/I |
| RaidItem | id, projectId, type, title, ownerId, severity, probability, impact, dueDate, status | 위험 및 통제 정보 |
| RaidLink | raidId, targetType, targetId | WBS·Sprint·Backlog 복수 연결 |
| Dependency | id, projectId, predecessorId, successorId, relationType, lag | 일정 선후행 관계 |
| ProgressSnapshot | id, projectId, asOf, baselineId, scopeVersion, policyVersion, metrics | 시점별 보고 재현 |
| ChangeLog | id, projectId, entityType, entityId, before, after, reason, changedBy, changedAt | 변경 추적 |

Milestone은 `WbsItem.nodeType = MILESTONE`으로 표현할 수 있다. Summary·Work Package·Milestone을 구별하고 Milestone은 기본적으로 진척 가중치를 갖지 않게 한다. 마일스톤과 연결된 산출물의 진척을 다시 가산하지 않는다.

### 11.2 연결 데이터 예시

아래 예시는 개념 설명용이며 실제 프로젝트 데이터가 아니다.

```json
{
  "project": { "id": "PRJ-001", "name": "PM 관리 시스템" },
  "wbsItem": {
    "id": "WP-033",
    "projectId": "PRJ-001",
    "parentId": "WBS-03",
    "code": "3.3",
    "name": "WBS 관리 기능",
    "nodeType": "WORK_PACKAGE",
    "executionMode": "AGILE",
    "weight": 30,
    "acceptanceStatus": "PENDING"
  },
  "backlogItem": {
    "id": "US-001",
    "projectId": "PRJ-001",
    "wbsId": "WP-033",
    "parentId": "EPIC-033",
    "type": "STORY",
    "title": "WBS 계층 등록",
    "storyPoint": 5,
    "progressWeight": 2,
    "status": "DONE",
    "acceptanceCriteria": "상하위 항목 등록 및 계층 검증 통과"
  },
  "sprint": {
    "id": "SPR-001",
    "projectId": "PRJ-001",
    "teamId": "TEAM-01",
    "name": "Sprint 1",
    "goal": "기본적인 WBS 등록 및 편집 기능 제공",
    "status": "CLOSED"
  },
  "sprintItem": {
    "id": "SI-001",
    "sprintId": "SPR-001",
    "backlogId": "US-001",
    "outcome": "DONE",
    "pointsAtStart": 5,
    "pointsAtClose": 5
  }
}
```

`WbsItem.weight`는 형제 WBS 사이의 비중이고, `BacklogItem.progressWeight`는 동일 Work Package 내부의 비중이다. `storyPoint`는 별도 추정값이다. 세 값을 같은 단위로 합산하지 않는다. 예시에서 참조한 상위 WBS·Epic·팀은 별도 레코드로 존재해야 한다.

### 11.3 무결성 및 변경 규칙

1. WBS 부모 관계와 Backlog 부모 관계의 순환 참조를 금지한다.
2. 연결된 WBS·Backlog·Sprint·RAID가 동일 프로젝트에 속하는지 검증한다.
3. Backlog는 Work Package에만 귀속시키고, 하위 Task는 부모의 귀속을 따른다.
4. `(sprintId, backlogId)`를 유일하게 유지하고, 동시 활성 배정을 제한한다.
5. 가중치는 음수가 될 수 없으며 분모가 0인 집계는 `산정 전`으로 처리한다.
6. 연결 항목이 있는 WBS 삭제 시 재귀속 또는 명시적 보관 처리를 요구한다. Baseline과 보고 이력은 보존한다.
7. `RaidLink`의 대상 유형·존재 여부를 검증한다. 필요하면 대상별 연결 테이블로 분리한다.
8. 진행률은 원본 상태에서 계산한다. 저장된 집계값은 재생성 가능한 캐시로 취급한다.

## 12. 운영 흐름과 최종 권장 아키텍처

### 12.1 운영 흐름

1. **계획 수립:** 프로젝트와 WBS를 구성하고 산출물, 인수 조건, 책임, 일정, 실행 방식, 가중치를 정한다.
2. **기준 승인:** 범위·일정·가중치의 Baseline을 저장한다.
3. **Backlog 구체화:** Work Package별 항목을 작성하고 수용 조건과 우선순위를 정한다.
4. **Sprint Planning:** 목표를 정하고 팀이 수행 가능한 항목을 선택한다. 여러 Work Package를 포함할 수 있다.
5. **실행:** Board에서 `To Do → In Progress → Review → Done`을 관리하고, 차단 상태는 별도 속성으로 표시한다.
6. **검토 및 개선:** Sprint 결과와 목표 달성을 확인하고 Backlog를 조정한다. 회고 결과는 후속 Action으로 연결한다.
7. **프로젝트 통제:** PM은 일정·진척 편차와 RAID를 검토하고 범위·일정 변경이 필요하면 변경 절차를 진행한다.
8. **인수 및 보고:** Work Package 완료 조건을 확인하고 보고 스냅샷을 저장한다.

### 12.2 논리 아키텍처

```text
                         Project / 공통 프로젝트 컨텍스트
                                      │
         ┌────────────────────────────┼─────────────────────────┐
         │                            │                         │
  계획·범위 관리                 실행 관리                  책임·통제 관리
  WBS / Baseline             Backlog / Sprint              RACI / RAID
  Work Package ── 귀속 연결 ── Story / Bug                   연결 대상 참조
  일정 / 의존성                 Task / Board                      │
         │                            │                         │
         └────────────────────────────┼─────────────────────────┘
                                      ▼
                    공통 집계·검증·변경 이력 서비스
                  진척 / 인수 상태 / 일정 편차 / 스냅샷
                                      │
                      ┌───────────────┼───────────────┐
                      ▼               ▼               ▼
                   WBS 화면       간트 차트       Dashboard / 보고
```

초기 구현은 **하나의 애플리케이션과 관계형 데이터베이스 안에서 계획·실행·통제 모듈을 분리하는 구조**를 권장한다. 화면별로 WBS와 Scrum 데이터를 복제하지 않고 동일 ID로 연결한다. 별도 서비스 분산은 운영 규모와 성능 요구가 확인된 후 검토한다.

Backlog 상태 변경 시 해당 Work Package와 상위 WBS를 재집계하고 결과를 관련 화면에 반영한다. 초기에는 트랜잭션과 동기 집계로 일관성을 확보하고, 데이터가 커지면 변경 이벤트·비동기 집계·캐시를 도입할 수 있다. 비동기 방식에서는 중복 이벤트 처리 방지와 마지막 갱신 시각을 제공한다.

### 12.3 단계별 도입 권고

| 단계 | 구현 범위 | 완료 판단 기준 |
|---|---|---|
| 1. 실행 연결 | Execution Mode, Backlog, Sprint, Board, WBS 귀속 | Story에서 원본 WBS까지 추적 가능하고 이중 등록 없이 실행 가능 |
| 2. 진척·일정 통합 | 가중치 집계, 완료 정책, Baseline, 간트 차트 Sprint 표시 | 동일 기준일의 WBS·간트·Dashboard 진척이 일치 |
| 3. 책임·통제 통합 | RACI 상속, RAID 연결, 차단 표시, Dashboard | 지연·위험 지표에서 담당자와 원인 항목까지 확인 가능 |
| 4. 운영 고도화 | 범위 변경 비교, 보고 스냅샷, 팀별 추세, 가져오기·내보내기 확장 | 과거 보고 재현과 연결 관계를 보존한 데이터 이동 가능 |

최종적으로 Work Package를 범위와 실행의 연결 축으로 두고, 계획·실행·책임·통제 정보가 같은 프로젝트 컨텍스트를 공유하도록 설계한다. 이 구조는 기존 WBS 기반 UI를 확장하면서도 Sprint 실행 결과를 프로젝트 수준의 일정·진척·의사결정으로 연결한다.
