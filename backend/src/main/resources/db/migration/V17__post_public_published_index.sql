-- 첫 화면의 지금 핫한 글·인기 블로거: 가장 최근 공개 글 찾기와 "N일 안에 올린 글" 고르기에 쓴다
CREATE INDEX ix_post_public_published ON post (published_at DESC) WHERE status = 'PUBLISHED' AND visibility = 'PUBLIC';
