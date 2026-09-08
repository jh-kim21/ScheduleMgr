-- 진척 집계의 기준 정보 (Hybrid PM Step 5-A).
--
-- weight: 형제 WBS 사이의 비중(설계 §11.2). Backlog의 progress_weight(같은 Work Package 안의 비중)와
--   Story Point(팀의 추정)와는 **다른 값**이며 같은 단위로 합산하지 않는다.
--   NULL은 "미입력"이고 0과 다르다 — 0은 "이 가지는 진척에 기여하지 않는다"는 뜻이 되고,
--   미입력은 "아직 정하지 않았다"는 뜻이다. 집계는 이 둘을 다르게 다룬다.
--
-- agile_ratio: Hybrid의 α, 0~100 (설계 §6.3). Agile 요소의 비중이며 나머지가 승인 요소다.
--   실행 방식이 HYBRID일 때만 쓰이고, 없으면 그 Work Package는 "산정 전"이 된다 —
--   비중은 실행 전에 합의되어야 하는 값이라 임의의 기본값을 두면 합의를 건너뛰게 된다.
--
-- acceptance_status: 실행 진척과 최종 인수를 분리한다(설계 §6.5). 진척 100%라도 인수가 남아 있으면
--   완료가 아니다. NULL은 "인수 절차를 두지 않음"이고, 값이 있으면 판정에 참여한다.
ALTER TABLE wbs_items ADD COLUMN weight INT;
ALTER TABLE wbs_items ADD COLUMN agile_ratio INT;
ALTER TABLE wbs_items ADD COLUMN acceptance_status VARCHAR(20);

ALTER TABLE wbs_items ADD CONSTRAINT ck_wbs_items_weight
    CHECK (weight IS NULL OR weight >= 0);
ALTER TABLE wbs_items ADD CONSTRAINT ck_wbs_items_agile_ratio
    CHECK (agile_ratio IS NULL OR (agile_ratio >= 0 AND agile_ratio <= 100));
