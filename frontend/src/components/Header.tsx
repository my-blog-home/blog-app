import { useCallback, useRef, useState, type FormEvent } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import Drawer from './Drawer'

// 로그인해야 쓰는 화면
const MEMBER_ONLY = /^\/(write|me(\/activity)?|manage(\/.*)?|admin(\/.*)?|posts\/\d+\/edit)$/

export default function Header() {
  const { me, logout, requireLogin, newCommentCount } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [q, setQ] = useState('')
  const [drawerOpen, setDrawerOpen] = useState(false)
  const menuRef = useRef<HTMLButtonElement>(null)
  const closeDrawer = useCallback(() => setDrawerOpen(false), [])
  const focusMenu = useCallback(() => menuRef.current?.focus(), [])

  const search = (e: FormEvent) => {
    e.preventDefault()
    navigate(`/search?q=${encodeURIComponent(q.trim())}`)
  }

  const onLogout = async () => {
    await logout()
    // 회원 전용 화면에 있었다면 첫 화면으로 (CF-02-11)
    if (MEMBER_ONLY.test(location.pathname)) navigate('/')
  }

  const write = () => {
    if (requireLogin(() => navigate('/write'))) navigate('/write')
  }

  // 머리글의 backdrop-filter가 fixed 위치를 가두므로 메뉴는 머리글 밖에 둔다
  return (
    <>
    <header className="header">
      <div className="header-inner">
        {/* 햄버거 메뉴 (FR-068) */}
        <button
          ref={menuRef}
          type="button"
          className="menu-btn"
          aria-label="전체 메뉴 열기"
          aria-expanded={drawerOpen}
          aria-controls="drawer"
          onClick={() => setDrawerOpen(true)}
        >
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true">
            <path d="M4 6h16M4 12h16M4 18h16" />
          </svg>
        </button>
        <Link to="/" className="logo" aria-label="Ylog 첫 화면">
          Y<span>log</span>
        </Link>
        <form className="search-box" onSubmit={search} role="search">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true">
            <circle cx="11" cy="11" r="7" />
            <path d="m20 20-3.5-3.5" />
          </svg>
          <input value={q} onChange={(e) => setQ(e.target.value)} placeholder="글 검색" aria-label="검색어" />
        </form>
        <nav className="header-nav">
          {/* 블로그가 없는 계정(관리자)에는 글쓰기·블로그 관리가 없다 (FR-078) */}
          {(!me || me.blogId) && <button onClick={write}>글쓰기</button>}
          {me ? (
            <>
              {me.blogId && (
                <Link to={`/blogs/${me.blogId}`} className="hide-mobile header-me">
                  <span className="avatar sm" style={{ background: me.profileColor }}>
                    {[...me.nickname][0]}
                  </span>
                  {me.nickname}
                </Link>
              )}
              {me.blogId && (
                <Link to="/manage">
                  블로그 관리{newCommentCount > 0 && <span className="count-badge" aria-label={`새 댓글 ${newCommentCount}개`}>{newCommentCount}</span>}
                </Link>
              )}
              {me.role === 'ADMIN' && (
                <Link to="/admin">
                  관리자
                  {(me.pendingReportCount ?? 0) > 0 && (
                    <span className="count-badge" aria-label={`처리 대기 신고 ${me.pendingReportCount}건`}>
                      {me.pendingReportCount}
                    </span>
                  )}
                </Link>
              )}
              <Link to="/me">마이페이지</Link>
              <button onClick={onLogout}>로그아웃</button>
            </>
          ) : (
            <>
              <Link to="/signup">회원가입</Link>
              <Link to={`/login?redirect=${encodeURIComponent(location.pathname + location.search)}`} className="button primary">
                로그인
              </Link>
            </>
          )}
        </nav>
      </div>
    </header>
    <Drawer open={drawerOpen} onClose={closeDrawer} returnFocus={focusMenu} />
    </>
  )
}
