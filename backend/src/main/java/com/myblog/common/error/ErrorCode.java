package com.myblog.common.error;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED),
    LOGIN_FAILED(HttpStatus.UNAUTHORIZED),
    FORBIDDEN(HttpStatus.FORBIDDEN),
    /** 비밀번호가 맞은 회원이 정지 중 (FR-081) */
    ACCOUNT_SUSPENDED(HttpStatus.FORBIDDEN),
    ADMIN_CANNOT_WITHDRAW(HttpStatus.FORBIDDEN),
    NOT_VERIFIED(HttpStatus.FORBIDDEN),
    NOT_FOUND(HttpStatus.NOT_FOUND),
    CONFLICT(HttpStatus.CONFLICT),
    /** 탈퇴한 지 30일이 안 된 이메일로 가입 (FR-087) */
    REJOIN_WAIT(HttpStatus.CONFLICT),
    /** 이미 처리한 신고가 섞임 (FR-079) */
    ALREADY_HANDLED(HttpStatus.CONFLICT),
    /** 관리자·탈퇴한 회원은 정지할 수 없음 / 정지 중이 아닌 회원의 해제 (FR-080) */
    CANNOT_SUSPEND(HttpStatus.CONFLICT),
    NOT_SUSPENDED(HttpStatus.CONFLICT),
    CODE_GONE(HttpStatus.GONE),
    ACCOUNT_LOCKED(HttpStatus.LOCKED),
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS),
    SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
