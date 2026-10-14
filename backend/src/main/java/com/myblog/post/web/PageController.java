package com.myblog.post.web;

import com.myblog.common.sql.PostVisibilitySql;
import com.myblog.post.service.Excerpts;
import com.myblog.post.service.PostQueryService;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * 화면 주소(/posts/1, /blogs/1, /manage 등)로 바로 들어오거나 새로고침하면 index.html을 돌려준다.
 * API(/api), 이미지(/images), 화면 파일(/assets), 점이 들어간 파일 이름은 해당하지 않는다.
 *
 * 카카오톡·페이스북 같은 링크 미리보기 수집기는 자바스크립트를 실행하지 않으므로, 서버가 index.html의
 * meta 자리(<!--meta:start--> ~ <!--meta:end-->)를 주소에 맞게 채워 준다.
 * - 공개 글: 글 제목, 본문 요약, 본문의 첫 이미지(없으면 사이트 기본 이미지)
 * - 블로그: 블로그 이름과 소개
 * - 그 밖, 비공개·임시저장·없는 글: 사이트 기본값 (비공개 글의 제목이 새지 않게)
 * 이미지 주소는 수집기가 읽을 수 있게 절대 주소로 바꾼다.
 */
@Controller
public class PageController {

    private static final String PROPERTY = "property";
    private static final String START = "<!--meta:start-->";
    private static final String END = "<!--meta:end-->";
    private static final String SITE = "Ylog";
    private static final String SITE_DESCRIPTION = "Ylog는 누구나 블로그를 만들어 글을 쓰고 나누는 블로그 서비스입니다.";
    private static final String DEFAULT_IMAGE = "/og-default.png";
    private static final int SUMMARY_LENGTH = 150;

    private record Meta(String type, String title, String description, String image) {
    }

    private final NamedParameterJdbcTemplate jdbc;
    private final String publicUrl;
    private volatile String template;

    /** publicUrl: 공개 주소(예: https://ylog.java21.net). 있으면 미리보기의 주소·이미지를 이 주소로 만든다 */
    public PageController(NamedParameterJdbcTemplate jdbc,
                          @org.springframework.beans.factory.annotation.Value("${blog.public-url:}") String publicUrl) {
        this.jdbc = jdbc;
        this.publicUrl = publicUrl.replaceAll("/+$", "");
    }

    @GetMapping({"/", "/{path:^(?!api$|images$|assets$|oauth2$)[^.]*}", "/{path:^(?!api$|images$|assets$|oauth2$)[^.]*}/**"})
    public ResponseEntity<String> page(HttpServletRequest request) throws IOException {
        String html = template();
        if (html == null) {
            // 화면을 빌드하지 않고 서버만 켠 개발 환경(화면은 Vite 개발 서버가 내려 줌)
            return ResponseEntity.notFound().build();
        }
        String base = publicUrl.isBlank()
                ? ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString() : publicUrl;
        String path = request.getRequestURI().substring(request.getContextPath().length());
        Meta meta = metaFor(path);
        String url = base + path;
        String image = absolute(base, meta.image());
        StringBuilder tags = new StringBuilder();
        tags.append(tag("name", "description", meta.description()));
        tags.append(tag(PROPERTY, "og:site_name", SITE));
        tags.append(tag(PROPERTY, "og:type", meta.type()));
        tags.append(tag(PROPERTY, "og:url", url));
        tags.append(tag(PROPERTY, "og:title", meta.title()));
        tags.append(tag(PROPERTY, "og:description", meta.description()));
        tags.append(tag(PROPERTY, "og:image", image));
        if (meta.image().equals(DEFAULT_IMAGE)) {
            tags.append(tag(PROPERTY, "og:image:width", "1200"));
            tags.append(tag(PROPERTY, "og:image:height", "630"));
        }
        tags.append(tag("name", "twitter:card", "summary_large_image"));
        tags.append(tag("name", "twitter:title", meta.title()));
        tags.append(tag("name", "twitter:description", meta.description()));
        tags.append(tag("name", "twitter:image", image));
        tags.append("    <title>").append(escape(meta.title().equals(SITE) ? SITE : meta.title() + " · " + SITE))
                .append("</title>\n");
        int start = html.indexOf(START);
        int end = html.indexOf(END);
        String body = start < 0 || end < 0 ? html
                : html.substring(0, start + START.length()) + "\n" + tags + "    " + html.substring(end);
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "html", StandardCharsets.UTF_8))
                .cacheControl(CacheControl.noCache())
                .body(body);
    }

    private Meta metaFor(String path) {
        String[] parts = path.split("/");
        Meta found = null;
        if (parts.length == 3 && parts[2].matches("\\d{1,18}")) {
            long id = Long.parseLong(parts[2]);
            if (parts[1].equals("posts")) {
                found = postMeta(id);
            } else if (parts[1].equals("blogs")) {
                found = blogMeta(id);
            }
        }
        return found != null ? found : new Meta("website", SITE, SITE_DESCRIPTION, DEFAULT_IMAGE);
    }

    /** 공개된 글이면 글 제목·요약·첫 이미지, 아니면 null */
    private Meta postMeta(long id) {
        List<Meta> found = jdbc.query("select p.title, p.body from post p where p.id = :id and "
                        + PostVisibilitySql.PUBLIC, Map.of("id", id),
                (rs, row) -> {
                    String body = rs.getString("body");
                    String summary = Excerpts.of(body, SUMMARY_LENGTH);
                    String image = PostQueryService.thumbnail(body);
                    return new Meta("article", rs.getString("title"),
                            summary.isBlank() ? SITE_DESCRIPTION : summary,
                            image == null ? DEFAULT_IMAGE : image);
                });
        return found.isEmpty() ? null : found.get(0);
    }

    /** 탈퇴하지 않은 회원의 블로그면 블로그 이름·소개, 아니면 null */
    private Meta blogMeta(long id) {
        List<Meta> found = jdbc.query("""
                        select b.name, b.description, m.nickname from blog b join member m on m.id = b.owner_id
                        where b.id = :id and m.withdrawn_at is null
                        """, Map.of("id", id),
                (rs, row) -> {
                    String description = rs.getString("description");
                    return new Meta("website", rs.getString("name"),
                            description == null || description.isBlank()
                                    ? rs.getString("nickname") + "님의 블로그" : description,
                            DEFAULT_IMAGE);
                });
        return found.isEmpty() ? null : found.get(0);
    }

    private String template() throws IOException {
        if (template == null) {
            ClassPathResource resource = new ClassPathResource("static/index.html");
            if (!resource.exists()) {
                return null;
            }
            try (InputStream in = resource.getInputStream()) {
                template = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
        return template;
    }

    private static String absolute(String base, String image) {
        return image.startsWith("http://") || image.startsWith("https://") ? image : base + image;
    }

    private static String tag(String attr, String key, String value) {
        return "    <meta " + attr + "=\"" + key + "\" content=\"" + escape(value) + "\" />\n";
    }

    static String escape(String value) {
        return value.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
