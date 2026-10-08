-- 릴리스 노트를 공지로 올릴 때 버전마다 한 번만 올라가게 한다 (2026-10-08)
ALTER TABLE notice ADD COLUMN release_version VARCHAR(20);
CREATE UNIQUE INDEX ux_notice_release_version ON notice (release_version) WHERE release_version IS NOT NULL;
