-- WBS 항목이 가리키는 외부 Action Item(회의록·티켓·산출물) 주소.
--
-- 설명과 같은 층위의 자유 메모라 모든 항목에 허용한다 — 실행 방식·분야처럼 "Summary에는 붙일
-- 수 없되 보관한다"는 규칙을 두지 않는다.
--
-- http/https만 허용한다. 검증은 애플리케이션(요청 DTO의 @Pattern, 가져오기의 검증 단계)이
-- 하고, 화면도 렌더 직전에 한 번 더 본다 — 이 값이 그대로 <a href>로 들어가기 때문이다.
-- CHECK 제약을 걸지 않는 이유는 H2/PostgreSQL의 정규식 문법이 갈리기 때문이다.
ALTER TABLE wbs_items ADD COLUMN action_item_url VARCHAR(2000);
