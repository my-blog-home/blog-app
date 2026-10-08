package com.myblog.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Entity
@Table(name = "member")
public class Member {

    /** 프로필 색은 정해진 6가지 중 하나. 첫 번째가 기본값 (FR-05) */
    public static final List<String> PROFILE_COLORS =
            List.of("#c9dcfb", "#e2d8f8", "#cdeee4", "#f8d6c6", "#f5e3ad", "#f9dbe8");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(nullable = false, length = 10)
    private String nickname;

    @Column(length = 100)
    private String bio;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "profile_color", nullable = false, length = 7)
    private String profileColor;

    @Column(name = "failed_login_count", nullable = false)
    private short failedLoginCount;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** 회원 또는 관리자 (FR-078) */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private MemberRole role;

    /** 탈퇴한 시각. 값이 있으면 탈퇴한 회원(소프트 삭제, FR-086) */
    @Column(name = "withdrawn_at")
    private Instant withdrawnAt;

    /** 탈퇴한 회원의 원래 이메일. 재가입 대기 기간에만 보관한다 (FR-087) */
    @Column(name = "original_email", length = 254)
    private String originalEmail;

    /** 어떤 비밀번호와도 맞지 않는 값. 탈퇴한 회원의 비밀번호 자리에 둔다 */
    public static final String UNUSABLE_PASSWORD = "!";

    private static final int EMAIL_MAX = 254;

    protected Member() {
    }

    public Member(String email, String nickname, String passwordHash, Instant now) {
        this(email, nickname, passwordHash, MemberRole.MEMBER, now);
    }

    public Member(String email, String nickname, String passwordHash, MemberRole role, Instant now) {
        this.email = email;
        this.nickname = nickname;
        this.passwordHash = passwordHash;
        this.role = role;
        this.profileColor = PROFILE_COLORS.get(0);
        this.createdAt = now;
        this.updatedAt = now;
    }

    public boolean isAdmin() {
        return role == MemberRole.ADMIN;
    }

    public boolean isWithdrawn() {
        return withdrawnAt != null;
    }

    /**
     * 탈퇴 표시: 원래 이메일은 따로 보관하고, 이메일 앞에 del_{번호}_를 붙이고(칸 길이에 맞게 자름),
     * 닉네임은 탈퇴{번호}, 소개와 비밀번호는 지운다 (FR-086, BR-23, CR-64)
     */
    public void withdraw(Instant now) {
        String original = email.toLowerCase(java.util.Locale.ROOT);
        String masked = "del_" + id + "_" + original;
        this.originalEmail = original;
        this.email = masked.length() > EMAIL_MAX ? masked.substring(0, EMAIL_MAX) : masked;
        this.nickname = withdrawnNickname(id);
        this.bio = null;
        this.passwordHash = UNUSABLE_PASSWORD;
        this.failedLoginCount = 0;
        this.lockedUntil = null;
        this.withdrawnAt = now;
        this.updatedAt = now;
    }

    /** 탈퇴한 회원의 닉네임. 닉네임 칸(10자)과 유일 규칙을 지키도록 번호를 쓴다 */
    public static String withdrawnNickname(long id) {
        return "탈퇴" + id;
    }

    public boolean isLocked(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    /** 잠금 시간이 지났으면 실패 횟수를 0으로 되돌린다 (CF-02-7) */
    public void clearExpiredLock(Instant now) {
        if (lockedUntil != null && !lockedUntil.isAfter(now)) {
            lockedUntil = null;
            failedLoginCount = 0;
            updatedAt = now;
        }
    }

    /** 실패를 하나 더하고, 한도에 닿으면 잠근다 (CF-02-5, CF-15-14) */
    public void recordFailure(int maxFailures, Duration lockDuration, Instant now) {
        failedLoginCount++;
        if (failedLoginCount >= maxFailures) {
            lockedUntil = now.plus(lockDuration);
        }
        updatedAt = now;
    }

    public void resetFailures(Instant now) {
        if (failedLoginCount != 0 || lockedUntil != null) {
            failedLoginCount = 0;
            lockedUntil = null;
            updatedAt = now;
        }
    }

    public void updateProfile(String nickname, String bio, String profileColor, Instant now) {
        this.nickname = nickname;
        this.bio = bio;
        this.profileColor = profileColor;
        this.updatedAt = now;
    }

    public void changePasswordHash(String passwordHash, Instant now) {
        this.passwordHash = passwordHash;
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getNickname() {
        return nickname;
    }

    public String getBio() {
        return bio;
    }

    public String getProfileColor() {
        return profileColor;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public MemberRole getRole() {
        return role;
    }

    public Instant getWithdrawnAt() {
        return withdrawnAt;
    }

    public String getOriginalEmail() {
        return originalEmail;
    }
}
