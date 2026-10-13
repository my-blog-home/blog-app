package com.myblog.user.social;

import com.myblog.blog.service.BlogCreationService;
import com.myblog.common.error.ApiException;
import com.myblog.common.error.ErrorCode;
import com.myblog.common.error.Messages;
import com.myblog.user.domain.Member;
import com.myblog.user.domain.MemberRepository;
import com.myblog.user.service.LoginService;
import com.myblog.user.service.RejoinPolicy;
import com.myblog.user.validation.InputRules;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 소셜 로그인 규칙.
 * 1. 이미 연결된 소셜 계정이면 그 회원으로 로그인한다.
 * 2. 처음 보는 소셜 계정이지만 제공자가 인증한 이메일이 기존 회원과 같으면, 그 회원에 연결하고 로그인한다(중복 계정을 만들지 않는다).
 * 3. 그 밖에는 닉네임과 약관 동의를 받아 새 회원과 블로그를 만든다(이메일 가입과 같은 규칙: 닉네임 규칙, 탈퇴 후 30일 대기).
 * 제공자가 인증한 이메일이 없으면 이메일로 연결하지 않는다. 이메일을 주지 않는 제공자(카카오)는 회원번호로만 알아보고
 * 이메일 없이 가입하며, 그 밖의 제공자는 인증 이메일이 없으면 가입하지 않는다. 정지된 회원은 이메일 로그인과 같이 막는다.
 */
@Service
public class SocialLoginService {

    public sealed interface Outcome permits LoggedIn, NeedsSignup, Rejected {
    }

    public record LoggedIn(long memberId, boolean linkedNow) implements Outcome {
    }

    public record NeedsSignup(SocialProfile profile, String suggestedNickname) implements Outcome {
    }

    /** reason은 화면 주소에 붙는 짧은 이름이다 (email, suspended, withdrawn-wait 등) */
    public record Rejected(String reason) implements Outcome {
    }

    private final NamedParameterJdbcTemplate jdbc;
    private final MemberRepository members;
    private final LoginService loginService;
    private final InputRules rules;
    private final RejoinPolicy rejoin;
    private final BlogCreationService blogCreation;
    private final Clock clock;

    public SocialLoginService(NamedParameterJdbcTemplate jdbc, MemberRepository members, LoginService loginService,
                              InputRules rules, RejoinPolicy rejoin, BlogCreationService blogCreation, Clock clock) {
        this.jdbc = jdbc;
        this.members = members;
        this.loginService = loginService;
        this.rules = rules;
        this.rejoin = rejoin;
        this.blogCreation = blogCreation;
        this.clock = clock;
    }

    @Transactional
    public Outcome login(SocialProfile profile) {
        Optional<Long> linked = linkedMember(profile);
        if (linked.isPresent()) {
            return allowed(linked.get(), false);
        }
        if (!hasVerifiedEmail(profile)) {
            // 인증 이메일이 없으면 기존 계정에 연결하지 않는다(남의 계정을 가로채지 못하게)
            return SocialProviders.allowsNoEmail(profile.provider())
                    ? new NeedsSignup(profile, suggestNickname(profile.nickname()))
                    : new Rejected("email");
        }
        String email = profile.email().strip().toLowerCase(java.util.Locale.ROOT);
        Optional<Member> existing = members.findByEmail(email).filter(m -> !m.isWithdrawn());
        if (existing.isPresent()) {
            Member member = existing.get();
            if (member.isAdmin()) {
                // 관리자 계정은 소셜 로그인으로 연결하지 않는다
                return new Rejected("admin");
            }
            Outcome outcome = allowed(member.getId(), true);
            if (outcome instanceof LoggedIn) {
                link(member.getId(), profile);
            }
            return outcome;
        }
        try {
            rejoin.check(email);
        } catch (ApiException e) {
            return new Rejected("rejoin");
        }
        return new NeedsSignup(profile, suggestNickname(profile.nickname()));
    }

    /** 처음 가입: 닉네임·약관 동의를 받아 회원(비밀번호 없음)과 블로그를 만들고 소셜 계정을 연결한다 */
    @Transactional
    public long completeSignup(SocialProfile profile, String rawNickname, boolean agreed) {
        if (!agreed) {
            throw ApiException.field("agreed", Messages.TERMS_REQUIRED);
        }
        String nickname = rules.checkNickname(rawNickname);
        boolean noEmail = !hasVerifiedEmail(profile);
        if (noEmail && !SocialProviders.allowsNoEmail(profile.provider())) {
            throw new ApiException(ErrorCode.CONFLICT, Messages.SOCIAL_SIGNUP_EXPIRED);
        }
        String email = noEmail ? Member.noEmailFor(profile.provider(), profile.providerUserId())
                : rules.normalizeEmail(profile.email());
        if (linkedMember(profile).isPresent() || members.existsByEmail(email)) {
            throw new ApiException(ErrorCode.CONFLICT, Messages.EMAIL_DUPLICATE);
        }
        if (!noEmail) {
            rejoin.check(email);
        }
        if (members.existsByNickname(nickname)) {
            throw ApiException.field("nickname", Messages.NICKNAME_DUPLICATE);
        }
        Instant now = clock.instant();
        Member member;
        try {
            member = members.saveAndFlush(new Member(email, nickname, Member.UNUSABLE_PASSWORD, now));
        } catch (DataIntegrityViolationException e) {
            throw new ApiException(ErrorCode.CONFLICT, Messages.EMAIL_DUPLICATE);
        }
        blogCreation.createFor(member.getId(), nickname, now);
        link(member.getId(), profile);
        return member.getId();
    }

    private static boolean hasVerifiedEmail(SocialProfile profile) {
        return profile.email() != null && !profile.email().isBlank() && profile.emailVerified();
    }

    private Outcome allowed(long memberId, boolean linkedNow) {
        if (loginService.activeSuspension(memberId).isPresent()) {
            return new Rejected("suspended");
        }
        return new LoggedIn(memberId, linkedNow);
    }

    private Optional<Long> linkedMember(SocialProfile profile) {
        List<Long> ids = jdbc.queryForList("""
                select s.member_id from member_social s join member m on m.id = s.member_id
                where s.provider = :provider and s.provider_user_id = :uid and m.withdrawn_at is null
                """, Map.of("provider", profile.provider(), "uid", profile.providerUserId()), Long.class);
        return ids.stream().findFirst();
    }

    private void link(long memberId, SocialProfile profile) {
        jdbc.update("""
                insert into member_social (member_id, provider, provider_user_id, created_at)
                values (:member, :provider, :uid, :now)
                on conflict (provider, provider_user_id) do nothing
                """, Map.of("member", memberId, "provider", profile.provider(), "uid", profile.providerUserId(),
                "now", Timestamp.from(clock.instant())));
    }

    /** 제공자 닉네임이 우리 닉네임 규칙에 맞고 비어 있으면 그대로 제안하고, 아니면 비워 둔다 */
    private String suggestNickname(String raw) {
        if (raw == null) {
            return "";
        }
        try {
            String nickname = rules.checkNickname(raw.strip());
            return members.existsByNickname(nickname) ? "" : nickname;
        } catch (ApiException e) {
            return "";
        }
    }
}
