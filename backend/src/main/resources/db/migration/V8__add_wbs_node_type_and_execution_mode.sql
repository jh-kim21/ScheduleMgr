-- WBS와 Agile 실행 계층을 잇기 위해 새로 추가하는 두 컬럼 (Hybrid PM Step 2).
--
-- node_type: "어느 항목이 최하위 관리 단위(Work Package)인가"를 저장한다. 지금까지 이 구분은
--   자식 유무로 조회 시점에 파생됐는데(WbsNode.summary()), 그러면 Work Package에 자식을 하나
--   추가하는 순간 실행 방식과 (이후의) 가중치·Backlog 귀속이 말없이 고아가 된다. 저장해 두어야
--   Step 3의 귀속 검증이 다른 행의 편집으로 흔들리지 않는다.
--   MILESTONE은 아직 쓰지 않는다 — 마일스톤 표시는 Step 6의 범위다.
--
-- execution_mode: Work Package의 실행 방식(Waterfall/Agile/Hybrid). NULL은 "미지정"이고,
--   기존 동작(수동 진행률 입력)을 그대로 쓴다는 뜻이다. 기본값을 WATERFALL로 두면 안 된다 —
--   설계의 Waterfall 진척은 승인 체크포인트 가중치 비율인데 기존 데이터에는 체크포인트가 없어,
--   Step 5에서 기존 프로젝트 전부가 0%/산정 전으로 떨어진다. 그래서 DB 기본값을 두지 않는다.
ALTER TABLE wbs_items ADD COLUMN node_type VARCHAR(20);
ALTER TABLE wbs_items ADD COLUMN execution_mode VARCHAR(20);

-- 기존 레코드 백필: 지금 화면이 판정하는 것과 똑같은 규칙(자식이 있으면 Summary)을 그대로 굳힌다.
-- 이 마이그레이션만으로는 보이는 것이 달라지지 않는다.
UPDATE wbs_items
   SET node_type = 'SUMMARY'
 WHERE id IN (SELECT parent_id FROM wbs_items WHERE parent_id IS NOT NULL);

UPDATE wbs_items
   SET node_type = 'WORK_PACKAGE'
 WHERE node_type IS NULL;

ALTER TABLE wbs_items ALTER COLUMN node_type SET NOT NULL;
