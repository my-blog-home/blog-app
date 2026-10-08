package com.myblog.common.sql;

/**
 * "지금 정지 중"의 SQL 조건을 한곳에 둔다 (FR-080, FR-081, BR-49).
 * 정지 기록이 풀리지 않았고(lifted_at 없음), 영구이거나(ends_at 없음) 끝나는 시각이 지금보다 뒤면 효력이 있다.
 * 그래서 기간이 끝나면 따로 풀어 줄 일이 없다. 로그인(user), 요청마다 확인(common), 관리자 화면(admin)이 함께 쓴다.
 */
public final class SuspensionSql {

    /** 겹친 정지 중 가장 오래 가는 것(영구가 먼저)이 앞에 오는 정렬 */
    public static final String LONGEST_FIRST = "ends_at desc nulls first";

    private SuspensionSql() {
    }

    /** 별칭 alias인 suspension 표의 행이 지금 효력이 있는지. nowParam은 지금 시각 파라미터 이름이다 */
    public static String active(String alias, String nowParam) {
        return "(" + alias + ".lifted_at is null and (" + alias + ".ends_at is null or " + alias + ".ends_at > :"
                + nowParam + "))";
    }

    /** 회원 id 식(memberIdExpr)이 지금 정지 중인지 */
    public static String memberSuspended(String memberIdExpr, String nowParam) {
        return "exists (select 1 from suspension act_s where act_s.member_id = " + memberIdExpr + " and "
                + active("act_s", nowParam) + ")";
    }
}
