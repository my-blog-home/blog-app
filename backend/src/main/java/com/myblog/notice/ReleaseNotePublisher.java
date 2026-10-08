package com.myblog.notice;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 버전마다 하나씩 있는 릴리스 노트(resources/release-notes/{버전}.md)를 공지로 올린다.
 * 서버가 켜질 때 아직 올라가지 않은 버전만 올리므로, 같은 버전을 다시 배포해도 중복되지 않는다.
 *
 * 파일 형식: 맨 위에 "title: 제목", "date: yyyy-MM-dd"(선택), 그다음 "---" 줄, 그 아래가 본문(글자만).
 */
@Component
public class ReleaseNotePublisher implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ReleaseNotePublisher.class);

    public record ReleaseNote(String version, String title, String content) {
    }

    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;
    private final boolean publishOnStartup;

    public ReleaseNotePublisher(NamedParameterJdbcTemplate jdbc, Clock clock,
                                @Value("${blog.release-notes.publish:true}") boolean publishOnStartup) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.publishOnStartup = publishOnStartup;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (publishOnStartup) {
            publishAll();
        }
    }

    /** 아직 공지에 없는 버전의 노트를 올리고, 새로 올린 개수를 돌려준다 */
    @Transactional
    public int publishAll() {
        int published = 0;
        for (ReleaseNote note : readAll()) {
            Map<String, Object> params = new HashMap<>();
            params.put("version", note.version());
            params.put("title", note.title());
            params.put("content", note.content());
            params.put("now", Timestamp.from(clock.instant()));
            published += jdbc.update("""
                    insert into notice (type, title, content, pinned, created_at, release_version)
                    values ('NOTICE', :title, :content, false, :now, :version)
                    on conflict (release_version) where release_version is not null do nothing
                    """, params);
        }
        if (published > 0) {
            log.info("릴리스 노트 {}개를 공지로 올렸습니다", published);
        }
        return published;
    }

    /** 버전 순서대로(오래된 것 먼저) 읽는다 */
    public List<ReleaseNote> readAll() {
        try {
            Resource[] files = new PathMatchingResourcePatternResolver().getResources("classpath*:release-notes/*.md");
            List<ReleaseNote> notes = new ArrayList<>();
            for (Resource file : files) {
                String name = file.getFilename();
                if (name == null) {
                    continue;
                }
                String version = name.substring(0, name.length() - ".md".length());
                notes.add(parse(version, new String(file.getContentAsByteArray(), StandardCharsets.UTF_8)));
            }
            notes.sort(Comparator.comparing(ReleaseNote::version, ReleaseNotePublisher::compareVersions));
            return notes;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static ReleaseNote parse(String version, String text) {
        String title = "Ylog " + version + " 업데이트";
        int split = text.indexOf("\n---");
        String body = text;
        if (split >= 0) {
            for (String line : text.substring(0, split).split("\n")) {
                if (line.startsWith("title:")) {
                    title = line.substring("title:".length()).strip();
                }
            }
            body = text.substring(text.indexOf('\n', split + 1) + 1);
        }
        return new ReleaseNote(version, "[v" + version + "] " + title, body.strip());
    }

    static int compareVersions(String a, String b) {
        String[] x = a.split("\\.");
        String[] y = b.split("\\.");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int p = i < x.length ? parseInt(x[i]) : 0;
            int q = i < y.length ? parseInt(y[i]) : 0;
            if (p != q) {
                return Integer.compare(p, q);
            }
        }
        return a.compareTo(b);
    }

    private static int parseInt(String s) {
        try {
            return Integer.parseInt(s.replaceAll("\\D.*", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
