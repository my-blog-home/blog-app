package com.myblog.blog.domain;

/** 분류의 공개 범위. 비공개 분류의 글은 블로그 주인만 본다 (BR-46) */
public enum CategoryVisibility {
    PUBLIC, PRIVATE
}
