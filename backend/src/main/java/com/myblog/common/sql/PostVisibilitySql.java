package com.myblog.common.sql;

/**
 * "방문자에게 보이는 글"의 SQL 조건을 한곳에 둔다 (BR-01, BR-02, BR-46).
 * 글이 방문자에게 보이려면 ① 작성완료(발행)됐고 ② 글이 공개이고 ③ 글이 속한 분류가 공개여야 한다.
 * 어느 모듈이든 post 표를 읽을 때 이 조건을 쓴다 (모듈 방향을 지키려고 common에 둔다).
 */
public final class PostVisibilitySql {

    /** 별칭이 p인 post 표에 쓰는 조건 */
    public static final String PUBLIC = publicPost("p");

    /** 별칭이 p인 post 표: 작성완료한 글 (블로그 주인의 목록·글 수에 쓴다. 임시저장 글은 어디에도 나오지 않는다) */
    public static final String PUBLISHED = published("p");

    private PostVisibilitySql() {
    }

    public static String publicPost(String alias) {
        return "(" + published(alias) + " and " + alias + ".visibility = 'PUBLIC'"
                + " and exists (select 1 from category vis_c where vis_c.id = " + alias + ".category_id"
                + " and vis_c.visibility = 'PUBLIC'))";
    }

    public static String published(String alias) {
        return "(" + alias + ".status = 'PUBLISHED')";
    }

    /**
     * 주인이면 작성완료한 글 전부(비공개 글·비공개 분류의 글 포함), 아니면 공개 조건.
     * includePrivate는 boolean 이름 붙은 파라미터 이름이다.
     */
    public static String visibleTo(String alias, String includePrivateParam) {
        return "(" + published(alias) + " and (:" + includePrivateParam + " or " + publicPost(alias) + "))";
    }

    /**
     * 여러 블로그의 글을 한 번에 읽을 때: 회원이 지금 읽을 수 있는 글 (내 블로그면 작성완료한 글 전부, 남의 글이면 공개 조건).
     * ownerIdExpr는 글이 속한 블로그 주인 id를 가리키는 SQL 식, viewerParam은 회원 id 파라미터 이름이다 (FR-069, BR-32).
     */
    public static String readableBy(String alias, String ownerIdExpr, String viewerParam) {
        return "(" + published(alias) + " and (" + ownerIdExpr + " = :" + viewerParam + " or " + publicPost(alias) + "))";
    }
}
