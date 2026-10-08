import { useEffect, useState } from 'react'
import { Link, NavLink, Outlet, useOutletContext } from 'react-router-dom'
import { get } from '../../api/client'
import type { BlogView } from '../../api/types'
import { useAuth } from '../../auth/AuthContext'
import { withPreview } from '../../preview'
import { useDocumentMeta } from '../../meta'

export interface ManageContext {
  blog: BlogView
  reloadBlog: () => Promise<void>
}

export const useManage = () => useOutletContext<ManageContext>()

/** 블로그 관리: 왼쪽 메뉴, 오른쪽 내용. 블로그 주인만 (BM-01) */
export default function ManageLayout() {
  const { me, loading, requireLogin, newCommentCount } = useAuth()
  const [blog, setBlog] = useState<BlogView | null>(null)
  useDocumentMeta('블로그 관리')

  const reloadBlog = async () => {
    if (me?.blogId) setBlog(await get<BlogView>(`/api/blogs/${me.blogId}`))
  }

  useEffect(() => {
    if (loading) return
    if (!me) {
      requireLogin()
      return
    }
    reloadBlog()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [loading, me])

  if (!me) return <p className="empty">로그인이 필요합니다</p>
  // 관리자 계정은 블로그가 없다 (FR-078)
  if (!me.blogId)
    return (
      <div className="empty">
        <p>블로그가 없는 계정입니다</p>
        {me.role === 'ADMIN' && <Link to="/admin">관리자 화면으로 가기</Link>}
      </div>
    )
  if (!blog) return null

  return (
    <div className="manage">
      <aside className="manage-menu">
        <div className="manage-top">
          <strong>{blog.name}</strong>
          <div className="row">
            <Link to={`/blogs/${blog.id}`} className="button sm">
              내 블로그 보기
            </Link>
            {/* 로그인하지 않은 방문자에게 보이는 모습으로 연다 (FR-083) */}
            <Link to={withPreview(`/blogs/${blog.id}`)} className="button sm">
              방문자 화면으로 보기
            </Link>
            <Link to="/write" className="button sm primary">
              글쓰기
            </Link>
          </div>
        </div>
        <nav>
          <NavLink to="/manage" end>
            대시보드
          </NavLink>
          <NavLink to="/manage/posts">글 관리</NavLink>
          <NavLink to="/manage/categories">분류 관리</NavLink>
          <NavLink to="/manage/comments">
            댓글 관리{newCommentCount > 0 && <span className="count-badge">{newCommentCount}</span>}
          </NavLink>
          <NavLink to="/manage/stats">통계</NavLink>
          <NavLink to="/manage/settings">설정</NavLink>
        </nav>
      </aside>
      <section className="manage-body">
        <Outlet context={{ blog, reloadBlog } satisfies ManageContext} />
      </section>
    </div>
  )
}
