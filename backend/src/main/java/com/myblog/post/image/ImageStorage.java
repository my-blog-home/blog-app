package com.myblog.post.image;

/**
 * 이미지 파일을 두는 곳. 지금은 서버 디스크, 나중에 MinIO(S3) 구현으로 바꿀 수 있다 (research R-08, 헌법 V).
 */
public interface ImageStorage {

    void save(String key, byte[] bytes);

    byte[] load(String key);

    void delete(String key);

    java.util.stream.Stream<String> keys();
}
