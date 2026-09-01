-- RAID 항목을 WBS 업무에 연결한다 (선택). "이 위험은 어느 업무의 것인가"를 표시하기 위한 것이며,
-- 소유자와 같은 이유로 SET NULL 이다 — WBS 항목이 지워져도 위험 기록 자체는 남아야 한다.
ALTER TABLE raid_items ADD COLUMN wbs_item_id BIGINT;

ALTER TABLE raid_items ADD CONSTRAINT fk_raid_wbs_item
    FOREIGN KEY (wbs_item_id) REFERENCES wbs_items (id) ON DELETE SET NULL;

CREATE INDEX idx_raid_wbs_item ON raid_items (wbs_item_id);
