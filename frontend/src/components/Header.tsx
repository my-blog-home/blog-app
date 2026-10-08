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
        <Link to="/" className="logo">
          내 블로그
        </Link>
        <form className="search-box" onSubmit={search} role="search">
          <input value={q} onChange={(e) => setQ(e.target.value)} placeholder="검색" aria-label="검색어" />
        </form>
        <nav className="header-nav">
          <button className="link" onClick={write}>
            글쓰기
          </button>
          {me ? (
            <>
              {me.blogId && <Link to={`/blogs/${me.blogId}`}>{me.nickname}</Link>}
              <Link to="/manage">
                블로그 관리{newCommentCount > 0 && <span className="count-badge" aria-label={`새 댓글 ${newCommentCount}개`}>{newCommentCount}</span>}
              </Link>
              <Link to="/me">마이페이지</Link>
              <button className="link" onClick={onLogout}>
                로그아웃
              </button>
            </>
          ) : (
            <>
              <Link to={`/login?redirect=${encodeURIComponent(location.pathname + location.search)}`}>로그인</Link>
              <Link to="/signup">회원가입</Link>
            </>
          )}
        </nav>
      </div>
    </header>
  )
}
