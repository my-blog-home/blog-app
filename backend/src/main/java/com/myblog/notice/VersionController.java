package com.myblog.notice;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 지금 돌아가는 서비스 버전과 그 버전의 릴리스 노트 공지 (화면 바닥글에 보여 준다).
 */
@RestController
public class VersionController {

    private final String version;
    private final NamedParameterJdbcTemplate jdbc;

    public VersionController(ObjectProvider<BuildProperties> build, NamedParameterJdbcTemplate jdbc) {
        BuildProperties props = build.getIfAvailable();
        this.version = props == null ? "dev" : props.getVersion();
        this.jdbc = jdbc;
    }

    @GetMapping("/api/config/version")
    public Map<String, Object> version() {
        List<Long> ids = jdbc.queryForList("select id from notice where release_version = :v",
                Map.of("v", version), Long.class);
        Map<String, Object> body = new HashMap<>();
        body.put("version", version);
        body.put("releaseNoticeId", ids.isEmpty() ? null : ids.get(0));
        return body;
    }
}
