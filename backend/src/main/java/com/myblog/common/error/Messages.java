package com.myblog.common.error;

/**
 * 화면에 보여 줄 안내 문구. 원천 문서 각 파일의 '안내 문구' 표와 같아야 한다 (FR-057).
 */
public final class Messages {

    public static final String INVALID_INPUT = "입력값을 확인해 주세요";
    public static final String EMAIL_FORMAT = "이메일 형식이 올바르지 않습니다";
    public static final String EMAIL_DUPLICATE = "이미 가입된 이메일입니다";
    public static final String NICKNAME_RULE = "닉네임은 한글, 영문, 숫자로 2~10자여야 합니다";
    public static final String NICKNAME_DUPLICATE = "이미 사용 중인 닉네임입니다";
    public static final String PASSWORD_RULE = "비밀번호는 영문, 숫자, 특수문자를 포함해 8자 이상으로 입력해 주세요";
    public static final String PASSWORD_MISMATCH = "비밀번호가 일치하지 않습니다";
    public static final String CODE_SENT = "인증번호를 보냈습니다. 10분 안에 입력해 주세요";
    public static final String CODE_WRONG = "인증번호가 올바르지 않습니다";
    public static final String CODE_EXPIRED = "인증번호가 만료되었습니다. 인증번호를 다시 받아 주세요";
    public static final String CODE_TOO_MANY_FAILURES = "인증번호 입력 횟수를 초과했습니다. 인증번호를 다시 받아 주세요";
    public static final String RESEND_TOO_SOON = "잠시 뒤에 다시 요청해 주세요";
    public static final String RESEND_DAILY_LIMIT = "오늘은 더 이상 인증번호를 보낼 수 없습니다";
    public static final String VERIFIED = "이메일 인증이 완료되었습니다";
    public static final String VERIFY_FIRST = "이메일 인증을 먼저 완료해 주세요";
    public static final String VERIFY_EXPIRED = "인증 유효 시간이 지났습니다. 이메일 인증을 다시 해 주세요";
    public static final String SIGNUP_DONE = "가입이 완료되었습니다. 로그인해 주세요";
    public static final String MAIL_FAILED = "메일을 보내지 못했습니다. 잠시 뒤 다시 시도해 주세요";
    public static final String LOGIN_FAILED = "이메일 또는 비밀번호가 올바르지 않습니다";
    public static final String ACCOUNT_LOCKED = "로그인 시도가 5회 실패해 잠겼습니다. %d분 뒤에 다시 시도해 주세요";
    public static final String LOGIN_REQUIRED = "로그인이 필요합니다";
    public static final String TRY_LATER = "잠시 뒤 다시 시도해 주세요";

    public static final String SAVED = "저장했습니다";
    public static final String CURRENT_PASSWORD_WRONG = "현재 비밀번호가 올바르지 않습니다";
    public static final String NEW_PASSWORD_SAME = "현재 비밀번호와 다른 값을 입력해 주세요";
    public static final String WITHDRAW_AGREE = "안내를 읽고 동의해 주세요";
    public static final String BIO_TOO_LONG = "소개는 100자 이하로 입력해 주세요";

    public static final String TITLE_REQUIRED = "제목을 입력해 주세요";
    public static final String BODY_REQUIRED = "본문을 입력해 주세요";
    public static final String POST_NOT_FOUND = "존재하지 않는 글입니다";
    public static final String NOT_FOUND = "찾을 수 없습니다";
    public static final String CATEGORY_DUPLICATE = "이미 있는 분류입니다";
    public static final String CATEGORY_NAME_REQUIRED = "분류 이름을 입력해 주세요";
    public static final String CATEGORY_HAS_POSTS = "이 분류에 글이 %d개 있어 삭제할 수 없습니다. 글을 다른 분류로 옮긴 뒤 삭제해 주세요";
    public static final String CATEGORY_DEFAULT_UNDELETABLE = "미분류는 삭제할 수 없습니다";
    public static final String BLOG_NAME_REQUIRED = "블로그 이름을 입력해 주세요";
    public static final String COMMENT_REQUIRED = "댓글을 입력해 주세요";
    public static final String COMMENT_TOO_FAST = "잠시 뒤에 다시 등록해 주세요";
    public static final String COMMENT_NOT_FOUND = "존재하지 않는 댓글입니다";
    public static final String LIKE_OWN_POST = "내 글에는 좋아요를 누를 수 없습니다";
    public static final String TAG_RULE = "태그는 공백과 쉼표 없이 1~15자로 입력해 주세요";
    public static final String TAG_TOO_MANY = "태그는 글마다 5개까지 붙일 수 있습니다";
    public static final String REPORT_DONE = "신고가 접수되었습니다";
    public static final String REPORT_DUPLICATE = "이미 신고한 글입니다";
    public static final String REPORT_OWN_POST = "내 글은 신고할 수 없습니다";
    public static final String REPORT_REASON = "신고 사유를 골라 주세요";
    public static final String REPORT_OWN_COMMENT = "내 댓글은 신고할 수 없습니다";
    public static final String REPORT_COMMENT_DUPLICATE = "이미 신고한 댓글입니다";
    public static final String REPLY_TO_REPLY = "답글에는 답글을 달 수 없습니다";
    public static final String SUBSCRIBE_OWN_BLOG = "내 블로그는 구독할 수 없습니다";
    public static final String IMAGE_RULE = "이미지는 5MB 이하의 jpg, png, gif, webp만 올릴 수 있습니다";
    public static final String IMAGE_TOO_MANY = "이미지는 글마다 10장까지 넣을 수 있습니다";
    public static final String SEARCH_TOO_SHORT = "검색어를 2자 이상 입력해 주세요";
    public static final String CATEGORY_NOT_FOUND = "존재하지 않는 분류입니다";
    public static final String CATEGORY_RESERVED_NAME = "미분류는 쓸 수 없는 이름입니다";
    public static final String CATEGORY_DEFAULT_UNMOVABLE = "미분류는 맨 뒤에 있고 옮길 수 없습니다";
    public static final String CATEGORY_VISIBILITY = "분류의 공개 범위를 골라 주세요";
    public static final String TOPIC_INVALID = "주제를 다시 골라 주세요";
    public static final String PROFILE_COLOR_INVALID = "프로필 색을 다시 골라 주세요";
    public static final String DRAFT_TITLE = "제목 없음";
    public static final String MEMBER_NOT_FOUND = "존재하지 않는 회원입니다";
    public static final String NOTICE_NOT_FOUND = "존재하지 않는 공지입니다";

    public static final String NICKNAME_RESERVED = "쓸 수 없는 닉네임입니다";
    public static final String WITHDRAWN_USER = "탈퇴한 사용자";
    /** %d: 대기 일수, %s: 가입할 수 있는 날 (yyyy. M. d.) (FR-087) */
    public static final String REJOIN_WAIT = "탈퇴한 지 %d일이 지나지 않아 같은 이메일로 다시 가입할 수 없습니다. %s부터 가입할 수 있습니다";
    public static final String ADMIN_ONLY = "관리자만 볼 수 있는 화면입니다";
    public static final String ADMIN_CANNOT_WITHDRAW = "관리자 계정은 탈퇴할 수 없습니다";
    /** 정지 안내 (FR-081). 끝나는 시각 또는 영구, 사유(있을 때만)를 이어 붙인다 */
    public static final String SUSPENDED_PREFIX = "정지된 계정입니다.";
    public static final String SUSPENDED_UNTIL = "%s까지 정지입니다.";
    public static final String SUSPENDED_PERMANENT = "영구 정지입니다.";
    public static final String SUSPENDED_REASON = "사유: %s.";
    public static final String SUSPENDED_SUFFIX = "운영자에게 문의해 주세요";
    public static final String REPORT_NOT_FOUND = "존재하지 않는 신고입니다";
    public static final String REPORT_ALREADY_HANDLED = "이미 처리한 신고입니다";
    public static final String REPORT_ACTION = "처리 방법을 골라 주세요";
    public static final String REPORT_IDS_REQUIRED = "처리할 신고를 골라 주세요";
    public static final String HANDLE_NOTE_TOO_LONG = "메모는 %d자 이하로 입력해 주세요";
    public static final String SUSPEND_ONLY_WITH_RESOLVE = "작성자 정지는 처리 완료와 함께만 할 수 있습니다";
    public static final String SUSPEND_ONE_AUTHOR = "작성자가 같은 신고만 함께 정지할 수 있습니다";
    public static final String SUSPEND_DAYS = "정지 기간을 골라 주세요";
    public static final String SUSPEND_REASON_TOO_LONG = "정지 사유는 %d자 이하로 입력해 주세요";
    public static final String CANNOT_SUSPEND = "정지할 수 없는 회원입니다";
    public static final String NOT_SUSPENDED = "정지 중인 회원이 아닙니다";
    public static final String NOTICE_TYPE = "종류를 골라 주세요";
    public static final String NOTICE_TITLE_RULE = "제목은 1~%d자로 입력해 주세요";
    public static final String NOTICE_CONTENT_RULE = "내용은 1~%d자로 입력해 주세요";

    private Messages() {
    }
}
