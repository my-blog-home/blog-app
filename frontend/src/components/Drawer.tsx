import { useEffect, useRef, useState, type RefObject } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { get } from '../api/client'
import { loadTopics } from '../api/topics'
import type { BlogView, Me, MySubscriptions, Topic } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { useCurrentBlog } from '../layout/CurrentBlog'
import { M } from '../messages'
import { useTheme } from '../theme'

const SUBSCRIPTION_LIMIT = 8

/** 지금 보고 있는 메뉴 항목이면 active */
const active = (on: boolean) => (on ? 'active' : '')

type Release = { version: string; releaseNoticeId: number | null }

// 열 때마다 새로 읽는다 (블로그 화면에서 구독을 눌렀어도 맞게 보이도록)
function useDrawerData(open: boolean, me: Me | null, currentBlogId: number | null) {
  const [topics, setTopics] = useState<Topic[]>([])
  const [blog, setBlog] = useState<BlogView | null>(null)
  const [subs, setSubs] = useState<MySubscriptions | null>(null)

  useEffect(() => {
    if (!open) return
    loadTopics(true).then(setTopics).catch(() => setTopics([]))
    if (me) get<MySubscriptions>(`/api/me/subscriptions?limit=${SUBSCRIPTION_LIMIT}`).then(setSubs).catch(() => setSubs(null))
    else setSubs(null)
    if (currentBlogId) get<BlogView>(`/api/blogs/${currentBlogId}`).then(setBlog).catch(() => setBlog(null))
    else setBlog(null)
  }, [open, me, currentBlogId])

  return { topics, blog, subs }
}

// 열면 닫기 버튼에 초점을 두고 ESC로 닫는다. 닫히면 메뉴 버튼에 초점을 돌려준다
function useDrawerFocus(open: boolean, onClose: () => void, returnFocus: () => void, closeRef: RefObject<HTMLButtonElement>) {
  const wasOpen = useRef(false)

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
  }, [open, onClose, returnFocus, closeRef])
}

function BlogCategorySection({ blog, pathname, currentCategory }: Readonly<{ blog: BlogView; pathname: string; currentCategory: string | null }>) {
  return (
    <div className="drawer-section">
      <h5>{blog.name} 분류</h5>
      <Link to={`/blogs/${blog.id}`} className={active(pathname === `/blogs/${blog.id}` && !currentCategory)}>
        전체 글 <span>{blog.totalPostCount}</span>
      </Link>
      {blog.categories.map((c) => (
        <Link key={c.id} to={`/blogs/${blog.id}?category=${c.id}`} className={active(currentCategory === String(c.id))}>
          <em className="drawer-name">
            {c.name}
            {blog.owner && c.visibility === 'PRIVATE' && <span aria-label="비공개 분류"> 🔒</span>}
          </em>
          <span>{c.postCount}</span>
        </Link>
      ))}
    </div>
  )
}

function MyBlogSection({ me, pathname }: Readonly<{ me: Me; pathname: string }>) {
  return (
    <div className="drawer-section">
      <h5>내 블로그</h5>
      {me.blogId && <Link to={`/blogs/${me.blogId}`}>내 블로그 홈</Link>}
      <Link to="/me/blog" className={active(pathname === '/me/blog')}>
        내 블로그 · 내가 쓴 글
      </Link>
      <Link to="/me/activity" className={active(pathname === '/me/activity')}>
        내 활동
      </Link>
      <Link to="/me/bookmarks" className={active(pathname === '/me/bookmarks')}>
        북마크
      </Link>
      {/* 블로그가 없는 계정(관리자)에는 글쓰기·블로그 관리가 없다 (FR-078) */}
      {me.blogId && <Link to="/write">글쓰기</Link>}
      {me.blogId && <Link to="/manage">블로그 관리</Link>}
    </div>
  )
}

function SubscriptionSection({ subs, feedActive, currentBlogId }: Readonly<{ subs: MySubscriptions | null; feedActive: boolean; currentBlogId: number | null }>) {
  return (
    <div className="drawer-section">
      <h5>구독한 블로그 {subs ? subs.totalCount : ''}</h5>
      <Link to="/?tab=feed" className={active(feedActive)}>
        구독 피드 보기
      </Link>
      {subs && subs.items.length === 0 && <p className="drawer-empty">{M.noSubscriptions}</p>}
      {subs?.items.map((b) => (
        <Link key={b.blogId} to={`/blogs/${b.blogId}`} className={active(currentBlogId === b.blogId)}>
          <em className="drawer-name">{b.blogName}</em> <span>{b.ownerNickname}</span>
        </Link>
      ))}
      {subs && subs.totalCount > subs.items.length && (
        <Link to="/?tab=feed">
          외 {subs.totalCount - subs.items.length}개 더 보기 <span>구독 피드</span>
        </Link>
      )}
    </div>
  )
}

function NoticeSection({ release, pathname, search }: Readonly<{ release: Release | null; pathname: string; search: string }>) {
  return (
    <div className="drawer-section">
      <h5>공지사항</h5>
      <Link to="/notices" className={active(pathname.startsWith('/notices') && !search)}>
        전체 공지
      </Link>
      <Link to="/notices?type=NOTICE" className={active(search === '?type=NOTICE')}>
        공지
      </Link>
      <Link to="/notices?type=GUIDE" className={active(search === '?type=GUIDE')}>
        이용법
      </Link>
      {release?.releaseNoticeId && (
        <Link to={`/notices/${release.releaseNoticeId}`} className={active(pathname === `/notices/${release.releaseNoticeId}`)}>
          릴리스 노트 <span>v{release.version}</span>
        </Link>
      )}
    </div>
  )
}

/**
 * 햄버거 메뉴: 왼쪽에서 열린다. 보고 있는 블로그의 분류, 주제별 보기(공개 글 수), 내 블로그, 구독한 블로그 (FR-068).
 * 바깥을 누르거나 ESC로 닫고, 열면 닫기 버튼에, 닫으면 메뉴 버튼에 초점을 둔다.
 */
export default function Drawer({ open, onClose, returnFocus }: Readonly<{ open: boolean; onClose: () => void; returnFocus: () => void }>) {
  const { me } = useAuth()
  const [theme, toggleTheme] = useTheme()
  const location = useLocation()
  const currentBlogId = useCurrentBlog()
  const closeRef = useRef<HTMLButtonElement>(null)
  // 공지사항 칸의 "최신 릴리스 노트" 링크에 쓴다
  const [release, setRelease] = useState<Release | null>(null)

  useEffect(() => {
    get<Release>('/api/config/version')
      .then(setRelease)
      .catch(() => setRelease(null))
  }, [])

  const { topics, blog, subs } = useDrawerData(open, me, currentBlogId)

  // 페이지를 옮기면 닫는다
  useEffect(() => {
    onClose()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [location.pathname, location.search])

  useDrawerFocus(open, onClose, returnFocus, closeRef)

  const params = new URLSearchParams(location.search)
  const onHome = location.pathname === '/'
  const currentTopic = onHome ? params.get('topic') : null
  const currentCategory = location.pathname.startsWith('/blogs/') ? params.get('category') : null
  const allCount = topics.reduce((sum, t) => sum + t.postCount, 0)
  const feedTab = params.get('tab') === 'feed'

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

        {blog && <BlogCategorySection blog={blog} pathname={location.pathname} currentCategory={currentCategory} />}

        <div className="drawer-section">
          <h5>주제별 보기</h5>
          <Link to="/" className={active(onHome && !currentTopic && !feedTab)}>
            전체 <span>{allCount}</span>
          </Link>
          {topics.map((t) => (
            <Link key={t.id} to={`/?topic=${t.id}`} className={active(currentTopic === String(t.id))}>
              {t.name} <span>{t.postCount}</span>
            </Link>
          ))}
        </div>

        {me?.role === 'ADMIN' && (
          <div className="drawer-section">
            <h5>관리자</h5>
            <Link to="/admin" className={active(location.pathname.startsWith('/admin'))}>
              관리자 화면 <span>{me.pendingReportCount ? `대기 ${me.pendingReportCount}` : ''}</span>
            </Link>
          </div>
        )}

        {me && <MyBlogSection me={me} pathname={location.pathname} />}

        {me && <SubscriptionSection subs={subs} feedActive={onHome && feedTab} currentBlogId={currentBlogId} />}

        <NoticeSection release={release} pathname={location.pathname} search={location.search} />

        <div className="drawer-section">
          <h5>화면</h5>
          <button type="button" className="drawer-item" onClick={toggleTheme}>
            {theme === 'dark' ? '밝은 화면으로 보기' : '어두운 화면으로 보기'}
          </button>
        </div>
      </nav>
    </>
  )
}
