package com.myblog.post.service;

/**
 * 글 상세를 열었다는 알림. 통계 모듈이 듣고 센다 (stats → post 방향을 지키려고 이벤트로 알린다).
 */
public record PostViewedEvent(long postId, long blogId, long ownerId, String visitorKey, Long viewerId) {
}
