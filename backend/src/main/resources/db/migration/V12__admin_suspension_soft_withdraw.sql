-- 관리자 (FR-078, BR-47). 관리자도 같은 회원 표에 role = 'ADMIN'으로 둔다. 가입으로는 만들 수 없다
ALTER TABLE member ADD COLUMN role VARCHAR(10) NOT NULL DEFAULT 'MEMBER' CHECK (role IN ('MEMBER', 'ADMIN'));

-- 탈퇴는 소프트 삭제 (FR-086, FR-087, BR-23). 회원 행은 남기고 탈퇴 시각을 채운다.
-- original_email은 재가입 대기 기간(30일) 동안만 보관하고, 지나면 매일 작업이 비운다
ALTER TABLE member ADD COLUMN withdrawn_at TIMESTAMPTZ;
ALTER TABLE member ADD COLUMN original_email VARCHAR(254);
CREATE INDEX ix_member_original_email ON member (lower(original_email)) WHERE original_email IS NOT NULL;

-- 회원 정지 기록 (FR-080, FR-081, BR-49). 정지 중인지는 칸이 아니라
-- "풀리지 않았고(lifted_at 없음), 영구이거나(ends_at 없음) 끝나는 시각이 지금보다 뒤"로 계산한다.
-- 회원 행은 지우지 않으므로 회원을 가리키는 칸에는 ON DELETE를 두지 않는다
CREATE TABLE suspension (
    id          BIGSERIAL PRIMARY KEY,
    member_id   BIGINT       NOT NULL REFERENCES member (id),
    reason      VARCHAR(200) NOT NULL DEFAULT '',
    starts_at   TIMESTAMPTZ  NOT NULL,
    ends_at     TIMESTAMPTZ,
    report_id   BIGINT       REFERENCES report (id) ON DELETE SET NULL,
    created_by  BIGINT       NOT NULL REFERENCES member (id),
    lifted_at   TIMESTAMPTZ,
    lifted_by   BIGINT       REFERENCES member (id),
    CONSTRAINT ck_suspension_period CHECK (ends_at IS NULL OR ends_at > starts_at),
    CONSTRAINT ck_suspension_lifted CHECK ((lifted_at IS NULL) = (lifted_by IS NULL))
);
CREATE INDEX ix_suspension_active ON suspension (member_id) WHERE lifted_at IS NULL;
CREATE INDEX ix_suspension_member ON suspension (member_id, starts_at DESC);

-- 신고를 받은 회원별로 세기 위한 인덱스 (회원 관리의 받은 신고 수)
CREATE INDEX ix_report_target_author ON report (target_author_id);

-- 이용 안내의 신고 설명을 지금 동작에 맞춘다 (관리자가 신고를 처리한다)
UPDATE notice
SET content = replace(content,
        '3. 신고는 접수되어 저장돼요. 지금은 신고를 확인하고 처리하는 화면은 따로 없어요.',
        '3. 신고는 운영자가 확인해 처리해요. 규칙을 어긴 글·댓글은 지워지고, 작성자는 이용이 정지될 수 있어요.')
WHERE title = '부적절한 글·댓글 신고하기';
UPDATE notice
SET content = replace(content,
        '회원 탈퇴는 마이페이지에서 할 수 있고, 탈퇴하면 내 블로그와 글이 모두 지워져요.',
        '회원 탈퇴는 마이페이지에서 할 수 있고, 탈퇴하면 내 블로그와 글이 모두 지워져요. 같은 이메일로는 탈퇴한 날부터 30일이 지나야 다시 가입할 수 있어요.')
WHERE title = '서비스 이용 시 유의사항';
