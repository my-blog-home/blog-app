import { useState, type FormEvent } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'

// 로그인해야 쓰는 화면
const MEMBER_ONLY = /^\/(write|me|manage(\/.*)?|posts\/\d+\/edit)$/

export default function Header() {
  const { me, logout, requireLogin, newCommentCount } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [q, setQ] = useState('')

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

  return (
    <header className="header">
      <div className="header-inner">
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
          <button onClick={write}>글쓰기</button>
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
              <Link to="/manage">
                블로그 관리{newCommentCount > 0 && <span className="count-badge" aria-label={`새 댓글 ${newCommentCount}개`}>{newCommentCount}</span>}
              </Link>
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
  )
}
