-- 지우지 않고 숨기는 공지. 릴리스 노트를 모음 공지로 합칠 때 옛 버전 공지를 숨긴다
ALTER TABLE notice ADD COLUMN hidden BOOLEAN NOT NULL DEFAULT false;
