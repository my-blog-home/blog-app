package com.myblog.post.image;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;

/**
 * 확장자가 아니라 파일 앞부분으로 형식을 확인한다 (CF-22-1, research R-08).
 */
public enum ImageType {
    JPEG("image/jpeg", "jpg"),
    PNG("image/png", "png"),
    GIF("image/gif", "gif"),
    WEBP("image/webp", "webp");

    private final String contentType;
    private final String extension;

    ImageType(String contentType, String extension) {
        this.contentType = contentType;
        this.extension = extension;
    }

    public String contentType() {
        return contentType;
    }

    public String extension() {
        return extension;
    }

    public static Optional<ImageType> detect(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return Optional.of(JPEG);
        }
        if (b.length >= 8 && Arrays.equals(Arrays.copyOf(b, 8),
                new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A})) {
            return Optional.of(PNG);
        }
        if (b.length >= 6) {
            String head = new String(b, 0, 6, StandardCharsets.US_ASCII);
            if (head.equals("GIF87a") || head.equals("GIF89a")) {
                return Optional.of(GIF);
            }
        }
        if (b.length >= 12 && new String(b, 0, 4, StandardCharsets.US_ASCII).equals("RIFF")
                && new String(b, 8, 4, StandardCharsets.US_ASCII).equals("WEBP")) {
            return Optional.of(WEBP);
        }
        return Optional.empty();
    }

    public static Optional<ImageType> fromContentType(String contentType) {
        return Arrays.stream(values()).filter(t -> t.contentType.equals(contentType)).findFirst();
    }
}
