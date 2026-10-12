-- 주제 '문화·리뷰'를 없앤다 (2026-10-12). 쓸 글이 적고 애매해서 9개로 줄인다.
-- 그 주제의 글과 블로그는 가장 가까운 '취미'로 옮겨서 글이 사라지지 않게 한다.
UPDATE post SET topic_id = 10 WHERE topic_id = 6;
UPDATE blog SET topic_id = 10 WHERE topic_id = 6;
DELETE FROM topic WHERE id = 6;

-- 이용 안내 글의 주제 목록에서도 뺀다
UPDATE notice SET content = replace(replace(content, '문화·리뷰, ', ''), ', 문화·리뷰', ''), updated_at = now()
WHERE content LIKE '%문화·리뷰%';
