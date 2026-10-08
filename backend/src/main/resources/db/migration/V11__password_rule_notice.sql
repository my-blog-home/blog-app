-- 비밀번호 규칙이 '8~10자'에서 '8자 이상'으로 바뀌어 기본 안내글 문구를 고친다 (2026-10-08)
UPDATE notice SET content = replace(replace(content, '8~10자로', '8자 이상으로'), '8~10자예요', '8자 이상이에요'), updated_at = now()
WHERE content LIKE '%8~10자%';
