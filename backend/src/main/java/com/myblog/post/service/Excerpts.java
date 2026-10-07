package com.myblog.post.service;

/**
 * 목록 미리보기: 마크다운 기호를 빼고 줄바꿈을 공백으로 바꾼 뒤 앞부분만 자른다 (CF-10-5, research R-10).
 */
public final class Excerpts {

    private Excerpts() {
    }

    public static String of(String markdown, int maxLength) {
        String text = markdown
                .replaceAll("!\\[[^\\]]*]\\([^)]*\\)", " ")       // 이미지
                .replaceAll("\\[([^\\]]*)]\\([^)]*\\)", "$1")     // 링크는 글자만
                .replaceAll("(?m)^\\s{0,3}(#{1,6}|>|[-*+]|\\d+\\.)\\s+", "") // 제목·인용·목록 기호
                .replaceAll("[*_~`]", "")
                .replaceAll("\\s+", " ")
                .strip();
        if (text.codePointCount(0, text.length()) <= maxLength) {
            return text;
        }
        int end = text.offsetByCodePoints(0, maxLength);
        return text.substring(0, end) + "…";
    }
}
