import { useEffect, useState } from 'react'
import { Link, NavLink, Outlet, useOutletContext } from 'react-router-dom'
import { get } from '../../api/client'
import type { BlogView } from '../../api/types'
import { useAuth } from '../../auth/AuthContext'

export interface ManageContext {
  blog: BlogView
  reloadBlog: () => Promise<void>
}

export const useManage = () => useOutletContext<ManageContext>()

/** 블로그 관리: 왼쪽 메뉴, 오른쪽 내용. 블로그 주인만 (BM-01) */
export default function ManageLayout() {
  const { me, loading, requireLogin, newCommentCount } = useAuth()
  const [blog, setBlog] = useState<BlogView | null>(null)

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
  if (!blog) return null

  return (
    <div className="manage">
      <aside className="manage-menu">
        <p className="blog-name">{blog.name}</p>
        <div className="row small">
          <Link to={`/blogs/${blog.id}`}>내 블로그 보기</Link>
          <Link to="/write">글쓰기</Link>
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
