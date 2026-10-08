package com.myblog.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

    protected Member() {
    }

    public Member(String email, String nickname, String passwordHash, Instant now) {
        this.email = email;
        this.nickname = nickname;
        this.passwordHash = passwordHash;
        this.profileColor = PROFILE_COLORS.get(0);
        this.createdAt = now;
        this.updatedAt = now;
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
}
