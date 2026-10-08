package com.myblog.post.service;

import com.myblog.common.error.ApiException;
import com.myblog.common.error.Messages;
import com.myblog.common.sql.PostVisibilitySql;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 작성자 프로필: 닉네임·소개·프로필 색, 공개 글 수, 블로그 카드 (FR-075, BR-28).
 * 내가 내 프로필을 보면 비공개 글 수(비공개 글 + 비공개 분류의 글)도 알려 준다. 임시저장 글은 어디에도 세지 않는다.
 * 없는 회원(탈퇴 포함)은 "존재하지 않는 회원입니다".
 * 블로그·글 정보를 함께 읽으므로 post 모듈에 둔다 (post → blog → user 방향).
 */
@Service
public class AuthorProfileService {

    public record BlogCard(long id, String name, String description, String topicName, long subscriberCount) {
    }

    /** privatePostCount는 내 프로필일 때만 있다 */
    public record Profile(long id, String nickname, String bio, String profileColor, long publicPostCount,
                          Long privatePostCount, boolean isMe, BlogCard blog) {
    }

    private final NamedParameterJdbcTemplate jdbc;

    public AuthorProfileService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public Profile profile(long memberId, Long viewerId) {
        Map<String, Object> params = new HashMap<>();
        params.put("id", memberId);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "select nickname, bio, profile_color from member where id = :id", params);
        if (rows.isEmpty()) {
            throw ApiException.notFound(Messages.MEMBER_NOT_FOUND);
        }
        Map<String, Object> member = rows.get(0);
        boolean isMe = viewerId != null && viewerId == memberId;

        Map<String, Object> counts = jdbc.queryForMap("""
                select count(*) filter (where %s) as public_cnt,
                       count(*) filter (where not %s) as private_cnt
                from post p
                where p.blog_id in (select b.id from blog b where b.owner_id = :id)
                  and %s
                """.formatted(PostVisibilitySql.PUBLIC, PostVisibilitySql.PUBLIC, PostVisibilitySql.PUBLISHED), params);

        BlogCard blog = jdbc.query("""
                select b.id, b.name, b.description, t.name as topic_name,
                       (select count(*) from subscription s where s.blog_id = b.id) as subscriber_count
                from blog b join topic t on t.id = b.topic_id
                where b.owner_id = :id
                order by b.id limit 1
                """, params, (rs, row) -> new BlogCard(rs.getLong("id"), rs.getString("name"),
                rs.getString("description"), rs.getString("topic_name"), rs.getLong("subscriber_count")))
                .stream().findFirst().orElse(null);

        return new Profile(memberId, (String) member.get("nickname"), (String) member.get("bio"),
                (String) member.get("profile_color"), ((Number) counts.get("public_cnt")).longValue(),
                isMe ? ((Number) counts.get("private_cnt")).longValue() : null, isMe, blog);
    }

    /** 글 목록을 읽기 전에 회원이 있는지 확인한다 */
    @Transactional(readOnly = true)
    public void requireMember(long memberId) {
        Long count = jdbc.queryForObject("select count(*) from member where id = :id", Map.of("id", memberId), Long.class);
        if (count == null || count == 0) {
            throw ApiException.notFound(Messages.MEMBER_NOT_FOUND);
        }
    }
}
