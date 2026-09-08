-- 실제 실적과 예상 종료 (Hybrid PM Step 6-A, 설계 §7).
--
-- 간트가 구분해 보여야 하는 세 가지 중 두 개가 지금까지 없었다:
--   기준 일정  = baseline_items.start_date/end_date  (Step 5에서 추가)
--   현재 계획  = wbs_items.start_date/end_date       (원래 있음)
--   실제 실적  = 여기서 추가하는 actual_*
--
-- forecast_end_date 는 "지금 아는 것으로 볼 때 언제 끝날 것 같은가"이며 현재 계획과 다르다.
-- 계획을 고치지 않고도 늦어질 것 같다고 말할 수 있어야 하고, 그 경고가 기준 종료일 초과 판정의
-- 근거가 된다(설계 §7: 예상 종료일이 기준 종료일을 넘으면 지연 경고).
--
-- **Sprint 종료일을 여기에 자동 복사하지 않는다**(지시서 6-A). Sprint가 끝난 것과 Work Package의
-- 산출물이 완료된 것은 다른 사실이고, 한 Sprint가 여러 Work Package에 걸쳐 있을 수도 있다.
ALTER TABLE wbs_items ADD COLUMN actual_start_date DATE;
ALTER TABLE wbs_items ADD COLUMN actual_end_date DATE;
ALTER TABLE wbs_items ADD COLUMN forecast_end_date DATE;
