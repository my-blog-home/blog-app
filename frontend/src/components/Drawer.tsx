import { useEffect, useRef, useState } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { get } from '../api/client'
import { loadTopics } from '../api/topics'
import type { BlogView, MySubscriptions, Topic } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { useCurrentBlog } from '../layout/CurrentBlog'
import { M } from '../messages'

const SUBSCRIPTION_LIMIT = 8

/**
 * 햄버거 메뉴: 왼쪽에서 열린다. 보고 있는 블로그의 분류, 주제별 보기(공개 글 수), 내 블로그, 구독한 블로그 (FR-068).
 * 바깥을 누르거나 ESC로 닫고, 열면 닫기 버튼에, 닫으면 메뉴 버튼에 초점을 둔다.
 */
export default function Drawer({ open, onClose, returnFocus }: { open: boolean; onClose: () => void; returnFocus: () => void }) {
  const { me } = useAuth()
  const location = useLocation()
  const currentBlogId = useCurrentBlog()
  const closeRef = useRef<HTMLButtonElement>(null)
  const [topics, setTopics] = useState<Topic[]>([])
  const [blog, setBlog] = useState<BlogView | null>(null)
  const [subs, setSubs] = useState<MySubscriptions | null>(null)
  const wasOpen = useRef(false)

  // 열 때마다 새로 읽는다 (블로그 화면에서 구독을 눌렀어도 맞게 보이도록)
  useEffect(() => {
    if (!open) return
    loadTopics(true).then(setTopics).catch(() => setTopics([]))
    if (me) get<MySubscriptions>(`/api/me/subscriptions?limit=${SUBSCRIPTION_LIMIT}`).then(setSubs).catch(() => setSubs(null))
    else setSubs(null)
    if (currentBlogId) get<BlogView>(`/api/blogs/${currentBlogId}`).then(setBlog).catch(() => setBlog(null))
    else setBlog(null)
  }, [open, me, currentBlogId])

  // 페이지를 옮기면 닫는다
  useEffect(() => {
    onClose()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [location.pathname, location.search])

  useEffect(() => {
    if (open) {
      wasOpen.current = true
      closeRef.current?.focus()
      document.body.style.overflow = 'hidden'
      const onKey = (e: KeyboardEvent) => {
        if (e.key === 'Escape') onClose()
      }
      document.addEventListener('keydown', onKey)
      return () => {
        document.removeEventListener('keydown', onKey)
        document.body.style.overflow = ''
      }
    }
    if (wasOpen.current) {
      wasOpen.current = false
      returnFocus()
    }
  }, [open, onClose, returnFocus])

  const params = new URLSearchParams(location.search)
  const onHome = location.pathname === '/'
  const currentTopic = onHome ? params.get('topic') : null
  const currentCategory = location.pathname.startsWith('/blogs/') ? params.get('category') : null
  const allCount = topics.reduce((sum, t) => sum + t.postCount, 0)

  return (
    <>
      <div className={open ? 'drawer-backdrop open' : 'drawer-backdrop'} onClick={onClose} aria-hidden="true" />
      <nav id="drawer" className={open ? 'drawer open' : 'drawer'} aria-label="전체 메뉴" aria-hidden={!open}>
        <div className="drawer-head">
          <Link to="/" className="logo">
            Y<span>log</span>
          </Link>
          <button ref={closeRef} type="button" className="menu-btn" onClick={onClose} aria-label="메뉴 닫기">
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true">
              <path d="M6 6l12 12M18 6 6 18" />
            </svg>
          </button>
        </div>
        {me ? (
          <div className="drawer-user">
            <span className="avatar sm" style={{ background: me.profileColor }}>
              {[...me.nickname][0]}
            </span>
            <strong>{me.nickname}</strong>
          </div>
        ) : (
          <div className="drawer-auth">
            <Link to="/signup" className="button">
              회원가입
            </Link>
            <Link to={`/login?redirect=${encodeURIComponent(location.pathname + location.search)}`} className="button primary">
              로그인
            </Link>
          </div>
        )}

        {blog && (
          <div className="drawer-section">
            <h5>{blog.name} 분류</h5>
            <Link to={`/blogs/${blog.id}`} className={location.pathname === `/blogs/${blog.id}` && !currentCategory ? 'active' : ''}>
              전체 글 <span>{blog.totalPostCount}</span>
            </Link>
            {blog.categories.map((c) => (
              <Link key={c.id} to={`/blogs/${blog.id}?category=${c.id}`} className={currentCategory === String(c.id) ? 'active' : ''}>
                <em className="drawer-name">
                  {c.name}
                  {blog.owner && c.visibility === 'PRIVATE' && <span aria-label="비공개 분류"> 🔒</span>}
                </em>
                <span>{c.postCount}</span>
              </Link>
            ))}
          </div>
        )}

        <div className="drawer-section">
          <h5>주제별 보기</h5>
          <Link to="/" className={onHome && !currentTopic && params.get('tab') !== 'feed' ? 'active' : ''}>
            전체 <span>{allCount}</span>
          </Link>
          {topics.map((t) => (
            <Link key={t.id} to={`/?topic=${t.id}`} className={currentTopic === String(t.id) ? 'active' : ''}>
              {t.name} <span>{t.postCount}</span>
            </Link>
          ))}
        </div>

        {me?.role === 'ADMIN' && (
          <div className="drawer-section">
            <h5>관리자</h5>
            <Link to="/admin" className={location.pathname.startsWith('/admin') ? 'active' : ''}>
              관리자 화면 <span>{me.pendingReportCount ? `대기 ${me.pendingReportCount}` : ''}</span>
            </Link>
          </div>
        )}

        {me && (
          <div className="drawer-section">
            <h5>내 블로그</h5>
            {me.blogId && <Link to={`/blogs/${me.blogId}`}>내 블로그 홈</Link>}
            <Link to="/me/blog" className={location.pathname === '/me/blog' ? 'active' : ''}>
              내 블로그 · 내가 쓴 글
            </Link>
            <Link to="/me/activity" className={location.pathname === '/me/activity' ? 'active' : ''}>
              내 활동
            </Link>
            {/* 블로그가 없는 계정(관리자)에는 글쓰기·블로그 관리가 없다 (FR-078) */}
            {me.blogId && <Link to="/write">글쓰기</Link>}
            {me.blogId && <Link to="/manage">블로그 관리</Link>}
          </div>
        )}

        {me && (
          <div className="drawer-section">
            <h5>구독한 블로그 {subs ? subs.totalCount : ''}</h5>
            <Link to="/?tab=feed" className={onHome && params.get('tab') === 'feed' ? 'active' : ''}>
              구독 피드 보기
            </Link>
            {subs && subs.items.length === 0 && <p className="drawer-empty">{M.noSubscriptions}</p>}
            {subs?.items.map((b) => (
              <Link key={b.blogId} to={`/blogs/${b.blogId}`} className={currentBlogId === b.blogId ? 'active' : ''}>
                <em className="drawer-name">{b.blogName}</em> <span>{b.ownerNickname}</span>
              </Link>
            ))}
            {subs && subs.totalCount > subs.items.length && (
              <Link to="/?tab=feed">
                외 {subs.totalCount - subs.items.length}개 더 보기 <span>구독 피드</span>
              </Link>
            )}
          </div>
        )}
      </nav>
    </>
  )
}
