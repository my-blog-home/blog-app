package com.myblog.post.image;

import com.myblog.common.config.BlogLimits;
import com.myblog.common.error.ApiException;
import com.myblog.common.error.Messages;
import com.myblog.post.service.PostService;
import java.io.IOException;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

/**
 * 이미지 올리기와 글 연결 (CF-22). 파일은 저장소에, DB에는 이름만 둔다.
 */
@Service
public class ImageService {

    public record Uploaded(long id, String url) {
    }

    public record ImageFile(byte[] bytes, String contentType) {
    }

    private static final Pattern IMAGE_URL = Pattern.compile("/images/([0-9a-f\\-]{36}\\.(?:jpg|png|gif|webp))");

    private final ImageStorage storage;
    private final NamedParameterJdbcTemplate jdbc;
    private final PostService postService;
    private final BlogLimits limits;
    private final Clock clock;

    public ImageService(ImageStorage storage, NamedParameterJdbcTemplate jdbc, PostService postService,
                        BlogLimits limits, Clock clock) {
        this.storage = storage;
        this.jdbc = jdbc;
        this.postService = postService;
        this.limits = limits;
        this.clock = clock;
    }

    /** jpg·png·gif·webp, 5MB 이하만 받고 서버가 새 이름을 붙인다 (CF-22-1, 2) */
    @Transactional
    public Uploaded upload(long memberId, MultipartFile file) {
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw ApiException.field("file", Messages.IMAGE_RULE);
        }
        if (bytes.length == 0 || bytes.length > limits.imageMaxBytes()) {
            throw ApiException.field("file", Messages.IMAGE_RULE);
        }
        ImageType type = ImageType.detect(bytes).orElseThrow(() -> ApiException.field("file", Messages.IMAGE_RULE));
        String key = UUID.randomUUID() + "." + type.extension();
        storage.save(key, bytes);
        Long id = jdbc.queryForObject("""
                insert into post_image (uploader_id, storage_key, content_type, size_bytes, created_at)
                values (:uploader, :key, :type, :size, :now) returning id
                """, Map.of("uploader", memberId, "key", key, "type", type.contentType(), "size", bytes.length,
                "now", Timestamp.from(clock.instant())), Long.class);
        return new Uploaded(id, "/images/" + key);
    }

    /**
     * 글을 저장할 때 본문에 들어 있는 내 이미지만 그 글에 연결하고, 빠진 이미지는 연결을 푼다.
     * 연결이 풀린 이미지는 정리 작업이 하루 뒤 지운다 (CF-22-3, research R-08).
     */
    public void linkToPost(long postId, long memberId, String body) {
        Set<String> keys = new LinkedHashSet<>();
        Matcher matcher = IMAGE_URL.matcher(body);
        while (matcher.find()) {
            keys.add(matcher.group(1));
        }
        if (keys.size() > limits.imagesPerPost()) {
            throw ApiException.field("body", Messages.IMAGE_TOO_MANY);
        }
        jdbc.update("update post_image set post_id = null where post_id = :postId",
                Map.of("postId", postId));
        if (!keys.isEmpty()) {
            jdbc.update("""
                    update post_image set post_id = :postId
                    where storage_key in (:keys) and uploader_id = :memberId and (post_id is null or post_id = :postId)
                    """, Map.of("postId", postId, "keys", keys, "memberId", memberId));
        }
    }

    /** 글을 지우기 전에 그 글의 파일을 모아 두었다가 커밋 뒤 지운다 */
    public void deleteFilesAfterCommit(long postId) {
        List<String> keys = jdbc.queryForList("select storage_key from post_image where post_id = :postId",
                Map.of("postId", postId), String.class);
        deleteAfterCommit(keys);
    }

    public void deleteAfterCommit(List<String> keys) {
        if (keys.isEmpty()) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    keys.forEach(storage::delete);
                }
            });
        } else {
            keys.forEach(storage::delete);
        }
    }

    /** 연결 전 이미지는 올린 사람만, 연결된 이미지는 그 글을 볼 수 있는 사람만 본다 */
    @Transactional(readOnly = true)
    public ImageFile load(String key, Long viewerId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select post_id, uploader_id, content_type from post_image where storage_key = :key
                """, Map.of("key", key));
        if (rows.isEmpty()) {
            throw ApiException.notFound(Messages.NOT_FOUND);
        }
        Map<String, Object> row = rows.get(0);
        Number postId = (Number) row.get("post_id");
        if (postId == null) {
            if (viewerId == null || viewerId != ((Number) row.get("uploader_id")).longValue()) {
                throw ApiException.notFound(Messages.NOT_FOUND);
            }
        } else {
            postService.getVisible(postId.longValue(), viewerId);
        }
        return new ImageFile(storage.load(key), (String) row.get("content_type"));
    }
}
