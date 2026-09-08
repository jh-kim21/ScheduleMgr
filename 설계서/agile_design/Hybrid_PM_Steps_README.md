# Hybrid PM 단계별 Agent 작업 지시서

전체 설계: [WBS_Agile_Hybrid_PM_Design.md](WBS_Agile_Hybrid_PM_Design.md)

## 사용 방법

1. 실제 구현 저장소에서 Agent 작업을 시작한다. 이 파일이 있는 문서 폴더를 구현 저장소로 오인하지 않게 한다.
2. 전체 설계 문서와 현재 Step 지시서를 함께 전달한다. Step 2부터는 이전 단계 결과 보고서도 제공한다.
3. 한 번에 한 Step만 요청한다. 이번 문서 생성 작업에서는 구현 Agent를 실행하지 않았다.
4. Agent가 제출한 변경 사항·검증 결과·미해결 사항을 확인한 후 다음 Step을 전달한다.
5. 앞 단계의 선행 조건이 미완료이면 먼저 해당 범위만 보완한다. 전체 단계를 한꺼번에 실행하도록 요청하지 않는다.

## 작업 순서

| Step | 지시서 | 주요 산출물 |
|---|---|---|
| 01 | [기존 구조 분석 및 구현 계획](Hybrid_PM_Step01_Analysis.md) | Hybrid_PM_Step01_Analysis.md |
| 02 | [WBS Execution Mode 추가](Hybrid_PM_Step02_Execution_Mode.md) | Hybrid_PM_Step02_Result.md |
| 03 | [Backlog와 Work Package 연결](Hybrid_PM_Step03_Backlog_Link.md) | Hybrid_PM_Step03_Result.md |
| 04 | [Sprint 및 Board 구현](Hybrid_PM_Step04_Sprint_Board.md) | Hybrid_PM_Step04_Result.md |
| 05 | [기준 정보 정비 및 진행률 자동 집계](Hybrid_PM_Step05_Progress.md) | Hybrid_PM_Step05_Result.md |
| 06 | [Gantt·RACI·RAID 연계](Hybrid_PM_Step06_Gantt_RACI_RAID.md) | Hybrid_PM_Step06_Result.md |
| 07 | [Dashboard 및 통합 검증](Hybrid_PM_Step07_Dashboard_Validation.md) | Hybrid_PM_Step07_Result.md |

Step 5는 기준 정보 → 계산 → 화면·보고, Step 6은 Gantt → RACI → RAID 순으로 내부 작업을 나눈다. 각 묶음을 검증한 뒤 진행하며, 실제 코드 분석에서 범위가 큰 것으로 확인되면 같은 Step을 더 작은 변경 단위로 나누어 보고한다.

## 단계 검토 기준

- 이번 단계의 기능과 완료 기준이 충족되었는가?
- 기존 데이터와 사용자 변경 사항이 보존되었는가?
- 검증 결과와 미실행 검증이 구분되어 있는가?
- 다음 단계의 선행 조건이 실제 코드에서 충족되는가?

Step 1은 분석만 수행한다. 나머지는 해당 단계의 구현을 허용하지만 운영 배포와 외부 메시지 발송은 포함하지 않는다. 원본 설계의 데이터 모델은 예시이므로 실제 저장소 구조에 맞춰 구현한다.

