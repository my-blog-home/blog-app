import { useEffect } from 'react'
import { Link, NavLink, Outlet, useOutletContext } from 'react-router-dom'
import { useAuth } from '../../auth/AuthContext'
import { useDocumentMeta } from '../../meta'

export interface AdminContext {
  /** 처리 대기 신고 수(머리글의 "관리자" 옆 숫자)를 다시 읽는다 */
  refreshBadge: () => Promise<void>
}

export const useAdmin = () => useOutletContext<AdminContext>()

/**
 * 관리자 화면: 왼쪽 메뉴(요약·신고 처리·회원 관리·공지 관리), 오른쪽 내용 (FR-078~082).
 * 로그인하지 않았으면 로그인 창, 관리자가 아니면 "관리자만 볼 수 있는 화면입니다". 서버도 요청마다 다시 확인한다.
 */
export default function AdminLayout() {
  const { me, loading, requireLogin, refresh } = useAuth()
  useDocumentMeta('관리자')

  useEffect(() => {
    if (!loading && !me) requireLogin()
  }, [loading, me, requireLogin])

  if (loading) return null
  if (!me)
    return (
      <div className="empty">
        <p>로그인이 필요합니다</p>
        <button type="button" className="link" onClick={() => requireLogin()}>
          로그인하기
        </button>
      </div>
    )
  if (me.role !== 'ADMIN')
    return (
      <div className="empty">
        <p>관리자만 볼 수 있는 화면입니다</p>
        <Link to="/">홈으로</Link>
      </div>
    )

  const pending = me.pendingReportCount ?? 0
  return (
    <div className="manage">
      <aside className="manage-menu">
        <div className="manage-top">
          <strong>관리자</strong>
          <span className="muted small">{me.nickname}</span>
        </div>
        <nav aria-label="관리자 메뉴">
          <NavLink to="/admin" end>
            요약
          </NavLink>
          <NavLink to="/admin/reports">
            신고 처리{pending > 0 && <span className="count-badge" aria-label={`처리 대기 신고 ${pending}건`}>{pending}</span>}
          </NavLink>
          <NavLink to="/admin/members">회원 관리</NavLink>
          <NavLink to="/admin/notices">공지 관리</NavLink>
        </nav>
      </aside>
      <section className="manage-body">
        <Outlet context={{ refreshBadge: refresh } satisfies AdminContext} />
      </section>
    </div>
  )
}
