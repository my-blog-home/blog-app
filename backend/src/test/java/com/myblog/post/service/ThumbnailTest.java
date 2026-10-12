package com.myblog.post.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 목록 썸네일은 본문의 첫 이미지: 직접 올린 이미지와 http(s) 이미지 주소 */
class ThumbnailTest {

    @Test
    void 직접_올린_이미지() {
        String id = "123e4567-e89b-12d3-a456-426614174000";
        assertThat(PostQueryService.thumbnail("글\n![사진](/images/" + id + ".png)\n끝"))
                .isEqualTo("/images/" + id + ".png");
    }

    @Test
    void 외부_이미지_주소와_제목() {
        assertThat(PostQueryService.thumbnail("![a](https://example.com/a.jpg \"제목\") ![b](/images/x.png)"))
                .isEqualTo("https://example.com/a.jpg");
    }

    @Test
    void 이미지가_없거나_허용하지_않는_주소() {
        assertThat(PostQueryService.thumbnail("그냥 글 [링크](https://example.com)")).isNull();
        assertThat(PostQueryService.thumbnail("![x](javascript:alert(1))")).isNull();
        assertThat(PostQueryService.thumbnail("![x](data:image/png;base64,AAAA)")).isNull();
    }
}
