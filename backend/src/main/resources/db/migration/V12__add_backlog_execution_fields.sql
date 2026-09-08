-- Board 실행에 필요한 필드 (Hybrid PM Step 4).
--
-- blocked / blocked_reason: 차단은 상태와 별개다(Step 4 지시서 6항). 차단된 항목도 어느 칸에
--   있었는지가 남아야 하고, 상태를 한 칸 늘리면 To Do → In Progress → Review → Done 전이에
--   "차단"이 섞여 든다. 보관(archived_at)을 상태와 분리한 것과 같은 이유다.
--
-- done_at: 완료로 인정된 시각. 상태만으로는 "지금 Done"과 "예전에 Done이었다가 재오픈됨"을
--   구분할 수 없다(지시서 10항). 재오픈하면 이 값을 비우고, Sprint 종료 결과는 그대로 남는다 —
--   현재 상태와 과거 실적은 다른 사실이다.
ALTER TABLE backlog_items ADD COLUMN blocked BOOLEAN DEFAULT FALSE NOT NULL;
ALTER TABLE backlog_items ADD COLUMN blocked_reason VARCHAR(500);
ALTER TABLE backlog_items ADD COLUMN done_at TIMESTAMP;

-- 이미 Done 상태로 들어와 있는 항목에는 완료 시각을 알 수 없으므로 updated_at 을 쓴다.
-- 비워 두면 "Done인데 완료 시각이 없는" 항목이 남아 재오픈 판정이 흔들린다.
UPDATE backlog_items SET done_at = updated_at WHERE status = 'DONE';
