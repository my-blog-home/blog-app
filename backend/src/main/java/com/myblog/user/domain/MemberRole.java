package com.myblog.user.domain;

/** 회원 = MEMBER, 관리자 = ADMIN. 관리자는 가입으로 만들 수 없다 (FR-078, BR-47) */
public enum MemberRole {
    MEMBER, ADMIN
}
