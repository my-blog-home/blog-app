package com.myblog.admin;

import com.myblog.common.error.ApiException;
import com.myblog.common.error.ErrorCode;
import com.myblog.common.error.Messages;
import com.myblog.common.security.CurrentMember;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 관리자 확인 (FR-078, BR-47). 관리자 API는 요청마다 회원 표의 role을 다시 읽어 확인한다
 * (화면에서 메뉴를 숨기는 것만으로는 막은 것이 아니다). 로그인하지 않았으면 401, 관리자가 아니면 403.
 */
@Component
public class AdminGuard {

    private final NamedParameterJdbcTemplate jdbc;

    public AdminGuard(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 관리자면 회원 id를 돌려준다 */
    public long requireAdmin() {
        long id = CurrentMember.id();
        List<String> role = jdbc.queryForList("select role from member where id = :id and withdrawn_at is null",
                Map.of("id", id), String.class);
        if (role.isEmpty() || !"ADMIN".equals(role.get(0))) {
            throw new ApiException(ErrorCode.FORBIDDEN, Messages.ADMIN_ONLY);
        }
        return id;
    }
}
